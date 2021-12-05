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

import static org.eclipse.ditto.policies.api.Permission.WRITE;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.commands.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.concierge.service.actors.LiveResponseAndAcknowledgementForwarder;
import org.eclipse.ditto.concierge.service.common.EnforcementConfig;
import org.eclipse.ditto.internal.models.signal.SignalInformationPoint;
import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.cacheloaders.EnforcementCacheKey;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;
import org.eclipse.ditto.internal.utils.pubsub.LiveSignalPub;
import org.eclipse.ditto.internal.utils.pubsub.StreamingType;
import org.eclipse.ditto.internal.utils.pubsub.extractors.AckExtractor;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.messages.model.MessageFormatInvalidException;
import org.eclipse.ditto.messages.model.MessageSendNotAllowedException;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.messages.model.signals.commands.SendClaimMessage;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.enforcers.EffectedSubjects;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.protocol.UnknownCommandException;
import org.eclipse.ditto.things.model.ThingConstants;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.exceptions.EventSendNotAllowedException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingUnavailableException;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommand;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommandResponse;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.japi.Pair;
import akka.pattern.Patterns;

/**
 * Enforces live commands (including message commands) and live events.
 */
public final class LiveSignalEnforcement extends AbstractEnforcementWithAsk<SignalWithEntityId<?>,
        ThingQueryCommandResponse<?>> {

    // TODO: configure
    static final Duration MIN_LIVE_TIMEOUT = Duration.ofSeconds(1L);
    static final Duration DEFAULT_LIVE_TIMEOUT = Duration.ofSeconds(60L);

    static final AckExtractor<ThingCommand<?>> THING_COMMAND_ACK_EXTRACTOR =
            AckExtractor.of(ThingCommand::getEntityId, ThingCommand::getDittoHeaders);
    private static final AckExtractor<ThingEvent<?>> THING_EVENT_ACK_EXTRACTOR =
            AckExtractor.of(ThingEvent::getEntityId, ThingEvent::getDittoHeaders);
    private static final AckExtractor<MessageCommand<?, ?>> MESSAGE_COMMAND_ACK_EXTRACTOR =
            AckExtractor.of(MessageCommand::getEntityId, MessageCommand::getDittoHeaders);

    private final EnforcerRetriever<Enforcer> enforcerRetriever;
    private final ActorRefFactory actorRefFactory;
    private final LiveSignalPub liveSignalPub;
    private final EnforcementConfig enforcementConfig;
    private final ResponseReceiverCache responseReceiverCache;

    private LiveSignalEnforcement(final Contextual<SignalWithEntityId<?>> context,
            final Cache<EnforcementCacheKey, Entry<EnforcementCacheKey>> thingIdCache,
            final Cache<EnforcementCacheKey, Entry<Enforcer>> policyEnforcerCache,
            final ActorRefFactory actorRefFactory,
            final ResponseReceiverCache responseReceiverCache,
            final LiveSignalPub liveSignalPub,
            final EnforcementConfig enforcementConfig) {

        super(context, ThingQueryCommandResponse.class);
        enforcerRetriever = PolicyEnforcerRetrieverFactory.create(thingIdCache, policyEnforcerCache);
        this.actorRefFactory = actorRefFactory;
        this.liveSignalPub = liveSignalPub;
        this.responseReceiverCache = responseReceiverCache;
        this.enforcementConfig = enforcementConfig;
    }

    @Override
    protected DittoRuntimeException handleAskTimeoutForCommand(final SignalWithEntityId<?> signal,
            final Throwable askTimeout) {
        log().info("Live command timed out. Response may be sent by another channel: <{}>", signal);
        return ThingUnavailableException.newBuilder(ThingId.of(signal.getEntityId()))
                .dittoHeaders(signal.getDittoHeaders())
                .build();
    }

    @Override
    protected ThingQueryCommandResponse<?> filterJsonView(final ThingQueryCommandResponse<?> commandResponse,
            final Enforcer enforcer) {
        try {
            return ThingCommandEnforcement.buildJsonViewForThingQueryCommandResponse(commandResponse, enforcer);
        } catch (final RuntimeException e) {
            throw reportError("Error after building JsonView", e);
        }
    }

    /**
     * {@link EnforcementProvider} for {@link LiveSignalEnforcement}.
     */
    public static final class Provider implements EnforcementProvider<SignalWithEntityId<?>> {

        /*
         * Defined as constant because it is crucial to use the same cache for
         * multiple instances of LiveSignalEnforcement to ensure that
         * correlating live commands with live responses works as expected.
         * Technically this constant could be defined in LiveSignalEnforcement
         * as well.
         */
        private static final ResponseReceiverCache RESPONSE_RECEIVER_CACHE = ResponseReceiverCache.newInstance();

        private final Cache<EnforcementCacheKey, Entry<EnforcementCacheKey>> thingIdCache;
        private final Cache<EnforcementCacheKey, Entry<Enforcer>> policyEnforcerCache;
        private final LiveSignalPub liveSignalPub;
        private final ActorRefFactory actorRefFactory;
        private final EnforcementConfig enforcementConfig;

        /**
         * Constructs a {@code Provider}.
         *
         * @param thingIdCache the thing-id-cache.
         * @param policyEnforcerCache the policy-enforcer cache.
         * @param actorRefFactory the actor ref factory to create new actors with.
         * @param liveSignalPub distributed-pub access for live signal publication.
         * @param enforcementConfig configuration properties for enforcement.
         * @throws NullPointerException if any argument but is {@code null}.
         */
        public Provider(final Cache<EnforcementCacheKey, Entry<EnforcementCacheKey>> thingIdCache,
                final Cache<EnforcementCacheKey, Entry<Enforcer>> policyEnforcerCache,
                final ActorRefFactory actorRefFactory,
                final LiveSignalPub liveSignalPub,
                final EnforcementConfig enforcementConfig) {

            this.thingIdCache = ConditionChecker.checkNotNull(thingIdCache, "thingIdCache");
            this.policyEnforcerCache = ConditionChecker.checkNotNull(policyEnforcerCache, "policyEnforcerCache");
            this.liveSignalPub = ConditionChecker.checkNotNull(liveSignalPub, "liveSignalPub");
            this.actorRefFactory = ConditionChecker.checkNotNull(actorRefFactory, "actorRefFactory");
            this.enforcementConfig = ConditionChecker.checkNotNull(enforcementConfig, "enforcementConfig");
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes", "java:S3740"})
        public Class<SignalWithEntityId<?>> getCommandClass() {
            return (Class) SignalWithEntityId.class;
        }

        @Override
        public boolean isApplicable(final SignalWithEntityId<?> signal) {
            return SignalInformationPoint.isChannelLive(signal);
        }

        @Override
        public LiveSignalEnforcement createEnforcement(final Contextual<SignalWithEntityId<?>> context) {
            return new LiveSignalEnforcement(context,
                    thingIdCache,
                    policyEnforcerCache,
                    actorRefFactory,
                    RESPONSE_RECEIVER_CACHE,
                    liveSignalPub,
                    enforcementConfig);
        }

    }

    @Override
    public CompletionStage<Contextual<WithDittoHeaders>> enforce() {
        return enforcerRetriever.retrieve(entityId(), (enforcerKeyEntry, enforcerEntry) -> {
            try {
                return doEnforce(signal(), enforcerEntry);
            } catch (final RuntimeException e) {
                return CompletableFuture.failedStage(e);
            }
        });
    }

    private CompletionStage<Contextual<WithDittoHeaders>> doEnforce(final SignalWithEntityId<?> liveSignal,
            final Entry<Enforcer> enforcerEntry) {

        final CompletionStage<Contextual<WithDittoHeaders>> result;
        final var correlationIdOpt = SignalInformationPoint.getCorrelationId(liveSignal);
        if (enforcerEntry.exists() && correlationIdOpt.isPresent()) {
            final Enforcer enforcer = enforcerEntry.getValueOrThrow();
            if (liveSignal instanceof SendClaimMessage) {

                // claim messages require no enforcement, publish them right away:
                result = publishMessageCommand((MessageCommand<?, ?>) liveSignal, enforcer);
            } else if (SignalInformationPoint.isCommandResponse(liveSignal)) {
                result = enforceLiveCommandResponse((CommandResponse<?>) liveSignal, correlationIdOpt.get(), enforcer);
            } else {
                final var streamingType = StreamingType.fromSignal(liveSignal);
                if (streamingType.isPresent()) {
                    result = enforceLiveSignal(streamingType.get(), liveSignal, enforcer);
                } else {
                    log().error("Unsupported Signal in LiveSignalEnforcement: <{}>", liveSignal);
                    throw GatewayInternalErrorException.newBuilder()
                            .dittoHeaders(liveSignal.getDittoHeaders())
                            .build();
                }
            }
        } else {
            // drop live command to nonexistent things and respond with error.
            log(liveSignal).info("Command of type <{}> with ID <{}> could not be dispatched as no enforcer " +
                            "could be looked up! Answering with ThingNotAccessibleException.", liveSignal.getType(),
                    liveSignal.getEntityId());
            throw ThingNotAccessibleException.newBuilder(ThingId.of(entityId().getId()))
                    .dittoHeaders(liveSignal.getDittoHeaders())
                    .build();
        }

        return result;
    }

    private CompletionStage<Contextual<WithDittoHeaders>> enforceLiveCommandResponse(
            final CommandResponse<?> liveResponse,
            final CharSequence correlationId,
            final Enforcer enforcer
    ) {
        final CompletionStage<Contextual<WithDittoHeaders>> result;
        if (enforcementConfig.isDispatchLiveResponsesGlobally()) {
            result = returnCommandResponseContextual(liveResponse, correlationId, enforcer);
        } else {
            log().info("Got live response when global dispatching is inactive: <{}> with correlation ID <{}>",
                    liveResponse.getType(),
                    SignalInformationPoint.getCorrelationId(liveResponse).orElse(""));

            result = CompletableFuture.completedFuture(withMessageToReceiver(null, null));
        }

        return result;
    }

    private CompletionStage<Contextual<WithDittoHeaders>> returnCommandResponseContextual(
            final CommandResponse<?> liveResponse,
            final CharSequence correlationId,
            final Enforcer enforcer
    ) {
        return responseReceiverCache.get(correlationId)
                .thenApply(responseReceiverEntry -> {
                    final Contextual<WithDittoHeaders> commandResponseContextual;
                    if (responseReceiverEntry.isPresent()) {
                        final var responseReceiver = responseReceiverEntry.get();
                        final CommandResponse<?> response;
                        if (liveResponse instanceof ThingQueryCommandResponse) {
                            final var liveResponseWithRequesterAuthCtx =
                                    injectRequestersAuthContext((ThingQueryCommandResponse<?>) liveResponse,
                                            responseReceiver.second());

                            response = ThingCommandEnforcement.buildJsonViewForThingQueryCommandResponse(
                                    liveResponseWithRequesterAuthCtx,
                                    enforcer);
                        } else {
                            response = liveResponse;
                        }
                        log().info("Scheduling CommandResponse <{}> to original sender <{}>", liveResponse,
                                responseReceiver);
                        commandResponseContextual = withMessageToReceiver(response, responseReceiver.first());
                    } else {
                        log().info("Got <{}> with unknown correlation ID: <{}>", liveResponse.getType(), correlationId);
                        commandResponseContextual = withMessageToReceiver(null, null);
                    }

                    return commandResponseContextual;
                });
    }

    private static ThingQueryCommandResponse<?> injectRequestersAuthContext(
            final ThingQueryCommandResponse<?> liveResponse,
            final AuthorizationContext requesterAuthContext) {

        final var dittoHeadersWithResponseReceiverAuthContext = liveResponse.getDittoHeaders()
                .toBuilder()
                .authorizationContext(requesterAuthContext)
                .build();

        return liveResponse.setDittoHeaders(dittoHeadersWithResponseReceiverAuthContext);
    }

    private CompletionStage<Contextual<WithDittoHeaders>> enforceLiveSignal(final StreamingType streamingType,
            final Signal<?> liveSignal, final Enforcer enforcer) {

        switch (streamingType) {
            case MESSAGES:
                return enforceMessageCommand((MessageCommand<?, ?>) liveSignal, enforcer);
            case LIVE_EVENTS:
                return enforceLiveEvent(liveSignal, enforcer);
            case LIVE_COMMANDS:
                ThingCommandEnforcement.authorizeByPolicyOrThrow(enforcer, (ThingCommand<?>) liveSignal);
                final ThingCommand<?> withReadSubjects =
                        addEffectedReadSubjectsToThingLiveSignal((ThingCommand<?>) liveSignal, enforcer);
                log(withReadSubjects).info("Live Command was authorized: <{}>", withReadSubjects);
                final boolean isThingQueryCommandRequiringResponse =
                        liveSignal instanceof ThingQueryCommand && liveSignal.getDittoHeaders().isResponseRequired();
                if (isThingQueryCommandRequiringResponse) {
                    return publishLiveQueryCommandAndBuildJsonView(withReadSubjects, enforcer);
                } else {
                    return publishLiveSignal(withReadSubjects, THING_COMMAND_ACK_EXTRACTOR, liveSignalPub.command());
                }
            default:
                log(liveSignal).warning("Ignoring unsupported command signal: <{}>", liveSignal);
                throw UnknownCommandException.newBuilder(liveSignal.getName())
                        .message("The sent command is not supported as live command")
                        .dittoHeaders(liveSignal.getDittoHeaders())
                        .build();
        }
    }

    private CompletionStage<Contextual<WithDittoHeaders>> publishLiveQueryCommandAndBuildJsonView(
            final ThingCommand<?> withReadSubjects, final Enforcer enforcer) {
        return addToResponseReceiver(withReadSubjects).thenApply(newSignal ->
                askAndBuildJsonViewWithAckForwarding((ThingCommand<?>) newSignal,
                        liveSignalPub.command(), enforcer));
    }

    /**
     * Extend a signal by subject headers given with granted and revoked READ access.
     * The subjects are provided by the given enforcer for the resource type {@link org.eclipse.ditto.things.model.ThingConstants#ENTITY_TYPE}.
     *
     * @param signal the signal to extend.
     * @param enforcer the enforcer.
     * @return the extended signal.
     */
    static <T extends Signal<T>> T addEffectedReadSubjectsToThingLiveSignal(final Signal<T> signal,
            final Enforcer enforcer) {

        final var resourceKey = ResourceKey.newInstance(ThingConstants.ENTITY_TYPE, signal.getResourcePath());
        final var effectedSubjects = enforcer.getSubjectsWithPermission(resourceKey, Permission.READ);
        final var newHeaders = DittoHeaders.newBuilder(signal.getDittoHeaders())
                .readGrantedSubjects(effectedSubjects.getGranted())
                .readRevokedSubjects(effectedSubjects.getRevoked())
                .build();

        return signal.setDittoHeaders(newHeaders);
    }

    private CompletionStage<Contextual<WithDittoHeaders>> enforceLiveEvent(final Signal<?> liveSignal,
            final Enforcer enforcer) {

        final boolean authorized = enforcer.hasUnrestrictedPermissions(
                PoliciesResourceType.thingResource(liveSignal.getResourcePath()),
                liveSignal.getDittoHeaders().getAuthorizationContext(), WRITE);

        if (authorized) {
            log(liveSignal).info("Live Event was authorized: <{}>", liveSignal);
            final ThingEvent<?> withReadSubjects =
                    addEffectedReadSubjectsToThingSignal((ThingEvent<?>) liveSignal, enforcer);

            return publishLiveSignal(withReadSubjects, THING_EVENT_ACK_EXTRACTOR, liveSignalPub.event());
        } else {
            log(liveSignal).info("Live Event was NOT authorized: <{}>", liveSignal);
            throw EventSendNotAllowedException.newBuilder(((ThingEvent<?>) liveSignal).getEntityId())
                    .dittoHeaders(liveSignal.getDittoHeaders())
                    .build();
        }
    }

    private CompletionStage<Contextual<WithDittoHeaders>> enforceMessageCommand(final MessageCommand<?, ?> command,
            final Enforcer enforcer) {
        if (isAuthorized(command, enforcer)) {
            return publishMessageCommand(command, enforcer);
        } else {
            return CompletableFuture.failedStage(rejectMessageCommand(command));
        }
    }

    private CompletionStage<Contextual<WithDittoHeaders>> publishMessageCommand(final MessageCommand<?, ?> command,
            final Enforcer enforcer) {

        final ResourceKey resourceKey =
                ResourceKey.newInstance(MessageCommand.RESOURCE_TYPE, command.getResourcePath());
        final EffectedSubjects effectedSubjects = enforcer.getSubjectsWithPermission(resourceKey, Permission.READ);
        final DittoHeaders headersWithReadSubjects = DittoHeaders.newBuilder(command.getDittoHeaders())
                .readGrantedSubjects(effectedSubjects.getGranted())
                .readRevokedSubjects(effectedSubjects.getRevoked())
                .build();
        final MessageCommand<?, ?> withReadSubjects = command.setDittoHeaders(headersWithReadSubjects);

        return publishLiveSignal(withReadSubjects, MESSAGE_COMMAND_ACK_EXTRACTOR, liveSignalPub.message());
    }

    private MessageSendNotAllowedException rejectMessageCommand(final MessageCommand<?, ?> command) {
        final MessageSendNotAllowedException error =
                MessageSendNotAllowedException.newBuilder(command.getEntityId())
                        .dittoHeaders(command.getDittoHeaders())
                        .build();

        log(command).info(
                "The command <{}> was not forwarded due to insufficient rights {}: {} - AuthorizationContext: {}",
                command.getType(), error.getClass().getSimpleName(), error.getMessage(),
                command.getDittoHeaders().getAuthorizationContext());

        return error;
    }

    @SuppressWarnings("unchecked")
    private <T extends Signal<?>, S extends T> CompletionStage<Contextual<WithDittoHeaders>> publishLiveSignal(
            final S signal,
            final AckExtractor<S> ackExtractor,
            final DistributedPub<T> pub) {

        // using pub/sub to publish the command to any interested parties (e.g. a Websocket):
        log(signal).debug("Publish message to pub-sub: <{}>", signal);

        return addToResponseReceiver(signal)
                .thenApply(newSignal -> withMessageToReceiver(newSignal,
                        pub.getPublisher(),
                        obj -> pub.wrapForPublicationWithAcks((S) obj, ackExtractor)));
    }

    private Contextual<WithDittoHeaders> askAndBuildJsonViewWithAckForwarding(
            final ThingCommand<?> signal,
            final DistributedPub<ThingCommand<?>> pub,
            final Enforcer enforcer) {

        final var props = LiveResponseAndAcknowledgementForwarder.props(signal, pub.getPublisher(), sender());
        final var liveResponseForwarder = actorRefFactory.actorOf(props);
        final var startTime = Instant.now();
        return withMessageToReceiverViaAskFuture(signal, sender(), () -> {
            final BiFunction<ActorRef, Object, CompletionStage<ThingQueryCommandResponse<?>>> askStrategy =
                    (toAsk, message) -> {
                        // TODO: move timeout adjustment to thing command enforcement
                        final var timeout = getAdjustedTimeout(signal, startTime);
                        final var signalWithAdjustedTimeout = adjustTimeout(signal, timeout);
                        final var publish =
                                pub.wrapForPublicationWithAcks(signalWithAdjustedTimeout, THING_COMMAND_ACK_EXTRACTOR);
                        return Patterns.ask(toAsk, publish, timeout)
                                .thenApply(getResponseCaster(signal, "before building JsonView"));
                    };
            return ask(liveResponseForwarder, signal, askStrategy)
                    .thenApply(response -> filterJsonView(response, enforcer));
        });
    }

    private CompletionStage<Signal<?>> addToResponseReceiver(final Signal<?> signal) {
        final CompletionStage<Signal<?>> result;
        final var dittoHeaders = signal.getDittoHeaders();
        if (enforcementConfig.isDispatchLiveResponsesGlobally() &&
                SignalInformationPoint.isCommand(signal) &&
                dittoHeaders.isResponseRequired()) {

            result = insertResponseReceiverConflictFree((Command<?>) signal,
                    Pair.create(sender(), dittoHeaders.getAuthorizationContext()));
        } else {
            result = CompletableFuture.completedStage(signal);
        }

        return result;
    }

    private static boolean isAuthorized(final MessageCommand<?, ?> command, final Enforcer enforcer) {
        return enforcer.hasUnrestrictedPermissions(extractMessageResourceKey(command),
                command.getDittoHeaders().getAuthorizationContext(), WRITE);
    }

    private static ResourceKey extractMessageResourceKey(final MessageCommand<?, ?> command) {
        try {
            return PoliciesResourceType.messageResource(command.getResourcePath());
        } catch (final IllegalArgumentException e) {
            throw MessageFormatInvalidException.newBuilder(JsonFactory.nullArray())
                    .message("Unable to determine message resource path.")
                    .description("Please verify that the thing ID, message subject and direction are set correctly.")
                    .dittoHeaders(command.getDittoHeaders())
                    .build();
        }
    }

    private CompletionStage<Signal<?>> insertResponseReceiverConflictFree(final Command<?> command,
            final Pair<ActorRef, AuthorizationContext> responseReceiver) {

        return setUniqueCorrelationIdForGlobalDispatching(command)
                .thenApply(commandWithUniqueCorrelationId -> {
                    responseReceiverCache.putCommand(commandWithUniqueCorrelationId, responseReceiver);
                    return commandWithUniqueCorrelationId;
                });
    }

    private CompletionStage<Command<?>> setUniqueCorrelationIdForGlobalDispatching(final Command<?> command) {
        final var correlationId = SignalInformationPoint.getCorrelationId(command)
                .orElseGet(() -> UUID.randomUUID().toString());

        return responseReceiverCache.get(correlationId)
                .thenCompose(entry -> {
                    final CompletionStage<String> uniqueCorrelationIdFuture;
                    if (entry.isPresent()) {
                        uniqueCorrelationIdFuture = findUniqueCorrelationId(correlationId, getNextSuffix());
                    } else {
                        uniqueCorrelationIdFuture = CompletableFuture.completedStage(correlationId);
                    }
                    return uniqueCorrelationIdFuture.thenApply(newCorrelationId -> {
                        final Command<?> result;
                        if (correlationId.equals(newCorrelationId)) {
                            result = command;
                        } else {
                            result = command.setDittoHeaders(DittoHeaders.newBuilder(command.getDittoHeaders())
                                    // always set "keep-cid" to true because global dispatching is active
                                    .correlationId(newCorrelationId)
                                    .build());
                        }
                        return result;
                    });
                });
    }

    private static String getNextSuffix() {
        return Long.toHexString(Double.doubleToRawLongBits(Math.random()));
    }

    private CompletionStage<String> findUniqueCorrelationId(final String startingId, final String suffix) {
        final var nextCorrelationId = startingId + "#x" + suffix;
        return responseReceiverCache.get(nextCorrelationId)
                .thenCompose(entry -> {
                    if (entry.isPresent()) {
                        return findUniqueCorrelationId(startingId, getNextSuffix());
                    } else {
                        return CompletableFuture.completedStage(nextCorrelationId);
                    }
                });
    }

    private static Duration getAdjustedTimeout(final Signal<?> signal, final Instant startTime) {
        final var baseTimeout = signal.getDittoHeaders().getTimeout().orElse(DEFAULT_LIVE_TIMEOUT);
        final var adjustedTimeout = baseTimeout.minus(Duration.between(startTime, Instant.now()));
        return adjustedTimeout.minus(MIN_LIVE_TIMEOUT).isNegative() ? MIN_LIVE_TIMEOUT : adjustedTimeout;
    }

    private static ThingCommand<?> adjustTimeout(final ThingCommand<?> command, final Duration adjustedTimeout) {
        return command.setDittoHeaders(
                command.getDittoHeaders()
                        .toBuilder()
                        .timeout(adjustedTimeout)
                        .build()
        );
    }
}
