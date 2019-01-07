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
package org.eclipse.ditto.services.connectivity.messaging.mqtt;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.common.CharsetDeterminer;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.BasePublisherActor;
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
import akka.stream.alpakka.mqtt.MqttMessage;
import akka.stream.alpakka.mqtt.MqttQoS;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Source;
import akka.util.ByteString;

/**
 * Responsible for publishing {@link ExternalMessage}s into an MQTT broker.
 */
public final class MqttPublisherActor extends BasePublisherActor<MqttPublishTarget> {

    static final String ACTOR_NAME = "mqttPublisher";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef sourceActor;
    private final ActorRef mqttClientActor;

    private final boolean dryRun;

    private MqttPublisherActor(final ActorRef mqttClientActor, final boolean dryRun,
            final MqttConnectionFactory factory) {

        this.mqttClientActor = mqttClientActor;
        this.dryRun = dryRun;

        final Pair<ActorRef, CompletionStage<Done>> materializedValues =
                Source.<MqttMessage>actorRef(100, OverflowStrategy.dropHead())
                        .map(this::countPublishedMqttMessage)
                        .toMat(factory.newSink(), Keep.both())
                        .run(ActorMaterializer.create(getContext()));

        materializedValues.second().handle(this::reportReadiness);

        sourceActor = materializedValues.first();
    }

    static Props props(final MqttConnectionFactory factory, final ActorRef mqttClientActor, final boolean dryRun) {
        return Props.create(MqttPublisherActor.class, new Creator<MqttPublisherActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public MqttPublisherActor create() {
                return new MqttPublisherActor(mqttClientActor, dryRun, factory);
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
    protected MqttPublishTarget toPublishTarget(final String address) {
        return MqttPublishTarget.of(address);
    }

    @Override
    protected MqttPublishTarget toReplyTarget(final String replyToAddress) {
        return MqttPublishTarget.of(replyToAddress);
    }

    @Override
    protected void publishMessage(@Nullable final Target target, final MqttPublishTarget publishTarget,
            final ExternalMessage message) {

        final MqttQoS targetQoS = null == target
                ? MqttQoS.atMostOnce()
                : MqttValidator.getQoS(((org.eclipse.ditto.model.connectivity.MqttTarget) target).getQos());

        publishMessage(publishTarget, targetQoS, message);
    }

    private void publishMessage(final MqttPublishTarget replyTarget, final MqttQoS qos, final ExternalMessage message) {

        final MqttMessage mqttMessage = mapExternalMessageToMqttMessage(replyTarget, qos, message);
        sourceActor.tell(mqttMessage, getSelf());
    }

    private boolean isDryRun(final Object message) {
        return dryRun;
    }

    private static MqttMessage mapExternalMessageToMqttMessage(final MqttPublishTarget mqttTarget, final MqttQoS qos,
            final ExternalMessage externalMessage) {

        final ByteString payload;
        if (externalMessage.isTextMessage()) {
            final Charset charset = externalMessage.findContentType()
                    .map(MqttPublisherActor::determineCharset)
                    .orElse(StandardCharsets.UTF_8);
            payload = externalMessage
                    .getTextPayload()
                    .map(text -> ByteString.fromString(text, charset))
                    .orElse(ByteString.empty());
        } else if (externalMessage.isBytesMessage()) {
            payload = externalMessage.getBytePayload()
                    .map(ByteString::fromByteBuffer)
                    .orElse(ByteString.empty());
        } else {
            payload = ByteString.empty();
        }
        return MqttMessage.create(mqttTarget.getTopic(), payload, qos);
    }

    private static Charset determineCharset(final CharSequence contentType) {
        return CharsetDeterminer.getInstance().apply(contentType);
    }

    /*
     * Called inside stream - must be thread-safe.
     */
    private <T> T countPublishedMqttMessage(final T message) {
        mqttClientActor.tell(new MqttClientActor.CountPublishedMqttMessage(), getSelf());
        return message;
    }

    /*
     * Called inside future - must be thread-safe.
     */
    @Nullable
    private Done reportReadiness(@Nullable final Done done, @Nullable final Throwable exception) {
        if (exception == null) {
            log.info("Publisher ready");
            mqttClientActor.tell(new Status.Success(done), getSelf());
        } else {
            log.info("Publisher failed");
            mqttClientActor.tell(new Status.Failure(exception), getSelf());
        }
        return done;
    }

    @Override
    protected DiagnosticLoggingAdapter log() {
        return log;
    }

}
