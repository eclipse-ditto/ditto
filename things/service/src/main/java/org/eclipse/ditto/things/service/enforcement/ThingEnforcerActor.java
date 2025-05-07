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

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.pattern.AskTimeoutException;
import org.apache.pekko.pattern.Patterns;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.contenttype.ContentType;
import org.eclipse.ditto.base.model.namespaces.NamespaceBlockedException;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.internal.utils.cacheloaders.AskWithRetry;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
import org.eclipse.ditto.internal.utils.tracing.span.SpanOperationName;
import org.eclipse.ditto.internal.utils.tracing.span.StartedSpan;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.messages.model.MessageDirection;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommandResponse;
import org.eclipse.ditto.messages.model.signals.commands.SendFeatureMessage;
import org.eclipse.ditto.messages.model.signals.commands.SendFeatureMessageResponse;
import org.eclipse.ditto.messages.model.signals.commands.SendThingMessage;
import org.eclipse.ditto.messages.model.signals.commands.SendThingMessageResponse;
import org.eclipse.ditto.policies.api.PoliciesValidator;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.enforcement.AbstractEnforcementReloaded;
import org.eclipse.ditto.policies.enforcement.AbstractPolicyLoadingEnforcerActor;
import org.eclipse.ditto.policies.enforcement.Invalidatable;
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
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyUnavailableException;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicyResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicy;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyResponse;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;
import org.eclipse.ditto.things.model.signals.commands.exceptions.PolicyInvalidException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotCreatableException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotModifiableException;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommand;
import org.eclipse.ditto.things.service.common.config.DittoThingsConfig;
import org.eclipse.ditto.things.service.common.config.PreDefinedExtraFieldsConfig;
import org.eclipse.ditto.things.service.persistence.actors.enrichment.EnrichSignalWithPreDefinedExtraFields;
import org.eclipse.ditto.things.service.persistence.actors.enrichment.EnrichSignalWithPreDefinedExtraFieldsResponse;
import org.eclipse.ditto.wot.api.validator.WotThingModelValidator;
import org.eclipse.ditto.wot.integration.DittoWotIntegration;
import org.eclipse.ditto.wot.validation.WotThingModelPayloadValidationException;

/**
 * Enforcer responsible for enforcing {@link ThingCommand}s and filtering {@link ThingCommandResponse}s utilizing the
 * {@link ThingEnforcement}.
 */
