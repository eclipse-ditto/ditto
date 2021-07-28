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
package org.eclipse.ditto.connectivity.service.messaging.rabbitmq;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.CharsetDeterminer;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.api.EnforcementFactoryFactory;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageBuilder;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.EnforcementFilterFactory;
import org.eclipse.ditto.connectivity.model.PayloadMapping;
import org.eclipse.ditto.connectivity.model.ResourceStatus;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.messaging.LegacyBaseConsumerActor;
import org.eclipse.ditto.connectivity.service.messaging.internal.RetrieveAddressStatus;
import org.eclipse.ditto.connectivity.service.util.ConnectivityMdcEntryKey;
import org.eclipse.ditto.internal.models.placeholders.PlaceholderFactory;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
import org.eclipse.ditto.internal.utils.tracing.instruments.trace.StartedTrace;
import org.eclipse.ditto.internal.utils.tracing.instruments.trace.Traces;

import com.rabbitmq.client.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;

import akka.NotUsed;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.javadsl.Sink;


/**
 * Actor which receives message from an RabbitMQ source and forwards them to a {@code MessageMappingProcessorActor}.
 */
public final class RabbitMQConsumerActor extends LegacyBaseConsumerActor {

    private static final String MESSAGE_ID_HEADER = "messageId";
    private static final String CONTENT_TYPE_APPLICATION_OCTET_STREAM = "application/octet-stream";

    private final ThreadSafeDittoLoggingAdapter log;

    @Nullable
    private final EnforcementFilterFactory<Map<String, String>, Signal<?>> headerEnforcementFilterFactory;
    private final PayloadMapping payloadMapping;
    private final Channel channel;

    @SuppressWarnings("unused")
    private RabbitMQConsumerActor(final Connection connection, final String sourceAddress,
            final Sink<Object, NotUsed> inboundMappingSink, final Source source, final Channel channel) {
        super(connection, sourceAddress, inboundMappingSink, source);

        log = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this)
                .withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID.toString(), connectionId);

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
    protected ThreadSafeDittoLoggingAdapter log() {
        return log;
    }

    /**
     * Creates Akka configuration object {@link Props} for this {@code RabbitMQConsumerActor}.
     *
     * @param sourceAddress the source address.
     * @param inboundMappingSink the mapping sink where received messages are forwarded to
     * @param source the configured connection source for the consumer actor.
     * @param connection the connection
     * @return the Akka configuration Props object.
     */
    static Props props(final String sourceAddress, final Sink<Object, NotUsed> inboundMappingSink, final Source source,
            Channel channel,
            final Connection connection) {

        return Props.create(RabbitMQConsumerActor.class, connection, sourceAddress, inboundMappingSink, source,
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

        StartedTrace trace = Traces.emptyStartedTrace();
        Map<String, String> headers = null;
        try {
            final String correlationId = properties.getCorrelationId();
            if (log.isDebugEnabled()) {
                log.withCorrelationId(correlationId)
                        .debug("Received message from RabbitMQ ({}//{}): {}", envelope, properties,
                                new String(body, StandardCharsets.UTF_8));
            }
            headers = extractHeadersFromMessage(properties, envelope);

            trace = DittoTracing.trace(DittoTracing.extractTraceContext(headers), "rabbitmq.consume")
                    .correlationId(correlationId)
                    .start();

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
            externalMessageBuilder.withHeaderMapping(source.getHeaderMapping());
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
            trace.fail(e);
        } catch (final Exception e) {
            log.warning("Processing delivery {} failed: {}", envelope.getDeliveryTag(), e.getMessage());
            if (headers != null) {
                inboundMonitor.exception(headers, e);
            } else {
                inboundMonitor.exception(e);
            }
            trace.fail(e);
        } finally {
            trace.finish();
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
