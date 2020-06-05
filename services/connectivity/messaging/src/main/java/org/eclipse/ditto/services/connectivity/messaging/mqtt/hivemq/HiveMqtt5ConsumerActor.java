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
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Enforcement;
import org.eclipse.ditto.model.connectivity.EnforcementFactoryFactory;
import org.eclipse.ditto.model.connectivity.EnforcementFilter;
import org.eclipse.ditto.model.connectivity.EnforcementFilterFactory;
import org.eclipse.ditto.model.connectivity.HeaderMapping;
import org.eclipse.ditto.model.connectivity.PayloadMapping;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.placeholders.PlaceholderFactory;
import org.eclipse.ditto.services.connectivity.messaging.BaseConsumerActor;
import org.eclipse.ditto.services.connectivity.messaging.internal.RetrieveAddressStatus;
import org.eclipse.ditto.services.connectivity.util.ConnectionLogUtil;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;

import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Actor which receives message from an MQTT 5 broker and forwards them to a {@code MessageMappingProcessorActor}.
 *
 * @since 1.1.0
 */
public final class HiveMqtt5ConsumerActor extends BaseConsumerActor {

    private static final String MQTT_TOPIC_HEADER = "mqtt.topic";
    static final String NAME = "HiveMqtt5ConsumerActor";

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
    private final boolean dryRun;
    @Nullable private final EnforcementFilterFactory<Map<String, String>, CharSequence> headerEnforcementFilterFactory;
    @Nullable private final EnforcementFilterFactory<String, CharSequence> topicEnforcementFilterFactory;
    private final PayloadMapping payloadMapping;

    @SuppressWarnings("unused")
    private HiveMqtt5ConsumerActor(final ConnectionId connectionId, final ActorRef messageMappingProcessor,
            final Source source, final boolean dryRun) {
        super(connectionId, String.join(";", source.getAddresses()), messageMappingProcessor, source);
        this.dryRun = dryRun;
        this.payloadMapping = source.getPayloadMapping();
        final Enforcement enforcement = source.getEnforcement().orElse(null);
        if (enforcement != null &&
                enforcement.getInput().contains(ConnectivityModelFactory.SOURCE_ADDRESS_ENFORCEMENT)) {
            topicEnforcementFilterFactory = EnforcementFactoryFactory
                    .newEnforcementFilterFactory(enforcement, ConnectivityModelFactory.newSourceAddressPlaceholder());
            headerEnforcementFilterFactory = null;
        } else {
            topicEnforcementFilterFactory = null;
            headerEnforcementFilterFactory = enforcement != null ? EnforcementFactoryFactory
                    .newEnforcementFilterFactory(enforcement, PlaceholderFactory.newHeadersPlaceholder()) :
                    input -> null;
        }
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
        return Props.create(HiveMqtt5ConsumerActor.class, connectionId, messageMappingProcessor,
                source, dryRun);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Mqtt5Publish.class, this::isDryRun,
                        message -> log.info("Dropping message in dryRun mode: {}", message))
                .match(Mqtt5Publish.class, this::handleMqttMessage)
                .match(RetrieveAddressStatus.class, ram -> getSender().tell(getCurrentSourceStatus(), getSelf()))
                .matchAny(unhandled -> {
                    log.info("Unhandled message: {}", unhandled);
                    unhandled(unhandled);
                })
                .build();
    }

    private void handleMqttMessage(final Mqtt5Publish message) {
        log.debug("Received message: {}", message);
        final Optional<ExternalMessage> externalMessageOptional = hiveToExternalMessage(message, connectionId);
        externalMessageOptional.ifPresent(externalMessage ->
                forwardToMappingActor(externalMessage, () -> acknowledge(message),
                        redeliver -> inboundMonitor.exception(
                                "Withholding PUBREC or PUBACK due to unfulfilled acknowledgements."))
        );
    }

    private void acknowledge(final Mqtt5Publish message) {
        try {
            log.debug("Acknowledging: {}", message);
            message.acknowledge();
        } catch (final IllegalStateException e) {
            // this message was acknowledged by another consumer actor due to overlapping topic
            inboundMonitor.exception("Acknowledgement of incoming message at topic <{0}> failed " +
                            "because it was acknowledged already by another source.",
                    message.getTopic());
        }
    }

    private Optional<ExternalMessage> hiveToExternalMessage(final Mqtt5Publish message,
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

            final Map<String, String> headerMappingMap = new HashMap<>(source.getHeaderMapping()
                    .map(HeaderMapping::getMapping)
                    .orElse(Collections.emptyMap()));
            headerMappingMap.put(MQTT_TOPIC_HEADER, topic);

            final ExternalMessage externalMessage = ExternalMessageFactory
                    .newExternalMessageBuilder(headers)
                    .withTextAndBytes(textPayload, payload)
                    .withAuthorizationContext(source.getAuthorizationContext())
                    .withEnforcement(getEnforcementFilter(headers, topic))
                    .withHeaderMapping(ConnectivityModelFactory.newHeaderMapping(headerMappingMap))
                    .withSourceAddress(sourceAddress)
                    .withPayloadMapping(payloadMapping)
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
            if (null != headers) {
                inboundMonitor.exception(headers, e);
            } else {
                inboundMonitor.exception(e);
            }

            log.error(e, "Unexpected {}: {}", e.getClass().getName(), e.getMessage());
        }
        return Optional.empty();
    }


    private HashMap<String, String> extractHeadersMapFromMqttMessage(final Mqtt5Publish message) {
        final HashMap<String, String> headersFromMqttMessage = new HashMap<>();

        final String topic = message.getTopic().toString();
        headersFromMqttMessage.put(MQTT_TOPIC_HEADER, topic);

        message.getCorrelationData().ifPresent(correlationData -> {
            final String correlationId = ByteBufferUtils.toUtf8String(correlationData);
            headersFromMqttMessage.put(DittoHeaderDefinition.CORRELATION_ID.getKey(), correlationId);
        });

        message.getResponseTopic().ifPresent(responseTopic -> {
            final String replyTo = responseTopic.toString();
            headersFromMqttMessage.put(ExternalMessage.REPLY_TO_HEADER, replyTo);
        });

        message.getContentType().ifPresent(contentType ->
                headersFromMqttMessage.put(ExternalMessage.CONTENT_TYPE_HEADER, contentType.toString())
        );

        message.getUserProperties().asList().forEach(
                userProp -> headersFromMqttMessage.put(userProp.getName().toString(), userProp.getValue().toString()));

        return headersFromMqttMessage;
    }

    @Nullable
    private EnforcementFilter<CharSequence> getEnforcementFilter(final Map<String, String> headers,
            final String topic) {
        if (headerEnforcementFilterFactory != null) {
            return headerEnforcementFilterFactory.getFilter(headers);
        } else if (topicEnforcementFilterFactory != null) {
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
