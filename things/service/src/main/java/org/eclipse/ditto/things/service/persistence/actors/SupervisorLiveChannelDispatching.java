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

package org.eclipse.ditto.things.service.persistence.actors;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.UnsupportedSignalException;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.commands.exceptions.CommandTimeoutException;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.persistentactors.DistributedPubWithMessage;
import org.eclipse.ditto.internal.utils.persistentactors.TargetActorWithMessage;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;
import org.eclipse.ditto.internal.utils.pubsub.StreamingType;
import org.eclipse.ditto.internal.utils.pubsub.extractors.AckExtractor;
import org.eclipse.ditto.internal.utils.pubsubthings.LiveSignalPub;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.policies.enforcement.config.EnforcementConfig;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommand;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.pattern.AskTimeoutException;

/**
 * Functionality used in {@link ThingSupervisorActor} for dispatching {@code "live"} channel messages.
 */
final class SupervisorLiveChannelDispatching {

    private static final Duration MIN_LIVE_TIMEOUT = Duration.ofSeconds(1L);
    private static final Duration DEFAULT_LIVE_TIMEOUT = Duration.ofSeconds(60L);

    private static final AckExtractor<ThingCommand<?>> THING_COMMAND_ACK_EXTRACTOR =
            AckExtractor.of(ThingCommand::getEntityId, ThingCommand::getDittoHeaders);
    private static final AckExtractor<ThingEvent<?>> THING_EVENT_ACK_EXTRACTOR =
            AckExtractor.of(ThingEvent::getEntityId, ThingEvent::getDittoHeaders);
    private static final AckExtractor<MessageCommand<?, ?>> MESSAGE_COMMAND_ACK_EXTRACTOR =
            AckExtractor.of(MessageCommand::getEntityId, MessageCommand::getDittoHeaders);

    private final DittoDiagnosticLoggingAdapter log;
    private final EnforcementConfig enforcementConfig;
    private final ResponseReceiverCache responseReceiverCache;
    private final LiveSignalPub liveSignalPub;
    private final ActorRefFactory actorRefFactory;

    SupervisorLiveChannelDispatching(final DittoDiagnosticLoggingAdapter log,
            final EnforcementConfig enforcementConfig,
            final ResponseReceiverCache responseReceiverCache,
            final LiveSignalPub liveSignalPub,
            final ActorRefFactory actorRefFactory) {

        this.log = log;
        this.enforcementConfig = enforcementConfig;
        this.responseReceiverCache = responseReceiverCache;
        this.liveSignalPub = liveSignalPub;
        this.actorRefFactory = actorRefFactory;
    }

    /**
     * Dispatches the passed in {@code thingQueryCommand} to the "live" channel applying the global dispatching of live
     * responses if globally configured via {@link EnforcementConfig#shouldDispatchGlobally(Signal)}.
     * Creates a new Actor {@link LiveResponseAndAcknowledgementForwarder} as child of the configured
     * {@code actorRefFactory} of this instance which is responsible for aggregating live responses and for forwarding
     * acknowledgements.
     *
     * @param thingQueryCommand the command to handle as "live" query command
     * @param responseHandler a response handler function creating the instance of {@link TargetActorWithMessage} the
     * returned CompletionStage will be completed with
     * @return a CompletionStage which will be completed with a target actor and a message to send to this target actor
     */
    CompletionStage<TargetActorWithMessage> dispatchLiveChannelThingQueryCommand(
            final ThingQueryCommand<?> thingQueryCommand,
            final BiFunction<ThingQueryCommand<?>, ActorRef, TargetActorWithMessage> responseHandler) {

        if (enforcementConfig.shouldDispatchGlobally(thingQueryCommand)) {
            return responseReceiverCache.insertResponseReceiverConflictFree(thingQueryCommand,
                    this::createLiveResponseReceiverActor,
                    responseHandler
            );
        } else {
            final var receiver = createLiveResponseReceiverActor(thingQueryCommand);
            return CompletableFuture.completedStage(responseHandler.apply(thingQueryCommand, receiver));
        }
    }

    /**
     * Creates the default response handler function creating an instance of {@link TargetActorWithMessage} based on
     * a {@code thingQueryCommand} and a receiver of a for pub/sub prepared, wrapped message.
     *
     * @param thingQueryCommand the command to prepare for publishing via pub/sub
     * @param receiver the receiver of the pub/sub message
     * @return a target actor and a for pub/sub prepared to send to this target actor to
     */
    TargetActorWithMessage prepareForPubSubPublishing(final ThingQueryCommand<?> thingQueryCommand,
            final ActorRef receiver) {

        return prepareForPubSubPublishing(thingQueryCommand, receiver, UnaryOperator.identity());
    }

