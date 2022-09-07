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
package org.eclipse.ditto.connectivity.service.messaging;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotEmpty;
import static org.eclipse.ditto.connectivity.model.MetricType.DROPPED;
import static org.eclipse.ditto.connectivity.model.MetricType.MAPPED;

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

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.SignalEnrichmentFailedException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.WithResource;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.commands.ErrorResponse;
import org.eclipse.ditto.base.service.config.limits.LimitsConfig;
import org.eclipse.ditto.connectivity.api.OutboundSignal;
import org.eclipse.ditto.connectivity.api.OutboundSignal.Mapped;
import org.eclipse.ditto.connectivity.api.OutboundSignalFactory;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.FilteredTopic;
import org.eclipse.ditto.connectivity.model.LogCategory;
import org.eclipse.ditto.connectivity.model.LogType;
import org.eclipse.ditto.connectivity.model.MetricDirection;
import org.eclipse.ditto.connectivity.model.MetricType;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.MonitoringConfig;
import org.eclipse.ditto.connectivity.service.config.mapping.MappingConfig;
import org.eclipse.ditto.connectivity.service.mapping.ConnectivitySignalEnrichmentProvider;
import org.eclipse.ditto.connectivity.service.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.connectivity.service.messaging.mappingoutcome.MappingOutcome;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.DefaultConnectionMonitorRegistry;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.InfoProviderFactory;
import org.eclipse.ditto.connectivity.service.messaging.validation.ConnectionValidator;
import org.eclipse.ditto.connectivity.service.util.ConnectivityMdcEntryKey;
import org.eclipse.ditto.edge.service.headers.DittoHeadersValidator;
import org.eclipse.ditto.internal.models.signalenrichment.SignalEnrichmentFacade;
import org.eclipse.ditto.internal.utils.akka.controlflow.AbstractGraphActor;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.pubsub.StreamingType;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.placeholders.ExpressionResolver;
import org.eclipse.ditto.placeholders.PipelineElement;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.placeholders.PlaceholderResolver;
import org.eclipse.ditto.placeholders.TimePlaceholder;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.placeholders.ResourcePlaceholder;
import org.eclipse.ditto.protocol.placeholders.TopicPathPlaceholder;
import org.eclipse.ditto.rql.parser.RqlPredicateParser;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.rql.query.things.ThingPredicateVisitor;
import org.eclipse.ditto.things.model.ThingFieldSelector;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.events.ThingEventToThingConverter;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status;
import akka.japi.Pair;
import akka.japi.pf.PFBuilder;
import akka.stream.QueueOfferResult;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

/**
 * This Actor processes {@link OutboundSignal outbound signals} and dispatches them.
 */
