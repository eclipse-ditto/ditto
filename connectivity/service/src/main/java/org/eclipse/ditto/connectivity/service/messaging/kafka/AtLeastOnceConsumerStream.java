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
import scala.util.Either;
import scala.util.Left;
import scala.util.Right;

/**
 * Kafka consumer stream with "at least once" (QoS 1) semantics.
 */
@Immutable
final class AtLeastOnceConsumerStream implements KafkaConsumerStream {

    private static final Logger LOGGER = DittoLoggerFactory.getThreadSafeLogger(AtLeastOnceConsumerStream.class);

    private final ConnectionMonitor inboundMonitor;
    private final ConnectionMonitor ackMonitor;
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
            final ConnectionMonitor ackMonitor,
            final Sink<AcknowledgeableMessage, NotUsed> inboundMappingSink,
            final Sink<DittoRuntimeException, ?> dreSink) {

        this.inboundMonitor = inboundMonitor;
        this.ackMonitor = ackMonitor;
        this.inboundMappingSink = inboundMappingSink;
        this.dreSink = dreSink;
        this.materializer = materializer;
        consumerControl = sourceSupplier.get()
                .filter(committableMessage -> isNotDryRun(committableMessage.record(), dryRun))
                .map(kafkaMessageTransformer::transform)
                .via(processTransformationResult())
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

    private Flow<CommittableTransformationResult, CompletableFuture<CommittableOffset>, NotUsed> processTransformationResult() {
        return Flow.<CommittableTransformationResult>create()
                .via(externalMessageFlow())
                .via(dittoRuntimeExceptionFlow())
                .via(unexpectedMessageFlow());
    }

    private Flow<CommittableTransformationResult, Either<CommittableTransformationResult, CompletableFuture<CommittableOffset>>, NotUsed>
    externalMessageFlow() {
        return Flow.of(CommittableTransformationResult.class)
                .<Either<CommittableTransformationResult, KafkaAcknowledgableMessage>>map(transformationResult ->
                        isExternalMessage(transformationResult)
                                ? new Right<>(toAcknowledgeableMessage(transformationResult))
                                : new Left<>(transformationResult))
                .alsoTo(Flow.<Either<CommittableTransformationResult, KafkaAcknowledgableMessage>>create()
                        .filter(Either::isRight)
                        .map(either -> either.right().get())
                        .map(KafkaAcknowledgableMessage::getAcknowledgeableMessage)
                        .to(inboundMappingSink))
                .map(either -> either.right().map(KafkaAcknowledgableMessage::getAcknowledgementFuture));
    }

    private Flow<Either<CommittableTransformationResult, CompletableFuture<CommittableOffset>>, Either<CommittableTransformationResult, CompletableFuture<CommittableOffset>>, NotUsed>
    dittoRuntimeExceptionFlow() {
        return Flow.<Either<CommittableTransformationResult, CompletableFuture<CommittableOffset>>>create()
                .alsoTo(Flow.<Either<CommittableTransformationResult, CompletableFuture<CommittableOffset>>>create()
                        .filter(either -> either.isLeft() && isDittoRuntimeException(either.left().get()))
                        .map(either -> either.left().get())
                        .map(AtLeastOnceConsumerStream::extractDittoRuntimeException)
                        .to(dreSink))
                .map(either -> {
                    if (either.isLeft() && isDittoRuntimeException(either.left().get())) {
                        return new Right<>(
                                CompletableFuture.completedFuture(either.left().get().getCommittableOffset()));
                    } else {
                        return either;
                    }
                });
    }

    private Flow<Either<CommittableTransformationResult, CompletableFuture<CommittableOffset>>, CompletableFuture<CommittableOffset>, NotUsed>
    unexpectedMessageFlow() {
        return Flow.<Either<CommittableTransformationResult, CompletableFuture<CommittableOffset>>>create()
                .alsoTo(Flow.<Either<CommittableTransformationResult, CompletableFuture<CommittableOffset>>>create()
                        .filter(Either::isLeft)
                        .map(either -> either.left().get())
                        .to(unexpectedMessageSink()))
                .map(either -> {
                    if (either.isLeft()) {
                        return CompletableFuture.completedFuture(either.left().get().getCommittableOffset());
                    } else {
                        return either.right().get();
                    }
                });
    }

    private static boolean isExpired(final CommittableTransformationResult transformationResult) {
        return transformationResult.getTransformationResult().isExpired();
    }

    private static boolean isExternalMessage(final CommittableTransformationResult transformationResult) {
        return !isExpired(transformationResult) &&
                transformationResult.getTransformationResult().getExternalMessage().isPresent();
    }

    private KafkaAcknowledgableMessage toAcknowledgeableMessage(final CommittableTransformationResult value) {
        final ExternalMessage externalMessage = value.getTransformationResult()
                .getExternalMessage()
                .orElseThrow(); // at this point, the ExternalMessage is present
        final CommittableOffset committableOffset = value.getCommittableOffset();
        return new KafkaAcknowledgableMessage(externalMessage, committableOffset, ackMonitor);
    }

    private static boolean isNotDryRun(final ConsumerRecord<String, String> cRecord, final boolean dryRun) {
        if (dryRun && LOGGER.isDebugEnabled()) {
            LOGGER.debug("Dropping record (key: {}, topic: {}, partition: {}, offset: {}) in dry run mode.",
                    cRecord.key(), cRecord.topic(), cRecord.partition(), cRecord.offset());
        }
        return !dryRun;
    }

    private static boolean isDittoRuntimeException(final CommittableTransformationResult value) {
        return !isExpired(value) && value.getTransformationResult().getDittoRuntimeException().isPresent();
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
