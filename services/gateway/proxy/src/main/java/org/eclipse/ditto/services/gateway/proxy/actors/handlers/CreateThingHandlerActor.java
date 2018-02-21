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
package org.eclipse.ditto.services.gateway.proxy.actors.handlers;

import static org.eclipse.ditto.services.models.policies.Permission.READ;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.common.Validator;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyException;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policiesenforcers.EffectedSubjectIds;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcers;
import org.eclipse.ditto.model.things.AclNotAllowedException;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingBuilder;
import org.eclipse.ditto.model.things.ThingIdInvalidException;
import org.eclipse.ditto.services.models.policies.PoliciesValidator;
import org.eclipse.ditto.services.models.policies.PolicyInvalidException;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.services.models.things.Permission;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.cluster.ShardedMessageEnvelope;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyConflictException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyUnavailableException;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicy;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicyResponse;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.PolicyIdNotAllowedException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingConflictException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotCreatableException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotModifiableException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingUnavailableException;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.CreateThingResponse;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.FI;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.AskTimeoutException;
import akka.util.Timeout;
import scala.concurrent.duration.Duration;

/**
 * This Actor handles {@link CreateThing} commands, which means that it forwards requests made to API version 1 and
 * intersects requests to API version 2 to handle the policy. If there is no policy at all for such requests, it will
 * inject a default policy with regards to the subjects in the request otherwise it validates the given policies.
 * In both cases, first the Policy and if successful, the Thing is created. If the latter fails, the creation of the
 * Policy is rolled back by deleting it.
 * <p>
 * This actor will terminate after it has handled the request.
 */
public final class CreateThingHandlerActor extends AbstractActor {

    private static final String DEFAULT_POLICY_ENTRY_LABEL = "DEFAULT";
    private static final int ASK_DURATION_VALUE = 20000;
    private static final Timeout ASK_TIMEOUT =
            new Timeout(Duration.create(ASK_DURATION_VALUE, TimeUnit.MILLISECONDS));

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    @Nullable
    private final ActorRef enforcerShard;

    @Nullable
    private final String enforcerId;

    @Nonnull
    private final ActorRef aclEnforcerShard;

    @Nonnull
    private final ActorRef policyEnforcerShard;

    private DittoHeaders dittoHeaders;
    private String thingId;
    private ActorRef requester;

    private CreateThingHandlerActor(@Nullable final ActorRef enforcerShard,
            @Nullable final String enforcerId,
            @Nonnull final ActorRef aclEnforcerShard,
            @Nonnull final ActorRef policyEnforcerShard) {

        this.enforcerShard = enforcerShard;
        this.enforcerId = enforcerId;
        this.aclEnforcerShard = aclEnforcerShard;
        this.policyEnforcerShard = policyEnforcerShard;
    }

    /**
     * Creates an actor creation function for proxy actor.
     *
     * @param enforcerShard Reference to the shard region actor containing the enforcer for this command, or null if it
     * does not exist.
     * @param enforcerId ID of the enforcer actor within the shard for this command, or null if it does not exist.
     * @param aclEnforcerShard The shard region of ACL enforcer actors.
     * @param policyEnforcerShard The shard region of policy enforcer actors.
     * @return a function creating this actor from enforcer shard region and policies shard region.
     */
    public static Props props(final ActorRef enforcerShard, final String enforcerId, final ActorRef aclEnforcerShard,
            final ActorRef policyEnforcerShard) {

        return Props.create(CreateThingHandlerActor.class, () ->
                new CreateThingHandlerActor(enforcerShard, enforcerId, aclEnforcerShard, policyEnforcerShard));
    }

    @Override
    public Receive createReceive() {
        final FI.UnitApply<CreateThing> messageHandler = enforcerShard == null
                ? this::handleCreateThingWithoutEnforcer
                : this::handleCreateThingWithEnforcer;

        return ReceiveBuilder.create()
                .match(CreateThing.class, createThing -> {
                    LogUtil.enhanceLogWithCorrelationId(log, createThing);
                    log.debug("Got 'CreateThing': {}", createThing);

                    // set actor state for message
                    requester = getSender();
                    dittoHeaders = createThing.getDittoHeaders();
                    thingId = createThing.getThingId();

                    messageHandler.apply(createThing);
                })
                .matchAny(this::handleUnknownMessage)
                .build();
    }

