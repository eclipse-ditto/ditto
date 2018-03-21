/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.authorization.util.actors;

import java.util.Optional;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.things.AclNotAllowedException;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.models.things.Permission;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyConflictException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyUnavailableException;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicy;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicyResponse;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.PolicyIdNotAllowedException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotCreatableException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingUnavailableException;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;

import akka.actor.ActorRef;
import akka.pattern.AskTimeoutException;
import akka.pattern.PatternsCS;

/**
 * Mixin to handle inline policy in the first {@code CreateThing} command to a thing.
 */
// TODO: javadoc
interface InlinePolicyHandling extends Enforcement {

    String DEFAULT_POLICY_ENTRY_LABEL = "DEFAULT";

    default boolean handleInitialCreateThing(final CreateThing createThing, final Enforcer enforcer,
            final ActorRef sender) {
        if (shouldCreatePolicyForCreateThing(createThing)) {

            checkForErrorsInCreateThingWithPolicy(createThing)
                    .map(error ->
                            replyToSender(error, sender))
                    .orElseGet(() ->
                            createThingWithInitialPolicy(createThing, enforcer, sender));

        } else if (createThing.getThing().getPolicyId().isPresent()) {

            final String policyId = createThing.getThing().getPolicyId().get();
            checkForErrorsInCreateThingWithPolicy(createThing)
                    .map(error ->
                            replyToSender(error, sender))
                    .orElseGet(() ->
                            enforceCreateThingForNonexistentThingWithPolicyId(createThing, policyId, sender));

        } else {
            // nothing to do with policy, simply forward the command
            forwardToThingsShardRegion(createThing, sender);
        }
        return true;
    }

    default boolean createThingWithInitialPolicy(final CreateThing createThing,
            final Enforcer enforcer,
            final ActorRef sender) {

        try {
            final Optional<Policy> policy =
                    getInlinedOrDefaultPolicyForCreateThing(createThing);

            if (policy.isPresent()) {

                final CreatePolicy createPolicy = CreatePolicy.of(policy.get(), createThing.getDittoHeaders());
                final Optional<CreatePolicy> authorizedCreatePolicy = authorizePolicyCommand(createPolicy, enforcer);
                // CreatePolicy is rejected; abort CreateThing.
                return authorizedCreatePolicy
                        .filter(cmd -> createPolicyAndThing(cmd, createThing, sender))
                        .isPresent();
            } else {
                // cannot create policy.
                final String thingId = createThing.getThingId();
                final String message = String.format("The Thing with ID ''%s'' could not be created with implicit " +
                        "Policy because no authorization subject is present.", thingId);
                final ThingNotCreatableException error =
                        ThingNotCreatableException.newBuilderForPolicyMissing(thingId, thingId)
                                .message(message)
                                .description(() -> null)
                                .build();
                replyToSender(error, sender);
                return true;
            }
        } catch (DittoRuntimeException error) {
            log().error(error, "error before creating thing with initial policy");
            replyToSender(error, sender);
            return true;
        }
    }

    static Optional<Policy> getInlinedOrDefaultPolicyForCreateThing(final CreateThing createThing) {
        if (createThing.getInitialPolicy().isPresent()) {
            final JsonObject policyJson = createThing.getInitialPolicy().get();
            final JsonObject policyJsonWithThingId = policyJson.toBuilder()
                    .set(Policy.JsonFields.ID, createThing.getThingId())
                    .build();
            return Optional.of(PoliciesModelFactory.newPolicy(policyJsonWithThingId));
        } else {
            return getDefaultPolicy(createThing.getDittoHeaders().getAuthorizationContext(), createThing.getThingId());
        }
    }

    static Optional<DittoRuntimeException> checkForErrorsInCreateThingWithPolicy(final CreateThing createThing) {
        return checkAclAbsenceInCreateThing(createThing)
                .map(Optional::of)
                .orElseGet(() -> checkPolicyIdValidityForCreateThing(createThing));
    }

    static Optional<DittoRuntimeException> checkAclAbsenceInCreateThing(final CreateThing createThing) {
        if (createThing.getThing().getAccessControlList().isPresent()) {
            final DittoRuntimeException error = AclNotAllowedException.newBuilder(createThing.getThingId())
                    .dittoHeaders(createThing.getDittoHeaders())
                    .build();
            return Optional.of(error);
        } else {
            return Optional.empty();
        }
    }

    static Optional<DittoRuntimeException> checkPolicyIdValidityForCreateThing(final CreateThing createThing) {
        final Thing thing = createThing.getThing();
        final Optional<String> thingIdOpt = thing.getId();
        final Optional<String> policyIdOpt = thing.getPolicyId();
        final Optional<String> policyIdInPolicyOpt = createThing.getInitialPolicy()
                .flatMap(jsonObject -> jsonObject.getValue(Thing.JsonFields.POLICY_ID));

        final boolean isValid;
        if (policyIdOpt.isPresent()) {
            isValid = !policyIdInPolicyOpt.isPresent() || policyIdInPolicyOpt.equals(policyIdOpt);
        } else {
            isValid = !policyIdInPolicyOpt.isPresent() || policyIdInPolicyOpt.equals(thingIdOpt);
        }

        if (!isValid) {
            final DittoRuntimeException error = PolicyIdNotAllowedException.newBuilder(createThing.getThingId())
                    .dittoHeaders(createThing.getDittoHeaders())
                    .build();
            return Optional.of(error);
        } else {
            return Optional.empty();
        }
    }

