/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging;

import static org.eclipse.ditto.model.connectivity.MetricType.DROPPED;
import static org.eclipse.ditto.model.connectivity.MetricType.MAPPED;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectionSignalIdEnforcementFailedException;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.EnforcementFilter;
import org.eclipse.ditto.model.connectivity.LogCategory;
import org.eclipse.ditto.model.connectivity.LogType;
import org.eclipse.ditto.model.connectivity.MetricDirection;
import org.eclipse.ditto.model.connectivity.MetricType;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.messages.MessageHeaderDefinition;
import org.eclipse.ditto.model.placeholders.ExpressionResolver;
import org.eclipse.ditto.model.placeholders.HeadersPlaceholder;
import org.eclipse.ditto.model.placeholders.PlaceholderFactory;
import org.eclipse.ditto.model.placeholders.PlaceholderFilter;
import org.eclipse.ditto.model.placeholders.ThingPlaceholder;
import org.eclipse.ditto.model.placeholders.TopicPathPlaceholder;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.base.config.limits.DefaultLimitsConfig;
import org.eclipse.ditto.services.base.config.limits.LimitsConfig;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientActor.PublishMappedMessage;
import org.eclipse.ditto.services.connectivity.messaging.config.DittoConnectivityConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.MonitoringConfig;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.DefaultConnectionMonitorRegistry;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.InfoProviderFactory;
import org.eclipse.ditto.services.connectivity.util.ConnectionLogUtil;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.services.models.connectivity.InboundExternalMessage;
import org.eclipse.ditto.services.models.connectivity.MappedInboundExternalMessage;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.models.connectivity.OutboundSignalFactory;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

/**
 * This Actor processes incoming {@link Signal}s and dispatches them.
 */
