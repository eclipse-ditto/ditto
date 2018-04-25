/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.connectivity.messaging;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.connectivity.ExternalMessage;
import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.model.connectivity.MessageMappingFailedException;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.services.connectivity.mapping.DefaultMessageMapperFactory;
import org.eclipse.ditto.services.connectivity.mapping.DittoMessageMapper;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapper;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapperRegistry;
import org.eclipse.ditto.services.connectivity.mapping.MessageMappers;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.base.Signal;

import akka.actor.ActorSystem;
import akka.event.DiagnosticLoggingAdapter;
import kamon.Kamon;
import kamon.trace.Segment;
import kamon.trace.TraceContext;
import scala.Option;

/**
 * Processes incoming {@link ExternalMessage}s to {@link Signal}s and {@link Signal}s back to {@link ExternalMessage}s.
 * Encapsulates the message processing logic from the message mapping processor actor.
 */
public final class MessageMappingProcessor {

    private static final String INBOUND_MAPPING_TRACE_SUFFIX = ".inbound";
    private static final String OUTBOUND_MAPPING_TRACE_SUFFIX = ".outbound";
    private static final String SEGMENT_CATEGORY = "payload-mapping";
    private static final String MAPPING_SEGMENT_NAME = "mapping";
    private static final String PROTOCOL_SEGMENT_NAME = "protocol";
    private static final DittoProtocolAdapter PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();

    private final String connectionId;
    private final MessageMapperRegistry registry;
    private final DiagnosticLoggingAdapter log;

    private MessageMappingProcessor(final String connectionId, final MessageMapperRegistry registry,
            final DiagnosticLoggingAdapter log) {
        this.connectionId = connectionId;
        this.registry = registry;
        this.log = log;
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
        return new MessageMappingProcessor(connectionId, registry, log);
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

        final String correlationId = DittoHeaders.of(message.getHeaders()).getCorrelationId()
                .orElse("no-correlation-id");
        return doApplyTraced(
                () -> createProcessingContext(connectionId + INBOUND_MAPPING_TRACE_SUFFIX, correlationId),
                ctx -> convertMessage(message, ctx));
    }

    /**
     * Processes a Signal to an ExternalMessage.
     *
     * @param signal the signal
     * @return the message
     * @throws RuntimeException if something went wrong
     */
    public Optional<ExternalMessage> process(final Signal<?> signal) {

        final String correlationId = signal.getDittoHeaders().getCorrelationId().orElse("no-correlation-id");
        return doApplyTraced(
                () -> createProcessingContext(connectionId + OUTBOUND_MAPPING_TRACE_SUFFIX, correlationId),
                ctx -> convertToExternalMessage(() -> PROTOCOL_ADAPTER.toAdaptable(signal), ctx));
    }

    private Optional<Signal<?>> convertMessage(final ExternalMessage message, final TraceContext ctx) {
        checkNotNull(message);
        checkNotNull(ctx);

        try {
            final Optional<Adaptable> adaptableOpt = doApplyTracedSegment(
                    () -> createSegment(ctx, MAPPING_SEGMENT_NAME),
                    () -> getMapper(message).map(message)
            );

            return adaptableOpt.map(adaptable -> {
                doUpdateCorrelationId(adaptable);

                return doApplyTracedSegment(
                        () -> createSegment(ctx, PROTOCOL_SEGMENT_NAME),
                        () -> {
                            final Signal<?> signal = PROTOCOL_ADAPTER.fromAdaptable(adaptable);
                            final DittoHeadersBuilder dittoHeadersBuilder =
                                    DittoHeaders.newBuilder(message.getHeaders());
                            dittoHeadersBuilder.putHeaders(signal.getDittoHeaders());
                            return signal.setDittoHeaders(dittoHeadersBuilder.build());
                        }
                );
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
            final TraceContext ctx) {
        checkNotNull(adaptableSupplier);
        checkNotNull(ctx);

        try {
            final Adaptable adaptable = doApplyTracedSegment(
                    () -> createSegment(ctx, PROTOCOL_SEGMENT_NAME), adaptableSupplier);

            doUpdateCorrelationId(adaptable);

            return doApplyTracedSegment(
                    () -> createSegment(ctx, MAPPING_SEGMENT_NAME),
                    () -> getMapper(adaptable).map(adaptable)
            );
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

    private Segment createSegment(final TraceContext ctx, final String segmentName) {
        return ctx.startSegment(segmentName, SEGMENT_CATEGORY, SEGMENT_CATEGORY);
    }

    private static TraceContext createProcessingContext(final String name, @Nullable final String correlationId) {
        checkNotEmpty(name, "name");
        return Objects.isNull(correlationId) || correlationId.isEmpty() ?
                Kamon.tracer().newContext(name) :
                Kamon.tracer().newContext(name, Option.apply(correlationId));
    }

    private static <T> T doApplyTraced(final Supplier<TraceContext> traceContextSupplier,
            final Function<TraceContext, T> function) {
        final TraceContext ctx = traceContextSupplier.get();
        try {
            final T t = function.apply(ctx);
            ctx.finish();
            return t;
        } catch (final Exception e) {
            ctx.finishWithError(e);
            throw e;
        }
    }

    private static <T> T doApplyTracedSegment(final Supplier<Segment> segmentSupplier,
            final Supplier<T> supplier) {
        final Segment segment = segmentSupplier.get();
        try {
            final T t = supplier.get();
            segment.finish();
            return t;
        } catch (final Exception e) {
            segment.finishWithError(e);
            throw e;
        }
    }
}
