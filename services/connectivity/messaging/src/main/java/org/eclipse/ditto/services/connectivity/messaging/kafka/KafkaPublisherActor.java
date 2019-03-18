/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging.kafka;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientData;
import org.eclipse.ditto.services.connectivity.messaging.BasePublisherActor;
import org.eclipse.ditto.services.connectivity.messaging.metrics.ConnectionMetricsCollector;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.Done;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.Pair;
import akka.japi.pf.ReceiveBuilder;
import akka.kafka.ProducerMessage;
import akka.stream.ActorMaterializer;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;

/**
 * Responsible for publishing {@link org.eclipse.ditto.services.models.connectivity.ExternalMessage}s into an Kafka
 * broker.
 */
public final class KafkaPublisherActor extends BasePublisherActor<KafkaPublishTarget> {

    static final String ACTOR_NAME = "kafkaPublisher";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef kafkaClientActor;
    private final KafkaConnectionFactory connectionFactory;
    private final boolean dryRun;
    private boolean shuttingDown = false;

    private ActorRef sourceActor;


    private KafkaPublisherActor(final String connectionId, final List<Target> targets,
            final KafkaConnectionFactory factory,
            final ActorRef kafkaClientActor,
            final boolean dryRun) {
        super(connectionId, targets);
        this.kafkaClientActor = kafkaClientActor;
        this.dryRun = dryRun;
        this.connectionFactory = factory;

        this.startInternalKafkaProducer();
        this.reportInitialConnectionState();
    }

    /*
      TODO: test cases:
      1.0 what happens if the topic is missing -> org.apache.kafka.common.errors.TimeoutException: Topic <topic> not present in metadata after 10000 ms.
      1.1 what happens if the partition is not available -> org.apache.kafka.common.errors.TimeoutException: Topic test not present in metadata after 10000 ms.
      2. what happens if authentication is unsuccessful -> org.apache.kafka.common.errors.SaslAuthenticationException: Authentication failed: Invalid username or password
                                                         The internal NetworkClient of the used library will start logging failures repeatedly:
                                                         o.a.k.c.NetworkClient  - [Producer clientId=producer-3] Connection to node -1 (localhost/127.0.0.1:9092) failed authentication due to: Authentication failed: Invalid username or password
                                                         todo: we should therefore definitely stop the producer and ourselves
      3. what happens if authorization is unsuccessful -> org.apache.kafka.common.errors.TopicAuthorizationException: Not authorized to access topics: [test]
                                                         todo: we should therefore definitely stop the producer and orselves. but the timeoutexception is a problem :/
      4. what happens if the port is closed -> timeout
      5. what happens if kafka is stopped -> timeout probably and a lot of network client problems:
                                            Error while fetching metadata with correlation id 81 : {test=LEADER_NOT_AVAILABLE} (org.apache.kafka.clients.NetworkClient)
      6. how to handle poisonpill to us? -> graceful shutdown
     */

