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
package org.eclipse.ditto.services.amqpbridge.messaging;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.amqpbridge.ExternalMessage;
import org.eclipse.ditto.model.amqpbridge.MappingContext;
import org.eclipse.ditto.model.amqpbridge.MessageMappingFailedException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.DefaultMessageMapperFactory;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.DittoMessageMapper;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapper;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapperRegistry;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMappers;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.events.base.Event;

import akka.actor.DynamicAccess;
import akka.event.DiagnosticLoggingAdapter;
import kamon.Kamon;
import kamon.trace.Segment;
import kamon.trace.TraceContext;
import scala.Option;

/**
 * Processes incoming bridge messages to commands and command responses to bridge messages.
 * This command processor encapsulates the message processing logic from the command processor actor.
 */
public final class CommandProcessor {

    private static final String CONTEXT_NAME = CommandProcessor.class.getSimpleName();
    private static final String SEGMENT_CATEGORY = "payload-mapping";
    private static final String MAPPING_SEGMENT_NAME = "mapping";
    private static final String PROTOCOL_SEGMENT_NAME = "protocoladapter";

    private static final DittoProtocolAdapter PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();

    private final MessageMapperRegistry registry;

    private final DiagnosticLoggingAdapter log;


    private CommandProcessor(final MessageMapperRegistry registry, final DiagnosticLoggingAdapter log) {
        this.registry = registry;
        this.log = log;
    }

    /**
     * Initializes a new command processor with mappers defined in mapping contexts. The dynamic access is needed
     * to instantiate message mappers for an actor system
     *
     * @param contexts the mapping contexts
     * @param access the dynamic access used for message mapper instantiation
     * @param log the log adapter
     * @return the processor instance
     */
    public static CommandProcessor of(final List<MappingContext> contexts, final DynamicAccess access,
            final DiagnosticLoggingAdapter log) {
        final MessageMapperRegistry registry = DefaultMessageMapperFactory.of(access, MessageMappers.class, log)
                .registryOf(DittoMessageMapper.CONTEXT, contexts);
        return new CommandProcessor(registry, log);
    }

    /**
     * Returns all supported content types of the processor
     *
     * @return the content types
     */
    public List<String> getSupportedContentTypes() {
        return registry.getMappers().stream()
                .map(MessageMapper::getContentType)
                .collect(Collectors.toList());
    }

    /**
     * Returns the content type of the default mapper.
     *
     * @return the default content type
     */
    public String getDefaultContentType() {
        return registry.getDefaultMapper().getContentType();
    }

    /**
     * Processes a message to a command
     *
     * @param message the message
     * @return the command
     * @throws RuntimeException if something went wrong
     */
    public Command process(final ExternalMessage message) {

        final String correlationId = DittoHeaders.of(message.getHeaders()).getCorrelationId()
                .orElse("no-correlation-id");
        return doApplyTraced(
                () -> createProcessingContext(CONTEXT_NAME, correlationId),
                ctx -> convertMessage(message, ctx));
    }

    /**
     * Processes a response to a message
     *
     * @param response the response
     * @return the message
     * @throws RuntimeException if something went wrong
     */
    public ExternalMessage process(final CommandResponse response) {

        final String correlationId = response.getDittoHeaders().getCorrelationId().orElse("no-correlation-id");
        return doApplyTraced(
                () -> createProcessingContext(CONTEXT_NAME, correlationId),
                ctx -> convertToExternalMessage(() -> PROTOCOL_ADAPTER.toAdaptable(response), ctx));
    }

    /**
     * Processes an event to a message
     *
     * @param event the event
     * @return the message
     * @throws RuntimeException if something went wrong
     */
    public ExternalMessage process(final Event event) {

        final String correlationId = event.getDittoHeaders().getCorrelationId().orElse("no-correlation-id");
        return doApplyTraced(
                () -> createProcessingContext(CONTEXT_NAME, correlationId),
                ctx -> convertToExternalMessage(() -> PROTOCOL_ADAPTER.toAdaptable(event), ctx));
    }

