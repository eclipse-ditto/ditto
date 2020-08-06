/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.models.acks;

import static org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel.LIVE_RESPONSE;
import static org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel.TWIN_PERSISTED;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.services.models.acks.AcknowledgementForwarderActorStarter.isLiveSignal;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.WithThingId;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.models.acks.config.AcknowledgementConfig;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.acks.base.AcknowledgementCorrelationIdMissingException;
import org.eclipse.ditto.signals.acks.base.Acknowledgements;
import org.eclipse.ditto.signals.acks.things.ThingAcknowledgementFactory;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.base.WithOptionalEntity;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;

/**
 * Actor which is created for an {@code ThingModifyCommand} containing {@code AcknowledgementRequests} responsible for
 * building an {@link AcknowledgementAggregator}, e.g. timing it out when not all requested acknowledgements were
 * received after the {@code timeout} contained in the passed thing modify command.
 *
 * @since 1.1.0
 */
public final class AcknowledgementAggregatorActor extends AbstractActor {

    /**
     * Prefix of the acknowledgement aggregator actor's name.
     */
    static final String ACTOR_NAME_PREFIX = "ackAggregator-";

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final String correlationId;
    private final DittoHeaders requestCommandHeaders;
    private final AcknowledgementAggregator ackregator;
    private final Consumer<Object> responseSignalConsumer;

    @SuppressWarnings("unused")
    private AcknowledgementAggregatorActor(final ThingId thingId,
            final DittoHeaders dittoHeaders,
            final AcknowledgementConfig acknowledgementConfig,
            final HeaderTranslator headerTranslator,
            final Consumer<Object> responseSignalConsumer) {

        this.responseSignalConsumer = responseSignalConsumer;
        requestCommandHeaders = dittoHeaders;
        correlationId = requestCommandHeaders.getCorrelationId()
                .orElseGet(() ->
                        // fall back using the actor name which also contains the correlation-id
                        getSelf().path().name().replace(ACTOR_NAME_PREFIX, "")
                );

        final Duration timeout =
                requestCommandHeaders.getTimeout().orElseGet(acknowledgementConfig::getForwarderFallbackTimeout);
        getContext().setReceiveTimeout(timeout);

        ackregator = AcknowledgementAggregator.getInstance(thingId, correlationId, timeout, headerTranslator);
        ackregator.addAcknowledgementRequests(requestCommandHeaders.getAcknowledgementRequests());
    }

    /**
     * Creates Akka configuration object Props for this AcknowledgementAggregatorActor.
     *
     * @param signal the signal which potentially includes {@code AcknowledgementRequests}
     * based on which the AggregatorActor is started.
     * @param acknowledgementConfig provides configuration setting regarding acknowledgement handling.
     * @param headerTranslator translates headers from external sources or to external sources.
     * @param responseSignalConsumer a consumer which is invoked with the response signal, e.g. in order to send the
     * response over a channel to the user.
     * @return the Akka configuration Props object.
     * @throws org.eclipse.ditto.model.base.acks.AcknowledgementRequestParseException if a contained acknowledgement
     * request could not be parsed.
     */
    static Props props(final Signal<?> signal,
            final AcknowledgementConfig acknowledgementConfig,
            final HeaderTranslator headerTranslator,
            final Consumer<Object> responseSignalConsumer) {

        final ThingId thingId = (ThingId) signal.getEntityId();
        return props(thingId, signal.getDittoHeaders(), acknowledgementConfig,
                headerTranslator, responseSignalConsumer);
    }


    private static Props props(final ThingId thingId,
            final DittoHeaders dittoHeaders,
            final AcknowledgementConfig acknowledgementConfig,
            final HeaderTranslator headerTranslator,
            final Consumer<Object> responseSignalConsumer) {
        return Props.create(AcknowledgementAggregatorActor.class, thingId, dittoHeaders,
                acknowledgementConfig, headerTranslator, responseSignalConsumer);
    }

