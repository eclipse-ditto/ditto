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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.eclipse.ditto.model.amqpbridge.InternalMessage;
import org.eclipse.ditto.model.amqpbridge.MappingContext;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapper;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapperFactory;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapperRegistry;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMappers;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;

import com.google.common.base.Converter;

import akka.actor.DynamicAccess;
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

    @Nullable
    private final Consumer<String> updateCorrelationId;

    private CommandProcessor(final MessageMapperRegistry registry, @Nullable final Consumer<String> updateCorrelationId) {
        this.registry = registry;
        this.updateCorrelationId = updateCorrelationId;
    }

    /**
     * Initializes a new command processor with mappers defined in mapping contexts. The dynamic access is needed
     * to instantiate message mappers for an actor system
     *
     * @param contexts the mapping contexts
     * @param access the dynamic access used for message mapper instantiation
     * @param logDebug a callback for debug logs (optional)
     * @param logWarning a callback for warning logs (optional)
     * @param updateCorrelationId a callback for correlation id changes
     * @return the processor instance
     */
    @Nonnull
    public static CommandProcessor from(final List<MappingContext> contexts, final DynamicAccess access,
            Consumer<String> logDebug, Consumer<String> logWarning, Consumer<String> updateCorrelationId) {
        MessageMapperRegistry registry =
                MessageMapperFactory.from(access, MessageMappers.class, logDebug, logWarning).loadRegistry(contexts);
        return new CommandProcessor(registry, updateCorrelationId);
    }

    /**
     * Returns all supported content types of the processor
     *
     * @return the content types
     */
    @Nonnull
    public List<String> getSupportedContentTypes() {
        return registry.stream().map(MessageMapper::getContentType).collect(Collectors.toList());
    }

    /**
     * Returns all supported content types of the processor
     *
     * @return the content types
     */
    public Optional<String> getDefaultContentType() {
        return Optional.ofNullable(registry.getDefaultMapper()).map(MessageMapper::getContentType);
    }

    /**
     * Processes a message to a command
     * @param message the message
     * @return the command
     * @throws RuntimeException if something went wrong
     */
    @Nullable
    public Command process(@Nullable final InternalMessage message) {
        if (Objects.isNull(message)) {
            return null;
        }

        final String correlationId = DittoHeaders.of(message.getHeaders()).getCorrelationId()
                .orElse("no-correlation-id");
        return doApplyTraced(
                () -> createProcessingContext(CONTEXT_NAME, correlationId),
                ctx -> convertMessage(message, ctx));
    }

    /**
     * Processes a response to a message
     * @param response the response
     * @return the message
     * @throws RuntimeException if something went wrong
     */
    @Nullable
    public InternalMessage process(@Nullable final CommandResponse response) {
        if (Objects.isNull(response)) {
            return null;
        }

        final String correlationId = DittoHeaders.of(response.getDittoHeaders()).getCorrelationId()
                .orElse("no-correlation-id");
        return doApplyTraced(
                () -> createProcessingContext(CONTEXT_NAME, correlationId),
                ctx -> convertResponse(response, ctx));
    }

    private Command<?> convertMessage(final InternalMessage message, final TraceContext ctx) {
        checkNotNull(message);
        checkNotNull(ctx);

        try {
            final Adaptable adaptable = doApplyTracedSegment(
                    () -> createSegment(ctx, MAPPING_SEGMENT_NAME, false),
                    () -> getConverter(message).convert(message)
            );

            doUpdateCorrelationId(adaptable);

            return doApplyTracedSegment(
                    () -> createSegment(ctx, PROTOCOL_SEGMENT_NAME, false),
                    () -> (Command) PROTOCOL_ADAPTER.fromAdaptable(adaptable)
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("Converting message failed: " + e.getMessage(), e);
        }
    }

    private InternalMessage convertResponse(final CommandResponse response, final TraceContext ctx) {
        checkNotNull(response);
        checkNotNull(ctx);

        try {
            final Adaptable adaptable = doApplyTracedSegment(
                    () -> createSegment(ctx, PROTOCOL_SEGMENT_NAME, true),
                    () -> PROTOCOL_ADAPTER.toAdaptable(response)
            );

            doUpdateCorrelationId(adaptable);

            return doApplyTracedSegment(
                    () -> createSegment(ctx, MAPPING_SEGMENT_NAME, true),
                    () -> getConverter(adaptable).convert(adaptable)
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("Converting response failed: " + e.getMessage(), e);
        }
    }

    private Converter<InternalMessage, Adaptable> getConverter(final InternalMessage message) {
        return registry.selectMapper(message).orElseThrow(
                () -> new IllegalArgumentException("No mapper found for message: " + message)
        );
    }

    private Converter<Adaptable, InternalMessage> getConverter(final Adaptable adaptable) {
        return registry.selectMapper(adaptable).orElseThrow(
                () -> new IllegalArgumentException("No mapper found for adaptable: " + adaptable)
        );
    }

    private void doUpdateCorrelationId(final Adaptable adaptable) {
        adaptable.getHeaders().map(DittoHeaders::getCorrelationId).map(Optional::get).ifPresent(s -> {
            if (Objects.nonNull(updateCorrelationId)) {
                updateCorrelationId.accept(s);
            }
        });
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

    private static <T> T doApplyTraced(Supplier<TraceContext> traceContextSupplier,
            Function<TraceContext, T> function) {
        TraceContext ctx = traceContextSupplier.get();
        try {
            T t = function.apply(ctx);
            ctx.finish();
            return t;
        } catch (Exception e) {
            ctx.finishWithError(e);
            throw e;
        }
    }

    private static <T> T doApplyTracedSegment(Supplier<Segment> segmentSupplier,
            Supplier<T> supplier) {
        Segment segment = segmentSupplier.get();
        try {
            T t = supplier.get();
            segment.finish();
            return t;
        } catch (Exception e) {
            segment.finishWithError(e);
            throw e;
        }
    }
}