    private void handleUnknownMessage(final Object m) {
        log.warning("Got unknown message: {}", m);
    }

    private void handleCreateThingWithEnforcer(final CreateThing createThing) {
        if (isJsonSchemaVersionV1(createThing)) {
            enforcerShard.forward(createShardedMessageEnvelope(enforcerId, createThing), getContext());
            getContext().stop(getSelf());
        } else if (isAclPresent(createThing)) {
            getSender().tell(
                    ThingErrorResponse.of(createThing.getId(), AclNotAllowedException.newBuilder(createThing.getId())
                            .dittoHeaders(createThing.getDittoHeaders())
                            .build(), createThing.getDittoHeaders()),
                    getSelf());
            getContext().stop(getSelf());
        } else if (createThing.getInitialPolicy().isPresent()) {
            // if an enforcer exists and there is an inline policy then we reject the request.
            // this may happen if the cache is out of date; the user should try again later.
            final ThingConflictException thingConflictException =
                    ThingConflictException.newBuilder(createThing.getThingId())
                            .dittoHeaders(createThing.getDittoHeaders())
                            .build();
            requester.tell(thingConflictException, getSelf());
        } else {
            // otherwise just forward the command to the enforcer.
            enforcerShard.forward(createShardedMessageEnvelope(enforcerId, createThing), getContext());
        }

        getContext().stop(getSelf());
    }

    private void handleCreateThingWithoutEnforcer(final CreateThing command) {
        if (isJsonSchemaVersionV1(command)) {
            aclEnforcerShard.forward(command, getContext());
            getContext().stop(getSelf());
        } else if (isAclPresent(command)) {
            getSender().tell(
                    ThingErrorResponse.of(command.getId(), AclNotAllowedException.newBuilder(command.getId())
                            .dittoHeaders(command.getDittoHeaders())
                            .build(), command.getDittoHeaders()),
                    getSelf());
            getContext().stop(getSelf());
        } else if (isPolicyIdValid(command)) {
            handleCreateThingWithValidPolicyIdWithoutEnforcer(command);
        } else {
            getSender().tell(PolicyIdNotAllowedException.newBuilder(command.getThingId())
                    .dittoHeaders(command.getDittoHeaders())
                    .build(), getSelf());
            getContext().stop(self());
        }
    }

    private void handleCreateThingWithValidPolicyIdWithoutEnforcer(final CreateThing command) {
        final Thing thing = command.getThing();
        final Optional<String> policyIdOpt = thing.getPolicyId();

        if (!policyIdOpt.isPresent()) {
            createThingWithImplicitPolicy(command);
        } else {
            // branch condition: policyIdOpt.isPresent()

            // create thing with given policy if policy exists, otherwise create policy implicitly.

            final String policyId = policyIdOpt.get();
            final Cancellable timeout = createPolicyLoadTimeout();

            getContext().become(ReceiveBuilder.create()
                    .match(SudoRetrievePolicyResponse.class, response -> {
                        // Policy with the specified ID is available
                        timeout.cancel();
                        if (command.getInitialPolicy().isPresent()) {
                            // policy exists; thing has inline policy
                            requester.tell(
                                    ThingNotCreatableException.newBuilderForPolicyExisting(thingId, policyIdOpt.get())
                                            .dittoHeaders(dittoHeaders)
                                            .build(), getSelf());
                            getContext().stop(getSelf());
                        } else {
                            // policy exists; thing has no inline policy
                            becomeCreateThingResponseAwaiting();
                            policyEnforcerShard.tell(createShardedMessageEnvelope(policyId, command), getSelf());
                        }
                    })
                    .match(PolicyNotAccessibleException.class, pnae -> {
                        // the Policy with the specified ID is not existing
                        timeout.cancel();
                        if (command.getInitialPolicy().isPresent()) {
                            // thing has inline policy; create it.
                            createThingWithImplicitPolicy(command);
                        } else {
                            // thing has no inline policy; reject the request.
                            LogUtil.enhanceLogWithCorrelationId(log, dittoHeaders.getCorrelationId());
                            log.info("The Policy with ID '{}' does not exist, the CreateThing " +
                                    "command is therefore not handled.", policyId);
                            requester.tell(ThingNotCreatableException.newBuilderForPolicyMissing(thingId, policyId)
                                    .dittoHeaders(dittoHeaders).build(), getSelf());
                            getContext().stop(getSelf());
                        }
                    })
                    .match(DittoRuntimeException.class, error -> {
                        // got error trying to retrieve the policy.
                        timeout.cancel();
                        LogUtil.enhanceLogWithCorrelationId(log, dittoHeaders.getCorrelationId());
                        log.error("Failed to retrieve Policy with ID '{}'", policyId);
                        requester.tell(error, getSelf());
                        getContext().stop(getSelf());
                    })
                    .match(AskTimeoutException.class, askTimeoutException -> {
                        LogUtil.enhanceLogWithCorrelationId(log, dittoHeaders.getCorrelationId());
                        log.error("For CreateThing <{}>, SudoRetrievePolicy <{}> timed out", thingId, policyId);
                        requester.tell(askTimeoutException, getSelf());
                        getContext().stop(getSelf());
                    })
                    .matchAny(message -> {
                        LogUtil.enhanceLogWithCorrelationId(log, dittoHeaders.getCorrelationId());
                        log.warning("Got unknown message waiting to retrieve policy <{}> to create thing <{}>: {}",
                                policyId, thingId, message);
                    })
                    .build());

            policyEnforcerShard.tell(SudoRetrievePolicy.of(policyId, dittoHeaders), getSelf());

        }
    }