public final class OutboundMappingProcessorActor
        extends AbstractGraphActor<OutboundMappingProcessorActor.OutboundSignalWithSender, OutboundSignal> {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "outboundMappingProcessor";

    /**
     * The name of the dispatcher that runs all mapping tasks and all message handling of this actor and its children.
     */
    private static final String MESSAGE_MAPPING_PROCESSOR_DISPATCHER = "message-mapping-processor-dispatcher";

    private static final DittoProtocolAdapter DITTO_PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();
    private static final TopicPathPlaceholder TOPIC_PATH_PLACEHOLDER = TopicPathPlaceholder.getInstance();
    private static final ResourcePlaceholder RESOURCE_PLACEHOLDER = ResourcePlaceholder.getInstance();
    private static final TimePlaceholder TIME_PLACEHOLDER = TimePlaceholder.getInstance();

    private final ActorRef clientActor;
    private final Connection connection;
    private final MappingConfig mappingConfig;
    private final DefaultConnectionMonitorRegistry connectionMonitorRegistry;
    private final ConnectionMonitor responseDispatchedMonitor;
    private final ConnectionMonitor responseDroppedMonitor;
    private final ConnectionMonitor responseMappedMonitor;
    private final SignalEnrichmentFacade signalEnrichmentFacade;
    private final int processorPoolSize;
    private final DittoRuntimeExceptionToErrorResponseFunction toErrorResponseFunction;
    private final List<OutboundMappingProcessor> outboundMappingProcessors;

    @SuppressWarnings("unused")
    private OutboundMappingProcessorActor(final ActorRef clientActor,
            final List<OutboundMappingProcessor> outboundMappingProcessors,
            final Connection connection,
            final ConnectivityConfig connectivityConfig,
            final int processorPoolSize) {

        super(OutboundSignal.class, logger ->
                logger.withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID, connection.getId())
        );
        final ActorSystem system = context().system();
        this.clientActor = clientActor;
        this.outboundMappingProcessors = checkNotEmpty(outboundMappingProcessors, "outboundMappingProcessors");
        this.connection = connection;

        final MonitoringConfig monitoringConfig = connectivityConfig.getMonitoringConfig();
        mappingConfig = connectivityConfig.getMappingConfig();
        final LimitsConfig limitsConfig = connectivityConfig.getLimitsConfig();


        final var dittoExtensionConfig = ScopedConfig.dittoExtension(system.settings().config());
        connectionMonitorRegistry = DefaultConnectionMonitorRegistry.fromConfig(connectivityConfig);
        responseDispatchedMonitor = connectionMonitorRegistry.forResponseDispatched(this.connection);
        responseDroppedMonitor = connectionMonitorRegistry.forResponseDropped(this.connection);
        responseMappedMonitor = connectionMonitorRegistry.forResponseMapped(this.connection);
        signalEnrichmentFacade = ConnectivitySignalEnrichmentProvider.get(system, dittoExtensionConfig).getFacade(this.connection.getId());
        this.processorPoolSize = determinePoolSize(processorPoolSize, mappingConfig.getMaxPoolSize());
        toErrorResponseFunction = DittoRuntimeExceptionToErrorResponseFunction.of(DittoHeadersValidator.get(system, dittoExtensionConfig));
    }

    /**
     * Issue weak acknowledgements to the sender of a signal.
     *
     * @param signal the signal with 0 or more acknowledgement requests.
     * @param isWeakAckLabel the predicate to test if a requested acknowledgement label should generate a weak ack.
     * @param actorContext the ActorContext to use for looking up actor selections.
     * @param log the logger to use for logging.
     */
    public static void issueWeakAcknowledgements(final Signal<?> signal,
            final Predicate<AcknowledgementLabel> isWeakAckLabel,
            final akka.actor.ActorContext actorContext,
            final ThreadSafeDittoLoggingAdapter log) {
        final Set<AcknowledgementRequest> requestedAcks = signal.getDittoHeaders().getAcknowledgementRequests();
        final boolean customAckRequested = requestedAcks.stream()
                .anyMatch(request -> !DittoAcknowledgementLabel.contains(request.getLabel()));

        final Optional<EntityId> entityIdWithType = extractEntityId(signal);
        if (customAckRequested && entityIdWithType.isPresent()) {
            final List<AcknowledgementLabel> weakAckLabels = requestedAcks.stream()
                    .map(AcknowledgementRequest::getLabel)
                    .filter(isWeakAckLabel)
                    .toList();
            if (!weakAckLabels.isEmpty()) {
                final DittoHeaders dittoHeaders = signal.getDittoHeaders();
                final List<Acknowledgement> ackList = weakAckLabels.stream()
                        .map(label -> weakAck(label, entityIdWithType.get(), dittoHeaders))
                        .toList();
                final Acknowledgements weakAcks = Acknowledgements.of(ackList, dittoHeaders);
                final String ackregatorAddress = weakAcks.getDittoHeaders()
                        .get(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey());
                if (null != ackregatorAddress) {
                    final ActorSelection acknowledgementRequester = actorContext.actorSelection(ackregatorAddress);
                    acknowledgementRequester.tell(weakAcks, ActorRef.noSender());
                } else {
                    log.withCorrelationId(weakAcks)
                            .error("Aggregated Acknowledgements did not contain header of acknowledgement aggregator " +
                                    "address: {}", weakAcks.getDittoHeaders());
                }
            }
        }
    }

    private static void issueFailedAcknowledgements(final Signal<?> signal,
            final Predicate<AcknowledgementLabel> isFailedAckLabel,
            final DittoRuntimeException dre,
            final ActorContext context,
            final ThreadSafeDittoLoggingAdapter logger) {

        final Set<AcknowledgementRequest> requestedAcks = signal.getDittoHeaders().getAcknowledgementRequests();
        final boolean customAckRequested = requestedAcks.stream()
                .anyMatch(request -> !DittoAcknowledgementLabel.contains(request.getLabel()));

        final Optional<EntityId> entityIdWithType = extractEntityId(signal);
        if (customAckRequested && entityIdWithType.isPresent()) {
            final List<AcknowledgementLabel> failedAckLabels = requestedAcks.stream()
                    .map(AcknowledgementRequest::getLabel)
                    .filter(isFailedAckLabel)
                    .toList();
            if (!failedAckLabels.isEmpty()) {
                final DittoHeaders dittoHeaders = signal.getDittoHeaders();
                final List<Acknowledgement> ackList = failedAckLabels.stream()
                        .map(label -> failedAck(label, entityIdWithType.get(), dittoHeaders, dre))
                        .toList();
                final Acknowledgements failedAcks = Acknowledgements.of(ackList, dittoHeaders);

                final String ackregatorAddress = failedAcks.getDittoHeaders()
                        .get(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey());
                if (null != ackregatorAddress) {
                    final ActorSelection acknowledgementRequester = context.actorSelection(ackregatorAddress);
                    acknowledgementRequester.tell(failedAcks, ActorRef.noSender());
                } else {
                    logger.withCorrelationId(failedAcks)
                            .error("Failed Acknowledgements did not contain header of acknowledgement aggregator " +
                                    "address: {}", failedAcks.getDittoHeaders());
                }
            }
        }
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
     * @param outboundMappingProcessors the MessageMappingProcessors to use for outbound messages. If at least as many
     * processors are given as `processorPoolSize`, then each processor is guaranteed to be invoked sequentially.
     * @param connection the connection.
     * @param connectivityConfig the config of the connectivity service with potential overwrites.
     * @param processorPoolSize how many message processing may happen in parallel per direction (incoming or outgoing).
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef clientActor,
            final List<OutboundMappingProcessor> outboundMappingProcessors,
            final Connection connection,
            final ConnectivityConfig connectivityConfig,
            final int processorPoolSize) {

        return Props.create(OutboundMappingProcessorActor.class,
                clientActor,
                outboundMappingProcessors,
                connection,
                connectivityConfig,
                processorPoolSize
        ).withDispatcher(MESSAGE_MAPPING_PROCESSOR_DISPATCHER);
    }

    @Override
    public Receive createReceive() {
        final PartialFunction<Object, Object> wrapAsOutboundSignal = new PFBuilder<>()
                .match(Acknowledgement.class, this::handleNotExpectedAcknowledgement)
                .match(ErrorResponse.class,
                        errResponse -> handleCommandResponse(errResponse, errResponse.getDittoRuntimeException(),
                                getSender()))
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
                .warning("Received Acknowledgement where none was expected, discarding it: {}", acknowledgement);
        return Done.getInstance();
    }

    private Object mapDittoRuntimeException(final DittoRuntimeException exception) {
        final ErrorResponse<?> errorResponse = toErrorResponseFunction.apply(exception, null);
        return handleErrorResponse(exception, errorResponse, getSender());
    }

    @Override
    protected OutboundSignalWithSender mapMessage(final OutboundSignal message) {
        if (message instanceof OutboundSignalWithSender outboundSignalWithSender) {
            // message contains original sender already
            return outboundSignalWithSender;
        } else {
            return OutboundSignalWithSender.of(message, getSender());
        }
    }

    @Override
    protected void messageDiscarded(OutboundSignal message,  QueueOfferResult result) {
        if (QueueOfferResult.dropped().equals(result)) {
            responseDispatchedMonitor.failure(message.getSource(), "Message is dropped as a result of backpressure strategy!");
        } else if (result instanceof final QueueOfferResult.Failure failure) {
            responseDispatchedMonitor.failure(message.getSource(), "Enqueue failed! - failure: {}", failure.cause());
        } else {
            responseDispatchedMonitor.failure(message.getSource(), "Enqueue failed without acknowledgement!");
        }
    }

    @Override
    protected Sink<OutboundSignalWithSender, ?> createSink() {
        // Enrich outbound signals by extra fields if necessary.
        // Targets attached to the OutboundSignal are pre-selected by authorization, topic and filter sans enrichment.
        final Flow<OutboundSignalWithSender, OutboundSignal.MultiMapped, ?> flow =
                Flow.<OutboundSignalWithSender>create()
                        .zipWithIndex()
                        .mapAsync(processorPoolSize, outboundPair -> {
                            final int processorIndex = (int) (outboundPair.second() % outboundMappingProcessors.size());
                            final var outboundMappingProcessor = outboundMappingProcessors.get(processorIndex);
                            return toMultiMappedOutboundSignal(
                                    outboundPair.first(),
                                    outboundMappingProcessor,
                                    Source.single(outboundPair.first())
                                            .via(splitByTargetExtraFieldsFlow())
                                            .mapAsync(mappingConfig.getParallelism(), this::enrichAndFilterSignal)
                                            .mapConcat(x -> x)
                                            .map(outbound -> handleOutboundSignal(outbound, outboundMappingProcessor))
                                            .flatMapConcat(x -> x)
                            );
                        })
                        .mapConcat(x -> x);
        return flow.to(Sink.foreach(this::forwardToPublisherActor));
    }

    /**
     * Create a flow that splits 1 outbound signal into many as follows.
     * <ol>
     * <li>
     * Targets with matching filtered topics without extra fields are grouped into 1 outbound signal, followed by
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
    private static Flow<OutboundSignalWithSender, Pair<OutboundSignalWithSender, FilteredTopic>, NotUsed> splitByTargetExtraFieldsFlow() {
        return Flow.<OutboundSignalWithSender>create()
                .mapConcat(outboundSignal -> {
                    final Pair<List<Target>, List<Pair<Target, FilteredTopic>>> splitTargets =
                            splitTargetsByExtraFields(outboundSignal);

                    final boolean shouldSendSignalWithoutExtraFields =
                            !splitTargets.first().isEmpty() ||
                                    isCommandResponseWithReplyTarget(outboundSignal.getSource()) ||
                                    outboundSignal.getTargets().isEmpty(); // no target - this is an error response
                    final Stream<Pair<OutboundSignalWithSender, FilteredTopic>> outboundSignalWithoutExtraFields =
                            shouldSendSignalWithoutExtraFields
                                    ? Stream.of(Pair.create(outboundSignal.setTargets(splitTargets.first()), null))
                                    : Stream.empty();

                    final Stream<Pair<OutboundSignalWithSender, FilteredTopic>> outboundSignalWithExtraFields =
                            splitTargets.second().stream()
                                    .map(targetAndSelector -> Pair.create(
                                            outboundSignal.setTargets(
                                                    Collections.singletonList(targetAndSelector.first())),
                                            targetAndSelector.second()));

                    return Stream.concat(outboundSignalWithoutExtraFields, outboundSignalWithExtraFields).toList();
                });
    }


    // Called inside stream; must be thread-safe
    // precondition: whenever filteredTopic != null, it contains an extra fields
    private CompletionStage<Collection<OutboundSignalWithSender>> enrichAndFilterSignal(
            final Pair<OutboundSignalWithSender, FilteredTopic> outboundSignalWithExtraFields) {

        final OutboundSignalWithSender outboundSignal = outboundSignalWithExtraFields.first();
        final FilteredTopic filteredTopic = outboundSignalWithExtraFields.second();
        final ExpressionResolver expressionResolver =
                Resolvers.forSignal(outboundSignal.getSource(), connection.getId());
        final Optional<JsonFieldSelector> extraFieldsOptional = getExtraFields(expressionResolver, filteredTopic);
        if (extraFieldsOptional.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.singletonList(outboundSignal));
        }
        final JsonFieldSelector extraFields = extraFieldsOptional.get();
        final Target target = outboundSignal.getTargets().get(0);


        final DittoHeaders headers = DittoHeaders.newBuilder()
                .authorizationContext(target.getAuthorizationContext())
                // the correlation-id MUST NOT be set! as the DittoHeaders are used as a caching key in the Caffeine
                // cache this would break the cache loading
                // schema version is always the latest for connectivity signal enrichment.
                .schemaVersion(JsonSchemaVersion.LATEST)
                .build();

        return extractEntityId(outboundSignal.delegate.getSource())
                .filter(ThingId.class::isInstance)
                .map(ThingId.class::cast)
                .map(thingId ->
                        signalEnrichmentFacade.retrievePartialThing(
                                thingId,
                                extraFields,
                                headers,
                                outboundSignal.getSource())
                )
                .map(partialThingCompletionStage -> partialThingCompletionStage.thenApply(outboundSignal::setExtra))
                .orElse(CompletableFuture.completedStage(outboundSignal))
                .thenApply(outboundSignalWithExtra -> applyFilter(outboundSignalWithExtra, filteredTopic))
                .exceptionally(error -> {
                    logger.withCorrelationId(outboundSignal.getSource())
                            .warning("Could not retrieve extra data due to: {} {}", error.getClass().getSimpleName(),
                                    error.getMessage());
                    // recover from all errors to keep message-mapping-stream running despite enrichment failures
                    return recoverFromEnrichmentError(outboundSignal, target, error);
                });
    }

    private static Optional<JsonFieldSelector> getExtraFields(final ExpressionResolver expressionResolver,
            @Nullable final FilteredTopic filteredTopic) {

        return Optional.ofNullable(filteredTopic)
                .flatMap(FilteredTopic::getExtraFields)
                .map(extraFields -> extraFields.getPointers().stream()
                        .map(JsonPointer::toString)
                        .map(expressionResolver::resolve)
                        .flatMap(PipelineElement::toStream)
                        .map(JsonPointer::of)
                        .toList())
                .filter(jsonPointers -> !jsonPointers.isEmpty())
                .map(JsonFactory::newFieldSelector)
                .map(ThingFieldSelector::fromJsonFieldSelector);
    }

    private static Optional<EntityId> extractEntityId(final Signal<?> signal) {
        return Optional.of(signal)
                .filter(WithEntityId.class::isInstance)
                .map(WithEntityId.class::cast)
                .map(WithEntityId::getEntityId);
    }

    // Called inside future; must be thread-safe
    private List<OutboundSignalWithSender> recoverFromEnrichmentError(final OutboundSignalWithSender outboundSignal,
            final Target target, final Throwable error) {

        final var dittoRuntimeException = DittoRuntimeException.asDittoRuntimeException(error, t ->
                SignalEnrichmentFailedException.newBuilder()
                        .dittoHeaders(outboundSignal.getSource().getDittoHeaders())
                        .cause(t)
                        .build());
        // show enrichment failure in the connection logs
        logEnrichmentFailure(outboundSignal, dittoRuntimeException);
        // show enrichment failure in service logs according to severity
        if (dittoRuntimeException instanceof ThingNotAccessibleException) {
            // This error should be rare but possible due to user action; log on INFO level
            logger.withCorrelationId(outboundSignal.getSource())
                    .info("Enrichment of <{}> failed due to <{}>.",
                            outboundSignal.getSource().getClass(), dittoRuntimeException);
        } else {
            // This error should not have happened during normal operation.
            // There is a (possibly transient) problem with the Ditto cluster. Request parent to restart.
            logger.withCorrelationId(outboundSignal.getSource())
                    .error(dittoRuntimeException, "Enrichment of <{}> failed due to <{}>.", outboundSignal,
                            dittoRuntimeException);
            final ConnectionFailure connectionFailure =
                    ConnectionFailure.internal(getSelf(), dittoRuntimeException, "Signal enrichment failed");
            clientActor.tell(connectionFailure, getSelf());
        }
        if (mappingConfig.getPublishFailedEnrichments()) {
            return Collections.singletonList(outboundSignal.setTargets(Collections.singletonList(target)));
        } else {
            return Collections.singletonList(outboundSignal.setFailedEnrichment(dittoRuntimeException, target));
        }
    }

    private void logEnrichmentFailure(final OutboundSignal outboundSignal, final DittoRuntimeException error) {

        final DittoRuntimeException errorToLog = SignalEnrichmentFailedException.dueTo(error);
        getMonitorsForMappedSignal(outboundSignal)
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

        final ThreadSafeDittoLoggingAdapter l =
                logger.isDebugEnabled() ? logger.withCorrelationId(response) :
                        logger;
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

    private Source<OutboundSignalWithSender, ?> handleOutboundSignal(final OutboundSignalWithSender outbound,
            final OutboundMappingProcessor outboundMappingProcessor) {

        final Signal<?> source = outbound.getSource();
        if (logger.isDebugEnabled()) {
            logger.withCorrelationId(source).debug("Handling outbound signal <{}>.", source);
        }
        return mapToExternalMessage(outbound, outboundMappingProcessor);
    }

    private void forwardToPublisherActor(final OutboundSignal.MultiMapped mappedEnvelop) {
        clientActor.tell(new BaseClientActor.PublishMappedMessage(mappedEnvelop),
                mappedEnvelop.getSender().orElse(null));
    }

    /**
     * Is called for responses or errors which were directly sent to the mapping actor as a response.
     *
     * @param signal the response/error
     */
    private Object handleSignal(final Signal<?> signal, final ActorRef sender) {
        // map to outbound signal without authorized target (responses and errors are only sent to its origin)
        logger.withCorrelationId(signal).debug("Handling raw signal <{}>.", signal);
        return OutboundSignalWithSender.of(signal, sender);
    }

    private Source<OutboundSignalWithSender, ?> mapToExternalMessage(final OutboundSignalWithSender outbound,
            final OutboundMappingProcessor outboundMappingProcessor) {

        final ConnectionMonitor.InfoProvider infoProvider = InfoProviderFactory.forSignal(outbound.getSource());
        final Set<ConnectionMonitor> outboundMapped = getMonitorsForMappedSignal(outbound);
        final Set<ConnectionMonitor> outboundDropped = getMonitorsForDroppedSignal(outbound);
        final Set<ConnectionMonitor> monitorsForOther = getMonitorsForOther(outbound);

        final MappingOutcome.Visitor<Mapped, Source<OutboundSignalWithSender, ?>> visitor =
                MappingOutcome.<OutboundSignal.Mapped, Source<OutboundSignalWithSender, ?>>newVisitorBuilder()
                        .onMapped((mapperId, mapped) -> {
                            outboundMapped.forEach(monitor -> monitor.success(infoProvider,
                                    "Mapped outgoing signal with mapper <{0}>", mapperId));
                            return Source.single(outbound.mapped(mapped));
                        })
                        .onDropped((mapperId, unused) -> {
                            outboundDropped.forEach(monitor -> monitor.success(infoProvider,
                                    "Payload mapping of mapper <{0}> returned null, outgoing message is dropped",
                                    mapperId));
                            return Source.empty();
                        })
                        .onError((mapperId, exception, topicPath, unused) -> {
                            if (exception instanceof DittoRuntimeException e) {
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
                .<Source<OutboundSignalWithSender, ?>>map(visitor::eval)
                .reduce(Source::concat)
                .orElse(Source.empty());
    }

    private Set<ConnectionMonitor> getMonitorsForDroppedSignal(final OutboundSignal outbound) {

        return getMonitorsForOutboundSignal(outbound, DROPPED, LogType.DROPPED, responseDroppedMonitor);
    }

    private Set<ConnectionMonitor> getMonitorsForMappedSignal(final OutboundSignal outbound) {

        return getMonitorsForOutboundSignal(outbound, MAPPED, LogType.MAPPED, responseMappedMonitor);
    }

    private Set<ConnectionMonitor> getMonitorsForOther(final OutboundSignal outbound) {

        return getMonitorsForOutboundSignal(outbound, MAPPED, LogType.OTHER, responseMappedMonitor);
    }

    private Set<ConnectionMonitor> getMonitorsForOutboundSignal(final OutboundSignal outbound,
            final MetricType metricType,
            final LogType logType,
            final ConnectionMonitor responseMonitor) {

        if (outbound.getSource() instanceof CommandResponse) {
            return Collections.singleton(responseMonitor);
        } else {
            return outbound.getTargets()
                    .stream()
                    .map(Target::getOriginalAddress)
                    .map(address -> connectionMonitorRegistry.getMonitor(connection, metricType,
                            MetricDirection.OUTBOUND,
                            logType, LogCategory.TARGET, address))
                    .collect(Collectors.toSet());
        }
    }

    private <T> CompletionStage<Collection<OutboundSignal.MultiMapped>> toMultiMappedOutboundSignal(
            final OutboundSignalWithSender outbound,
            final OutboundMappingProcessor outboundMappingProcessor,
            final Source<OutboundSignalWithSender, T> source) {

        final ActorContext context = getContext();
        return source.runWith(Sink.seq(), materializer)
                .thenApply(outboundSignals -> {
                    if (outboundSignals.isEmpty()) {
                        // signal dropped; issue weak acks for all requested acks belonging to this connection
                        issueWeakAcknowledgements(outbound.getSource(),
                                outboundMappingProcessor::isSourceDeclaredOrTargetIssuedAck,
                                context,
                                logger);
                        return List.of();
                    } else {
                        final ActorRef sender = outboundSignals.get(0).sender;
                        final List<Target> targetsToPublishAt = outboundSignals.stream()
                                .map(OutboundSignal::getTargets)
                                .flatMap(List::stream)
                                .toList();
                        final Predicate<AcknowledgementLabel> willPublish =
                                ConnectionValidator.getTargetIssuedAcknowledgementLabels(connection.getId(),
                                                targetsToPublishAt)
                                        .collect(Collectors.toSet())::contains;
                        final var signalsWithoutEnrichmentFailures =
                                filterFailedEnrichments(outboundSignals, willPublish, context, logger);
                        final List<Mapped> mappedSignals = signalsWithoutEnrichmentFailures
                                .map(OutboundSignalWithSender::asMapped)
                                .toList();
                        issueWeakAcknowledgements(outbound.getSource(),
                                willPublish.negate().and(outboundMappingProcessor::isTargetIssuedAck),
                                context,
                                logger);
                        if (mappedSignals.isEmpty()) {
                            return List.of();
                        }
                        return List.of(OutboundSignalFactory.newMultiMappedOutboundSignal(mappedSignals, sender));
                    }
                });
    }

    private static Stream<OutboundSignalWithSender> filterFailedEnrichments(
            final Collection<OutboundSignalWithSender> signals,
            final Predicate<AcknowledgementLabel> predicate,
            final ActorContext context,
            final ThreadSafeDittoLoggingAdapter logger) {

        return signals.stream().filter(signal -> {
            if (null != signal.enrichmentFailure) {
                final var optionalAcknowledgementLabel = signal.getTargets()
                        .get(signal.getTargets().indexOf(signal.enrichmentFailure.second()))
                        .getIssuedAcknowledgementLabel();
                if (optionalAcknowledgementLabel.isPresent()) {
                    final Predicate<AcknowledgementLabel> perTargetPredicate =
                            optionalAcknowledgementLabel.get()::equals;
                    final var combinedPredicate = predicate.and(perTargetPredicate);
                    issueFailedAcknowledgements(signal.getSource(), combinedPredicate, signal.enrichmentFailure.first(),
                            context, logger);
                }
                return false;
            } else {
                return true;
            }
        });
    }

    private Collection<OutboundSignalWithSender> applyFilter(final OutboundSignalWithSender outboundSignalWithExtra,
            final FilteredTopic filteredTopic) {

        final Optional<String> filter = filteredTopic.getFilter();
        final Optional<JsonFieldSelector> extraFields = filteredTopic.getExtraFields();
        if (filter.isPresent() && extraFields.isPresent()) {
            // evaluate filter criteria again if signal enrichment is involved.
            final Signal<?> signal = outboundSignalWithExtra.getSource();
            final TopicPath topicPath = DITTO_PROTOCOL_ADAPTER.toTopicPath(signal);
            final PlaceholderResolver<TopicPath> topicPathPlaceholderResolver = PlaceholderFactory
                    .newPlaceholderResolver(TOPIC_PATH_PLACEHOLDER, topicPath);
            final PlaceholderResolver<WithResource> resourcePlaceholderResolver = PlaceholderFactory
                    .newPlaceholderResolver(RESOURCE_PLACEHOLDER, signal);
            final PlaceholderResolver<Object> timePlaceholderResolver = PlaceholderFactory
                    .newPlaceholderResolver(TIME_PLACEHOLDER, new Object());
            final DittoHeaders dittoHeaders = signal.getDittoHeaders();
            final Criteria criteria = QueryFilterCriteriaFactory.modelBased(RqlPredicateParser.getInstance(),
                    topicPathPlaceholderResolver, resourcePlaceholderResolver, timePlaceholderResolver
            ).filterCriteria(filter.get(), dittoHeaders);
            return outboundSignalWithExtra.getExtra()
                    .flatMap(extra -> ThingEventToThingConverter
                            .mergeThingWithExtraFields(signal, extraFields.get(), extra)
                            .filter(ThingPredicateVisitor.apply(criteria, topicPathPlaceholderResolver,
                                    resourcePlaceholderResolver, timePlaceholderResolver))
                            .map(thing -> outboundSignalWithExtra))
                    .map(Collections::singletonList)
                    .orElse(List.of());
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
        final var responseHttpStatus = response.getHttpStatus();
        return responseHttpStatus.isSuccess();
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

    private static boolean isCommandResponseWithReplyTarget(final Signal<?> signal) {
        final DittoHeaders dittoHeaders = signal.getDittoHeaders();
        return signal instanceof CommandResponse && dittoHeaders.getReplyTarget().isPresent();
    }

    private static Acknowledgement weakAck(final AcknowledgementLabel label,
            final EntityId entityId,
            final DittoHeaders dittoHeaders) {
        final JsonValue payload = JsonValue.of("Acknowledgement was issued automatically as weak ack, " +
                "because the signal is not relevant for the subscriber. Possible reasons are: " +
                "the subscriber was not authorized, " +
                "the subscriber did not subscribe for the signal type, " +
                "the signal was dropped by a configured RQL filter, " +
                "or the signal was dropped by all payload mappers.");
        return Acknowledgement.weak(label, entityId, dittoHeaders, payload);
    }

    private static Acknowledgement failedAck(final AcknowledgementLabel label,
            final EntityId entityId,
            final DittoHeaders dittoHeaders,
            final DittoRuntimeException dre) {
        final JsonValue payload = JsonValue.of("Acknowledgement was issued automatically as failed ack, " +
                "because the signal enrichment failed: " + dre.getMessage());
        return Acknowledgement.of(label, entityId, dre.getHttpStatus(), dittoHeaders, payload);
    }

    static final class OutboundSignalWithSender implements OutboundSignal {

        private final OutboundSignal delegate;
        private final ActorRef sender;

        @Nullable
        private final Pair<DittoRuntimeException, Target> enrichmentFailure;
        @Nullable
        private final JsonObject extra;

        private OutboundSignalWithSender(final OutboundSignal delegate,
                final ActorRef sender,
                @Nullable final Pair<DittoRuntimeException, Target> enrichmentFailure,
                @Nullable final JsonObject extra) {

            this.delegate = delegate;
            this.sender = sender;
            this.enrichmentFailure = enrichmentFailure;
            this.extra = extra;
        }

        static OutboundSignalWithSender of(final Signal<?> signal, final ActorRef sender) {
            final OutboundSignal outboundSignal =
                    OutboundSignalFactory.newOutboundSignal(signal, Collections.emptyList());
            return new OutboundSignalWithSender(outboundSignal, sender, null, null);
        }

        static OutboundSignalWithSender of(final OutboundSignal outboundSignal, final ActorRef sender) {
            return new OutboundSignalWithSender(outboundSignal, sender, null, null);
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

        private OutboundSignalWithSender setTargets(final List<Target> targets) {
            return new OutboundSignalWithSender(OutboundSignalFactory.newOutboundSignal(delegate.getSource(), targets),
                    sender, enrichmentFailure, extra);
        }

        // Also set target, because enrichment can fail per target.
        private OutboundSignalWithSender setFailedEnrichment(final DittoRuntimeException e, final Target t) {
            return new OutboundSignalWithSender(
                    OutboundSignalFactory.newOutboundSignal(delegate.getSource(), getTargets()),
                    sender, Pair.apply(e, t), extra);
        }

        private OutboundSignalWithSender setExtra(final JsonObject extra) {
            return new OutboundSignalWithSender(
                    OutboundSignalFactory.newOutboundSignal(delegate.getSource(), getTargets()),
                    sender, enrichmentFailure, extra
            );
        }

        private OutboundSignalWithSender mapped(final Mapped mapped) {
            return new OutboundSignalWithSender(mapped, sender, enrichmentFailure, extra);
        }

        private Mapped asMapped() {
            return (Mapped) delegate;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "delegate=" + delegate +
                    ", sender=" + sender +
                    ", enrichmentFailure=" + enrichmentFailure +
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
            final OutboundSignalWithSender that = (OutboundSignalWithSender) o;
            return Objects.equals(delegate, that.delegate) &&
                    Objects.equals(sender, that.sender) &&
                    Objects.equals(enrichmentFailure, that.enrichmentFailure) &&
                    Objects.equals(extra, that.extra);
        }

        @Override
        public int hashCode() {
            return Objects.hash(delegate, sender, enrichmentFailure, extra);
        }

    }

}
