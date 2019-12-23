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
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.common.ConditionChecker;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectionSignalIdEnforcementFailedException;
import org.eclipse.ditto.model.connectivity.EnforcementFilter;
import org.eclipse.ditto.model.connectivity.LogCategory;
import org.eclipse.ditto.model.connectivity.LogType;
import org.eclipse.ditto.model.connectivity.MetricDirection;
import org.eclipse.ditto.model.connectivity.MetricType;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.messages.MessageHeaderDefinition;
import org.eclipse.ditto.model.placeholders.ExpressionResolver;
import org.eclipse.ditto.model.placeholders.PlaceholderFilter;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.base.config.limits.DefaultLimitsConfig;
import org.eclipse.ditto.services.base.config.limits.LimitsConfig;
import org.eclipse.ditto.services.connectivity.mapping.MappingConfig;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientActor.PublishMappedMessage;
import org.eclipse.ditto.services.connectivity.messaging.config.DittoConnectivityConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.MonitoringConfig;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.DefaultConnectionMonitorRegistry;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.InfoProviderFactory;
import org.eclipse.ditto.services.connectivity.util.ConnectionLogUtil;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.MappedInboundExternalMessage;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.models.connectivity.OutboundSignalFactory;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.akka.controlflow.AbstractGraphActor;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.base.WithId;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import scala.util.Either;
import scala.util.Left;
import scala.util.Right;

/**
 * This Actor processes incoming {@link Signal}s and dispatches them.
 */
