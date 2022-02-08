/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.concierge.service.enforcement;

import static java.util.Objects.requireNonNull;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.concierge.service.enforcement.LiveSignalEnforcement.addEffectedReadSubjectsToThingLiveSignal;
import static org.eclipse.ditto.concierge.service.enforcement.LiveSignalEnforcement.adjustTimeoutAndFilterLiveQueryResponse;
import static org.eclipse.ditto.policies.api.Permission.MIN_REQUIRED_POLICY_PERMISSIONS;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.api.persistence.PersistenceLifecycle;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.exceptions.AskException;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.model.headers.LiveChannelTimeoutStrategy;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.namespaces.NamespaceBlockedException;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.CommandToExceptionRegistry;
import org.eclipse.ditto.base.model.signals.commands.ErrorResponse;
import org.eclipse.ditto.base.model.signals.commands.exceptions.GatewayCommandTimeoutException;
import org.eclipse.ditto.base.model.signals.commands.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.concierge.api.ConciergeMessagingConstants;
import org.eclipse.ditto.concierge.service.actors.LiveResponseAndAcknowledgementForwarder;
import org.eclipse.ditto.concierge.service.common.ConciergeConfig;
import org.eclipse.ditto.concierge.service.common.DittoConciergeConfig;
import org.eclipse.ditto.concierge.service.common.EnforcementConfig;
import org.eclipse.ditto.concierge.service.enforcement.placeholders.references.PolicyIdReferencePlaceholderResolver;
import org.eclipse.ditto.concierge.service.enforcement.placeholders.references.ReferencePlaceholder;
import org.eclipse.ditto.internal.models.signal.CommandHeaderRestoration;
import org.eclipse.ditto.internal.models.signal.SignalInformationPoint;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.cacheloaders.AskWithRetry;
import org.eclipse.ditto.internal.utils.cacheloaders.EnforcementCacheKey;
import org.eclipse.ditto.internal.utils.cacheloaders.EnforcementContext;
import org.eclipse.ditto.internal.utils.cacheloaders.PolicyEnforcer;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.pubsub.LiveSignalPub;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonPointerInvalidException;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.api.PoliciesValidator;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyException;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.policies.model.enforcers.PolicyEnforcers;
import org.eclipse.ditto.policies.model.signals.commands.PolicyErrorResponse;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyConflictException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyUnavailableException;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicyResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicy;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyResponse;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.rql.model.ParserException;
import org.eclipse.ditto.rql.model.predicates.ast.RootNode;
import org.eclipse.ditto.rql.parser.RqlPredicateParser;
import org.eclipse.ditto.rql.query.things.FieldNamesPredicateVisitor;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingConstants;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.exceptions.LiveChannelConditionNotAllowedException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.PolicyIdNotAllowedException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.PolicyInvalidException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingCommandToAccessExceptionRegistry;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingCommandToModifyExceptionRegistry;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingConditionFailedException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingConditionInvalidException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotCreatableException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotModifiableException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingUnavailableException;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommand;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommand;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommandResponse;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.pattern.AskTimeoutException;
import akka.pattern.Patterns;

/**
 * Authorize {@code ThingCommand}.
 */
