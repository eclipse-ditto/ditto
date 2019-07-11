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
package org.eclipse.ditto.services.connectivity.messaging.kafka;

import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.BasePublisherActor;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.connectivity.util.ConnectionLogUtil;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.Done;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.event.DiagnosticLoggingAdapter;
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
final class KafkaPublisherActor extends BasePublisherActor<KafkaPublishTarget> {

    static final String ACTOR_NAME = "kafkaPublisher";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef kafkaClientActor;
    private final KafkaConnectionFactory connectionFactory;
    private final boolean dryRun;

    private boolean shuttingDown = false;
    private ActorRef sourceActor;

    @SuppressWarnings("unused")
    private KafkaPublisherActor(final String connectionId,
            final List<Target> targets,
            final KafkaConnectionFactory factory,
            final ActorRef kafkaClientActor,
            final boolean dryRun) {

        super(connectionId, targets);
        this.kafkaClientActor = kafkaClientActor;
        this.dryRun = dryRun;
        connectionFactory = factory;

        startInternalKafkaProducer();
        reportInitialConnectionState();
    }

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
    static Props props(final String connectionId,
            final List<Target> targets,
            final KafkaConnectionFactory factory,
            final ActorRef kafkaClientActor,
            final boolean dryRun) {

        return Props.create(KafkaPublisherActor.class, connectionId, targets, factory, kafkaClientActor, dryRun);
    }

