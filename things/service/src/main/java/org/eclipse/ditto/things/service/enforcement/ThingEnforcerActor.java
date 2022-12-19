/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.things.service.enforcement;

import static org.eclipse.ditto.policies.api.Permission.MIN_REQUIRED_POLICY_PERMISSIONS;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.namespaces.NamespaceBlockedException;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.internal.utils.cacheloaders.AskWithRetry;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.policies.api.PoliciesValidator;
import org.eclipse.ditto.policies.enforcement.AbstractEnforcementReloaded;
import org.eclipse.ditto.policies.enforcement.AbstractPolicyLoadingEnforcerActor;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProvider;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyErrorResponse;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyConflictException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyUnavailableException;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicyResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicy;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyResponse;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;
import org.eclipse.ditto.things.model.signals.commands.exceptions.PolicyInvalidException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotCreatableException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotModifiableException;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommand;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.pattern.AskTimeoutException;
import akka.pattern.Patterns;

/**
 * Enforcer responsible for enforcing {@link ThingCommand}s and filtering {@link ThingCommandResponse}s utilizing the
 * {@link ThingEnforcement}.
 */
public final class ThingEnforcerActor
        extends AbstractPolicyLoadingEnforcerActor<ThingId, Signal<?>, CommandResponse<?>, ThingEnforcement> {

    private static final String ENFORCEMENT_DISPATCHER = "enforcement-dispatcher";
    /**
     * Label of default policy entry in default policy.
     */
    private static final String DEFAULT_POLICY_ENTRY_LABEL = "DEFAULT";
    private final PolicyIdReferencePlaceholderResolver policyIdReferencePlaceholderResolver;
    private final ActorRef policiesShardRegion;
    private final AskWithRetryConfig askWithRetryConfig;

    @SuppressWarnings("unused")
    private ThingEnforcerActor(final ThingId thingId,
            final ThingEnforcement thingEnforcement,
            final AskWithRetryConfig askWithRetryConfig,
            final ActorRef policiesShardRegion,
            final ActorRef thingsShardRegion,
            final PolicyEnforcerProvider policyEnforcerProvider) {

        super(thingId, thingEnforcement, policyEnforcerProvider);

        this.policiesShardRegion = policiesShardRegion;
        this.askWithRetryConfig = askWithRetryConfig;
        final ActorSystem system = context().system();
        policyIdReferencePlaceholderResolver = PolicyIdReferencePlaceholderResolver.of(
                thingsShardRegion, askWithRetryConfig, system);
    }

    /**
     * Creates Akka configuration object Props for this Actor.
     *
     * @param thingId the ThingId this enforcer actor is responsible for.
     * @param thingEnforcement the thing enforcement logic to apply in the enforcer.
     * @param askWithRetryConfig used to configure retry mechanism policy loading.
     * @param policiesShardRegion used to create the policy when handling create thing commands.
     * @param thingsShardRegion used to resolve policy placeholder.
     * @param policyEnforcerProvider used to load the policy enforcer.
     * @return the {@link Props} to create this actor.
     */
    public static Props props(final ThingId thingId,
            final ThingEnforcement thingEnforcement,
            final AskWithRetryConfig askWithRetryConfig,
            final ActorRef policiesShardRegion,
            final ActorRef thingsShardRegion,
            final PolicyEnforcerProvider policyEnforcerProvider) {

        return Props.create(ThingEnforcerActor.class, thingId, thingEnforcement, askWithRetryConfig,
                policiesShardRegion, thingsShardRegion, policyEnforcerProvider).withDispatcher(ENFORCEMENT_DISPATCHER);
    }

    @Override
    protected CompletionStage<Optional<PolicyEnforcer>> loadPolicyEnforcer(final Signal<?> signal) {
        if (signal instanceof CreateThing createThing && !Signal.isChannelLive(createThing)) {
            return loadPolicyEnforcerForCreateThing(createThing);
        } else {
            return providePolicyIdForEnforcement(signal)
                    .thenCompose(policyId -> providePolicyEnforcer(policyId)
                            .thenCompose(policyEnforcer -> {
                                if (policyId != null && policyEnforcer.isEmpty() &&
                                        signal instanceof ThingCommand<?> thingCommand) {
                                    return getDreForMissingPolicyEnforcer(thingCommand, policyId)
                                            .thenCompose(CompletableFuture::failedStage);
                                } else {
                                    return CompletableFuture.completedStage(policyEnforcer);
                                }
                            }));
        }
    }

    private CompletionStage<DittoRuntimeException> getDreForMissingPolicyEnforcer(final ThingCommand<?> thingCommand,
            final PolicyId policyId) {

        return doesThingExist().thenApply(thingExists -> {
            if (thingExists) {
                return errorForExistingThingWithDeletedPolicy(thingCommand, policyId);
            } else {
                return ThingNotAccessibleException.newBuilder(entityId)
                        .dittoHeaders(thingCommand.getDittoHeaders())
                        .build();
            }
        });
    }

    /**
     * Create error for commands to an existing thing whose policy is deleted.
     *
     * @param thingCommand the triggering command.
     * @param policyId ID of the deleted policy.
     * @return an appropriate error.
     */
    private static DittoRuntimeException errorForExistingThingWithDeletedPolicy(final ThingCommand<?> thingCommand,
            final PolicyId policyId) {

        final ThingId thingId = thingCommand.getEntityId();

        final var message = String.format(
                "The Thing with ID '%s' could not be accessed as its Policy with ID '%s' is not or no longer existing.",
                thingId, policyId);
        final var description = String.format(
                "Recreate/create the Policy with ID '%s' in order to get access to the Thing again.",
                policyId);

        if (thingCommand instanceof ThingModifyCommand) {
            return ThingNotModifiableException.newBuilder(thingId)
                    .message(message)
                    .description(description)
                    .dittoHeaders(thingCommand.getDittoHeaders())
                    .build();
        } else {
            return ThingNotAccessibleException.newBuilder(thingId)
                    .message(message)
                    .description(description)
                    .dittoHeaders(thingCommand.getDittoHeaders())
                    .build();
        }
    }

    private CompletionStage<Optional<PolicyEnforcer>> loadPolicyEnforcerForCreateThing(final CreateThing createThing) {
        final Optional<String> policyIdOrPlaceholder = createThing.getPolicyIdOrPlaceholder();
        final Optional<JsonObject> initialPolicyJson = createThing.getInitialPolicy();
        final CompletionStage<Policy> policyCs;
        if (policyIdOrPlaceholder.isPresent()) {
            // A policy should be copied => build a copy of policy and return it as enforcer
            final Thing thing = createThing.getThing();
            final PolicyId policyIdToBe = thing.getPolicyId()
                    .orElseGet(() -> thing.getEntityId().map(PolicyId::of).orElseThrow());
            final DittoHeaders dittoHeaders = createThing.getDittoHeaders();
            policyCs = getCopiedPolicy(policyIdOrPlaceholder.get(), dittoHeaders, policyIdToBe)
                    .thenCompose(copiedPolicy -> createPolicy(copiedPolicy, createThing));
        } else if (initialPolicyJson.isPresent()) {
            // An initial policy was defined => build policy and return it as enforcer
            final Policy initialPolicy = getInitialPolicy(createThing, initialPolicyJson.get());
            policyCs = createPolicy(initialPolicy, createThing);
        } else if (createThing.getThing().getPolicyId().isPresent()) {
            // An existing policy should be reused => retrieve policy and return it as enforcer
            final PolicyId referencedPolicyId = createThing.getThing().getPolicyId().get();
            policyCs = retrievePolicyWithEnforcement(createThing.getDittoHeaders(), referencedPolicyId);
        } else {
            // No policy to copy defined, no existing policy referenced and no initial policy present => build default policy and return it as enforcer
            final Policy defaultPolicy = getDefaultPolicy(createThing.getDittoHeaders(), createThing.getEntityId());
            policyCs = createPolicy(defaultPolicy, createThing);
        }
        return policyCs.thenCompose(policy -> providePolicyEnforcer(policy.getEntityId().orElse(null)));
    }

    private CompletionStage<Policy> getCopiedPolicy(final String policyIdOrPlaceholder,
            final DittoHeaders dittoHeaders, final PolicyId policyIdForCopiedPolicy) {

        return ReferencePlaceholder.fromCharSequence(policyIdOrPlaceholder)
                .map(referencePlaceholder -> {
                    log.withCorrelationId(dittoHeaders)
                            .debug("CreateThing command contains a reference placeholder for the policy it wants to copy: {}",
                                    referencePlaceholder);
                    final var dittoHeadersWithoutPreconditionHeaders = dittoHeaders.toBuilder()
                            .removePreconditionHeaders()
                            .responseRequired(true)
                            .build();
                    return policyIdReferencePlaceholderResolver.resolve(referencePlaceholder,
                                    dittoHeadersWithoutPreconditionHeaders)
                            .thenApply(PolicyId::of);
                })
                .orElseGet(() -> CompletableFuture.completedStage(PolicyId.of(policyIdOrPlaceholder)))
                .thenCompose(resolvedPolicyId -> retrievePolicyWithEnforcement(dittoHeaders, resolvedPolicyId)
                        .thenApply(Policy::toBuilder)
                        .thenApply(policyBuilder -> policyBuilder.setId(policyIdForCopiedPolicy)
                                .build()));
    }

    private CompletionStage<Policy> retrievePolicyWithEnforcement(final DittoHeaders dittoHeaders,
            final PolicyId policyId) {

        final var adjustedHeaders = dittoHeaders.toBuilder()
                .removePreconditionHeaders()
                .responseRequired(true)
                .build();

        return AskWithRetry.askWithRetry(policiesShardRegion,
                RetrievePolicy.of(policyId, adjustedHeaders),
                askWithRetryConfig,
                context().system(),
                response -> {
                    if (response instanceof RetrievePolicyResponse rpr) {
                        return rpr.getPolicy();
                    } else if (response instanceof PolicyErrorResponse per) {
                        throw per.getDittoRuntimeException();
                    } else if (response instanceof DittoRuntimeException dre) {
                        throw dre;
                    } else {
                        log.withCorrelationId(adjustedHeaders)
                                .error("Got an unexpected response while retrieving a Policy that should be copied" +
                                        " during Thing creation: {}", response);
                        throw DittoInternalErrorException.newBuilder().build();
                    }
                });
    }

    private Policy getInitialPolicy(final CreateThing createThing, final JsonObject inlinedPolicy) {
        try {
            final var thing = createThing.getThing();
            final JsonObjectBuilder policyJsonBuilder = inlinedPolicy.toBuilder();
            if (thing.getPolicyId().isPresent() || !inlinedPolicy.contains(Policy.JsonFields.ID.getPointer())) {
                final String policyId = thing.getPolicyId()
                        .map(String::valueOf)
                        .orElse(createThing.getEntityId().toString());
                policyJsonBuilder.set(Policy.JsonFields.ID, policyId);
            }
            final var initialPolicy = PoliciesModelFactory.newPolicy(policyJsonBuilder.build());
            final var policiesValidator = PoliciesValidator.newInstance(initialPolicy);
            if (policiesValidator.isValid()) {
                return initialPolicy;
            } else {
                throw PolicyInvalidException.newBuilder(MIN_REQUIRED_POLICY_PERMISSIONS, createThing.getEntityId())
                        .dittoHeaders(createThing.getDittoHeaders())
                        .build();
            }
        } catch (final JsonRuntimeException | DittoJsonException e) {
            final var thingId = createThing.getEntityId();
            throw PolicyInvalidException.newBuilderForCause(e, thingId)
                    .dittoHeaders(createThing.getDittoHeaders())
                    .build();
        } catch (final DittoRuntimeException e) {
            final var dittoHeaders = createThing.getDittoHeaders();
            throw e.setDittoHeaders(dittoHeaders);
        }
    }

    private static Policy getDefaultPolicy(final DittoHeaders dittoHeaders, final ThingId thingId) {

        final Subject subject = dittoHeaders.getAuthorizationContext().getFirstAuthorizationSubject()
                .map(AuthorizationSubject::getId)
                .map(SubjectId::newInstance)
                .map(Subject::newInstance)
                .orElseThrow(() -> {
                    final var message = String.format("The Thing with ID '%s' could not be created with " +
                            "implicit Policy because no authorization subject is present.", thingId);
                    throw ThingNotCreatableException.newBuilderForPolicyMissing(thingId, PolicyId.of(thingId))
                            .message(message)
                            .description(() -> null)
                            .dittoHeaders(dittoHeaders)
                            .build();
                });

        return Policy.newBuilder(PolicyId.of(thingId))
                .forLabel(DEFAULT_POLICY_ENTRY_LABEL)
                .setSubject(subject)
                .setGrantedPermissions(PoliciesResourceType.thingResource("/"),
                        org.eclipse.ditto.things.api.Permission.DEFAULT_THING_PERMISSIONS)
                .setGrantedPermissions(PoliciesResourceType.policyResource("/"),
                        org.eclipse.ditto.policies.api.Permission.DEFAULT_POLICY_PERMISSIONS)
                .setGrantedPermissions(PoliciesResourceType.messageResource("/"),
                        org.eclipse.ditto.policies.api.Permission.DEFAULT_POLICY_PERMISSIONS)
                .build();
    }

    private CompletionStage<Policy> createPolicy(final Policy policy, final CreateThing createThing) {
        final DittoHeaders dittoHeaders = createThing.getDittoHeaders();
        final var dittoHeadersForCreatePolicy = DittoHeaders.newBuilder(dittoHeaders)
                .removePreconditionHeaders()
                .responseRequired(true)
                .build();

        final var createPolicy = CreatePolicy.of(policy, dittoHeadersForCreatePolicy);
        return Patterns.ask(policiesShardRegion, createPolicy, askWithRetryConfig.getAskTimeout()
                        // don't retry creating policy (not idempotent!) - but increase default timeout for doing so
                        .multipliedBy(5L))
                .thenApply(policyResponse -> handleCreatePolicyResponse(createPolicy, policyResponse, createThing))
                .exceptionally(throwable -> {
                    if (throwable instanceof AskTimeoutException) {
                        throw PolicyUnavailableException.newBuilder(createPolicy.getEntityId())
                                .dittoHeaders(createThing.getDittoHeaders())
                                .build();
                    }
                    throw AbstractEnforcementReloaded.reportError(
                            String.format("creating initial policy during creation of Thing <%s>",
                                    createThing.getEntityId()), throwable, createThing.getDittoHeaders());
                });
    }

    private Policy handleCreatePolicyResponse(final CreatePolicy createPolicy, final Object policyResponse,
            final CreateThing createThing) {

        if (policyResponse instanceof CreatePolicyResponse createPolicyResponse) {
            return createPolicyResponse.getPolicyCreated().orElseThrow();
        } else {
            if (shouldReportInitialPolicyCreationFailure(policyResponse)) {
                throw reportInitialPolicyCreationFailure(createPolicy.getEntityId(), createThing);
            } else if (isAskTimeoutException(policyResponse, null)) {
                throw PolicyUnavailableException.newBuilder(createPolicy.getEntityId())
                        .dittoHeaders(createThing.getDittoHeaders())
                        .build();
            } else {
                final var hint = String.format("creating initial policy during creation of Thing <%s>",
                        createThing.getEntityId());
                throw AbstractEnforcementReloaded.reportErrorOrResponse(hint, policyResponse, null,
                        createThing.getDittoHeaders());
            }
        }
    }

    /**
     * Check whether response or error from a future is {@code AskTimeoutException}.
     *
     * @param response response from a future.
     * @param error error thrown in a future.
     * @return whether either is {@code AskTimeoutException}.
     */
    private static boolean isAskTimeoutException(final Object response, @Nullable final Throwable error) {
        return error instanceof AskTimeoutException || response instanceof AskTimeoutException;
    }

    private static boolean shouldReportInitialPolicyCreationFailure(final Object policyResponse) {
        return policyResponse instanceof PolicyConflictException ||
                policyResponse instanceof PolicyNotAccessibleException ||
                policyResponse instanceof NamespaceBlockedException;
    }

    private ThingNotCreatableException reportInitialPolicyCreationFailure(final PolicyId policyId,
            final CreateThing command) {

        log.withCorrelationId(command)
                .info("Failed to create Policy with ID <{}> because it already exists." +
                        " The CreateThing command which would have created a Policy for the Thing with ID <{}>" +
                        " is therefore not handled.", policyId, command.getEntityId());
        return ThingNotCreatableException.newBuilderForPolicyExisting(command.getEntityId(), policyId)
                .dittoHeaders(command.getDittoHeaders())
                .build();
    }

    @Override
    protected CompletionStage<PolicyId> providePolicyIdForEnforcement(final Signal<?> signal) {
        return Patterns.ask(getContext().getParent(), SudoRetrieveThing.of(entityId,
                        JsonFieldSelector.newInstance("policyId"),
                        DittoHeaders.newBuilder()
                                .correlationId("sudoRetrieveThingFromThingEnforcerActor-" + UUID.randomUUID())
                                .putHeader(DittoHeaderDefinition.DITTO_RETRIEVE_DELETED.getKey(),
                                        Boolean.TRUE.toString())
                                .build()
                ), DEFAULT_LOCAL_ASK_TIMEOUT
        ).thenApply(response -> extractPolicyIdFromSudoRetrieveThingResponse(response).orElse(null));
    }

    private CompletionStage<Boolean> doesThingExist() {
        return Patterns.ask(getContext().getParent(), SudoRetrieveThing.of(entityId,
                        JsonFieldSelector.newInstance("policyId"),
                        DittoHeaders.newBuilder()
                                .correlationId("sudoRetrieveThingFromThingEnforcerActor-" + UUID.randomUUID())
                                .build()
                ), DEFAULT_LOCAL_ASK_TIMEOUT
        ).thenApply(response -> {
            if (response instanceof SudoRetrieveThingResponse) {
                return true;
            } else if (response instanceof ThingNotAccessibleException) {
                return false;
            } else {
                throw new IllegalStateException("expected SudoRetrieveThingResponse, got: " + response);
            }
        });
    }

    /**
     * Extracts a {@link PolicyId} from the passed {@code response} which is expected to be a
     * {@link SudoRetrieveThingResponse}. A {@code response} being a {@link ThingNotAccessibleException} leads to an
     * empty Optional.
     *
     * @param response the response to extract the PolicyId from.
     * @return the optional extracted PolicyId.
     */
    static Optional<PolicyId> extractPolicyIdFromSudoRetrieveThingResponse(final Object response) {
        if (response instanceof SudoRetrieveThingResponse sudoRetrieveThingResponse) {
            return sudoRetrieveThingResponse.getThing().getPolicyId();
        } else if (response instanceof ThingNotAccessibleException) {
            return Optional.empty();
        } else {
            throw new IllegalStateException("expected SudoRetrieveThingResponse, got: " + response);
        }
    }

}
