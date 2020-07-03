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
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.common.ByteBufferUtils;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.EnforcementFactoryFactory;
import org.eclipse.ditto.model.connectivity.EnforcementFilter;
import org.eclipse.ditto.model.connectivity.EnforcementFilterFactory;
import org.eclipse.ditto.model.connectivity.HeaderMapping;
import org.eclipse.ditto.model.connectivity.PayloadMapping;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.services.connectivity.messaging.BaseConsumerActor;
import org.eclipse.ditto.services.connectivity.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.internal.ImmutableConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.internal.RetrieveAddressStatus;
import org.eclipse.ditto.services.connectivity.util.ConnectionLogUtil;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;

import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Actor which receives message from an MQTT broker and forwards them to a {@code MessageMappingProcessorActor}.
 */
public final class HiveMqtt3ConsumerActor extends BaseConsumerActor {

    private static final String MQTT_TOPIC_HEADER = "mqtt.topic";
    private static final String MQTT_QOS_HEADER = "mqtt.qos";
    private static final String MQTT_RETAIN_HEADER = "mqtt.retain";
    static final String NAME = "HiveMqtt3ConsumerActor";

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
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
        log.debug("Received message: {}", message);
        final Optional<ExternalMessage> externalMessageOptional = hiveToExternalMessage(message, connectionId);
        final ActorRef parent = getContext().getParent();
        externalMessageOptional.ifPresent(externalMessage ->
                forwardToMappingActor(externalMessage,
                        () -> acknowledge(message),
                        redeliver -> reject(message, redeliver, parent))
        );
    }

    private void reject(final Mqtt3Publish publish, final boolean redeliver, final ActorRef parent) {
        if (redeliver) {
            final String message = "Restarting connection for redeliveries due to unfulfilled acknowledgements.";
            inboundMonitor.exception(message);
            final ConnectionFailure failure = new ImmutableConnectionFailure(null, null, message);
            getContext().getParent().tell(failure, getSelf());
        } else {
            final String message = "Unfulfilled acknowledgements are present, but redelivery is not possible. " +
                    "Sending ";
            inboundMonitor.exception(message);
            publish.acknowledge();
        }
    }

    private void acknowledge(final Mqtt3Publish message) {
        try {
            message.acknowledge();
        } catch (final IllegalStateException e) {
            // this message was acknowledged by another consumer actor due to overlapping topic
            inboundMonitor.exception("Acknowledgement of incoming message at topic <{0}> failed " +
                            "because it was acknowledged already by another source.",
                    message.getTopic());
        }
    }

    private Optional<ExternalMessage> hiveToExternalMessage(final Mqtt3Publish message,
            final ConnectionId connectionId) {
        HashMap<String, String> headers = null;
        try {
            ConnectionLogUtil.enhanceLogWithConnectionId(log, connectionId);
            final ByteBuffer payload = message.getPayload()
                    .map(ByteBuffer::asReadOnlyBuffer)
                    .orElse(ByteBufferUtils.empty());
            final String textPayload = ByteBufferUtils.toUtf8String(payload);
            final String topic = message.getTopic().toString();
            log.debug("Received MQTT message on topic <{}>: {}", topic, textPayload);

            headers = extractHeadersMapFromMqttMessage(message);
            final Map<String, String> headerMappingMap = new HashMap<>();
            headerMappingMap.put(MQTT_TOPIC_HEADER, topic);
            headerMappingMap.putAll(source.getHeaderMapping()
                    .map(HeaderMapping::getMapping)
                    .orElse(Collections.emptyMap()));

            final HeaderMapping mqttTopicHeaderMapping = ConnectivityModelFactory.newHeaderMapping(headerMappingMap);
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
            log.info("Got DittoRuntimeException '{}' when command was parsed: {}", e.getErrorCode(), e.getMessage());
            if (headers != null) {
                // forwarding to messageMappingProcessor only make sense if we were able to extract the headers,
                // because we need a reply-to address to send the error response
                inboundMonitor.failure(headers, e);
                forwardToMappingActor(e.setDittoHeaders(DittoHeaders.of(headers)));
            } else {
                inboundMonitor.failure(e);
            }
        } catch (final Exception e) {
            log.info("Failed to handle MQTT message: {}", e.getMessage());
            if (null != headers) {
                inboundMonitor.exception(headers, e);
            } else {
                inboundMonitor.exception(e);
            }

        }
        return Optional.empty();
    }

    private HashMap<String, String> extractHeadersMapFromMqttMessage(final Mqtt3Publish message) {
        final HashMap<String, String> headersFromMqttMessage = new HashMap<>();

        headersFromMqttMessage.put(MQTT_TOPIC_HEADER, message.getTopic().toString());
        headersFromMqttMessage.put(MQTT_QOS_HEADER, String.valueOf(message.getQos().getCode()));
        headersFromMqttMessage.put(MQTT_RETAIN_HEADER, String.valueOf(message.isRetain()));

        return headersFromMqttMessage;
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

    @Override
    protected DittoDiagnosticLoggingAdapter log() {
        return log;
    }
}
