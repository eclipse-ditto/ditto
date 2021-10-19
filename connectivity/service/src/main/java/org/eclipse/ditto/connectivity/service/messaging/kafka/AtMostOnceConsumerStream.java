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
import scala.util.Either;
import scala.util.Left;
import scala.util.Right;

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
                .via(processTransformationResult())
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

    private Flow<TransformationResult, Either<TransformationResult, CompletableFuture<Done>>, NotUsed>
    externalMessageFlow() {
        return Flow.of(TransformationResult.class)
                .<Either<TransformationResult, KafkaCompletableMessage>>map(transformationResult ->
                        isExternalMessage(transformationResult)
                                ? new Right<>(toAcknowledgeableMessage(transformationResult))
                                : new Left<>(transformationResult))
                .alsoTo(Flow.<Either<TransformationResult, KafkaCompletableMessage>>create()
                        .filter(Either::isRight)
                        .map(either -> either.right().get())
                        .map(KafkaCompletableMessage::getAcknowledgeableMessage)
                        .to(inboundMappingSink))
                .map(either -> either.right().map(KafkaCompletableMessage::getAcknowledgementFuture));
    }

    private Flow<Either<TransformationResult, CompletableFuture<Done>>, Either<TransformationResult, CompletableFuture<Done>>, NotUsed>
    dittoRuntimeExceptionFlow() {
        return Flow.<Either<TransformationResult, CompletableFuture<Done>>>create()
                .alsoTo(Flow.<Either<TransformationResult, CompletableFuture<Done>>>create()
                        .filter(either -> either.isLeft() && isDittoRuntimeException(either.left().get()))
                        .map(either -> either.left().get())
                        .map(AtMostOnceConsumerStream::extractDittoRuntimeException)
                        .to(dreSink))
                .map(either -> {
                    if (either.isLeft() && isDittoRuntimeException(either.left().get())) {
                        return new Right<>(CompletableFuture.completedFuture(Done.getInstance()));
                    } else {
                        return either;
                    }
                });
    }

    private Flow<Either<TransformationResult, CompletableFuture<Done>>, CompletableFuture<Done>, NotUsed>
    unexpectedMessageFlow() {
        return Flow.<Either<TransformationResult, CompletableFuture<Done>>>create()
                .alsoTo(Flow.<Either<TransformationResult, CompletableFuture<Done>>>create()
                        .filter(Either::isLeft)
                        .map(either -> either.left().get())
                        .to(unexpectedMessageSink()))
                .map(either -> {
                    if (either.isLeft()) {
                        return CompletableFuture.completedFuture(Done.getInstance());
                    } else {
                        return either.right().get();
                    }
                });
    }

    private Flow<TransformationResult, CompletableFuture<Done>, NotUsed> processTransformationResult() {
        return Flow.<TransformationResult>create()
                .via(externalMessageFlow())
                .via(dittoRuntimeExceptionFlow())
                .via(unexpectedMessageFlow());
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
