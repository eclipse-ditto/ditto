/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.messaging.mqtt.hivemq;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.common.ByteBufferUtils;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.EnforcementFactoryFactory;
import org.eclipse.ditto.model.connectivity.EnforcementFilter;
import org.eclipse.ditto.model.connectivity.EnforcementFilterFactory;
import org.eclipse.ditto.model.connectivity.HeaderMapping;
import org.eclipse.ditto.model.connectivity.PayloadMapping;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.services.connectivity.messaging.BaseConsumerActor;
import org.eclipse.ditto.services.connectivity.messaging.internal.RetrieveAddressStatus;
import org.eclipse.ditto.services.connectivity.util.ConnectionLogUtil;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;

/**
 * Actor which receives message from an MQTT broker and forwards them to a {@code MessageMappingProcessorActor}.
 */
public final class HiveMqtt3ConsumerActor extends BaseConsumerActor {

    private static final String MQTT_TOPIC_HEADER = "mqtt.topic";
    static final String NAME = "HiveMqtt3ConsumerActor";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final boolean dryRun;
    @Nullable private final EnforcementFilterFactory<String, CharSequence> topicEnforcementFilterFactory;
    private final PayloadMapping payloadMapping;

    @SuppressWarnings("unused")
    private HiveMqtt3ConsumerActor(final ConnectionId connectionId, final ActorRef messageMappingProcessor,
            final Source source, final boolean dryRun) {
        super(connectionId, String.join(";", source.getAddresses()), messageMappingProcessor, source);
        this.dryRun = dryRun;
        this.payloadMapping = source.getPayloadMapping();
        topicEnforcementFilterFactory = source.getEnforcement()
                .map(enforcement -> EnforcementFactoryFactory
                        .newEnforcementFilterFactory(enforcement,
                                ConnectivityModelFactory.newSourceAddressPlaceholder()))
                .orElse(null);
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connectionId ID of the connection this consumer is belongs to
     * @param messageMappingProcessor the ActorRef to the {@code MessageMappingProcessor}
     * @param source the source from which this consumer is built
     * @param dryRun whether this is a dry-run/connection test or not
     * @return the Akka configuration Props object.
     */
    static Props props(final ConnectionId connectionId, final ActorRef messageMappingProcessor,
            final Source source, final boolean dryRun) {
        return Props.create(HiveMqtt3ConsumerActor.class, connectionId, messageMappingProcessor,
                source, dryRun);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Mqtt3Publish.class, this::isDryRun,
                        message -> log.info("Dropping message in dryRun mode: {}", message))
                .match(Mqtt3Publish.class, this::handleMqttMessage)
                .match(RetrieveAddressStatus.class, ram -> getSender().tell(getCurrentSourceStatus(), getSelf()))
                .matchAny(unhandled -> {
                    log.info("Unhandled message: {}", unhandled);
                    unhandled(unhandled);
                })
                .build();
    }

    private void handleMqttMessage(final Mqtt3Publish message) {
        log.info("Received message: {}", message);
        final Optional<ExternalMessage> externalMessageOptional = hiveToExternalMessage(message, connectionId);
        externalMessageOptional.ifPresent(this::forwardToMappingActor);
    }

    private Optional<ExternalMessage> hiveToExternalMessage(final Mqtt3Publish message,
            final ConnectionId connectionId) {
        final HashMap<String, String> headers = new HashMap<>();
        try {
            ConnectionLogUtil.enhanceLogWithConnectionId(log, connectionId);
            final ByteBuffer payload = message.getPayload()
                    .map(ByteBuffer::asReadOnlyBuffer)
                    .orElse(ByteBufferUtils.empty());
            final String textPayload = ByteBufferUtils.toUtf8String(payload);
            final String topic = message.getTopic().toString();
            log.debug("Received MQTT message on topic <{}>: {}", topic, textPayload);

            headers.put(MQTT_TOPIC_HEADER, topic);
            final HeaderMapping mqttTopicHeaderMapping = ConnectivityModelFactory.newHeaderMapping(
                    Collections.singletonMap(MQTT_TOPIC_HEADER, topic));
            final ExternalMessage externalMessage = ExternalMessageFactory
                    .newExternalMessageBuilder(headers)
                    .withTextAndBytes(textPayload, payload)
                    .withAuthorizationContext(source.getAuthorizationContext())
                    .withEnforcement(getEnforcementFilter(topic))
                    .withSourceAddress(sourceAddress)
                    .withPayloadMapping(payloadMapping)
                    .withHeaderMapping(mqttTopicHeaderMapping)
                    .build();
            inboundMonitor.success(externalMessage);

            return Optional.of(externalMessage);
        } catch (final DittoRuntimeException e) {
            log.info("Failed to handle MQTT message: {}", e.getMessage());
            inboundMonitor.failure(headers, e);
        } catch (final Exception e) {
            log.info("Failed to handle MQTT message: {}", e.getMessage());
            inboundMonitor.exception(headers, e);
        }
        return Optional.empty();
    }

    @Nullable
    private EnforcementFilter<CharSequence> getEnforcementFilter(final String topic) {
        if (topicEnforcementFilterFactory != null) {
            return topicEnforcementFilterFactory.getFilter(topic);
        } else {
            return null;
        }
    }

    private boolean isDryRun(final Object message) {
        return dryRun;
    }
}
