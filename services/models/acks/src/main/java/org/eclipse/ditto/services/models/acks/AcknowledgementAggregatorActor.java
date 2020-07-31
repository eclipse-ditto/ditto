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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.services.models.acks.config.AcknowledgementConfig;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.acks.base.AcknowledgementCorrelationIdMissingException;
import org.eclipse.ditto.signals.acks.base.Acknowledgements;
import org.eclipse.ditto.signals.acks.things.ThingAcknowledgementFactory;
import org.eclipse.ditto.signals.base.WithOptionalEntity;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.acks.MessageCommandAckRequestSetter;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;
import org.eclipse.ditto.signals.commands.things.acks.ThingLiveCommandAckRequestSetter;
import org.eclipse.ditto.signals.commands.things.acks.ThingModifyCommandAckRequestSetter;
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
     * @param thingModifyCommand the thing modify command which potentially includes {@code AcknowledgementRequests}
     * based on which the AggregatorActor is started.
     * @param acknowledgementConfig provides configuration setting regarding acknowledgement handling.
     * @param headerTranslator translates headers from external sources or to external sources.
     * @param responseSignalConsumer a consumer which is invoked with the response signal, e.g. in order to send the
     * response over a channel to the user.
     * @return the Akka configuration Props object.
     * @throws org.eclipse.ditto.model.base.acks.AcknowledgementRequestParseException if a contained acknowledgement
     * request could not be parsed.
     */
    static Props props(final ThingModifyCommand<?> thingModifyCommand,
            final AcknowledgementConfig acknowledgementConfig,
            final HeaderTranslator headerTranslator,
            final Consumer<Object> responseSignalConsumer) {

        final ThingModifyCommand<?> commandWithAckLabels =
                ThingModifyCommandAckRequestSetter.getInstance().apply(thingModifyCommand);
        return props(commandWithAckLabels.getEntityId(), commandWithAckLabels.getDittoHeaders(), acknowledgementConfig,
                headerTranslator, responseSignalConsumer);
    }


    /**
     * Creates Akka configuration object Props for this AcknowledgementAggregatorActor.
     *
     * @param thingCommand the thing live command which potentially includes {@code AcknowledgementRequests}
     * based on which the AggregatorActor is started.
     * @param acknowledgementConfig provides configuration setting regarding acknowledgement handling.
     * @param headerTranslator translates headers from external sources or to external sources.
     * @param responseSignalConsumer a consumer which is invoked with the response signal, e.g. in order to send the
     * response over a channel to the user.
     * @return the Akka configuration Props object.
     * @throws org.eclipse.ditto.model.base.acks.AcknowledgementRequestParseException if a contained acknowledgement
     * request could not be parsed.
     */
    static Props props(final ThingCommand<?> thingCommand,
            final AcknowledgementConfig acknowledgementConfig,
            final HeaderTranslator headerTranslator,
            final Consumer<Object> responseSignalConsumer) {

        final ThingCommand<?> commandWithAckLabels = ThingLiveCommandAckRequestSetter.getInstance().apply(thingCommand);
        return props(commandWithAckLabels.getEntityId(), commandWithAckLabels.getDittoHeaders(), acknowledgementConfig,
                headerTranslator, responseSignalConsumer);
    }

    /**
     * Creates Akka configuration object Props for this AcknowledgementAggregatorActor.
     *
     * @param messageCommand the message command which potentially includes {@code AcknowledgementRequests}
     * based on which the AggregatorActor is started.
     * @param acknowledgementConfig provides configuration setting regarding acknowledgement handling.
     * @param headerTranslator translates headers from external sources or to external sources.
     * @param responseSignalConsumer a consumer which is invoked with the response signal, e.g. in order to send the
     * response over a channel to the user.
     * @return the Akka configuration Props object.
     * @throws org.eclipse.ditto.model.base.acks.AcknowledgementRequestParseException if a contained acknowledgement
     * request could not be parsed.
     */
    static Props props(final MessageCommand<?, ?> messageCommand,
            final AcknowledgementConfig acknowledgementConfig,
            final HeaderTranslator headerTranslator,
            final Consumer<Object> responseSignalConsumer) {

        final MessageCommand<?, ?> commandWithAckLabels =
                MessageCommandAckRequestSetter.getInstance().apply(messageCommand);
        return props(commandWithAckLabels.getEntityId(), commandWithAckLabels.getDittoHeaders(), acknowledgementConfig,
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
     * The actor's name is derived from the {@code correlation-id} extracted via the passed {@code thingModifyCommand}'s
     * {@code dittoHeaders} and in case that an Actor with this name already exists, a
     * {@code AcknowledgementRequestDuplicateCorrelationIdException} will be thrown.
     * <p>
     * NOT thread-safe!
     * <p>
     * Only actually starts the actor if the passed {@code thingModifyCommand}:
     * <ul>
     * <li>contains in its ditto headers that {@code response-required} is given</li>
     * </ul>
     *
     * @param context the context to start the aggregator actor in.
     * @param thingModifyCommand the thing modify command which potentially includes {@code AcknowledgementRequests}
     * based on which the AggregatorActor is started.
     * @param ackConfig provides configuration setting regarding acknowledgement handling.
     * @param headerTranslator translates headers from external sources or to external sources.
     * @param responseSignalConsumer a consumer which is invoked with the response signal, e.g. in order to send the
     * response over a channel to the user.
     * @return the optionally created ActorRef - empty when either no AcknowledgementRequests were contained in the
     * {@code dittoHeaders} of the passed {@code thingModifyCommand} or when a conflict caused by a re-used
     * {@code correlation-id} was detected.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.signals.acks.base.AcknowledgementRequestDuplicateCorrelationIdException in case that an
     * aggregator actor with the same correlation-id is already running.
     * @throws org.eclipse.ditto.model.base.acks.AcknowledgementRequestParseException if a contained acknowledgement
     * request could not be parsed.
     */
    public static Optional<ActorRef> startAcknowledgementAggregator(final akka.actor.ActorContext context,
            final ThingModifyCommand<?> thingModifyCommand,
            final AcknowledgementConfig ackConfig,
            final HeaderTranslator headerTranslator,
            final Consumer<Object> responseSignalConsumer) {

        return ThingModifyTwinCommandAcknowledgementAggregatorActorStarter
                .getInstance(context, thingModifyCommand, ackConfig, headerTranslator, responseSignalConsumer).get();
    }

    /**
     * Creates and starts an {@code AcknowledgementAggregatorActor} actor in the passed {@code context} using the passed
     * arguments.
     * The actor's name is derived from the {@code correlation-id} extracted via the passed {@code thingCommand}'s
     * {@code dittoHeaders} and in case that an Actor with this name already exists, a
     * {@code AcknowledgementRequestDuplicateCorrelationIdException} will be thrown.
     * <p>
     * NOT thread-safe!
     * <p>
     * Only actually starts the actor if the passed {@code thingCommand}:
     * <ul>
     * <li>contains in its ditto headers that {@code response-required} is given</li>
     * </ul>
     *
     * @param context the context to start the aggregator actor in.
     * @param thingCommand the thing live command which potentially includes {@code AcknowledgementRequests}
     * based on which the AggregatorActor is started.
     * @param ackConfig provides configuration setting regarding acknowledgement handling.
     * @param headerTranslator translates headers from external sources or to external sources.
     * @param responseSignalConsumer a consumer which is invoked with the response signal, e.g. in order to send the
     * response over a channel to the user.
     * @return the optionally created ActorRef - empty when either no AcknowledgementRequests were contained in the
     * {@code dittoHeaders} of the passed {@code thingCommand} or when a conflict caused by a re-used
     * {@code correlation-id} was detected.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.signals.acks.base.AcknowledgementRequestDuplicateCorrelationIdException in case that an
     * aggregator actor with the same correlation-id is already running.
     * @throws org.eclipse.ditto.model.base.acks.AcknowledgementRequestParseException if a contained acknowledgement
     * request could not be parsed.
     */
    public static Optional<ActorRef> startAcknowledgementAggregator(final akka.actor.ActorContext context,
            final ThingCommand<?> thingCommand,
            final AcknowledgementConfig ackConfig,
            final HeaderTranslator headerTranslator,
            final Consumer<Object> responseSignalConsumer) {

        return ThingLiveCommandAcknowledgementAggregatorActorStarter
                .getInstance(context, thingCommand, ackConfig, headerTranslator, responseSignalConsumer).get();
    }

    /**
     * Creates and starts an {@code AcknowledgementAggregatorActor} actor in the passed {@code context} using the passed
     * arguments.
     * The actor's name is derived from the {@code correlation-id} extracted via the passed {@code messageCommand}'s
     * {@code dittoHeaders} and in case that an Actor with this name already exists, a
     * {@code AcknowledgementRequestDuplicateCorrelationIdException} will be thrown.
     * <p>
     * NOT thread-safe!
     * <p>
     * Only actually starts the actor if the passed {@code messageCommand}:
     * <ul>
     * <li>contains in its ditto headers that {@code response-required} is given</li>
     * </ul>
     *
     * @param context the context to start the aggregator actor in.
     * @param messageCommand the message command which potentially includes {@code AcknowledgementRequests}
     * based on which the AggregatorActor is started.
     * @param ackConfig provides configuration setting regarding acknowledgement handling.
     * @param headerTranslator translates headers from external sources or to external sources.
     * @param responseSignalConsumer a consumer which is invoked with the response signal, e.g. in order to send the
     * response over a channel to the user.
     * @return the optionally created ActorRef - empty when either no AcknowledgementRequests were contained in the
     * {@code dittoHeaders} of the passed {@code messageCommand} or when a conflict caused by a re-used
     * {@code correlation-id} was detected.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.signals.acks.base.AcknowledgementRequestDuplicateCorrelationIdException in case that an
     * aggregator actor with the same correlation-id is already running.
     * @throws org.eclipse.ditto.model.base.acks.AcknowledgementRequestParseException if a contained acknowledgement
     * request could not be parsed.
     */
    public static Optional<ActorRef> startAcknowledgementAggregator(final akka.actor.ActorContext context,
            final MessageCommand<?, ?> messageCommand,
            final AcknowledgementConfig ackConfig,
            final HeaderTranslator headerTranslator,
            final Consumer<Object> responseSignalConsumer) {

        return MessageCommandAcknowledgementAggregatorActorStarter
                .getInstance(context, messageCommand, ackConfig, headerTranslator, responseSignalConsumer).get();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(ThingCommandResponse.class, this::handleThingCommandResponse)
                .match(Acknowledgement.class, this::handleAcknowledgement)
                .match(Acknowledgements.class, this::handleAcknowledgements)
                .match(DittoRuntimeException.class, this::handleDittoRuntimeException)
                .match(ReceiveTimeout.class, this::handleReceiveTimeout)
                .matchAny(m -> log.warning("Received unexpected message: <{}>", m))
                .build();
    }

    private void handleThingCommandResponse(final ThingCommandResponse<?> thingCommandResponse) {
        final DittoHeaders dittoHeaders = thingCommandResponse.getDittoHeaders();
        ackregator.addReceivedAcknowledgment(ThingAcknowledgementFactory.newAcknowledgement(
                DittoAcknowledgementLabel.TWIN_PERSISTED,
                thingCommandResponse.getEntityId(),
                thingCommandResponse.getStatusCode(),
                dittoHeaders,
                getPayload(thingCommandResponse).orElse(null)
        ));
        potentiallyCompleteAcknowledgements(thingCommandResponse);
    }

    private static Optional<JsonValue> getPayload(final ThingCommandResponse<?> thingCommandResponse) {
        final Optional<JsonValue> result;
        if (thingCommandResponse instanceof WithOptionalEntity) {
            result = ((WithOptionalEntity) thingCommandResponse).getEntity(
                    thingCommandResponse.getImplementedSchemaVersion());
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

    private void potentiallyCompleteAcknowledgements(@Nullable final ThingCommandResponse<?> response) {

        if (ackregator.receivedAllRequestedAcknowledgements()) {
            completeAcknowledgements(response, requestCommandHeaders);
        }
    }

    private void completeAcknowledgements(@Nullable final ThingCommandResponse<?> response,
            final DittoHeaders dittoHeaders) {

        final Acknowledgements aggregatedAcknowledgements = ackregator.getAggregatedAcknowledgements(dittoHeaders);
        if (null != response && containsOnlyTwinPersisted(aggregatedAcknowledgements)) {
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

    private static boolean containsOnlyTwinPersisted(final Acknowledgements aggregatedAcknowledgements) {
        return aggregatedAcknowledgements.getSize() == 1 &&
                aggregatedAcknowledgements.stream()
                        .anyMatch(ack -> DittoAcknowledgementLabel.TWIN_PERSISTED.equals(ack.getLabel()));
    }

}
