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

import static org.eclipse.ditto.services.models.connectivity.placeholders.ConnectivityPlaceholders.newSourceAddressPlaceholder;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.common.ByteBufferUtils;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.EnforcementFilter;
import org.eclipse.ditto.model.connectivity.EnforcementFilterFactory;
import org.eclipse.ditto.model.connectivity.HeaderMapping;
import org.eclipse.ditto.model.connectivity.PayloadMapping;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.services.connectivity.messaging.BaseConsumerActor;
import org.eclipse.ditto.services.connectivity.messaging.internal.RetrieveAddressStatus;
import org.eclipse.ditto.services.connectivity.util.ConnectivityMdcEntryKey;
import org.eclipse.ditto.services.models.connectivity.EnforcementFactoryFactory;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.services.utils.akka.logging.ThreadSafeDittoLoggingAdapter;

import com.hivemq.client.mqtt.datatypes.MqttQos;

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

    protected final ThreadSafeDittoLoggingAdapter logger;
    protected final boolean dryRun;
    @Nullable protected final EnforcementFilterFactory<String, CharSequence> topicEnforcementFilterFactory;
    protected final PayloadMapping payloadMapping;
    protected final boolean reconnectForRedelivery;

    protected AbstractMqttConsumerActor(final Connection connection, final ActorRef messageMappingProcessor,
            final Source source, final boolean dryRun, final boolean reconnectForRedelivery) {
        super(connection, String.join(";", source.getAddresses()), messageMappingProcessor, source);

        logger = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this)
                .withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID.toString(), connection.getId());

        this.dryRun = dryRun;
        this.payloadMapping = source.getPayloadMapping();
        this.reconnectForRedelivery = reconnectForRedelivery;
        topicEnforcementFilterFactory = source.getEnforcement()
                .map(enforcement -> EnforcementFactoryFactory
                        .newEnforcementFilterFactory(enforcement, newSourceAddressPlaceholder()))
                .orElse(null);
    }

    /**
     * Returns the HiveMQ client specific publish message class to work with.
     *
     * @return the HiveMQ publish message class.
     */
    protected abstract Class<P> getPublishMessageClass();

    /**
     * Extracts MQTT specific headers / user properties from the passed message as a new mutable HashMap.
     *
     * @param message the message to extract the headers from.
     * @return the newly created HashMap of headers.
     */
    protected HashMap<String, String> extractHeadersMapFromMqttMessage(final P message) {
        final HashMap<String, String> headersFromMqttMessage = new HashMap<>();

        headersFromMqttMessage.put(MQTT_TOPIC_HEADER, getTopic(message));
        headersFromMqttMessage.put(MQTT_QOS_HEADER, String.valueOf(getQoS(message).getCode()));
        headersFromMqttMessage.put(MQTT_RETAIN_HEADER, String.valueOf(isRetain(message)));

        return headersFromMqttMessage;
    }

    /**
     * Extracts the MQTT payload from the given message.
     *
     * @param message the message to extract the payload from.
     * @return the MQTT payload.
     */
    protected abstract Optional<ByteBuffer> getPayload(P message);

    /**
     * Extracts the MQTT topic from the given message.
     *
     * @param message the message to extract the topic from.
     * @return the MQTT topic.
     */
    protected abstract String getTopic(P message);

    /**
     * Extracts the MQTT qos from the given message.
     *
     * @param message the message to extract the qos from.
     * @return the MQTT qos.
     */
    protected abstract MqttQos getQoS(P message);

    /**
     * Extracts the MQTT retain flag from the given message.
     *
     * @param message the message to extract the retain flag from.
     * @return the MQTT retain flag.
     */
    protected abstract boolean isRetain(P message);

    /**
     * Acknowledges the passed message.
     *
     * @param message the message to acknowledge.
     */
    protected abstract void sendPubAck(P message);

    @Nullable
    abstract EnforcementFilter<CharSequence> getEnforcementFilter(Map<String, String> headers, String topic);

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(getPublishMessageClass(), this::isDryRun,
                        message -> logger.info("Dropping message in dryRun mode: {}", message))
                .match(getPublishMessageClass(), this::handleMqttMessage)
                .match(RetrieveAddressStatus.class, ram -> getSender().tell(getCurrentSourceStatus(), getSelf()))
                .matchAny(unhandled -> {
                    logger.info("Unhandled message: {}", unhandled);
                    unhandled(unhandled);
                })
                .build();
    }

    @Override
    protected ThreadSafeDittoLoggingAdapter log() {
        return logger;
    }

    private void handleMqttMessage(final P message) {
        logger.debug("Received message: {}", message);
        final Optional<ExternalMessage> externalMessageOptional = hiveToExternalMessage(message, connectionId);
        final ActorRef parent = getContext().getParent();
        externalMessageOptional.ifPresent(externalMessage ->
                forwardToMappingActor(externalMessage,
                        () -> acknowledge(externalMessage, message),
                        redeliver -> reject(externalMessage, message, redeliver, parent))
        );
    }

    private Optional<ExternalMessage> hiveToExternalMessage(final P message, final ConnectionId connectionId) {
        HashMap<String, String> headers = null;
        try {
            final ByteBuffer payload = getPayload(message)
                    .map(ByteBuffer::asReadOnlyBuffer)
                    .orElse(ByteBufferUtils.empty());
            final String textPayload = ByteBufferUtils.toUtf8String(payload);
            final String topic = getTopic(message);
            logger.debug("Received MQTT message on topic <{}>: {}", topic, textPayload);

            headers = extractHeadersMapFromMqttMessage(message);

            final Map<String, String> headerMappingMap = new HashMap<>();

            final Optional<HeaderMapping> sourceHeaderMapping = source.getHeaderMapping();
            if (sourceHeaderMapping.isPresent()) {
                headerMappingMap.putAll(sourceHeaderMapping.get().getMapping());
            } else {
                // apply fallback header mapping when headerMapping was "null"/not present in order to stay backwards
                //  compatible:
                headerMappingMap.put(MQTT_TOPIC_HEADER, getHeaderPlaceholder(MQTT_TOPIC_HEADER));
                headerMappingMap.put(MQTT_QOS_HEADER, getHeaderPlaceholder(MQTT_QOS_HEADER));
                headerMappingMap.put(MQTT_RETAIN_HEADER, getHeaderPlaceholder(MQTT_RETAIN_HEADER));
            }

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
            logger.info("Got DittoRuntimeException '{}' when command was parsed: {}", e.getErrorCode(), e.getMessage());
            if (headers != null) {
                // forwarding to messageMappingProcessor only make sense if we were able to extract the headers,
                // because we need a reply-to address to send the error response
                inboundMonitor.failure(headers, e);
                forwardToMappingActor(e.setDittoHeaders(DittoHeaders.of(headers)));
            } else {
                inboundMonitor.failure(e);
            }
        } catch (final Exception e) {
            logger.info("Failed to handle MQTT message: {}", e.getMessage());
            if (null != headers) {
                inboundMonitor.exception(headers, e);
            } else {
                inboundMonitor.exception(e);
            }

        }
        return Optional.empty();
    }

    private static String getHeaderPlaceholder(final String headerName) {
        return "{{ header:" + headerName + "}}";
    }

    private void acknowledge(final ExternalMessage externalMessage, final P message) {
        try {
            sendPubAck(message);
            inboundAcknowledgedMonitor.success(externalMessage, "Sending success acknowledgement");
        } catch (final IllegalStateException e) {
            // this message was acknowledged by another consumer actor due to overlapping topic
            inboundAcknowledgedMonitor.exception(externalMessage,
                    "Acknowledgement of incoming message at topic <{0}> failed " +
                            "because it was acknowledged already by another source.",
                    getTopic(message));
        }
    }

    private void reject(final ExternalMessage externalMessage, final P publish, final boolean redeliver,
            final ActorRef parent) {
        if (redeliver && reconnectForRedelivery) {
            final String message = "Restarting connection for redeliveries due to unfulfilled acknowledgements.";
            inboundAcknowledgedMonitor.exception(externalMessage, message);
            parent.tell(AbstractMqttClientActor.Control.RECONNECT_CONSUMER_CLIENT, getSelf());
        } else {
            final String message = "Unfulfilled acknowledgements are present, but redelivery is not possible.";
            inboundAcknowledgedMonitor.exception(externalMessage, message);
            acknowledge(externalMessage, publish);
        }
    }

    private boolean isDryRun(final Object message) {
        return dryRun;
    }

}
