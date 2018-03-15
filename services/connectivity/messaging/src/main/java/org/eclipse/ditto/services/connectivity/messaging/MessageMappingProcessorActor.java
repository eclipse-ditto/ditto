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


import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.common.ConditionChecker;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ExternalMessage;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
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
 * This Actor processes incoming {@link Signal}s and dispatches them via {@link DistributedPubSubMediator} to a
 * consumer actor.
 */
public final class MessageMappingProcessorActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME_PREFIX = "messageMappingProcessor-";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef pubSubMediator;
    private final String pubSubTargetPath;
    private final ActorRef commandProducer;
    private final AuthorizationContext authorizationContext;
    private final MessageMappingProcessor processor;

    private final Cache<String, TraceContext> traces;

    private MessageMappingProcessorActor(final ActorRef pubSubMediator, final String pubSubTargetPath,
            final ActorRef commandProducer, final AuthorizationContext authorizationContext,
            final MessageMappingProcessor processor) {
        this.pubSubMediator = pubSubMediator;
        this.pubSubTargetPath = pubSubTargetPath;
        this.commandProducer = commandProducer;
        this.authorizationContext = authorizationContext;
        this.processor = processor;
        traces = CacheBuilder.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .removalListener((RemovalListener<String, TraceContext>) notification
                        -> log.debug("Trace for {} removed.", notification.getKey()))
                .build();
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param pubSubMediator the akka pubsub mediator actor
     * @param pubSubTargetPath the target path where incoming messages are sent
     * @param commandProducer actor that handles outgoing messages
     * @param authorizationContext the authorization context (authorized subjects) that are set in command headers
     * @param processor the MessageMappingProcessor to use
     * @return the Akka configuration Props object
     */
    public static Props props(final ActorRef pubSubMediator, final String pubSubTargetPath,
            final ActorRef commandProducer,
            final AuthorizationContext authorizationContext,
            final MessageMappingProcessor processor) {

        return Props.create(MessageMappingProcessorActor.class, new Creator<MessageMappingProcessorActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public MessageMappingProcessorActor create() {
                return new MessageMappingProcessorActor(pubSubMediator, pubSubTargetPath, commandProducer,
                        authorizationContext, processor);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(ExternalMessage.class, this::handle)
                .match(CommandResponse.class, this::handleCommandResponse)
                .match(Signal.class, this::handleSignal)
                .match(DittoRuntimeException.class, this::handleDittoRuntimeException)
                .match(Status.Failure.class, f -> log.error(f.cause(), "Got an unexpected failure."))
                .matchAny(m -> {
                    log.debug("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private void handle(final ExternalMessage m) {
        ConditionChecker.checkNotNull(m);
        final String correlationId = m.getHeaders().get(DittoHeaderDefinition.CORRELATION_ID.getKey());
        LogUtil.enhanceLogWithCorrelationId(log, correlationId);

        final String authSubjectsArray = authorizationContext.stream()
                        .map(AuthorizationSubject::getId)
                        .map(JsonFactory::newValue)
                        .collect(JsonCollectors.valuesToArray())
                .toString();
        final ExternalMessage messageWithAuthSubject =
                m.withHeader(DittoHeaderDefinition.AUTHORIZATION_SUBJECTS.getKey(), authSubjectsArray);

        try {
            final Optional<Signal<?>> signalOpt = processor.process(messageWithAuthSubject);
            signalOpt.ifPresent(signal -> {
                final DittoHeaders adjustedHeaders = signal.getDittoHeaders().toBuilder()
                        .authorizationContext(authorizationContext)
                        .build();
                // overwrite the auth-subjects to the configured ones after mapping in order to be sure that the mapping
                // does not choose/change the auth-subjects itself:
                final Signal<?> adjustedSignal = signal.setDittoHeaders(adjustedHeaders);
                startTrace(adjustedSignal);
                log.info("Publishing '{}' to '{}'", adjustedSignal.getType(), pubSubTargetPath);
                pubSubMediator.tell(new DistributedPubSubMediator.Send(pubSubTargetPath, adjustedSignal, true),
                        getSelf());
            });
        } catch (final DittoRuntimeException e) {
            handleDittoRuntimeException(e);
        } catch (final Exception e) {
            log.warning("Got <{}> when message was processed: <{}>", e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private void handleDittoRuntimeException(final DittoRuntimeException exception) {
        final ThingErrorResponse errorResponse = ThingErrorResponse.of(exception);

        LogUtil.enhanceLogWithCorrelationId(log, exception);

        log.info( "Got DittoRuntimeException '{}' when command via AMQP was processed: {}",
                exception.getErrorCode(), exception.getMessage());

        handleCommandResponse(errorResponse);
    }

    private void handleCommandResponse(final CommandResponse<?> response) {
        LogUtil.enhanceLogWithCorrelationId(log, response);
        finishTrace(response);

        if (response.getStatusCodeValue() < HttpStatusCode.BAD_REQUEST.toInt()) {
            log.debug("Received response: {}", response);
        } else {
            log.info("Received error response: {}", response.toJsonString());
        }

        handleSignal(response);
    }

    private void handleSignal(final Signal<?> response) {
        LogUtil.enhanceLogWithCorrelationId(log, response);

        try {
            final Optional<ExternalMessage> messageOpt = processor.process(response);
            messageOpt.ifPresent(message -> commandProducer.forward(message, getContext()));
        } catch (final DittoRuntimeException e) {
            log.info("Got DittoRuntimeException during processing Signal: <{}>", e.getMessage());
        } catch (final Exception e) {
            log.warning("Got unexpected exception during processing Signal: <{}>", e.getMessage());
        }
    }

    private void startTrace(final Signal<?> command) {
        command.getDittoHeaders().getCorrelationId().ifPresent(correlationId ->
                traces.put(correlationId, createRoundtripContext(correlationId, command.getType()))
        );
    }

    private void finishTrace(final Signal<?> response) {
        if (ThingErrorResponse.class.isAssignableFrom(response.getClass())) {
            finishTrace(response, ((ThingErrorResponse) response).getDittoRuntimeException());
        } else {
            finishTrace(response, null);
        }
    }

    private void finishTrace(final Signal<?> response, @Nullable final Throwable cause) {
        response.getDittoHeaders().getCorrelationId().ifPresent(correlationId -> {
            try {
                finishTrace(correlationId, cause);
            } catch (final IllegalArgumentException e) {
                log.info("Trace missing for response: '{}'", response);
            }
        });
    }

    private void finishTrace(final String correlationId, @Nullable final Throwable cause) {
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
