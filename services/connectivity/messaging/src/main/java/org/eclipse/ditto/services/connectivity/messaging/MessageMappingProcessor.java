/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ExternalMessage;
import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.model.connectivity.MessageMappingFailedException;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.ProtocolAdapter;
import org.eclipse.ditto.services.connectivity.mapping.DefaultMessageMapperFactory;
import org.eclipse.ditto.services.connectivity.mapping.DittoMessageMapper;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapper;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapperRegistry;
import org.eclipse.ditto.services.connectivity.mapping.MessageMappers;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.services.utils.protocol.ProtocolConfigReader;
import org.eclipse.ditto.services.utils.tracing.TracingTags;
import org.eclipse.ditto.signals.base.Signal;

import akka.actor.ActorSystem;
import akka.event.DiagnosticLoggingAdapter;

/**
 * Processes incoming {@link ExternalMessage}s to {@link Signal}s and {@link Signal}s back to {@link ExternalMessage}s.
 * Encapsulates the message processing logic from the message mapping processor actor.
 */
public final class MessageMappingProcessor {

    private static final String TIMER_NAME = "connectivity_message_mapping";
    private static final String INBOUND = "inbound";
    private static final String OUTBOUND = "outbound";
    private static final String PAYLOAD_SEGMENT_NAME = "payload";
    private static final String PROTOCOL_SEGMENT_NAME = "protocol";
    private static final String DIRECTION_TAG_NAME = "direction";

    private final String connectionId;
    private final MessageMapperRegistry registry;
    private final DiagnosticLoggingAdapter log;
    private final ProtocolAdapter protocolAdapter;

    private MessageMappingProcessor(final String connectionId, final MessageMapperRegistry registry,
            final DiagnosticLoggingAdapter log, final ProtocolAdapter protocolAdapter) {
        this.connectionId = connectionId;
        this.registry = registry;
        this.log = log;
        this.protocolAdapter = protocolAdapter;
    }

    /**
     * Initializes a new command processor with mappers defined in mapping mappingContext. The dynamic access is needed
     * to instantiate message mappers for an actor system
     *
     * @param mappingContext the mapping Context
     * @param actorSystem the dynamic access used for message mapper instantiation
     * @param log the log adapter
     * @return the processor instance
     * @throws org.eclipse.ditto.model.connectivity.MessageMapperConfigurationInvalidException if the configuration of
     * one of the {@code mappingContext} is invalid
     * @throws org.eclipse.ditto.model.connectivity.MessageMapperConfigurationFailedException if the configuration of
     * one of the {@code mappingContext} failed for a mapper specific reason
     */
    public static MessageMappingProcessor of(final String connectionId, @Nullable final MappingContext mappingContext,
            final ActorSystem actorSystem, final DiagnosticLoggingAdapter log) {
        final MessageMapperRegistry registry = DefaultMessageMapperFactory.of(actorSystem, MessageMappers.class, log)
                .registryOf(DittoMessageMapper.CONTEXT, mappingContext);
        final ProtocolConfigReader protocolConfigReader =
                ProtocolConfigReader.fromRawConfig(actorSystem.settings().config());
        final ProtocolAdapter protocolAdapter =
                protocolConfigReader.loadProtocolAdapterProvider(actorSystem).getProtocolAdapter(null);
        return new MessageMappingProcessor(connectionId, registry, log, protocolAdapter);
    }

    /**
     * @return the message mapper registry to use for mapping messages.
     */
    MessageMapperRegistry getRegistry() {
        return registry;
    }

    /**
     * Processes an ExternalMessage to a Signal.
     *
     * @param message the message
     * @return the signal
     * @throws RuntimeException if something went wrong
     */
    public Optional<Signal<?>> process(final ExternalMessage message) {
        final StartedTimer overAllProcessingTimer = startNewTimer().tag(DIRECTION_TAG_NAME, INBOUND);
        return withTimer(overAllProcessingTimer, () -> convertMessage(message, overAllProcessingTimer));
    }

    /**
     * Processes a Signal to an ExternalMessage.
     *
     * @param signal the signal
     * @return the message
     * @throws RuntimeException if something went wrong
     */
    public Optional<ExternalMessage> process(final Signal<?> signal) {
        final StartedTimer overAllProcessingTimer = startNewTimer().tag(DIRECTION_TAG_NAME, OUTBOUND);
        return withTimer(overAllProcessingTimer,
                () -> convertToExternalMessage(() -> protocolAdapter.toAdaptable(signal), overAllProcessingTimer));
    }

