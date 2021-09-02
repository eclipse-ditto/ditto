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
import akka.kafka.CommitterSettings;
import akka.kafka.ConsumerMessage.CommittableOffset;
import akka.kafka.javadsl.Committer;
import akka.kafka.javadsl.Consumer;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Kafka consumer stream with "at least once" (QoS 1) semantics.
 */
@Immutable
final class AtLeastOnceConsumerStream implements KafkaConsumerStream {

    private static final Logger LOGGER = DittoLoggerFactory.getThreadSafeLogger(AtLeastOnceConsumerStream.class);

    private final ConnectionMonitor inboundMonitor;
    private final Materializer materializer;
    private final Sink<AcknowledgeableMessage, NotUsed> inboundMappingSink;
    private final Sink<DittoRuntimeException, ?> dreSink;
    private final Consumer.DrainingControl<Done> consumerControl;

    AtLeastOnceConsumerStream(
            final AtLeastOnceKafkaConsumerSourceSupplier sourceSupplier,
            final CommitterSettings committerSettings,
            final int consumerMaxInflight,
            final KafkaMessageTransformer kafkaMessageTransformer,
            final boolean dryRun,
            final Materializer materializer,
            final ConnectionMonitor inboundMonitor,
            final Sink<AcknowledgeableMessage, NotUsed> inboundMappingSink,
            final Sink<DittoRuntimeException, ?> dreSink) {

        this.inboundMonitor = inboundMonitor;
        this.inboundMappingSink = inboundMappingSink;
        this.dreSink = dreSink;
        this.materializer = materializer;
        consumerControl = sourceSupplier.get()
                .filter(committableMessage -> isNotDryRun(committableMessage.record(), dryRun))
                .filter(committableMessage -> committableMessage.record().value() != null)
                .filter(committableMessage -> KafkaConsumerStream.isNotExpired(committableMessage.record()))
                .map(kafkaMessageTransformer::transform)
                .flatMapConcat(this::processTransformationResult)
                .mapAsync(consumerMaxInflight, x -> x)
                .toMat(Committer.sink(committerSettings), Consumer::createDrainingControl)
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

    private Source<CompletableFuture<CommittableOffset>, NotUsed> processTransformationResult(
            final CommittableTransformationResult result) {
        if (isExternalMessage(result)) {
            return Source.single(result)
                    .map(AtLeastOnceConsumerStream::toAcknowledgeableMessage)
                    .alsoTo(this.externalMessageSink())
                    .map(KafkaAcknowledgableMessage::getAcknowledgementFuture);
        }
        /*
         * For all other cases a retry for consuming this message makes no sense, so we want to commit these offsets.
         * Therefore, we return an already completed future holding the offset to commit. No reject needed.
         */
        final CompletableFuture<CommittableOffset> offsetFuture =
                CompletableFuture.completedFuture(result.getCommittableOffset());
        if (isDittoRuntimeException(result)) {
            return Source.single(result)
                    .map(AtLeastOnceConsumerStream::extractDittoRuntimeException)
                    .alsoTo(dreSink)
                    .map(dre -> offsetFuture);
        }
        return Source.single(result)
                .alsoTo(unexpectedMessageSink())
                .map(unexpected -> offsetFuture);
    }

    private Sink<KafkaAcknowledgableMessage, NotUsed> externalMessageSink() {
        return Flow.of(KafkaAcknowledgableMessage.class)
                .map(KafkaAcknowledgableMessage::getAcknowledgeableMessage)
                .to(inboundMappingSink);
    }

    private static boolean isExternalMessage(final CommittableTransformationResult transformationResult) {
        return transformationResult.getTransformationResult().getExternalMessage().isPresent();
    }

    private static KafkaAcknowledgableMessage toAcknowledgeableMessage(final CommittableTransformationResult value) {
        final ExternalMessage externalMessage = value.getTransformationResult()
                .getExternalMessage()
                .orElseThrow(); // at this point, the ExternalMessage is present
        final CommittableOffset committableOffset = value.getCommittableOffset();
        return new KafkaAcknowledgableMessage(externalMessage, committableOffset);
    }

    private static boolean isNotDryRun(final ConsumerRecord<String, String> cRecord, final boolean dryRun) {
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

    private Sink<CommittableTransformationResult, CompletionStage<Done>> unexpectedMessageSink() {
        return Sink.foreach(transformationResult -> inboundMonitor.exception(
                "Got unexpected transformation result <{0}>. This is an internal error. " +
                        "Please contact the service team.", transformationResult
        ));
    }

}