    /**
     * Creates Akka configuration object {@link akka.actor.Props} for this {@code BasePublisherActor}.
     *
     * @param connectionId the connectionId this publisher belongs to.
     * @param targets the targets to publish to.
     * @param factory the factory to create Kafka connections with.
     * @param kafkaClientActor the ActorRef to the Kafka Client Actor
     * @param dryRun whether this publisher is only created for a test or not.
     * @return the Akka configuration Props object.
     */
    static Props props(final String connectionId, final List<Target> targets,
            final KafkaConnectionFactory factory, final ActorRef kafkaClientActor,
            final boolean dryRun) {
        return Props.create(KafkaPublisherActor.class, new Creator<KafkaPublisherActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public KafkaPublisherActor create() {
                return new KafkaPublisherActor(connectionId, targets, factory, kafkaClientActor, dryRun);
            }
        });
    }

    private static Sink<ProducerMessage.Results<String, String, ConnectionMetricsCollector>, CompletionStage<Done>> publishSuccessSink() {

        // basically, we don't know if the 'publish' will succeed or fail. We would need to write our own
        // GraphStage actor for Kafka and MQTT, since alpakka doesn't provide this useful information for us.
        return Sink.foreach(results -> results.passThrough().recordSuccess());
    }

    @Override
    protected void preEnhancement(final ReceiveBuilder receiveBuilder) {
        receiveBuilder.match(OutboundSignal.WithExternalMessage.class, this::isDryRun,
                outbound -> log.info("Message dropped in dry run mode: {}", outbound))
                .matchEquals(GracefulStop.INSTANCE, unused -> this.stopGracefully());
    }

    @Override
    protected void postEnhancement(final ReceiveBuilder receiveBuilder) {
        // noop
    }

    @Override
    protected KafkaPublishTarget toPublishTarget(final String address) {
        return KafkaPublishTarget.fromTargetAddress(address);
    }

    @Override
    protected KafkaPublishTarget toReplyTarget(final String replyToAddress) {
        return KafkaPublishTarget.fromTargetAddress(replyToAddress);
    }

    @Override
    protected void publishMessage(@Nullable final Target target, final KafkaPublishTarget publishTarget,
            final ExternalMessage message, final ConnectionMetricsCollector publishedCounter) {

        publishMessage(publishTarget, message, publishedCounter);
    }

    private void publishMessage(final KafkaPublishTarget publishTarget, final ExternalMessage message,
            final ConnectionMetricsCollector publishedCounter) {

        final ProducerMessage.Envelope<String, String, ConnectionMetricsCollector> kafkaMessage = mapExternalMessageToKafkaMessage(publishTarget, message, publishedCounter);
        sourceActor.tell(kafkaMessage, getSelf());
    }

    private boolean isDryRun() {
        return dryRun;
    }

    private static ProducerMessage.Envelope<String, String, ConnectionMetricsCollector> mapExternalMessageToKafkaMessage(
            final KafkaPublishTarget publishTarget,
            final ExternalMessage externalMessage,
            final ConnectionMetricsCollector metricsCollector) {

        final String payload = mapExternalMessagePayload(externalMessage);
        final Iterable<Header> headers = mapExternalMessageHeaders(externalMessage);

        final ProducerRecord<String, String> record =
                new ProducerRecord<>(publishTarget.getTopic(), publishTarget.getPartition(), publishTarget.getKey(), payload, headers);
        return ProducerMessage.single(record, metricsCollector);
    }

    private static Iterable<Header> mapExternalMessageHeaders(final ExternalMessage externalMessage) {
        return externalMessage.getHeaders()
                .entrySet()
                .stream()
                .map(header -> new RecordHeader(header.getKey(), header.getValue().getBytes(StandardCharsets.US_ASCII)))
                .collect(Collectors.toList());
    }

    private static String mapExternalMessagePayload(final ExternalMessage externalMessage) {
        if (externalMessage.isTextMessage()) {
            return externalMessage.getTextPayload()
                    .orElse("");
        } else if (externalMessage.isBytesMessage()) {
            return externalMessage.getBytePayload()
                    .map(ByteString::fromByteBuffer)
                    .map(ByteString::utf8String)
                    .orElse("");

        }
        return "";
    }

    private Done handleCompletionOrFailure(final Done done, final Throwable throwable) {
        if (null == throwable) {
            logWithConnectionId().info("Internal kafka publisher completed.");
            stop();
        } else if (shuttingDown) {
            logWithConnectionId().info("Received and ignoring exception while shutting down: {}", throwable.getMessage());
        } else if (throwable instanceof AuthorizationException || throwable instanceof AuthenticationException) {
            logWithConnectionId().info("Received exception from internal kafka publisher and can't recover from it: {}", throwable.getMessage());
            stop();
        } else if (throwable instanceof TimeoutException) {
            logWithConnectionId().info("Ran into a timeout when accessing Kafka with message: <{}>. This might have several reasons, " +
                    "e.g. the Kafka broker not being accessible, the topic or the partition not being existing, a wrong port etc. ",
                    throwable.getMessage());
            restartInternalKafkaProducer();
        } else {
            logWithConnectionId().error(throwable, "An unexpected error happened in the internal kafka publisher and we can't recover from it. Stopping publisher actor.");
            stop();
        }
        return done;
    }

    private void stop() {
        logWithConnectionId().info("Stopping publisher actor");
        // TODO: implement
    }

    private void startInternalKafkaProducer() {
        logWithConnectionId().info("Starting internal Kafka producer.");
        this.sourceActor = createInternalKafkaProducer(connectionFactory, this::handleCompletionOrFailure);
    }

    private void restartInternalKafkaProducer() {
        logWithConnectionId().info("Restarting internal Kafka producer");
        this.sourceActor = createInternalKafkaProducer(connectionFactory, this::handleCompletionOrFailure);
    }

    private ActorRef createInternalKafkaProducer(final KafkaConnectionFactory factory, final BiFunction<Done, Throwable, Done> completionOrFailureHandler) {
        final Pair<ActorRef, CompletionStage<Done>> materializedFlowedValues =
                Source.<ProducerMessage.Envelope<String, String, ConnectionMetricsCollector>>actorRef(100, OverflowStrategy.dropHead())
                        .via(factory.newFlow())
                        .toMat(KafkaPublisherActor.publishSuccessSink(), Keep.both())
                        .run(ActorMaterializer.create(getContext()));
        materializedFlowedValues.second().handleAsync(completionOrFailureHandler);
        return materializedFlowedValues.first();
    }

    private void stopInternalKafkaProducer() {
        logWithConnectionId().info("Stopping internal Kafka producer.");
        if (null != sourceActor) {
            sourceActor.tell(new Status.Success("stopped"), ActorRef.noSender());
        }
    }

    private void reportInitialConnectionState() {
        logWithConnectionId().info("Publisher ready");
        kafkaClientActor.tell(new Status.Success(Done.done()), getSelf());
    }

    @Override
    protected DiagnosticLoggingAdapter log() {
        return log;
    }

    private DiagnosticLoggingAdapter logWithConnectionId() {
        LogUtil.enhanceLogWithCustomField(log(), BaseClientData.MDC_CONNECTION_ID, connectionId);
        return log();
    }

    private void stopGracefully() {
        this.shuttingDown = true;
        stopInternalKafkaProducer();
        logWithConnectionId().debug("Stopping myself.");
        getContext().stop(getSelf());
    }

    static class GracefulStop {
        static final GracefulStop INSTANCE = new GracefulStop();
        private GracefulStop() {
            // intentionally empty
        }
    }

}
