/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.SignalEnrichmentFailedException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.FilteredTopic;
import org.eclipse.ditto.model.connectivity.LogCategory;
import org.eclipse.ditto.model.connectivity.LogType;
import org.eclipse.ditto.model.connectivity.MetricDirection;
import org.eclipse.ditto.model.connectivity.MetricType;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.query.criteria.Criteria;
import org.eclipse.ditto.model.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.model.query.things.ThingPredicateVisitor;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocoladapter.ProtocolAdapter;
import org.eclipse.ditto.services.base.config.limits.DefaultLimitsConfig;
import org.eclipse.ditto.services.base.config.limits.LimitsConfig;
import org.eclipse.ditto.services.connectivity.mapping.ConnectivitySignalEnrichmentProvider;
import org.eclipse.ditto.services.connectivity.mapping.MappingConfig;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientActor.PublishMappedMessage;
import org.eclipse.ditto.services.connectivity.messaging.config.DittoConnectivityConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.MonitoringConfig;
import org.eclipse.ditto.services.connectivity.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.internal.ImmutableConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.mappingoutcome.MappingOutcome;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.DefaultConnectionMonitorRegistry;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.InfoProviderFactory;
import org.eclipse.ditto.services.connectivity.util.ConnectivityMdcEntryKey;
import org.eclipse.ditto.services.models.concierge.streaming.StreamingType;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal.Mapped;
import org.eclipse.ditto.services.models.connectivity.OutboundSignalFactory;
import org.eclipse.ditto.services.models.signalenrichment.SignalEnrichmentFacade;
import org.eclipse.ditto.services.utils.akka.controlflow.AbstractGraphActor;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.services.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.base.WithId;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.ErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.events.things.ThingEventToThingConverter;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.japi.Pair;
import akka.japi.pf.PFBuilder;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

/**
 * This Actor processes {@link OutboundSignal outbound signals} and dispatches them.
 */
