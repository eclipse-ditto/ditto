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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.service.config.ThrottlingConfig;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.service.config.mapping.MappingConfig;
import org.eclipse.ditto.connectivity.service.messaging.mappingoutcome.MappingOutcome;
import org.eclipse.ditto.connectivity.service.util.ConnectivityMdcEntryKey;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;

import akka.NotUsed;
import akka.actor.Status;
import akka.dispatch.MessageDispatcher;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;

/**
 * This class creates a Sink which is responsible for inbound payload mapping.
 * The instance of this class holds the "state" of the sink (see {@link #inboundMappingProcessors}).
 */
public final class InboundMappingSink {

    private final ThreadSafeDittoLogger logger;

    private final List<InboundMappingProcessor> inboundMappingProcessors;
    private final Sink<Object, ?> inboundDispatchingSink;
    @Nullable private final ThrottlingConfig throttlingConfig;
    private final MessageDispatcher messageMappingProcessorDispatcher;
    private final int processorPoolSize;

    private InboundMappingSink(final List<InboundMappingProcessor> inboundMappingProcessors,
            final ConnectionId connectionId,
            final int processorPoolSize,
            final Sink<Object, ?> inboundDispatchingSink,
            final MappingConfig mappingConfig,
            @Nullable final ThrottlingConfig throttlingConfig,
            final MessageDispatcher messageMappingProcessorDispatcher) {

        this.inboundMappingProcessors = checkNotEmpty(inboundMappingProcessors, "inboundMappingProcessors");
        this.inboundDispatchingSink = checkNotNull(inboundDispatchingSink, "inboundDispatchingSink");
        checkNotNull(mappingConfig, "mappingConfig");
        this.throttlingConfig = throttlingConfig;
        this.messageMappingProcessorDispatcher =
                checkNotNull(messageMappingProcessorDispatcher, "messageMappingProcessorDispatcher");

        logger = DittoLoggerFactory.getThreadSafeLogger(InboundMappingSink.class)
                .withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID, connectionId);

        this.processorPoolSize = this.determinePoolSize(processorPoolSize, mappingConfig.getMaxPoolSize());
    }


    /**
     * Creates a Sink which is responsible for inbound payload mapping.
     *
     * @param inboundMappingProcessors the MessageMappingProcessors to use for inbound messages. If at least as many
     * processors are given as `processorPoolSize`, then each processor is guaranteed to be invoked sequentially.
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
            final List<InboundMappingProcessor> inboundMappingProcessors,
            final ConnectionId connectionId,
            final int processorPoolSize,
            final Sink<Object, ?> inboundDispatchingSink,
            final MappingConfig mappingConfig,
            @Nullable final ThrottlingConfig throttlingConfig,
            final MessageDispatcher messageMappingProcessorDispatcher) {

        final var inboundMappingSink = new InboundMappingSink(inboundMappingProcessors,
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
                .divertTo(mapMessage(), ExternalMessageWithSender.class::isInstance)
                .to(Sink.foreach(message -> logger.warn("Received unknown message <{}>.", message)));
    }

    private Sink<Object, ?> logStatusFailure() {
        return Flow.fromFunction(Status.Failure.class::cast)
                .to(Sink.foreach(f -> logger.warn("Got failure with cause {}: {}",
                        f.cause().getClass().getSimpleName(), f.cause().getMessage())));
    }

    private Sink<Object, NotUsed> mapMessage() {
        final Flow<Object, InboundMappingOutcomes, NotUsed> mapMessageFlow =
                Flow.fromFunction(ExternalMessageWithSender.class::cast)
                        .zipWithIndex()
                        // parallelize potentially CPU-intensive payload mapping on this actor's dispatcher
                        .mapAsync(processorPoolSize, pair -> CompletableFuture.supplyAsync(
                                () -> {
                                    final var message = pair.first();
                                    final int processorIndex = (int) (pair.second() % inboundMappingProcessors.size());
                                    final var inboundMappingProcessor = inboundMappingProcessors.get(processorIndex);
                                    logger.debug("Received inbound Message to map with processor no. <{}>: {}",
                                            processorIndex, message);
                                    return mapInboundMessage(message, inboundMappingProcessor);
                                },
                                messageMappingProcessorDispatcher)
                        );

        final Flow<Object, InboundMappingOutcomes, NotUsed> flowWithOptionalThrottling;
        if (throttlingConfig != null && throttlingConfig.isEnabled()) {
            flowWithOptionalThrottling = mapMessageFlow
                    .throttle(throttlingConfig.getLimit(), throttlingConfig.getInterval(),
                            outcomes -> (int) outcomes.getOutcomes()
                                    .stream()
                                    .filter(MappingOutcome::wasSuccessfullyMapped)
                                    .count());
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
            final InboundMappingProcessor inboundMappingProcessor) {

        final var externalMessage = withSender.externalMessage();
        @Nullable final var correlationId =
                externalMessage.findHeaderIgnoreCase(DittoHeaderDefinition.CORRELATION_ID.getKey()).orElse(null);
        logger.withCorrelationId(correlationId)
                .debug("Handling ExternalMessage: {}", externalMessage);
        try {
            return mapExternalMessageToSignal(withSender, inboundMappingProcessor);
        } catch (final Exception e) {
            logger.withCorrelationId(correlationId)
                    .error("Handling exception when mapping external message: {}", e.getMessage());
            return InboundMappingOutcomes.of(withSender.externalMessage(), e, withSender.sender());
        }
    }

    private static InboundMappingOutcomes mapExternalMessageToSignal(final ExternalMessageWithSender withSender,
            final InboundMappingProcessor inboundMappingProcessor) {

        return InboundMappingOutcomes.of(inboundMappingProcessor.process(withSender.externalMessage()),
                withSender.externalMessage(),
                withSender.sender());
    }

}
