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

import static org.eclipse.ditto.policies.api.Permission.WRITE;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.UnsupportedSignalException;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
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
import org.eclipse.ditto.policies.enforcement.AbstractEnforcementWithAsk;
import org.eclipse.ditto.policies.enforcement.Contextual;
import org.eclipse.ditto.policies.enforcement.EnforcementReloaded;
import org.eclipse.ditto.policies.enforcement.EnforcerRetriever;
import org.eclipse.ditto.policies.enforcement.LiveResponseAndAcknowledgementForwarder;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.enforcement.config.EnforcementConfig;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.enforcers.EffectedSubjects;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.things.model.ThingConstants;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;
import org.eclipse.ditto.things.model.signals.commands.exceptions.EventSendNotAllowedException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommand;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommandResponse;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;

/**
 * Enforces live commands (including message commands) and live events.
 */
public final class LiveSignalEnforcement extends AbstractEnforcementWithAsk<SignalWithEntityId<?>,
        ThingQueryCommandResponse<?>> {

    private static final Duration MIN_LIVE_TIMEOUT = Duration.ofSeconds(1L);
    private static final Duration DEFAULT_LIVE_TIMEOUT = Duration.ofSeconds(60L);

    private static final AckExtractor<ThingCommand<?>> THING_COMMAND_ACK_EXTRACTOR =
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
    protected Optional<DittoRuntimeException> handleAskTimeoutForCommand(final SignalWithEntityId<?> signal,
            final Throwable askTimeout) {
        log().info("Live command timed out. Response may be sent by another channel: <{}>", signal);
        return Optional.empty();
    }

    @Override
    public ThingQueryCommandResponse<?> filterJsonView(final ThingQueryCommandResponse<?> commandResponse,
            final Enforcer enforcer) {
        try {
            return ThingCommandEnforcement.buildJsonViewForThingQueryCommandResponse(commandResponse, enforcer);
        } catch (final RuntimeException e) {
            throw reportError("Error after building JsonView", e);
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
        final var correlationIdOpt = WithDittoHeaders.getCorrelationId(liveSignal);
        if (enforcerEntry.exists() && correlationIdOpt.isPresent()) {
            final Enforcer enforcer = enforcerEntry.getValueOrThrow();
            if (liveSignal instanceof SendClaimMessage) {

                // claim messages require no enforcement, publish them right away:
                result = publishMessageCommand((MessageCommand<?, ?>) liveSignal, enforcer);
            } else if (CommandResponse.isCommandResponse(liveSignal)) {
                result = enforceLiveCommandResponse((CommandResponse<?>) liveSignal, correlationIdOpt.get());
            } else {
                final var streamingType = StreamingType.fromSignal(liveSignal);
                if (streamingType.isPresent()) {
                    result = null;
                    // TODO TJ fix the following:
//                    result = enforceLiveSignal(streamingType.get(), liveSignal, enforcer);
                } else {
                    log().error("Unsupported Signal in LiveSignalEnforcement: <{}>", liveSignal);
                    throw DittoInternalErrorException.newBuilder()
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
            final CharSequence correlationId
    ) {
        final CompletionStage<Contextual<WithDittoHeaders>> result;
        if (enforcementConfig.isDispatchLiveResponsesGlobally()) {
            result = returnCommandResponseContextual(liveResponse, correlationId);
        } else {
            log().info("Got live response when global dispatching is inactive: <{}> with correlation ID <{}>",
                    liveResponse.getType(),
                    WithDittoHeaders.getCorrelationId(liveResponse).orElse(""));

            result = CompletableFuture.completedFuture(withMessageToReceiver(null, null));
        }

        return result;
    }

    private CompletionStage<Contextual<WithDittoHeaders>> returnCommandResponseContextual(
            final CommandResponse<?> liveResponse,
            final CharSequence correlationId) {

        return responseReceiverCache.get(correlationId)
                .thenApply(responseReceiverEntry -> {
                    final Contextual<WithDittoHeaders> commandResponseContextual;
                    if (responseReceiverEntry.isPresent()) {
                        final var receiver = responseReceiverEntry.get();
                        log().info("Scheduling CommandResponse <{}> to original sender <{}>", liveResponse, receiver);
                        commandResponseContextual = withMessageToReceiver(liveResponse, receiver);
                        responseReceiverCache.invalidate(correlationId);
                    } else {
                        log().info("Got <{}> with unknown correlation ID: <{}>", liveResponse.getType(), correlationId);
                        commandResponseContextual = withMessageToReceiver(null, null);
                    }
                    return commandResponseContextual;
                });
    }

    /**
     * TODO TJ use somewhere
     * @param streamingType
     * @param liveSignal
     * @param enforcer
     * @return
     */
    private CompletionStage<Contextual<WithDittoHeaders>> enforceLiveSignal(final StreamingType streamingType,
            final Signal<?> liveSignal, final PolicyEnforcer enforcer) {

        switch (streamingType) {
            case MESSAGES:
                return enforceMessageCommand((MessageCommand<?, ?>) liveSignal, enforcer.getEnforcer());
            case LIVE_EVENTS:
                return enforceLiveEvent(liveSignal, enforcer.getEnforcer());
            case LIVE_COMMANDS:
                ThingCommandEnforcement.authorizeByPolicyOrThrow(enforcer.getEnforcer(), (ThingCommand<?>) liveSignal);
                final ThingCommand<?> withReadSubjects =
                        addEffectedReadSubjectsToThingLiveSignal((ThingCommand<?>) liveSignal, enforcer.getEnforcer());
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
                throw UnsupportedSignalException.newBuilder(liveSignal.getType())
                        .message("The sent command is not supported as live command")
                        .dittoHeaders(liveSignal.getDittoHeaders())
                        .build();
        }
    }

    private CompletionStage<Contextual<WithDittoHeaders>> publishLiveQueryCommandAndBuildJsonView(
            final ThingCommand<?> withReadSubjects, final PolicyEnforcer policyEnforcer) {
        if (enforcementConfig.shouldDispatchGlobally(withReadSubjects)) {
            return responseReceiverCache.insertResponseReceiverConflictFree(
                    withReadSubjects,
                    this::createReceiverActor,
                    (command, receiver) -> askAndBuildJsonViewWithReceiverActor(command, receiver, policyEnforcer)
            );
        } else {
            final var receiver = createReceiverActor(withReadSubjects);
            final var result = askAndBuildJsonViewWithReceiverActor(withReadSubjects, receiver, policyEnforcer);
            return CompletableFuture.completedStage(result);
        }
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
        final var newHeaders = signal.getDittoHeaders()
                .toBuilder()
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
        final var headersWithReadSubjects = command.getDittoHeaders()
                .toBuilder()
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

    private <T extends Signal<?>, S extends T> CompletionStage<Contextual<WithDittoHeaders>> publishLiveSignal(
            final S signal,
            final AckExtractor<S> ackExtractor,
            final DistributedPub<T> pub) {

        // using pub/sub to publish the command to any interested parties (e.g. a Websocket):

        if (enforcementConfig.shouldDispatchGlobally(signal)) {
            return responseReceiverCache.insertResponseReceiverConflictFree(signal,
                    newSignal -> sender(),
                    (newSignal, receiver) -> {
                        log(newSignal).debug("Publish message to pub-sub: <{}>", newSignal);
                        return publishSignal(newSignal, ackExtractor, pub);
                    });
        } else {
            log(signal).debug("Publish message to pub-sub: <{}>", signal);
            return CompletableFuture.completedStage(publishSignal(signal, ackExtractor, pub));
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Signal<?>, S extends T> Contextual<WithDittoHeaders> publishSignal(final T signal,
            final AckExtractor<S> ackExtractor, final DistributedPub<T> pub) {
        return withMessageToReceiver(signal, pub.getPublisher(),
                obj -> pub.wrapForPublicationWithAcks((S) obj, ackExtractor));
    }

    private ActorRef createReceiverActor(final Command<?> signal) {
        final var pub = liveSignalPub.command();
        final var props = LiveResponseAndAcknowledgementForwarder.props(signal, pub.getPublisher(), sender());
        return actorRefFactory.actorOf(props);
    }

    private Contextual<WithDittoHeaders> askAndBuildJsonViewWithReceiverActor(
            final ThingCommand<?> thingCommand,
            final ActorRef receiver,
            final PolicyEnforcer policyEnforcer) {

        final var startTime = Instant.now();
        final var pub = liveSignalPub.command();
        final var responseCaster = getResponseCaster(thingCommand, "before building JsonView")
                .<CompletionStage<ThingQueryCommandResponse<?>>>andThen(CompletableFuture::completedStage);

        return null;
        // TODO TJ fix the following:
//        return withMessageToReceiverViaAskFuture(thingCommand, sender(), () ->
//                adjustTimeoutAndFilterLiveQueryResponse(this, thingCommand, startTime, pub, receiver, policyEnforcer,
//                        responseCaster));
    }

    static CompletionStage<ThingQueryCommandResponse<?>> adjustTimeoutAndFilterLiveQueryResponse(
            final EnforcementReloaded<? super ThingCommand<?>, ThingCommandResponse<?>> enforcement,
            final ThingCommand<?> command,
            final Instant startTime,
            final DistributedPub<ThingCommand<?>> pub,
            final ActorRef liveResponseForwarder,
            final PolicyEnforcer policyEnforcer,
            final Function<Object, CompletionStage<ThingQueryCommandResponse<?>>> responseCaster) {

        final var timeout = getAdjustedTimeout(command, startTime);
        final var signalWithAdjustedTimeout = adjustTimeout(command, timeout);
        final var publish = pub.wrapForPublicationWithAcks(signalWithAdjustedTimeout,
                LiveSignalEnforcement.THING_COMMAND_ACK_EXTRACTOR);

        return CompletableFuture.completedStage(null);
        // TODO TJ fix the following:
//        return Patterns.ask(liveResponseForwarder, publish, timeout)
//                .exceptionally(e -> e)
//                .thenCompose(responseCaster)
//                .thenApply(response -> {
//                    if (null != response) {
//                        return enforcement.filterResponse(replaceAuthContext(response, command), policyEnforcer);
//                    } else {
//                        return null;
//                    }
//                });
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

    static Duration getLiveSignalTimeout(final Signal<?> signal) {
        return signal.getDittoHeaders().getTimeout().orElse(DEFAULT_LIVE_TIMEOUT);
    }

    private static Duration getAdjustedTimeout(final Signal<?> signal, final Instant startTime) {
        final var baseTimeout = getLiveSignalTimeout(signal);
        final var adjustedTimeout = baseTimeout.minus(Duration.between(startTime, Instant.now()));
        return adjustedTimeout.minus(MIN_LIVE_TIMEOUT).isNegative() ? MIN_LIVE_TIMEOUT : adjustedTimeout;
    }

    private static ThingCommand<?> adjustTimeout(final ThingCommand<?> signal, final Duration adjustedTimeout) {
        return signal.setDittoHeaders(
                signal.getDittoHeaders()
                        .toBuilder()
                        .timeout(adjustedTimeout)
                        .build()
        );
    }

    static ThingCommandResponse<?> replaceAuthContext(final ThingCommandResponse<?> response,
            final WithDittoHeaders command) {
        return response.setDittoHeaders(response.getDittoHeaders()
                .toBuilder()
                .authorizationContext(command.getDittoHeaders().getAuthorizationContext())
                .build());
    }
}
