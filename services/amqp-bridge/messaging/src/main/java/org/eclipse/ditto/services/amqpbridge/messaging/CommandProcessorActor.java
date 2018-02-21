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


import static org.eclipse.ditto.services.models.amqpbridge.AmqpBridgeMessagingConstants.GATEWAY_PROXY_ACTOR_PATH;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.model.amqpbridge.InternalMessage;
import org.eclipse.ditto.model.amqpbridge.MappingContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;

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

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef pubSubMediator;
    private final ActorRef commandProducer;
    private final AuthorizationSubject authorizationSubject;
    private final Cache<String, TraceContext> traces;

    private final CommandProcessor processor;

    private CommandProcessorActor(final ActorRef pubSubMediator, final ActorRef commandProducer,
            final AuthorizationSubject authorizationSubject,
            final List<MappingContext> mappingContexts) {
        this.pubSubMediator = pubSubMediator;
        this.commandProducer = commandProducer;
        this.authorizationSubject = authorizationSubject;
        traces = CacheBuilder.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .removalListener((RemovalListener<String, TraceContext>) notification
                        -> log.debug("Trace for {} removed.", notification.getKey()))
                .build();

        this.processor = CommandProcessor.from(mappingContexts, getDynamicAccess(), log::debug, log::warning,
                this::updateCorrelationId);

        log.info("Configured for processing messages with the following content types: {}",
                processor.getSupportedContentTypes());

        Optional<String> defaultContentType = processor.getDefaultContentType();
        if (defaultContentType.isPresent()) {
            log.info("Interpreting messages with missing content type as '{}'", defaultContentType.get());
        } else {
            log.warning("No default config type configured!");
        }
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param pubSubMediator the akka pubsub mediator actor.
     * @param commandProducer actor that handles outgoing messages
     * @param authorizationSubject the authorized subject that are set in command headers.
     * @return the Akka configuration Props object
     */
    static Props props(final ActorRef pubSubMediator, final ActorRef commandProducer,
            final AuthorizationSubject authorizationSubject,
            final List<MappingContext> mappingContexts) {

        return Props.create(CommandProcessorActor.class, new Creator<CommandProcessorActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public CommandProcessorActor create() {
                return new CommandProcessorActor(pubSubMediator, commandProducer, authorizationSubject,
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
        final String correlationId = DittoHeaders.of(m.getHeaders()).getCorrelationId().orElse("no-correlation-id");
        LogUtil.enhanceLogWithCorrelationId(log, correlationId);

        log.debug("Processing: {}", m);

        // TODO dg find better way to inject header fields
        final String subjectsArray = JsonFactory.newArray().add(authorizationSubject.getId()).toString();
        m.getHeaders().put(DittoHeaderDefinition.AUTHORIZATION_SUBJECTS.getKey(), subjectsArray);

        try {
            final Command<?> command = processor.process(m);
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
        finishTrace(response);

        if (response.getStatusCodeValue() < HttpStatusCode.BAD_REQUEST.toInt()) {
            log.debug("Received response: {}", response);
        } else {
            log.info("Received error response: {}", response);
        }

        try {
            final InternalMessage message = processor.process(response);
            commandProducer.forward(response, context());
        } catch (Exception e) {
            log.info(e.getMessage());
        }
    }

    /**
     * Shortcut to the dynamic access object
     *
     * @return the dynamic access object
     */
    private DynamicAccess getDynamicAccess() {
        return ((ExtendedActorSystem) getContext().getSystem()).dynamicAccess();
    }

    private void updateCorrelationId(String correlationId) {
        LogUtil.enhanceLogWithCorrelationId(log, correlationId);
    }

//    kamon helpers

    private void startTrace(final Command<?> command) {
        command.getDittoHeaders().getCorrelationId().ifPresent(correlationId ->
                traces.put(correlationId, createRoundtripContext(correlationId, command.getType()))
        );
    }

    private void finishTrace(final CommandResponse response) {
        if (ThingErrorResponse.class.isAssignableFrom(response.getClass())) {
            finishTrace(response, ((ThingErrorResponse) response).getDittoRuntimeException());
        } else {
            finishTrace(response, null);
        }
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

    private static TraceContext createRoundtripContext(final String correlationId, final String type) {
        final Option<String> token = Option.apply(correlationId);
        final TraceContext ctx = Kamon.tracer().newContext("roundtrip.amqp_" + type, token);
        ctx.addMetadata("command", type);
        return ctx;
    }
}
