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
package org.eclipse.ditto.services.connectivity.messaging;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.services.connectivity.mapping.MappingConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.DittoConnectivityConfig;
import org.eclipse.ditto.services.connectivity.util.ConnectivityMdcEntryKey;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.utils.akka.controlflow.AbstractGraphActor;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.services.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.acks.base.Acknowledgements;
import org.eclipse.ditto.signals.commands.base.CommandResponse;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;

/**
 * This Actor processes inbound {@link ExternalMessage external messages}.
 */
public final class InboundMappingProcessorActor
        extends AbstractGraphActor<InboundMappingProcessorActor.ExternalMessageWithSender, ExternalMessage> {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "inboundMappingProcessor";

    /**
     * The name of the dispatcher that runs all mapping tasks and all message handling of this actor and its children.
     */
    private static final String MESSAGE_MAPPING_PROCESSOR_DISPATCHER = "message-mapping-processor-dispatcher";

    private final ThreadSafeDittoLoggingAdapter logger;

    private final InboundMappingProcessor inboundMappingProcessor;
    private final MappingConfig mappingConfig;
    private final int processorPoolSize;
    private final ActorRef inboundDispatchingActor;

    @SuppressWarnings("unused")
    private InboundMappingProcessorActor(final InboundMappingProcessor inboundMappingProcessor,
            final HeaderTranslator headerTranslator,
            final Connection connection,
            final int processorPoolSize,
            final ActorRef inboundDispatchingActor) {

        super(ExternalMessage.class);

        this.inboundMappingProcessor = inboundMappingProcessor;
        this.inboundDispatchingActor = inboundDispatchingActor;

        logger = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this)
                .withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID, connection.getId());

        final DefaultScopedConfig dittoScoped =
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config());
        final DittoConnectivityConfig connectivityConfig = DittoConnectivityConfig.of(dittoScoped);
        mappingConfig = connectivityConfig.getMappingConfig();

        this.processorPoolSize = this.determinePoolSize(processorPoolSize, mappingConfig.getMaxPoolSize());
    }

    private int determinePoolSize(final int connectionPoolSize, final int maxPoolSize) {
        if (connectionPoolSize > maxPoolSize) {
            logger.info("Configured pool size <{}> is greater than the configured max pool size <{}>." +
                    " Will use max pool size <{}>.", connectionPoolSize, maxPoolSize, maxPoolSize);
            return maxPoolSize;
        }
        return connectionPoolSize;
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param inboundMappingProcessor the MessageMappingProcessor to use for inbound messages.
     * @param headerTranslator the headerTranslator to use.
     * @param connection the connection
     * @param processorPoolSize how many message processing may happen in parallel per direction (incoming or outgoing).
     * @param inboundDispatchingActor used to dispatch inbound signals.
     * @return the Akka configuration Props object.
     */
    public static Props props(final InboundMappingProcessor inboundMappingProcessor,
            final HeaderTranslator headerTranslator,
            final Connection connection,
            final int processorPoolSize,
            final ActorRef inboundDispatchingActor) {

        // TODO: move unused parameters to the dispatcher actor

        return Props.create(InboundMappingProcessorActor.class,
                inboundMappingProcessor,
                headerTranslator,
                connection,
                processorPoolSize,
                inboundDispatchingActor
        ).withDispatcher(MESSAGE_MAPPING_PROCESSOR_DISPATCHER);
    }

    @Override
    protected int getBufferSize() {
        return mappingConfig.getBufferSize();
    }

    @Override
    protected void preEnhancement(final ReceiveBuilder receiveBuilder) {
        receiveBuilder
                // Outgoing responses and signals go through the signal enrichment stream
                .match(Acknowledgement.class, this::handleNotExpectedAcknowledgement)
                .match(Status.Failure.class, f -> logger.warning("Got failure with cause {}: {}",
                        f.cause().getClass().getSimpleName(), f.cause().getMessage()));
    }

    @Override
    protected ExternalMessageWithSender mapMessage(final ExternalMessage message) {
        return new ExternalMessageWithSender(message, getSender());
    }

    @Override
    protected Sink<ExternalMessageWithSender, ?> createSink() {
        final Flow<ExternalMessageWithSender, Optional<InboundMappingOutcomes>, ?> flow =
                Flow.<ExternalMessageWithSender>create()
                        // parallelize potentially CPU-intensive payload mapping on this actor's dispatcher
                        .mapAsync(processorPoolSize, externalMessage -> CompletableFuture.supplyAsync(
                                () -> {
                                    logger.debug("Received inbound Message to map: {}", externalMessage);
                                    return forwardInboundMessage(externalMessage);
                                },
                                getContext().getDispatcher())
                        );

        final Sink<Optional<InboundMappingOutcomes>, ?> sink =
                Sink.foreach(outcomesOptional -> outcomesOptional.ifPresent(outcomes ->
                        inboundDispatchingActor.tell(outcomes, outcomes.getSender())
                ));

        return flow.to(sink);
    }

    private void handleNotExpectedAcknowledgement(final Acknowledgement acknowledgement) {
        // acknowledgements are not published to targets or reply-targets. this one is mis-routed.
        logger.withCorrelationId(acknowledgement)
                .warning("Received Acknowledgement where non was expected, discarding it: {}", acknowledgement);
    }

    private Optional<InboundMappingOutcomes> forwardInboundMessage(final ExternalMessageWithSender withSender) {
        final ExternalMessage externalMessage = withSender.externalMessage;
        final String correlationId =
                externalMessage.getHeaders().get(DittoHeaderDefinition.CORRELATION_ID.getKey());
        logger.withCorrelationId(correlationId)
                .debug("Handling ExternalMessage: {}", externalMessage);
        try {
            return Optional.of(mapExternalMessageToSignal(withSender, externalMessage));
        } catch (final Exception e) {
            final InboundMappingOutcomes outcomes =
                    InboundMappingOutcomes.of(withSender.externalMessage, e, withSender.sender);
            inboundDispatchingActor.tell(outcomes, withSender.sender);
            return Optional.empty();
        }
    }

    private InboundMappingOutcomes mapExternalMessageToSignal(final ExternalMessageWithSender withSender,
            final ExternalMessage externalMessage) {
        return InboundMappingOutcomes.of(inboundMappingProcessor.process(withSender.externalMessage), externalMessage,
                withSender.sender);
    }

    /**
     * Appends the ConnectionId to the processed {@code commandResponse} payload.
     *
     * @param commandResponse the CommandResponse (or Acknowledgement as subtype) to append the ConnectionId to
     * @param connectionId the ConnectionId to append to the CommandResponse's DittoHeader
     * @param <T> the type of the CommandResponse
     * @return the CommandResponse with appended ConnectionId.
     */
    static <T extends CommandResponse<T>> T appendConnectionIdToAcknowledgementOrResponse(final T commandResponse,
            final ConnectionId connectionId) {
        final DittoHeaders newHeaders = commandResponse.getDittoHeaders()
                .toBuilder()
                .putHeader(DittoHeaderDefinition.CONNECTION_ID.getKey(), connectionId.toString())
                .build();
        return commandResponse.setDittoHeaders(newHeaders);
    }

    static Acknowledgements appendConnectionIdToAcknowledgements(final Acknowledgements acknowledgements,
            final ConnectionId connectionId) {
        final List<Acknowledgement> acksList = acknowledgements.stream()
                .map(ack -> appendConnectionIdToAcknowledgementOrResponse(ack, connectionId))
                .collect(Collectors.toList());
        // Uses EntityId and StatusCode from input acknowledges expecting these were set when Acknowledgements was created
        return Acknowledgements.of(acknowledgements.getEntityId(), acksList, acknowledgements.getStatusCode(),
                acknowledgements.getDittoHeaders());
    }

    static final class ExternalMessageWithSender {

        private final ExternalMessage externalMessage;
        private final ActorRef sender;

        private ExternalMessageWithSender(
                final ExternalMessage externalMessage, final ActorRef sender) {
            this.externalMessage = externalMessage;
            this.sender = sender;
        }
    }

}
