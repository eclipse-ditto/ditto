/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.kafka;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.model.EnforcementFilterFactory;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLogger;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
import org.eclipse.ditto.internal.utils.tracing.instruments.trace.StartedTrace;

import scala.util.Either;
import scala.util.Left;
import scala.util.Right;

/**
 * Transforms incoming messages from Apache Kafka to {@link org.eclipse.ditto.connectivity.api.ExternalMessage}.
 */
@Immutable
final class KafkaMessageTransformer {

    private static final DittoLogger LOGGER = DittoLoggerFactory.getLogger(KafkaMessageTransformer.class);

    private final Source source;
    private final String sourceAddress;
    private final EnforcementFilterFactory<Map<String, String>, Signal<?>> headerEnforcementFilterFactory;
    private final ConnectionMonitor inboundMonitor;

    KafkaMessageTransformer(final Source source, final String sourceAddress,
            final EnforcementFilterFactory<Map<String, String>, Signal<?>> headerEnforcementFilterFactory,
            final ConnectionMonitor inboundMonitor) {
        this.source = source;
        this.sourceAddress = sourceAddress;
        this.headerEnforcementFilterFactory = headerEnforcementFilterFactory;
        this.inboundMonitor = inboundMonitor;
    }

    /**
     * Takes incoming kafka record and transforms the value to an {@link ExternalMessage}.
     *
     * @param consumerRecord the kafka record.
     * @return A value that is either an {@link ExternalMessage} in case the
     * transformation succeeded, or a {@link DittoRuntimeException} if it failed. Could also be null if an unexpected
     * Exception occurred which should result in the message being dropped as automated recovery is expected.
     */
    @Nullable
    public Either<ExternalMessage, DittoRuntimeException> transform(
            final ConsumerRecord<String, String> consumerRecord) {

        LOGGER.trace("Received record from kafka: {}", consumerRecord);

        final String key = consumerRecord.key();
        final String value = consumerRecord.value();
        final Map<String, String> messageHeaders = extractMessageHeaders(consumerRecord);
        final String correlationId = messageHeaders
                .getOrDefault(DittoHeaderDefinition.CORRELATION_ID.getKey(), UUID.randomUUID().toString());

        final StartedTrace trace = DittoTracing.trace(DittoTracing.extractTraceContext(messageHeaders), "kafka.consume")
                .correlationId(correlationId).start();

        try {
            final DittoLogger correlationIdScopedLogger = LOGGER.withCorrelationId(correlationId);
            correlationIdScopedLogger.debug(
                    "Transforming incoming kafka message <{}> with headers <{}> and key <{}>.",
                    value, messageHeaders, key
            );

            final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(messageHeaders)
                    .withTextAndBytes(value, value == null ? null : value.getBytes())
                    .withAuthorizationContext(source.getAuthorizationContext())
                    .withEnforcement(headerEnforcementFilterFactory.getFilter(messageHeaders))
                    .withHeaderMapping(source.getHeaderMapping())
                    .withSourceAddress(sourceAddress)
                    .withPayloadMapping(source.getPayloadMapping())
                    .build();

            inboundMonitor.success(externalMessage);

            return new Left<>(externalMessage);
        } catch (final DittoRuntimeException e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.withCorrelationId(e).debug(
                        "Got DittoRuntimeException '{}' when command was parsed: {}", e.getErrorCode(),
                        e.getMessage());
            }
            trace.fail(e);
            return new Right<>(e.setDittoHeaders(DittoHeaders.of(messageHeaders)));
        } catch (final Exception e) {
            inboundMonitor.exception(messageHeaders, e);
            LOGGER.withCorrelationId(correlationId)
                    .error(String.format("Unexpected {%s}: {%s}", e.getClass().getName(), e.getMessage()), e);
            trace.fail(e);
            return null; // Drop message
        } finally {
            trace.finish();
        }

    }

    private Map<String, String> extractMessageHeaders(final ConsumerRecord<String, String> consumerRecord) {
        final Map<String, String> messageHeaders = new HashMap<>();
        for (final Header header : consumerRecord.headers()) {
            if (messageHeaders.put(header.key(), new String(header.value())) != null) {
                inboundMonitor.exception("Dropped duplicated headers in record from topic {0} at offset #{1}",
                        consumerRecord.topic(), consumerRecord.offset());
            }
        }
        if (!messageHeaders.containsKey(DittoHeaderDefinition.CORRELATION_ID.getKey())) {
            messageHeaders.put(DittoHeaderDefinition.CORRELATION_ID.getKey(), UUID.randomUUID().toString());
        }

        // add properties from consumer record to headers to make them available in payload/header mappings
        Arrays.stream(KafkaHeader.values())
                .forEach(kafkaHeader -> kafkaHeader.apply(consumerRecord)
                        .ifPresent(property -> messageHeaders.put(kafkaHeader.getName(), property)));

        return messageHeaders;
    }

}