public final class ThingEnforcerActor
        extends AbstractPolicyLoadingEnforcerActor<ThingId, Signal<?>, CommandResponse<?>, ThingEnforcement> {

    /**
     * Label of default policy entry in default policy.
     */
    private static final String DEFAULT_POLICY_ENTRY_LABEL = "DEFAULT";

    private final PolicyIdReferencePlaceholderResolver policyIdReferencePlaceholderResolver;
    private final ActorRef policiesShardRegion;
    private final DittoThingsConfig thingsConfig;
    private final AskWithRetryConfig askWithRetryConfig;
    private final WotThingModelValidator thingModelValidator;
    private final Executor wotValidationExecutor;

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
        thingsConfig = DittoThingsConfig.of(
                DefaultScopedConfig.dittoScoped(system.settings().config())
        );
        policyIdReferencePlaceholderResolver = PolicyIdReferencePlaceholderResolver.of(
                thingsShardRegion, askWithRetryConfig, system);

        final DittoWotIntegration wotIntegration = DittoWotIntegration.get(system);
        thingModelValidator = wotIntegration.getWotThingModelValidator();
        wotValidationExecutor = getContext().getSystem().dispatchers().lookup("wot-dispatcher");
    }

    /**
     * Creates Pekko configuration object Props for this Actor.
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
                    .thenComposeAsync(policyId -> providePolicyEnforcer(policyId)
                            .thenComposeAsync(policyEnforcer -> {
                                if (policyId != null && policyEnforcer.isEmpty() &&
                                        signal instanceof ThingCommand<?> thingCommand) {
                                    return getDreForMissingPolicyEnforcer(thingCommand, policyId)
                                            .thenComposeAsync(CompletableFuture::failedStage, enforcementExecutor);
                                } else {
                                    return CompletableFuture.completedFuture(policyEnforcer);
                                }
                            }, enforcementExecutor), enforcementExecutor);
        }
    }

    private CompletionStage<DittoRuntimeException> getDreForMissingPolicyEnforcer(final ThingCommand<?> thingCommand,
            final PolicyId policyId) {

        return doesThingExist().thenApply(thingExists -> {
            if (Boolean.TRUE.equals(thingExists)) {
                return errorForExistingThingWithDeletedPolicy(thingCommand, policyId);
            } else {
                return ThingNotAccessibleException.newBuilder(entityId)
                        .dittoHeaders(thingCommand.getDittoHeaders())
                        .build();
            }
        });
    }

    @Override
    protected CompletionStage<Signal<?>> performWotBasedSignalValidation(final Signal<?> signal
    ) {
        if (signal instanceof MessageCommand<?, ?> messageCommand) {
            final var startedSpan = DittoTracing.newPreparedSpan(
                            messageCommand.getDittoHeaders(),
                            SpanOperationName.of("enforce_wot_model message")
                    )
                    .start();
            return performWotBasedMessageCommandValidation(messageCommand.setDittoHeaders(
                    DittoHeaders.of(startedSpan.propagateContext(messageCommand.getDittoHeaders()))
                            .toBuilder()
                            .putHeader(DittoHeaderDefinition.ENTITY_ID.getKey(),
                                    entityId.getEntityType() + ":" + entityId)
                            .build()
            )).whenComplete((result, error) -> {
                        if (error instanceof DittoRuntimeException dre) {
                            startedSpan.tagAsFailed(dre.toString());
                        } else if (null != error) {
                            startedSpan.tagAsFailed(error);
                        }
                        startedSpan.finish();
                    })
                    .thenApply(Function.identity());
        } else if (signal instanceof MessageCommandResponse<?, ?> messageCommandResponse) {
            return doPerformWotBasedMessageCommandResponseValidation(messageCommandResponse)
                    .thenApply(Function.identity());
        } else {
            return super.performWotBasedSignalValidation(signal);
        }
    }

    @Override
    protected CompletionStage<Signal<?>> enrichWithPreDefinedExtraFields(final Signal<?> signal) {
        if (signal instanceof MessageCommand<?, ?> messageCommand) {
            return enrichSignalWithPredefinedFieldsAtPersistenceActor(messageCommand)
                    .thenApply(opt -> opt.orElse(messageCommand));
        } else {
            // events are enriched directly in the persistence actor:
            return super.enrichWithPreDefinedExtraFields(signal);
        }
    }

    private CompletionStage<Optional<Signal<?>>> enrichSignalWithPredefinedFieldsAtPersistenceActor(
            final Signal<?> signal
    ) {
        final List<PreDefinedExtraFieldsConfig> predefinedExtraFieldsConfigs =
                thingsConfig.getThingConfig().getEventConfig().getPredefinedExtraFieldsConfigs();
        if (!predefinedExtraFieldsConfigs.isEmpty() &&
                predefinedExtraFieldsConfigs.stream()
                        .anyMatch(conf -> conf.getNamespace().isEmpty() ||
                                conf.getNamespace()
                                        .stream()
                                        .anyMatch(pattern ->
                                                pattern.matcher(entityId.getNamespace()).matches()
                                        )
                        )
        ) {
            return Patterns.ask(getContext().getParent(),
                    new EnrichSignalWithPreDefinedExtraFields(signal), DEFAULT_LOCAL_ASK_TIMEOUT
                    // it might also take longer, as resolving a policy may be involved - in that case, the optimization is simply not done
            ).handle((response, t) -> {
                if (response instanceof EnrichSignalWithPreDefinedExtraFieldsResponse(Signal<?> enrichedSignal)) {
                    return Optional.of(enrichedSignal);
                } else if (response instanceof ThingNotAccessibleException) {
                    return Optional.empty();
                } else if (t != null) {
                    log.withCorrelationId(signal)
                            .warning(t, "expected EnrichSignalWithPreDefinedExtraFieldsResponse, " +
                                    "got throwable: <{}: {}>", t.getClass().getSimpleName(), t.getMessage());
                    return Optional.empty();
                } else {
                    log.withCorrelationId(signal)
                            .error("expected EnrichSignalWithPreDefinedExtraFieldsResponse, got: {}", response);
                    return Optional.empty();
                }
            });
        } else {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    @Override
    protected CompletionStage<CommandResponse<?>> performWotBasedResponseValidation(
            final CommandResponse<?> filteredResponse
    ) {
        if (filteredResponse instanceof MessageCommandResponse<?, ?> messageCommandResponse) {
            return doPerformWotBasedMessageCommandResponseValidation(messageCommandResponse)
                    .thenApply(Function.identity());
        } else {
            return super.performWotBasedResponseValidation(filteredResponse);
        }
    }

    private CompletionStage<MessageCommandResponse<?, ?>> doPerformWotBasedMessageCommandResponseValidation(
            final MessageCommandResponse<?, ?> messageCommandResponse
    ) {
        final var startedSpan = DittoTracing.newPreparedSpan(
                        messageCommandResponse.getDittoHeaders(),
                        SpanOperationName.of("enforce_wot_model message_response")
                )
                .start();
        return performWotBasedMessageCommandResponseValidation(
                messageCommandResponse.setDittoHeaders(
                        DittoHeaders.of(startedSpan.propagateContext(messageCommandResponse.getDittoHeaders()))
                                .toBuilder()
                                .putHeader(DittoHeaderDefinition.ENTITY_ID.getKey(),
                                        entityId.getEntityType() + ":" + entityId)
                                .build()
                )
        )
                .whenComplete((result, error) -> {
                    if (error instanceof DittoRuntimeException dre) {
                        startedSpan.tagAsFailed(dre.toString());
                    } else if (null != error) {
                        startedSpan.tagAsFailed(error);
                    }
                    startedSpan.finish();
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
                    .thenComposeAsync(copiedPolicy -> createPolicy(copiedPolicy, createThing), enforcementExecutor);
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
        final String correlationId =
                createThing.getDittoHeaders().getCorrelationId().orElse("unexpected:" + UUID.randomUUID());
        return policyCs
                .thenComposeAsync(policy -> {
                    if (policyEnforcerProvider instanceof Invalidatable invalidatable &&
                            policy.getEntityId().isPresent() && policy.getRevision().isPresent()) {
                        return invalidatable.invalidate(PolicyTag.of(policy.getEntityId().get(),
                                        policy.getRevision().get().toLong()), correlationId, askWithRetryConfig.getAskTimeout())
                                .thenApply(bool -> {
                                    log.withCorrelationId(createThing)
                                            .debug("PolicyEnforcerCache invalidated. Previous entity was present: {}",
                                                    bool);
                                    return policy;
                                });
                    }
                    return CompletableFuture.completedFuture(policy);
                }, enforcementExecutor)
                .thenComposeAsync(policy -> providePolicyEnforcer(policy.getEntityId().orElse(null)),
                        enforcementExecutor);
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
                .orElseGet(() -> CompletableFuture.completedFuture(PolicyId.of(policyIdOrPlaceholder)))
                .thenComposeAsync(resolvedPolicyId -> retrievePolicyWithEnforcement(dittoHeaders, resolvedPolicyId)
                        .thenApply(Policy::toBuilder)
                        .thenApply(policyBuilder -> policyBuilder.setId(policyIdForCopiedPolicy)
                                .build()),
                        enforcementExecutor
                );
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
                    return ThingNotCreatableException.newBuilderForPolicyMissing(thingId, PolicyId.of(thingId))
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
            createPolicyResponse.getPolicyCreated()
                    .ifPresent(policy -> getContext().getParent().tell(new ThingPolicyCreated(createThing.getEntityId(),
                            createPolicyResponse.getEntityId(), createPolicy.getDittoHeaders()), getSelf()));
            return createPolicyResponse.getPolicyCreated().orElseThrow();
        } else if (isAskTimeoutException(policyResponse, null)) {
            throw PolicyUnavailableException.newBuilder(createPolicy.getEntityId())
                    .dittoHeaders(createThing.getDittoHeaders())
                    .build();
        } else if (policyResponse instanceof DittoRuntimeException policyException) {
            throw reportInitialPolicyCreationFailure(createPolicy.getEntityId(), createThing, policyException);
        } else {
            final var hint = String.format("creating initial policy during creation of Thing <%s>",
                    createThing.getEntityId());
            throw AbstractEnforcementReloaded.reportErrorOrResponse(hint, policyResponse, null,
                    createThing.getDittoHeaders());
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

    private ThingNotCreatableException reportInitialPolicyCreationFailure(final PolicyId policyId,
            final CreateThing command, final DittoRuntimeException policyException) {

        log.withCorrelationId(command)
                .info("Failed to create Policy with ID <{}> due to: <{}: {}>." +
                                " The CreateThing command which would have created a Policy for the Thing with ID <{}>" +
                                " is therefore not handled.", policyId,
                        policyException.getClass().getSimpleName(), policyException.getMessage(),
                        command.getEntityId()
                );
        if (policyException instanceof PolicyConflictException) {
            return ThingNotCreatableException.newBuilderForPolicyExisting(command.getEntityId(), policyId)
                    .dittoHeaders(command.getDittoHeaders())
                    .build();
        } else if (policyException instanceof NamespaceBlockedException) {
            return ThingNotCreatableException.newBuilderForPolicyMissing(command.getEntityId(), policyId)
                    .dittoHeaders(command.getDittoHeaders())
                    .build();
        } else {
            return ThingNotCreatableException.newBuilderForOtherReason(policyException.getHttpStatus(),
                            command.getEntityId(), policyId,
                            policyException.getMessage())
                    .dittoHeaders(command.getDittoHeaders())
                    .build();
        }
    }

    @Override
    protected CompletionStage<PolicyId> providePolicyIdForEnforcement(final Signal<?> signal) {
        final var startedSpan = DittoTracing.newPreparedSpan(
                        signal.getDittoHeaders(),
                        SpanOperationName.of("sudo_retrieve_thing")
                )
                .correlationId(signal.getDittoHeaders().getCorrelationId().orElse(null))
                .start();
        final SudoRetrieveThing sudoRetrieveThing = SudoRetrieveThing.of(entityId,
                JsonFieldSelector.newInstance("policyId"),
                DittoHeaders.of(startedSpan.propagateContext(DittoHeaders.newBuilder()
                        .correlationId("sudoRetrieveThingFromThingEnforcerActor-" + UUID.randomUUID())
                        .putHeader(DittoHeaderDefinition.DITTO_RETRIEVE_DELETED.getKey(),
                                Boolean.TRUE.toString())
                        .build()))
        );
        return Patterns.ask(getContext().getParent(), sudoRetrieveThing, DEFAULT_LOCAL_ASK_TIMEOUT)
                .thenApply(
                        response -> extractPolicyIdFromSudoRetrieveThingResponse(response, startedSpan).orElse(null));
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

    private CompletionStage<MessageCommand<?, ?>> performWotBasedMessageCommandValidation(
            final MessageCommand<?, ?> messageCommand
    ) {
        @SuppressWarnings("unchecked") final Message<JsonValue> message =
                ((MessageCommand<JsonValue, ?>) messageCommand)
                        .getMessage();

        // lazily only supply JsonValue if validation is enabled for the message:
        final Supplier<JsonValue> messageCommandPayloadSupplier = () -> {
            if (message.getPayload().isPresent() && !isJsonMessageContent(message)) {
                throw WotThingModelPayloadValidationException
                                .newBuilder("Could not validate non-JSON message content type <" +
                                        message.getContentType().orElse("?") + "> for message subject " +
                                        "<" + message.getSubject() + ">"
                                )
                                .dittoHeaders(messageCommand.getDittoHeaders())
                                .build();
            }

            return message.getPayload().orElse(null);
        };

        final MessageDirection messageDirection = message.getDirection();
        if (messageCommand instanceof SendThingMessage<?> sendThingMessage) {
            return performWotBasedThingMessageValidation(messageCommand, sendThingMessage, messageDirection,
                    messageCommandPayloadSupplier
            ).thenApply(aVoid -> messageCommand);
        } else if (messageCommand instanceof SendFeatureMessage<?> sendFeatureMessage) {
            final String featureId = sendFeatureMessage.getFeatureId();
            return performWotBasedFeatureMessageValidation(messageCommand, sendFeatureMessage, featureId,
                    messageDirection, messageCommandPayloadSupplier
            ).thenApply(aVoid -> messageCommand);

        } else {
            return CompletableFuture.completedFuture(messageCommand);
        }
    }

    private CompletionStage<Void> performWotBasedThingMessageValidation(final MessageCommand<?, ?> messageCommand,
            final SendThingMessage<?> sendThingMessage,
            final MessageDirection messageDirection,
            final Supplier<JsonValue> messageCommandPayloadSupplier
    ) {
        return resolveThingDefinition()
                .thenComposeAsync(optThingDefinition -> {
                    if (messageDirection == MessageDirection.TO) {
                        return thingModelValidator.validateThingActionInput(
                                optThingDefinition.orElse(null),
                                sendThingMessage.getMessage().getSubject(),
                                messageCommandPayloadSupplier,
                                sendThingMessage.getResourcePath(),
                                sendThingMessage.getDittoHeaders()
                        );
                    } else if (messageDirection == MessageDirection.FROM) {
                        return thingModelValidator.validateThingEventData(
                                optThingDefinition.orElse(null),
                                sendThingMessage.getMessage().getSubject(),
                                messageCommandPayloadSupplier,
                                sendThingMessage.getResourcePath(),
                                sendThingMessage.getDittoHeaders()
                        );
                    } else {
                        return CompletableFuture.failedStage(DittoInternalErrorException.newBuilder()
                                .message("Unknown message direction")
                                .dittoHeaders(messageCommand.getDittoHeaders())
                                .build()
                        );
                    }
                }, wotValidationExecutor);
    }

    private CompletionStage<Void> performWotBasedFeatureMessageValidation(final MessageCommand<?, ?> messageCommand,
            final SendFeatureMessage<?> sendFeatureMessage,
            final String featureId,
            final MessageDirection messageDirection,
            final Supplier<JsonValue> messageCommandPayloadSupplier
    ) {
        return resolveThingAndFeatureDefinition(featureId)
                .thenComposeAsync(optDefinitionPair -> {
                    if (messageDirection == MessageDirection.TO) {
                        return thingModelValidator.validateFeatureActionInput(
                                optDefinitionPair.first().orElse(null),
                                optDefinitionPair.second().orElse(null),
                                featureId,
                                sendFeatureMessage.getMessage().getSubject(),
                                messageCommandPayloadSupplier,
                                sendFeatureMessage.getResourcePath(),
                                sendFeatureMessage.getDittoHeaders()
                        );
                    } else if (messageDirection == MessageDirection.FROM) {
                        return thingModelValidator.validateFeatureEventData(
                                optDefinitionPair.first().orElse(null),
                                optDefinitionPair.second().orElse(null),
                                featureId,
                                sendFeatureMessage.getMessage().getSubject(),
                                messageCommandPayloadSupplier,
                                sendFeatureMessage.getResourcePath(),
                                sendFeatureMessage.getDittoHeaders()
                        );
                    } else {
                        return CompletableFuture.failedStage(DittoInternalErrorException.newBuilder()
                                .message("Unknown message direction")
                                .dittoHeaders(messageCommand.getDittoHeaders())
                                .build()
                        );
                    }
                }, wotValidationExecutor);
    }

    private CompletionStage<MessageCommandResponse<?, ?>> performWotBasedMessageCommandResponseValidation(
            final MessageCommandResponse<?, ?> messageCommandResponse
    ) {
        @SuppressWarnings("unchecked") final Message<JsonValue> message =
                ((MessageCommandResponse<JsonValue, ?>) messageCommandResponse)
                        .getMessage();

        // lazily only supply JsonValue if validation is enabled for the message:
        final Supplier<JsonValue> messageCommandPayloadSupplier = () -> {
            if (message.getPayload().isPresent() && !isJsonMessageContent(message)) {
                throw WotThingModelPayloadValidationException
                        .newBuilder("Could not validate non-JSON message content type <" +
                                message.getContentType().orElse("?") + "> for message response subject " +
                                "<" + message.getSubject() + ">"
                        )
                        .dittoHeaders(messageCommandResponse.getDittoHeaders())
                        .build();
            }

            return message.getPayload().orElse(null);
        };

        if (messageCommandResponse instanceof SendThingMessageResponse<?> sendThingMessageResponse) {
            return resolveThingDefinition()
                    .thenComposeAsync(optThingDefinition ->
                            thingModelValidator.validateThingActionOutput(
                                optThingDefinition.orElse(null),
                                sendThingMessageResponse.getMessage().getSubject(),
                                messageCommandPayloadSupplier,
                                sendThingMessageResponse.getResourcePath(),
                                sendThingMessageResponse.getDittoHeaders()
                        ), wotValidationExecutor
                    )
                    .thenApply(aVoid -> messageCommandResponse);
        } else if (messageCommandResponse instanceof SendFeatureMessageResponse<?> sendFeatureMessageResponse) {
            final String featureId = sendFeatureMessageResponse.getFeatureId();
            return resolveThingAndFeatureDefinition(featureId)
                    .thenComposeAsync(optDefinitionPair ->
                            thingModelValidator.validateFeatureActionOutput(
                                optDefinitionPair.first().orElse(null),
                                optDefinitionPair.second().orElse(null),
                                featureId,
                                sendFeatureMessageResponse.getMessage().getSubject(),
                                messageCommandPayloadSupplier,
                                sendFeatureMessageResponse.getResourcePath(),
                                sendFeatureMessageResponse.getDittoHeaders()
                        ), wotValidationExecutor
                    )
                    .thenApply(aVoid -> messageCommandResponse);
        } else {
            return CompletableFuture.completedFuture(messageCommandResponse);
        }
    }

    private static boolean isJsonMessageContent(final Message<?> message) {
        return message
                .getInterpretedContentType()
                .filter(ContentType::isJson)
                .isPresent();
    }

    private CompletionStage<Optional<ThingDefinition>> resolveThingDefinition() {
        return Patterns.ask(getContext().getParent(), SudoRetrieveThing.of(entityId,
                        JsonFieldSelector.newInstance("definition"),
                        DittoHeaders.newBuilder()
                                .correlationId("sudoRetrieveThingDefinitionFromThingEnforcerActor-" + UUID.randomUUID())
                                .build()
                ), DEFAULT_LOCAL_ASK_TIMEOUT
        ).thenApply(response -> {
            if (response instanceof SudoRetrieveThingResponse sudoRetrieveThingResponse) {
                return sudoRetrieveThingResponse.getThing().getDefinition();
            } else if (response instanceof ThingNotAccessibleException) {
                return Optional.empty();
            } else {
                throw new IllegalStateException("expected SudoRetrieveThingResponse, got: " + response);
            }
        });
    }

    private CompletionStage<Pair<Optional<ThingDefinition>, Optional<FeatureDefinition>>>
    resolveThingAndFeatureDefinition(final String featureId) {
        return Patterns.ask(getContext().getParent(), SudoRetrieveThing.of(entityId,
                        JsonFieldSelector.newInstance("definition", "features/" + featureId + "/definition"),
                        DittoHeaders.newBuilder()
                                .correlationId(
                                        "sudoRetrieveThingAndFeatureDefinitionFromThingEnforcerActor-" + UUID.randomUUID())
                                .build()
                ), DEFAULT_LOCAL_ASK_TIMEOUT
        ).thenApply(response -> {
            if (response instanceof SudoRetrieveThingResponse sudoRetrieveThingResponse) {
                return new Pair<>(sudoRetrieveThingResponse.getThing().getDefinition(),
                        sudoRetrieveThingResponse.getThing()
                                .getFeatures()
                                .flatMap(f -> f.getFeature(featureId))
                                .flatMap(Feature::getDefinition)
                );
            } else if (response instanceof ThingNotAccessibleException) {
                return new Pair<>(Optional.empty(), Optional.empty());
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
     * @param startedSpan the started span to finish upon success or failure
     * @return the optional extracted PolicyId.
     */
    static Optional<PolicyId> extractPolicyIdFromSudoRetrieveThingResponse(final Object response,
            final StartedSpan startedSpan) {
        if (response instanceof SudoRetrieveThingResponse sudoRetrieveThingResponse) {
            startedSpan.finish();
            return sudoRetrieveThingResponse.getThing().getPolicyId();
        } else if (response instanceof ThingNotAccessibleException) {
            startedSpan.tagAsFailed("Thing not accessible")
                    .finish();
            return Optional.empty();
        } else {
            startedSpan.tagAsFailed("expected SudoRetrieveThingResponse, got: " + response)
                    .finish();
            throw new IllegalStateException("expected SudoRetrieveThingResponse, got: " + response);
        }
    }

}
