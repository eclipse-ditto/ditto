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


import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.services.models.amqpbridge.AmqpBridgeMessagingConstants.GATEWAY_PROXY_ACTOR_PATH;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.amqpbridge.InternalMessage;
import org.eclipse.ditto.model.amqpbridge.MappingContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapper;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapperFactory;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapperRegistry;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMappers;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;

import com.google.common.base.Converter;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.DynamicAccess;
import akka.actor.ExtendedActorSystem;
import akka.actor.Props;
import akka.actor.Status;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import kamon.Kamon;
import kamon.trace.Segment;
import kamon.trace.TraceContext;
import scala.Option;

/**
 * This Actor processes incoming {@link Command}s and dispatches them via {@link DistributedPubSubMediator} to a
 * consumer actor.
 */
public final class CommandProcessorActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME_PREFIX = "amqpCommandProcessor-";

    private static final DittoProtocolAdapter PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();

    private static final Converter<Adaptable, Command<?>> ADAPTABLE_COMMAND_CONVERTER = Converter.from(
            a -> (Command<?>) PROTOCOL_ADAPTER.fromAdaptable(a),
            PROTOCOL_ADAPTER::toAdaptable
    );

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef pubSubMediator;
    private final AuthorizationSubject authorizationSubject;
    private final Cache<String, TraceContext> traces;

    private final MessageMapperRegistry registry;

    private CommandProcessorActor(final ActorRef pubSubMediator, final AuthorizationSubject authorizationSubject,
            final List<MappingContext> mappingContexts) {
        this.pubSubMediator = pubSubMediator;
        this.authorizationSubject = authorizationSubject;
        traces = CacheBuilder.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .removalListener((RemovalListener<String, TraceContext>) notification
                        -> log.info("Trace for {} expired.", notification.getKey()))
                .build();

        this.registry = MessageMapperFactory.from(getDynamicAccess(), MessageMappers.class, log)
                .loadRegistry(mappingContexts);

        log.info("Configured for processing messages with the following content types: {}",
                registry.stream().map(MessageMapper::getContentType).collect(Collectors.toList()));

        final MessageMapper defaultMapper = registry.getDefaultMapper();
        if (Objects.nonNull(defaultMapper)) {
            log.info("Interpreting messages with missing content type as '{}'",
                    defaultMapper.getContentType());
        } else {
            log.warning("No default mapper configured!");
        }
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param pubSubMediator the akka pubsub mediator actor.
     * @param authorizationSubject the authorized subject that are set in command headers.
     * @return the Akka configuration Props object
     */
    static Props props(final ActorRef pubSubMediator, final AuthorizationSubject authorizationSubject,
            final List<MappingContext> mappingContexts) {

        return Props.create(CommandProcessorActor.class, new Creator<CommandProcessorActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public CommandProcessorActor create() {
                return new CommandProcessorActor(pubSubMediator, authorizationSubject, mappingContexts);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(InternalMessage.class, this::handle)
                .match(CommandResponse.class, this::handleCommandResponse)
                .match(DittoRuntimeException.class, this::handleDittoRuntimeException)
                .match(Status.Failure.class, f -> log.error(f.cause(), "Got an unexpected failure."))
                .matchAny(m -> {
                    log.debug("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private void handle(final InternalMessage m) {
        final String correlationId = DittoHeaders.of(m.getHeaders()).getCorrelationId().orElse("no-correlation-id");
        LogUtil.enhanceLogWithCorrelationId(log, correlationId);

        try {
            final Command<?> command =
                    doApplyTraced(() -> createProcessingContext(correlationId), (ctx -> convertMessage(m, ctx)));

            startTrace(command);
            log.info("Publishing '{}' to '{}'", command.getType(), GATEWAY_PROXY_ACTOR_PATH);
            pubSubMediator.tell(new DistributedPubSubMediator.Send(GATEWAY_PROXY_ACTOR_PATH, command, true),
                    getSelf());
        } catch (Exception e) {
            log.info(e.getMessage());
        }


    }

    private void handleDittoRuntimeException(final DittoRuntimeException exception) {
        final ThingErrorResponse errorResponse = ThingErrorResponse.of(exception);

        logDittoRuntimeException(exception);
        handleCommandResponse(errorResponse);
    }

    private void logDittoRuntimeException(final DittoRuntimeException exception) {
        LogUtil.enhanceLogWithCorrelationId(log, exception);

        final String msgTemplate = "Got DittoRuntimeException '{}' when command via AMQP was processed: {}";
        log.info(msgTemplate, exception.getErrorCode(), exception.getMessage());
    }

    private void handleCommandResponse(final CommandResponse response) {
        LogUtil.enhanceLogWithCorrelationId(log, response);

        if (response.getStatusCodeValue() < HttpStatusCode.BAD_REQUEST.toInt()) {
            log.debug("Received response: {}", response);
        } else {
            log.info("Received error response: {}", response);
        }

        try {
            final InternalMessage message = convertResponse(response);
            //TODO send message back to command consumer actor
            finishTrace(response);
        } catch (Exception e) {
            log.info(e.getMessage());
            finishTrace(response, e);
        }
    }

    private void startTrace(final Command<?> command) {
        command.getDittoHeaders().getCorrelationId().ifPresent(correlationId ->
            traces.put(correlationId, createRoundtripContext(correlationId, command.getType()))
        );
    }

    private void finishTrace(final CommandResponse response) {
        finishTrace(response, null);
    }

    private void finishTrace(final CommandResponse response, @Nullable Throwable cause) {
        response.getDittoHeaders().getCorrelationId().ifPresent(correlationId -> {
            try {
                finishTrace(correlationId, cause);
            } catch (IllegalArgumentException e) {
                log.info("Trace missing for response: '{}'", response);
            }
        });
    }

    private void finishTrace(String correlationId, @Nullable Throwable cause) {
        final TraceContext ctx = traces.getIfPresent(correlationId);
        if (Objects.isNull(ctx)) {
            throw new IllegalArgumentException("No trace found for correlationId: " + correlationId);
        }
        traces.invalidate(ctx);
        if (Objects.isNull(cause)) {
            ctx.finish();
        } else {
            ctx.finishWithError(cause);
        }
    }

    private Command<?> convertMessage(final InternalMessage message, final TraceContext ctx) {
        checkNotNull(message);
        checkNotNull(ctx);

        DittoHeaders headers = DittoHeaders.of(message.getHeaders());

        try {
            final Adaptable adaptable = doApplyTracedSegment(
                    () -> createMessageInSegment(ctx),
                    () -> getConverter(message).convert(message)
            );

            // use headers from adaptable if present
            headers = adaptable.getHeaders().orElse(headers);
            headers.getCorrelationId().ifPresent(s -> LogUtil.enhanceLogWithCorrelationId(log, s));

            return doApplyTracedSegment(
                    () -> createProtocolInSegment(ctx),
                    () -> ADAPTABLE_COMMAND_CONVERTER.convert(adaptable)
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("Converting message failed: " + e.getMessage(), e);
        }
    }

    private InternalMessage convertResponse(final CommandResponse response) {
        try {
            final Adaptable adaptable = ProtocolFactory.jsonifiableAdaptableFromJson(response.toJson());
            return getConverter(adaptable).convert(adaptable);
        } catch (Exception e) {
            throw new IllegalArgumentException("Converting adaptable failed: " + e.getMessage(), e);
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

    /**
     * Shortcut to the dynamic access object
     *
     * @return the dynamic access object
     */
    private DynamicAccess getDynamicAccess() {
        return ((ExtendedActorSystem) getContext().getSystem()).dynamicAccess();
    }

    private static TraceContext createRoundtripContext(final String correlationId, final String type) {
        final Option<String> token = Option.apply(correlationId);
        final TraceContext ctx = Kamon.tracer().newContext("roundtrip.amqp_" + type, token);
        ctx.addMetadata("command", type);
        return ctx;
    }

    private static TraceContext createProcessingContext(@Nullable final String correlationId) {
        return Objects.isNull(correlationId) || correlationId.isEmpty() ?
                Kamon.tracer().newContext("commandProcessor") :
                Kamon.tracer().newContext("commandProcessor", Option.apply(correlationId));
    }

    private static Segment createMessageInSegment(final TraceContext ctx) {
        return ctx.startSegment("mapping", "payload-mapping", "commandProcessor");
    }

    private static Segment createProtocolInSegment(final TraceContext ctx) {
        return ctx.startSegment("protocoladapter", "payload-mapping", "commandProcessor");
    }


//    kamon helpers

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
