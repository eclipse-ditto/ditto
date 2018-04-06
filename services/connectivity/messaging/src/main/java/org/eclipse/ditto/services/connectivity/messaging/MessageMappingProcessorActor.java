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

    /**
     * The pub sub group used to publish responses to live commands and messages.
     */
    public static final String LIVE_RESPONSES_PUB_SUB_GROUP = "live-responses";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef pubSubMediator;
    private final String pubSubTargetPath;
    private final ActorRef publisherActor;
    private final AuthorizationContext authorizationContext;
    private final Cache<String, TraceContext> traces;

    private final MessageHeaderFilter headerFilter;
    private final MessageMappingProcessor processor;
    private final String connectionId;

    private MessageMappingProcessorActor(final ActorRef pubSubMediator, final String pubSubTargetPath,
            final ActorRef publisherActor, final AuthorizationContext authorizationContext,
            final MessageHeaderFilter headerFilter,
            final MessageMappingProcessor processor, final String connectionId) {
        this.pubSubMediator = pubSubMediator;
        this.pubSubTargetPath = pubSubTargetPath;
        this.publisherActor = publisherActor;
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
     * @param pubSubMediator the akka pubsub mediator actor
     * @param pubSubTargetPath the target path where incoming messages are sent
     * @param publisherActor actor that handles/publishes outgoing messages
     * @param authorizationContext the authorization context (authorized subjects) that are set in command headers
     * @param headerFilter the header filter used to apply on responses
     * @param processor the MessageMappingProcessor to use
     * @param connectionId the connection id
     * @return the Akka configuration Props object
     */
    public static Props props(final ActorRef pubSubMediator, final String pubSubTargetPath,
            final ActorRef publisherActor,
            final AuthorizationContext authorizationContext,
            final MessageHeaderFilter headerFilter,
            final MessageMappingProcessor processor,
            final String connectionId) {

        return Props.create(MessageMappingProcessorActor.class, new Creator<MessageMappingProcessorActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public MessageMappingProcessorActor create() {
                return new MessageMappingProcessorActor(pubSubMediator, pubSubTargetPath, publisherActor,
                        authorizationContext, headerFilter, processor, connectionId);
            }
        });
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param pubSubMediator the akka pubsub mediator actor
     * @param pubSubTargetPath the target path where incoming messages are sent
     * @param publisherActor actor that handles outgoing messages
     * @param authorizationContext the authorization context (authorized subjects) that are set in command headers
     * @param processor the MessageMappingProcessor to use
     * @param connectionId the connection id
     * @return the Akka configuration Props object
     */
    public static Props props(final ActorRef pubSubMediator, final String pubSubTargetPath,
            final ActorRef publisherActor,
            final AuthorizationContext authorizationContext,
            final MessageMappingProcessor processor,
            final String connectionId) {

        return props(pubSubMediator, pubSubTargetPath, publisherActor,
                        authorizationContext,
                        new MessageHeaderFilter(MessageHeaderFilter.Mode.EXCLUDE, Collections.emptyList()),
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
                .match(DistributedPubSubMediator.SubscribeAck.class, this::subscribeAck)
                .match(DistributedPubSubMediator.UnsubscribeAck.class, this::unsubscribeAck)
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private void handle(final ExternalMessage externalMessage) {
        ConditionChecker.checkNotNull(externalMessage);
        final String correlationId = externalMessage.getHeaders().get(DittoHeaderDefinition.CORRELATION_ID.getKey());
        LogUtil.enhanceLogWithCorrelationId(log, correlationId);
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
                LogUtil.enhanceLogWithCorrelationId(log, signal);
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
                log.info("Sending '{}' to '{}'", adjustedSignal.getType(), pubSubTargetPath);
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

        log.info( "Got DittoRuntimeException '{}' when ExternalMessage was processed: {} - {}",
                exception.getErrorCode(), exception.getMessage(), exception.getDescription().orElse(""));

        handleCommandResponse(errorResponse);
    }

    private void handleCommandResponse(final CommandResponse<?> response) {
        LogUtil.enhanceLogWithCorrelationId(log, response);
        finishTrace(response);

        response.getDittoHeaders()
                .getCorrelationId()
                .map(correlationId -> new DistributedPubSubMediator.Unsubscribe(correlationId,
                        LIVE_RESPONSES_PUB_SUB_GROUP, getSelf()))
                .ifPresent(unsubscribe -> pubSubMediator.tell(unsubscribe, getSelf()));

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
        LogUtil.enhanceLogWithCorrelationId(log, signal);
        log.debug("Handling signal: {}", signal);

        try {
            final Optional<ExternalMessage> messageOpt = processor.process(signal);
            messageOpt.map(headerFilter).ifPresent(message -> publisherActor.forward(message, getContext()));
        } catch (final DittoRuntimeException e) {
            log.info("Got DittoRuntimeException during processing Signal: {} - {}", e.getMessage(),
                    e.getDescription().orElse(""));
        } catch (final Exception e) {
            log.warning("Got unexpected exception during processing Signal: {}", e.getMessage());
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

    private static TraceContext createRoundtripContext(final String correlationId, final String type) {
        final Option<String> token = Option.apply(correlationId);
        final TraceContext ctx = Kamon.tracer().newContext("roundtrip.amqp_" + type, token);
        ctx.addMetadata("command", type);
        return ctx;
    }

    private void subscribeAck(final DistributedPubSubMediator.SubscribeAck subscribeAck) {
        log.debug("Successfully subscribed to distributed pub/sub on topic '{}' in group '{}'.",
                subscribeAck.subscribe().topic(), subscribeAck.subscribe().group());
    }

    private void unsubscribeAck(final DistributedPubSubMediator.UnsubscribeAck unsubscribeAck) {
        log.debug("Successfully unsubscribed to distributed pub/sub on topic '{}' in group '{}'.",
                unsubscribeAck.unsubscribe().topic(), unsubscribeAck.unsubscribe().group());
    }
}