    /**
     * Determines the actor name to use for the passed DittoHeaders.
     *
     * @param dittoHeaders the headers to extract the correlation-id from which is used as part of the actor name.
     * @return the actor name to use.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     * @throws AcknowledgementCorrelationIdMissingException if no {@code correlation-id} was present in the passed
     * {@code dittoHeaders}.
     */
    public static String determineActorName(final DittoHeaders dittoHeaders) {
        checkNotNull(dittoHeaders, "dittoHeaders");
        final String correlationId = dittoHeaders.getCorrelationId()
                .orElseThrow(() -> AcknowledgementCorrelationIdMissingException.newBuilder()
                        .dittoHeaders(dittoHeaders)
                        .build());
        return ACTOR_NAME_PREFIX + URLEncoder.encode(correlationId, Charset.defaultCharset());
    }

    /**
     * Creates and starts an {@code AcknowledgementAggregatorActor} actor in the passed {@code context} using the passed
     * arguments.
     * The actor's name is derived from the {@code correlation-id} extracted via the passed {@code signal}'s
     * {@code dittoHeaders} and in case that an Actor with this name already exists, a
     * {@code AcknowledgementRequestDuplicateCorrelationIdException} will be thrown.
     * <p>
     * NOT thread-safe!
     * <p>
     * Only actually starts the actor if the passed {@code command}:
     * <ul>
     * <li>contains in its ditto headers that {@code response-required} is given</li>
     * </ul>
     *
     * @param context the context to start the aggregator actor in.
     * @param signal the signal which potentially includes {@code AcknowledgementRequests} based on which the
     * AggregatorActor is started.
     * @param ackConfig provides configuration setting regarding acknowledgement handling.
     * @param headerTranslator translates headers from external sources or to external sources.
     * @param responseSignalConsumer a consumer which is invoked with the response signal, e.g. in order to send the
     * response over a channel to the user.
     * @return the optionally created ActorRef - empty when either no AcknowledgementRequests were contained in the
     * {@code dittoHeaders} of the passed {@code command} or when a conflict caused by a re-used
     * {@code correlation-id} was detected.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.signals.acks.base.AcknowledgementRequestDuplicateCorrelationIdException in case that an
     * aggregator actor with the same correlation-id is already running.
     * @throws org.eclipse.ditto.model.base.acks.AcknowledgementRequestParseException if a contained acknowledgement
     * request could not be parsed.
     */
    public static Optional<ActorRef> startAcknowledgementAggregator(final akka.actor.ActorContext context,
            final String actorName,
            final Signal<?> signal,
            final AcknowledgementConfig ackConfig,
            final HeaderTranslator headerTranslator,
            final Consumer<Object> responseSignalConsumer) {

        return AcknowledgementAggregatorActorStarter
                .getInstance(context, actorName, signal, ackConfig, headerTranslator, responseSignalConsumer)
                .get();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(ThingCommandResponse.class, this::handleThingCommandResponse)
                .match(MessageCommandResponse.class, this::handleMessageCommandResponse)
                .match(Acknowledgement.class, this::handleAcknowledgement)
                .match(Acknowledgements.class, this::handleAcknowledgements)
                .match(DittoRuntimeException.class, this::handleDittoRuntimeException)
                .match(ReceiveTimeout.class, this::handleReceiveTimeout)
                .matchAny(m -> log.warning("Received unexpected message: <{}>", m))
                .build();
    }

    private void handleThingCommandResponse(final ThingCommandResponse<?> thingCommandResponse) {
        final boolean isLiveResponse = thingCommandResponse.getDittoHeaders().getChannel().stream()
                .anyMatch(TopicPath.Channel.LIVE.getName()::equals);
        addCommandResponse(thingCommandResponse, thingCommandResponse, isLiveResponse);
    }

    private void handleMessageCommandResponse(final MessageCommandResponse<?, ?> messageCommandResponse) {
        addCommandResponse(messageCommandResponse, messageCommandResponse, true);
    }

    private void addCommandResponse(final CommandResponse<?> commandResponse, final WithThingId withThingId,
            final boolean isLiveResponse) {
        final DittoHeaders dittoHeaders = commandResponse.getDittoHeaders();
        ackregator.addReceivedAcknowledgment(ThingAcknowledgementFactory.newAcknowledgement(
                isLiveResponse ? LIVE_RESPONSE : TWIN_PERSISTED,
                withThingId.getThingEntityId(),
                commandResponse.getStatusCode(),
                dittoHeaders,
                getPayload(commandResponse).orElse(null)
        ));
        potentiallyCompleteAcknowledgements(commandResponse);
    }

