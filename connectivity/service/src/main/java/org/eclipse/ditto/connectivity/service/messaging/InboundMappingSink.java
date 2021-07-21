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

import java.util.List;
import java.util.concurrent.CompletableFuture;

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
import akka.japi.function.Function;
import akka.japi.pf.PFBuilder;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import scala.PartialFunction;

/**
 * This class creates a Sink which is responsible for inbound payload mapping.
 * The instance of this class holds the "state" of the sink (see {@link #initialInboundMappingProcessor}).
 */
public final class InboundMappingSink {

    private final ThreadSafeDittoLogger logger;

    private final int processorPoolSize;
    private final MessageDispatcher messageMappingProcessorDispatcher;
    private final Sink<Object, ?> inboundDispatchingSink;
    private final InboundMappingProcessor initialInboundMappingProcessor;

    private InboundMappingSink(final InboundMappingProcessor initialInboundMappingProcessor,
            final ConnectionId connectionId,
            final int processorPoolSize,
            final Sink<Object, ?> inboundDispatchingSink,
            final MappingConfig mappingConfig,
            final MessageDispatcher messageMappingProcessorDispatcher) {

        this.messageMappingProcessorDispatcher = messageMappingProcessorDispatcher;
        this.initialInboundMappingProcessor = initialInboundMappingProcessor;
        this.inboundDispatchingSink = inboundDispatchingSink;

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
     * @param inboundDispatchingSink used to dispatch inbound signals.
     * @param mappingConfig The mapping config.
     * @param messageMappingProcessorDispatcher The dispatcher which is used for async mapping.
     * @return the Akka configuration Props object.
     */
    public static Sink<Object, NotUsed> createSink(
            final InboundMappingProcessor inboundMappingProcessor,
            final ConnectionId connectionId,
            final int processorPoolSize,
            final Sink<Object, ?> inboundDispatchingSink,
            final MappingConfig mappingConfig,
            final MessageDispatcher messageMappingProcessorDispatcher) {

        final var inboundMappingSink = new InboundMappingSink(inboundMappingProcessor,
                connectionId,
                processorPoolSize,
                inboundDispatchingSink,
                mappingConfig,
                messageMappingProcessorDispatcher);

        return inboundMappingSink.getSink();
    }

    private Sink<Object, NotUsed> getSink() {
        return Flow.create()
                .divertTo(logStatusFailure(), Status.Failure.class::isInstance)
                .divertTo(inboundDispatchingSink, DittoRuntimeException.class::isInstance)
                .divertTo(mapMessage(),
                        x -> x instanceof ExternalMessageWithSender || x instanceof ReplaceInboundMappingProcessor)
                .to(Sink.foreach(message -> logger.warn("Received unknown message <{}>.", message)));
    }

    private Sink<Object, ?> logStatusFailure() {
        return Flow.fromFunction(Status.Failure.class::cast)
                .to(Sink.foreach(f -> logger.warn("Got failure with cause {}: {}",
                        f.cause().getClass().getSimpleName(), f.cause().getMessage())));
    }

    private Sink<Object, NotUsed> mapMessage() {
        final Sink<MappingContext, NotUsed> mappingSink = Flow.<MappingContext>create()
                // parallelize potentially CPU-intensive payload mapping on this actor's dispatcher
                .mapAsync(processorPoolSize, mappingContext -> CompletableFuture.supplyAsync(
                        () -> {
                            logger.debug("Received inbound Message to map: {}", mappingContext);
                            return mapInboundMessage(mappingContext.message, mappingContext.mappingProcessor);
                        },
                        messageMappingProcessorDispatcher)
                )
                // map returns outcome
                .map(Object.class::cast)
                .to(inboundDispatchingSink);

        return Flow.create()
                .statefulMapConcat(StatefulExternalMessageHandler::new)
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

    private InboundMappingOutcomes mapInboundMessage(final ExternalMessageWithSender withSender,
            final InboundMappingProcessor inboundMappingProcessor) {
        final var externalMessage = withSender.externalMessage;
        final String correlationId =
                externalMessage.getHeaders().get(DittoHeaderDefinition.CORRELATION_ID.getKey());
        logger.withCorrelationId(correlationId)
                .debug("Handling ExternalMessage: {}", externalMessage);
        try {
            return mapExternalMessageToSignal(withSender, externalMessage, inboundMappingProcessor);
        } catch (final Exception e) {
            final var outcomes =
                    InboundMappingOutcomes.of(withSender.externalMessage, e, withSender.sender);
            logger.withCorrelationId(correlationId)
                    .error("Handling exception when mapping external message: {}", e.getMessage());
            return outcomes;
        }
    }

    private InboundMappingOutcomes mapExternalMessageToSignal(final ExternalMessageWithSender withSender,
            final ExternalMessage externalMessage, final InboundMappingProcessor inboundMappingProcessor) {
        return InboundMappingOutcomes.of(inboundMappingProcessor.process(withSender.externalMessage),
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

    private final class StatefulExternalMessageHandler implements Function<Object, Iterable<MappingContext>> {

        private transient InboundMappingProcessor inboundMappingProcessor = initialInboundMappingProcessor;
        private final transient PartialFunction<Object, Iterable<MappingContext>> matcher =
                new PFBuilder<Object, Iterable<MappingContext>>()
                        .match(ReplaceInboundMappingProcessor.class, replaceInboundMappingProcessor -> {
                            logger.info("Replacing the InboundMappingProcessor with a modified one.");
                            inboundMappingProcessor = replaceInboundMappingProcessor.getInboundMappingProcessor();
                            return List.of();
                        })
                        .match(ExternalMessageWithSender.class,
                                message -> List.of(new MappingContext(message, inboundMappingProcessor)))
                        .matchAny(streamingElement -> {
                            logger.warn("Received unknown message <{}>.", streamingElement);
                            return List.of();
                        })
                        .build();

        @Override
        public Iterable<MappingContext> apply(final Object streamingElement) {
            return matcher.apply(streamingElement);
        }

    }

    private static final class MappingContext {

        private final ExternalMessageWithSender message;
        private final InboundMappingProcessor mappingProcessor;

        private MappingContext(
                final ExternalMessageWithSender message,
                final InboundMappingProcessor mappingProcessor) {
            this.message = message;
            this.mappingProcessor = mappingProcessor;
        }

    }

}