    private ShardedMessageEnvelope createShardedMessageEnvelope(final String policyId, final Command<?> command) {
        return ShardedMessageEnvelope.of(policyId, command.getType(),
                command.toJson(command.getImplementedSchemaVersion(), FieldType.regularOrSpecial()),
                command.getDittoHeaders());
    }

    private void createThingWithImplicitPolicy(final CreateThing command) {
        final Policy newPolicy = extractPolicy(command, dittoHeaders);
        if (newPolicy != null) {
            final Validator policiesValidator = PoliciesValidator.newInstance(newPolicy);
            if (!policiesValidator.isValid()) {
                requester.tell(PolicyInvalidException.newBuilder(
                        org.eclipse.ditto.services.models.policies.Permission.MIN_REQUIRED_POLICY_PERMISSIONS,
                        command.getId())
                        .description(policiesValidator.getReason().orElse(null))
                        .dittoHeaders(dittoHeaders)
                        .build(), getSelf());
                getContext().stop(getSelf());
            } else {
                final CreatePolicy createPolicy = CreatePolicy.of(newPolicy, dittoHeaders);
                final String policyId = createPolicy.getId();
                becomeCreatePolicyResponseAwaiting(policyId, command);
                policyEnforcerShard.tell(createShardedMessageEnvelope(policyId, createPolicy), getSelf());
            }
        } else {
            // newPolicy == null because e. g. there is no auth subject
            requester.tell(PolicyInvalidException.newBuilder(
                    org.eclipse.ditto.services.models.policies.Permission.MIN_REQUIRED_POLICY_PERMISSIONS,
                    command.getId())
                    .dittoHeaders(dittoHeaders)
                    .build(), getSelf());
            getContext().stop(getSelf());
        }
    }

    private static Policy extractPolicy(final CreateThing command, final DittoHeaders dittoHeaders) {
        final Thing thing = command.getThing();
        final String thingId = extractThingId(thing, dittoHeaders);
        return command.getInitialPolicy()
                .map(jsonObj -> {
                    // Sets policy ID with fallback semantics:
                    // - Take the 'policyId' field in the Thing first.
                    // - If 'policyId' is not defined in the Thing, use the policy ID given by inlined policy.
                    // - If neither is defined, use the thing ID as policy ID.
                    // - Throw an exception if thingID is undefined.
                    final Optional<String> policyIdOfThing = thing.getPolicyId();
                    final boolean shouldOverride =
                            policyIdOfThing.isPresent() || !jsonObj.contains(Policy.JsonFields.ID.getPointer());
                    if (shouldOverride){
                        return jsonObj.set(Policy.JsonFields.ID, policyIdOfThing.orElse(thingId));
                    } else{
                        return jsonObj;
                    }
                })
                .map(PoliciesModelFactory::newPolicy)
                .filter(it -> it.iterator().hasNext())
                .orElse(getDefaultPolicy(dittoHeaders.getAuthorizationContext(), thingId));
    }

