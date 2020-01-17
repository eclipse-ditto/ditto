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
import java.time.Duration;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.common.ConditionChecker;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.SignalEnrichmentFailedException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectionSignalIdEnforcementFailedException;
import org.eclipse.ditto.model.connectivity.EnforcementFilter;
import org.eclipse.ditto.model.connectivity.FilteredTopic;
import org.eclipse.ditto.model.connectivity.LogCategory;
import org.eclipse.ditto.model.connectivity.LogType;
import org.eclipse.ditto.model.connectivity.MetricDirection;
import org.eclipse.ditto.model.connectivity.MetricType;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.messages.MessageHeaderDefinition;
import org.eclipse.ditto.model.placeholders.ExpressionResolver;
import org.eclipse.ditto.model.placeholders.PlaceholderFilter;
import org.eclipse.ditto.model.query.criteria.Criteria;
import org.eclipse.ditto.model.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.model.query.things.ThingPredicateVisitor;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.base.config.limits.DefaultLimitsConfig;
import org.eclipse.ditto.services.base.config.limits.LimitsConfig;
import org.eclipse.ditto.services.connectivity.mapping.ConnectivitySignalEnrichmentProvider;
import org.eclipse.ditto.services.connectivity.mapping.MappingConfig;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientActor.PublishMappedMessage;
import org.eclipse.ditto.services.connectivity.messaging.config.DittoConnectivityConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.MonitoringConfig;
import org.eclipse.ditto.services.connectivity.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.internal.ImmutableConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.DefaultConnectionMonitorRegistry;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.InfoProviderFactory;
import org.eclipse.ditto.services.connectivity.util.ConnectionLogUtil;
import org.eclipse.ditto.services.models.concierge.streaming.StreamingType;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.MappedInboundExternalMessage;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.models.connectivity.OutboundSignalFactory;
import org.eclipse.ditto.services.models.signalenrichment.SignalEnrichmentFacade;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.akka.controlflow.AbstractGraphActor;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.base.WithId;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.events.things.ThingEventToThingConverter;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.actor.Status;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Pair;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import scala.util.Either;
import scala.util.Left;
import scala.util.Right;

/**
 * This Actor processes incoming {@link Signal}s and dispatches them.
 */