public final class MessageMappingProcessorActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "messageMappingProcessor";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef clientActor;
    private final MessageMappingProcessor messageMappingProcessor;
    private final ConnectionId connectionId;
    private final ActorRef conciergeForwarder;
    private final LimitsConfig limitsConfig;

    private final Function<ExternalMessage, ExternalMessage> placeholderSubstitution;
    private final BiFunction<ExternalMessage, DittoHeaders, DittoHeaders> adjustHeaders;
    private final Function<InboundExternalMessage, DittoHeaders> mapHeaders;
    private final BiFunction<OutboundSignal, ExternalMessage, OutboundSignal.WithExternalMessage>
            replaceTargetAddressPlaceholders;
    private final BiConsumer<ExternalMessage, Signal<?>> applySignalIdEnforcement;

    private final DefaultConnectionMonitorRegistry connectionMonitorRegistry;
    private final ConnectionMonitor responseDispatchedMonitor;
    private final ConnectionMonitor responseDroppedMonitor;
    private final ConnectionMonitor responseMappedMonitor;

    @SuppressWarnings("unused")
    private MessageMappingProcessorActor(final ActorRef conciergeForwarder,
            final ActorRef clientActor,
            final MessageMappingProcessor messageMappingProcessor,
            final ConnectionId connectionId) {

        this.conciergeForwarder = conciergeForwarder;
        this.clientActor = clientActor;
        this.messageMappingProcessor = messageMappingProcessor;
        this.connectionId = connectionId;

        this.limitsConfig = DefaultLimitsConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config())
        );

        placeholderSubstitution = new PlaceholderSubstitution();
        adjustHeaders = new AdjustHeaders(connectionId);
        mapHeaders = new ApplyHeaderMapping(log);
        applySignalIdEnforcement = new ApplySignalIdEnforcement(log);
        replaceTargetAddressPlaceholders = new PlaceholderInTargetAddressSubstitution();

        final MonitoringConfig monitoringConfig = DittoConnectivityConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config())
        ).getMonitoringConfig();
        this.connectionMonitorRegistry = DefaultConnectionMonitorRegistry.fromConfig(monitoringConfig);
        responseDispatchedMonitor = connectionMonitorRegistry.forResponseDispatched(connectionId);
        responseDroppedMonitor = connectionMonitorRegistry.forResponseDropped(connectionId);
        responseMappedMonitor = connectionMonitorRegistry.forResponseMapped(connectionId);
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param conciergeForwarder the actor used to send signals to the concierge service.
     * @param clientActor the client actor that created this mapping actor
     * @param processor the MessageMappingProcessor to use.
     * @param connectionId the connection ID.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef conciergeForwarder,
            final ActorRef clientActor,
            final MessageMappingProcessor processor,
            final ConnectionId connectionId) {

        return Props.create(MessageMappingProcessorActor.class, conciergeForwarder, clientActor, processor,
                connectionId);
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
        ConnectionLogUtil.enhanceLogWithCorrelationIdAndConnectionId(log, correlationId, connectionId);
        log.debug("Handling ExternalMessage: {}", externalMessage);
        try {
            mapExternalMessageToSignalAndForwardToConcierge(externalMessage);
        } catch (final Exception e) {
            handleException(e, externalMessage);
        }
    }

    private void handleException(final Exception e, final ExternalMessage message) {
        if (e instanceof DittoRuntimeException) {
            final DittoRuntimeException dittoRuntimeException = (DittoRuntimeException) e;
            responseMappedMonitor.getLogger()
                    .failure("Got exception {0} when processing external message: {1}",
                            dittoRuntimeException.getErrorCode(),
                            e.getMessage());
            handleDittoRuntimeException(dittoRuntimeException, DittoHeaders.newBuilder()
                    .putHeaders(message.getHeaders())
                    .putHeaders(message.getInternalHeaders())
                    .build());
        } else {
            responseMappedMonitor.getLogger()
                    .failure("Got unknown exception when processing external message: {1}", e.getMessage());
            log.warning("Got <{}> when message was processed: <{}>", e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private void mapExternalMessageToSignalAndForwardToConcierge(final ExternalMessage externalMessage) {
        final String source = externalMessage.getSourceAddress().orElse("unknown");
        final ExternalMessage messageWithAuthSubject = placeholderSubstitution.apply(externalMessage);
        final ConnectionMonitor inboundMapped = connectionMonitorRegistry.forInboundMapped(connectionId, source);
        final ConnectionMonitor inboundDropped = connectionMonitorRegistry.forInboundDropped(connectionId, source);
        final MappingResultHandler<MappedInboundExternalMessage> inboundMappingHandlers
                = new InboundMappingResultHandler(mappedInboundMessage -> {
            final Signal<?> signal = mappedInboundMessage.getSignal();
            enhanceLogUtil(signal);
            connectionMonitorRegistry.forInboundEnforced(connectionId, source)
                    .wrapExecution(signal)
                    .execute(() -> applySignalIdEnforcement.accept(messageWithAuthSubject, signal));
            // the above throws an exception if signal id enforcement fails
            final Signal<?> adjustedSignal = mapHeaders.andThen(
                    mappedHeaders -> adjustHeaders.apply(messageWithAuthSubject, mappedHeaders))
                    .andThen(adjustedHeaders -> adjustedHeaders.toBuilder()
                            // add all internal headers added by consumer actors
                            .putHeaders(externalMessage.getInternalHeaders())
                            .build())
                    .andThen(signal::setDittoHeaders)
                    .apply(mappedInboundMessage);

            // This message is important to check if a command is accepted for a specific connection, as this happens
            // quite a lot this is going to the debug level. Use best with a connection-id filter.
            log.debug("Message successfully mapped to signal: '{}'. Passing to conciergeForwarder",
                    adjustedSignal.getType());
            conciergeForwarder.tell(adjustedSignal, getSelf());
        },
                () -> log.debug("Message mapping returned null, message is dropped."),
                exception -> this.handleException(exception, externalMessage),
                inboundMapped, inboundDropped, InfoProviderFactory.forExternalMessage(externalMessage));

        messageMappingProcessor.process(messageWithAuthSubject, inboundMappingHandlers);
    }

    private void enhanceLogUtil(final WithDittoHeaders<?> signal) {
        ConnectionLogUtil.enhanceLogWithCorrelationIdAndConnectionId(log, signal, connectionId);
    }

    private void handleDittoRuntimeException(final DittoRuntimeException exception) {
        handleDittoRuntimeException(exception, exception.getDittoHeaders());
    }

    private void handleDittoRuntimeException(final DittoRuntimeException exception,
            final Map<String, String> externalHeaders) {

        final ThingErrorResponse errorResponse = convertExceptionToErrorResponse(exception, externalHeaders);

        enhanceLogUtil(exception);

        log.info("Got DittoRuntimeException '{}' when ExternalMessage was processed: {} - {}",
                exception.getErrorCode(), exception.getMessage(), exception.getDescription().orElse(""));

        if (log.isDebugEnabled()) {
            final String stackTrace = stackTraceAsString(exception);
            log.info("Got DittoRuntimeException '{}' when ExternalMessage was processed: {} - {}. StackTrace: {}",
                    exception.getErrorCode(), exception.getMessage(), exception.getDescription().orElse(""),
                    stackTrace);
        }

        handleCommandResponse(errorResponse, exception);
    }

    private ThingErrorResponse convertExceptionToErrorResponse(final DittoRuntimeException exception,
            final Map<String, String> externalHeaders) {

        final DittoHeaders mergedDittoHeaders = DittoHeaders.newBuilder(exception.getDittoHeaders())
                .putHeaders(externalHeaders)
                .build();

        /*
         * Truncate headers to send in an error response.
         * This is necessary because the consumer actor and the publisher actor may not reside in the same connectivity
         * instance due to cluster routing.
         */
        final DittoHeaders truncatedHeaders = mergedDittoHeaders.truncate(limitsConfig.getHeadersMaxSize());
        return getThingId(exception)
                .map(thingId -> ThingErrorResponse.of(thingId, exception, truncatedHeaders))
                .orElseGet(() -> ThingErrorResponse.of(exception, truncatedHeaders));
    }

    private static Optional<ThingId> getThingId(final DittoRuntimeException e) {
        return Optional.ofNullable(e.getDittoHeaders().get(MessageHeaderDefinition.THING_ID.getKey())).map(ThingId::of);
    }

    private static String stackTraceAsString(final DittoRuntimeException exception) {
        final StringWriter stringWriter = new StringWriter();
        exception.printStackTrace(new PrintWriter(stringWriter));
        return stringWriter.toString();
    }

    private void handleCommandResponse(final CommandResponse<?> response) {
        this.handleCommandResponse(response, null);
    }

    private void handleCommandResponse(final CommandResponse<?> response,
            @Nullable final DittoRuntimeException exception) {
        enhanceLogUtil(response);
        recordResponse(response, exception);

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
            responseDroppedMonitor.success(response,
                    "Dropped response since requester did not require response via Header {0}",
                    DittoHeaderDefinition.RESPONSE_REQUIRED);
        }
    }

    private void recordResponse(final CommandResponse<?> response, @Nullable final DittoRuntimeException exception) {
        if (isSuccessResponse(response)) {
            responseDispatchedMonitor.success(response);
        } else {
            responseDispatchedMonitor.failure(response, exception);
        }
    }

    private boolean isSuccessResponse(final CommandResponse<?> response) {
        return response.getStatusCodeValue() < HttpStatusCode.BAD_REQUEST.toInt();
    }

    private void handleOutboundSignal(final OutboundSignal outbound) {
        enhanceLogUtil(outbound.getSource());
        log.debug("Handling outbound signal: {}", outbound.getSource());
        mapToExternalMessage(outbound);
    }

    private void forwardToPublisherActor(final OutboundSignal.WithExternalMessage mappedOutboundSignal) {
        clientActor.forward(new PublishMappedMessage(mappedOutboundSignal), getContext());
    }

    /**
     * Is called for responses or errors which were directly sent to the mapping actor as a response.
     *
     * @param signal the response/error
     */
    private void handleSignal(final Signal<?> signal) {
        // map to outbound signal without authorized target (responses and errors are only sent to its origin)
        log.debug("Handling raw signal: {}", signal);
        handleOutboundSignal(OutboundSignalFactory.newOutboundSignal(signal, Collections.emptyList()));
    }

    private void mapToExternalMessage(final OutboundSignal outbound) {
        final Set<ConnectionMonitor> outboundMapped = getMonitorsForMappedSignal(outbound, connectionId);
        final Set<ConnectionMonitor> outboundDropped = getMonitorsForDroppedSignal(outbound, connectionId);

        final MappingResultHandler<ExternalMessage> outboundMappingResultHandler = new OutboundMappingResultHandler(
                mappedOutboundMessage -> {
                    final OutboundSignal.WithExternalMessage withExternalMessage =
                            replaceTargetAddressPlaceholders.apply(outbound, mappedOutboundMessage);
                    forwardToPublisherActor(withExternalMessage);
                },
                () -> log.debug("Message mapping returned null, message is dropped."),
                exception -> {
                    if (exception instanceof DittoRuntimeException) {
                        final DittoRuntimeException e = (DittoRuntimeException) exception;
                        log.info("Got DittoRuntimeException during processing Signal: {} - {}", e.getMessage(),
                                e.getDescription().orElse(""));
                    } else {
                        log.warning("Got unexpected exception during processing Signal: {}", exception.getMessage());
                    }
                }, outboundMapped, outboundDropped, InfoProviderFactory.forSignal(outbound.getSource()));

        messageMappingProcessor.process(outbound, outboundMappingResultHandler);
    }

    private Set<ConnectionMonitor> getMonitorsForDroppedSignal(final OutboundSignal outbound,
            final ConnectionId connectionId) {
        return getMonitorsForOutboundSignal(outbound, connectionId, DROPPED, LogType.DROPPED, responseDroppedMonitor);
    }

    private Set<ConnectionMonitor> getMonitorsForMappedSignal(final OutboundSignal outbound,
            final ConnectionId connectionId) {
        return getMonitorsForOutboundSignal(outbound, connectionId, MAPPED, LogType.MAPPED, responseMappedMonitor);
    }

    private Set<ConnectionMonitor> getMonitorsForOutboundSignal(final OutboundSignal outbound,
            final ConnectionId connectionId, final MetricType metricType, final LogType logType,
            final ConnectionMonitor responseMonitor) {
        if (outbound.getSource() instanceof CommandResponse) {
            return Collections.singleton(responseMonitor);
        } else {
            return outbound.getTargets()
                    .stream()
                    .map(Target::getOriginalAddress)
                    .map(address -> connectionMonitorRegistry.getMonitor(connectionId, metricType,
                            MetricDirection.OUTBOUND,
                            logType, LogCategory.TARGET, address))
                    .collect(Collectors.toSet());
        }
    }

    /**
     * Helper class that replaces thing + topic placeholders in target addresses. This is done here and not in
     * ConnectionActor because we have the topic at hand not before the mapping was done.
     */
    static final class PlaceholderInTargetAddressSubstitution
            implements BiFunction<OutboundSignal, ExternalMessage, OutboundSignal.WithExternalMessage> {

        private static final HeadersPlaceholder HEADERS_PLACEHOLDER = PlaceholderFactory.newHeadersPlaceholder();
        private static final ThingPlaceholder THING_PLACEHOLDER = PlaceholderFactory.newThingPlaceholder();
        private static final TopicPathPlaceholder TOPIC_PLACEHOLDER = PlaceholderFactory.newTopicPathPlaceholder();

        private PlaceholderInTargetAddressSubstitution() {

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
                        PlaceholderFactory.newPlaceholderResolver(HEADERS_PLACEHOLDER, externalMessage.getHeaders()),
                        PlaceholderFactory.newPlaceholderResolver(THING_PLACEHOLDER,
                                outboundSignal.getSource().getEntityId()),
                        PlaceholderFactory.newPlaceholderResolver(TOPIC_PLACEHOLDER, topicPathOpt.orElse(null))
                );

                final List<Target> targets = outboundSignal.getTargets().stream().map(t -> {
                    final String address = PlaceholderFilter.applyOrElseRetain(t.getAddress(), expressionResolver);
                    return ConnectivityModelFactory.newTarget(t, address, t.getQos().orElse(null));
                }).collect(Collectors.toList());
                final OutboundSignal modifiedOutboundSignal =
                        OutboundSignalFactory.newOutboundSignal(outboundSignal.getSource(), targets);
                return OutboundSignalFactory.newMappedOutboundSignal(modifiedOutboundSignal,
                        setInternalHeaders(outboundSignal, externalMessage));
            } else {
                return OutboundSignalFactory.newMappedOutboundSignal(outboundSignal,
                        setInternalHeaders(outboundSignal, externalMessage));
            }
        }

        private static ExternalMessage setInternalHeaders(final OutboundSignal outboundSignal,
                final ExternalMessage externalMessage) {
            return ExternalMessageFactory.newExternalMessageBuilder(externalMessage)
                    .withInternalHeaders(outboundSignal.getSource().getDittoHeaders())
                    .build();
        }

    }

    static final class PlaceholderSubstitution implements UnaryOperator<ExternalMessage> {

        @Override
        public ExternalMessage apply(final ExternalMessage externalMessage) {
            final AuthorizationContext authorizationContext = getAuthorizationContextFromMessage(externalMessage);
            final AuthorizationContext filteredContext =
                    PlaceholderFilter.applyHeadersPlaceholderToAuthContext(authorizationContext,
                            externalMessage.getHeaders());
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

        private final ConnectionId connectionId;

        private AdjustHeaders(final ConnectionId connectionId) {
            this.connectionId = connectionId;
        }

        @Override
        public DittoHeaders apply(final ExternalMessage externalMessage, final DittoHeaders dittoHeaders) {
            final DittoHeadersBuilder dittoHeadersBuilder = dittoHeaders.toBuilder();

            final String beforeMapping = externalMessage
                    .findHeader(DittoHeaderDefinition.AUTHORIZATION_SUBJECTS.getKey())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Missing header " + DittoHeaderDefinition.AUTHORIZATION_SUBJECTS.getKey()));
            // do not use dittoHeadersBuilder#authorizationSubjects(beforeMapping)
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
     * Helper class applying the {@link EnforcementFilter} of the passed in {@link ExternalMessage} by throwing a {@link
     * ConnectionSignalIdEnforcementFailedException} if the enforcement failed.
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
                        signal.getEntityId(), enforcementFilter);
                enforcementFilter.match(signal.getEntityId(), signal.getDittoHeaders());
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

                final ExpressionResolver expressionResolver = PlaceholderFactory.newExpressionResolver(
                        PlaceholderFactory.newPlaceholderResolver(HEADERS_PLACEHOLDER, dittoHeaders),
                        PlaceholderFactory.newPlaceholderResolver(THING_PLACEHOLDER, signal.getEntityId()),
                        PlaceholderFactory.newPlaceholderResolver(TOPIC_PLACEHOLDER,
                                inboundExternalMessage.getTopicPath())
                );

                final DittoHeadersBuilder dittoHeadersBuilder = dittoHeaders.toBuilder();
                mapping.getMapping().entrySet().stream()
                        .flatMap(e -> PlaceholderFilter.applyWithDeletion(e.getValue(), expressionResolver)
                                .map(resolvedValue -> Stream.of(newEntry(e.getKey(), resolvedValue)))
                                .orElseGet(Stream::empty)
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