public final class ThingCommandEnforcement
        extends AbstractEnforcementWithAsk<ThingCommand<?>, ThingQueryCommandResponse<?>> {

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(ThingCommandEnforcement.class);

    private static final Map<String, ThreadSafeDittoLogger> NAMESPACE_INSPECTION_LOGGERS = new HashMap<>();

    /**
     * Label of default policy entry in default policy.
     */
    private static final String DEFAULT_POLICY_ENTRY_LABEL = "DEFAULT";

    /**
     * Json fields that are always shown regardless of authorization.
     */
    private static final JsonFieldSelector THING_QUERY_COMMAND_RESPONSE_ALLOWLIST =
            JsonFactory.newFieldSelector(Thing.JsonFields.ID);

    private final ActorRef thingsShardRegion;
    private final ActorRef policiesShardRegion;
    private final EnforcerRetriever<Enforcer> thingEnforcerRetriever;
    private final EnforcerRetriever<Enforcer> policyEnforcerRetriever;
    private final Cache<EnforcementCacheKey, Entry<EnforcementCacheKey>> thingIdCache;
    private final Cache<EnforcementCacheKey, Entry<Enforcer>> policyEnforcerCache;
    private final PreEnforcer preEnforcer;
    private final PolicyIdReferencePlaceholderResolver policyIdReferencePlaceholderResolver;
    private final CreationRestrictionEnforcer creationRestrictionEnforcer;
    private final LiveSignalPub liveSignalPub;
    private final ActorSystem actorSystem;
    private final EnforcementConfig enforcementConfig;
    private final ResponseReceiverCache responseReceiverCache;

    private ThingCommandEnforcement(final Contextual<ThingCommand<?>> data,
            final ActorSystem actorSystem,
            final ActorRef thingsShardRegion,
            final ActorRef policiesShardRegion,
            final Cache<EnforcementCacheKey, Entry<EnforcementCacheKey>> thingIdCache,
            final Cache<EnforcementCacheKey, Entry<Enforcer>> policyEnforcerCache,
            final PreEnforcer preEnforcer,
            final CreationRestrictionEnforcer creationRestrictionEnforcer,
            final LiveSignalPub liveSignalPub,
            final EnforcementConfig enforcementConfig,
            final ResponseReceiverCache responseReceiverCache) {

        super(data, ThingQueryCommandResponse.class);
        this.thingsShardRegion = requireNonNull(thingsShardRegion);
        this.policiesShardRegion = requireNonNull(policiesShardRegion);

        final ConciergeConfig conciergeConfig = DittoConciergeConfig.of(
                DefaultScopedConfig.dittoScoped(actorSystem.settings().config())
        );

        conciergeConfig.getEnforcementConfig().getSpecialLoggingInspectedNamespaces()
                .forEach(loggedNamespace -> NAMESPACE_INSPECTION_LOGGERS.put(
                        loggedNamespace,
                        DittoLoggerFactory.getThreadSafeLogger(ThingCommandEnforcement.class.getName() +
                                ".namespace." + loggedNamespace)));

        this.thingIdCache = requireNonNull(thingIdCache);
        this.policyEnforcerCache = requireNonNull(policyEnforcerCache);
        this.preEnforcer = preEnforcer;
        this.actorSystem = actorSystem;
        this.enforcementConfig = enforcementConfig;
        thingEnforcerRetriever = PolicyEnforcerRetrieverFactory.create(thingIdCache, policyEnforcerCache);
        policyEnforcerRetriever = new EnforcerRetriever<>(IdentityCache.INSTANCE, policyEnforcerCache);
        policyIdReferencePlaceholderResolver =
                PolicyIdReferencePlaceholderResolver.of(conciergeForwarder(), getAskWithRetryConfig(),
                        context.getScheduler(), context.getExecutor());
        this.creationRestrictionEnforcer = creationRestrictionEnforcer;
        this.liveSignalPub = liveSignalPub;
        this.responseReceiverCache = responseReceiverCache;
    }

    @Override
    public CompletionStage<Contextual<WithDittoHeaders>> enforce() {
        return thingEnforcerRetriever.retrieve(entityId(), (enforcerKeyEntry, enforcerEntry) -> {
            try {
                return doEnforce(enforcerKeyEntry, enforcerEntry);
            } catch (final RuntimeException e) {
                return CompletableFuture.failedStage(e);
            }
        });
    }

    private CompletionStage<Contextual<WithDittoHeaders>> doEnforce(
            final Entry<EnforcementCacheKey> enforcerKeyEntry, final Entry<Enforcer> enforcerEntry) {

        if (enforcerEntry.exists()) {
            if (keyEntryForDeletedThing(enforcerKeyEntry)) {
                if (isRetrieveCommandForDeletedThing()) {
                    final EntityId policyId = enforcerKeyEntry.getValueOrThrow().getId();
                    final Contextual<WithDittoHeaders> enforcementResult = enforceThingCommandByPolicyEnforcer(signal(),
                            PolicyId.of(policyId),
                            enforcerEntry.getValueOrThrow());
                    return CompletableFuture.completedFuture(enforcementResult);
                } else {
                    return enforceThingCommandByNonexistentEnforcer(enforcerKeyEntry);
                }
            } else {
                final EntityId policyId = enforcerKeyEntry.getValueOrThrow().getId();
                final Contextual<WithDittoHeaders> enforcementResult = enforceThingCommandByPolicyEnforcer(signal(),
                        PolicyId.of(policyId),
                        enforcerEntry.getValueOrThrow());
                return CompletableFuture.completedFuture(enforcementResult);
            }
        } else {
            return enforceThingCommandByNonexistentEnforcer(enforcerKeyEntry);
        }
    }

    private boolean isRetrieveCommandForDeletedThing() {
        return (signal() instanceof RetrieveThing) && signal().getDittoHeaders().shouldRetrieveDeleted();
    }

    private boolean keyEntryForDeletedThing(final Entry<EnforcementCacheKey> enforcerKeyEntry) {
        return enforcerKeyEntry.exists() && enforcerKeyEntry.getValueOrThrow()
                .getCacheLookupContext()
                .flatMap(EnforcementContext::getPersistenceLifecycle)
                .map(x -> PersistenceLifecycle.DELETED == x)
                .orElse(false);
    }

    /**
     * Authorize a thing command in the absence of an enforcer. This happens when the thing did not exist or when the
     * policy of the thing does not exist.
     *
     * @param enforcerKeyEntry cache entry in the entity ID cache for the enforcer cache key.
     * @return the completionStage of the contextual including message and receiver
     */
    private CompletionStage<Contextual<WithDittoHeaders>> enforceThingCommandByNonexistentEnforcer(
            final Entry<EnforcementCacheKey> enforcerKeyEntry) {

        if (enforcerKeyEntry.exists() && !keyEntryForDeletedThing(enforcerKeyEntry)) {
            // Thing exists but its policy is deleted.
            final var thingId = signal().getEntityId();
            final EntityId policyId = enforcerKeyEntry.getValueOrThrow().getId();
            final DittoRuntimeException error = errorForExistingThingWithDeletedPolicy(signal(), thingId, policyId);
            if (LOGGER.isInfoEnabled()) {
                LOGGER.withCorrelationId(dittoHeaders())
                        .info("Enforcer was not existing for Thing <{}>, responding with: {}", thingId,
                                error.toString());
            }
            throw error;
        } else {
            // Without prior enforcer in cache, enforce CreateThing by self.
            // DO NOT use Contextual.askFuture to handle the ask-steps of a CreateThing command! Otherwise
            // the query- and modify-commands sent immediately after may be processed before the thing is created.
            return enforceCreateThingBySelf()
                    .thenCompose(pair ->
                            handleInitialCreateThing(pair.createThing, pair.enforcer)
                                    .thenApply(create -> create.withReceiver(thingsShardRegion))
                    )
                    .exceptionally(throwable -> {
                        final ThreadSafeDittoLogger l = LOGGER.withCorrelationId(dittoHeaders());

                        final var dittoRuntimeException =
                                DittoRuntimeException.asDittoRuntimeException(throwable, cause -> {
                                    l.warn("Error during thing by itself enforcement - {}: {}",
                                            cause.getClass().getSimpleName(), cause.getMessage());
                                    throw GatewayInternalErrorException.newBuilder()
                                            .cause(cause)
                                            .build();
                                });

                        l.debug("DittoRuntimeException during enforceThingCommandByNonexistentEnforcer - {}: {}",
                                dittoRuntimeException.getClass().getSimpleName(), dittoRuntimeException.getMessage());
                        throw dittoRuntimeException;
                    });
        }
    }

    private static boolean isResponseRequired(final WithDittoHeaders withDittoHeaders) {
        final var dittoHeaders = withDittoHeaders.getDittoHeaders();
        return dittoHeaders.isResponseRequired();
    }

    /**
     * Authorize a thing command by policy enforcer with view restriction for query commands.
     *
     * @param thingCommand the thing command to authorize.
     * @param policyId the ID of the thing's policy.
     * @param enforcer the policy enforcer.
     * @return the contextual including message and receiver
     */
    private Contextual<WithDittoHeaders> enforceThingCommandByPolicyEnforcer(
            final ThingCommand<?> thingCommand, final PolicyId policyId, final Enforcer enforcer) {

        final ThingCommand<?> commandWithReadSubjects = authorizeByPolicyOrThrow(enforcer, thingCommand);

        final Contextual<WithDittoHeaders> result;
        if (commandWithReadSubjects instanceof ThingQueryCommand) {
            final ThingQueryCommand<?> thingQueryCommand = (ThingQueryCommand<?>) commandWithReadSubjects;
            final Instant startTime = Instant.now();
            if (!isResponseRequired(thingQueryCommand)) {
                // drop query command with response-required=false
                result = withMessageToReceiver(null, ActorRef.noSender());
            } else if (thingQueryCommand instanceof RetrieveThing && shouldRetrievePolicyWithThing(thingQueryCommand)) {
                final var retrieveThing = (RetrieveThing) ensureTwinChannel(thingQueryCommand);
                result = withMessageToReceiverViaAskFuture(retrieveThing, sender(), () ->
                        retrieveThingAndPolicy(retrieveThing, policyId, enforcer).thenCompose(response -> {
                                    if (null != response) {
                                        return doSmartChannelSelection(thingQueryCommand, response, startTime, enforcer);
                                    } else {
                                        log(retrieveThing).error("Response was null at a place where it must never " +
                                                "be null");
                                        throw GatewayInternalErrorException.newBuilder()
                                                .dittoHeaders(retrieveThing.getDittoHeaders())
                                                .build();
                                    }
                                })
                );
            } else {
                final var twinCommand = ensureTwinChannel(thingQueryCommand);
                result = withMessageToReceiverViaAskFuture(twinCommand, sender(), () ->
                        askAndBuildJsonView(thingsShardRegion, thingQueryCommand, enforcer,
                                context.getScheduler(), context.getExecutor()).thenCompose(response ->
                                doSmartChannelSelection(thingQueryCommand, response, startTime, enforcer))
                );
            }
        } else if (commandWithReadSubjects.getDittoHeaders().getLiveChannelCondition().isPresent()) {
            throw LiveChannelConditionNotAllowedException.newBuilder()
                    .dittoHeaders(commandWithReadSubjects.getDittoHeaders())
                    .build();
        } else {
            result = forwardToThingsShardRegion(commandWithReadSubjects);
        }
        return result;
    }

    private CompletionStage<ThingQueryCommandResponse<?>> doSmartChannelSelection(final ThingQueryCommand<?> command,
            final ThingQueryCommandResponse<?> response, final Instant startTime, final Enforcer enforcer) {

        final ThingQueryCommandResponse<?> twinResponseWithTwinChannel = setTwinChannel(response);
        final ThingQueryCommandResponse<?> twinResponse = CommandHeaderRestoration.restoreCommandConnectivityHeaders(
                twinResponseWithTwinChannel, command.getDittoHeaders());
        if (!shouldAttemptLiveChannel(command, twinResponse)) {
            return CompletableFuture.completedStage(twinResponse);
        }

        final ThingQueryCommand<?> liveCommand = toLiveCommand(command, enforcer);
        final var pub = liveSignalPub.command();
        final var liveResponseForwarder = startLiveResponseForwarder(liveCommand);
        if (enforcementConfig.shouldDispatchGlobally(liveCommand)) {
            return responseReceiverCache.insertResponseReceiverConflictFreeWithFuture(
                    liveCommand,
                    newCommand -> liveResponseForwarder,
                    (newCommand, forwarder) -> adjustTimeoutAndFilterLiveQueryResponse(this, newCommand,
                            startTime, pub, forwarder, enforcer, getFallbackResponseCaster(liveCommand, twinResponse))
            );
        } else {
            return adjustTimeoutAndFilterLiveQueryResponse(this, liveCommand, startTime, pub, liveResponseForwarder,
                    enforcer, getFallbackResponseCaster(liveCommand, twinResponse));
        }
    }

    private ActorRef startLiveResponseForwarder(final ThingQueryCommand<?> signal) {
        final var pub = liveSignalPub.command();
        final var props = LiveResponseAndAcknowledgementForwarder.props(signal, pub.getPublisher(), sender());
        return actorSystem.actorOf(props);
    }

    private Function<Object, CompletionStage<ThingQueryCommandResponse<?>>> getFallbackResponseCaster(
            final ThingQueryCommand<?> liveCommand, final ThingQueryCommandResponse<?> twinResponse) {

        return response -> {
            if (response instanceof ThingQueryCommandResponse) {
                return CompletableFuture.completedStage(
                        setAdditionalHeaders((ThingQueryCommandResponse<?>) response, liveCommand.getDittoHeaders()));
            } else if (response instanceof ErrorResponse) {
                return CompletableFuture.failedStage(
                        setAdditionalHeaders(((ErrorResponse<?>) response),
                                liveCommand.getDittoHeaders()).getDittoRuntimeException());
            } else if (response instanceof AskException || response instanceof AskTimeoutException) {
                return applyTimeoutStrategy(liveCommand, twinResponse);
            } else {
                final var errorToReport = reportErrorOrResponse(
                        "before building JsonView for live response via smart channel selection",
                        response, null);
                return CompletableFuture.failedStage(errorToReport);
            }
        };
    }

    private static boolean shouldAttemptLiveChannel(final ThingQueryCommand<?> command,
            final ThingQueryCommandResponse<?> twinResponse) {
        return isLiveChannelConditionMatched(command, twinResponse) || isLiveQueryCommandWithTimeoutStrategy(command);
    }

    private static boolean isLiveChannelConditionMatched(final ThingQueryCommand<?> command,
            final ThingQueryCommandResponse<?> twinResponse) {
        return command.getDittoHeaders().getLiveChannelCondition().isPresent() &&
                twinResponse.getDittoHeaders().didLiveChannelConditionMatch();
    }

    static boolean isLiveQueryCommandWithTimeoutStrategy(final Signal<?> command) {
        return command instanceof ThingQueryCommand &&
                command.getDittoHeaders().getLiveChannelTimeoutStrategy().isPresent() &&
                SignalInformationPoint.isChannelLive(command);
    }

    private static <T extends DittoHeadersSettable<?>> T setAdditionalHeaders(final T settable,
            final DittoHeaders commandHeaders) {
        final DittoHeaders dittoHeaders = settable.getDittoHeaders();
        final DittoHeadersSettable<?> theSettable = settable.setDittoHeaders(dittoHeaders
                .toBuilder()
                .putHeaders(getAdditionalLiveResponseHeaders(dittoHeaders))
                .build());
        return (T) CommandHeaderRestoration.restoreCommandConnectivityHeaders(theSettable, commandHeaders);
    }

    private static CompletionStage<ThingQueryCommandResponse<?>> applyTimeoutStrategy(
            final ThingCommand<?> command,
            final ThingQueryCommandResponse<?> twinResponse) {

        if (isTwinFallbackEnabled(twinResponse)) {
            return CompletableFuture.completedStage(twinResponse);
        } else {
            final var timeout = LiveSignalEnforcement.getLiveSignalTimeout(command);
            final GatewayCommandTimeoutException timeoutException = GatewayCommandTimeoutException.newBuilder(timeout)
                    .dittoHeaders(twinResponse.getDittoHeaders()
                            .toBuilder()
                            .channel(TopicPath.Channel.LIVE.getName())
                            .putHeaders(getAdditionalLiveResponseHeaders(twinResponse.getDittoHeaders()))
                            .build())
                    .build();
            final GatewayCommandTimeoutException timeoutExceptionWithConnectivityHeaders =
                    CommandHeaderRestoration.restoreCommandConnectivityHeaders(timeoutException,
                            command.getDittoHeaders());
            return CompletableFuture.failedStage(timeoutExceptionWithConnectivityHeaders);
        }
    }

    private static boolean isTwinFallbackEnabled(final Signal<?> signal) {
        final var liveChannelFallbackStrategy =
                signal.getDittoHeaders().getLiveChannelTimeoutStrategy().orElse(LiveChannelTimeoutStrategy.FAIL);
        return LiveChannelTimeoutStrategy.USE_TWIN == liveChannelFallbackStrategy;
    }

    private static ThingQueryCommand<?> toLiveCommand(final ThingQueryCommand<?> command, final Enforcer enforcer) {
        final ThingQueryCommand<?> withReadSubjects = addEffectedReadSubjectsToThingLiveSignal(command, enforcer);
        return withReadSubjects.setDittoHeaders(withReadSubjects.getDittoHeaders().toBuilder()
                .liveChannelCondition(null)
                .channel(TopicPath.Channel.LIVE.getName())
                .build());
    }

    /**
     * Retrieve a thing and its policy and combine them into a response.
     *
     * @param retrieveThing the retrieve-thing command.
     * @param policyId the ID of the thing's policy.
     * @param enforcer the enforcer for the command.
     * @return always {@code true}.
     */
    private CompletionStage<ThingQueryCommandResponse<?>> retrieveThingAndPolicy(final RetrieveThing retrieveThing,
            final PolicyId policyId, final Enforcer enforcer) {

        final var dittoHeadersWithoutPreconditionHeaders =
                DittoHeaders.newBuilder(retrieveThing.getDittoHeaders())
                        .removePreconditionHeaders()
                        .build();

        final Optional<RetrievePolicy> retrievePolicyOptional = PolicyCommandEnforcement.authorizePolicyCommand(
                RetrievePolicy.of(policyId, dittoHeadersWithoutPreconditionHeaders), PolicyEnforcer.of(enforcer),
                this.creationRestrictionEnforcer);

        if (retrievePolicyOptional.isPresent()) {
            return retrieveThingBeforePolicy(retrieveThing)
                    .thenCompose(retrieveThingResponse -> {
                        if (retrieveThingResponse instanceof RetrieveThingResponse) {
                            final var retrievePolicy = retrievePolicyOptional.get();
                            return retrieveInlinedPolicyForThing(retrieveThing, retrievePolicy)
                                    .thenApply(policyResponse -> {
                                        if (policyResponse.isPresent()) {
                                            final RetrievePolicyResponse filteredPolicyResponse =
                                                    PolicyCommandEnforcement.buildJsonViewForPolicyQueryCommandResponse(
                                                            policyResponse.get(), enforcer);
                                            return reportAggregatedThingAndPolicyResponse(retrieveThing,
                                                    (RetrieveThingResponse) retrieveThingResponse,
                                                    filteredPolicyResponse, enforcer);
                                        } else {
                                            return retrieveThingResponse;
                                        }
                                    });
                        } else {
                            return CompletableFuture.completedFuture(retrieveThingResponse);
                        }
                    });
        } else {
            // sender is not authorized to view the policy, ignore the request to embed policy.
            return askAndBuildJsonView(thingsShardRegion, retrieveThing, enforcer, context.getScheduler(),
                    context.getExecutor());
        }
    }

    /**
     * Retrieve a thing before retrieving its inlined policy. Report errors to sender.
     *
     * @param command the command.
     * @return future response from things-shard-region.
     */
    private CompletionStage<ThingQueryCommandResponse<?>> retrieveThingBeforePolicy(final RetrieveThing command) {
        return ask(thingsShardRegion, command, "retrieving thing before inlined policy", context.getScheduler(),
                context.getExecutor());
    }

    /**
     * Retrieve inlined policy after retrieving a thing. Do not report errors.
     *
     * @param retrieveThing the original command.
     * @param retrievePolicy the command to retrieve the thing's policy.
     * @return future response from policies-shard-region.
     */
    private CompletionStage<Optional<RetrievePolicyResponse>> retrieveInlinedPolicyForThing(
            final RetrieveThing retrieveThing, final RetrievePolicy retrievePolicy) {

        return preEnforcer.apply(retrievePolicy)
                .thenCompose(msg -> AskWithRetry.askWithRetry(policiesShardRegion, msg,
                        getAskWithRetryConfig(), context.getScheduler(), context.getExecutor(),
                        response -> {
                            if (response instanceof RetrievePolicyResponse) {
                                return Optional.of((RetrievePolicyResponse) response);
                            } else {
                                LOGGER.withCorrelationId(getCorrelationIdOrNull(response, retrieveThing))
                                        .info("No authorized response when retrieving inlined policy <{}> for thing <{}>: {}",
                                                retrievePolicy.getEntityId(), retrieveThing.getEntityId(), response);
                                return Optional.<RetrievePolicyResponse>empty();
                            }
                        }
                ).exceptionally(error -> {
                    LOGGER.withCorrelationId(getCorrelationIdOrNull(error, retrieveThing))
                            .error("Retrieving inlined policy after RetrieveThing", error);
                    return Optional.empty();
                }));
    }

    @Nullable
    private static CharSequence getCorrelationIdOrNull(final Object signal, final WithDittoHeaders fallBackSignal) {
        final WithDittoHeaders withDittoHeaders;
        if (isWithDittoHeaders(signal)) {
            withDittoHeaders = (WithDittoHeaders) signal;
        } else {
            withDittoHeaders = fallBackSignal;
        }
        final var dittoHeaders = withDittoHeaders.getDittoHeaders();
        return dittoHeaders.getCorrelationId().orElse(null);
    }

    private static boolean isWithDittoHeaders(final Object o) {
        return o instanceof WithDittoHeaders;
    }

    /**
     * Put thing and policy together as response to the sender.
     *
     * @param retrieveThing the original command.
     * @param thingResponse response from things-shard-region.
     * @param policyResponse response from policies-shard-region.
     * @param enforcer enforcer to build the JSON view.
     */
    private static RetrieveThingResponse reportAggregatedThingAndPolicyResponse(final RetrieveThing retrieveThing,
            final RetrieveThingResponse thingResponse,
            final RetrievePolicyResponse policyResponse,
            final Enforcer enforcer) {

        return reportAggregatedThingAndPolicy(retrieveThing, thingResponse, policyResponse.getPolicy(), enforcer);
    }

    private static RetrieveThingResponse reportAggregatedThingAndPolicy(final RetrieveThing retrieveThing,
            final RetrieveThingResponse retrieveThingResponse,
            final Policy policy,
            final Enforcer enforcer) {

        final RetrieveThingResponse limitedView =
                buildJsonViewForThingQueryCommandResponse(retrieveThingResponse, enforcer);

        final JsonObject inlinedPolicy =
                policy.toInlinedJson(retrieveThing.getImplementedSchemaVersion(), FieldType.notHidden());

        final JsonObject thingWithInlinedPolicy = limitedView.getEntity().asObject().toBuilder()
                .setAll(inlinedPolicy)
                .build();

        return limitedView.setEntity(thingWithInlinedPolicy);
    }

    /**
     * Report timeout of {@code ThingQueryCommand}.
     *
     * @param command the original command.
     * @param askTimeout the timeout exception.
     */
    @Override
    protected Optional<DittoRuntimeException> handleAskTimeoutForCommand(final ThingCommand<?> command,
            final Throwable askTimeout) {
        LOGGER.withCorrelationId(dittoHeaders()).error("Timeout before building JsonView", askTimeout);
        return Optional.of(ThingUnavailableException.newBuilder(command.getEntityId())
                .dittoHeaders(command.getDittoHeaders())
                .build());
    }

    /**
     * Mixin-private: report thing query response with view on entity restricted by enforcer.
     *
     * @param commandResponse response of query.
     * @param enforcer the enforcer.
     */
    @Override
    protected ThingQueryCommandResponse<?> filterJsonView(final ThingQueryCommandResponse<?> commandResponse,
            final Enforcer enforcer) {
        try {
            return buildJsonViewForThingQueryCommandResponse(commandResponse, enforcer);
        } catch (final RuntimeException e) {
            throw reportError("Error after building JsonView", e);
        }
    }

    /**
     * Query caches again to authorize a {@code CreateThing} command with explicit policy ID and no inline policy.
     *
     * @param command the command.
     * @param policyId the policy ID.
     * @return the completionStage of contextual including message and receiver
     */
    private CompletionStage<Contextual<WithDittoHeaders>> enforceCreateThingForNonexistentThingWithPolicyId(
            final CreateThing command, final PolicyId policyId) {

        final var policyCacheKey = EnforcementCacheKey.of(policyId);
        return policyEnforcerRetriever.retrieve(policyCacheKey, (policyIdEntry, policyEnforcerEntry) -> {
            if (policyEnforcerEntry.exists()) {
                final Contextual<WithDittoHeaders> enforcementResult =
                        enforceThingCommandByPolicyEnforcer(command, policyId, policyEnforcerEntry.getValueOrThrow());
                return CompletableFuture.completedFuture(enforcementResult);
            } else {
                throw errorForExistingThingWithDeletedPolicy(command, command.getEntityId(), policyId);
            }
        });
    }

    /**
     * Limit view on entity of {@code ThingQueryCommandResponse} by enforcer.
     *
     * @param response the response.
     * @param enforcer the enforcer.
     * @return response with view on entity restricted by enforcer.
     */
    static <T extends ThingQueryCommandResponse<T>> T buildJsonViewForThingQueryCommandResponse(
            final ThingQueryCommandResponse<T> response, final Enforcer enforcer) {

        final JsonValue entity = response.getEntity();
        if (entity.isObject()) {
            final JsonObject filteredView =
                    getJsonViewForThingQueryCommandResponse(entity.asObject(), response, enforcer);
            return response.setEntity(filteredView);
        } else {
            return response.setEntity(entity);
        }
    }

    /**
     * Forward a command to things-shard-region.
     *
     * @param command command to forward.
     * @return the contextual including message and receiver
     */
    private Contextual<WithDittoHeaders> forwardToThingsShardRegion(final ThingCommand<?> command) {
        if (command instanceof ThingModifyCommand && ((ThingModifyCommand<?>) command).changesAuthorization()) {
            invalidateThingCaches(command.getEntityId());
        }

        if (NAMESPACE_INSPECTION_LOGGERS.containsKey(command.getEntityId().getNamespace())) {
            final ThreadSafeDittoLogger namespaceLogger = NAMESPACE_INSPECTION_LOGGERS
                    .get(command.getEntityId().getNamespace()).withCorrelationId(command);
            if (command instanceof ThingModifyCommand) {
                final JsonValue value = ((ThingModifyCommand<?>) command).getEntity().orElse(null);
                if (null != value) {
                    final Set<ResourceKey> resourceKeys = calculateLeaves(command.getResourcePath(), value);
                    namespaceLogger.info("Forwarding modify command type <{}> with resourceKeys <{}>",
                            command.getType(),
                            resourceKeys);
                }
            }
            namespaceLogger.debug("Forwarding command type <{}>: <{}>", command.getType(), command);
        }
        return withMessageToReceiver(command, thingsShardRegion);
    }

    /**
     * Whenever a Command changed the authorization, the caches must be invalidated - otherwise a directly following
     * Command targeted for the same entity will probably fail as the enforcer was not yet updated.
     *
     * @param thingId the ID of the Thing to invalidate caches for.
     */
    private void invalidateThingCaches(final ThingId thingId) {
        final var thingCacheKey = EnforcementCacheKey.of(thingId);
        thingIdCache.invalidate(thingCacheKey);
        pubSubMediator().tell(DistPubSubAccess.sendToAll(
                        ConciergeMessagingConstants.ENFORCER_ACTOR_PATH,
                        InvalidateCacheEntry.of(thingCacheKey),
                        true),
                self());
    }

    private void invalidatePolicyCache(final PolicyId policyId) {
        final var policyCacheKey = EnforcementCacheKey.of(policyId);
        policyEnforcerCache.invalidate(policyCacheKey);
        pubSubMediator().tell(DistPubSubAccess.sendToAll(
                        ConciergeMessagingConstants.ENFORCER_ACTOR_PATH,
                        InvalidateCacheEntry.of(policyCacheKey),
                        true),
                self());
    }

    /**
     * Restrict view on a JSON object by enforcer.
     *
     * @param responseEntity the JSON object to restrict view on.
     * @param response the response containing the object.
     * @param enforcer the enforcer.
     * @return JSON object with view restricted by enforcer.
     */
    private static JsonObject getJsonViewForThingQueryCommandResponse(final JsonObject responseEntity,
            final ThingQueryCommandResponse<?> response, final Enforcer enforcer) {


        final var resourceKey = ResourceKey.newInstance(ThingConstants.ENTITY_TYPE, response.getResourcePath());
        final var authorizationContext = response.getDittoHeaders().getAuthorizationContext();

        return enforcer.buildJsonView(resourceKey, responseEntity, authorizationContext,
                THING_QUERY_COMMAND_RESPONSE_ALLOWLIST, Permissions.newInstance(Permission.READ));
    }

    /**
     * Create error for commands to an existing thing whose policy is deleted.
     *
     * @param thingCommand the triggering command.
     * @param thingId ID of the thing.
     * @param policyId ID of the deleted policy.
     * @return an appropriate error.
     */
    private static DittoRuntimeException errorForExistingThingWithDeletedPolicy(final ThingCommand<?> thingCommand,
            final ThingId thingId, final CharSequence policyId) {

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

    /**
     * Create error due to failing to execute a thing-command in the expected way.
     *
     * @param thingCommand the command.
     * @return the error.
     */
    static DittoRuntimeException errorForThingCommand(final ThingCommand<?> thingCommand) {
        final CommandToExceptionRegistry<ThingCommand<?>, DittoRuntimeException> registry =
                thingCommand instanceof ThingModifyCommand
                        ? ThingCommandToModifyExceptionRegistry.getInstance()
                        : ThingCommandToAccessExceptionRegistry.getInstance();
        return registry.exceptionFrom(thingCommand);
    }

    /**
     * Authorize a thing-command by authorization information contained in itself. Only {@code CreateThing} commands are
     * authorized in this manner in the absence of an existing enforcer. {@code ModifyThing} commands are transformed to
     * {@code CreateThing} commands before being processed.
     *
     * @return optionally the authorized command extended by  read subjects.
     */
    private CompletionStage<CreateThingWithEnforcer> enforceCreateThingBySelf() {
        final ThingCommand<?> thingCommand = transformModifyThingToCreateThing(signal());
        if (thingCommand instanceof CreateThing) {
            return replaceInitialPolicyWithCopiedPolicyIfPresent((CreateThing) thingCommand)
                    .thenApply(createThing -> {
                        final Optional<JsonObject> initialPolicyOptional = createThing.getInitialPolicy();
                        return initialPolicyOptional.map(
                                        initialPolicy -> enforceCreateThingByOwnInlinedPolicyOrThrow(createThing,
                                                initialPolicy))
                                .orElseGet(() -> enforceCreateThingByAuthorizationContext(createThing));
                    });
        } else {
            // Other commands cannot be authorized by policy contained in self.
            final DittoRuntimeException error = ThingNotAccessibleException.newBuilder(thingCommand.getEntityId())
                    .dittoHeaders(thingCommand.getDittoHeaders())
                    .build();
            LOGGER.withCorrelationId(dittoHeaders())
                    .info("Enforcer was not existing for Thing <{}> and no auth info was inlined, responding with: {} - {}",
                            thingCommand.getEntityId(), error.getClass().getSimpleName(), error.getMessage());
            throw error;
        }
    }

    private CompletionStage<CreateThing> replaceInitialPolicyWithCopiedPolicyIfPresent(final CreateThing createThing) {
        return getInitialPolicyOrCopiedPolicy(createThing)
                .thenApply(initialPolicyOrCopiedPolicy ->
                        CreateThing.of(createThing.getThing(), initialPolicyOrCopiedPolicy,
                                createThing.getDittoHeaders()));
    }

    private CompletionStage<JsonObject> getInitialPolicyOrCopiedPolicy(final CreateThing createThing) {
        final ThreadSafeDittoLogger l = LOGGER.withCorrelationId(createThing);
        return createThing.getPolicyIdOrPlaceholder()
                .flatMap(ReferencePlaceholder::fromCharSequence)
                .map(referencePlaceholder -> {
                    l.debug("CreateThing command contains a reference placeholder for the policy it wants to copy: {}",
                            referencePlaceholder);
                    final var dittoHeadersWithoutPreconditionHeaders = dittoHeaders().toBuilder()
                            .removePreconditionHeaders()
                            .responseRequired(true)
                            .build();
                    return policyIdReferencePlaceholderResolver.resolve(referencePlaceholder,
                            dittoHeadersWithoutPreconditionHeaders);
                })
                .orElseGet(() -> CompletableFuture.completedFuture(createThing.getPolicyIdOrPlaceholder().orElse(null)))
                .thenCompose(policyId -> {
                    if (policyId != null) {
                        l.debug("CreateThing command wants to use a copy of Policy <{}>", policyId);
                        return retrievePolicyWithEnforcement(PolicyId.of(policyId))
                                .thenApply(policy -> policy.toJson(JsonSchemaVersion.V_2).remove("policyId"));
                    } else {
                        l.debug("CreateThing command did not contain a policy that should be copied.");
                        return CompletableFuture.completedFuture(createThing.getInitialPolicy().orElse(null));
                    }
                });
    }

    private CompletionStage<Policy> retrievePolicyWithEnforcement(final PolicyId policyId) {
        final var adjustedHeaders = dittoHeaders().toBuilder()
                .removePreconditionHeaders()
                .responseRequired(true)
                .build();

        return AskWithRetry.askWithRetry(conciergeForwarder(), RetrievePolicy.of(policyId, adjustedHeaders),
                getAskWithRetryConfig(), context.getScheduler(), context.getExecutor(),
                response -> {
                    if (response instanceof RetrievePolicyResponse) {
                        return ((RetrievePolicyResponse) response).getPolicy();
                    } else if (response instanceof PolicyErrorResponse) {
                        throw ((PolicyErrorResponse) response).getDittoRuntimeException();
                    } else if (response instanceof DittoRuntimeException) {
                        throw (DittoRuntimeException) response;
                    } else {
                        LOGGER.withCorrelationId(adjustedHeaders)
                                .error("Got an unexpected response while retrieving a Policy that should be copied" +
                                        " during Thing creation: {}", response);
                        throw GatewayInternalErrorException.newBuilder().build();
                    }
                });
    }

    private static CreateThingWithEnforcer enforceCreateThingByAuthorizationContext(final CreateThing createThing) {

        // Command without authorization information is authorized by default.
        final var dittoHeaders = createThing.getDittoHeaders();
        final var authorizationContext = dittoHeaders.getAuthorizationContext();
        final Set<AuthorizationSubject> authorizedSubjects = authorizationContext.getFirstAuthorizationSubject()
                .map(Collections::singleton)
                .orElse(Collections.emptySet());
        final Enforcer enforcer = new AuthorizedSubjectsEnforcer(
                AuthorizationContext.newInstance(authorizationContext.getType(), authorizedSubjects));
        final CreateThing command = AbstractEnforcement.addEffectedReadSubjectsToThingSignal(createThing, enforcer);
        return new CreateThingWithEnforcer(command, enforcer);
    }

    private CreateThingWithEnforcer enforceCreateThingByOwnInlinedPolicyOrThrow(final CreateThing createThing,
            final JsonObject inlinedPolicy) {

        final var initialPolicy = getInitialPolicy(createThing, inlinedPolicy);
        final var policiesValidator = PoliciesValidator.newInstance(initialPolicy);
        if (policiesValidator.isValid()) {
            final var initialEnforcer = PolicyEnforcers.defaultEvaluator(initialPolicy);
            return attachEnforcerOrThrow(createThing, initialEnforcer,
                    ThingCommandEnforcement::authorizeByPolicyOrThrow);
        } else {
            throw PolicyInvalidException.newBuilder(MIN_REQUIRED_POLICY_PERMISSIONS, createThing.getEntityId())
                    .dittoHeaders(createThing.getDittoHeaders())
                    .build();
        }
    }

    @SuppressWarnings("java:S1193")
    private Policy getInitialPolicy(final CreateThing createThing, final JsonObject inlinedPolicy) {
        try {
            // Java doesn't permit conversion of this early return into assignment to final variable.
            return PoliciesModelFactory.newPolicy(inlinedPolicy);
        } catch (final JsonRuntimeException | DittoJsonException e) {
            final var thingId = createThing.getEntityId();
            throw PolicyInvalidException.newBuilderForCause(e, thingId)
                    .dittoHeaders(createThing.getDittoHeaders())
                    .build();
        } catch (final DittoRuntimeException e) {
            final var dittoHeaders = createThing.getDittoHeaders();
            // PolicyException is an interface!
            if (e instanceof PolicyException) {
                // user error; no need to log stack trace.
                throw e.setDittoHeaders(dittoHeaders);
            } else {
                throw reportError("Error during creation of inline policy from JSON", e);
            }
        }
    }

    private static CreateThingWithEnforcer attachEnforcerOrThrow(final CreateThing command, final Enforcer enforcer,
            final BiFunction<Enforcer, ThingCommand<CreateThing>, CreateThing> authorization) {

        final CreateThing authorizedCommand = authorization.apply(enforcer, command);
        return new CreateThingWithEnforcer(authorizedCommand, enforcer);
    }

    /**
     * Transform a {@code ModifyThing} command sent to nonexistent thing to {@code CreateThing} command if it is sent to
     * a nonexistent thing.
     *
     * @param receivedCommand the command to transform.
     * @return {@code CreateThing} command containing the same information if the argument is a {@code ModifyThing}
     * command. Otherwise return the command itself.
     */
    private static ThingCommand<?> transformModifyThingToCreateThing(final ThingCommand<?> receivedCommand) {
        if (receivedCommand instanceof ModifyThing) {
            final var modifyThing = (ModifyThing) receivedCommand;
            final JsonObject initialPolicy = modifyThing.getInitialPolicy().orElse(null);
            final String policyIdOrPlaceholder = modifyThing.getPolicyIdOrPlaceholder().orElse(null);
            final var newThing = modifyThing.getThing().toBuilder()
                    .setId(modifyThing.getEntityId())
                    .build();
            return CreateThing.of(newThing, initialPolicy, policyIdOrPlaceholder, modifyThing.getDittoHeaders());
        } else {
            return receivedCommand;
        }
    }

    /**
     * Authorize a thing-command by a policy enforcer.
     *
     * @param <T> type of the thing-command.
     * @param policyEnforcer the policy enforcer.
     * @param command the command to authorize.
     * @return optionally the authorized command extended by read subjects.
     */
    static <T extends ThingCommand<T>> T authorizeByPolicyOrThrow(final Enforcer policyEnforcer,
            final ThingCommand<T> command) {

        final var thingResourceKey = PoliciesResourceType.thingResource(command.getResourcePath());
        final var dittoHeaders = command.getDittoHeaders();
        final var authorizationContext = dittoHeaders.getAuthorizationContext();

        final boolean commandAuthorized;
        if (command instanceof MergeThing) {
            commandAuthorized = enforceMergeThingCommand(policyEnforcer, (MergeThing) command, thingResourceKey,
                    authorizationContext);
        } else if (command instanceof ThingModifyCommand) {
            commandAuthorized = policyEnforcer.hasUnrestrictedPermissions(thingResourceKey, authorizationContext,
                    Permission.WRITE);
        } else {
            commandAuthorized =
                    policyEnforcer.hasPartialPermissions(thingResourceKey, authorizationContext, Permission.READ);
        }

        final var condition = dittoHeaders.getCondition();
        if (!(command instanceof CreateThing) && condition.isPresent()) {
            enforceReadPermissionOnCondition(condition.get(), policyEnforcer, dittoHeaders, () ->
                    ThingConditionFailedException.newBuilderForInsufficientPermission(dittoHeaders).build());
        }
        final var liveChannelCondition = dittoHeaders.getLiveChannelCondition();
        if ((command instanceof ThingQueryCommand) && liveChannelCondition.isPresent()) {
            enforceReadPermissionOnCondition(liveChannelCondition.get(), policyEnforcer, dittoHeaders, () ->
                    ThingConditionFailedException.newBuilderForInsufficientLiveChannelPermission(dittoHeaders).build());
        }

        if (commandAuthorized) {
            return AbstractEnforcement.addEffectedReadSubjectsToThingSignal(command, policyEnforcer);
        } else {
            throw errorForThingCommand(command);
        }
    }

    private static void enforceReadPermissionOnCondition(final String condition,
            final Enforcer policyEnforcer,
            final DittoHeaders dittoHeaders,
            final Supplier<DittoRuntimeException> exceptionSupplier) {

        final var authorizationContext = dittoHeaders.getAuthorizationContext();
        final var rootNode = tryParseRqlCondition(condition, dittoHeaders);
        final var resourceKeys = determineResourceKeys(rootNode, dittoHeaders);

        if (!policyEnforcer.hasUnrestrictedPermissions(resourceKeys, authorizationContext, Permission.READ)) {
            throw exceptionSupplier.get();
        }
    }

    private static RootNode tryParseRqlCondition(final String condition, final DittoHeaders dittoHeaders) {
        try {
            return RqlPredicateParser.getInstance().parse(condition);
        } catch (final ParserException e) {
            throw ThingConditionInvalidException.newBuilder(condition, e.getMessage())
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    private static Set<ResourceKey> determineResourceKeys(final RootNode rootNode, final DittoHeaders dittoHeaders) {
        final var visitor = FieldNamesPredicateVisitor.getNewInstance();
        visitor.visit(rootNode);
        final var extractedFieldNames = visitor.getFieldNames();

        return extractedFieldNames.stream()
                .map(fieldName -> tryGetResourceKey(fieldName, dittoHeaders))
                .collect(Collectors.toSet());
    }

    private static ResourceKey tryGetResourceKey(final String fieldName, final DittoHeaders dittoHeaders) {
        try {
            return PoliciesResourceType.thingResource(fieldName);
        } catch (final JsonPointerInvalidException e) {
            throw ThingConditionInvalidException.newBuilder(fieldName, e.getDescription().orElse(""))
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    private static boolean enforceMergeThingCommand(final Enforcer policyEnforcer,
            final MergeThing command, final ResourceKey thingResourceKey,
            final AuthorizationContext authorizationContext) {
        if (policyEnforcer.hasUnrestrictedPermissions(thingResourceKey, authorizationContext, Permission.WRITE)) {
            // unrestricted permissions at thingResourceKey level
            return true;
        } else if (policyEnforcer.hasPartialPermissions(thingResourceKey, authorizationContext, Permission.WRITE)) {
            // in case of partial permissions at thingResourceKey level check all leaves of merge patch for
            // unrestricted permissions
            final Set<ResourceKey> resourceKeys = calculateLeaves(command.getPath(), command.getValue());
            return policyEnforcer.hasUnrestrictedPermissions(resourceKeys, authorizationContext, Permission.WRITE);
        } else {
            // not even partial permission
            return false;
        }
    }

    private static Set<ResourceKey> calculateLeaves(final JsonPointer path, final JsonValue value) {
        if (value.isObject()) {
            return value.asObject().stream()
                    .map(f -> calculateLeaves(path.append(f.getKey().asPointer()), f.getValue()))
                    .reduce(new HashSet<>(), ThingCommandEnforcement::addAll, ThingCommandEnforcement::addAll);
        } else {
            return Set.of(PoliciesResourceType.thingResource(path));
        }
    }

    private static Set<ResourceKey> addAll(final Set<ResourceKey> result, final Set<ResourceKey> toBeAdded) {
        result.addAll(toBeAdded);
        return result;
    }

    /**
     * Check if inlined policy should be retrieved together with the thing.
     *
     * @param command the thing query command.
     * @return whether it is necessary to retrieve the thing's policy.
     */
    private static boolean shouldRetrievePolicyWithThing(final ThingCommand<?> command) {
        final var retrieveThing = (RetrieveThing) command;
        return retrieveThing.getSelectedFields()
                .filter(selector -> selector.getPointers()
                        .stream()
                        .anyMatch(jsonPointer -> jsonPointer.getRoot()
                                .filter(jsonKey -> Policy.INLINED_FIELD_NAME.equals(jsonKey.toString()))
                                .isPresent()))
                .isPresent();
    }

    private CompletionStage<Contextual<WithDittoHeaders>> handleInitialCreateThing(
            final CreateThing createThing, final Enforcer enforcer) {

        final CompletionStage<Contextual<WithDittoHeaders>> result;
        if (shouldCreatePolicyForCreateThing(createThing)) {
            checkForErrorsInCreateThingWithPolicy(createThing);
            result = createThingWithInitialPolicy(createThing, enforcer).thenApply(this::forwardToThingsShardRegion);
        } else if (createThing.getThing().getPolicyEntityId().isPresent()) {
            final var policyId = createThing.getThing()
                    .getPolicyEntityId()
                    .orElseThrow(IllegalStateException::new);
            checkForErrorsInCreateThingWithPolicy(createThing);
            result = enforceCreateThingForNonexistentThingWithPolicyId(createThing, policyId);
        } else {
            // nothing to do with policy, simply forward the command
            result = CompletableFuture.completedFuture(forwardToThingsShardRegion(createThing));
        }
        return result;
    }

    private static boolean shouldCreatePolicyForCreateThing(final CreateThing createThing) {
        return createThing.getInitialPolicy().isPresent() || createThing.getThing().getPolicyEntityId().isEmpty();
    }

    private static void checkForErrorsInCreateThingWithPolicy(final CreateThing command) {
        validatePolicyIdForCreateThing(command);
    }

    private static void validatePolicyIdForCreateThing(final CreateThing createThing) {
        final var thing = createThing.getThing();
        final Optional<String> policyIdOpt = thing.getPolicyEntityId().map(String::valueOf);
        final Optional<String> policyIdInPolicyOpt = createThing.getInitialPolicy()
                .flatMap(jsonObject -> jsonObject.getValue(Thing.JsonFields.POLICY_ID));

        final boolean isValid;
        if (policyIdOpt.isPresent()) {
            isValid = policyIdInPolicyOpt.isEmpty() || policyIdInPolicyOpt.equals(policyIdOpt);
        } else {
            isValid = true;
        }

        if (!isValid) {
            throw PolicyIdNotAllowedException.newBuilder(createThing.getEntityId())
                    .dittoHeaders(createThing.getDittoHeaders())
                    .build();
        }
    }

    private CompletionStage<CreateThing> createThingWithInitialPolicy(final CreateThing createThing,
            final Enforcer enforcer) {

        try {
            final Optional<Policy> policy =
                    getInlinedOrDefaultPolicyForCreateThing(createThing);

            if (policy.isPresent()) {

                final var dittoHeadersForCreatePolicy = DittoHeaders.newBuilder(createThing.getDittoHeaders())
                        .removePreconditionHeaders()
                        .responseRequired(true)
                        .build();

                final var createPolicy = CreatePolicy.of(policy.get(), dittoHeadersForCreatePolicy);
                final Optional<CreatePolicy> authorizedCreatePolicy =
                        PolicyCommandEnforcement.authorizePolicyCommand(createPolicy,
                                PolicyEnforcer.of(enforcer),
                                this.creationRestrictionEnforcer
                        );

                // CreatePolicy is rejected; abort CreateThing.
                return authorizedCreatePolicy
                        .map(cmd -> createPolicyAndThing(cmd, createThing))
                        .orElseThrow(() -> errorForThingCommand(createThing));
            } else {
                // cannot create policy.
                final var thingId = createThing.getEntityId();
                final var message = String.format("The Thing with ID '%s' could not be created with implicit " +
                        "Policy because no authorization subject is present.", thingId);
                throw ThingNotCreatableException.newBuilderForPolicyMissing(thingId, PolicyId.of(thingId))
                        .message(message)
                        .description(() -> null)
                        .dittoHeaders(createThing.getDittoHeaders())
                        .build();
            }
        } catch (final RuntimeException error) {
            throw reportError("error before creating thing with initial policy", error);
        }
    }

    private CompletionStage<CreateThing> createPolicyAndThing(final CreatePolicy createPolicy,
            final CreateThing createThingWithoutPolicyId) {

        final var createThing = CreateThing.of(
                createThingWithoutPolicyId.getThing().setPolicyId(createPolicy.getEntityId()),
                null,
                createThingWithoutPolicyId.getDittoHeaders());

        invalidatePolicyCache(createPolicy.getEntityId());

        return preEnforcer.apply(createPolicy)
                .thenCompose(msg -> Patterns.ask(policiesShardRegion, msg, getAskWithRetryConfig().getAskTimeout()
                                .multipliedBy(5L)) // don't retry creating policy (not idempotent!) - but increase default timeout for doing so
                        .thenApply(policyResponse -> {
                            handlePolicyResponseForCreateThing(createPolicy, createThing, policyResponse);
                            invalidateThingCaches(createThing.getEntityId());
                            return createThing;
                        })
                )
                .exceptionally(throwable -> {
                    if (throwable instanceof AskTimeoutException) {
                        throw PolicyUnavailableException.newBuilder(createPolicy.getEntityId())
                                .dittoHeaders(createThing.getDittoHeaders())
                                .build();
                    }
                    throw reportErrorOrResponse(String.format("creating initial policy during creation of Thing <%s>",
                            createThing.getEntityId()), null, throwable);
                });
    }

    private void handlePolicyResponseForCreateThing(final CreatePolicy createPolicy, final CreateThing createThing,
            final Object policyResponse) {

        if (!(policyResponse instanceof CreatePolicyResponse)) {
            if (shouldReportInitialPolicyCreationFailure(policyResponse)) {

                throw reportInitialPolicyCreationFailure(createPolicy.getEntityId(), createThing);
            } else if (isAskTimeoutException(policyResponse, null)) {

                throw PolicyUnavailableException.newBuilder(createPolicy.getEntityId())
                        .dittoHeaders(createThing.getDittoHeaders())
                        .build();
            } else {

                final var hint = String.format("creating initial policy during creation of Thing <%s>",
                        createThing.getEntityId());
                throw reportErrorOrResponse(hint, policyResponse, null);
            }
        }
    }

    private static boolean shouldReportInitialPolicyCreationFailure(final Object policyResponse) {
        return policyResponse instanceof PolicyConflictException ||
                policyResponse instanceof PolicyNotAccessibleException ||
                policyResponse instanceof NamespaceBlockedException;
    }

    private static ThingNotCreatableException reportInitialPolicyCreationFailure(final PolicyId policyId,
            final CreateThing command) {

        LOGGER.withCorrelationId(command)
                .info("Failed to create Policy with ID <{}> because it already exists." +
                        " The CreateThing command which would have created a Policy for the Thing with ID <{}>" +
                        " is therefore not handled.", policyId, command.getEntityId());
        return ThingNotCreatableException.newBuilderForPolicyExisting(command.getEntityId(), policyId)
                .dittoHeaders(command.getDittoHeaders())
                .build();
    }

    private static Optional<Policy> getInlinedOrDefaultPolicyForCreateThing(final CreateThing createThing) {
        final Optional<JsonObject> initialPolicy = createThing.getInitialPolicy();
        if (initialPolicy.isPresent()) {
            final JsonObject policyJson = initialPolicy.get();
            final JsonObjectBuilder policyJsonBuilder = policyJson.toBuilder();
            final var thing = createThing.getThing();
            if (thing.getPolicyEntityId().isPresent() || !policyJson.contains(Policy.JsonFields.ID.getPointer())) {
                final String policyId = thing.getPolicyEntityId()
                        .map(String::valueOf)
                        .orElse(createThing.getEntityId().toString());
                policyJsonBuilder.set(Policy.JsonFields.ID, policyId);
            }
            return Optional.of(PoliciesModelFactory.newPolicy(policyJsonBuilder.build()));
        } else {
            return getDefaultPolicy(createThing.getDittoHeaders().getAuthorizationContext(),
                    createThing.getEntityId());
        }
    }

    private static Optional<Policy> getDefaultPolicy(final AuthorizationContext authorizationContext,
            final ThingId thingId) {

        final Optional<Subject> subjectOptional = authorizationContext.getFirstAuthorizationSubject()
                .map(AuthorizationSubject::getId)
                .map(SubjectId::newInstance)
                .map(Subject::newInstance);

        return subjectOptional.map(subject ->
                Policy.newBuilder(PolicyId.of(thingId))
                        .forLabel(DEFAULT_POLICY_ENTRY_LABEL)
                        .setSubject(subject)
                        .setGrantedPermissions(PoliciesResourceType.thingResource("/"),
                                org.eclipse.ditto.things.api.Permission.DEFAULT_THING_PERMISSIONS)
                        .setGrantedPermissions(PoliciesResourceType.policyResource("/"),
                                org.eclipse.ditto.policies.api.Permission.DEFAULT_POLICY_PERMISSIONS)
                        .setGrantedPermissions(PoliciesResourceType.messageResource("/"),
                                org.eclipse.ditto.policies.api.Permission.DEFAULT_POLICY_PERMISSIONS)
                        .build());
    }

    private static ThingQueryCommandResponse<?> setTwinChannel(final ThingQueryCommandResponse<?> response) {
        return response.setDittoHeaders(response.getDittoHeaders()
                .toBuilder()
                .channel(TopicPath.Channel.TWIN.getName())
                .putHeaders(getAdditionalLiveResponseHeaders(response.getDittoHeaders()))
                .build());
    }

    private static ThingQueryCommand<?> ensureTwinChannel(final ThingQueryCommand<?> command) {
        if (SignalInformationPoint.isChannelLive(command)) {
            return command.setDittoHeaders(command.getDittoHeaders()
                    .toBuilder()
                    .channel(TopicPath.Channel.TWIN.getName())
                    .build());
        } else {
            return command;
        }
    }

    private static DittoHeaders getAdditionalLiveResponseHeaders(final DittoHeaders responseHeaders) {
        final var liveChannelConditionMatched = responseHeaders.getOrDefault(
                DittoHeaderDefinition.LIVE_CHANNEL_CONDITION_MATCHED.getKey(), Boolean.TRUE.toString());
        final DittoHeadersBuilder<?, ?> dittoHeadersBuilder = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.LIVE_CHANNEL_CONDITION_MATCHED.getKey(), liveChannelConditionMatched)
                .responseRequired(false);
        return dittoHeadersBuilder.build();
    }

    /**
     * A pair of {@code CreateThing} command with {@code Enforcer}.
     */
    private static final class CreateThingWithEnforcer {

        private final CreateThing createThing;
        private final Enforcer enforcer;

        private CreateThingWithEnforcer(final CreateThing createThing, final Enforcer enforcer) {
            this.createThing = createThing;
            this.enforcer = enforcer;
        }

    }

    /**
     * Provides {@link AbstractEnforcement} for commands of type {@link ThingCommand}.
     */
    public static final class Provider implements EnforcementProvider<ThingCommand<?>> {

        private final ActorSystem actorSystem;
        private final ActorRef thingsShardRegion;
        private final ActorRef policiesShardRegion;
        private final Cache<EnforcementCacheKey, Entry<EnforcementCacheKey>> thingIdCache;
        private final Cache<EnforcementCacheKey, Entry<Enforcer>> policyEnforcerCache;
        private final PreEnforcer preEnforcer;
        private final CreationRestrictionEnforcer creationRestrictionEnforcer;
        private final LiveSignalPub liveSignalPub;
        private final EnforcementConfig enforcementConfig;
        private final ResponseReceiverCache responseReceiverCache;

        /**
         * Constructor.
         *
         * @param actorSystem the ActorSystem for e.g. looking up config.
         * @param thingsShardRegion the ActorRef to the Things shard region.
         * @param policiesShardRegion the ActorRef to the Policies shard region.
         * @param thingIdCache the thing-id-cache.
         * @param policyEnforcerCache the policy-enforcer cache.
         * @param preEnforcer pre-enforcer function to block undesirable messages to policies shard region.
         * @param creationRestrictionEnforcer the enforcer for restricting entity creation.
         * @param liveSignalPub publisher of live signals.
         * @param enforcementConfig the enforcement config.
         */
        @SuppressWarnings("NullableProblems")
        public Provider(final ActorSystem actorSystem,
                final ActorRef thingsShardRegion,
                final ActorRef policiesShardRegion,
                final Cache<EnforcementCacheKey, Entry<EnforcementCacheKey>> thingIdCache,
                final Cache<EnforcementCacheKey, Entry<Enforcer>> policyEnforcerCache,
                @Nullable final PreEnforcer preEnforcer,
                @Nullable final CreationRestrictionEnforcer creationRestrictionEnforcer,
                final LiveSignalPub liveSignalPub,
                final EnforcementConfig enforcementConfig) {

            this.actorSystem = requireNonNull(actorSystem);
            this.thingsShardRegion = checkNotNull(thingsShardRegion, "thingShardRegion");
            this.policiesShardRegion = checkNotNull(policiesShardRegion, "policiesShardRegion");
            this.thingIdCache = checkNotNull(thingIdCache, "thingIdCache");
            this.policyEnforcerCache = checkNotNull(policyEnforcerCache, "policyEnforcerCache");
            this.preEnforcer = Objects.requireNonNullElseGet(preEnforcer, () -> CompletableFuture::completedFuture);
            this.creationRestrictionEnforcer = Optional.ofNullable(creationRestrictionEnforcer)
                    .orElse(CreationRestrictionEnforcer.NULL);
            this.liveSignalPub = checkNotNull(liveSignalPub, "liveSignalPub");
            this.enforcementConfig = enforcementConfig;
            responseReceiverCache = ResponseReceiverCache.lookup(actorSystem);
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes", "java:S3740"})
        public Class<ThingCommand<?>> getCommandClass() {
            return (Class) ThingCommand.class;
        }

        @Override
        public boolean isApplicable(final ThingCommand<?> command) {

            // live commands are not applicable for thing command enforcement
            // because they should never be forwarded to things shard region
            return !SignalInformationPoint.isChannelLive(command) || SignalInformationPoint.isChannelSmart(command);
        }

        @Override
        public boolean changesAuthorization(final ThingCommand<?> signal) {
            return signal instanceof ThingModifyCommand && ((ThingModifyCommand<?>) signal).changesAuthorization();
        }

        @Override
        public AbstractEnforcement<ThingCommand<?>> createEnforcement(final Contextual<ThingCommand<?>> context) {
            return new ThingCommandEnforcement(context,
                    actorSystem,
                    thingsShardRegion,
                    policiesShardRegion,
                    thingIdCache,
                    policyEnforcerCache,
                    preEnforcer,
                    creationRestrictionEnforcer,
                    liveSignalPub,
                    enforcementConfig,
                    responseReceiverCache);
        }

    }

}