public final class MessageMappingProcessorActor
        extends AbstractGraphActor<MessageMappingProcessorActor.OutboundSignalWithId, OutboundSignal> {

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
    private final CompletionStage<SignalEnrichmentFacade> signalEnrichmentFacade;

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
        mappingConfig = connectivityConfig.getMappingConfig();

        this.connectionMonitorRegistry = DefaultConnectionMonitorRegistry.fromConfig(monitoringConfig);
        responseDispatchedMonitor = connectionMonitorRegistry.forResponseDispatched(connectionId);
        responseDroppedMonitor = connectionMonitorRegistry.forResponseDropped(connectionId);
        responseMappedMonitor = connectionMonitorRegistry.forResponseMapped(connectionId);
        signalEnrichmentFacade = getSignalEnrichmentFacade(
                getContext().getSystem().actorSelection(mappingConfig.getSignalEnrichmentProviderPath()),
                connectionId, getContext().getParent(), getSelf());
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
                .match(ExternalMessage.class,
                        this::handleInboundMessage)   // ExternalMessages are handled directly, without first putting them into a stream
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
        return new OutboundSignalWithId(message, message.getSource().getEntityId());
    }

    @Override
    protected Flow<OutboundSignalWithId, OutboundSignalWithId, NotUsed> processMessageFlow() {
        // Enrich outbound signals by extra fields if necessary.
        return splitByTargetExtraFieldsFlow()
                .mapAsync(mappingConfig.getParallelism(), this::enrichAndFilterSignal)
                .mapConcat(x -> x);
    }

    /**
     * Create a flow that splits 1 outbound signal into many as follows.
     * <ol>
     * <li>
     *   Targets with matching filtered topics without extra fields are grouped into 1 outbound signal, followed by
     * </li>
     * <li>one outbound signal for each target with a matching filtered topic with extra fields.</li>
     * </ol>
     * The matching filtered topic is attached in the latter case.
     * Consequently, for each outbound signal leaving this flow, if it has a filtered topic attached,
     * then it has 1 unique target with a matching topic with extra fields.
     * This satisfies the precondition of {@code this#enrichAndFilterSignal}.
     *
     * @return the flow.
     */
    private Flow<OutboundSignalWithId, Pair<OutboundSignalWithId, FilteredTopic>, NotUsed>
    splitByTargetExtraFieldsFlow() {
        return Flow.<OutboundSignalWithId>create()
                .mapConcat(outboundSignal -> {
                    final Pair<List<Target>, List<Pair<Target, FilteredTopic>>> splitTargets =
                            splitTargetsByExtraFields(outboundSignal);

                    final boolean shouldSendSignalWithoutExtraFields = !splitTargets.first().isEmpty() ||
                            outboundSignal.getSource().getDittoHeaders().getReplyTarget().isPresent();
                    final Stream<Pair<OutboundSignalWithId, FilteredTopic>> outboundSignalWithoutExtraFields =
                            shouldSendSignalWithoutExtraFields
                                    ? Stream.of(Pair.create(outboundSignal.setTargets(splitTargets.first()), null))
                                    : Stream.empty();

                    final Stream<Pair<OutboundSignalWithId, FilteredTopic>> outboundSignalWithExtraFields =
                            splitTargets.second().stream()
                                    .map(targetAndSelector -> Pair.create(
                                            outboundSignal.setTargets(
                                                    Collections.singletonList(targetAndSelector.first())),
                                            targetAndSelector.second()));

                    return Stream.concat(outboundSignalWithoutExtraFields, outboundSignalWithExtraFields)
                            .collect(Collectors.toList());
                });
    }

    @Override
    protected Sink<OutboundSignalWithId, ?> processedMessageSink() {
        return Sink.foreach(this::handleOutboundSignal);
    }

    // Called inside stream; must be thread-safe
    // precondition: whenever filteredTopic != null, it contains an extra fields
    private CompletionStage<Collection<OutboundSignalWithId>> enrichAndFilterSignal(
            final Pair<OutboundSignalWithId, FilteredTopic> outboundSignalWithExtraFields) {

        final OutboundSignalWithId outboundSignal = outboundSignalWithExtraFields.first();
        final FilteredTopic filteredTopic = outboundSignalWithExtraFields.second();
        if (filteredTopic == null || !filteredTopic.getExtraFields().isPresent()) {
            return CompletableFuture.completedFuture(Collections.singletonList(outboundSignal));
        }
        final JsonFieldSelector extraFields = filteredTopic.getExtraFields().get();
        final Target target = outboundSignal.getTargets().get(0);

        final ThingId thingId = ThingId.of(outboundSignal.getEntityId());
        final DittoHeaders headers = outboundSignal.getSource()
                .getDittoHeaders()
                .toBuilder()
                .authorizationContext(target.getAuthorizationContext())
                .build();
        final CompletionStage<JsonObject> extraFuture =
                signalEnrichmentFacade.thenCompose(facade ->
                        facade.retrievePartialThing(thingId, extraFields, headers, outboundSignal.getSource()));

        return extraFuture.thenApply(outboundSignal::setExtra)
                .thenApply(outboundSignalWithExtra -> applyFilter(outboundSignalWithExtra, filteredTopic))
                .exceptionally(error -> {
                    logger.withCorrelationId(outboundSignal.getSource())
                            .warning("Could not retrieve extra data due to: {} {}",
                            error.getClass().getSimpleName(), error.getMessage());
                    // recover from all errors to keep message-mapping-stream running despite enrichment failures
                    return Collections.singletonList(recoverFromEnrichmentError(outboundSignal, target, error));
                });
    }

    private static Collection<OutboundSignalWithId> applyFilter(final OutboundSignalWithId outboundSignalWithExtra,
            final FilteredTopic filteredTopic) {

        final Optional<String> filter = filteredTopic.getFilter();
        final Optional<JsonFieldSelector> extraFields = filteredTopic.getExtraFields();
        if (filter.isPresent() && extraFields.isPresent()) {
            // evaluate filter criteria again if signal enrichment is involved.
            final Criteria criteria = QueryFilterCriteriaFactory.modelBased()
                    .filterCriteria(filter.get(), outboundSignalWithExtra.getSource().getDittoHeaders());
            return outboundSignalWithExtra.getExtra()
                    .flatMap(extra -> {
                        final Signal<?> signal = outboundSignalWithExtra.getSource();
                        return ThingEventToThingConverter.mergeThingWithExtraFields(signal, extraFields.get(), extra)
                                .filter(ThingPredicateVisitor.apply(criteria))
                                .map(thing -> outboundSignalWithExtra);
                    })
                    .map(Collections::singletonList)
                    .orElseGet(Collections::emptyList);
        } else {
            // no signal enrichment: filtering is already done in SignalFilter since there is no ignored field
            return Collections.singletonList(outboundSignalWithExtra);
        }
    }

    // Called inside future; must be thread-safe
    private OutboundSignalWithId recoverFromEnrichmentError(final OutboundSignalWithId outboundSignal,
            final Target target, final Throwable error) {

        // getContext().getParent() is thread-safe; it is okay to call inside a future
        final ActorRef parentClientActor = getContext().getParent();
        // show enrichment failure in the connection logs
        logEnrichmentFailure(outboundSignal, connectionId, error);
        // show enrichment failure in service logs according to severity
        if (error instanceof ThingNotAccessibleException) {
            // This error should be rare but possible due to user action; log on INFO level
            log.info("Enrichment of  <{}> failed for <{}> due to <{}>", outboundSignal.getSource().getClass(),
                    outboundSignal.getEntityId(), error);
        } else {
            // This error should not have happened during normal operation.
            // There is a (possibly transient) problem with the Ditto cluster. Request parent to restart.
            log.error("Enrichment of <{}> failed due to <{}>", outboundSignal, error);
            final ConnectionFailure connectionFailure =
                    new ImmutableConnectionFailure(getSelf(), error, "Signal enrichment failed");
            parentClientActor.tell(connectionFailure, getSelf());
        }
        return outboundSignal.setTargets(Collections.singletonList(target));
    }

    private void logEnrichmentFailure(final OutboundSignal outboundSignal, final ConnectionId connectionId,
            final Throwable error) {

        final DittoRuntimeException errorToLog;
        if (error instanceof DittoRuntimeException) {
            errorToLog = SignalEnrichmentFailedException.dueTo((DittoRuntimeException) error);
        } else {
            errorToLog = SignalEnrichmentFailedException.newBuilder()
                    .dittoHeaders(outboundSignal.getSource().getDittoHeaders())
                    .build();
        }
        getMonitorsForMappedSignal(outboundSignal, connectionId)
                .forEach(monitor -> monitor.failure(outboundSignal.getSource(), errorToLog));
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

    private static CompletionStage<SignalEnrichmentFacade> getSignalEnrichmentFacade(
            final ActorSelection signalEnrichmentProvider,
            final ConnectionId connectionId,
            final ActorRef parentClientActor,
            final ActorRef self) {

        final CompletionStage<SignalEnrichmentFacade> future =
                Patterns.ask(signalEnrichmentProvider, Request.GET_SIGNAL_ENRICHMENT_PROVIDER, Duration.ofSeconds(10L))
                        .thenApply(reply -> {
                            // fail future with ClassCastException if the reply does not have the expected type
                            final ConnectivitySignalEnrichmentProvider provider =
                                    (ConnectivitySignalEnrichmentProvider) reply;
                            return provider.createFacade(connectionId);
                        });

        // Handle errors in a separate stage so that the returned future stayed a failed future on failure.
        future.exceptionally(e -> {
            // If this future fails, then the service has a (possibly transient) problem.
            // Request parent to restart connection.
            final String description = "";
            parentClientActor.tell(new ImmutableConnectionFailure(self, e, description), self);
            return null;
        });

        return future;
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
     * Split the targets of an outbound signal into 2 parts: those without extra fields and those with.
     *
     * @param outboundSignal The outbound signal.
     * @return A pair of lists. The first list contains targets without matching extra fields.
     * The second list contains targets together with their extra fields matching the outbound signal.
     */
    private static Pair<List<Target>, List<Pair<Target, FilteredTopic>>> splitTargetsByExtraFields(
            final OutboundSignal outboundSignal) {

        final Optional<StreamingType> streamingTypeOptional = StreamingType.fromSignal(outboundSignal.getSource());
        if (streamingTypeOptional.isPresent()) {
            // Find targets with a matching topic with extra fields
            final StreamingType streamingType = streamingTypeOptional.get();
            final List<Target> targetsWithoutExtraFields = new ArrayList<>(outboundSignal.getTargets().size());
            final List<Pair<Target, FilteredTopic>> targetsWithExtraFields =
                    new ArrayList<>(outboundSignal.getTargets().size());
            for (final Target target : outboundSignal.getTargets()) {
                final Optional<FilteredTopic> matchingExtraFields = target.getTopics()
                        .stream()
                        .filter(filteredTopic -> filteredTopic.getExtraFields().isPresent() &&
                                streamingType == StreamingType.fromTopic(filteredTopic.getTopic().getPubSubTopic()))
                        .findAny();
                if (matchingExtraFields.isPresent()) {
                    targetsWithExtraFields.add(Pair.create(target, matchingExtraFields.get()));
                } else {
                    targetsWithoutExtraFields.add(target);
                }
            }
            return Pair.create(targetsWithoutExtraFields, targetsWithExtraFields);
        } else {
            // The outbound signal has no streaming type: Do not attach extra fields.
            return Pair.create(outboundSignal.getTargets(), Collections.emptyList());
        }
    }

    static class OutboundSignalWithId implements OutboundSignal, WithId {

        private final OutboundSignal delegate;
        private final EntityId entityId;

        @Nullable
        private final JsonObject extra;

        private OutboundSignalWithId(final OutboundSignal delegate, final EntityId entityId) {
            this(delegate, entityId, null);
        }

        private OutboundSignalWithId(final OutboundSignal delegate, final EntityId entityId,
                @Nullable final JsonObject extra) {
            this.delegate = delegate;
            this.entityId = entityId;
            this.extra = extra;
        }

        @Override
        public Optional<JsonObject> getExtra() {
            return Optional.ofNullable(extra);
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

        private OutboundSignalWithId setTargets(final List<Target> targets) {
            return new OutboundSignalWithId(OutboundSignalFactory.newOutboundSignal(delegate.getSource(), targets),
                    entityId);
        }

        private OutboundSignalWithId setExtra(final JsonObject extra) {
            return new OutboundSignalWithId(
                    OutboundSignalFactory.newOutboundSignal(delegate.getSource(), getTargets()),
                    entityId,
                    extra
            );
        }
    }

    /**
     * Request made by this actor.
     */
    public enum Request {

        /**
         * Request a signal-enrichment provider.
         */
        GET_SIGNAL_ENRICHMENT_PROVIDER
    }

}
