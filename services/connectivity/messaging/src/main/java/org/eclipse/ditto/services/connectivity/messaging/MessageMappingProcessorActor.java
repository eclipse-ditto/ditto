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

import static org.eclipse.ditto.model.connectivity.MetricType.DROPPED;
import static org.eclipse.ditto.model.connectivity.MetricType.MAPPED;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.common.ConditionChecker;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.common.Placeholders;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectionSignalIdEnforcementFailedException;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.MetricDirection;
import org.eclipse.ditto.model.connectivity.MetricType;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.placeholders.EnforcementFilter;
import org.eclipse.ditto.model.placeholders.ExpressionResolver;
import org.eclipse.ditto.model.placeholders.HeadersPlaceholder;
import org.eclipse.ditto.model.placeholders.PlaceholderFactory;
import org.eclipse.ditto.model.placeholders.PlaceholderFilter;
import org.eclipse.ditto.model.placeholders.ThingPlaceholder;
import org.eclipse.ditto.model.placeholders.TopicPathPlaceholder;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.connectivity.messaging.metrics.ConnectionMetricsCollector;
import org.eclipse.ditto.services.connectivity.messaging.metrics.ConnectivityCounterRegistry;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.InboundExternalMessage;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.models.connectivity.OutboundSignalFactory;
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
 * This Actor processes incoming {@link Signal}s and dispatches them via {@link DistributedPubSubMediator} to a consumer
 * actor.
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
    private final BiFunction<ExternalMessage, DittoHeaders, DittoHeaders> adjustHeaders;
    private final Function<InboundExternalMessage, DittoHeaders> mapHeaders;
    private final BiFunction<OutboundSignal, ExternalMessage, OutboundSignal.WithExternalMessage>
            replaceTargetAddressPlaceholders;
    private final BiConsumer<ExternalMessage, Signal<?>> applySignalIdEnforcement;
    private final ConnectionMetricsCollector responseConsumedCounter;
    private final ConnectionMetricsCollector responseDroppedCounter;
    private ConnectionMetricsCollector responseMappedCounter;

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
        mapHeaders = new ApplyHeaderMapping(log);
        applySignalIdEnforcement = new ApplySignalIdEnforcement(log);
        replaceTargetAddressPlaceholders = new PlaceholderInTargetAddressSubstitution(log);
        responseConsumedCounter = ConnectivityCounterRegistry.getResponseDispatchedCounter(connectionId);
        responseDroppedCounter = ConnectivityCounterRegistry.getResponseDroppedCounter(connectionId);
        responseMappedCounter = ConnectivityCounterRegistry.getResponseMappedCounter(connectionId);
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
                .match(ExternalMessage.class, this::handleInboundMessage)
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

    private void handleInboundMessage(final ExternalMessage externalMessage) {
        ConditionChecker.checkNotNull(externalMessage);
        final String correlationId = externalMessage.getHeaders().get(DittoHeaderDefinition.CORRELATION_ID.getKey());
        LogUtil.enhanceLogWithCorrelationId(log, correlationId);
        LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId);
        log.debug("Handling ExternalMessage: {}", externalMessage);
        try {
            mapExternalMessageToSignalAndForwardToConcierge(externalMessage);
        } catch (final DittoRuntimeException e) {
            handleDittoRuntimeException(e, externalMessage.getHeaders());
        } catch (final Exception e) {
            log.warning("Got <{}> when message was processed: <{}>", e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private void mapExternalMessageToSignalAndForwardToConcierge(final ExternalMessage externalMessage) {
        final String source = externalMessage.getSourceAddress().orElse("unknown");
        final ExternalMessage messageWithAuthSubject = placeholderSubstitution.apply(externalMessage);

        final Optional<InboundExternalMessage> inboundMessageOpt =
                ConnectivityCounterRegistry.getInboundMappedCounter(connectionId, source).record(() ->
                        processor.process(messageWithAuthSubject));

        if (inboundMessageOpt.isPresent()) {
            final InboundExternalMessage inboundMessage = inboundMessageOpt.get();
            final Signal<?> signal = inboundMessage.getSignal();
            enhanceLogUtil(signal);

            ConnectivityCounterRegistry.getInboundEnforcedCounter(connectionId, source).record(() ->
                    applySignalIdEnforcement.accept(messageWithAuthSubject, signal));
            // the above throws an exception if signal id enforcement fails

            final Signal<?> adjustedSignal = mapHeaders
                    .andThen(mappedHeaders -> adjustHeaders.apply(messageWithAuthSubject, mappedHeaders))
                    .andThen(signal::setDittoHeaders)
                    .apply(inboundMessage);

            startTrace(adjustedSignal);
            // This message is important to check if a command is accepted for a specific connection, as this
            // happens quite a lot this is going to the debug level. Use best with a connection-id filter.
            log.debug("Message successfully mapped to signal: '{}'. Passing to conciergeForwarder",
                    adjustedSignal.getType());
            conciergeForwarder.tell(adjustedSignal, getSelf());
        } else {
            log.debug("Message mapping returned null, message is dropped.");
            ConnectivityCounterRegistry.getInboundDroppedCounter(connectionId, source).recordSuccess();
        }
    }

    private void enhanceLogUtil(final WithDittoHeaders<?> signal) {
        LogUtil.enhanceLogWithCorrelationId(log, signal);
        LogUtil.enhanceLogWithCustomField(log, BaseClientData.MDC_CONNECTION_ID, connectionId);
    }

    private void handleDittoRuntimeException(final DittoRuntimeException exception) {
        handleDittoRuntimeException(exception, exception.getDittoHeaders());
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
        recordResponse(response);

        if (response.getDittoHeaders().isResponseRequired()) {

            if (isSuccessResponse(response)) {
                log.debug("Received response: {}", response);
            } else {
                log.debug("Received error response: {}", response.toJsonString());
            }

            handleSignal(response);
        } else {
            log.debug("Requester did not require response (via DittoHeader '{}') - not mapping back to ExternalMessage",
                    DittoHeaderDefinition.RESPONSE_REQUIRED);
            responseDroppedCounter.recordSuccess();
        }
    }

    private void recordResponse(final CommandResponse<?> response) {
        if (isSuccessResponse(response)) {
            responseConsumedCounter.recordSuccess();
        } else {
            responseConsumedCounter.recordFailure();
        }
    }

    private boolean isSuccessResponse(final CommandResponse<?> response) {
        return response.getStatusCodeValue() < HttpStatusCode.BAD_REQUEST.toInt();
    }

    private void handleOutboundSignal(final OutboundSignal outbound) {
        enhanceLogUtil(outbound.getSource());
        log.debug("Handling outbound signal: {}", outbound.getSource());

        final Optional<OutboundSignal.WithExternalMessage> mappedOutboundSignal = mapToExternalMessage(outbound)
                .map(externalMessage -> replaceTargetAddressPlaceholders.apply(outbound, externalMessage));

        if (mappedOutboundSignal.isPresent()) {
            publisherActor.forward(mappedOutboundSignal.get(), getContext());
        } else {
            log.debug("Message mapping returned null, message is dropped.");
            getCountersForOutboundSignal(outbound, connectionId, DROPPED)
                    .forEach(ConnectionMetricsCollector::recordSuccess);
        }
    }

    /**
     * Is called for responses or errors which were directly sent to the mapping actor as a response.
     *
     * @param signal the response/error
     */
    private void handleSignal(final Signal<?> signal) {
        // map to outbound signal without authorized target (responses and errors are only sent to its origin)
        log.debug("Handling raw signal: {}", signal);
        handleOutboundSignal(OutboundSignalFactory.newOutboundSignal(signal, Collections.emptySet()));
    }

    private Optional<ExternalMessage> mapToExternalMessage(final OutboundSignal outbound) {
        try {
            final Set<ConnectionMetricsCollector> collectors =
                    getCountersForOutboundSignal(outbound, connectionId, MAPPED);
            return ConnectionMetricsCollector.record(collectors, () -> processor.process(outbound.getSource()));
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

    private Set<ConnectionMetricsCollector> getCountersForOutboundSignal(final OutboundSignal outbound,
            final String connectionId, final MetricType metricType) {

        if (outbound.getSource() instanceof CommandResponse) {
            return Collections.singleton(responseMappedCounter);
        } else {
            return outbound.getTargets()
                    .stream()
                    .map(Target::getOriginalAddress)
                    .map(address -> ConnectivityCounterRegistry.getCounter(connectionId, metricType,
                            MetricDirection.OUTBOUND, address))
                    .collect(Collectors.toSet());
        }
    }

    /**
     * Helper class that replaces thing + topic placeholders in target addresses. This is done here and not in
     * ConnectionActor because we have the topic at hand not before the mapping was done.
     */
    static final class PlaceholderInTargetAddressSubstitution
            implements BiFunction<OutboundSignal, ExternalMessage, OutboundSignal.WithExternalMessage> {

        private static final HeadersPlaceholder HEADER_PLACEHOLDER = PlaceholderFactory.newHeadersPlaceholder();
        private static final ThingPlaceholder THING_PLACEHOLDER = PlaceholderFactory.newThingPlaceholder();
        private static final TopicPathPlaceholder TOPIC_PLACEHOLDER = PlaceholderFactory.newTopicPathPlaceholder();

        private final DiagnosticLoggingAdapter log;

        private PlaceholderInTargetAddressSubstitution(final DiagnosticLoggingAdapter log) {
            this.log = log;
        }

        @Override
        public OutboundSignal.WithExternalMessage apply(final OutboundSignal outboundSignal,
                final ExternalMessage externalMessage) {
            final Optional<TopicPath> topicPathOpt = externalMessage.getTopicPath();
            if (outboundSignal
                    .getTargets()
                    .stream()
                    .anyMatch(t -> Placeholders.containsAnyPlaceholder(t.getAddress()))) {

                final ExpressionResolver expressionResolver = PlaceholderFactory.newExpressionResolver(
                        PlaceholderFactory.newPlaceholderResolver(HEADER_PLACEHOLDER, externalMessage.getHeaders()),
                        PlaceholderFactory.newPlaceholderResolver(THING_PLACEHOLDER, outboundSignal.getSource().getId()),
                        PlaceholderFactory.newPlaceholderResolver(TOPIC_PLACEHOLDER, topicPathOpt.orElse(null))
                );

                final Set<Target> targets = outboundSignal.getTargets().stream().map(t -> {
                    final String address =
                            PlaceholderFilter.apply(t.getAddress(), expressionResolver, true);
                    return ConnectivityModelFactory.newTarget(t, address, t.getQos().orElse(null));
                }).collect(Collectors.toSet());
                final OutboundSignal modifiedOutboundSignal =
                        OutboundSignalFactory.newOutboundSignal(outboundSignal.getSource(), targets);
                return OutboundSignalFactory.newMappedOutboundSignal(modifiedOutboundSignal,
                        externalMessage);
            } else {
                return OutboundSignalFactory.newMappedOutboundSignal(outboundSignal,
                        externalMessage);
            }
        }
    }

    static final class PlaceholderSubstitution implements Function<ExternalMessage, ExternalMessage> {

        @Override
        public ExternalMessage apply(final ExternalMessage externalMessage) {
            final AuthorizationContext authorizationContext = getAuthorizationContextFromMessage(externalMessage);
            final AuthorizationContext filteredContext =
                    PlaceholderFilter.applyHeadersPlaceholderToAuthContext(authorizationContext, externalMessage.getHeaders());
            final JsonArray authSubjectsArray = mapAuthorizationContextToSubjectsArray(filteredContext);
            final ExternalMessage externalMessageWithSourceHeader = authSubjectsArray.get(0)
                    .map(JsonValue::asString)
                    .map(firstAuthorizationSubject -> externalMessage.withHeader(DittoHeaderDefinition.SOURCE.getKey(),
                            firstAuthorizationSubject))
                    .orElse(externalMessage);
            return externalMessageWithSourceHeader.withHeader(DittoHeaderDefinition.AUTHORIZATION_SUBJECTS.getKey(),
                    authSubjectsArray.toString());
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

        private static JsonArray mapAuthorizationContextToSubjectsArray(
                final AuthorizationContext authorizationContext) {
            return authorizationContext.stream()
                    .map(AuthorizationSubject::getId)
                    .map(JsonFactory::newValue)
                    .collect(JsonCollectors.valuesToArray());
        }
    }

    static final class AdjustHeaders implements BiFunction<ExternalMessage, DittoHeaders, DittoHeaders> {

        private final String connectionId;

        private AdjustHeaders(final String connectionId) {
            this.connectionId = connectionId;
        }

        @Override
        public DittoHeaders apply(final ExternalMessage externalMessage, final DittoHeaders dittoHeaders) {
            final DittoHeadersBuilder dittoHeadersBuilder = dittoHeaders.toBuilder();

            final String beforeMapping = externalMessage
                    .findHeader(DittoHeaderDefinition.AUTHORIZATION_SUBJECTS.getKey())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Missing header " + DittoHeaderDefinition.AUTHORIZATION_SUBJECTS.getKey()));
            // do not use dittoHeadersBuilder.authorizationSubjects(beforeMapping);
            // because beforeMapping is a JsonArray String, we need to set AUTHORIZATION_SUBJECTS header directly
            dittoHeadersBuilder.putHeader(DittoHeaderDefinition.AUTHORIZATION_SUBJECTS.getKey(), beforeMapping);

            if (!dittoHeaders.getOrigin().isPresent()) {
                dittoHeadersBuilder.origin(connectionId);
            }

            // overwrite the auth-subjects to the configured ones after mapping in order to be sure that the mapping
            // does not choose/change the auth-subjects itself:
            return dittoHeadersBuilder.build();
        }

    }

    /**
     * Helper class applying the {@link EnforcementFilter} of
     * the passed in {@link ExternalMessage} by throwing a {@link ConnectionSignalIdEnforcementFailedException} if the
     * enforcement failed.
     */
    static final class ApplySignalIdEnforcement implements BiConsumer<ExternalMessage, Signal<?>> {

        private final DiagnosticLoggingAdapter log;

        ApplySignalIdEnforcement(final DiagnosticLoggingAdapter log) {
            this.log = log;
        }

        @Override
        public void accept(final ExternalMessage externalMessage, final Signal<?> signal) {
            externalMessage.getEnforcementFilter().ifPresent(enforcementFilter -> {
                log.debug("Connection Signal ID Enforcement enabled - matching Signal ID <{}> with filter: {}",
                        signal.getId(), enforcementFilter);
                enforcementFilter.match(signal.getId(), signal.getDittoHeaders());
            });
        }
    }

    /**
     * Helper class applying the {@link org.eclipse.ditto.model.connectivity.HeaderMapping}.
     */
    static final class ApplyHeaderMapping implements Function<InboundExternalMessage, DittoHeaders> {

        private static final HeadersPlaceholder HEADERS_PLACEHOLDER = PlaceholderFactory.newHeadersPlaceholder();
        private static final ThingPlaceholder THING_PLACEHOLDER = PlaceholderFactory.newThingPlaceholder();
        private static final TopicPathPlaceholder TOPIC_PLACEHOLDER = PlaceholderFactory.newTopicPathPlaceholder();

        private final DiagnosticLoggingAdapter log;

        ApplyHeaderMapping(final DiagnosticLoggingAdapter log) {
            this.log = log;
        }

        @Override
        public DittoHeaders apply(final InboundExternalMessage inboundExternalMessage) {
            final Signal<?> signal = inboundExternalMessage.getSignal();
            final ExternalMessage externalMessage = inboundExternalMessage.getSource();
            return externalMessage.getHeaderMapping().map(mapping -> {
                final DittoHeaders dittoHeaders = signal.getDittoHeaders();
                final String thingId = signal.getId();

                final ExpressionResolver expressionResolver = PlaceholderFactory.newExpressionResolver(
                        PlaceholderFactory.newPlaceholderResolver(HEADERS_PLACEHOLDER, dittoHeaders),
                        PlaceholderFactory.newPlaceholderResolver(THING_PLACEHOLDER, thingId),
                        PlaceholderFactory.newPlaceholderResolver(TOPIC_PLACEHOLDER, inboundExternalMessage.getTopicPath())
                );

                final DittoHeadersBuilder dittoHeadersBuilder = dittoHeaders.toBuilder();
                mapping.getMapping().entrySet().stream()
                        .map(e -> newEntry(e.getKey(),
                                PlaceholderFilter.apply(e.getValue(), expressionResolver, true))
                        )
                        .forEach(e -> dittoHeadersBuilder.putHeader(e.getKey(), e.getValue()));

                LogUtil.enhanceLogWithCorrelationId(log, signal);
                final DittoHeaders newHeaders = dittoHeadersBuilder.build();
                log.debug("Result of header mapping <{}> are these headers: {}", mapping, newHeaders);
                return newHeaders;
            }).orElse(signal.getDittoHeaders());
        }

        private static Map.Entry<String, String> newEntry(final String key, final String value) {
            return new AbstractMap.SimpleImmutableEntry<>(key, value);
        }
    }
}
