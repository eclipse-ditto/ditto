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

import static org.eclipse.ditto.services.connectivity.messaging.mqtt.MqttClientActor.ConsumerStreamMessage.STREAM_ACK;

import java.time.Instant;
import java.util.HashMap;
import java.util.Objects;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.connectivity.AddressMetric;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ExternalMessage;
import org.eclipse.ditto.services.connectivity.messaging.internal.RetrieveAddressMetric;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.stream.alpakka.mqtt.MqttMessage;

public class MqttConsumerActor extends AbstractActor {

    static final String ACTOR_NAME_PREFIX = "mqttConsumer-";
    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final ActorRef messageMappingProcessor;
    private final AuthorizationContext sourceAuthorizationContext;

    private long consumedMessages = 0L;
    private Instant lastMessageConsumedAt;
    private final AddressMetric addressMetric;
    private final ActorRef deadLetters;

    private MqttConsumerActor(final ActorRef messageMappingProcessor,
            final AuthorizationContext sourceAuthorizationContext) {
        this.messageMappingProcessor = messageMappingProcessor;
        this.sourceAuthorizationContext = sourceAuthorizationContext;
        addressMetric =
                ConnectivityModelFactory.newAddressMetric(ConnectionStatus.OPEN, "Started at " + Instant.now(),
                        0, null);
        deadLetters = getContext().system().deadLetters();
    }

    static Props props(final ActorRef messageMappingProcessor,
            final AuthorizationContext sourceAuthorizationContext) {
        return Props.create(MqttConsumerActor.class, new Creator<MqttConsumerActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public MqttConsumerActor create() {
                return new MqttConsumerActor(messageMappingProcessor, sourceAuthorizationContext);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(MqttMessage.class, message -> {

                    log.debug("Received MQTT message on topic {}: {}", message.topic(), message.payload().utf8String());

                    final HashMap<String, String> headers = new HashMap<>();

                    headers.put("mqtt.topic", message.topic());

                    final ExternalMessage externalMessage = ConnectivityModelFactory.newExternalMessageBuilder(headers)
                            .withBytes(message.payload().toByteBuffer())
                            .withAuthorizationContext(sourceAuthorizationContext)
                            .build();

                    lastMessageConsumedAt = Instant.now();
                    consumedMessages++;

                    messageMappingProcessor.tell(externalMessage, getSelf());
                    replyStreamAck();
                })
                .match(RetrieveAddressMetric.class, ram -> {
                    final AddressMetric addressMetric = ConnectivityModelFactory.newAddressMetric(
                            this.addressMetric != null ? this.addressMetric.getStatus() : ConnectionStatus.UNKNOWN,
                            this.addressMetric != null ? this.addressMetric.getStatusDetails().orElse(null) : null,
                            consumedMessages, lastMessageConsumedAt);
                    log.debug("addressMetric: {}", addressMetric);
                    getSender().tell(addressMetric, getSelf());
                })
                .match(MqttClientActor.ConsumerStreamMessage.class, this::handleConsumerStreamMessage)
                .matchAny(unhandled -> {
                    log.info("Unhandled message: {}", unhandled);
                    unhandled(unhandled);
                })
                .build();
    }

    private void handleConsumerStreamMessage(final MqttClientActor.ConsumerStreamMessage message) {
        switch (message) {
            case STREAM_STARTED:
                replyStreamAck();
                break;
            case STREAM_ENDED:
                log.debug("Underlying stream completed, shutdown consumer actor.");
                getSelf().tell(PoisonPill.getInstance(), getSelf());
                break;
            case STREAM_ACK:
                log.error("Protocol violation: STREAM_ACK");
                break;
        }
    }

    private void replyStreamAck() {
        final ActorRef sender = getSender();
        // check sender against deadLetters because stream actor terminates itself before waiting for the final ACK
        if (!Objects.equals(sender, deadLetters)) {
            sender.tell(STREAM_ACK, getSelf());
        }
    }
}