    private static Policy getDefaultPolicy(final AuthorizationContext authorizationContext,
            final CharSequence thingId) {

        final Subject subject = authorizationContext.getFirstAuthorizationSubject()
                .map(AuthorizationSubject::getId)
                .map(SubjectId::newInstance)
                .map(Subject::newInstance)
                .orElse(null);

        if (subject == null) {
            return null;
        } else {
            return Policy.newBuilder(thingId)
                    .forLabel(DEFAULT_POLICY_ENTRY_LABEL)
                    .setSubject(subject)
                    .setGrantedPermissions(PoliciesResourceType.thingResource("/"),
                            Permission.DEFAULT_THING_PERMISSIONS)
                    .setGrantedPermissions(PoliciesResourceType.policyResource("/"),
                            org.eclipse.ditto.services.models.policies.Permission.DEFAULT_POLICY_PERMISSIONS)
                    .setGrantedPermissions(PoliciesResourceType.messageResource("/"),
                            org.eclipse.ditto.services.models.policies.Permission.DEFAULT_POLICY_PERMISSIONS)
                    .build();
        }
    }

    private static String extractThingId(final Thing thing, final DittoHeaders headers) {
        return thing.getId().orElseThrow(() ->
                ThingIdInvalidException.newBuilder("").message("Thing ID must be present in 'Thing' payload")
                        .dittoHeaders(headers)
                        .build());
    }

    private static boolean isAclPresent(final CreateThing command) {
        return command.getThing().getAccessControlList().isPresent();
    }

    private static boolean isPolicyIdValid(final CreateThing command) {
        final Thing thing = command.getThing();
        final Optional<String> thingIdOpt = thing.getId();
        final Optional<String> policyIdOpt = thing.getPolicyId();
        final Optional<String> policyIdInPolicyOpt = command.getInitialPolicy()
                .flatMap(o -> o.getValue("policyId").filter(JsonValue::isString).map(JsonValue::asString));

        if (policyIdOpt.isPresent()) {
            return !policyIdInPolicyOpt.isPresent() || policyIdInPolicyOpt.equals(policyIdOpt);
        } else
            return !policyIdInPolicyOpt.isPresent() || policyIdInPolicyOpt.equals(thingIdOpt);
    }

    private static boolean isJsonSchemaVersionV1(final CreateThing command) {
        return command.getDittoHeaders().getSchemaVersion()
                .orElse(JsonSchemaVersion.LATEST)
                .equals(JsonSchemaVersion.V_1);
    }

