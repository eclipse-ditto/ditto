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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.concurrent.NotThreadSafe;

import org.apache.kafka.common.header.Headers;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.model.EnforcementFilterFactory;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLogger;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;

/**
 * Transforms the value part of a kafka key-value-pair to an {@link ExternalMessage}.
 */
@NotThreadSafe
final class KafkaMessageToExternalMessage
        implements Transformer<String, String, KeyValue<String, ExternalMessage>> {

    private static final DittoLogger LOGGER = DittoLoggerFactory.getLogger(KafkaMessageToExternalMessage.class);
    private final Source source;
    private final String sourceAddress;
    private final EnforcementFilterFactory<Map<String, String>, Signal<?>> headerEnforcementFilterFactory;

    private ProcessorContext context;

    public KafkaMessageToExternalMessage(final Source source, final String sourceAddress,
            final EnforcementFilterFactory<Map<String, String>, Signal<?>> headerEnforcementFilterFactory) {
        this.source = source;
        this.sourceAddress = sourceAddress;
        this.headerEnforcementFilterFactory = headerEnforcementFilterFactory;
    }

    @Override
    public void init(final ProcessorContext context) {
        this.context = context;
    }

    @Override
    public KeyValue<String, ExternalMessage> transform(final String key, final String value) {
        final Headers headers = context.headers();
        final Map<String, String> messageHeaders = new HashMap<>();
        headers.forEach(header -> messageHeaders.put(header.key(), new String(header.value())));
        final String correlationId = messageHeaders
                .getOrDefault(DittoHeaderDefinition.CORRELATION_ID.getKey(), UUID.randomUUID().toString());

        final DittoLogger correlationIdScopedLogger = LOGGER.withCorrelationId(correlationId);
        correlationIdScopedLogger.info(
                "Transforming incoming kafka message with headers <{}> for thing with ID <{}>.",
                messageHeaders, key);
        if (correlationIdScopedLogger.isDebugEnabled()) {
            correlationIdScopedLogger.debug(
                    "Transforming incoming kafka message <{}> with headers <{}> for thing with ID <{}>.",
                    value, messageHeaders, key);
        }

        final ExternalMessage externalMessage = ExternalMessageFactory.newExternalMessageBuilder(messageHeaders)
                .withTextAndBytes(value, value.getBytes())
                .withAuthorizationContext(source.getAuthorizationContext())
                .withEnforcement(headerEnforcementFilterFactory.getFilter(messageHeaders))
                .withHeaderMapping(source.getHeaderMapping())
                .withSourceAddress(sourceAddress)
                .withPayloadMapping(source.getPayloadMapping())
                .build();

        return new KeyValue<>(key, externalMessage);
    }

    @Override
    public void close() {
        this.context = null;
    }

}
