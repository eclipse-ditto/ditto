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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.UnsupportedSignalException;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.commands.exceptions.CommandTimeoutException;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.cacheloaders.AskWithRetry;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistentactors.DistributedPubWithMessage;
import org.eclipse.ditto.internal.utils.persistentactors.TargetActorWithMessage;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;
import org.eclipse.ditto.internal.utils.pubsub.StreamingType;
import org.eclipse.ditto.internal.utils.pubsub.extractors.AckExtractor;
import org.eclipse.ditto.internal.utils.pubsubthings.LiveSignalPub;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.policies.enforcement.config.DefaultEnforcementConfig;
import org.eclipse.ditto.policies.enforcement.config.EnforcementConfig;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingErrorResponse;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommand;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.service.persistence.actors.strategies.commands.ThingConditionValidator;

import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.ActorSystem;
import akka.pattern.AskTimeoutException;

/**
 * Functionality used in {@link ThingSupervisorActor} for dispatching {@code "live"} channel messages.
 */
final class SupervisorLiveChannelDispatching {

    private static final Duration DEFAULT_LIVE_TIMEOUT = Duration.ofSeconds(60L);

    private static final AckExtractor<ThingCommand<?>> THING_COMMAND_ACK_EXTRACTOR =
            AckExtractor.of(ThingCommand::getEntityId, ThingCommand::getDittoHeaders);
    private static final AckExtractor<ThingEvent<?>> THING_EVENT_ACK_EXTRACTOR =
            AckExtractor.of(ThingEvent::getEntityId, ThingEvent::getDittoHeaders);
    private static final AckExtractor<MessageCommand<?, ?>> MESSAGE_COMMAND_ACK_EXTRACTOR =
            AckExtractor.of(MessageCommand::getEntityId, MessageCommand::getDittoHeaders);

    private final ThreadSafeDittoLoggingAdapter log;
    private final EnforcementConfig enforcementConfig;
    private final ResponseReceiverCache responseReceiverCache;
    private final LiveSignalPub liveSignalPub;
    private final ActorRefFactory actorRefFactory;
    private final ActorRef thingsShardRegion;
    private final ActorSystem actorSystem;
    private final AskWithRetryConfig askWithRetryConfig;

    SupervisorLiveChannelDispatching(final ThreadSafeDittoLoggingAdapter log,
            final EnforcementConfig enforcementConfig,
            final ResponseReceiverCache responseReceiverCache,
            final LiveSignalPub liveSignalPub,
            final ActorRefFactory actorRefFactory,
            final ActorRef thingsShardRegion,
            final ActorSystem actorSystem) {

        this.log = log;
        this.enforcementConfig = enforcementConfig;
        this.responseReceiverCache = responseReceiverCache;
        this.liveSignalPub = liveSignalPub;
        this.actorRefFactory = actorRefFactory;
        this.thingsShardRegion = thingsShardRegion;
        this.actorSystem = actorSystem;
        this.askWithRetryConfig = getAskWithRetryConfig(actorSystem);
    }

