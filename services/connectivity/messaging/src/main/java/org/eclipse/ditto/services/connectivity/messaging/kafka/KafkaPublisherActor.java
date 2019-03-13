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
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.eclipse.ditto.model.connectivity.Target;
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

    private final ActorRef sourceActor;
    private final ActorRef kafkaClientActor;

    private final boolean dryRun;
    private boolean shuttingDown = false;


    private KafkaPublisherActor(final String connectionId, final List<Target> targets,
            final KafkaConnectionFactory factory,
            final ActorRef kafkaClientActor,
            final boolean dryRun) {
        super(connectionId, targets);
        this.kafkaClientActor = kafkaClientActor;
        this.dryRun = dryRun;

        final Pair<ActorRef, CompletionStage<Done>> materializedFlowedValues =
                Source.<ProducerMessage.Envelope<String, String, ConnectionMetricsCollector>>actorRef(100, OverflowStrategy.dropHead())
                        .via(factory.newFlow())
                        .toMat(KafkaPublisherActor.publishSuccessSink(), Keep.both())
                        .run(ActorMaterializer.create(getContext()));
        sourceActor = materializedFlowedValues.first();
        materializedFlowedValues.second().handleAsync(this::handleCompletionOrFailure);

        // TODO: think about doing this somewhere else
        // has to be done since we the publisher won't send a Done instance after finishing its connectivity.
        // in fact at the time of writing it doesn't report on connectivity in any way.
        this.reportInitialConnectionState();
    }

    /*
      TODO: test cases:
      1.0 what happens if the topic is missing -> org.apache.kafka.common.errors.TimeoutException: Topic <topic> not present in metadata after 10000 ms.
      1.1 what happens if the partition is not available -> org.apache.kafka.common.errors.TimeoutException: Topic test not present in metadata after 10000 ms.
      2. what happens if authentication is unsuccessful -> org.apache.kafka.common.errors.SaslAuthenticationException: Authentication failed: Invalid username or password
                                                         The internal NetworkClient of the used library will start logging failures repeatedly:
                                                         o.a.k.c.NetworkClient  - [Producer clientId=producer-3] Connection to node -1 (localhost/127.0.0.1:9092) failed authentication due to: Authentication failed: Invalid username or password
                                                         we should therefore definitely stop the producer and ourself
      3. what happens if authorization is unsuccessful
      4. what happens if the port is closed
      5. what happens if kafka is stopped
      6. how to handle poisonpill to us?
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
                .matchEquals(GracefulStop.INSTANCE, this::stopGracefully);
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

    private boolean isDryRun(final Object unused) {
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
            // todo: stop actor in this case
            log.info("Internal kafka publisher completed. Stopping publisher actor.");
        } else if (shuttingDown) {
            log.info("Received and ignoring exception while shutting down: {}", throwable.getMessage());
        } else {
            // TODO: handle the different exceptions thrown by the publisher
            /*
            possible errors as found in KafkaProducer.java:
            @throws AuthenticationException if authentication fails. See the exception for more details
             * @throws AuthorizationException fatal error indicating that the producer is not allowed to write
             * @throws IllegalStateException if a transactional.id has been configured and no transaction has been started, or
             *                               when send is invoked after producer has been closed.
             * @throws InterruptException If the thread is interrupted while blocked
             * @throws SerializationException If the key or value are not valid objects given the configured serializers
             * @throws TimeoutException If the time taken for fetching metadata or allocating memory for the record has surpassed <code>max.block.ms</code>.
             * @throws KafkaException If a Kafka related error occurs that does not belong to the public API exceptions.

             */
            log.error(throwable, "An error happened in the internal kafka publisher: {}");
        }
        return done;
    }

    private void reportInitialConnectionState() {
        log.info("Publisher ready");
        kafkaClientActor.tell(new Status.Success(Done.done()), getSelf());
    }

    @Override
    protected DiagnosticLoggingAdapter log() {
        return log;
    }

    private void stopGracefully(GracefulStop unused) {
        this.shuttingDown = true;
        log.info("stopping publisher actor, sending status to child actor to stop it");
        sourceActor.tell(new Status.Success("stopped"), ActorRef.noSender());
        log.debug("stopping myself.");
        getContext().stop(getSelf());
    }

    public static class GracefulStop {
        public static final GracefulStop INSTANCE = new GracefulStop();
        private GracefulStop() {
            // intentionally empty
        }
    }

}
