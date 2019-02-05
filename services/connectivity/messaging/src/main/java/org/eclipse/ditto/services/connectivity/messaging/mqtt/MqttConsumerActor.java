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

import static org.eclipse.ditto.services.connectivity.messaging.mqtt.MqttClientActor.ConsumerStreamMessage.STREAM_ACK;

import java.util.HashMap;
import java.util.Objects;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.connectivity.Enforcement;
import org.eclipse.ditto.services.connectivity.messaging.BaseConsumerActor;
import org.eclipse.ditto.services.connectivity.messaging.internal.RetrieveAddressStatus;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.services.models.connectivity.placeholder.EnforcementFactoryFactory;
import org.eclipse.ditto.services.models.connectivity.placeholder.EnforcementFilter;
import org.eclipse.ditto.services.models.connectivity.placeholder.EnforcementFilterFactory;
import org.eclipse.ditto.services.models.connectivity.placeholder.PlaceholderFactory;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.stream.alpakka.mqtt.MqttMessage;

/**
 * Actor which receives message from a MQTT broker and forwards them to a {@code MessageMappingProcessorActor}.
 */
public final class MqttConsumerActor extends BaseConsumerActor {

    static final String ACTOR_NAME_PREFIX = "mqttConsumer-";
    private static final String MQTT_TOPIC_HEADER = "mqtt.topic";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final ActorRef deadLetters;
    private final boolean dryRun;
    @Nullable private final EnforcementFilterFactory<String, String> topicEnforcementFilterFactory;

    private MqttConsumerActor(final String connectionId, final ActorRef messageMappingProcessor,
            final AuthorizationContext sourceAuthorizationContext, @Nullable final Enforcement enforcement,
            final boolean dryRun, final String sourceAddress) {
        super(connectionId, sourceAddress, messageMappingProcessor, sourceAuthorizationContext, null);
        this.dryRun = dryRun;
        deadLetters = getContext().system().deadLetters();

        if (enforcement != null) {
            this.topicEnforcementFilterFactory = EnforcementFactoryFactory.newEnforcementFilterFactory(enforcement,
                    PlaceholderFactory.newSourceAddressPlaceholder());
        } else {
            topicEnforcementFilterFactory = null;
        }
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connectionId ID of the connection this consumer is belongs to
     * @param messageMappingProcessor the ActorRef to the {@code MessageMappingProcessor}
     * @param sourceAuthorizationContext the {@link AuthorizationContext} of the source
     * @param enforcement the optional Enforcement to apply
     * @param dryRun whether this is a dry-run/connection test or not
     * @param topic the topic for which this consumer receives messages
     * @return the Akka configuration Props object.
     */
    static Props props(final String connectionId, final ActorRef messageMappingProcessor,
            final AuthorizationContext sourceAuthorizationContext,
            @Nullable final Enforcement enforcement,
            final boolean dryRun, final String topic) {
        return Props.create(MqttConsumerActor.class, new Creator<MqttConsumerActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public MqttConsumerActor create() {
                return new MqttConsumerActor(connectionId, messageMappingProcessor, sourceAuthorizationContext,
                        enforcement,
                        dryRun, topic);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(MqttMessage.class, this::isDryRun,
                        message -> log.info("Dropping message in dryRun mode: {}", message))
                .match(MqttMessage.class, this::handleMqttMessage)
                .match(RetrieveAddressStatus.class, ram -> getSender().tell(getCurrentSourceStatus(), getSelf()))
                .match(MqttClientActor.ConsumerStreamMessage.class, this::handleConsumerStreamMessage)
                .matchAny(unhandled -> {
                    log.info("Unhandled message: {}", unhandled);
                    unhandled(unhandled);
                })
                .build();
    }

    private void handleMqttMessage(final MqttMessage message) {
        try {
            log.debug("Received MQTT message on topic {}: {}", message.topic(), message.payload().utf8String());
            final HashMap<String, String> headers = new HashMap<>();
            headers.put(MQTT_TOPIC_HEADER, message.topic());
            final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(headers)
                    .withBytes(message.payload().toByteBuffer())
                    .withAuthorizationContext(authorizationContext)
                    .withEnforcement(getEnforcementFilter(message.topic()))
                    .withSourceAddress(sourceAddress)
                    .build();
            inboundCounter.recordSuccess();
            messageMappingProcessor.tell(externalMessage, getSelf());
            replyStreamAck();
        } catch (final Exception e) {
            inboundCounter.recordFailure();
            log.info("Failed to handle MQTT message: {}", e.getMessage());
        }
    }

    @Nullable
    private EnforcementFilter<String> getEnforcementFilter(final String topic) {
        if (topicEnforcementFilterFactory != null) {
            return topicEnforcementFilterFactory.getFilter(topic);
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
}
