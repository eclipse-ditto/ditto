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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.service.messaging.AcknowledgeableMessage;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.slf4j.Logger;

import akka.Done;
import akka.NotUsed;
import akka.japi.function.Function;
import akka.kafka.CommitterSettings;
import akka.kafka.ConsumerMessage;
import akka.kafka.javadsl.Committer;
import akka.kafka.javadsl.Consumer;
import akka.stream.Attributes;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;

final class AtLeastOnceConsumerStream implements KafkaConsumerStream {

    private static final Logger LOGGER = DittoLoggerFactory.getThreadSafeLogger(AtLeastOnceConsumerStream.class);
    private static final String TTL = "ttl";
    private static final String CREATION_TIME = "creation-time";

    private final akka.stream.javadsl.Source<CommittableTransformationResult, Consumer.Control> runnableKafkaStream;
    private final ConnectionMonitor inboundMonitor;
    private final Materializer materializer;
    private final CommitterSettings committerSettings;
    @Nullable private Consumer.Control consumerControl;

    AtLeastOnceConsumerStream(
            final AtLeastOnceKafkaConsumerSourceSupplier sourceSupplier,
            final CommitterSettings committerSettings,
            final KafkaMessageTransformer kafkaMessageTransformer,
            final boolean dryRun,
            final Materializer materializer,
            final ConnectionMonitor inboundMonitor,
            final Sink<AcknowledgeableMessage, NotUsed> inboundMappingSink,
            final Sink<DittoRuntimeException, ?> dreSink) {

        this.inboundMonitor = inboundMonitor;
        this.materializer = materializer;
        this.committerSettings = committerSettings;
        runnableKafkaStream = sourceSupplier.get()
                .filter(committableMessage -> isNotDryRun(committableMessage.record(), dryRun))
                .filter(committableMessage -> committableMessage.record().value() != null)
                .filter(committableMessage -> isNotExpired(committableMessage.record()))
                .map(kafkaMessageTransformer::transform)
                .divertTo(this.externalMessageSink(inboundMappingSink), this::isExternalMessage)
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

    private Sink<CommittableTransformationResult, ?> externalMessageSink(
            final Sink<AcknowledgeableMessage, NotUsed> inboundMappingSink) {
        return Flow.fromFunction(this::toAcknowledgeableMessage)
                .statefulMapConcat(MessageSequentializer::new)
                .alsoTo(committerSink())
                .map(KafkaAcknowledgableMessage::getAcknowledgeableMessage)
                .to(inboundMappingSink);
    }

    private Sink<KafkaAcknowledgableMessage, NotUsed> committerSink() {
        return Flow.of(KafkaAcknowledgableMessage.class)
                .mapAsync(1, KafkaAcknowledgableMessage::getAcknowledgementFuture)
                .to(Committer.sink(committerSettings));
    }

    private boolean isExternalMessage(final CommittableTransformationResult transformationResult) {
        return transformationResult.getTransformationResult().getExternalMessage().isPresent();
    }

    private KafkaAcknowledgableMessage toAcknowledgeableMessage(final CommittableTransformationResult value) {
        final ExternalMessage externalMessage = value.getTransformationResult().getExternalMessage().get();
        final ConsumerMessage.CommittableOffset committableOffset = value.getCommittableOffset();
        return new KafkaAcknowledgableMessage(externalMessage, committableOffset);
    }

    private boolean isNotExpired(final ConsumerRecord<String, String> consumerRecord) {
        final Headers headers = consumerRecord.headers();
        final long now = Instant.now().toEpochMilli();
        try {
            final Optional<Long> creationTimeOptional = Optional.ofNullable(headers.lastHeader(CREATION_TIME))
                    .map(Header::value)
                    .map(String::new)
                    .map(Long::parseLong);
            final Optional<Long> ttlOptional = Optional.ofNullable(headers.lastHeader(TTL))
                    .map(Header::value)
                    .map(String::new)
                    .map(Long::parseLong);
            if (creationTimeOptional.isPresent() && ttlOptional.isPresent()) {
                return now - creationTimeOptional.get() >= ttlOptional.get();
            }
            return true;
        } catch (final Exception e) {
            // Errors during reading/parsing headers should not cause the message to be dropped.
            return true;
        }
    }

    private boolean isNotDryRun(final ConsumerRecord<String, String> record, final boolean dryRun) {
        if (dryRun && LOGGER.isDebugEnabled()) {
            LOGGER.debug("Dropping record (key: {}, topic: {}, partition: {}, offset: {}) in dry run mode.",
                    record.key(), record.topic(), record.partition(), record.offset());
        }
        return !dryRun;
    }

    private Sink<CommittableTransformationResult, ?> dittoRuntimeExceptionSink(
            final Sink<DittoRuntimeException, ?> dreSink) {
        return Flow.fromFunction(this::extractDittoRuntimeException)
                .to(dreSink);
    }

    private boolean isDittoRuntimeException(final CommittableTransformationResult value) {
        return value.getTransformationResult().getDittoRuntimeException().isPresent();
    }

    private DittoRuntimeException extractDittoRuntimeException(final CommittableTransformationResult value) {
        return value.getTransformationResult().getDittoRuntimeException().get();
    }

    private Sink<CommittableTransformationResult, CompletionStage<Done>> unexpectedMessageSink() {
        return Sink.foreach(either -> inboundMonitor.exception(
                "Got unexpected transformation result <{0}>. This is an internal error. " +
                        "Please contact the service team.", either
        ));
    }

    private static final class MessageSequentializer implements
            Function<KafkaAcknowledgableMessage, Iterable<KafkaAcknowledgableMessage>> {

        private transient CompletableFuture<ConsumerMessage.CommittableOffset> last;

        private MessageSequentializer() {
            last = new CompletableFuture<>();
            last.complete(null);
        }

        @Override
        public Iterable<KafkaAcknowledgableMessage> apply(final KafkaAcknowledgableMessage kafkaAcknowledgableMessage) {
            final KafkaAcknowledgableMessage sequentialized = kafkaAcknowledgableMessage.commitAfter(last);
            last = sequentialized.getAcknowledgementFuture();
            return List.of(sequentialized);
        }

    }

}
