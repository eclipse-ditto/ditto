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
import java.util.function.BiConsumer;

import javax.annotation.concurrent.Immutable;

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
import akka.stream.javadsl.Source;

/**
 * Kafka consumer stream with "at most once" (QoS 0) semantics.
 */
@Immutable
final class AtMostOnceConsumerStream implements KafkaConsumerStream {

    private static final Logger LOGGER = DittoLoggerFactory.getThreadSafeLogger(AtMostOnceConsumerStream.class);

    private final ConnectionMonitor inboundMonitor;
    private final Materializer materializer;
    private final Consumer.DrainingControl<Done> consumerControl;
    private final Sink<AcknowledgeableMessage, NotUsed> inboundMappingSink;
    private final Sink<DittoRuntimeException, ?> dreSink;

    AtMostOnceConsumerStream(
            final AtMostOnceKafkaConsumerSourceSupplier sourceSupplier,
            final int consumerMaxInflight,
            final KafkaMessageTransformer kafkaMessageTransformer,
            final boolean dryRun,
            final Materializer materializer,
            final ConnectionMonitor inboundMonitor,
            final Sink<AcknowledgeableMessage, NotUsed> inboundMappingSink,
            final Sink<DittoRuntimeException, ?> dreSink) {

        this.inboundMonitor = inboundMonitor;
        this.materializer = materializer;
        this.inboundMappingSink = inboundMappingSink;
        this.dreSink = dreSink;
        consumerControl = sourceSupplier.get()
                .filter(consumerRecord -> isNotDryRun(consumerRecord, dryRun))
                .map(kafkaMessageTransformer::transform)
                .filter(result -> !result.isExpired())
                .flatMapConcat(this::processTransformationResult)
                .mapAsync(consumerMaxInflight, x -> x)
                .toMat(Sink.ignore(), Consumer::createDrainingControl)
                .run(materializer);
    }

    @Override
    public CompletionStage<Done> whenComplete(final BiConsumer<? super Done, ? super Throwable> handleCompletion) {
        return consumerControl.streamCompletion().whenComplete(handleCompletion);
    }

    @Override
    public CompletionStage<Done> stop() {
        return consumerControl.drainAndShutdown(materializer.executionContext());
    }

    private Sink<KafkaCompletableMessage, NotUsed> externalMessageSink() {
        return Flow.of(KafkaCompletableMessage.class)
                .map(KafkaCompletableMessage::getAcknowledgeableMessage)
                .to(inboundMappingSink);
    }

    private Source<CompletableFuture<Done>, NotUsed> processTransformationResult(
            final TransformationResult result) {

        if (isExternalMessage(result)) {
            return Source.single(result)
                    .map(this::toAcknowledgeableMessage)
                    .alsoTo(this.externalMessageSink())
                    .map(KafkaCompletableMessage::getAcknowledgementFuture);
        }

        final CompletableFuture<Done> offsetFuture = CompletableFuture.completedFuture(Done.getInstance());

        if (isDittoRuntimeException(result)) {
            return Source.single(result)
                    .map(AtMostOnceConsumerStream::extractDittoRuntimeException)
                    .alsoTo(dreSink)
                    .map(dre -> offsetFuture);
        }

        return Source.single(result)
                .alsoTo(unexpectedMessageSink())
                .map(unexpected -> offsetFuture);
    }

    private KafkaCompletableMessage toAcknowledgeableMessage(final TransformationResult value) {
        final ExternalMessage externalMessage =
                value.getExternalMessage().orElseThrow(); // at this point, the ExternalMessage is present
        return new KafkaCompletableMessage(externalMessage);
    }

    private static boolean isExternalMessage(final TransformationResult value) {
        return value.getExternalMessage().isPresent();
    }

    private static boolean isNotDryRun(final ConsumerRecord<String, String> cRecord, final boolean dryRun) {
        if (dryRun && LOGGER.isDebugEnabled()) {
            LOGGER.debug("Dropping record (key: {}, topic: {}, partition: {}, offset: {}) in dry run mode.",
                    cRecord.key(), cRecord.topic(), cRecord.partition(), cRecord.offset());
        }
        return !dryRun;
    }

    private static boolean isDittoRuntimeException(final TransformationResult value) {
        return value.getDittoRuntimeException().isPresent();
    }

    private static DittoRuntimeException extractDittoRuntimeException(final TransformationResult value) {
        return value.getDittoRuntimeException().orElseThrow(); // at this point, the DRE is present
    }

    private Sink<TransformationResult, CompletionStage<Done>> unexpectedMessageSink() {
        return Sink.foreach(either -> inboundMonitor.exception(
                "Got unexpected transformation result <{0}>. This is an internal error. " +
                        "Please contact the service team.", either
        ));
    }

}
