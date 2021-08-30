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
package org.eclipse.ditto.connectivity.service.messaging.kafka;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.service.messaging.AcknowledgeableMessage;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.slf4j.Logger;

import akka.Done;
import akka.NotUsed;
import akka.kafka.javadsl.Consumer;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;

/**
 * Kafka consumer stream with "at most once" (QoS 0) semantics.
 */
final class AtMostOnceConsumerStream implements KafkaConsumerStream {

    private static final Logger LOGGER = DittoLoggerFactory.getThreadSafeLogger(AtMostOnceConsumerStream.class);

    private final akka.stream.javadsl.Source<TransformationResult, Consumer.Control> runnableKafkaStream;
    private final ConnectionMonitor inboundMonitor;
    private final Materializer materializer;
    @Nullable private Consumer.Control consumerControl;

    AtMostOnceConsumerStream(
            final AtMostOnceKafkaConsumerSourceSupplier sourceSupplier,
            final KafkaMessageTransformer kafkaMessageTransformer,
            final boolean dryRun,
            final Materializer materializer,
            final ConnectionMonitor inboundMonitor,
            final Sink<AcknowledgeableMessage, NotUsed> externalMessageSink,
            final Sink<DittoRuntimeException, ?> dreSink) {

        this.inboundMonitor = inboundMonitor;
        this.materializer = materializer;
        runnableKafkaStream = sourceSupplier.get()
                .filter(consumerRecord -> isNotDryRun(consumerRecord, dryRun))
                .filter(consumerRecord -> consumerRecord.value() != null)
                .filter(KafkaMessageTransformer::isNotExpired)
                .map(kafkaMessageTransformer::transform)
                .divertTo(this.externalMessageSink(externalMessageSink), this::isExternalMessage)
                .divertTo(this.dittoRuntimeExceptionSink(dreSink), this::isDittoRuntimeException);
    }

    @Override
    public CompletionStage<Done> start() throws IllegalStateException {
        if (consumerControl != null) {
            stop();
        }
        return runnableKafkaStream
                .mapMaterializedValue(cc -> {
                    consumerControl = cc;
                    return cc;
                })
                .runWith(unexpectedMessageSink(), materializer);
    }


    @Override
    public void stop() {
        if (consumerControl != null) {
            consumerControl.drainAndShutdown(new CompletableFuture<>(), materializer.executionContext());
            consumerControl = null;
        }
    }

    private Sink<TransformationResult, ?> externalMessageSink(
            final Sink<AcknowledgeableMessage, NotUsed> externalMessageSink) {
        return Flow.fromFunction(this::extractExternalMessage)
                .map(externalMessage -> AcknowledgeableMessage.of(externalMessage, () -> {
                            // NoOp because at most once
                        },
                        redeliver -> {
                            // NoOp because at most once
                        }))
                .to(externalMessageSink);
    }

    private boolean isExternalMessage(final TransformationResult value) {
        return value.getExternalMessage().isPresent();
    }

    private ExternalMessage extractExternalMessage(final TransformationResult value) {
        return value.getExternalMessage().orElseThrow(); // at this point, the ExternalMessage is present
    }

    private boolean isNotDryRun(final ConsumerRecord<String, String> cRecord, final boolean dryRun) {
        if (dryRun && LOGGER.isDebugEnabled()) {
            LOGGER.debug("Dropping record (key: {}, topic: {}, partition: {}, offset: {}) in dry run mode.",
                    cRecord.key(), cRecord.topic(), cRecord.partition(), cRecord.offset());
        }
        return !dryRun;
    }

    private Sink<TransformationResult, ?> dittoRuntimeExceptionSink(final Sink<DittoRuntimeException, ?> dreSink) {
        return Flow.fromFunction(this::extractDittoRuntimeException)
                .to(dreSink);
    }

    private boolean isDittoRuntimeException(final TransformationResult value) {
        return value.getDittoRuntimeException().isPresent();
    }

    private DittoRuntimeException extractDittoRuntimeException(final TransformationResult value) {
        return value.getDittoRuntimeException().orElseThrow(); // at this point, the DRE is present
    }

    private Sink<TransformationResult, CompletionStage<Done>> unexpectedMessageSink() {
        return Sink.foreach(either -> inboundMonitor.exception(
                "Got unexpected transformation result <{0}>. This is an internal error. " +
                        "Please contact the service team.", either
        ));
    }

}
