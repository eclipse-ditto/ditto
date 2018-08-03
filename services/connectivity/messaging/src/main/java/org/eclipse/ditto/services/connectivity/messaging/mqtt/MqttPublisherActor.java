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

import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.connectivity.ExternalMessage;
import org.eclipse.ditto.services.connectivity.mapping.MessageMappers;
import org.eclipse.ditto.services.connectivity.messaging.BasePublisherActor;
import org.eclipse.ditto.services.connectivity.messaging.OutboundSignal;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.stream.alpakka.mqtt.MqttMessage;
import akka.stream.alpakka.mqtt.MqttQoS;
import akka.util.ByteString;

public class MqttPublisherActor extends BasePublisherActor<MqttTarget> {

    static final String ACTOR_NAME = "mqttPublisher";
    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final ActorRef mqttPublisher;


    private MqttPublisherActor(final ActorRef mqttPublisher) {
        this.mqttPublisher = mqttPublisher;
    }

    static Props props(final ActorRef mqttPublisher) {
        return Props.create(MqttPublisherActor.class, new Creator<MqttPublisherActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public MqttPublisherActor create() {
                return new MqttPublisherActor(mqttPublisher);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(OutboundSignal.WithExternalMessage.class, this::isResponseOrError, outbound -> {
                    final ExternalMessage response = outbound.getExternalMessage();
                    final String correlationId =
                            response.getHeaders().get(DittoHeaderDefinition.CORRELATION_ID.getKey());
                    LogUtil.enhanceLogWithCorrelationId(log, correlationId);
                    log.debug("Received mapped response {} ", response);

                    final String replyTo = response.getHeaders().get(ExternalMessage.REPLY_TO_HEADER);
                    if (replyTo != null) {
                        final MqttTarget replyTarget = toPublishTarget(replyTo);
                        publishMessage(replyTarget, response);
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
                    outbound.getTargets().stream()
                            .map(t -> toPublishTarget(t.getAddress()))
                            .forEach(destination -> publishMessage(destination, message));
                })
                .matchAny(message -> {
                    unhandled(message);
                    log.info("Unknown message: {}", message.getClass().getName());
                })
                .build();
    }

    private void publishMessage(final MqttTarget replyTarget,
            final ExternalMessage externalMessage) {
        final MqttMessage mqttMessage = mapExternalMessageToMqttMessage(replyTarget, externalMessage);
        mqttPublisher.tell(mqttMessage, getSelf());
    }

    private MqttMessage mapExternalMessageToMqttMessage(
            final MqttTarget replyTarget,
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
        return MqttMessage.create(replyTarget.getTopic(), payload, MqttQoS.atMostOnce());
    }

    @Override
    protected MqttTarget toPublishTarget(final String address) {
        return MqttTarget.of(address);
    }
}
