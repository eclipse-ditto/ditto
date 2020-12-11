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

import java.io.IOException;
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
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.EnforcementFilterFactory;
import org.eclipse.ditto.model.connectivity.PayloadMapping;
import org.eclipse.ditto.model.connectivity.ResourceStatus;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.placeholders.PlaceholderFactory;
import org.eclipse.ditto.services.connectivity.messaging.BaseConsumerActor;
import org.eclipse.ditto.services.connectivity.messaging.internal.RetrieveAddressStatus;
import org.eclipse.ditto.services.models.connectivity.EnforcementFactoryFactory;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageBuilder;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;

import com.rabbitmq.client.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;


/**
 * Actor which receives message from an RabbitMQ source and forwards them to a {@code MessageMappingProcessorActor}.
 */
public final class RabbitMQConsumerActor extends BaseConsumerActor {

    private static final String MESSAGE_ID_HEADER = "messageId";
    private static final String CONTENT_TYPE_APPLICATION_OCTET_STREAM = "application/octet-stream";

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    @Nullable
    private final EnforcementFilterFactory<Map<String, String>, CharSequence> headerEnforcementFilterFactory;
    private final PayloadMapping payloadMapping;
    private final Channel channel;

    @SuppressWarnings("unused")
    private RabbitMQConsumerActor(final Connection connection, final String sourceAddress,
            final ActorRef inboundMessageProcessor, final Source source, final Channel channel) {
        super(connection, sourceAddress, inboundMessageProcessor, source);
        headerEnforcementFilterFactory =
                source.getEnforcement()
                        .map(value ->
                                EnforcementFactoryFactory.newEnforcementFilterFactory(value,
                                        PlaceholderFactory.newHeadersPlaceholder()))
                        .orElse(null);
        this.payloadMapping = source.getPayloadMapping();
        this.channel = channel;
    }

    @Override
    protected DittoDiagnosticLoggingAdapter log() {
        return log;
    }

    /**
     * Creates Akka configuration object {@link Props} for this {@code RabbitMQConsumerActor}.
     *
     * @param sourceAddress the source address.
     * @param inboundMessageProcessor the message mapping processor where received messages are forwarded to
     * @param source the configured connection source for the consumer actor.
     * @param connection the connection
     * @return the Akka configuration Props object.
     */
    static Props props(final String sourceAddress, final ActorRef inboundMessageProcessor, final Source source,
            Channel channel,
            final Connection connection) {

        return Props.create(RabbitMQConsumerActor.class, connection, sourceAddress, inboundMessageProcessor, source,
                channel);
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

        Map<String, String> headers = null;
        try {
            final String correlationId = properties.getCorrelationId();
            if (log.isDebugEnabled()) {
                log.withCorrelationId(correlationId)
                        .debug("Received message from RabbitMQ ({}//{}): {}", envelope, properties,
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

            forwardToMappingActor(externalMessage,
                    () -> {
                        try {
                            final long deliveryTag = delivery.getEnvelope().getDeliveryTag();
                            channel.basicAck(deliveryTag, false);
                            inboundAcknowledgedMonitor.success(externalMessage,
                                    "Sending success acknowledgement: basic.ack for deliveryTag={0}", deliveryTag);
                        } catch (final IOException e) {
                            log.error("Acknowledging delivery {} failed: {}", envelope.getDeliveryTag(),
                                    e.getMessage());
                            inboundAcknowledgedMonitor.exception(e);
                        }
                    },
                    requeue -> {
                        try {
                            channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, requeue);
                            inboundAcknowledgedMonitor.exception("Sending negative acknowledgement: " +
                                            "basic.nack for deliveryTag={0}, requeue={0}",
                                    delivery.getEnvelope().getDeliveryTag(), requeue);
                        } catch (final IOException e) {
                            log.error("Delivery of basic.nack for deliveryTag={} failed: {}", envelope.getDeliveryTag(),
                                    e.getMessage());
                            inboundAcknowledgedMonitor.exception(e);
                        }
                    });
        } catch (final DittoRuntimeException e) {
            log.warning("Processing delivery {} failed: {}", envelope.getDeliveryTag(), e.getMessage());
            if (headers != null) {
                // send response if headers were extracted successfully
                forwardToMappingActor(e.setDittoHeaders(DittoHeaders.of(headers)));
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