    private Command<?> convertMessage(final ExternalMessage message, final TraceContext ctx) {
        checkNotNull(message);
        checkNotNull(ctx);

        try {
            final Adaptable adaptable = doApplyTracedSegment(
                    () -> createSegment(ctx, MAPPING_SEGMENT_NAME, false),
                    () -> getMapper(message).map(message)
            );

            doUpdateCorrelationId(adaptable);

            return doApplyTracedSegment(
                    () -> createSegment(ctx, PROTOCOL_SEGMENT_NAME, false),
                    () -> {
                        final Command command = (Command) PROTOCOL_ADAPTER.fromAdaptable(adaptable);
                        final DittoHeadersBuilder dittoHeadersBuilder = DittoHeaders.newBuilder(message.getHeaders());
                        dittoHeadersBuilder.putHeaders(command.getDittoHeaders());
                        return command.setDittoHeaders(dittoHeadersBuilder.build());
                    }
            );
        } catch (final DittoRuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw MessageMappingFailedException.newBuilder(message.findContentType().orElse(""))
                    .description("Could not map ExternalMessage due to unknown problem: " +
                            e.getClass().getSimpleName() + " " + e.getMessage())
                    .cause(e)
                    .build();
        }
    }

    private ExternalMessage convertToExternalMessage(final Supplier<Adaptable> adaptableSupplier,
            final TraceContext ctx) {
        checkNotNull(adaptableSupplier);
        checkNotNull(ctx);

        try {
            final Adaptable adaptable = doApplyTracedSegment(
                    () -> createSegment(ctx, PROTOCOL_SEGMENT_NAME, true), adaptableSupplier);

            doUpdateCorrelationId(adaptable);

            return doApplyTracedSegment(
                    () -> createSegment(ctx, MAPPING_SEGMENT_NAME, true),
                    () -> getMapper(adaptable).map(adaptable)
            );
        } catch (final DittoRuntimeException e) {
            throw e;
        } catch (final Exception e) {
            final String contentType = adaptableSupplier.get()
                    .getHeaders()
                    .map(h -> h.get(ExternalMessage.CONTENT_TYPE_HEADER))
                    .orElse("");
            throw MessageMappingFailedException.newBuilder(contentType)
                    .description("Could not map Adaptable due to unknown problem: " + e.getMessage())
                    .cause(e)
                    .build();
        }
    }

    private MessageMapper getMapper(final ExternalMessage message) {

        final Optional<String> contentTypeOpt = message.findContentType();
        if (contentTypeOpt.isPresent()) {
            final String contentType = contentTypeOpt.get();
            return registry.selectMapper(contentType);
        } else {
            return registry.getDefaultMapper();
        }
    }

    private MessageMapper getMapper(final Adaptable adaptable) {

        final Optional<String> acceptHeaderOpt = adaptable.getHeaders()
                .map(m -> m.get(ExternalMessage.ACCEPT_HEADER));
        final Optional<String> contentTypeOpt = adaptable.getHeaders()
                .map(m -> m.get(ExternalMessage.CONTENT_TYPE_HEADER));
        if (acceptHeaderOpt.isPresent()) {
            final String acceptHeader = acceptHeaderOpt.get();
            log.info("Selecting MessageMapper based on <{}> header <{}> for mapping back Adaptable on " +
                    "topic <{}>", ExternalMessage.ACCEPT_HEADER, acceptHeader, adaptable.getTopicPath().getPath());
            return registry.selectMapper(acceptHeader);
        } else if (contentTypeOpt.isPresent()) {
            final String contentType = contentTypeOpt.get();
            log.info("Selecting MessageMapper based on <{}> header <{}> for mapping back Adaptable on " +
                    "topic <{}>", ExternalMessage.CONTENT_TYPE_HEADER, contentType, adaptable.getTopicPath().getPath());
            return registry.selectMapper(contentType);
        } else {
            log.info("No header <{}> was set, so using the default MessageMapper for mapping back Adaptable on " +
                            "topic <{}>", ExternalMessage.ACCEPT_HEADER, adaptable.getTopicPath().getPath());
            return registry.getDefaultMapper();
        }
    }

    private void doUpdateCorrelationId(final Adaptable adaptable) {
        adaptable.getHeaders().flatMap(DittoHeaders::getCorrelationId)
                .ifPresent(s -> LogUtil.enhanceLogWithCorrelationId(log, s));
    }

    private Segment createSegment(final TraceContext ctx, final String name, final boolean isReverse) {
        final String segmentName = isReverse ? name.concat("-reverse") : name;
        final String library = isReverse ? CONTEXT_NAME.concat("Reverse") : CONTEXT_NAME;
        return ctx.startSegment(segmentName, SEGMENT_CATEGORY, library);
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
