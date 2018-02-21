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

public class CommandProcessor {

    private static final String CONTEXT_NAME = CommandProcessor.class.getSimpleName();
    private static final String SEGMENT_CATEGORY = "payload-mapping";
    private static final String MAPPING_SEGMENT_NAME = "mapping";
    private static final String PROTOCOL_SEGMENT_NAME = "protocoladapter";

    private static final DittoProtocolAdapter PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();
    private static final Converter<Adaptable, Command> ADAPTABLE_COMMAND_CONVERTER = Converter.from(
            a -> (Command) PROTOCOL_ADAPTER.fromAdaptable(a),
            PROTOCOL_ADAPTER::toAdaptable
    );
    private static final Converter<CommandResponse, Adaptable> COMMAND_RESPONSE_ADAPTABLE_CONVERTER = Converter.from(
            PROTOCOL_ADAPTER::toAdaptable,
            a -> (CommandResponse) PROTOCOL_ADAPTER.fromAdaptable(a)
    );

    private final MessageMapperRegistry registry;
    private final Consumer<String> updateCorrelationId;

    private CommandProcessor(final MessageMapperRegistry registry, final Consumer<String> updateCorrelationId) {
        this.registry = registry;
        this.updateCorrelationId = updateCorrelationId;
    }

    public static CommandProcessor from(final List<MappingContext> contexts, final DynamicAccess access,
            Consumer<String> logDebug, Consumer<String> logWarning, Consumer<String> updateCorrelationId) {
        MessageMapperRegistry registry =
                MessageMapperFactory.from(access, MessageMappers.class, logDebug, logWarning).loadRegistry(contexts);
        return new CommandProcessor(registry, updateCorrelationId);
    }

    public List<String> getSupportedContentTypes() {
        return registry.stream().map(MessageMapper::getContentType).collect(Collectors.toList());
    }

    public Optional<String> getDefaultContentType() {
        return Optional.ofNullable(registry.getDefaultMapper()).map(MessageMapper::getContentType);
    }


    public Command process(final InternalMessage message) {
        final String correlationId = DittoHeaders.of(message.getHeaders()).getCorrelationId()
                .orElse("no-correlation-id");
        return doApplyTraced(
                () -> createProcessingContext(CONTEXT_NAME, correlationId),
                ctx -> convertMessage(message, ctx));
    }

    public InternalMessage process(final CommandResponse response) {
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
                    () -> ADAPTABLE_COMMAND_CONVERTER.convert(adaptable)
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
                    () -> COMMAND_RESPONSE_ADAPTABLE_CONVERTER.convert(response)
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
        adaptable.getHeaders().map(DittoHeaders::getCorrelationId).map(Optional::get).ifPresent(updateCorrelationId);
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