    private Optional<Signal<?>> convertMessage(final ExternalMessage message,
            final StartedTimer overAllProcessingTimer) {
        checkNotNull(message);

        try {
            final Optional<Adaptable> adaptableOpt = withTimer(
                    overAllProcessingTimer.startNewSegment(PAYLOAD_SEGMENT_NAME),
                    () -> getMapper(message).map(message));

            return adaptableOpt.map(adaptable -> {
                doUpdateCorrelationId(adaptable);

                return this.<Signal<?>>withTimer(
                        overAllProcessingTimer.startNewSegment(PROTOCOL_SEGMENT_NAME),
                        () -> protocolAdapter.fromAdaptable(adaptable));
            });
        } catch (final DittoRuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw MessageMappingFailedException.newBuilder(message.findContentType().orElse(""))
                    .description("Could not map ExternalMessage due to unknown problem: " +
                            e.getClass().getSimpleName() + " " + e.getMessage())
                    .dittoHeaders(DittoHeaders.of(message.getHeaders()))
                    .cause(e)
                    .build();
        }
    }

    private Optional<ExternalMessage> convertToExternalMessage(final Supplier<Adaptable> adaptableSupplier,
            final StartedTimer overAllProcessingTimer) {
        checkNotNull(adaptableSupplier);

        try {
            final Adaptable adaptable =
                    withTimer(overAllProcessingTimer.startNewSegment(PROTOCOL_SEGMENT_NAME), adaptableSupplier);

            doUpdateCorrelationId(adaptable);

            return withTimer(overAllProcessingTimer.startNewSegment(PAYLOAD_SEGMENT_NAME),
                    () -> getMapper(adaptable).map(adaptable));
        } catch (final DittoRuntimeException e) {
            throw e;
        } catch (final Exception e) {
            final Optional<DittoHeaders> headers = adaptableSupplier.get()
                    .getHeaders();
            final String contentType = headers
                    .map(h -> h.get(ExternalMessage.CONTENT_TYPE_HEADER))
                    .orElse("");
            throw MessageMappingFailedException.newBuilder(contentType)
                    .description("Could not map Adaptable due to unknown problem: " + e.getMessage())
                    .dittoHeaders(headers.orElseGet(DittoHeaders::empty))
                    .cause(e)
                    .build();
        }
    }

    private MessageMapper getMapper(final ExternalMessage message) {

        LogUtil.enhanceLogWithCorrelationId(log, message.getHeaders().get("correlation-id"));
        LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId);

        final Optional<String> contentTypeOpt = message.findContentType();
        if (contentTypeOpt.isPresent()) {
            final String contentType = contentTypeOpt.get();
            if (registry.getDefaultMapper().getContentType().filter(contentType::equals).isPresent()) {
                log.info("Selected Default MessageMapper for mapping ExternalMessage as content-type matched <{}>",
                        contentType);
                return registry.getDefaultMapper();
            }
        }

        return registry.getMapper().orElseGet(() -> {
            log.debug("Falling back to Default MessageMapper for mapping ExternalMessage " +
                    "as no MessageMapper was present: {}", message);
            return registry.getDefaultMapper();
        });

    }

    private MessageMapper getMapper(final Adaptable adaptable) {

        doUpdateCorrelationId(adaptable);
        return registry.getMapper().orElseGet(() -> {
            log.debug("Falling back to Default MessageMapper for mapping Adaptable as no MessageMapper was present: {}",
                    adaptable);
            return registry.getDefaultMapper();
        });
    }

    private void doUpdateCorrelationId(final Adaptable adaptable) {
        adaptable.getHeaders().flatMap(DittoHeaders::getCorrelationId)
                .ifPresent(s -> LogUtil.enhanceLogWithCorrelationId(log, s));
        LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId);
    }

    private <T> T withTimer(final StartedTimer timer, final Supplier<T> supplier) {
        try {
            final T result = supplier.get();
            timer.tag(TracingTags.MAPPING_SUCCESS, true)
                    .stop();
            return result;
        } catch (final Exception ex) {
            timer.tag(TracingTags.MAPPING_SUCCESS, false)
                    .stop();
            throw ex;
        }
    }

    private StartedTimer startNewTimer() {
        return DittoMetrics
                .expiringTimer(TIMER_NAME)
                .tag(TracingTags.CONNECTION_ID, connectionId)
                .expirationHandling(expiredTimer -> expiredTimer.tag(TracingTags.MAPPING_SUCCESS, false))
                .build();
    }
}
