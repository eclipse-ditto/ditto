/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

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
import org.eclipse.ditto.services.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.services.utils.tracing.TraceUtils;
import org.eclipse.ditto.services.utils.tracing.TracingTags;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;

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
    private final Map<String, StartedTimer> timers;

    private final MessageMappingProcessor processor;
    private final String connectionId;
    private final ActorRef conciergeForwarder;

    private final Function<ExternalMessage, ExternalMessage> placeholderSubstitution;
    private final BiFunction<ExternalMessage, Signal<?>, Signal<?>> adjustHeaders;
    private final BiConsumer<ExternalMessage, Signal<?>> thingIdEnforcer;

    private MessageMappingProcessorActor(final ActorRef publisherActor,
            final ActorRef conciergeForwarder,
            final MessageMappingProcessor processor,
            final String connectionId) {

        this.publisherActor = publisherActor;
        this.conciergeForwarder = conciergeForwarder;
        this.processor = processor;
        this.connectionId = connectionId;
        timers = new ConcurrentHashMap<>();
        placeholderSubstitution = new PlaceholderSubstitution();
        adjustHeaders = new AdjustHeaders(connectionId);
        thingIdEnforcer = new ThingIdEnforcer(log);
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param publisherActor actor that handles/publishes outgoing messages.
     * @param conciergeForwarder the actor used to send signals to the concierge service.
     * @param processor the MessageMappingProcessor to use.
     * @param connectionId the connection id.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef publisherActor,
            final ActorRef conciergeForwarder,
            final MessageMappingProcessor processor,
            final String connectionId) {

        return Props.create(MessageMappingProcessorActor.class, new Creator<MessageMappingProcessorActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public MessageMappingProcessorActor create() {
                return new MessageMappingProcessorActor(publisherActor, conciergeForwarder, processor, connectionId);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(ExternalMessage.class, this::handle)
                .match(CommandResponse.class, this::handleCommandResponse)
                .match(OutboundSignal.class, this::handleOutboundSignal)
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
        try {
            final ExternalMessage messageWithAuthSubject = placeholderSubstitution.apply(externalMessage);
            final Optional<Signal<?>> signalOpt = processor.process(messageWithAuthSubject);
            signalOpt.ifPresent(signal -> {
                enhanceLogUtil(signal);
                thingIdEnforcer.accept(messageWithAuthSubject, signal);
                final Signal<?> adjustedSignal = adjustHeaders.apply(messageWithAuthSubject, signal);
                startTrace(adjustedSignal);
                // This message is important to check if a command is accepted for a specific connection, as this
                // happens quite a lot this is going to the debug level. Use best with a connection-id filter.
                log.debug("Message successfully mapped to signal: '{}'. Passing to conciergeForwarder", adjustedSignal
                        .getType());
                conciergeForwarder.tell(adjustedSignal, getSelf());
            });
        } catch (final DittoRuntimeException e) {
            handleDittoRuntimeException(e, externalMessage.getHeaders());
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
            final Map<String, String> dittoHeaders) {

        final ThingErrorResponse errorResponse =
                ThingErrorResponse.of(exception, DittoHeaders.newBuilder(exception.getDittoHeaders())
                        .putHeaders(dittoHeaders)
                        .build());

        enhanceLogUtil(exception);

        final String stackTrace = stackTraceAsString(exception);
        log.info("Got DittoRuntimeException '{}' when ExternalMessage was processed: {} - {}. StackTrace: {}",
                exception.getErrorCode(), exception.getMessage(), exception.getDescription().orElse(""), stackTrace);

        handleCommandResponse(errorResponse);
    }

    private static String stackTraceAsString(final DittoRuntimeException exception) {
        final StringWriter stringWriter = new StringWriter();
        exception.printStackTrace(new PrintWriter(stringWriter));
        return stringWriter.toString();
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

    private void handleOutboundSignal(final OutboundSignal outbound) {
        final Signal<?> signal = outbound.getSource();
        enhanceLogUtil(signal);
        log.debug("Handling outbound signal: {}", signal);
        mapToExternalMessage(signal)
                .map(message -> new MappedOutboundSignal(outbound, message))
                .ifPresent(outboundSignal -> publisherActor.forward(outboundSignal, getContext()));
    }

    /**
     * Is called for responses or errors which were directly sent to the mapping actor as a response.
     *
     * @param signal the response/error
     */
    private void handleSignal(final Signal<?> signal) {
        // map to outbound signal without authorized target (responses and errors are only sent to its origin)
        log.debug("Handling raw signal: {}", signal);
        handleOutboundSignal(new UnmappedOutboundSignal(signal, Collections.emptySet()));
    }

    private Optional<ExternalMessage> mapToExternalMessage(final Signal<?> signal) {
        try {
            return processor.process(signal);
        } catch (final DittoRuntimeException e) {
            log.info("Got DittoRuntimeException during processing Signal: {} - {}", e.getMessage(),
                    e.getDescription().orElse(""));
        } catch (final Exception e) {
            log.warning("Got unexpected exception during processing Signal: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private void startTrace(final Signal<?> command) {
        command.getDittoHeaders().getCorrelationId().ifPresent(correlationId -> {
            final StartedTimer timer = TraceUtils
                    .newAmqpRoundTripTimer(command)
                    .expirationHandling(startedTimer -> timers.remove(correlationId))
                    .build();
            timers.put(correlationId, timer);
        });
    }

    private void finishTrace(final WithDittoHeaders<? extends Signal> response) {
        if (ThingErrorResponse.class.isAssignableFrom(response.getClass())) {
            finishTrace(response, ((ThingErrorResponse) response).getDittoRuntimeException());
        } else {
            finishTrace(response, null);
        }
    }

    private void finishTrace(final WithDittoHeaders<? extends Signal> response, @Nullable final Throwable cause) {
        response.getDittoHeaders().getCorrelationId().ifPresent(correlationId -> {
            try {
                finishTrace(correlationId, cause);
            } catch (final IllegalArgumentException e) {
                log.debug("Trace missing for response: '{}'", response);
            }
        });
    }

    private void finishTrace(final String correlationId, @Nullable final Throwable cause) {
        final StartedTimer timer = timers.remove(correlationId);
        if (null == timer) {
            throw new IllegalArgumentException(
                    MessageFormat.format("No trace found for correlation-id <{0}>!", correlationId));
        }

        timer.tag(TracingTags.MAPPING_SUCCESS, null == cause).stop();
    }

    static final class PlaceholderSubstitution implements Function<ExternalMessage, ExternalMessage> {

        private final PlaceholderFilter placeholderFilter = new PlaceholderFilter();

        @Override
        public ExternalMessage apply(final ExternalMessage externalMessage) {
            final AuthorizationContext authorizationContext = getAuthorizationContextFromMessage(externalMessage);
            final AuthorizationContext filteredContext =
                    placeholderFilter.filterAuthorizationContext(authorizationContext, externalMessage.getHeaders());
            final String authSubjectsArray = mapAuthorizationContextToSubjectsArray(filteredContext);
            return externalMessage.withHeader(DittoHeaderDefinition.AUTHORIZATION_SUBJECTS.getKey(),
                    authSubjectsArray);
        }

        private static AuthorizationContext getAuthorizationContextFromMessage(final ExternalMessage externalMessage) {
            final AuthorizationContext authorizationContext = externalMessage
                    .getAuthorizationContext()
                    .orElseThrow(() -> new IllegalArgumentException("No authorizationContext available."));
            if (authorizationContext.isEmpty()) {
                throw new IllegalArgumentException("Empty authorization context not allowed.");
            }
            return authorizationContext;
        }

        private static String mapAuthorizationContextToSubjectsArray(final AuthorizationContext authorizationContext) {
            return authorizationContext.stream()
                    .map(AuthorizationSubject::getId)
                    .map(JsonFactory::newValue)
                    .collect(JsonCollectors.valuesToArray())
                    .toString();
        }

    }

    static final class AdjustHeaders implements BiFunction<ExternalMessage, Signal<?>, Signal<?>> {

        private final String connectionId;

        private AdjustHeaders(final String connectionId) {
            this.connectionId = connectionId;
        }

        @Override
        public Signal<?> apply(final ExternalMessage externalMessage, final Signal<?> signal) {
            final DittoHeadersBuilder dittoHeadersBuilder = signal.getDittoHeaders().toBuilder();

            final String beforeMapping = externalMessage
                    .findHeader(DittoHeaderDefinition.AUTHORIZATION_SUBJECTS.getKey())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Missing header " + DittoHeaderDefinition.AUTHORIZATION_SUBJECTS.getKey()));
            // do not use dittoHeadersBuilder.authorizationSubjects(beforeMapping);
            // because beforeMapping is a JsonArray String, we need to set AUTHORIZATION_SUBJECTS header directly
            dittoHeadersBuilder.putHeader(DittoHeaderDefinition.AUTHORIZATION_SUBJECTS.getKey(), beforeMapping);

            if (!signal.getDittoHeaders().getOrigin().isPresent()) {
                dittoHeadersBuilder.origin(connectionId);
            }

            final DittoHeaders adjustedHeaders = dittoHeadersBuilder.build();
            // overwrite the auth-subjects to the configured ones after mapping in order to be sure that the mapping
            // does not choose/change the auth-subjects itself:
            return signal.setDittoHeaders(adjustedHeaders);
        }

    }

    static final class ThingIdEnforcer implements BiConsumer<ExternalMessage, Signal<?>> {

        private final PlaceholderFilter placeholderFilter = new PlaceholderFilter();
        private final DiagnosticLoggingAdapter log;

        ThingIdEnforcer(final DiagnosticLoggingAdapter log) {
            this.log = log;
        }

        @Override
        public void accept(final ExternalMessage externalMessage, final Signal<?> signal) {
            externalMessage.getThingIdEnforcement().ifPresent(thingIdEnforcement -> {
                log.debug("Thing ID Enforcement enabled: {}", thingIdEnforcement);
                final Collection<String> filtered =
                        placeholderFilter.filterAddresses(thingIdEnforcement.getFilters(), signal.getId(),
                                this::logUnresolvedPlaceholder);
                final String enforcementTarget = thingIdEnforcement.getTarget();
                log.debug("Target '{}' must match one of {}", enforcementTarget, filtered);
                if (!filtered.contains(enforcementTarget)) {
                    throw thingIdEnforcement.getError(signal.getDittoHeaders());
                }
            });
        }

        private void logUnresolvedPlaceholder(final String unresolvedPlaceholder) {
            log.info("The placeholder '{}' was not resolved, messages may be dropped.", unresolvedPlaceholder);
        }

    }

}
