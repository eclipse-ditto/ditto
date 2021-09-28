/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.service.config.ThrottlingConfig;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.service.config.mapping.MappingConfig;
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

    /**
     * Defines the maximum of costs that can be processed by the sink per second. This value is used to
     * initialize the throttling on the stream. The actual throughput is defined by the cost per message i.e. if the
     * cost per message is 1, the throughput is the maximum ({@value}), if the cost is the maximum ({@value}) the
     * throughput is 1.
     */
    private static final int MAXIMUM_COSTS_PER_SECOND = 100_000;

    private final ThreadSafeDittoLogger logger;

    private final InboundMappingProcessor initialInboundMappingProcessor;
    private final Sink<Object, ?> inboundDispatchingSink;
    @Nullable private final ThrottlingConfig throttlingConfig;
    private final MessageDispatcher messageMappingProcessorDispatcher;
    private final int processorPoolSize;
    private final int initialCostPerMessage;

    private InboundMappingSink(final InboundMappingProcessor initialInboundMappingProcessor,
            final ConnectionId connectionId,
            final int processorPoolSize,
            final Sink<Object, ?> inboundDispatchingSink,
            final MappingConfig mappingConfig,
            @Nullable final ThrottlingConfig throttlingConfig,
            final MessageDispatcher messageMappingProcessorDispatcher) {

        this.initialInboundMappingProcessor =
                checkNotNull(initialInboundMappingProcessor, "initialInboundMappingProcessor");
        this.inboundDispatchingSink = checkNotNull(inboundDispatchingSink, "inboundDispatchingSink");
        checkNotNull(mappingConfig, "mappingConfig");
        this.throttlingConfig = throttlingConfig;
        this.messageMappingProcessorDispatcher =
                checkNotNull(messageMappingProcessorDispatcher, "messageMappingProcessorDispatcher");

        logger = DittoLoggerFactory.getThreadSafeLogger(InboundMappingSink.class)
                .withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID, connectionId);

        this.processorPoolSize = this.determinePoolSize(processorPoolSize, mappingConfig.getMaxPoolSize());

        if (throttlingConfig != null) {
            initialCostPerMessage = calculateCostPerMessage(throttlingConfig);
            logger.debug("Cost per message initialized to: {}", initialCostPerMessage);
        } else {
            initialCostPerMessage = 1;
        }
    }

    static int calculateCostPerMessage(final ThrottlingConfig throttlingConfig) {
        final int limit = throttlingConfig.getLimit();
        final Duration interval = throttlingConfig.getInterval();
        final double limitPerInterval = (double) limit / interval.toSeconds();
        return (int) (MAXIMUM_COSTS_PER_SECOND / limitPerInterval);
    }

    /**
     * Creates a Sink which is responsible for inbound payload mapping.
     *
     * @param inboundMappingProcessor the MessageMappingProcessor to use for inbound messages.
     * @param connectionId the connectionId
     * @param processorPoolSize how many message processing may happen in parallel per direction (incoming or outgoing).
     * @param inboundDispatchingSink used to dispatch inbound signals.
     * @param mappingConfig The mapping config.
     * @param throttlingConfig the throttling config.
     * @param messageMappingProcessorDispatcher The dispatcher which is used for async mapping.
     * @return the Sink.
     * @throws java.lang.NullPointerException if any of the passed arguments except {@code throttlingConfig} was
     * {@code null}.
     */
    public static Sink<Object, NotUsed> createSink(
            final InboundMappingProcessor inboundMappingProcessor,
            final ConnectionId connectionId,
            final int processorPoolSize,
            final Sink<Object, ?> inboundDispatchingSink,
            final MappingConfig mappingConfig,
            @Nullable final ThrottlingConfig throttlingConfig,
            final MessageDispatcher messageMappingProcessorDispatcher) {

        final var inboundMappingSink = new InboundMappingSink(inboundMappingProcessor,
                connectionId,
                processorPoolSize,
                inboundDispatchingSink,
                mappingConfig,
                throttlingConfig,
                messageMappingProcessorDispatcher);

        return inboundMappingSink.getSink();
    }

    private Sink<Object, NotUsed> getSink() {
        return Flow.create()
                .divertTo(logStatusFailure(), Status.Failure.class::isInstance)
                .divertTo(inboundDispatchingSink, DittoRuntimeException.class::isInstance)
                .divertTo(mapMessage(),
                        x -> x instanceof ExternalMessageWithSender ||
                                x instanceof BaseClientActor.ReplaceInboundMappingProcessor ||
                                x instanceof BaseClientActor.UpdateCostsPerMessage)
                .to(Sink.foreach(message -> logger.warn("Received unknown message <{}>.", message)));
    }

    private Sink<Object, ?> logStatusFailure() {
        return Flow.fromFunction(Status.Failure.class::cast)
                .to(Sink.foreach(f -> logger.warn("Got failure with cause {}: {}",
                        f.cause().getClass().getSimpleName(), f.cause().getMessage())));
    }

    private Sink<Object, NotUsed> mapMessage() {
        final Flow<Object, InboundMappingOutcomes, NotUsed> mapMessageFlow = Flow.create()
                .statefulMapConcat(StatefulExternalMessageHandler::new)
                // parallelize potentially CPU-intensive payload mapping on this actor's dispatcher
                .mapAsync(processorPoolSize, mappingContext -> CompletableFuture.supplyAsync(
                        () -> {
                            logger.debug("Received inbound Message to map: {}", mappingContext);
                            return mapInboundMessage(mappingContext.message, mappingContext.mappingProcessor,
                                    mappingContext.costPerMessage);
                        },
                        messageMappingProcessorDispatcher)
                );

        final Flow<Object, InboundMappingOutcomes, NotUsed> flowWithOptionalThrottling;
        if (throttlingConfig != null) {
            // use fixed throttle and adjust costs via UpdateCostsPerMessage message
            flowWithOptionalThrottling = mapMessageFlow.throttle(MAXIMUM_COSTS_PER_SECOND, Duration.ofSeconds(1),
                    InboundMappingOutcomes::getCosts);
        } else {
            flowWithOptionalThrottling = mapMessageFlow;
        }

        return flowWithOptionalThrottling
                // map returns outcome
                .map(Object.class::cast)
                .to(inboundDispatchingSink);
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
            final InboundMappingProcessor inboundMappingProcessor, final int costPerMessage) {
        final var externalMessage = withSender.externalMessage;
        final String correlationId =
                externalMessage.getHeaders().get(DittoHeaderDefinition.CORRELATION_ID.getKey());
        logger.withCorrelationId(correlationId)
                .debug("Handling ExternalMessage: {}", externalMessage);
        try {
            return mapExternalMessageToSignal(withSender, externalMessage, inboundMappingProcessor, costPerMessage);
        } catch (final Exception e) {
            final var outcomes =
                    InboundMappingOutcomes.of(withSender.externalMessage, e, withSender.sender, costPerMessage);
            logger.withCorrelationId(correlationId)
                    .error("Handling exception when mapping external message: {}", e.getMessage());
            return outcomes;
        }
    }

    private InboundMappingOutcomes mapExternalMessageToSignal(final ExternalMessageWithSender withSender,
            final ExternalMessage externalMessage, final InboundMappingProcessor inboundMappingProcessor,
            final int costPerMessage) {
        return InboundMappingOutcomes.of(inboundMappingProcessor.process(withSender.externalMessage),
                externalMessage, withSender.sender, costPerMessage);
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
        private transient int costPerMessage = initialCostPerMessage;
        private final transient PartialFunction<Object, Iterable<MappingContext>> matcher =
                new PFBuilder<Object, Iterable<MappingContext>>()
                        .match(BaseClientActor.ReplaceInboundMappingProcessor.class, replaceInboundMappingProcessor -> {
                            logger.info("Replacing the InboundMappingProcessor with a modified one.");
                            inboundMappingProcessor = replaceInboundMappingProcessor.getInboundMappingProcessor();
                            return List.of();
                        })
                        .match(BaseClientActor.UpdateCostsPerMessage.class, updateCostsPerMessage -> {
                            logger.info("Updating cost per message: {}", updateCostsPerMessage.getCostPerMessage());
                            costPerMessage = updateCostsPerMessage.getCostPerMessage();
                            return List.of();
                        })
                        .match(ExternalMessageWithSender.class,
                                message -> List.of(
                                        new MappingContext(message, inboundMappingProcessor, costPerMessage)))
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
        private final int costPerMessage;

        private MappingContext(
                final ExternalMessageWithSender message,
                final InboundMappingProcessor mappingProcessor, final int costPerMessage) {
            this.message = message;
            this.mappingProcessor = mappingProcessor;
            this.costPerMessage = costPerMessage;
        }

    }
}
