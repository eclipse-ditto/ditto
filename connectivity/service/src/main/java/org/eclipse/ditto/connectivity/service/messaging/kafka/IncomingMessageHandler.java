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
import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.To;
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


@NotThreadSafe
final class IncomingMessageHandler implements Processor<String, String> {

    private static final DittoLogger LOGGER = DittoLoggerFactory.getLogger(IncomingMessageHandler.class);
    private final Source source;
    private final String sourceAddress;
    private final EnforcementFilterFactory<Map<String, String>, Signal<?>> headerEnforcementFilterFactory;
    private final ConnectionMonitor inboundMonitor;
    private final String messageForwarder;
    private final String errorForwarder;

    private ProcessorContext context;

    public IncomingMessageHandler(final Source source, final String sourceAddress,
            final EnforcementFilterFactory<Map<String, String>, Signal<?>> headerEnforcementFilterFactory,
            final ConnectionMonitor inboundMonitor, final String messageForwarder,
            final String errorForwarder) {
        this.source = source;
        this.sourceAddress = sourceAddress;
        this.headerEnforcementFilterFactory = headerEnforcementFilterFactory;
        this.inboundMonitor = inboundMonitor;
        this.messageForwarder = messageForwarder;
        this.errorForwarder = errorForwarder;
    }

    @Override
    public void init(final ProcessorContext context) {
        this.context = context;
    }

    /**
     * Takes incoming kafka messages and transforms them to an {@link ExternalMessage}.
     * Successfully transformed messages are forwarded to the {@link #messageForwarder}.
     * {@link DittoRuntimeException} resulting from transforming messages are forwarded to the {@link #errorForwarder}.
     *
     * @param key the key of the kafka record.
     * @param value the value (the message) of the kafka record.
     */
    @Override
    public void process(final String key, final String value) {
        final Map<String, String> messageHeaders = new HashMap<>();
        final Headers headers = context.headers();
        headers.forEach(header -> messageHeaders.put(header.key(), new String(header.value())));
        final String correlationId = messageHeaders
                .getOrDefault(DittoHeaderDefinition.CORRELATION_ID.getKey(), UUID.randomUUID().toString());
        try {
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

            inboundMonitor.success(externalMessage);
            context.forward(key, externalMessage, To.child(messageForwarder));
        } catch (final DittoRuntimeException e) {
            LOGGER.withCorrelationId(e)
                    .info("Got DittoRuntimeException '{}' when command was parsed: {}", e.getErrorCode(),
                            e.getMessage());
            inboundMonitor.failure(messageHeaders, e);
            context.forward(key, e.setDittoHeaders(DittoHeaders.of(messageHeaders)), To.child(errorForwarder));
        } catch (final Exception e) {
            inboundMonitor.exception(messageHeaders, e);
            LOGGER.withCorrelationId(correlationId)
                    .error(String.format("Unexpected {%s}: {%s}", e.getClass().getName(), e.getMessage()), e);
        }
    }

    @Override
    public void close() {
        this.context = null;
    }

}
