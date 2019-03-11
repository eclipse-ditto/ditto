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

import java.util.List;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TimeoutException;
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
import akka.stream.ActorMaterializer;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Source;
import akka.util.ByteString;

/**
 * Responsible for publishing {@link org.eclipse.ditto.services.models.connectivity.ExternalMessage}s into an Kafka broker.
 */
public final class KafkaPublisherActor extends BasePublisherActor<KafkaPublishTarget> {
    static final String ACTOR_NAME = "kafkaPublisher";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef sourceActor;
    private final ActorRef kafkaClientActor;

    private final boolean dryRun;

    private KafkaPublisherActor(final String connectionId, final List<Target> targets,
            final KafkaConnectionFactory factory,
            final ActorRef kafkaClientActor,
            final boolean dryRun) {
        super(connectionId, targets);
        this.kafkaClientActor = kafkaClientActor;
        this.dryRun = dryRun;

        // TODO: verify the actor is stopped via Status.Success or Status.Failure. When using a PoisonPill, the Source
        //  will keep consuming even though the actor is killed.
        final Pair<ActorRef, CompletionStage<Done>> materializedValues =
                Source.<ProducerRecord<String, String>>actorRef(100, OverflowStrategy.dropHead())
                        .toMat(factory.newSink(), Keep.both())
                        .run(ActorMaterializer.create(getContext()));

        materializedValues.second().handleAsync(this::reportReadiness);

        sourceActor = materializedValues.first();

        // TODO: think about doing this somewhere else
        // has to be done since we the publisher won't send a Done instance after finishing its connectivity.
        // in fact at the time of writing it doesn't report on connectivity in any way.
        this.reportInitialConnectionState();
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

    @Override
    protected void preEnhancement(final ReceiveBuilder receiveBuilder) {
        receiveBuilder.match(OutboundSignal.WithExternalMessage.class, this::isDryRun,
                outbound -> log.info("Message dropped in dry run mode: {}", outbound));
    }

    @Override
    protected void postEnhancement(final ReceiveBuilder receiveBuilder) {
        // noop
    }

    @Override
    protected KafkaPublishTarget toPublishTarget(final String address) {
        return KafkaPublishTarget.of(address);
    }

    @Override
    protected KafkaPublishTarget toReplyTarget(final String replyToAddress) {
        return KafkaPublishTarget.of(replyToAddress);
    }

    @Override
    protected void publishMessage(@Nullable final Target target, final KafkaPublishTarget publishTarget,
            final ExternalMessage message, final ConnectionMetricsCollector publishedCounter) {

        publishMessage(publishTarget, message, publishedCounter);
    }

    private void publishMessage(final KafkaPublishTarget publishTarget, final ExternalMessage message,
            final ConnectionMetricsCollector publishedCounter) {

        final ProducerRecord<String, String> kafkaMessage = mapExternalMessageToKafkaMessage(publishTarget, message);
        // TODO: handle org.apache.kafka.common.errors.TimeoutException which is a reply if the producer can't send the message in time
        sourceActor.tell(kafkaMessage, getSelf());
        // TODO: we don't know yet if we succeeded here...
        publishedCounter.recordSuccess();
    }

    private boolean isDryRun(final Object message) {
        return dryRun;
    }

    private static ProducerRecord<String, String> mapExternalMessageToKafkaMessage(final KafkaPublishTarget publishTarget,
            final ExternalMessage externalMessage) {

        final String payload;
        if (externalMessage.isTextMessage()) {
            payload = externalMessage.getTextPayload()
                    .orElse("");
        } else if (externalMessage.isBytesMessage()) {
            payload = externalMessage.getBytePayload()
                    .map(ByteString::fromByteBuffer)
                    .map(ByteString::utf8String)
                    .orElse("");
        } else {
            payload = "";
        }
        return new ProducerRecord<>(publishTarget.getTopic(), payload);
    }

    private void reportInitialConnectionState() {
        this.reportReadiness(Done.done(), null);
    }

    /*
     * Called inside future - must be thread-safe.
     */
    @Nullable
    private Done reportReadiness(@Nullable final Done done, @Nullable final Throwable exception) {
        if (exception == null) {
            log.info("Publisher ready");
            kafkaClientActor.tell(new Status.Success(done), getSelf());
        } else {
            log.info("Publisher failed");
            kafkaClientActor.tell(new Status.Failure(exception), getSelf());
        }
        return done;
    }

    @Override
    protected DiagnosticLoggingAdapter log() {
        return log;
    }

}
