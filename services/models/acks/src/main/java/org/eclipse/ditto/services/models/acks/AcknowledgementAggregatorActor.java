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

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.WithThingId;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.models.acks.config.AcknowledgementConfig;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.acks.base.Acknowledgements;
import org.eclipse.ditto.signals.acks.things.ThingAcknowledgementFactory;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.base.WithOptionalEntity;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayCommandTimeoutException;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;

import akka.actor.AbstractActor;
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

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final String correlationId;
    private final DittoHeaders requestCommandHeaders;
    private final AcknowledgementAggregator ackregator;
    private final Consumer<Object> responseSignalConsumer;
    private final Duration timeout;

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
                        getSelf().path().name()
                );

        timeout = requestCommandHeaders.getTimeout().orElseGet(acknowledgementConfig::getForwarderFallbackTimeout);
        getContext().setReceiveTimeout(timeout);

        final Set<AcknowledgementRequest> acknowledgementRequests = requestCommandHeaders.getAcknowledgementRequests();
        ackregator = AcknowledgementAggregator.getInstance(thingId, correlationId, timeout, headerTranslator);
        ackregator.addAcknowledgementRequests(acknowledgementRequests);
        log.withCorrelationId(correlationId)
                .info("Starting to wait for all requested acknowledgements <{}> for a maximum duration of <{}>.",
                        acknowledgementRequests, timeout);
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
        log.withCorrelationId(correlationId).debug("Received command response <{}>.", commandResponse);
        final DittoHeaders dittoHeaders = commandResponse.getDittoHeaders();
        ackregator.addReceivedAcknowledgment(ThingAcknowledgementFactory.newAcknowledgement(
                isLiveResponse ? LIVE_RESPONSE : TWIN_PERSISTED,
                withThingId.getThingEntityId(),
                commandResponse.getHttpStatus(),
                dittoHeaders,
                getPayload(commandResponse).orElse(null)
        ));
        potentiallyCompleteAcknowledgements(commandResponse);
    }

    private static Optional<JsonValue> getPayload(final CommandResponse<?> response) {
        final Optional<JsonValue> result;
        if (response instanceof WithOptionalEntity) {
            result = ((WithOptionalEntity) response).getEntity(response.getImplementedSchemaVersion());
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
        completeAcknowledgements(null);
    }

    private void handleAcknowledgement(final Acknowledgement acknowledgement) {
        log.withCorrelationId(correlationId).debug("Received acknowledgement <{}>.", acknowledgement);
        ackregator.addReceivedAcknowledgment(acknowledgement);
        potentiallyCompleteAcknowledgements(null);
    }

    private void handleAcknowledgements(final Acknowledgements acknowledgements) {
        log.withCorrelationId(correlationId).debug("Received acknowledgements <{}>.", acknowledgements);
        acknowledgements.stream().forEach(ackregator::addReceivedAcknowledgment);
        potentiallyCompleteAcknowledgements(null);
    }

    private void handleDittoRuntimeException(final DittoRuntimeException dittoRuntimeException) {
        log.withCorrelationId(correlationId)
                .info("Stopped waiting for acknowledgements because of ditto runtime exception <{}>.",
                        dittoRuntimeException);
        // abort on DittoRuntimeException
        handleSignal(dittoRuntimeException);
        getContext().stop(getSelf());
    }

    private void potentiallyCompleteAcknowledgements(@Nullable final CommandResponse<?> response) {

        if (ackregator.receivedAllRequestedAcknowledgements()) {
            completeAcknowledgements(response);
        }
    }

    private void completeAcknowledgements(@Nullable final CommandResponse<?> response) {
        final Acknowledgements aggregatedAcknowledgements =
                ackregator.getAggregatedAcknowledgements(requestCommandHeaders);
        final boolean builtInAcknowledgementOnly = containsOnlyTwinPersistedOrLiveResponse(aggregatedAcknowledgements);
        if (null != response && builtInAcknowledgementOnly) {
            // in this case, only the implicit "twin-persisted" acknowledgement was asked for, respond with the signal:
            handleSignal(response);
        } else if (builtInAcknowledgementOnly && !ackregator.receivedAllRequestedAcknowledgements()) {
            // there is no response. send an error according to channel
            handleSignal(asThingErrorResponse(aggregatedAcknowledgements));
        } else {
            log.withCorrelationId(requestCommandHeaders)
                    .debug("Completing with collected acknowledgements: {}", aggregatedAcknowledgements);
            handleSignal(aggregatedAcknowledgements);
        }
        getContext().stop(getSelf());
    }

    public static WithDittoHeaders<?> restoreCommandConnectivityHeaders(final WithDittoHeaders<?> signal,
            final DittoHeaders requestCommandHeaders) {
        final DittoHeadersBuilder<?, ?> enhancedHeadersBuilder = signal.getDittoHeaders()
                .toBuilder()
                .removeHeader(DittoHeaderDefinition.EXPECTED_RESPONSE_TYPES.getKey())
                .removeHeader(DittoHeaderDefinition.INBOUND_PAYLOAD_MAPPER.getKey())
                .removeHeader(DittoHeaderDefinition.REPLY_TARGET.getKey());
        if (requestCommandHeaders.containsKey(DittoHeaderDefinition.EXPECTED_RESPONSE_TYPES.getKey())) {
            enhancedHeadersBuilder.expectedResponseTypes(requestCommandHeaders.getExpectedResponseTypes());
        }
        requestCommandHeaders.getInboundPayloadMapper().ifPresent(enhancedHeadersBuilder::inboundPayloadMapper);
        requestCommandHeaders.getReplyTarget().ifPresent(enhancedHeadersBuilder::replyTarget);
        return signal.setDittoHeaders(enhancedHeadersBuilder.build());
    }

    private void handleSignal(final WithDittoHeaders<?> signal) {
        responseSignalConsumer.accept(restoreCommandConnectivityHeaders(signal, requestCommandHeaders));
    }

    /**
     * Convert aggregated acknowledgements to a single error response in case only built-in acknowledgements
     * are requested.
     *
     * @param aggregatedAcknowledgements the aggregated acknowledgements.
     * @return the error response.
     */
    private ThingErrorResponse asThingErrorResponse(final Acknowledgements aggregatedAcknowledgements) {
        final ThingId thingId = ThingId.of(aggregatedAcknowledgements.getEntityId());
        final DittoRuntimeException dittoRuntimeException = GatewayCommandTimeoutException.newBuilder(timeout)
                .dittoHeaders(aggregatedAcknowledgements.getDittoHeaders())
                .build();

        return ThingErrorResponse.of(thingId, dittoRuntimeException);
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

}
