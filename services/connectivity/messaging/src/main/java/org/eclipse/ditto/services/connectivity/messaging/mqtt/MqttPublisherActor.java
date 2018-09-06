/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.connectivity.messaging.mqtt;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.connectivity.AddressMetric;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ExternalMessage;
import org.eclipse.ditto.services.connectivity.mapping.MessageMappers;
import org.eclipse.ditto.services.connectivity.messaging.BasePublisherActor;
import org.eclipse.ditto.services.connectivity.messaging.OutboundSignal;
import org.eclipse.ditto.services.connectivity.messaging.internal.RetrieveAddressMetric;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.Done;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.Pair;
import akka.stream.ActorMaterializer;
import akka.stream.OverflowStrategy;
import akka.stream.alpakka.mqtt.MqttMessage;
import akka.stream.alpakka.mqtt.MqttQoS;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.util.ByteString;

public class MqttPublisherActor extends BasePublisherActor<MqttPublishTarget> {

    static final String ACTOR_NAME = "mqttPublisher";
    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final ActorRef sourceActor;
    private final ActorRef mqttClientActor;

    private long publishedMessages = 0L;
    private Instant lastMessagePublishedAt;
    private final AddressMetric addressMetric;
    private final boolean dryRun;

    private MqttPublisherActor(final MqttConnectionFactory factory, final ActorRef mqttClientActor,
            final boolean dryRun) {
        this.mqttClientActor = mqttClientActor;
        this.dryRun = dryRun;

        final Sink<MqttMessage, CompletionStage<Done>> mqttSink = factory.newSink();

        final Pair<ActorRef, CompletionStage<Done>> materializedValues =
                akka.stream.javadsl.Source.<MqttMessage>actorRef(100, OverflowStrategy.dropHead())
                        .map(this::countPublishedMqttMessage)
                        .toMat(mqttSink, Keep.both())
                        .run(ActorMaterializer.create(getContext()));

        materializedValues.second().handle(this::reportReadiness);

        sourceActor = materializedValues.first();

        addressMetric =
                ConnectivityModelFactory.newAddressMetric(ConnectionStatus.OPEN, "Started at " + Instant.now(),
                        0, null);
    }

    static Props props(final MqttConnectionFactory factory, final ActorRef mqttClientActor, final boolean dryRun) {
        return Props.create(MqttPublisherActor.class, new Creator<MqttPublisherActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public MqttPublisherActor create() {
                return new MqttPublisherActor(factory, mqttClientActor, dryRun);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(OutboundSignal.WithExternalMessage.class, this::isDryRun, outbound -> {
                    log.info("Message dropped in dryrun mode: {}", outbound);
                })
                .match(OutboundSignal.WithExternalMessage.class, this::isResponseOrError, outbound -> {
                    final ExternalMessage response = outbound.getExternalMessage();
                    final String correlationId =
                            response.getHeaders().get(DittoHeaderDefinition.CORRELATION_ID.getKey());
                    LogUtil.enhanceLogWithCorrelationId(log, correlationId);
                    log.debug("Received mapped response {} ", response);

                    final String replyTo = response.getHeaders().get(ExternalMessage.REPLY_TO_HEADER);
                    if (replyTo != null) {
                        final MqttPublishTarget replyTarget = toPublishTarget(replyTo);
                        final MqttQoS defaultQoS = MqttQoS.atMostOnce();
                        publishMessage(replyTarget, defaultQoS, response);
                    } else {
                        log.info("Response dropped, missing replyTo address: {}", response);
                    }
                })
                .match(OutboundSignal.WithExternalMessage.class, outbound -> {
                    final ExternalMessage message = outbound.getExternalMessage();
                    final String correlationId =
                            message.getHeaders().get(DittoHeaderDefinition.CORRELATION_ID.getKey());
                    LogUtil.enhanceLogWithCorrelationId(log, correlationId);
                    log.debug("Received mapped message {} ", message);

                    log.debug("Publishing message to targets <{}>: {} ", outbound.getTargets(), message);
                    outbound.getTargets().forEach(target -> {
                        final MqttPublishTarget mqttTarget = toPublishTarget(target.getAddress());
                        final int qos = ((org.eclipse.ditto.model.connectivity.MqttTarget) target).getQos();
                        final MqttQoS targetQoS = MqttValidator.getQoS(qos);
                        publishMessage(mqttTarget, targetQoS, message);
                    });
                })
                .match(RetrieveAddressMetric.class, ram -> {
                    getSender().tell(ConnectivityModelFactory.newAddressMetric(
                            addressMetric != null ? addressMetric.getStatus() : ConnectionStatus.UNKNOWN,
                            addressMetric != null ? addressMetric.getStatusDetails().orElse(null) : null,
                            publishedMessages, lastMessagePublishedAt), getSelf());
                })
                .matchAny(message -> {
                    unhandled(message);
                    log.info("Unknown message: {}", message.getClass().getName());
                })
                .build();
    }

    @Override
    protected MqttPublishTarget toPublishTarget(final String address) {
        return MqttPublishTarget.of(address);
    }

    private void publishMessage(final MqttPublishTarget replyTarget,
            final MqttQoS qos,
            final ExternalMessage externalMessage) {

        final MqttMessage mqttMessage = mapExternalMessageToMqttMessage(replyTarget, qos, externalMessage);
        sourceActor.tell(mqttMessage, getSelf());

        publishedMessages++;
        lastMessagePublishedAt = Instant.now();
    }

    private boolean isDryRun(final Object message) {
        return dryRun;
    }

    private MqttMessage mapExternalMessageToMqttMessage(
            final MqttPublishTarget mqttTarget,
            final MqttQoS qos,
            final ExternalMessage externalMessage) {
        final ByteString payload;
        if (externalMessage.isTextMessage()) {
            final Charset charset = externalMessage.findContentType()
                    .map(MessageMappers::determineCharset)
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
}
