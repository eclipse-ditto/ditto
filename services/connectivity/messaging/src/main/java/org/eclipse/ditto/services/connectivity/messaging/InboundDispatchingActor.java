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

import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.acks.AbstractCommandAckRequestSetter;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabelNotDeclaredException;
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.acks.FilteredAcknowledgementRequest;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.placeholders.ExpressionResolver;
import org.eclipse.ditto.model.placeholders.PlaceholderFactory;
import org.eclipse.ditto.model.placeholders.PlaceholderFilter;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.ProtocolAdapter;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.base.config.limits.DefaultLimitsConfig;
import org.eclipse.ditto.services.base.config.limits.LimitsConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.DittoConnectivityConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.MonitoringConfig;
import org.eclipse.ditto.services.connectivity.messaging.mappingoutcome.MappingOutcome;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.DefaultConnectionMonitorRegistry;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.InfoProviderFactory;
import org.eclipse.ditto.services.connectivity.messaging.validation.ConnectionValidator;
import org.eclipse.ditto.services.connectivity.util.ConnectivityMdcEntryKey;
import org.eclipse.ditto.services.models.acks.AcknowledgementAggregatorActorStarter;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.MappedInboundExternalMessage;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.acks.base.Acknowledgements;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.ErrorResponse;
import org.eclipse.ditto.signals.commands.messages.acks.MessageCommandAckRequestSetter;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.acks.ThingLiveCommandAckRequestSetter;
import org.eclipse.ditto.signals.commands.things.acks.ThingModifyCommandAckRequestSetter;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.PFBuilder;
import akka.japi.pf.ReceiveBuilder;
import scala.PartialFunction;
import scala.util.Either;
import scala.util.Left;
import scala.util.Right;

/**
 * This Actor dispatches inbound messages after they are mapped.
 */
