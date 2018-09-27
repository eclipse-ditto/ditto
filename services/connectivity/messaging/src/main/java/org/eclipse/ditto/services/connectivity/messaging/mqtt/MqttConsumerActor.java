/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.ditto.services.connectivity.messaging.mqtt;

import static org.eclipse.ditto.services.connectivity.messaging.mqtt.MqttClientActor.ConsumerStreamMessage.STREAM_ACK;

import java.time.Instant;
import java.util.HashMap;
import java.util.Objects;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.connectivity.AddressMetric;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Enforcement;
import org.eclipse.ditto.services.connectivity.messaging.internal.RetrieveAddressMetric;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.services.models.connectivity.placeholder.EnforcementFactoryFactory;
import org.eclipse.ditto.services.models.connectivity.placeholder.EnforcementFilter;
import org.eclipse.ditto.services.models.connectivity.placeholder.EnforcementFilterFactory;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.stream.alpakka.mqtt.MqttMessage;

public class MqttConsumerActor extends AbstractActor {

    static final String ACTOR_NAME_PREFIX = "mqttConsumer-";
    private static final String MQTT_TOPIC_HEADER = "mqtt.topic";
    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final ActorRef messageMappingProcessor;
    private final AuthorizationContext sourceAuthorizationContext;
//    private final Enforcement enforcement;

    private long consumedMessages = 0L;
    private Instant lastMessageConsumedAt;
    private final AddressMetric addressMetric;
    private final ActorRef deadLetters;
    private final boolean dryRun;
    @Nullable private final EnforcementFilterFactory<String, String> enforcementFilterFactory;

    private MqttConsumerActor(final ActorRef messageMappingProcessor,
            final AuthorizationContext sourceAuthorizationContext,
            final Enforcement enforcement,
            final boolean dryRun) {
        this.messageMappingProcessor = messageMappingProcessor;
        this.sourceAuthorizationContext = sourceAuthorizationContext;
//        this.enforcement = enforcement;
        this.dryRun = dryRun;
        addressMetric =
                ConnectivityModelFactory.newAddressMetric(ConnectionStatus.OPEN, "Started at " + Instant.now(),
                        0, null);
        deadLetters = getContext().system().deadLetters();

        if (enforcement != null) {
            this.enforcementFilterFactory = EnforcementFactoryFactory.newThingIdEnforcementFactory(enforcement,
                    TopicPlaceholder.INSTANCE);
        } else {
            enforcementFilterFactory = null;
        }
    }

    static Props props(final ActorRef messageMappingProcessor,
            final AuthorizationContext sourceAuthorizationContext,
            final Enforcement enforcement,
            final boolean dryRun) {
        return Props.create(MqttConsumerActor.class, new Creator<MqttConsumerActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public MqttConsumerActor create() {
                return new MqttConsumerActor(messageMappingProcessor, sourceAuthorizationContext, enforcement,
                        dryRun);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(MqttMessage.class, this::isDryRun, message -> {
                    log.info("Dropping message in dryRun mode: {}", message);
                })
                .match(MqttMessage.class, message -> {

                    log.debug("Received MQTT message on topic {}: {}", message.topic(), message.payload().utf8String());

                    final HashMap<String, String> headers = new HashMap<>();

                    headers.put(MQTT_TOPIC_HEADER, message.topic());

                    final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(headers)
                            .withBytes(message.payload().toByteBuffer())
                            .withAuthorizationContext(sourceAuthorizationContext)
                            .withEnforcement(getEnforcementFilter(message.topic()))
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

    @Nullable
    private EnforcementFilter<String> getEnforcementFilter(final String topic) {
        if (enforcementFilterFactory != null) {
            return enforcementFilterFactory.getFilter(topic);
            // TODO error message
        } else {
            return null;
        }
    }

    private boolean isDryRun(final Object message) {
        return dryRun;
    }

    private void handleConsumerStreamMessage(final MqttClientActor.ConsumerStreamMessage message) {
        switch (message) {
            case STREAM_STARTED:
                replyStreamAck();
                break;
            case STREAM_ENDED:
                // sometimes Akka sends STREAM_ENDED out-of-band before the last stream element
                log.info("Underlying stream completed, waiting for shutdown by parent.");
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

    private static String getIdEnforcementErrorMessage(final String topic) {
        return String.format("The MQTT topic ''%s'' of the Ditto protocol message does not match any message filter " +
                "configured for the connection.", topic);
    }
}
