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


import java.util.Collections;
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
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
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
    public static final String ACTOR_NAME = "messageMappingProcessor";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef publisherActor;
    private final AuthorizationContext authorizationContext;
    private final Cache<String, TraceContext> traces;

    private final DittoHeadersFilter headerFilter;
    private final MessageMappingProcessor processor;
    private final String connectionId;
    private final ActorRef commandRouter;

    private MessageMappingProcessorActor(final ActorRef publisherActor,
            final ActorRef commandRouter, final AuthorizationContext authorizationContext,
            final DittoHeadersFilter headerFilter,
            final MessageMappingProcessor processor,
            final String connectionId) {
        this.publisherActor = publisherActor;
        this.commandRouter = commandRouter;
        this.authorizationContext = authorizationContext;
        this.processor = processor;
        this.headerFilter = headerFilter;
        this.connectionId = connectionId;
        traces = CacheBuilder.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .removalListener((RemovalListener<String, TraceContext>) notification
                        -> log.debug("Trace for {} removed.", notification.getKey()))
                .build();
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param publisherActor actor that handles/publishes outgoing messages
     * @param commandRouter the command router used to send signals into the cluster
     * @param authorizationContext the authorization context (authorized subjects) that are set in command headers
     * @param headerFilter the header filter used to apply on responses
     * @param processor the MessageMappingProcessor to use
     * @param connectionId the connection id
     * @return the Akka configuration Props object
     */
    public static Props props(final ActorRef publisherActor,
            final ActorRef commandRouter, final AuthorizationContext authorizationContext,
            final DittoHeadersFilter headerFilter,
            final MessageMappingProcessor processor,
            final String connectionId) {

        return Props.create(MessageMappingProcessorActor.class, new Creator<MessageMappingProcessorActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public MessageMappingProcessorActor create() {
                return new MessageMappingProcessorActor(publisherActor, commandRouter, authorizationContext,
                        headerFilter, processor,
                        connectionId);
            }
        });
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param publisherActor actor that handles outgoing messages
     * @param commandRouter the command router used to send signals into the cluster
     * @param authorizationContext the authorization context (authorized subjects) that are set in command headers
     * @param processor the MessageMappingProcessor to use
     * @param connectionId the connection id
     * @return the Akka configuration Props object
     */
    public static Props props(final ActorRef publisherActor,
            final ActorRef commandRouter,
            final AuthorizationContext authorizationContext,
            final MessageMappingProcessor processor,
            final String connectionId) {

        return props(publisherActor,
                commandRouter,
                authorizationContext,
                new DittoHeadersFilter(DittoHeadersFilter.Mode.EXCLUDE, Collections.emptyList()),
                processor, connectionId);
    }


    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(ExternalMessage.class, this::handle)
                .match(CommandResponse.class, this::handleCommandResponse)
                .match(Signal.class, this::handleSignal)
                .match(DittoRuntimeException.class, this::handleDittoRuntimeException)
                .match(Status.Failure.class, f -> log.warning("Got failure with cause {}: {}",
                        f.cause().getClass().getSimpleName(), f.cause().getMessage()))
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private void handle(final ExternalMessage externalMessage) {
        ConditionChecker.checkNotNull(externalMessage);
        final String correlationId = externalMessage.getHeaders().get(DittoHeaderDefinition.CORRELATION_ID.getKey());
        LogUtil.enhanceLogWithCorrelationId(log, correlationId);
        LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId);
        log.debug("Handling ExternalMessage: {}", externalMessage);

        final String authSubjectsArray = authorizationContext.stream()
                .map(AuthorizationSubject::getId)
                .map(JsonFactory::newValue)
                .collect(JsonCollectors.valuesToArray())
                .toString();
        final ExternalMessage messageWithAuthSubject =
                externalMessage.withHeader(DittoHeaderDefinition.AUTHORIZATION_SUBJECTS.getKey(), authSubjectsArray);

        try {
            final Optional<Signal<?>> signalOpt = processor.process(messageWithAuthSubject);
            signalOpt.ifPresent(signal -> {
                enhanceLogUtil(signal);
                final DittoHeadersBuilder adjustedHeadersBuilder = signal.getDittoHeaders().toBuilder()
                        .authorizationContext(authorizationContext);

                if (!signal.getDittoHeaders().getOrigin().isPresent()) {
                    adjustedHeadersBuilder.origin(connectionId);
                }
                final DittoHeaders adjustedHeaders = adjustedHeadersBuilder.build();
                // overwrite the auth-subjects to the configured ones after mapping in order to be sure that the mapping
                // does not choose/change the auth-subjects itself:
                final Signal<?> adjustedSignal = signal.setDittoHeaders(adjustedHeaders);
                startTrace(adjustedSignal);
                log.info("Sending '{}' using command router", adjustedSignal.getType());
                commandRouter.tell(adjustedSignal, getSelf());
            });
        } catch (final DittoRuntimeException e) {
            handleDittoRuntimeException(e, DittoHeaders.of(externalMessage.getHeaders()));
        } catch (final Exception e) {
            log.warning("Got <{}> when message was processed: <{}>", e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private void enhanceLogUtil(final WithDittoHeaders<?> signal) {
        LogUtil.enhanceLogWithCorrelationId(log, signal);
        LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId);
    }

    private void handleDittoRuntimeException(final DittoRuntimeException exception) {
        handleDittoRuntimeException(exception, DittoHeaders.empty());
    }

    private void handleDittoRuntimeException(final DittoRuntimeException exception,
            final DittoHeaders dittoHeaders) {
        final DittoHeaders mergedHeaders =
                DittoHeaders.newBuilder(exception.getDittoHeaders()).putHeaders(dittoHeaders).build();
        final ThingErrorResponse errorResponse = ThingErrorResponse.of(exception, mergedHeaders);

        enhanceLogUtil(exception);

        log.info("Got DittoRuntimeException '{}' when ExternalMessage was processed: {} - {}",
                exception.getErrorCode(), exception.getMessage(), exception.getDescription().orElse(""));

        handleCommandResponse(errorResponse);
    }

    private void handleCommandResponse(final CommandResponse<?> response) {
        enhanceLogUtil(response);
        finishTrace(response);
        if (response.getDittoHeaders().isResponseRequired()) {

            if (response.getStatusCodeValue() < HttpStatusCode.BAD_REQUEST.toInt()) {
                log.debug("Received response: {}", response);
            } else {
                log.debug("Received error response: {}", response.toJsonString());
            }

            handleSignal(response);
        } else {
            log.debug("Requester did not require response (via DittoHeader '{}') - not mapping back to ExternalMessage",
                    DittoHeaderDefinition.RESPONSE_REQUIRED);
        }
    }

    private void handleSignal(final Signal<?> signal) {
        enhanceLogUtil(signal);
        log.debug("Handling signal: {}", signal);

        try {
            final DittoHeaders filteredDittoHeaders = headerFilter.apply(signal.getDittoHeaders());
            final Signal signalWithFilteredHeaders = signal.setDittoHeaders(filteredDittoHeaders);
            processor.process(signalWithFilteredHeaders)
                    .ifPresent(message -> publisherActor.forward(message, getContext()));
        } catch (final DittoRuntimeException e) {
            log.info("Got DittoRuntimeException during processing Signal: {} - {}", e.getMessage(),
                    e.getDescription().orElse(""));
        } catch (final Exception e) {
            log.warning("Got unexpected exception during processing Signal: {}", e.getMessage());
        }
    }

    private void startTrace(final Signal<?> command) {
        command.getDittoHeaders().getCorrelationId().ifPresent(correlationId ->
                traces.put(correlationId, createRoundtripContext(correlationId, connectionId, command.getType()))
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
                log.debug("Trace missing for response: '{}'", response);
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

    private static TraceContext createRoundtripContext(final String correlationId, final String connectionId,
            final String type) {
        final Option<String> token = Option.apply(correlationId);
        final TraceContext ctx = Kamon.tracer().newContext("roundtrip." + connectionId + "." + type,
                token);
        ctx.addMetadata("command", type);
        return ctx;
    }
}