    private static AskWithRetryConfig getAskWithRetryConfig(final ActorSystem actorSystem) {
        final DefaultScopedConfig dittoScoped = DefaultScopedConfig.dittoScoped(actorSystem.settings().config());
        final var enforcementConfig = DefaultEnforcementConfig.of(dittoScoped);
        return enforcementConfig.getAskWithRetryConfig();
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

        return prepareForPubSubPublishing(thingQueryCommand, receiver,
                response -> handleEncounteredAskTimeoutsAsCommandTimeoutException(thingQueryCommand.getDittoHeaders(),
                        response));
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

        final var timeout = calculateLiveChannelTimeout(thingQueryCommand.getDittoHeaders());
        final var pub = liveSignalPub.command();
        final var thingId = thingQueryCommand.getEntityId();
        final var publish = pub.wrapForPublicationWithAcks(thingQueryCommand, thingId, THING_COMMAND_ACK_EXTRACTOR);

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

        return evaluateCondition(signal).thenCompose(s -> {
            final UnaryOperator<Object> errorHandler =
                    response -> handleEncounteredAskTimeoutsAsCommandTimeoutException(signal.getDittoHeaders(),
                            response);
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
                                calculateLiveChannelTimeout(distributedPub.signal().getDittoHeaders()),
                                errorHandler
                        ));
            } else {
                log.withCorrelationId(signal)
                        .debug("Publish message to pub-sub: <{}>", signal);
                return CompletableFuture.completedStage(new TargetActorWithMessage(
                        distributedPubWithMessage.pub().getPublisher(),
                        distributedPubWithMessage.wrappedSignalForPublication(),
                        calculateLiveChannelTimeout(signal.getDittoHeaders()),
                        errorHandler
                ));
            }
        });
    }

    private CompletionStage<Signal<?>> evaluateCondition(final Signal<?> signal) {
        final var condition = signal.getDittoHeaders().getCondition();

        if (condition.isPresent()) {
            final var retrieveThing = getRetrieveThing(signal);
            if (retrieveThing.isPresent()) {
                final var thing = AskWithRetry.askWithRetry(thingsShardRegion, retrieveThing.get(), askWithRetryConfig,
                        actorSystem,
                        response -> handleRetrieveThingResponse(response, signal.getDittoHeaders())
                );
                return thing.thenApply(t -> {
                    final var optionalException = ThingConditionValidator.validate(signal, condition.get(), t);
                    if (optionalException.isPresent()) {
                        log.withCorrelationId(signal).info("Live-message was filtered due to not matching condition.");
                        throw optionalException.get();
                    } else {
                        return signal;
                    }
                });
            }
        }
        return CompletableFuture.completedFuture(signal);
    }

    private Optional<SudoRetrieveThing> getRetrieveThing(final Signal<?> signal) {
        final Optional<SudoRetrieveThing> result;
        if (signal instanceof WithEntityId withEntityId) {
            if (withEntityId.getEntityId() instanceof ThingId thingId) {
                result = Optional.of(SudoRetrieveThing.of(thingId, signal.getDittoHeaders()));
            } else {
                result = Optional.empty();
                log.withCorrelationId(signal).error("Skipping live-message condition validation, because entityId " +
                        "is no thingId.");
            }
        } else {
            result = Optional.empty();
            log.withCorrelationId(signal).error("Skipping live-message condition validation, because message " +
                    "does not contain an entityId.");
        }
        return result;
    }

    private Thing handleRetrieveThingResponse(final Object response, final DittoHeaders dittoHeaders) {

        if (response instanceof SudoRetrieveThingResponse retrieveThingResponse) {
            final JsonValue entity = retrieveThingResponse.getEntity();
            if (!entity.isObject()) {
                log.withCorrelationId(dittoHeaders)
                        .error("Expected SudoRetrieveThingResponse to contain a JsonObject as Entity but was: {}",
                                entity);
                throw DittoInternalErrorException.newBuilder().dittoHeaders(dittoHeaders).build();
            }
            return ThingsModelFactory.newThing(entity.asObject());

        } else if (response instanceof ThingErrorResponse thingErrorResponse) {
            log.withCorrelationId(dittoHeaders)
                    .info("Got ThingErrorResponse when waiting on RetrieveThingResponse when validating live-message " +
                            "condition.");
            throw thingErrorResponse.getDittoRuntimeException();
        } else if (response instanceof DittoRuntimeException dre) {
            log.withCorrelationId(dittoHeaders)
                    .info("Got Exception when waiting on RetrieveThingResponse when validating live-message condition: {}",
                            dre.getMessage());
            throw dre;
        } else {
            log.withCorrelationId(dittoHeaders)
                    .error("Did not retrieve expected RetrieveThingResponse when validating live-message condition: {}",
                            response);
            throw DittoInternalErrorException.newBuilder().dittoHeaders(dittoHeaders).build();
        }
    }

    private static Duration calculateLiveChannelTimeout(final DittoHeaders dittoHeaders) {
        if (dittoHeaders.isResponseRequired()) {
            return dittoHeaders.getTimeout().orElse(DEFAULT_LIVE_TIMEOUT);
        } else {
            return Duration.ZERO;
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
                                Duration.ZERO,
                                // ZERO duration means that no "ask" is used, but "tell" - not expecting an answer
                                Function.identity()
                        );
                    } else {
                        log.withCorrelationId(liveResponse)
                                .warning("Got <{}> with unknown correlation ID: <{}>", liveResponse.getType(),
                                        correlationId);
                        targetActorWithMessage = null;
                    }
                    return targetActorWithMessage;
                });
    }

    private Object handleEncounteredAskTimeoutsAsCommandTimeoutException(final DittoHeaders dittoHeaders,
            final Object response) {

        if (response instanceof Throwable t) {
            Throwable throwable = t;
            if (t instanceof CompletionException completionException) {
                throwable = completionException.getCause();
            }

            if (throwable instanceof AskTimeoutException askTimeoutException) {
                return CommandTimeoutException.newBuilder(calculateLiveChannelTimeout(dittoHeaders))
                        .cause(askTimeoutException)
                        .dittoHeaders(dittoHeaders)
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

    private <T extends SignalWithEntityId<?>, S extends T> Object wrapLiveSignal(final S signal,
            final AckExtractor<S> ackExtractor, final DistributedPub<T> pub) {

        return pub.wrapForPublicationWithAcks(signal, signal.getEntityId(), ackExtractor);
    }

}
