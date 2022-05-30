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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq;

import static org.eclipse.ditto.connectivity.service.placeholders.ConnectivityPlaceholders.newSourceAddressPlaceholder;
import static org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttHeader.MQTT_QOS;
import static org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttHeader.MQTT_RETAIN;
import static org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttHeader.MQTT_TOPIC;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.ByteBufferUtils;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.service.EnforcementFactoryFactory;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.EnforcementFilter;
import org.eclipse.ditto.connectivity.model.EnforcementFilterFactory;
import org.eclipse.ditto.connectivity.model.PayloadMapping;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.ConnectivityStatusResolver;
import org.eclipse.ditto.connectivity.service.messaging.LegacyBaseConsumerActor;
import org.eclipse.ditto.connectivity.service.messaging.internal.RetrieveAddressStatus;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;

import com.hivemq.client.mqtt.datatypes.MqttQos;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.stream.javadsl.Sink;

/**
 * Common implementation for MQTT3 and MQTT5 consumer actors.
 *
 * @param <P> type of PUBLISH messages.
 */
abstract class AbstractMqttConsumerActor<P> extends LegacyBaseConsumerActor {

    protected final boolean dryRun;
    @Nullable protected final EnforcementFilterFactory<String, Signal<?>> topicEnforcementFilterFactory;
    protected final PayloadMapping payloadMapping;
    protected final boolean reconnectForRedelivery;

    protected AbstractMqttConsumerActor(final Connection connection, final Sink<Object, NotUsed> inboundMappingSink,
            final Source source, final boolean dryRun, final boolean reconnectForRedelivery,
            final ConnectivityStatusResolver connectivityStatusResolver, final ConnectivityConfig connectivityConfig) {
        super(connection, String.join(";", source.getAddresses()), inboundMappingSink, source,
                connectivityStatusResolver, connectivityConfig);

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

        headersFromMqttMessage.put(MQTT_TOPIC.getName(), getTopic(message));
        headersFromMqttMessage.put(MQTT_QOS.getName(), String.valueOf(getQoS(message).getCode()));
        headersFromMqttMessage.put(MQTT_RETAIN.getName(), String.valueOf(isRetain(message)));

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
    abstract EnforcementFilter<Signal<?>> getEnforcementFilter(Map<String, String> headers, String topic);

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
        final Optional<ExternalMessage> externalMessageOptional = hiveToExternalMessage(message);
        final ActorRef parent = getContext().getParent();
        externalMessageOptional.ifPresent(externalMessage ->
                forwardToMapping(externalMessage,
                        () -> acknowledge(externalMessage, message),
                        redeliver -> reject(externalMessage, message, redeliver, parent))
        );
    }

    private Optional<ExternalMessage> hiveToExternalMessage(final P message) {
        HashMap<String, String> headers = null;
        try {
            final ByteBuffer payload = getPayload(message)
                    .map(ByteBuffer::asReadOnlyBuffer)
                    .orElse(ByteBufferUtils.empty());
            final var textPayload = ByteBufferUtils.toUtf8String(payload);
            final String topic = getTopic(message);
            logger.debug("Received MQTT message on topic <{}>: {}", topic, textPayload);

            headers = extractHeadersMapFromMqttMessage(message);

            final ExternalMessage externalMessage = ExternalMessageFactory
                    .newExternalMessageBuilder(headers)
                    .withTextAndBytes(textPayload, payload)
                    .withAuthorizationContext(source.getAuthorizationContext())
                    .withEnforcement(getEnforcementFilter(headers, topic))
                    .withSourceAddress(sourceAddress)
                    .withPayloadMapping(payloadMapping)
                    .withHeaderMapping(source.getHeaderMapping())
                    .build();
            inboundMonitor.success(externalMessage);

            return Optional.of(externalMessage);
        } catch (final DittoRuntimeException e) {
            logger.info("Got DittoRuntimeException '{}' when command was parsed: {}", e.getErrorCode(), e.getMessage());
            if (headers != null) {
                // forwarding to messageMappingProcessor only make sense if we were able to extract the headers,
                // because we need a reply-to address to send the error response
                inboundMonitor.failure(headers, e);
                forwardToMapping(e.setDittoHeaders(DittoHeaders.of(headers)));
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
        if (reconnectForRedelivery && redeliver) {
            inboundAcknowledgedMonitor.exception(externalMessage, "Unfulfilled acknowledgements are " +
                            "present, restarting consumer client in order to get redeliveries.");
            parent.tell(AbstractMqttClientActor.Control.RECONNECT_CONSUMER_CLIENT, getSelf());
        } else if (!redeliver) {
            // acknowledge messages for which redelivery does not make sense (e.g. 400 bad request or 403 forbidden)
            //  as redelivering them will not solve any problem
            acknowledge(externalMessage, publish);
            inboundAcknowledgedMonitor.exception(externalMessage, "Unfulfilled acknowledgements are " +
                    "present, redelivery was NOT requested - therefore acknowledging the MQTT message!");
        } else {
            // strictly speaking one should not acknowledge message for which a redelivery was asked for, the MQTT spec
            //  however does not define that a MQTT broker should redeliver messages if an acknowledgement was not
            //  received - UNLESS the client reconnects - see option "reconnectForRedelivery" for getting reconnects
            acknowledge(externalMessage, publish);
            inboundAcknowledgedMonitor.exception(externalMessage, "Unfulfilled acknowledgements are " +
                    "present, redelivery was requested - however MQTT broker would not redeliver the message without " +
                    "a reconnect from the client - therefore acknowledging the MQTT message!");
        }
    }

    private boolean isDryRun(final Object message) {
        return dryRun;
    }

}
