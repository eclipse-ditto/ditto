/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.messaging.rabbitmq;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.common.CharsetDeterminer;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.PayloadMapping;
import org.eclipse.ditto.model.connectivity.ResourceStatus;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.placeholders.EnforcementFactoryFactory;
import org.eclipse.ditto.model.placeholders.EnforcementFilterFactory;
import org.eclipse.ditto.model.placeholders.PlaceholderFactory;
import org.eclipse.ditto.services.connectivity.messaging.BaseConsumerActor;
import org.eclipse.ditto.services.connectivity.messaging.internal.RetrieveAddressStatus;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageBuilder;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import com.rabbitmq.client.BasicProperties;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;


/**
 * Actor which receives message from an RabbitMQ source and forwards them to a {@code MessageMappingProcessorActor}.
 */
public final class RabbitMQConsumerActor extends BaseConsumerActor {

    private static final String MESSAGE_ID_HEADER = "messageId";
    private static final String CONTENT_TYPE_APPLICATION_OCTET_STREAM = "application/octet-stream";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    @Nullable
    private final EnforcementFilterFactory<Map<String, String>, CharSequence> headerEnforcementFilterFactory;
    private final PayloadMapping payloadMapping;

    @SuppressWarnings("unused")
    private RabbitMQConsumerActor(final ConnectionId connectionId, final String sourceAddress,
            final ActorRef messageMappingProcessor, final Source source) {
        super(connectionId, sourceAddress, messageMappingProcessor, source);
        headerEnforcementFilterFactory =
                source.getEnforcement()
                        .map(value ->
                                EnforcementFactoryFactory.newEnforcementFilterFactory(value,
                                        PlaceholderFactory.newHeadersPlaceholder()))
                        .orElse(null);
        this.payloadMapping = source.getPayloadMapping();
    }

    /**
     * Creates Akka configuration object {@link Props} for this {@code RabbitMQConsumerActor}.
     *
     * @param sourceAddress the source address.
     * @param messageMappingProcessor the message mapping processor where received messages are forwarded to
     * @param source the configured connection source for the consumer actor.
     * @param connectionId ID of the connection
     * @return the Akka configuration Props object.
     */
    static Props props(final String sourceAddress, final ActorRef messageMappingProcessor, final Source source,
            final ConnectionId connectionId) {

        return Props.create(RabbitMQConsumerActor.class, connectionId, sourceAddress, messageMappingProcessor, source);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(Delivery.class, this::handleDelivery)
                .match(ResourceStatus.class, this::handleAddressStatus)
                .match(RetrieveAddressStatus.class, ram -> getSender().tell(getCurrentSourceStatus(), getSelf()))
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private void handleDelivery(final Delivery delivery) {
        final BasicProperties properties = delivery.getProperties();
        final Envelope envelope = delivery.getEnvelope();
        final byte[] body = delivery.getBody();
        final String hashKey = envelope.getExchange() + ":" + envelope.getRoutingKey();

        Map<String, String> headers = null;
        try {
            final String correlationId = properties.getCorrelationId();
            LogUtil.enhanceLogWithCorrelationId(log, correlationId);
            if (log.isDebugEnabled()) {
                log.debug("Received message from RabbitMQ ({}//{}): {}", envelope, properties,
                        new String(body, StandardCharsets.UTF_8));
            }
            headers = extractHeadersFromMessage(properties, envelope);
            final ExternalMessageBuilder externalMessageBuilder =
                    ExternalMessageFactory.newExternalMessageBuilder(headers);
            final String contentType = properties.getContentType();
            final String text = new String(body, CharsetDeterminer.getInstance().apply(contentType));
            if (shouldBeInterpretedAsBytes(contentType)) {
                externalMessageBuilder.withBytes(body);
            } else {
                externalMessageBuilder.withTextAndBytes(text, body);
            }
            externalMessageBuilder.withAuthorizationContext(source.getAuthorizationContext());
            if (headerEnforcementFilterFactory != null) {
                externalMessageBuilder.withEnforcement(headerEnforcementFilterFactory.getFilter(headers));
            }
            externalMessageBuilder.withHeaderMapping(source.getHeaderMapping().orElse(null));
            externalMessageBuilder.withSourceAddress(sourceAddress);
            externalMessageBuilder.withPayloadMapping(payloadMapping);
            final ExternalMessage externalMessage = externalMessageBuilder.build();
            inboundMonitor.success(externalMessage);
            forwardToMappingActor(externalMessage, hashKey);
        } catch (final DittoRuntimeException e) {
            log.warning("Processing delivery {} failed: {}", envelope.getDeliveryTag(), e.getMessage());
            if (headers != null) {
                // send response if headers were extracted successfully
                forwardToMappingActor(e.setDittoHeaders(DittoHeaders.of(headers)), hashKey);
                inboundMonitor.failure(headers, e);
            } else {
                inboundMonitor.failure(e);
            }
        } catch (final Exception e) {
            log.warning("Processing delivery {} failed: {}", envelope.getDeliveryTag(), e.getMessage());
            if (headers != null) {
                inboundMonitor.exception(headers, e);
            } else {
                inboundMonitor.exception(e);
            }
        }
    }

    private static boolean shouldBeInterpretedAsBytes(@Nullable final String contentType) {
        return contentType != null && contentType.startsWith(CONTENT_TYPE_APPLICATION_OCTET_STREAM);
    }

    private static Map<String, String> extractHeadersFromMessage(final BasicProperties properties,
            final Envelope envelope) {

        final Map<String, String> headersFromProperties = getHeadersFromProperties(properties.getHeaders());

        // set headers specific to rmq messages
        if (properties.getReplyTo() != null) {
            headersFromProperties.put(ExternalMessage.REPLY_TO_HEADER, properties.getReplyTo());
        }
        if (properties.getCorrelationId() != null) {
            headersFromProperties.put(DittoHeaderDefinition.CORRELATION_ID.getKey(), properties.getCorrelationId());
        }
        if (properties.getContentType() != null) {
            headersFromProperties.put(ExternalMessage.CONTENT_TYPE_HEADER, properties.getContentType());
        }
        headersFromProperties.put(MESSAGE_ID_HEADER, Long.toString(envelope.getDeliveryTag()));

        return headersFromProperties;
    }

    private static Map<String, String> getHeadersFromProperties(@Nullable final Map<String, Object> originalProps) {
        if (null != originalProps) {
            return originalProps.entrySet()
                    .stream()
                    .filter(entry -> Objects.nonNull(entry.getValue()))
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> String.valueOf(entry.getValue())));
        }

        return new HashMap<>();
    }

}