    static boolean shouldCreatePolicyForCreateThing(final CreateThing createThing) {
        final JsonSchemaVersion commandVersion =
                createThing.getDittoHeaders().getSchemaVersion().orElse(JsonSchemaVersion.LATEST);
        return createThing.getInitialPolicy().isPresent() ||
                (JsonSchemaVersion.V_1 != commandVersion && !createThing.getThing().getPolicyId().isPresent());
    }

    default boolean createPolicyAndThing(final CreatePolicy createPolicy,
            final CreateThing createThingWithoutPolicyId,
            final ActorRef sender) {

        final long timeout = getAskTimeout().toMillis();

        final CreateThing createThing = CreateThing.of(
                createThingWithoutPolicyId.getThing().setPolicyId(createPolicy.getId()),
                null,
                createThingWithoutPolicyId.getDittoHeaders());

        PatternsCS.ask(policiesShardRegion(), createPolicy, timeout).handleAsync((policyResponse, policyError) -> {

            final Optional<CreateThing> nextStep =
                    handlePolicyResponseForCreateThing(createPolicy, createThing, policyResponse, policyError, sender);

            nextStep.ifPresent(cmd -> PatternsCS.ask(thingsShardRegion(), cmd, timeout)
                    .handleAsync((thingResponse, thingError) ->
                            handleThingResponseForCreateThing(createThing, thingResponse, thingError, sender)));

            return null;
        });

        return true;
    }

    default Optional<CreateThing> handlePolicyResponseForCreateThing(
            final CreatePolicy createPolicy,
            final CreateThing createThing,
            final Object policyResponse,
            final Throwable policyError,
            final ActorRef sender) {

        if (policyResponse instanceof CreatePolicyResponse) {

            return Optional.of(createThing);

        } else if (policyResponse instanceof PolicyConflictException ||
                policyResponse instanceof PolicyNotAccessibleException) {

            reportInitialPolicyCreationFailure(createPolicy.getId(), createThing, sender);

        } else if (policyError instanceof AskTimeoutException) {

            replyToSender(PolicyUnavailableException.newBuilder(createThing.getThingId()).build(), sender);

        } else {

            final String hint =
                    String.format("creating initial policy during creation of Thing <%s>",
                            createThing.getThingId());
            reportUnexpectedErrorOrResponse(hint, sender, policyResponse, policyError);
        }

        return Optional.empty();
    }

    default Void handleThingResponseForCreateThing(
            final CreateThing createThing,
            final Object thingResponse,
            final Throwable thingError,
            final ActorRef sender) {

        if (thingResponse instanceof ThingCommandResponse || thingResponse instanceof DittoRuntimeException) {

            replyToSender(thingResponse, sender);

        } else if (thingError instanceof AskTimeoutException) {

            replyToSender(ThingUnavailableException.newBuilder(createThing.getThingId()).build(), sender);

        } else {

            final String hint =
                    String.format("after creating initial policy during creation of Thing <%s>",
                            createThing.getThingId());
            reportUnexpectedErrorOrResponse(hint, sender, thingResponse, thingError);
        }

        return null;
    }

    default void reportInitialPolicyCreationFailure(final String policyId,
            final CreateThing command,
            final ActorRef sender) {

        log().info("The Policy with ID '{}' is already existing, the CreateThing " +
                "command which would have created an implicit Policy for the Thing with ID '{}' " +
                "is therefore not handled", policyId, command.getThingId());
        final ThingNotCreatableException error =
                ThingNotCreatableException.newBuilderForPolicyExisting(command.getThingId(), policyId)
                        .dittoHeaders(command.getDittoHeaders())
                        .build();
        replyToSender(error, sender);
    }

    static Optional<Policy> getDefaultPolicy(final AuthorizationContext authorizationContext,
            final CharSequence thingId) {

        final Optional<Subject> subjectOptional = authorizationContext.getFirstAuthorizationSubject()
                .map(AuthorizationSubject::getId)
                .map(SubjectId::newInstance)
                .map(Subject::newInstance);

        return subjectOptional.map(subject ->
                Policy.newBuilder(thingId)
                        .forLabel(DEFAULT_POLICY_ENTRY_LABEL)
                        .setSubject(subject)
                        .setGrantedPermissions(PoliciesResourceType.thingResource("/"),
                                Permission.DEFAULT_THING_PERMISSIONS)
                        .setGrantedPermissions(PoliciesResourceType.policyResource("/"),
                                org.eclipse.ditto.services.models.policies.Permission.DEFAULT_POLICY_PERMISSIONS)
                        .setGrantedPermissions(PoliciesResourceType.messageResource("/"),
                                org.eclipse.ditto.services.models.policies.Permission.DEFAULT_POLICY_PERMISSIONS)
                        .build());
    }

}