    private static Optional<JsonValue> getPayload(final CommandResponse<?> response) {
        final Optional<JsonValue> result;
        if (response instanceof WithOptionalEntity) {
            result = ((WithOptionalEntity) response).getEntity(
                    response.getImplementedSchemaVersion());
        } else if (response instanceof MessageCommandResponse) {
            result = response.toJson().getValue(MessageCommandResponse.JsonFields.JSON_MESSAGE).map(x -> x);
        } else {
            result = Optional.empty();
        }
        return result;
    }

    private void handleReceiveTimeout(final ReceiveTimeout receiveTimeout) {
        log.withCorrelationId(correlationId).info("Timed out waiting for all requested acknowledgements, " +
                "completing Acknowledgements with timeouts...");
        completeAcknowledgements(null, requestCommandHeaders);
    }

    private void handleAcknowledgement(final Acknowledgement acknowledgement) {
        ackregator.addReceivedAcknowledgment(acknowledgement);
        potentiallyCompleteAcknowledgements(null);
    }

    private void handleAcknowledgements(final Acknowledgements acknowledgements) {
        acknowledgements.stream().forEach(ackregator::addReceivedAcknowledgment);
        potentiallyCompleteAcknowledgements(null);
    }

    private void handleDittoRuntimeException(final DittoRuntimeException dittoRuntimeException) {
        // abort on DittoRuntimeException
        handleSignal(dittoRuntimeException);
        getContext().stop(getSelf());
    }

    private void potentiallyCompleteAcknowledgements(@Nullable final CommandResponse<?> response) {

        if (ackregator.receivedAllRequestedAcknowledgements()) {
            completeAcknowledgements(response, requestCommandHeaders);
        }
    }

    private void completeAcknowledgements(@Nullable final CommandResponse<?> response,
            final DittoHeaders dittoHeaders) {

        final Acknowledgements aggregatedAcknowledgements = ackregator.getAggregatedAcknowledgements(dittoHeaders);
        if (null != response && containsOnlyTwinPersistedOrLiveResponse(aggregatedAcknowledgements)) {
            // in this case, only the implicit "twin-persisted" acknowledgement was asked for, respond with the signal:
            handleSignal(response);
        } else {
            log.withCorrelationId(dittoHeaders)
                    .debug("Completing with collected acknowledgements: {}", aggregatedAcknowledgements);
            handleSignal(aggregatedAcknowledgements);
        }

        getContext().stop(getSelf());
    }

    private void handleSignal(final Object signal) {
        responseSignalConsumer.accept(signal);
    }

    private static boolean containsOnlyTwinPersistedOrLiveResponse(final Acknowledgements aggregatedAcknowledgements) {
        return aggregatedAcknowledgements.getSize() == 1 &&
                aggregatedAcknowledgements.stream()
                        .anyMatch(ack -> {
                            final AcknowledgementLabel label = ack.getLabel();
                            return TWIN_PERSISTED.equals(label) ||
                                    LIVE_RESPONSE.equals(label);
                        });
    }

    /**
     * Tests whether an incoming signal has effective acknowledgement requests and thereby warrants starting an
     * acknowledgement forwarder.
     *
     * @param signal the outgoing signal.
     * @return whether the signal has effective acknowledgement requests.
     */
    public static boolean shouldStartForIncoming(final Signal<?> signal) {
        final boolean isLiveSignal = isLiveSignal(signal);
        final Collection<AcknowledgementRequest> ackRequests = signal.getDittoHeaders().getAcknowledgementRequests();
        if (signal instanceof ThingModifyCommand && !isLiveSignal) {
            return ackRequests.stream().anyMatch(AcknowledgementForwarderActorStarter::isNotLiveResponse);
        } else if (signal instanceof MessageCommand || (isLiveSignal && signal instanceof ThingCommand)) {
            return ackRequests.stream().anyMatch(AcknowledgementForwarderActorStarter::isNotTwinPersisted);
        } else {
            return false;
        }
    }

}
