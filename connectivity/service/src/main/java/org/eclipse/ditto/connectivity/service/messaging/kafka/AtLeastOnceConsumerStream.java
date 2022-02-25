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

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;

import javax.annotation.concurrent.Immutable;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.service.config.ConnectionThrottlingConfig;
import org.eclipse.ditto.connectivity.service.messaging.AcknowledgeableMessage;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.slf4j.Logger;

import akka.Done;
import akka.NotUsed;
import akka.kafka.CommitterSettings;
import akka.kafka.ConsumerMessage.CommittableOffset;
import akka.kafka.javadsl.Committer;
import akka.kafka.javadsl.Consumer;
import akka.stream.Materializer;
import akka.stream.javadsl.MergeHub;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Kafka consumer stream with "at least once" (QoS 1) semantics.
 */
@Immutable
final class AtLeastOnceConsumerStream implements KafkaConsumerStream {

    private static final Logger LOGGER = DittoLoggerFactory.getThreadSafeLogger(AtLeastOnceConsumerStream.class);

    private final ConnectionMonitor ackMonitor;
    private final Materializer materializer;
    private final Sink<KafkaAcknowledgableMessage, NotUsed> externalMessageSink;
    private final Sink<CommittableTransformationResult, NotUsed> dreSink;
    private final Sink<CommittableTransformationResult, NotUsed> unexpectedMessageSink;
    private final Consumer.DrainingControl<Done> consumerControl;
    private final KafkaConsumerMetrics consumerMetrics;

    AtLeastOnceConsumerStream(
            final AtLeastOnceKafkaConsumerSourceSupplier sourceSupplier,
            final CommitterSettings committerSettings,
            final ConnectionThrottlingConfig throttlingConfig,
            final KafkaMessageTransformer kafkaMessageTransformer,
            final boolean dryRun,
            final Materializer materializer,
            final ConnectionMonitor inboundMonitor,
            final ConnectionMonitor ackMonitor,
            final Sink<AcknowledgeableMessage, NotUsed> inboundMappingSink,
            final Sink<DittoRuntimeException, ?> exceptionSink,
            final ConnectionId connectionId,
            final String consumerId) {

        this.ackMonitor = ackMonitor;

        // Pre materialize sinks with MergeHub to avoid multiple materialization per kafka record in processTransformationResult
        externalMessageSink = MergeHub.of(KafkaAcknowledgableMessage.class)
                .map(KafkaAcknowledgableMessage::getAcknowledgeableMessage)
                .to(inboundMappingSink)
                .run(materializer);

        dreSink = MergeHub.of(CommittableTransformationResult.class)
                .map(AtLeastOnceConsumerStream::extractDittoRuntimeException)
                .to(exceptionSink)
                .run(materializer);

        unexpectedMessageSink = MergeHub.of(CommittableTransformationResult.class)
                .to(Sink.foreach(transformationResult -> inboundMonitor.exception(
                        "Got unexpected transformation result <{0}>. This is an internal error. " +
                                "Please contact the service team", transformationResult)))
                .run(materializer);

        this.materializer = materializer;

        final var source = sourceSupplier.get()
                .filter(committableMessage -> isNotDryRun(committableMessage.record(), dryRun))
                .map(kafkaMessageTransformer::transform);

        final Source<CommittableTransformationResult, Consumer.Control> throttledSource;
        if (throttlingConfig.isEnabled()) {
            throttledSource = source.throttle(throttlingConfig.getLimit(), throttlingConfig.getInterval());
        } else {
            throttledSource = source;
        }

        consumerControl = throttledSource
                .flatMapConcat(this::processTransformationResult)
                .mapAsync(throttlingConfig.getMaxInFlight(), x -> x)
                .toMat(Committer.sink(committerSettings), Consumer::createDrainingControl)
                .run(materializer);

        consumerMetrics = KafkaConsumerMetrics.newInstance(consumerControl, connectionId, consumerId);
    }

    @Override
    public CompletionStage<Done> whenComplete(final BiConsumer<? super Done, ? super Throwable> handleCompletion) {
        return consumerControl.streamCompletion().whenComplete(handleCompletion);
    }

    @Override
    public CompletionStage<Done> stop() {
        return consumerControl.drainAndShutdown(materializer.executionContext());
    }

    @Override
    public void reportMetrics() {
        consumerMetrics.reportMetrics();
    }

    private Source<CompletableFuture<CommittableOffset>, NotUsed> processTransformationResult(
            final CommittableTransformationResult result) {

        final CompletableFuture<CommittableOffset> offsetFuture =
                CompletableFuture.completedFuture(result.getCommittableOffset());

        if (isExpired(result)) {
            return Source.single(offsetFuture);
        }

        if (isExternalMessage(result)) {
            return Source.single(result)
                    .map(this::toAcknowledgeableMessage)
                    .alsoTo(externalMessageSink)
                    .map(KafkaAcknowledgableMessage::getAcknowledgementFuture);
        }
        /*
         * For all other cases a retry for consuming this message makes no sense, so we want to commit these offsets.
         * Therefore, we return an already completed future holding the offset to commit. No reject needed.
         */
        if (isDittoRuntimeException(result)) {
            return Source.single(result)
                    .alsoTo(dreSink)
                    .map(transformationResult -> offsetFuture);
        }
        return Source.single(result)
                .alsoTo(unexpectedMessageSink)
                .map(unexpected -> offsetFuture);
    }

    private static boolean isExpired(final CommittableTransformationResult transformationResult) {
        return transformationResult.getTransformationResult().isExpired();
    }

    private static boolean isExternalMessage(final CommittableTransformationResult transformationResult) {
        return transformationResult.getTransformationResult().getExternalMessage().isPresent();
    }

    private KafkaAcknowledgableMessage toAcknowledgeableMessage(final CommittableTransformationResult value) {
        final ExternalMessage externalMessage = value.getTransformationResult()
                .getExternalMessage()
                .orElseThrow(); // at this point, the ExternalMessage is present
        final CommittableOffset committableOffset = value.getCommittableOffset();
        return new KafkaAcknowledgableMessage(externalMessage, committableOffset, ackMonitor);
    }

    private static boolean isNotDryRun(final ConsumerRecord<String, ByteBuffer> cRecord, final boolean dryRun) {
        if (dryRun && LOGGER.isDebugEnabled()) {
            LOGGER.debug("Dropping record (key: {}, topic: {}, partition: {}, offset: {}) in dry run mode.",
                    cRecord.key(), cRecord.topic(), cRecord.partition(), cRecord.offset());
        }
        return !dryRun;
    }

    private static boolean isDittoRuntimeException(final CommittableTransformationResult value) {
        return value.getTransformationResult().getDittoRuntimeException().isPresent();
    }

    private static DittoRuntimeException extractDittoRuntimeException(final CommittableTransformationResult value) {
        return value.getTransformationResult()
                .getDittoRuntimeException()
                .orElseThrow(); // at this point, the DRE is present
    }

}