    /**
     * Creates the default response handler function creating an instance of {@link TargetActorWithMessage} based on
     * a {@code thingQueryCommand} and a receiver of a for pub/sub prepared, wrapped message.
     *
     * @param thingQueryCommand the command to prepare for publishing via pub/sub
     * @param receiver the receiver of the pub/sub message
     * @param responseOrErrorConverter a converter function which takes responses and {@link Throwable} errors and
     * to create a response from, e.g. in order to convert responses or translate technical exceptions like an
     * {@code AskTimeoutException} to a DittoRuntimeException instead
     * @return a target actor and a for pub/sub prepared to send to this target actor to
     */
    TargetActorWithMessage prepareForPubSubPublishing(final ThingQueryCommand<?> thingQueryCommand,
            final ActorRef receiver,
            final UnaryOperator<Object> responseOrErrorConverter) {

        final var startTime = Instant.now();
        final var timeout = getAdjustedTimeout(thingQueryCommand, startTime);
        final var signalWithAdjustedTimeout = adjustTimeout(thingQueryCommand, timeout);
        final var pub = liveSignalPub.command();
        final var publish =
                pub.wrapForPublicationWithAcks(signalWithAdjustedTimeout, THING_COMMAND_ACK_EXTRACTOR);

        return new TargetActorWithMessage(
                receiver,
                publish,
                timeout,
                responseOrErrorConverter
        );
    }

    /**
     * Dispatches the passed in {@code signal} by preparing it for the "live" channel applying the global dispatching
     * of live responses if globally configured via {@link EnforcementConfig#shouldDispatchGlobally(Signal)}.
     *
     * @param signal the signal to publish via the "live" channel via pub/sub
     * @param sender the original sender
     * @return a CompletionStage which will be completed with a target actor and a message to send to this target actor
     */
    CompletionStage<TargetActorWithMessage> dispatchLiveSignal(final Signal<?> signal, final ActorRef sender) {

        final DistributedPubWithMessage distributedPubWithMessage = selectLiveSignalPublisher(signal);
        if (enforcementConfig.shouldDispatchGlobally(signal)) {
            return responseReceiverCache.insertResponseReceiverConflictFree(signal,
                            newSignal -> sender,
                            (newSignal, receiver) -> {
                                log.withCorrelationId(newSignal)
                                        .info("Publishing message to pub-sub: <{}>", newSignal.getType());
                                if (newSignal.equals(signal)) {
                                    return distributedPubWithMessage;
                                } else {
                                    return selectLiveSignalPublisher(newSignal);
                                }
                            })
                    .thenApply(distributedPub -> new TargetActorWithMessage(
                            distributedPub.pub().getPublisher(),
                            distributedPub.wrappedSignalForPublication(),
                            distributedPub.signal().getDittoHeaders().getTimeout().orElse(DEFAULT_LIVE_TIMEOUT),
                            response -> handleEncounteredAskTimeoutsAsCommandTimeoutException(
                                    signal, distributedPub, response)
                    ));
        } else {
            log.withCorrelationId(signal)
                    .debug("Publish message to pub-sub: <{}>", signal);
            return CompletableFuture.completedStage(new TargetActorWithMessage(
                    distributedPubWithMessage.pub().getPublisher(),
                    distributedPubWithMessage.wrappedSignalForPublication(),
                    signal.getDittoHeaders().getTimeout().orElse(DEFAULT_LIVE_TIMEOUT),
                    Function.identity()
            ));
        }
    }

    /**
     * Dispatches the passed in {@code commandResponse} which was received as "live" response globally without knowing
     * the sender of the command by using the {@code responseReceiverCache} to find out the originating sender and
     * sending it back.
     *
     * @param commandResponse the global "live" command response to dispatch
     * @return a CompletionStage which will be completed with a target actor and a message to send to this target actor
     */
    CompletionStage<TargetActorWithMessage> dispatchGlobalLiveCommandResponse(
            final CommandResponse<?> commandResponse) {

        return WithDittoHeaders.getCorrelationId(commandResponse).map(correlationId ->
                dispatchLiveCommandResponse(commandResponse, correlationId)
        ).orElseGet(() -> CompletableFuture.completedStage(null));
    }

    private CompletionStage<TargetActorWithMessage> dispatchLiveCommandResponse(
            final CommandResponse<?> liveResponse,
            final CharSequence correlationId) {

        final CompletionStage<TargetActorWithMessage> result;
        if (enforcementConfig.isDispatchLiveResponsesGlobally()) {
            result = returnCommandResponseContext(liveResponse, correlationId);
        } else {
            log.withCorrelationId(liveResponse)
                    .warning("Got live response when global dispatching is inactive: <{}>", liveResponse.getType());
            result = CompletableFuture.completedFuture(null);
        }
        return result;
    }