    private void becomeCreatePolicyResponseAwaiting(final String policyId, final CreateThing command) {

        final Cancellable timeout = createPolicyLoadTimeout();
        getContext().become(ReceiveBuilder.create()
                .match(CreatePolicyResponse.class, response -> {
                    // policy creation is successful.
                    // replace policy in thing by ID of the created policy, then forward it to policy enforcer shard.
                    timeout.cancel();
                    becomeCreateThingResponseAwaiting();
                    final Thing thingWithPolicyId = command.getThing().toBuilder()
                            .setPolicyId(policyId)
                            .build();

                    final DittoHeaders adjustedHeaders = command.getInitialPolicy().map(PoliciesModelFactory::newPolicy)
                            .map(PolicyEnforcers::defaultEvaluator)
                            .map(eval -> eval.getSubjectIdsWithPermission(
                                    ResourceKey.newInstance(command.getResourceType(), command.getResourcePath()), READ))
                            .map(EffectedSubjectIds::getGranted)
                            .map(granted -> command.getDittoHeaders().toBuilder().readSubjects(granted).build())
                            .orElse(command.getDittoHeaders());

                    final CreateThing commandWithPolicyId =
                            CreateThing.of(thingWithPolicyId, command.getInitialPolicy().orElse(null),
                                    adjustedHeaders);
                    policyEnforcerShard.tell(createShardedMessageEnvelope(policyId, commandWithPolicyId), getSelf());
                })
                .match(PolicyNotAccessibleException.class, pnae -> {
                    // it was not allowed to access the policy; someone else created the policy already.
                    // we have a race condition. should reject the request.
                    timeout.cancel();
                    handlePolicyCreationFailure(policyId, command);
                })
                .match(PolicyConflictException.class, pce -> {
                    // the Policy already exists, this is a race condition.
                    // someone created the policy before we could create it.
                    // the thing should not be governed by a random policy.
                    timeout.cancel();
                    handlePolicyCreationFailure(policyId, command);
                })
                .match(PolicyException.class, pre -> {
                    timeout.cancel();
                    requester.forward(pre, getContext());
                    getContext().stop(getSelf());
                })
                .match(DittoRuntimeException.class, cre -> {
                    timeout.cancel();
                    LogUtil.enhanceLogWithCorrelationId(log, cre.getDittoHeaders().getCorrelationId());
                    log.info("Got an unexpected DittoRuntimeException while trying to create a Thing and Policy: {}",
                            cre);
                    requester.tell(cre, getSelf());
                    getContext().stop(getSelf());
                })
                .match(AskTimeoutException.class, e -> {
                    LogUtil.enhanceLogWithCorrelationId(log, dittoHeaders.getCorrelationId());
                    log.error("Timeout exception while trying to create the corresponding policy");
                    requester
                            .tell(PolicyUnavailableException.newBuilder(thingId).dittoHeaders(dittoHeaders).build(),
                                    getSelf());
                    getContext().stop(getSelf());
                })
                .matchAny(this::handleUnknownMessage)
                .build());
    }

    private void handlePolicyCreationFailure(final String policyId, final CreateThing command) {

        LogUtil.enhanceLogWithCorrelationId(log, dittoHeaders.getCorrelationId());
        // that means we cannot create the Thing implicitly
        log.info("The Policy with ID '{}' is already existing, the CreateThing " +
                "command which would have created an implicit Policy for the Thing with ID '{}' " +
                "is therefore not handled", policyId, command.getThingId());
        requester.tell(
                ThingNotCreatableException.newBuilderForPolicyExisting(thingId, policyId)
                        .dittoHeaders(dittoHeaders)
                        .build(),
                getSelf());
        getContext().stop(getSelf());
    }

    private Cancellable createPolicyLoadTimeout() {
        return getContext().system().scheduler().scheduleOnce(ASK_TIMEOUT.duration(), getSelf(),
                new AskTimeoutException("The policy could not be loaded within a the specified time frame"),
                getContext().dispatcher(), null);
    }

    private void becomeCreateThingResponseAwaiting() {
        final Cancellable timeout = getContext().system().scheduler().scheduleOnce(ASK_TIMEOUT.duration(), getSelf(),
                new AskTimeoutException("The thing could not be loaded within a the specified time frame"),
                getContext().dispatcher(), null);

        getContext().become(ReceiveBuilder.create()
                .match(CreateThingResponse.class, response -> {
                    timeout.cancel();
                    final ThingBuilder.FromCopy thingBuilder = response.getThingCreated().get().toBuilder();
                    requester.tell(CreateThingResponse.of(thingBuilder.build(), dittoHeaders), getSelf());
                    getContext().stop(getSelf());
                })
                .match(ThingNotModifiableException.class, e -> {
                    timeout.cancel();
                    requester.forward(e, getContext());
                    getContext().stop(getSelf());
                })
                .match(ThingErrorResponse.class, tre -> {
                    timeout.cancel();
                    requester.forward(tre, getContext());
                    getContext().stop(getSelf());
                })
                .match(AskTimeoutException.class, e -> {
                    log.error("Timeout exception while trying to create the thing");
                    requester.tell(ThingUnavailableException.newBuilder(thingId).dittoHeaders(dittoHeaders).build(),
                            getSelf());
                    getContext().stop(getSelf());
                })
                .match(DittoRuntimeException.class, cre -> {
                    timeout.cancel();
                    log.info("There occurred a DittoRuntimeException while trying to aggregate Thing and Policy: {}",
                            cre);
                    requester.tell(cre, getSelf());
                    getContext().stop(getSelf());
                })
                .matchAny(this::handleUnknownMessage)
                .build());
    }

}
