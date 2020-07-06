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
import org.eclipse.ditto.services.connectivity.messaging.internal.RetrieveAddressStatus;
import org.eclipse.ditto.services.connectivity.util.ConnectionLogUtil;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;

import akka.actor.ActorRef;

/**
 * Common implementation for MQTT3 and MQTT5 consumer actors.
 *
 * @param <P> type of PUBLISH messages.
 */
abstract class AbstractMqttConsumerActor<P> extends BaseConsumerActor {

    protected static final String MQTT_TOPIC_HEADER = "mqtt.topic";
    protected static final String MQTT_QOS_HEADER = "mqtt.qos";
    protected static final String MQTT_RETAIN_HEADER = "mqtt.retain";

    protected final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
    protected final boolean dryRun;
    @Nullable protected final EnforcementFilterFactory<String, CharSequence> topicEnforcementFilterFactory;
    protected final PayloadMapping payloadMapping;
    protected final boolean reconnectForRedelivery;

    protected AbstractMqttConsumerActor(final ConnectionId connectionId, final ActorRef messageMappingProcessor,
            final Source source, final boolean dryRun, final boolean reconnectForRedelivery) {
        super(connectionId, String.join(";", source.getAddresses()), messageMappingProcessor, source);
        this.dryRun = dryRun;
        this.payloadMapping = source.getPayloadMapping();
        this.reconnectForRedelivery = reconnectForRedelivery;
        topicEnforcementFilterFactory = source.getEnforcement()
                .map(enforcement -> EnforcementFactoryFactory
                        .newEnforcementFilterFactory(enforcement,
                                ConnectivityModelFactory.newSourceAddressPlaceholder()))
                .orElse(null);
    }

    abstract Class<P> getPublishMessageClass();

    abstract HashMap<String, String> extractHeadersMapFromMqttMessage(P message);

    abstract Optional<ByteBuffer> getPayload(P message);

    abstract String getTopic(P message);

    abstract void sendPubAck(P message);

    @Nullable
    abstract EnforcementFilter<CharSequence> getEnforcementFilter(Map<String, String> headers, String topic);

    public Receive createReceive() {
        return receiveBuilder()
                .match(getPublishMessageClass(), this::isDryRun,
                        message -> log.info("Dropping message in dryRun mode: {}", message))
                .match(getPublishMessageClass(), this::handleMqttMessage)
                .match(RetrieveAddressStatus.class, ram -> getSender().tell(getCurrentSourceStatus(), getSelf()))
                .matchAny(unhandled -> {
                    log.info("Unhandled message: {}", unhandled);
                    unhandled(unhandled);
                })
                .build();
    }

    @Override
    protected DittoDiagnosticLoggingAdapter log() {
        return log;
    }

    private void handleMqttMessage(final P message) {
        log.debug("Received message: {}", message);
        final Optional<ExternalMessage> externalMessageOptional = hiveToExternalMessage(message, connectionId);
        final ActorRef parent = getContext().getParent();
        externalMessageOptional.ifPresent(externalMessage ->
                forwardToMappingActor(externalMessage,
                        () -> acknowledge(message),
                        redeliver -> reject(message, redeliver, parent))
        );
    }

    private Optional<ExternalMessage> hiveToExternalMessage(final P message, final ConnectionId connectionId) {
        HashMap<String, String> headers = null;
        try {
            ConnectionLogUtil.enhanceLogWithConnectionId(log, connectionId);
            final ByteBuffer payload = getPayload(message)
                    .map(ByteBuffer::asReadOnlyBuffer)
                    .orElse(ByteBufferUtils.empty());
            final String textPayload = ByteBufferUtils.toUtf8String(payload);
            final String topic = getTopic(message);
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
                    .withEnforcement(getEnforcementFilter(headers, topic))
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

    private void acknowledge(final P message) {
        try {
            sendPubAck(message);
        } catch (final IllegalStateException e) {
            // this message was acknowledged by another consumer actor due to overlapping topic
            inboundMonitor.exception("Acknowledgement of incoming message at topic <{0}> failed " +
                            "because it was acknowledged already by another source.",
                    getTopic(message));
        }
    }

    private void reject(final P publish, final boolean redeliver, final ActorRef parent) {
        if (redeliver && reconnectForRedelivery) {
            final String message = "Restarting connection for redeliveries due to unfulfilled acknowledgements.";
            inboundMonitor.exception(message);
            parent.tell(AbstractMqttClientActor.Control.RECONNECT_CONSUMER_CLIENT, getSelf());
        } else {
            final String message = "Unfulfilled acknowledgements are present, but redelivery is not possible.";
            inboundMonitor.exception(message);
            acknowledge(publish);
        }
    }

    private boolean isDryRun(final Object message) {
        return dryRun;
    }

}