    private CompletionStage<TargetActorWithMessage> returnCommandResponseContext(
            final CommandResponse<?> liveResponse,
            final CharSequence correlationId) {

        return responseReceiverCache.get(correlationId)
                .thenApply(responseReceiverEntry -> {
                    final TargetActorWithMessage targetActorWithMessage;
                    if (responseReceiverEntry.isPresent()) {
                        final var receiver = responseReceiverEntry.get();
                        log.withCorrelationId(liveResponse)
                                .info("Responding with 'live' CommandResponse type <{}> to original sender <{}>",
                                        liveResponse.getType(), receiver);
                        targetActorWithMessage = new TargetActorWithMessage(receiver.sender(),
                                liveResponse,
                                Duration.ZERO, // ZERO duration means that no "ask" is used, but "tell" - not expecting an answer
                                Function.identity()
                        );
                        responseReceiverCache.invalidate(correlationId);
                    } else {
                        log.withCorrelationId(liveResponse)
                                .warning("Got <{}> with unknown correlation ID: <{}>", liveResponse.getType(),
                                        correlationId);
                        targetActorWithMessage = null;
                    }
                    return targetActorWithMessage;
                });
    }

    private Object handleEncounteredAskTimeoutsAsCommandTimeoutException(final Signal<?> signal,
            final DistributedPubWithMessage distributedPub,
            final Object response) {

        if (response instanceof Throwable t) {
            Throwable throwable = t;
            if (t instanceof CompletionException completionException) {
                throwable = completionException.getCause();
            }

            if (throwable instanceof AskTimeoutException askTimeoutException) {
                return CommandTimeoutException.newBuilder(
                                distributedPub.signal().getDittoHeaders().getTimeout().orElse(DEFAULT_LIVE_TIMEOUT)
                        )
                        .cause(askTimeoutException)
                        .dittoHeaders(signal.getDittoHeaders())
                        .build();
            } else {
                return response;
            }
        } else {
            return response;
        }
    }

    private ActorRef createLiveResponseReceiverActor(final ThingQueryCommand<?> thingQueryCommand) {

        final var pub = liveSignalPub.command();
        final var props = LiveResponseAndAcknowledgementForwarder.props(thingQueryCommand, pub.getPublisher());
        // and start the actor using the provided actorRefFactory
        return actorRefFactory.actorOf(props);
    }

    private static Duration getAdjustedTimeout(final Signal<?> signal, final Instant startTime) {

        final var baseTimeout = getLiveSignalTimeout(signal);
        final var adjustedTimeout = baseTimeout.minus(Duration.between(startTime, Instant.now()));
        return adjustedTimeout.minus(MIN_LIVE_TIMEOUT).isNegative() ? MIN_LIVE_TIMEOUT : adjustedTimeout;
    }

    static Duration getLiveSignalTimeout(final Signal<?> signal) {
        return signal.getDittoHeaders().getTimeout().orElse(DEFAULT_LIVE_TIMEOUT);
    }

    private static ThingCommand<?> adjustTimeout(final ThingCommand<?> signal, final Duration adjustedTimeout) {

        return signal.setDittoHeaders(
                signal.getDittoHeaders()
                        .toBuilder()
                        .timeout(adjustedTimeout)
                        .build()
        );
    }

    private DistributedPubWithMessage selectLiveSignalPublisher(final Signal<?> enforcedSignal) {

        final var streamingType = StreamingType.fromSignal(enforcedSignal);
        if (streamingType.isPresent()) {
            switch (streamingType.get()) {
                case MESSAGES -> {
                    final DistributedPub<SignalWithEntityId<?>> pubM = liveSignalPub.message();
                    return new DistributedPubWithMessage(pubM,
                            wrapLiveSignal((MessageCommand<?, ?>) enforcedSignal, MESSAGE_COMMAND_ACK_EXTRACTOR, pubM),
                            enforcedSignal
                    );
                }
                case LIVE_EVENTS -> {
                    final DistributedPub<ThingEvent<?>> pubE = liveSignalPub.event();
                    return new DistributedPubWithMessage(pubE,
                            wrapLiveSignal((ThingEvent<?>) enforcedSignal, THING_EVENT_ACK_EXTRACTOR, pubE),
                            enforcedSignal
                    );
                }
                case LIVE_COMMANDS -> {
                    final DistributedPub<ThingCommand<?>> pubC = liveSignalPub.command();
                    return new DistributedPubWithMessage(pubC,
                            wrapLiveSignal((ThingCommand<?>) enforcedSignal, THING_COMMAND_ACK_EXTRACTOR, pubC),
                            enforcedSignal
                    );
                }
                default -> {
                    // empty
                }
            }
        }
        log.withCorrelationId(enforcedSignal)
                .warning("Ignoring unsupported signal: <{}>", enforcedSignal);
        throw UnsupportedSignalException.newBuilder(enforcedSignal.getType())
                .message("The sent command is not supported as live command")
                .dittoHeaders(enforcedSignal.getDittoHeaders())
                .build();
    }

    private <T extends Signal<?>, S extends T> Object wrapLiveSignal(final S signal,
            final AckExtractor<S> ackExtractor, final DistributedPub<T> pub) {

        return pub.wrapForPublicationWithAcks(signal, ackExtractor);
    }

}