public final class OutboundMappingProcessorActor
        extends AbstractGraphActor<OutboundMappingProcessorActor.OutboundSignalWithId, OutboundSignal> {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "outboundMappingProcessor";

    /**
     * The name of the dispatcher that runs all mapping tasks and all message handling of this actor and its children.
     */
    private static final String MESSAGE_MAPPING_PROCESSOR_DISPATCHER = "message-mapping-processor-dispatcher";

    private final ThreadSafeDittoLoggingAdapter logger;

    private final ActorRef clientActor;
    private final OutboundMappingProcessor outboundMappingProcessor;
    private final ConnectionId connectionId;
    private final MappingConfig mappingConfig;
    private final DefaultConnectionMonitorRegistry connectionMonitorRegistry;
    private final ConnectionMonitor responseDispatchedMonitor;
    private final ConnectionMonitor responseDroppedMonitor;
    private final ConnectionMonitor responseMappedMonitor;
    private final SignalEnrichmentFacade signalEnrichmentFacade;
    private final int processorPoolSize;
    private final DittoRuntimeExceptionToErrorResponseFunction toErrorResponseFunction;

    @SuppressWarnings("unused")
    private OutboundMappingProcessorActor(final ActorRef clientActor,
            final OutboundMappingProcessor outboundMappingProcessor,
            final Connection connection,
            final int processorPoolSize) {

        super(OutboundSignal.class);

        this.clientActor = clientActor;
        this.outboundMappingProcessor = outboundMappingProcessor;
        this.connectionId = connection.getId();

        logger = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this)
                .withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID, connectionId);

        final DefaultScopedConfig dittoScoped =
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config());

        final DittoConnectivityConfig connectivityConfig = DittoConnectivityConfig.of(dittoScoped);
        final MonitoringConfig monitoringConfig = connectivityConfig.getMonitoringConfig();
        mappingConfig = connectivityConfig.getMappingConfig();
        final LimitsConfig limitsConfig = DefaultLimitsConfig.of(dittoScoped);

        connectionMonitorRegistry = DefaultConnectionMonitorRegistry.fromConfig(monitoringConfig);
        responseDispatchedMonitor = connectionMonitorRegistry.forResponseDispatched(connectionId);
        responseDroppedMonitor = connectionMonitorRegistry.forResponseDropped(connectionId);
        responseMappedMonitor = connectionMonitorRegistry.forResponseMapped(connectionId);
        signalEnrichmentFacade =
                ConnectivitySignalEnrichmentProvider.get(getContext().getSystem()).getFacade(connectionId);
        this.processorPoolSize = determinePoolSize(processorPoolSize, mappingConfig.getMaxPoolSize());
        toErrorResponseFunction = DittoRuntimeExceptionToErrorResponseFunction.of(limitsConfig.getHeadersMaxSize());
    }

    private int determinePoolSize(final int connectionPoolSize, final int maxPoolSize) {
        if (connectionPoolSize > maxPoolSize) {
            logger.info("Configured pool size <{}> is greater than the configured max pool size <{}>." +
                    " Will use max pool size <{}>.", connectionPoolSize, maxPoolSize, maxPoolSize);
            return maxPoolSize;
        }
        return connectionPoolSize;
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param clientActor the client actor that created this mapping actor.
     * @param outboundMappingProcessor the MessageMappingProcessor to use for outbound messages.
     * @param connection the connection.
     * @param processorPoolSize how many message processing may happen in parallel per direction (incoming or outgoing).
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef clientActor,
            final OutboundMappingProcessor outboundMappingProcessor,
            final Connection connection,
            final int processorPoolSize) {

        return Props.create(OutboundMappingProcessorActor.class,
                clientActor,
                outboundMappingProcessor,
                connection,
                processorPoolSize
        ).withDispatcher(MESSAGE_MAPPING_PROCESSOR_DISPATCHER);
    }

    @Override
    public Receive createReceive() {
        final PartialFunction<Object, Object> wrapAsOutboundSignal = new PFBuilder<>()
                .match(Acknowledgement.class, this::handleNotExpectedAcknowledgement)
                .match(CommandResponse.class, response -> handleCommandResponse(response, null, getSender()))
                .match(Signal.class, signal -> handleSignal(signal, getSender()))
                .match(DittoRuntimeException.class, this::mapDittoRuntimeException)
                .match(Status.Failure.class, f -> {
                    logger.warning("Got failure with cause {}: {}",
                            f.cause().getClass().getSimpleName(), f.cause().getMessage());
                    return Done.getInstance();
                })
                .matchAny(x -> x)
                .build();

        final PartialFunction<Object, BoxedUnit> doNothingIfDone = new PFBuilder<Object, BoxedUnit>()
                .matchEquals(Done.getInstance(), done -> BoxedUnit.UNIT)
                .build();

        final Receive addToSourceQueue = super.createReceive();

        return new Receive(wrapAsOutboundSignal.andThen(doNothingIfDone.orElse(addToSourceQueue.onMessage())));
    }

    @Override
    protected int getBufferSize() {
        return mappingConfig.getBufferSize();
    }

    private Object handleNotExpectedAcknowledgement(final Acknowledgement acknowledgement) {
        // acknowledgements are not published to targets or reply-targets. this one is mis-routed.
        logger.withCorrelationId(acknowledgement)
                .warning("Received Acknowledgement where non was expected, discarding it: {}", acknowledgement);
        return Done.getInstance();
    }

    private Object mapDittoRuntimeException(final DittoRuntimeException exception) {
        final ErrorResponse<?> errorResponse = toErrorResponseFunction.apply(exception, null);
        return handleErrorResponse(exception, errorResponse, getSender());
    }

    @Override
    protected OutboundSignalWithId mapMessage(final OutboundSignal message) {
        if (message instanceof OutboundSignalWithId) {
            // message contains original sender already
            return (OutboundSignalWithId) message;
        } else {
            return OutboundSignalWithId.of(message, getSender());
        }
    }

    @Override
    protected Sink<OutboundSignalWithId, ?> createSink() {
        // Enrich outbound signals by extra fields if necessary.
        final Flow<OutboundSignalWithId, OutboundSignal.MultiMapped, ?> flow = Flow.<OutboundSignalWithId>create()
                .mapAsync(processorPoolSize, outbound -> toMultiMappedOutboundSignal(
                        Source.single(outbound)
                                .via(splitByTargetExtraFieldsFlow())
                                .mapAsync(mappingConfig.getParallelism(), this::enrichAndFilterSignal)
                                .mapConcat(x -> x)
                                .map(this::handleOutboundSignal)
                                .flatMapConcat(x -> x)
                ))
                .mapConcat(x -> x);
        return flow.to(Sink.foreach(this::forwardToPublisherActor));
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
    private static Flow<OutboundSignalWithId, Pair<OutboundSignalWithId, FilteredTopic>, NotUsed> splitByTargetExtraFieldsFlow() {
        return Flow.<OutboundSignalWithId>create()
                .mapConcat(outboundSignal -> {
                    final Pair<List<Target>, List<Pair<Target, FilteredTopic>>> splitTargets =
                            splitTargetsByExtraFields(outboundSignal);

                    final boolean shouldSendSignalWithoutExtraFields =
                            !splitTargets.first().isEmpty() ||
                                    isTwinCommandResponseWithReplyTarget(outboundSignal.getSource()) ||
                                    outboundSignal.getTargets().isEmpty(); // no target - this is an error response
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


    // Called inside stream; must be thread-safe
    // precondition: whenever filteredTopic != null, it contains an extra fields
    private CompletionStage<Collection<OutboundSignalWithId>> enrichAndFilterSignal(
            final Pair<OutboundSignalWithId, FilteredTopic> outboundSignalWithExtraFields) {

        final OutboundSignalWithId outboundSignal = outboundSignalWithExtraFields.first();
        final FilteredTopic filteredTopic = outboundSignalWithExtraFields.second();
        final Optional<JsonFieldSelector> extraFieldsOptional =
                Optional.ofNullable(filteredTopic).flatMap(FilteredTopic::getExtraFields);
        if (extraFieldsOptional.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.singletonList(outboundSignal));
        }
        final JsonFieldSelector extraFields = extraFieldsOptional.get();
        final Target target = outboundSignal.getTargets().get(0);

        final ThingId thingId = ThingId.of(outboundSignal.getEntityId());
        final DittoHeaders headers = DittoHeaders.newBuilder()
                .authorizationContext(target.getAuthorizationContext())
                // the correlation-id MUST NOT be set! as the DittoHeaders are used as a caching key in the Caffeine
                //  cache this would break the cache loading
                // schema version is always the latest for connectivity signal enrichment.
                .schemaVersion(JsonSchemaVersion.LATEST)
                .build();
        final CompletionStage<JsonObject> extraFuture =
                signalEnrichmentFacade.retrievePartialThing(thingId, extraFields, headers, outboundSignal.getSource());

        return extraFuture.thenApply(outboundSignal::setExtra)
                .thenApply(outboundSignalWithExtra -> applyFilter(outboundSignalWithExtra, filteredTopic))
                .exceptionally(error -> {
                    logger.withCorrelationId(outboundSignal.getSource())
                            .warning("Could not retrieve extra data due to: {} {}", error.getClass().getSimpleName(),
                                    error.getMessage());
                    // recover from all errors to keep message-mapping-stream running despite enrichment failures
                    return Collections.singletonList(recoverFromEnrichmentError(outboundSignal, target, error));
                });
    }

    // Called inside future; must be thread-safe
    private OutboundSignalWithId recoverFromEnrichmentError(final OutboundSignalWithId outboundSignal,
            final Target target, final Throwable error) {

        // show enrichment failure in the connection logs
        logEnrichmentFailure(outboundSignal, connectionId, error);
        // show enrichment failure in service logs according to severity
        if (error instanceof ThingNotAccessibleException) {
            // This error should be rare but possible due to user action; log on INFO level
            logger.withCorrelationId(outboundSignal.getSource())
                    .info("Enrichment of <{}> failed for <{}> due to <{}>.", outboundSignal.getSource().getClass(),
                            outboundSignal.getEntityId(), error);
        } else {
            // This error should not have happened during normal operation.
            // There is a (possibly transient) problem with the Ditto cluster. Request parent to restart.
            logger.withCorrelationId(outboundSignal.getSource())
                    .error("Enrichment of <{}> failed due to <{}>.", outboundSignal, error);
            final ConnectionFailure connectionFailure =
                    new ImmutableConnectionFailure(getSelf(), error, "Signal enrichment failed");
            clientActor.tell(connectionFailure, getSelf());
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

    private Object handleErrorResponse(final DittoRuntimeException exception, final ErrorResponse<?> errorResponse,
            final ActorRef sender) {

        final ThreadSafeDittoLoggingAdapter l = logger.withCorrelationId(exception);

        if (l.isInfoEnabled()) {
            l.info("Got DittoRuntimeException '{}' when ExternalMessage was processed: {} - {}",
                    exception.getErrorCode(), exception.getMessage(), exception.getDescription().orElse(""));
        }
        if (l.isDebugEnabled()) {
            final String stackTrace = stackTraceAsString(exception);
            l.debug("Got DittoRuntimeException '{}' when ExternalMessage was processed: {} - {}. StackTrace: {}",
                    exception.getErrorCode(), exception.getMessage(), exception.getDescription().orElse(""),
                    stackTrace);
        }

        return handleCommandResponse(errorResponse, exception, sender);
    }

    private Object handleCommandResponse(final CommandResponse<?> response,
            @Nullable final DittoRuntimeException exception, final ActorRef sender) {

        final ThreadSafeDittoLoggingAdapter l = logger.isDebugEnabled() ? logger.withCorrelationId(response) : logger;
        recordResponse(response, exception);
        if (!response.isOfExpectedResponseType()) {
            l.debug("Requester did not require response (via DittoHeader '{}') - not mapping back to ExternalMessage.",
                    DittoHeaderDefinition.EXPECTED_RESPONSE_TYPES);
            responseDroppedMonitor.success(response,
                    "Dropped response since requester did not require response via Header {0}.",
                    DittoHeaderDefinition.EXPECTED_RESPONSE_TYPES);
            return Done.getInstance();
        } else {
            if (isSuccessResponse(response)) {
                l.debug("Received response <{}>.", response);
            } else if (l.isDebugEnabled()) {
                l.debug("Received error response <{}>.", response.toJsonString());
            }

            return handleSignal(response, sender);
        }
    }

    private void recordResponse(final CommandResponse<?> response, @Nullable final DittoRuntimeException exception) {
        if (isSuccessResponse(response)) {
            responseDispatchedMonitor.success(response);
        } else {
            responseDispatchedMonitor.failure(response, exception);
        }
    }

    private Source<OutboundSignalWithId, ?> handleOutboundSignal(final OutboundSignalWithId outbound) {
        final Signal<?> source = outbound.getSource();
        if (logger.isDebugEnabled()) {
            logger.withCorrelationId(source).debug("Handling outbound signal <{}>.", source);
        }
        return mapToExternalMessage(outbound);
    }

    private void forwardToPublisherActor(final OutboundSignal.MultiMapped mappedEnvelop) {
        clientActor.tell(new PublishMappedMessage(mappedEnvelop), mappedEnvelop.getSender().orElse(null));
    }

    /**
     * Is called for responses or errors which were directly sent to the mapping actor as a response.
     *
     * @param signal the response/error
     */
    private Object handleSignal(final Signal<?> signal, final ActorRef sender) {
        // map to outbound signal without authorized target (responses and errors are only sent to its origin)
        logger.withCorrelationId(signal).debug("Handling raw signal <{}>.", signal);
        return OutboundSignalWithId.of(signal, sender);
    }

    private Source<OutboundSignalWithId, ?> mapToExternalMessage(final OutboundSignalWithId outbound) {
        final ConnectionMonitor.InfoProvider infoProvider = InfoProviderFactory.forSignal(outbound.getSource());
        final Set<ConnectionMonitor> outboundMapped = getMonitorsForMappedSignal(outbound, connectionId);
        final Set<ConnectionMonitor> outboundDropped = getMonitorsForDroppedSignal(outbound, connectionId);
        final Set<ConnectionMonitor> monitorsForOther = getMonitorsForOther(outbound, connectionId);

        final MappingOutcome.Visitor<OutboundSignal.Mapped, Source<OutboundSignalWithId, ?>> visitor =
                MappingOutcome.<OutboundSignal.Mapped, Source<OutboundSignalWithId, ?>>newVisitorBuilder()
                        .onMapped((mapperId, mapped) -> {
                            outboundMapped.forEach(monitor -> monitor.success(infoProvider,
                                    "Mapped outgoing signal with mapper <{0}>.", mapperId));
                            return Source.single(outbound.mapped(mapped));
                        })
                        .onDropped((mapperId, _null) -> {
                            outboundDropped.forEach(monitor -> monitor.success(infoProvider,
                                    "Payload mapping of mapper <{0}> returned null, outgoing message is dropped.",
                                    mapperId));
                            return Source.empty();
                        })
                        .onError((mapperId, exception, topicPath, _null) -> {
                            if (exception instanceof DittoRuntimeException) {
                                final DittoRuntimeException e = (DittoRuntimeException) exception;
                                monitorsForOther.forEach(monitor ->
                                        monitor.getLogger().failure(infoProvider, e));
                                logger.withCorrelationId(e)
                                        .info("Got DittoRuntimeException during processing Signal: {} - {}",
                                                e.getMessage(),
                                                e.getDescription().orElse(""));
                            } else {
                                monitorsForOther.forEach(monitor ->
                                        monitor.getLogger().exception(infoProvider, exception));
                                logger.withCorrelationId(outbound.getSource())
                                        .warning("Got unexpected exception during processing Signal <{}>.",
                                                exception.getMessage());
                            }
                            return Source.empty();
                        })
                        .build();

        return outboundMappingProcessor.process(outbound).stream()
                .<Source<OutboundSignalWithId, ?>>map(visitor::eval)
                .reduce(Source::concat)
                .orElse(Source.empty());
    }

    private Set<ConnectionMonitor> getMonitorsForDroppedSignal(final OutboundSignal outbound,
            final ConnectionId connectionId) {

        return getMonitorsForOutboundSignal(outbound, connectionId, DROPPED, LogType.DROPPED, responseDroppedMonitor);
    }

    private Set<ConnectionMonitor> getMonitorsForMappedSignal(final OutboundSignal outbound,
            final ConnectionId connectionId) {

        return getMonitorsForOutboundSignal(outbound, connectionId, MAPPED, LogType.MAPPED, responseMappedMonitor);
    }

    private Set<ConnectionMonitor> getMonitorsForOther(final OutboundSignal outbound,
            final ConnectionId connectionId) {

        return getMonitorsForOutboundSignal(outbound, connectionId, MAPPED, LogType.OTHER, responseMappedMonitor);
    }

    private Set<ConnectionMonitor> getMonitorsForOutboundSignal(final OutboundSignal outbound,
            final ConnectionId connectionId,
            final MetricType metricType,
            final LogType logType,
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

    private <T> CompletionStage<Collection<OutboundSignal.MultiMapped>> toMultiMappedOutboundSignal(
            final Source<OutboundSignalWithId, T> source) {

        return source.runWith(Sink.seq(), materializer)
                .thenApply(outboundSignals -> {
                    if (outboundSignals.isEmpty()) {
                        return List.of();
                    } else {
                        final ActorRef sender = outboundSignals.get(0).sender;
                        final List<Mapped> mappedSignals = outboundSignals.stream()
                                .map(OutboundSignalWithId::asMapped)
                                .collect(Collectors.toList());
                        return List.of(OutboundSignalFactory.newMultiMappedOutboundSignal(mappedSignals, sender));
                    }
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

    private static String stackTraceAsString(final DittoRuntimeException exception) {
        final StringWriter stringWriter = new StringWriter();
        exception.printStackTrace(new PrintWriter(stringWriter));
        return stringWriter.toString();
    }

    private static boolean isSuccessResponse(final CommandResponse<?> response) {
        return response.getStatusCodeValue() < HttpStatusCode.BAD_REQUEST.toInt();
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

    private static boolean isTwinCommandResponseWithReplyTarget(final Signal<?> signal) {
        final DittoHeaders dittoHeaders = signal.getDittoHeaders();
        return signal instanceof CommandResponse &&
                !ProtocolAdapter.isLiveSignal(signal) &&
                dittoHeaders.getReplyTarget().isPresent();
    }

    static final class OutboundSignalWithId implements OutboundSignal, WithId {

        private final OutboundSignal delegate;
        private final EntityId entityId;
        private final ActorRef sender;

        @Nullable
        private final JsonObject extra;

        private OutboundSignalWithId(final OutboundSignal delegate,
                final EntityId entityId,
                final ActorRef sender,
                @Nullable final JsonObject extra) {

            this.delegate = delegate;
            this.entityId = entityId;
            this.sender = sender;
            this.extra = extra;
        }

        static OutboundSignalWithId of(final Signal<?> signal, final ActorRef sender) {
            final OutboundSignal outboundSignal =
                    OutboundSignalFactory.newOutboundSignal(signal, Collections.emptyList());
            final EntityId entityId = signal.getEntityId();
            return new OutboundSignalWithId(outboundSignal, entityId, sender, null);
        }

        static OutboundSignalWithId of(final OutboundSignal outboundSignal, final ActorRef sender) {
            final EntityId entityId = outboundSignal.getSource().getEntityId();
            return new OutboundSignalWithId(outboundSignal, entityId, sender, null);
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
                    entityId, sender, extra);
        }

        private OutboundSignalWithId setExtra(final JsonObject extra) {
            return new OutboundSignalWithId(
                    OutboundSignalFactory.newOutboundSignal(delegate.getSource(), getTargets()),
                    entityId, sender, extra
            );
        }

        private OutboundSignalWithId mapped(final Mapped mapped) {
            return new OutboundSignalWithId(mapped, entityId, sender, extra);
        }

        private Mapped asMapped() {
            return (Mapped) delegate;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "delegate=" + delegate +
                    ", entityId=" + entityId +
                    ", sender=" + sender +
                    ", extra=" + extra +
                    "]";
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final OutboundSignalWithId that = (OutboundSignalWithId) o;
            return Objects.equals(delegate, that.delegate) &&
                    Objects.equals(entityId, that.entityId) &&
                    Objects.equals(sender, that.sender) &&
                    Objects.equals(extra, that.extra);
        }

        @Override
        public int hashCode() {
            return Objects.hash(delegate, entityId, sender, extra);
        }

    }

}