public final class InboundDispatchingActor extends AbstractActor
        implements MappingOutcome.Visitor<MappedInboundExternalMessage, Optional<Signal<?>>> {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "inboundDispatching";

    private final DittoDiagnosticLoggingAdapter logger;

    private final HeaderTranslator headerTranslator;
    private final ConnectionId connectionId;
    private final ActorRef proxyActor;
    private final ActorRef connectionActor;
    private final DefaultConnectionMonitorRegistry connectionMonitorRegistry;
    private final ConnectionMonitor responseMappedMonitor;
    private final DittoRuntimeExceptionToErrorResponseFunction toErrorResponseFunction;
    private final AcknowledgementAggregatorActorStarter ackregatorStarter;
    private final ActorRef outboundMessageMappingProcessorActor;
    private final ExpressionResolver connectionIdResolver;

    @SuppressWarnings("unused")
    private InboundDispatchingActor(
            final Connection connection,
            final HeaderTranslator headerTranslator,
            final ActorRef proxyActor,
            final ActorRef connectionActor,
            final ActorRef outboundMessageMappingProcessorActor) {

        this.proxyActor = proxyActor;
        this.outboundMessageMappingProcessorActor = outboundMessageMappingProcessorActor;
        this.headerTranslator = headerTranslator;
        this.connectionId = connection.getId();
        this.connectionActor = connectionActor;

        logger = DittoLoggerFactory.getDiagnosticLoggingAdapter(this)
                .withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID, connectionId);

        connectionIdResolver = PlaceholderFactory.newExpressionResolver(PlaceholderFactory.newConnectionIdPlaceholder(),
                connectionId);

        final DefaultScopedConfig dittoScoped =
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config());

        final DittoConnectivityConfig connectivityConfig = DittoConnectivityConfig.of(dittoScoped);
        final MonitoringConfig monitoringConfig = connectivityConfig.getMonitoringConfig();
        final LimitsConfig limitsConfig = DefaultLimitsConfig.of(dittoScoped);

        connectionMonitorRegistry = DefaultConnectionMonitorRegistry.fromConfig(monitoringConfig);
        responseMappedMonitor = connectionMonitorRegistry.forResponseMapped(connectionId);
        toErrorResponseFunction = DittoRuntimeExceptionToErrorResponseFunction.of(limitsConfig.getHeadersMaxSize());
        ackregatorStarter = AcknowledgementAggregatorActorStarter.of(getContext(),
                connectivityConfig.getConnectionConfig().getAcknowledgementConfig(),
                headerTranslator,
                ThingModifyCommandAckRequestSetter.getInstance(),
                ThingLiveCommandAckRequestSetter.getInstance(),
                MessageCommandAckRequestSetter.getInstance());
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connection the connection
     * @param headerTranslator the headerTranslator to use.
     * @param proxyActor the actor used to send signals into the ditto cluster.
     * @param connectionActor the connection actor acting as the grandparent of this actor.
     * @param outboundMessageMappingProcessorActor used to publish errors.
     * @return the Akka configuration Props object.
     */
    public static Props props(final Connection connection,
            final HeaderTranslator headerTranslator,
            final ActorRef proxyActor,
            final ActorRef connectionActor,
            final ActorRef outboundMessageMappingProcessorActor) {

        return Props.create(InboundDispatchingActor.class,
                connection,
                headerTranslator,
                proxyActor,
                connectionActor,
                outboundMessageMappingProcessorActor
        );
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(InboundMappingOutcomes.class, InboundMappingOutcomes::hasError, this::dispatchError)
                .match(InboundMappingOutcomes.class, this::dispatchMapped)
                .match(DittoRuntimeException.class, this::onDittoRuntimeException)
                .matchAny(message -> logger.warning("Received unknown message <{}>.", message))
                .build();
    }

    private void onDittoRuntimeException(final DittoRuntimeException dittoRuntimeException) {
        onError(dittoRuntimeException, null, null);
    }

    private void dispatchError(final InboundMappingOutcomes outcomes) {
        onError(outcomes.getError(), null, outcomes.getExternalMessage());
    }

    private void dispatchMapped(final InboundMappingOutcomes outcomes) {
        final ActorRef sender = getSender();
        final PartialFunction<Signal<?>, Stream<IncomingSignal>> dispatchResponsesAndSearchCommands =
                dispatchResponsesAndSearchCommands(sender, getDeclaredAckLabels(outcomes));
        final int ackRequestingSignalCount = outcomes.getOutcomes()
                .stream()
                .map(this::eval)
                .flatMap(Optional::stream)
                .flatMap(dispatchResponsesAndSearchCommands::apply)
                .mapToInt(this::dispatchIncomingSignal)
                .sum();
        sender.tell(ResponseCollectorActor.setCount(ackRequestingSignalCount), getSelf());
    }

    private Set<AcknowledgementLabel> getDeclaredAckLabels(final InboundMappingOutcomes outcomes) {
        return outcomes.getExternalMessage()
                .getSource()
                .map(Source::getDeclaredAcknowledgementLabels)
                .orElse(Set.of())
                .stream()
                .map(ackLabel -> ConnectionValidator.resolveConnectionIdPlaceholder(connectionIdResolver, ackLabel))
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());
    }

    @Override
    public Optional<Signal<?>> onMapped(final MappedInboundExternalMessage mappedInboundMessage) {
        final ExternalMessage incomingMessage = mappedInboundMessage.getSource();
        final String source = getAddress(incomingMessage);
        final Signal<?> signal = mappedInboundMessage.getSignal();
        final AuthorizationContext authorizationContext = getAuthorizationContextOrThrow(incomingMessage);
        final ConnectionMonitor.InfoProvider infoProvider = InfoProviderFactory.forExternalMessage(incomingMessage);
        final ConnectionMonitor mappedMonitor = connectionMonitorRegistry.forInboundMapped(connectionId, source);

        final DittoHeaders mappedHeaders =
                applyInboundHeaderMapping(signal, incomingMessage, authorizationContext,
                        mappedInboundMessage.getTopicPath(), incomingMessage.getInternalHeaders());

        final Signal<?> adjustedSignal = appendConnectionAcknowledgementsToSignal(incomingMessage,
                signal.setDittoHeaders(mappedHeaders));

        // enforce signal ID after header mapping was done
        connectionMonitorRegistry.forInboundEnforced(connectionId, source)
                .wrapExecution(adjustedSignal)
                .execute(() -> applySignalIdEnforcement(incomingMessage, signal));
        // the above throws an exception if signal id enforcement fails

        mappedMonitor.success(infoProvider);
        return Optional.of(adjustedSignal);
    }

    @Override
    public Optional<Signal<?>> onDropped(@Nullable final ExternalMessage incomingMessage) {
        logger.debug("Message mapping returned null, message is dropped.");
        if (incomingMessage != null) {
            final String source = getAddress(incomingMessage);
            final ConnectionMonitor.InfoProvider infoProvider = InfoProviderFactory.forExternalMessage(incomingMessage);
            final ConnectionMonitor droppedMonitor = connectionMonitorRegistry.forInboundDropped(connectionId, source);
            droppedMonitor.success(infoProvider);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Signal<?>> onError(@Nullable final Exception e,
            @Nullable final TopicPath topicPath,
            @Nullable final ExternalMessage message) {

        if (e instanceof DittoRuntimeException) {
            final DittoRuntimeException dittoRuntimeException = (DittoRuntimeException) e;
            final ErrorResponse<?> errorResponse = toErrorResponseFunction.apply(dittoRuntimeException, topicPath);
            final DittoHeaders mappedHeaders;
            if (message != null) {
                final AuthorizationContext authorizationContext = getAuthorizationContext(message).orElse(null);
                final String source = getAddress(message);
                final ConnectionMonitor mappedMonitor =
                        connectionMonitorRegistry.forInboundMapped(connectionId, source);
                mappedMonitor.getLogger()
                        .failure("Got exception {0} when processing external message: {1}",
                                dittoRuntimeException.getErrorCode(),
                                e.getMessage());
                mappedHeaders = applyInboundHeaderMapping(errorResponse, message, authorizationContext,
                        message.getTopicPath().orElse(null), message.getInternalHeaders());
                logger.info("Resolved mapped headers of {} : with HeaderMapping {} : and external headers {}",
                        mappedHeaders, message.getHeaderMapping(), message.getHeaders());
            } else {
                mappedHeaders = dittoRuntimeException.getDittoHeaders();
            }
            outboundMessageMappingProcessorActor.tell(errorResponse.setDittoHeaders(mappedHeaders),
                    ActorRef.noSender());
        } else if (e != null) {
            responseMappedMonitor.getLogger()
                    .failure("Got unknown exception when processing external message: {1}", e.getMessage());
            if (message != null) {
                logger.withCorrelationId(message.getInternalHeaders());
            }
            logger.warning("Got <{}> when message was processed: <{}>", e.getClass().getSimpleName(),
                    e.getMessage());
            logger.discardCorrelationId();
        }
        return Optional.empty();
    }

    private String getAddress(final ExternalMessage incomingMessage) {
        return incomingMessage.getSourceAddress().orElse("unknown");
    }

    private PartialFunction<Signal<?>, Stream<IncomingSignal>> dispatchResponsesAndSearchCommands(final ActorRef sender,
            final Set<AcknowledgementLabel> declaredAckLabels) {
        final PartialFunction<Signal<?>, Signal<?>> appendConnectionId = new PFBuilder<Signal<?>, Signal<?>>()
                .match(Acknowledgements.class, acks -> appendConnectionIdToAcknowledgements(acks, connectionId))
                .match(CommandResponse.class,
                        ack -> appendConnectionIdToAcknowledgementOrResponse(ack, connectionId))
                .matchAny(x -> x)
                .build();

        final PartialFunction<Signal<?>, Stream<IncomingSignal>> dispatchSignal =
                new PFBuilder<Signal<?>, Stream<IncomingSignal>>()
                        .match(Acknowledgement.class, ack -> forwardAcknowledgement(ack, declaredAckLabels))
                        .match(Acknowledgements.class, acks -> forwardAcknowledgements(acks, declaredAckLabels))
                        .match(CommandResponse.class, ProtocolAdapter::isLiveSignal, liveResponse ->
                                forwardToConnectionActor(liveResponse, ActorRef.noSender())
                        )
                        .match(ThingSearchCommand.class, cmd -> forwardToConnectionActor(cmd, sender))
                        .matchAny(baseSignal -> ackregatorStarter.preprocess(baseSignal,
                                (signal, isAckRequesting) -> Stream.of(new IncomingSignal(signal,
                                        getReturnAddress(sender, isAckRequesting, signal),
                                        isAckRequesting)),
                                headerInvalidException -> {
                                    // tell the response collector to settle negatively without redelivery
                                    sender.tell(headerInvalidException, ActorRef.noSender());
                                    // publish the error response
                                    outboundMessageMappingProcessorActor.tell(
                                            ThingErrorResponse.of(headerInvalidException),
                                            ActorRef.noSender());
                                    return Stream.empty();
                                }))
                        .build();

        return appendConnectionId.andThen(dispatchSignal);
    }

    /**
     * Handle incoming signals that request acknowledgements in the actor's thread, since creating the necessary
     * acknowledgement aggregators is not thread-safe.
     *
     * @param incomingSignal the signal requesting acknowledgements together with its original sender,
     * the response collector actor.
     * @return 1 if the signal is ack-requesting, 0 if it is not.
     */
    private int dispatchIncomingSignal(final IncomingSignal incomingSignal) {
        final Signal<?> signal = incomingSignal.signal;
        final ActorRef sender = incomingSignal.sender;
        if (incomingSignal.isAckRequesting) {
            try {
                startAckregatorAndForwardSignal(signal, sender);
            } catch (final DittoRuntimeException e) {
                handleErrorDuringStartingOfAckregator(e, signal.getDittoHeaders(), sender);
            }
            return 1;
        } else {
            proxyActor.tell(signal, sender);
            return 0;
        }
    }

    private void startAckregatorAndForwardSignal(final Signal<?> signal, @Nullable final ActorRef sender) {
        ackregatorStarter.doStart(signal,
                responseSignal -> {
                    // potentially publish response/aggregated acks to reply target
                    if (signal.getDittoHeaders().isResponseRequired()) {
                        outboundMessageMappingProcessorActor.tell(responseSignal, getSelf());
                    }

                    // forward acks to the original sender for consumer settlement
                    if (sender != null) {
                        sender.tell(responseSignal, ActorRef.noSender());
                    }
                },
                ackregator -> {
                    proxyActor.tell(signal, ackregator);
                    return null;
                });
    }

    private void handleErrorDuringStartingOfAckregator(final DittoRuntimeException e,
            final DittoHeaders dittoHeaders, @Nullable final ActorRef sender) {
        logger.withCorrelationId(dittoHeaders.getCorrelationId().orElse("?"))
                .info("Got 'DittoRuntimeException' during 'startAcknowledgementAggregator':" +
                        " {}: <{}>", e.getClass().getSimpleName(), e.getMessage());
        responseMappedMonitor.getLogger()
                .failure("Got exception {0} when processing external message: {1}",
                        e.getErrorCode(), e.getMessage());
        final ErrorResponse<?> errorResponse = toErrorResponseFunction.apply(e, null);
        // tell sender the error response for consumer settlement
        if (sender != null) {
            sender.tell(errorResponse, getSelf());
        }
        // publish error response
        outboundMessageMappingProcessorActor.tell(errorResponse.setDittoHeaders(dittoHeaders), ActorRef.noSender());
    }

    /**
     * Only special Signals must be forwarded to the {@code ConnectionPersistenceActor}:
     * <ul>
     * <li>{@code Acknowledgement}s which were received via an incoming connection source</li>
     * <li>live {@code CommandResponse}s which were received via an incoming connection source</li>
     * <li>{@code SearchCommand}s which were received via an incoming connection source</li>
     * </ul>
     *
     * @param signal the Signal to forward to the connectionActor
     * @param sender the sender which shall receive the response
     * @param <T> type of elements for the next step..
     * @return an empty source of Signals
     */
    private <T> Stream<T> forwardToConnectionActor(final Signal<?> signal,
            @Nullable final ActorRef sender) {
        connectionActor.tell(signal, sender);
        return Stream.empty();
    }

    private <T> Stream<T> forwardAcknowledgement(final Acknowledgement ack,
            final Set<AcknowledgementLabel> declaredAckLabels) {
        if (declaredAckLabels.contains(ack.getLabel())) {
            return forwardToConnectionActor(ack, outboundMessageMappingProcessorActor);
        } else {
            outboundMessageMappingProcessorActor.tell(
                    AcknowledgementLabelNotDeclaredException.of(ack.getLabel(), ack.getDittoHeaders()),
                    ActorRef.noSender());
            return Stream.empty();
        }
    }

    private <T> Stream<T> forwardAcknowledgements(final Acknowledgements acks,
            final Set<AcknowledgementLabel> declaredAckLabels) {
        final Optional<AcknowledgementLabelNotDeclaredException> ackLabelNotDeclaredException = acks.stream()
                .map(Acknowledgement::getLabel)
                .filter(label -> !declaredAckLabels.contains(label))
                .map(label -> AcknowledgementLabelNotDeclaredException.of(label, acks.getDittoHeaders()))
                .findAny();
        if (ackLabelNotDeclaredException.isPresent()) {
            outboundMessageMappingProcessorActor.tell(ackLabelNotDeclaredException.get(), ActorRef.noSender());
            return Stream.empty();
        }
        return forwardToConnectionActor(acks, outboundMessageMappingProcessorActor);
    }

    @Nullable
    private ActorRef getReturnAddress(final ActorRef sender, final boolean isAcksRequesting,
            final Signal<?> signal) {
        // acks-requesting signals: all replies should be directed to the sender address (ack. aggregator actor)
        // other commands: set this actor as return address to receive any errors to publish.
        if (isAcksRequesting) {
            return sender;
        } else if (signal instanceof Command<?> && signal.getDittoHeaders().isResponseRequired()) {
            return outboundMessageMappingProcessorActor;
        } else {
            return ActorRef.noSender();
        }
    }

    private Signal<?> appendConnectionAcknowledgementsToSignal(final ExternalMessage message,
            final Signal<?> signal) {
        if (!canRequestAcks(signal)) {
            return signal;
        }
        final Set<AcknowledgementRequest> additionalAcknowledgementRequests = message.getSource()
                .flatMap(Source::getAcknowledgementRequests)
                .map(FilteredAcknowledgementRequest::getIncludes)
                .orElse(Collections.emptySet());
        final String filter = message.getSource()
                .flatMap(Source::getAcknowledgementRequests)
                .flatMap(FilteredAcknowledgementRequest::getFilter)
                .orElse(null);

        if (additionalAcknowledgementRequests.isEmpty()) {
            // do not change the signal's header if no additional acknowledgementRequests are defined in the Source
            // to preserve the default behavior for signals without the header 'requested-acks'
            return filterAcknowledgements(signal, filter, connectionId);
        } else {
            // The Source's acknowledgementRequests get appended to the requested-acks DittoHeader of the mapped signal
            final Set<AcknowledgementRequest> combinedRequestedAcks =
                    new HashSet<>(signal.getDittoHeaders().getAcknowledgementRequests());
            combinedRequestedAcks.addAll(additionalAcknowledgementRequests);

            return filterAcknowledgements(signal.setDittoHeaders(
                    signal.getDittoHeaders()
                            .toBuilder()
                            .acknowledgementRequests(combinedRequestedAcks)
                            .build()),
                    filter,
                    connectionId);
        }
    }

    /**
     * Helper applying the {@link org.eclipse.ditto.model.connectivity.EnforcementFilter} of the passed in {@link org.eclipse.ditto.services.models.connectivity.ExternalMessage} by throwing a {@link
     * org.eclipse.ditto.model.connectivity.ConnectionSignalIdEnforcementFailedException} if the enforcement failed.
     */
    private void applySignalIdEnforcement(final ExternalMessage externalMessage, final Signal<?> signal) {
        externalMessage.getEnforcementFilter().ifPresent(enforcementFilter -> {
            logger.withCorrelationId(signal)
                    .debug("Connection Signal ID Enforcement enabled - matching Signal ID <{}> with filter <{}>.",
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
            @Nullable final TopicPath topicPath,
            final DittoHeaders extraInternalHeaders) {

        return externalMessage.getHeaderMapping()
                .map(mapping -> {
                    final ExpressionResolver expressionResolver =
                            Resolvers.forInbound(externalMessage, signal, topicPath, authorizationContext,
                                    connectionId);

                    final DittoHeadersBuilder<?, ?> dittoHeadersBuilder = signal.getDittoHeaders().toBuilder();

                    // Add mapped external headers as if they were injected into the Adaptable.
                    final Map<String, String> mappedExternalHeaders = mapping.getMapping()
                            .entrySet()
                            .stream()
                            .flatMap(e -> PlaceholderFilter.applyOrElseDelete(e.getValue(), expressionResolver)
                                    .stream()
                                    .map(resolvedValue -> new AbstractMap.SimpleEntry<>(e.getKey(), resolvedValue))
                            )
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                    dittoHeadersBuilder.putHeaders(headerTranslator.fromExternalHeaders(mappedExternalHeaders));

                    final String correlationIdKey = DittoHeaderDefinition.CORRELATION_ID.getKey();
                    final boolean hasCorrelationId = mapping.getMapping().containsKey(correlationIdKey) ||
                            signal.getDittoHeaders().getCorrelationId().isPresent();

                    final DittoHeaders newHeaders =
                            appendInternalHeaders(dittoHeadersBuilder, authorizationContext, extraInternalHeaders,
                                    !hasCorrelationId).build();

                    logger.withCorrelationId(newHeaders)
                            .debug("Result of header mapping <{}> are these headers: {}", mapping, newHeaders);
                    return newHeaders;
                })
                .orElseGet(() ->
                        appendInternalHeaders(
                                signal.getDittoHeaders().toBuilder(),
                                authorizationContext,
                                extraInternalHeaders,
                                signal.getDittoHeaders().getCorrelationId().isEmpty()
                        ).build()
                );
    }

    private DittoHeadersBuilder<?, ?> appendInternalHeaders(final DittoHeadersBuilder<?, ?> builder,
            @Nullable final AuthorizationContext authorizationContext,
            final DittoHeaders extraInternalHeaders,
            final boolean appendRandomCorrelationId) {

        builder.putHeaders(extraInternalHeaders).origin(connectionId);
        if (authorizationContext != null) {
            builder.authorizationContext(authorizationContext);
        }
        if (appendRandomCorrelationId && extraInternalHeaders.getCorrelationId().isEmpty()) {
            builder.randomCorrelationId();
        }
        return builder;
    }

    /**
     * Only used for testing
     * TODO: Extract logic into separate class and test it there.
     *
     * @param signal signal to filter requested acknowledges for
     * @param filter the filter string
     * @param connectionId the connection ID receiving the signal.
     * @return the filtered signal.
     */
    static Signal<?> filterAcknowledgements(final Signal<?> signal, final @Nullable String filter,
            final ConnectionId connectionId) {
        if (filter != null) {
            final String requestedAcks = DittoHeaderDefinition.REQUESTED_ACKS.getKey();
            final boolean headerDefined = signal.getDittoHeaders().containsKey(requestedAcks);
            final String fullFilter = "header:" + requestedAcks + "|fn:default('[]')|" + filter;
            final ExpressionResolver resolver = Resolvers.forSignal(signal, connectionId);
            final Optional<String> resolverResult = resolver.resolveAsPipelineElement(fullFilter).toOptional();
            if (resolverResult.isEmpty()) {
                // filter tripped: set requested-acks to []
                return signal.setDittoHeaders(DittoHeaders.newBuilder(signal.getDittoHeaders())
                        .acknowledgementRequests(Collections.emptySet())
                        .build());
            } else if (headerDefined) {
                // filter not tripped, header defined
                return signal.setDittoHeaders(DittoHeaders.newBuilder(signal.getDittoHeaders())
                        .putHeader(requestedAcks, resolverResult.orElseThrow())
                        .build());
            } else {
                // filter not tripped, header not defined:
                // - evaluate filter again against unresolved and set requested-acks accordingly
                // - if filter is not resolved, then keep requested-acks undefined for the default behavior
                final Optional<String> unsetFilterResult =
                        resolver.resolveAsPipelineElement(filter).toOptional();
                return unsetFilterResult.<Signal<?>>map(newAckRequests ->
                        signal.setDittoHeaders(DittoHeaders.newBuilder(signal.getDittoHeaders())
                                .putHeader(requestedAcks, newAckRequests)
                                .build()))
                        .orElse(signal);
            }
        }
        return signal;
    }

    /**
     * Appends the ConnectionId to the processed {@code commandResponse} payload.
     *
     * @param commandResponse the CommandResponse (or Acknowledgement as subtype) to append the ConnectionId to
     * @param connectionId the ConnectionId to append to the CommandResponse's DittoHeader
     * @param <T> the type of the CommandResponse
     * @return the CommandResponse with appended ConnectionId.
     */
    static <T extends CommandResponse<T>> T appendConnectionIdToAcknowledgementOrResponse(final T commandResponse,
            final ConnectionId connectionId) {
        final DittoHeaders newHeaders = commandResponse.getDittoHeaders()
                .toBuilder()
                .putHeader(DittoHeaderDefinition.CONNECTION_ID.getKey(), connectionId.toString())
                .build();
        return commandResponse.setDittoHeaders(newHeaders);
    }

    static Acknowledgements appendConnectionIdToAcknowledgements(final Acknowledgements acknowledgements,
            final ConnectionId connectionId) {
        final List<Acknowledgement> acksList = acknowledgements.stream()
                .map(ack -> appendConnectionIdToAcknowledgementOrResponse(ack, connectionId))
                .collect(Collectors.toList());
        // Uses EntityId and StatusCode from input acknowledges expecting these were set when Acknowledgements was created
        return Acknowledgements.of(acknowledgements.getEntityId(), acksList, acknowledgements.getStatusCode(),
                acknowledgements.getDittoHeaders());
    }

    private static Optional<AuthorizationContext> getAuthorizationContext(final ExternalMessage externalMessage) {
        final Either<RuntimeException, AuthorizationContext> result =
                getAuthorizationContextAsEither(externalMessage);
        if (result.isRight()) {
            return Optional.of(result.right().get());
        } else {
            return Optional.empty();
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

    private static Either<RuntimeException, AuthorizationContext> getAuthorizationContextAsEither(
            final ExternalMessage externalMessage) {

        return externalMessage.getAuthorizationContext()
                .filter(authorizationContext -> !authorizationContext.isEmpty())
                .<Either<RuntimeException, AuthorizationContext>>map(authorizationContext -> {
                    try {
                        return new Right<>(
                                PlaceholderFilter.applyHeadersPlaceholderToAuthContext(authorizationContext,
                                        externalMessage.getHeaders()));
                    } catch (final RuntimeException e) {
                        return new Left<>(e);
                    }
                })
                .orElseGet(() ->
                        new Left<>(new IllegalArgumentException("No nonempty authorization context is available")));

    }

    private static boolean canRequestAcks(final Signal<?> signal) {
        return isApplicable(ThingModifyCommandAckRequestSetter.getInstance(), signal) ||
                isApplicable(ThingLiveCommandAckRequestSetter.getInstance(), signal) ||
                isApplicable(MessageCommandAckRequestSetter.getInstance(), signal);
    }

    private static <C extends WithDittoHeaders<? extends C>> boolean isApplicable(
            final AbstractCommandAckRequestSetter<C> setter, final Signal<?> signal) {
        return setter.getMatchedClass().isInstance(signal) &&
                setter.isApplicable(setter.getMatchedClass().cast(signal));
    }

    private static final class IncomingSignal {

        private final Signal<?> signal;
        @Nullable private final ActorRef sender;
        private final boolean isAckRequesting;

        private IncomingSignal(final Signal<?> signal, @Nullable final ActorRef sender,
                final boolean isAckRequesting) {
            this.signal = signal;
            this.sender = sender;
            this.isAckRequesting = isAckRequesting;
        }
    }

}