public final class MessageMappingProcessorActor extends
        AbstractGraphActor<MessageMappingProcessorActor.OutboundSignalWithId, OutboundSignal> {

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
    private final MappingConfig mappingConfig;
    private final DefaultConnectionMonitorRegistry connectionMonitorRegistry;
    private final ConnectionMonitor responseDispatchedMonitor;
    private final ConnectionMonitor responseDroppedMonitor;
    private final ConnectionMonitor responseMappedMonitor;

    @SuppressWarnings("unused")
    private MessageMappingProcessorActor(final ActorRef conciergeForwarder,
            final ActorRef clientActor,
            final MessageMappingProcessor messageMappingProcessor,
            final ConnectionId connectionId) {

        super(OutboundSignal.class);

        this.conciergeForwarder = conciergeForwarder;
        this.clientActor = clientActor;
        this.messageMappingProcessor = messageMappingProcessor;
        this.connectionId = connectionId;

        final DefaultScopedConfig dittoScoped =
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config());
        this.limitsConfig = DefaultLimitsConfig.of(dittoScoped);

        final DittoConnectivityConfig connectivityConfig = DittoConnectivityConfig.of(dittoScoped);
        final MonitoringConfig monitoringConfig = connectivityConfig.getMonitoringConfig();
        mappingConfig = connectivityConfig.getConnectionConfig().getMappingConfig();

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
    protected int getBufferSize() {
        return mappingConfig.getBufferSize();
    }

    @Override
    protected int getParallelism() {
        return mappingConfig.getParallelism();
    }

    @Override
    protected void preEnhancement(final ReceiveBuilder receiveBuilder) {
        receiveBuilder
                .match(ExternalMessage.class, this::handleInboundMessage)   // ExternalMessages are handled directly, without first putting them into a stream
                .match(CommandResponse.class, this::handleCommandResponse)  // same for command responses
                .match(Signal.class, this::handleSignal)                    // and all other Signals
                .match(Status.Failure.class, f -> log.warning("Got failure with cause {}: {}",
                        f.cause().getClass().getSimpleName(), f.cause().getMessage()));
    }

    @Override
    protected void handleDittoRuntimeException(final DittoRuntimeException exception) {
        final ThingErrorResponse errorResponse = convertExceptionToErrorResponse(exception);
        handleThingErrorResponse(exception, errorResponse);
    }

    @Override
    protected OutboundSignalWithId mapMessage(final OutboundSignal message) {
        // currently no need to further enhance the OutboundSignal with contextual information, so just use it:
        return new OutboundSignalWithId(message, message.getSource().getEntityId());
    }

    @Override
    protected Flow<OutboundSignalWithId, OutboundSignalWithId, NotUsed> processMessageFlow() {
        // for now, don't pre-process the OutboundSignals:
        return Flow.create();
        // this is probably the place to do the "extra" fields enrichment
    }

    @Override
    protected Sink<OutboundSignalWithId, ?> processedMessageSink() {
        // simply handle each OutboundSignals:
        return Sink.foreach(this::handleOutboundSignal);
    }

    private void handleInboundMessage(final ExternalMessage externalMessage) {
        ConditionChecker.checkNotNull(externalMessage);
        final String correlationId = externalMessage.getHeaders().get(DittoHeaderDefinition.CORRELATION_ID.getKey());
        ConnectionLogUtil.enhanceLogWithCorrelationIdAndConnectionId(log, correlationId, connectionId);
        log.debug("Handling ExternalMessage: {}", externalMessage);
        try {
            mapExternalMessageToSignalAndForwardToConcierge(externalMessage);
        } catch (final Exception e) {
            handleException(e, externalMessage, getAuthorizationContext(externalMessage).orElse(null));
        }
    }

    private void handleException(final Exception e, final ExternalMessage message,
            @Nullable final AuthorizationContext authorizationContext) {
        if (e instanceof DittoRuntimeException) {
            final DittoRuntimeException dittoRuntimeException = (DittoRuntimeException) e;
            responseMappedMonitor.getLogger()
                    .failure("Got exception {0} when processing external message: {1}",
                            dittoRuntimeException.getErrorCode(),
                            e.getMessage());
            final ThingErrorResponse thingErrorResponse = convertExceptionToErrorResponse(dittoRuntimeException);
            final DittoHeaders mappedHeaders =
                    applyInboundHeaderMapping(thingErrorResponse, message, authorizationContext,
                            message.getTopicPath().orElse(null), message.getInternalHeaders());
            handleThingErrorResponse(dittoRuntimeException, thingErrorResponse.setDittoHeaders(mappedHeaders));
        } else {
            responseMappedMonitor.getLogger()
                    .failure("Got unknown exception when processing external message: {1}", e.getMessage());
            log.warning("Got <{}> when message was processed: <{}>", e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private void mapExternalMessageToSignalAndForwardToConcierge(final ExternalMessage externalMessage) {
        messageMappingProcessor.process(externalMessage,
                handleMappingResult(externalMessage, getAuthorizationContextOrThrow(externalMessage)));
    }

    private MappingResultHandler<MappedInboundExternalMessage> handleMappingResult(
            final ExternalMessage incomingMessage,
            final AuthorizationContext authorizationContext) {
        final String source = incomingMessage.getSourceAddress().orElse("unknown");
        final ConnectionMonitor inboundMapped = connectionMonitorRegistry.forInboundMapped(connectionId, source);
        final ConnectionMonitor inboundDropped = connectionMonitorRegistry.forInboundDropped(connectionId, source);

        return new InboundMappingResultHandler(
                mappedInboundMessage -> {
                    final Signal<?> signal = mappedInboundMessage.getSignal();
                    enhanceLogUtil(signal);
                    // the above throws an exception if signal id enforcement fails
                    final DittoHeaders mappedHeaders =
                            applyInboundHeaderMapping(signal, incomingMessage, authorizationContext,
                                    mappedInboundMessage.getTopicPath(), incomingMessage.getInternalHeaders());

                    final Signal<?> adjustedSignal = signal.setDittoHeaders(mappedHeaders);

                    enhanceLogUtil(adjustedSignal);
                    // enforce signal ID after header mapping was done
                    connectionMonitorRegistry.forInboundEnforced(connectionId, source)
                            .wrapExecution(adjustedSignal)
                            .execute(() -> applySignalIdEnforcement(incomingMessage, signal));

                    // This message is important to check if a command is accepted for a specific connection, as this happens
                    // quite a lot this is going to the debug level. Use best with a connection-id filter.
                    log.debug("Message successfully mapped to signal: '{}'. Passing to conciergeForwarder",
                            adjustedSignal.getType());
                    conciergeForwarder.tell(adjustedSignal, getSelf());
                },
                () -> log.debug("Message mapping returned null, message is dropped."),
                exception -> this.handleException(exception, incomingMessage, authorizationContext),
                inboundMapped,
                inboundDropped,
                InfoProviderFactory.forExternalMessage(incomingMessage)
        );
    }

    private void enhanceLogUtil(final WithDittoHeaders<?> signal) {
        ConnectionLogUtil.enhanceLogWithCorrelationIdAndConnectionId(log, signal, connectionId);
    }

    private void handleThingErrorResponse(final DittoRuntimeException exception,
            final ThingErrorResponse errorResponse) {

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

    private ThingErrorResponse convertExceptionToErrorResponse(final DittoRuntimeException exception) {

        /*
         * Truncate headers to send in an error response.
         * This is necessary because the consumer actor and the publisher actor may not reside in the same connectivity
         * instance due to cluster routing.
         */
        final DittoHeaders truncatedHeaders = exception.getDittoHeaders().truncate(limitsConfig.getHeadersMaxSize());
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

    private void forwardToPublisherActor(final OutboundSignal.Mapped mappedOutboundSignal) {
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

        final OutboundMappingResultHandler outboundMappingResultHandler = new OutboundMappingResultHandler(
                this::forwardToPublisherActor,
                () -> log.debug("Message mapping returned null, message is dropped."),
                exception -> {
                    if (exception instanceof DittoRuntimeException) {
                        final DittoRuntimeException e = (DittoRuntimeException) exception;
                        log.info("Got DittoRuntimeException during processing Signal: {} - {}", e.getMessage(),
                                e.getDescription().orElse(""));
                    } else {
                        log.warning("Got unexpected exception during processing Signal: {}", exception.getMessage());
                    }
                },
                outboundMapped,
                outboundDropped,
                InfoProviderFactory.forSignal(outbound.getSource())
        );

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

    private static AuthorizationContext getAuthorizationContextOrThrow(final ExternalMessage externalMessage) {
        final Either<RuntimeException, AuthorizationContext> result = getAuthorizationContextAsEither(externalMessage);
        if (result.isRight()) {
            return result.right().get();
        } else {
            throw result.left().get();
        }
    }

    private static Optional<AuthorizationContext> getAuthorizationContext(final ExternalMessage externalMessage) {
        final Either<RuntimeException, AuthorizationContext> result = getAuthorizationContextAsEither(externalMessage);
        if (result.isRight()) {
            return Optional.of(result.right().get());
        } else {
            return Optional.empty();
        }
    }

    private static Either<RuntimeException, AuthorizationContext> getAuthorizationContextAsEither(
            final ExternalMessage externalMessage) {
        return externalMessage.getAuthorizationContext()
                .filter(authorizationContext -> !authorizationContext.isEmpty())
                .<Either<RuntimeException, AuthorizationContext>>map(authorizationContext -> {
                    try {
                        return new Right<>(PlaceholderFilter.applyHeadersPlaceholderToAuthContext(authorizationContext,
                                externalMessage.getHeaders()));
                    } catch (final RuntimeException e) {
                        return new Left<>(e);
                    }
                })
                .orElseGet(() ->
                        new Left<>(new IllegalArgumentException("No nonempty authorization context is available")));

    }

    /**
     * Helper applying the {@link EnforcementFilter} of the passed in {@link ExternalMessage} by throwing a {@link
     * ConnectionSignalIdEnforcementFailedException} if the enforcement failed.
     */
    private void applySignalIdEnforcement(final ExternalMessage externalMessage, final Signal<?> signal) {
        externalMessage.getEnforcementFilter().ifPresent(enforcementFilter -> {
            log.debug("Connection Signal ID Enforcement enabled - matching Signal ID <{}> with filter: {}",
                    signal.getEntityId(), enforcementFilter);
            enforcementFilter.match(signal.getEntityId(), signal.getDittoHeaders());
        });
    }

    /**
     * Helper applying the {@link org.eclipse.ditto.model.connectivity.HeaderMapping}.
     */
    private DittoHeaders applyInboundHeaderMapping(final Signal<?> signal,
            final ExternalMessage externalMessage,
            @Nullable final AuthorizationContext authorizationContext,
            @Nullable TopicPath topicPath,
            final DittoHeaders extraInternalHeaders) {

        return externalMessage.getHeaderMapping()
                .map(mapping -> {

                    final ExpressionResolver expressionResolver =
                            Resolvers.forInbound(externalMessage, signal, topicPath, authorizationContext);

                    final DittoHeadersBuilder dittoHeadersBuilder = signal.getDittoHeaders().toBuilder();

                    // Add mapped external headers as if they were injected into the Adaptable.
                    final Map<String, String> mappedExternalHeaders = mapping.getMapping()
                            .entrySet()
                            .stream()
                            .flatMap(e -> PlaceholderFilter.applyOrElseDelete(e.getValue(), expressionResolver)
                                    .map(resolvedValue ->
                                            Stream.of(new AbstractMap.SimpleEntry<>(e.getKey(), resolvedValue)))
                                    .orElseGet(Stream::empty)
                            )
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                    dittoHeadersBuilder.putHeaders(messageMappingProcessor.getHeaderTranslator()
                            .fromExternalHeaders(mappedExternalHeaders));

                    final String correlationIdKey = DittoHeaderDefinition.CORRELATION_ID.getKey();
                    final boolean hasCorrelationId = mapping.getMapping().containsKey(correlationIdKey) ||
                            signal.getDittoHeaders().getCorrelationId().isPresent();

                    final DittoHeaders newHeaders =
                            appendInternalHeaders(dittoHeadersBuilder, authorizationContext, extraInternalHeaders,
                                    !hasCorrelationId).build();

                    LogUtil.enhanceLogWithCorrelationId(log, newHeaders);
                    log.debug("Result of header mapping <{}> are these headers: {}", mapping, newHeaders);
                    return newHeaders;
                })
                .orElseGet(() ->
                        appendInternalHeaders(
                                signal.getDittoHeaders().toBuilder(),
                                authorizationContext,
                                extraInternalHeaders,
                                !signal.getDittoHeaders().getCorrelationId().isPresent()
                        ).build()
                );
    }

    private DittoHeadersBuilder appendInternalHeaders(final DittoHeadersBuilder builder,
            @Nullable final AuthorizationContext authorizationContext,
            final DittoHeaders extraInternalHeaders,
            final boolean appendRandomCorrelationId) {
        builder.putHeaders(extraInternalHeaders).origin(connectionId);
        if (authorizationContext != null) {
            builder.authorizationContext(authorizationContext);
        }
        if (appendRandomCorrelationId && !extraInternalHeaders.getCorrelationId().isPresent()) {
            builder.correlationId(UUID.randomUUID().toString());
        }
        return builder;
    }

    /**
     *
     */
    static class OutboundSignalWithId implements OutboundSignal, WithId {

        private final OutboundSignal delegate;
        private final EntityId entityId;

        private OutboundSignalWithId(final OutboundSignal delegate, final EntityId entityId) {
            this.delegate = delegate;
            this.entityId = entityId;
        }

        @Override
        public Signal<?> getSource() {
            return delegate.getSource();
        }

        @Override
        public List<Target> getTargets() {
            return delegate.getTargets();
        }

        @Override
        public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> predicate) {
            return delegate.toJson(schemaVersion, predicate);
        }

        @Override
        public EntityId getEntityId() {
            return entityId;
        }
    }

}
