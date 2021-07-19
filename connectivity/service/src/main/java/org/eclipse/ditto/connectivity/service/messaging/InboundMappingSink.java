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
package org.eclipse.ditto.connectivity.service.messaging;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.service.config.mapping.MappingConfig;
import org.eclipse.ditto.connectivity.service.messaging.BaseClientActor.ReplaceInboundMappingProcessor;
import org.eclipse.ditto.connectivity.service.util.ConnectivityMdcEntryKey;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Status;
import akka.dispatch.MessageDispatcher;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;

/**
 * This class creates a Sink which is responsible for inbound payload mapping.
 * The instance of this class holds the "state" of the sink (see {@link #inboundMappingProcessor}).
 */
public final class InboundMappingSink {

    private final ThreadSafeDittoLogger logger;

    private final int processorPoolSize;
    private final MessageDispatcher messageMappingProcessorDispatcher;
    private final ActorRef inboundDispatchingActor;
    private final AtomicReference<InboundMappingProcessor> inboundMappingProcessor;

    private InboundMappingSink(final InboundMappingProcessor inboundMappingProcessor,
            final ConnectionId connectionId,
            final int processorPoolSize,
            final ActorRef inboundDispatchingActor,
            final MappingConfig mappingConfig,
            final MessageDispatcher messageMappingProcessorDispatcher) {

        this.messageMappingProcessorDispatcher = messageMappingProcessorDispatcher;
        this.inboundMappingProcessor = new AtomicReference<>(inboundMappingProcessor);
        this.inboundDispatchingActor = inboundDispatchingActor;

        logger = DittoLoggerFactory.getThreadSafeLogger(InboundMappingSink.class)
                .withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID, connectionId);

        this.processorPoolSize = this.determinePoolSize(processorPoolSize, mappingConfig.getMaxPoolSize());
    }


    /**
     * Creates a Sink which is responsible for inbound payload mapping.
     *
     * @param inboundMappingProcessor the MessageMappingProcessor to use for inbound messages.
     * @param connectionId the connectionId
     * @param processorPoolSize how many message processing may happen in parallel per direction (incoming or outgoing).
     * @param inboundDispatchingActor used to dispatch inbound signals.
     * @param mappingConfig The mapping config.
     * @param messageMappingProcessorDispatcher The dispatcher which is used for async mapping.
     * @return the Akka configuration Props object.
     */
    public static Sink<Object, NotUsed> createSink(
            final InboundMappingProcessor inboundMappingProcessor,
            final ConnectionId connectionId,
            final int processorPoolSize,
            final ActorRef inboundDispatchingActor,
            final MappingConfig mappingConfig,
            final MessageDispatcher messageMappingProcessorDispatcher) {

        final var inboundMappingSink = new InboundMappingSink(inboundMappingProcessor,
                connectionId,
                processorPoolSize,
                inboundDispatchingActor,
                mappingConfig,
                messageMappingProcessorDispatcher);

        return inboundMappingSink.getSink();
    }

    private Sink<Object, NotUsed> getSink() {
        return Flow.create()
                .divertTo(logStatusFailure(), Status.Failure.class::isInstance)
                .divertTo(replaceInboundMappingProcessor(), ReplaceInboundMappingProcessor.class::isInstance)
                .divertTo(handleDittoRuntimeException(), DittoRuntimeException.class::isInstance)
                .divertTo(mapMessage(), ExternalMessageWithSender.class::isInstance)
                .to(Sink.foreach(message -> logger.warn("Received unknown message <{}>.", message)));
    }

    private Sink<Object, ?> logStatusFailure() {
        return Flow.fromFunction(Status.Failure.class::cast)
                .to(Sink.foreach(f -> logger.warn("Got failure with cause {}: {}",
                        f.cause().getClass().getSimpleName(), f.cause().getMessage())));
    }

    private Sink<Object, NotUsed> replaceInboundMappingProcessor() {
        return Flow.fromFunction(ReplaceInboundMappingProcessor.class::cast)
                .to(Sink.foreach(replaceProcessor -> {
                    logger.info("Replacing the InboundMappingProcessor with a modified one.");
                    this.inboundMappingProcessor.set(replaceProcessor.getInboundMappingProcessor());
                }));
    }

    private Sink<Object, NotUsed> handleDittoRuntimeException() {
        return Flow.fromFunction(DittoRuntimeException.class::cast)
                .to(Sink.foreach(this::handleDittoRuntimeException));
    }

    private Sink<Object, NotUsed> mapMessage() {
        final Flow<ExternalMessageWithSender, Optional<InboundMappingOutcomes>, ?> flow =
                Flow.<ExternalMessageWithSender>create()
                        // parallelize potentially CPU-intensive payload mapping on this actor's dispatcher
                        .mapAsync(processorPoolSize, externalMessage -> CompletableFuture.supplyAsync(
                                () -> {
                                    logger.debug("Received inbound Message to map: {}", externalMessage);
                                    return mapInboundMessage(externalMessage);
                                },
                                messageMappingProcessorDispatcher)
                        )
                        // map returns outcome
                        .throttle(1000, Duration.ofSeconds(1));

        final Sink<Optional<InboundMappingOutcomes>, ?> sink = Sink.<Optional<InboundMappingOutcomes>>foreach(
                outcomesOptional -> outcomesOptional.ifPresent(outcomes ->
                        inboundDispatchingActor.tell(outcomes, outcomes.getSender())
                ));

        final Sink<ExternalMessageWithSender, ?> mappingSink = flow.to(sink);

        return Flow.fromFunction(ExternalMessageWithSender.class::cast)
                .to(mappingSink);
    }

    private int determinePoolSize(final int connectionPoolSize, final int maxPoolSize) {
        if (connectionPoolSize > maxPoolSize) {
            logger.info("Configured pool size <{}> is greater than the configured max pool size <{}>." +
                    " Will use max pool size <{}>.", connectionPoolSize, maxPoolSize, maxPoolSize);
            return maxPoolSize;
        }
        return connectionPoolSize;
    }

    private void handleDittoRuntimeException(final DittoRuntimeException dittoRuntimeException) {
        inboundDispatchingActor.tell(dittoRuntimeException, ActorRef.noSender());
    }

    private Optional<InboundMappingOutcomes> mapInboundMessage(final ExternalMessageWithSender withSender) {
        final var externalMessage = withSender.externalMessage;
        final String correlationId =
                externalMessage.getHeaders().get(DittoHeaderDefinition.CORRELATION_ID.getKey());
        logger.withCorrelationId(correlationId)
                .debug("Handling ExternalMessage: {}", externalMessage);
        try {
            return Optional.of(mapExternalMessageToSignal(withSender, externalMessage));
        } catch (final Exception e) {
            final var outcomes =
                    InboundMappingOutcomes.of(withSender.externalMessage, e, withSender.sender);
            inboundDispatchingActor.tell(outcomes, withSender.sender);
            logger.withCorrelationId(correlationId)
                    .error("Handling exception when mapping external message: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private InboundMappingOutcomes mapExternalMessageToSignal(final ExternalMessageWithSender withSender,
            final ExternalMessage externalMessage) {
        return InboundMappingOutcomes.of(inboundMappingProcessor.get().process(withSender.externalMessage),
                externalMessage, withSender.sender);
    }

    static final class ExternalMessageWithSender {

        private final ExternalMessage externalMessage;
        private final ActorRef sender;

        ExternalMessageWithSender(final ExternalMessage externalMessage, final ActorRef sender) {
            this.externalMessage = externalMessage;
            this.sender = sender;
        }

    }

}
