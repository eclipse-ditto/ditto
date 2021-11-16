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

import java.util.Objects;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.util.ConnectivityMdcEntryKey;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;

import akka.stream.Materializer;
import akka.stream.OverflowStrategy;
import akka.stream.QueueOfferResult;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.SourceQueueWithComplete;

/**
 * This class provides the previous default of telling messages to a mapping actor instead of using a stream
 * with backpressure. The goal is to implement a stream based processing step by step for all ConsumerActors.
 *
 * @deprecated Extend {@link BaseConsumerActor} directly instead.
 */
@Deprecated
public abstract class LegacyBaseConsumerActor extends BaseConsumerActor {

    protected final ThreadSafeDittoLoggingAdapter logger;

    private final SourceQueueWithComplete<AcknowledgeableMessage> messageMappingSourceQueue;
    private final SourceQueueWithComplete<DittoRuntimeException> dreSourceQueue;

    protected LegacyBaseConsumerActor(final Connection connection,
            final String sourceAddress,
            final Sink<Object, ?> inboundMappingSink,
            final Source source,
            final ConnectivityStatusResolver connectivityStatusResolver,
            final ConnectivityConfig connectivityConfig) {
        super(connection, sourceAddress, inboundMappingSink, source, connectivityStatusResolver, connectivityConfig);

        logger = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this)
                .withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID.toString(), connectionId);

        final var materializer = Materializer.createMaterializer(this::getContext);

        messageMappingSourceQueue = akka.stream.javadsl.Source
                .<AcknowledgeableMessage>queue(connectivityConfig.getMappingConfig().getBufferSize(),
                        OverflowStrategy.dropNew())
                .to(getMessageMappingSink())
                .run(materializer);

        dreSourceQueue = akka.stream.javadsl.Source
                .<DittoRuntimeException>queue(connectivityConfig.getMappingConfig().getBufferSize(),
                        OverflowStrategy.dropNew())
                .to(getDittoRuntimeExceptionSink())
                .run(materializer);
    }

    /**
     * Send an external message to the inbound mapping sink.
     *
     * @param message the external message
     * @param settle technically settle the incoming message. MUST be thread-safe.
     * @param reject technically reject the incoming message. MUST be thread-safe.
     */
    protected final void forwardToMapping(final ExternalMessage message, final Runnable settle,
            final Reject reject) {
        final AcknowledgeableMessage acknowledgeableMessage = AcknowledgeableMessage.of(message, settle, reject);
        messageMappingSourceQueue.offer(acknowledgeableMessage)
                .whenComplete((queueOfferResult, error) -> {
                    if (error != null) {
                        logger.withCorrelationId(message.getInternalHeaders())
                                .error(error,
                                        "Message mapping source queue failure, invoking 'reject with redeliver'.");
                        acknowledgeableMessage.reject(true);
                    } else if (Objects.equals(queueOfferResult, QueueOfferResult.dropped())) {
                        logger.withCorrelationId(message.getInternalHeaders())
                                .warning("Message mapping source queue dropped message as part of backpressure " +
                                        "strategy, invoking 'reject with redeliver'. Increase " +
                                        "'ditto.connectivity.mapping.buffer-size' if this situation prevails.");
                        acknowledgeableMessage.reject(true);
                    }
                });
    }

    /**
     * Send an error to the inbound mapping sink to be published in the reply-target.
     *
     * @param dittoRuntimeException the error.
     */
    protected final void forwardToMapping(final DittoRuntimeException dittoRuntimeException) {
        dreSourceQueue.offer(dittoRuntimeException)
                .whenComplete((queueOfferResult, error) -> {
                    if (error != null) {
                        logger.withCorrelationId(dittoRuntimeException)
                                .error(error, "DRE handling source queue failure.");
                    } else if (Objects.equals(queueOfferResult, QueueOfferResult.dropped())) {
                        logger.withCorrelationId(dittoRuntimeException)
                                .warning("DRE handling source queue dropped dittoRuntimeException as part of " +
                                        "backpressure strategy. Increase " +
                                        "'ditto.connectivity.mapping.buffer-size' if this situation prevails.");
                    }
                });
    }

}