    private static Sink<ProducerMessage.Results<String, String, PassThrough>, CompletionStage<Done>> publishSuccessSink() {

        // basically, we don't know if the 'publish' will succeed or fail. We would need to write our own
        // GraphStage actor for Kafka and MQTT, since alpakka doesn't provide this useful information for us.
        return Sink.foreach(results -> {
            final ConnectionMonitor connectionMonitor = results.passThrough().connectionMonitor;
            connectionMonitor.success(results.passThrough().externalMessage);
        });
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
    protected void publishMessage(@Nullable final Target target,
            final KafkaPublishTarget publishTarget,
            final ExternalMessage message,
            final ConnectionMonitor publishedMonitor) {

        publishMessage(publishTarget, message, new PassThrough(publishedMonitor, message));
    }

    private void publishMessage(final KafkaPublishTarget publishTarget, final ExternalMessage message,
            final PassThrough passThrough) {

        final ProducerMessage.Envelope<String, String, PassThrough> kafkaMessage =
                mapExternalMessageToKafkaMessage(publishTarget, message, passThrough);
        sourceActor.tell(kafkaMessage, getSelf());
    }

    private boolean isDryRun() {
        return dryRun;
    }

    private static ProducerMessage.Envelope<String, String, PassThrough> mapExternalMessageToKafkaMessage(
            final KafkaPublishTarget publishTarget, final ExternalMessage externalMessage,
            final PassThrough passThrough) {

        final String payload = mapExternalMessagePayload(externalMessage);
        final Iterable<Header> headers = mapExternalMessageHeaders(externalMessage);

        final ProducerRecord<String, String> record =
                new ProducerRecord<>(publishTarget.getTopic(),
                        publishTarget.getPartition().orElse(null),
                        publishTarget.getKey().orElse(null),
                        payload, headers);
        return ProducerMessage.single(record, passThrough);
    }

    private static Iterable<Header> mapExternalMessageHeaders(final ExternalMessage externalMessage) {
        return externalMessage.getHeaders()
                .entrySet()
                .stream()
                .map(header -> new RecordHeader(header.getKey(), header.getValue().getBytes(StandardCharsets.UTF_8)))
                .collect(Collectors.toList());
    }

    private static String mapExternalMessagePayload(final ExternalMessage externalMessage) {
        if (externalMessage.isTextMessage()) {
            return externalMessage.getTextPayload().orElse("");
        } else if (externalMessage.isBytesMessage()) {
            return externalMessage.getBytePayload()
                    .map(ByteString::fromByteBuffer)
                    .map(ByteString::utf8String)
                    .orElse("");

        }
        return "";
    }

    private Done handleCompletionOrFailure(final Done done, @Nullable final Throwable throwable) {
        // when getting here, the Kafka producer will have finished its work either because it got an exception or because
        // the stream (it is built upon) is finished. Since the stream is never expected to finish, we will try to
        // restart the producer most of the times. Only when it was intentionally stopped (#shuttingDown), we will not
        // restart it.
        if (null == throwable) {
            logWithConnectionId().info("Internal kafka publisher completed.");
            restartInternalKafkaProducer();

        } else if (shuttingDown) {
            logWithConnectionId().info("Received exception while shutting down and thus ignoring it: {}",
                    throwable.getMessage());

        } else if (throwable instanceof AuthorizationException || throwable instanceof AuthenticationException) {
            final String message =
                    MessageFormat.format("Ran into authentication or authorization problems against Kafka broker: {0}",
                            throwable.getMessage());

            // use response published monitor since we don't know if this comes from publishing to a target or from a response.
            // we should fix this with #418
            responsePublishedMonitor.exception(message);
            logWithConnectionId().info(message);
            restartInternalKafkaProducer();

        } else if (throwable instanceof TimeoutException) {
            final String message = MessageFormat.format("Ran into a timeout when accessing Kafka with message: <{0}>. "
                            + "This might have several reasons, e.g. the Kafka broker not being accessible, the topic or the "
                            + "partition not being existing, a wrong port etc. ",
                    throwable.getMessage());

            // use response published monitor since we don't know if this comes from publishing to a target or from a response.
            // we should fix this with #418
            responsePublishedMonitor.exception(message);
            logWithConnectionId().info(message);
            restartInternalKafkaProducer();

        } else {
            connectionLogger.exception("An unexpected error happened: {0}", throwable.getMessage());
            logWithConnectionId().error(throwable,
                    "An unexpected error happened in the internal Kafka publisher and we can't recover from it.");
            restartInternalKafkaProducer();

        }

        return done;
    }

    private void startInternalKafkaProducer() {
        logWithConnectionId().info("Starting internal Kafka producer.");
        sourceActor = createInternalKafkaProducer(connectionFactory, this::handleCompletionOrFailure);
    }

    private void restartInternalKafkaProducer() {
        logWithConnectionId().info("Restarting internal Kafka producer");
        sourceActor = createInternalKafkaProducer(connectionFactory, this::handleCompletionOrFailure);
    }

    private ActorRef createInternalKafkaProducer(final KafkaConnectionFactory factory,
            final BiFunction<Done, Throwable, Done> completionOrFailureHandler) {

        final Pair<ActorRef, CompletionStage<Done>> materializedFlowedValues =
                Source.<ProducerMessage.Envelope<String, String, PassThrough>>actorRef(100,
                        OverflowStrategy.dropHead())
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
        return logWithConnectionId();
    }

    private DiagnosticLoggingAdapter logWithConnectionId() {
        ConnectionLogUtil.enhanceLogWithConnectionId(log, connectionId);
        return log;
    }

    private void stopGracefully() {
        shuttingDown = true;
        stopInternalKafkaProducer();
        logWithConnectionId().debug("Stopping myself.");
        getContext().stop(getSelf());
    }

    /**
     * Message that allows gracefully stopping the publisher actor.
     */
    static final class GracefulStop {

        static final GracefulStop INSTANCE = new GracefulStop();

        private GracefulStop() {
            // intentionally empty
        }

    }

    /**
     * Class that is used as a <em>pass through</em> object when sending messages via alpakka to Kafka.
     */
    @Immutable
    private static class PassThrough {

        private final ConnectionMonitor connectionMonitor;
        private final ExternalMessage externalMessage;

        private PassThrough(final ConnectionMonitor connectionMonitor, final ExternalMessage message) {
            this.connectionMonitor = connectionMonitor;
            this.externalMessage = message;
        }

    }

}
