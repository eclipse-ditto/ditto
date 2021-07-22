/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.DittoConnectivityConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;

import akka.NotUsed;
import akka.stream.Materializer;
import akka.stream.OverflowStrategy;
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

    private final SourceQueueWithComplete<AcknowledgeableMessage> messageSourceQueue;
    private final SourceQueueWithComplete<DittoRuntimeException> dreSourceQueue;

    protected LegacyBaseConsumerActor(final Connection connection, final String sourceAddress,
            final Sink<Object, ?> inboundMappingSink, final Source source) {
        super(connection, sourceAddress, inboundMappingSink, source);

        final ConnectivityConfig connectivityConfig = DittoConnectivityConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config()));

        final var materializer = Materializer.createMaterializer(this::getContext);

        messageSourceQueue = akka.stream.javadsl.Source
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
    protected final void forwardToMappingActor(final ExternalMessage message, final Runnable settle,
            final Reject reject) {
        messageSourceQueue.offer(AcknowledgeableMessage.of(message, settle, reject));
    }

    /**
     * Send an error to the inbound mapping sink to be published in the reply-target.
     *
     * @param message the error.
     */
    protected final void forwardToMappingActor(final DittoRuntimeException message) {
        dreSourceQueue.offer(message);
    }

}
