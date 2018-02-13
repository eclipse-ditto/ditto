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


import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.jms.BytesMessage;
import javax.jms.TextMessage;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.amqpbridge.MappingContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.common.DittoConstants;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.DittoProtocolMapper;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.PayloadMapper;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.PayloadMapperFactory;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.PayloadMapperMessage;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.PayloadMappers;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.PayloadMappingException;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
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
    static final String ACTOR_NAME_PREFIX = "amqpCommandProcessor-";

    private static final DittoProtocolAdapter PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef pubSubMediator;
    private final String pubSubTargetActorPath;
    private final AuthorizationSubject authorizationSubject;
    private final Cache<String, TraceContext> traces;

    private final PayloadMapper defaultMapper;
    private final Map<String, PayloadMapper> payloadMappers;

    private CommandProcessorActor(final ActorRef pubSubMediator, final String pubSubTargetActorPath,
            final AuthorizationSubject authorizationSubject, final List<MappingContext> mappingContexts) {
        this.pubSubMediator = pubSubMediator;
        this.pubSubTargetActorPath = pubSubTargetActorPath;
        this.authorizationSubject = authorizationSubject;
        traces = CacheBuilder.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .removalListener((RemovalListener<String, TraceContext>) notification
                        -> log.info("Trace for {} expired.", notification.getKey()))
                .build();

        defaultMapper = new DittoProtocolMapper(true);

        final PayloadMapperFactory mapperFactory = new PayloadMapperFactory(
                (ExtendedActorSystem) getContext().getSystem(), PayloadMappers.class);
        payloadMappers = loadPayloadMappers(mapperFactory, mappingContexts);
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param pubSubMediator the akka pubsub mediator actor.
     * @param pubSubTargetActorPath the path of the command consuming actor (via pubsub).
     * @param authorizationSubject the authorized subject that are set in command headers.
     * @return the Akka configuration Props object
     */
    static Props props(final ActorRef pubSubMediator, final String pubSubTargetActorPath,
            final AuthorizationSubject authorizationSubject, final List<MappingContext> mappingContexts) {

        return Props.create(CommandProcessorActor.class, new Creator<CommandProcessorActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public CommandProcessorActor create() {
                return new CommandProcessorActor(pubSubMediator, pubSubTargetActorPath, authorizationSubject,
                        mappingContexts);
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

        final TraceContext traceContext = Kamon.tracer().newContext("commandProcessor",
                Option.apply(DittoHeaders.of(m.getHeaders()).getCorrelationId().orElse("no-correlation-id")));
        final Optional<Command<?>> command = parseMessage(m, traceContext);
        traceContext.finish();

        command.ifPresent(c -> {
            traceCommand(c);
            log.info("Publishing '{}' to '{}'", c.getType(), pubSubTargetActorPath);
            pubSubMediator.tell(new DistributedPubSubMediator.Send(pubSubTargetActorPath, command, true),
                    getSelf());
        });
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

        // TODO map back

        if (response.getStatusCodeValue() < HttpStatusCode.BAD_REQUEST.toInt()) {
            log.debug("Received response: {}", response);
        } else {
            log.info("Received error response: {}", response);
        }

        response.getDittoHeaders().getCorrelationId().ifPresent(cid -> {
            final TraceContext traceContext = traces.getIfPresent(cid);
            traces.invalidate(cid);
            if (traceContext != null) {
                traceContext.finish();
            } else {
                log.info("Trace missing for response: '{}'", response);
            }
        });
    }

    private void traceCommand(final Command<?> command) {
        command.getDittoHeaders().getCorrelationId().ifPresent(correlationId -> {
            final Option<String> token = Option.apply(correlationId);
            final TraceContext traceContext = Kamon.tracer().newContext("roundtrip.amqp_" + command.getType(), token);
            traceContext.addMetadata("command", command.getType());
            traces.put(correlationId, traceContext);
        });
    }


    private Adaptable mapPayloadToAdaptable(final PayloadMapperMessage message, final Segment segment) {
        final PayloadMapper mapper = message.getContentType().map(payloadMappers::get).orElseGet(() -> {
            log.warning("Either message has no content-type or no mapper is configured for content-type. Using " +
                    "default mapper <{}>", defaultMapper);
            return defaultMapper;
        });


        try {
            final Adaptable adaptable = mapTraced(mapper::mapIncoming, message, segment);
            log.debug("Successfully mapped message with content-type <{}> to <{}> using mapper <{}>", message,
                    adaptable, mapper.getClass().getSimpleName());
        } catch (Exception e) {
            log.info("Mapping failed: <{}>", e.getMessage());
        }
        return null;
    }


    private Command<?> mapAdaptableToCommand(final Adaptable adaptable, final Segment segment) {
        try {
            return mapTraced(a -> (Command<?>) PROTOCOL_ADAPTER.fromAdaptable(a), adaptable, segment);
        } catch (Exception e) {
            log.info("Parsing command from adaptible failed: <{}>", e.getMessage());
        }
        return null;
    }
    

    private static <T, R> R mapTraced(Function<T, R> mapping, T t, final Segment segment) throws Exception {
        try {
            final R r = mapping.apply(t);
            segment.finish();
            return r;
        } catch (Exception e) {
            segment.finishWithError(e);
            throw e;
        }
    }


    private Optional<Command<?>> parseMessage(final InternalMessage message, final TraceContext traceContext) {
        DittoHeaders headers = DittoHeaders.of(message.getHeaders());
        headers.getCorrelationId().ifPresent(s -> LogUtil.enhanceLogWithCorrelationId(log, s));

        return Optional.of(message)
                .map(InternalMessage::toPayloadMapperMessage)
                .map(m -> this.mapPayloadToAdaptable(m,
                        traceContext.startSegment("mapping", "payload-mapping", "commandProcessor")))
                .map(ProtocolFactory::wrapAsJsonifiableAdaptable)
                .map(a -> this.mapAdaptableToCommand(a,
                        traceContext.startSegment("protocoladapter", "payload-mapping", "commandProcessor")))
                .map(a -> {
                    // use correlationId from json payload if present
                    // TODO DG rly required??
                    a.getDittoHeaders().getCorrelationId().ifPresent(s -> LogUtil.enhanceLogWithCorrelationId(log, s));
                    return a;
                })
                .map(c -> c.setDittoHeaders(headers));
    }


    private Map<String, PayloadMapper> loadPayloadMappers(final PayloadMapperFactory factory,
            final List<MappingContext> mappingContexts) {
        return mappingContexts.stream().collect(Collectors.toMap(MappingContext::getContentType, mappingContext -> {
            try {
                final Optional<PayloadMapper> mapper = factory.findAndCreateInstanceFor(mappingContext);
                if (!mapper.isPresent()) {
                    log.debug("No PayloadMapper found for context: <{}>", mappingContext);
                    return null;
                }
                return mapper.get();
            } catch (InvocationTargetException | IllegalAccessException | ClassCastException | InstantiationException e) {
                log.error(e, "Could not initialize PayloadMapper: <{}>", e.getMessage());
                return null;
            }
        }));
    }
}
