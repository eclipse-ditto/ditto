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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.acks.AbstractCommandAckRequestSetter;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabelNotDeclaredException;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.acks.FilteredAcknowledgementRequest;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.WithStreamingSubscriptionId;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.commands.ErrorResponse;
import org.eclipse.ditto.base.model.signals.commands.streaming.SubscribeForPersistedEvents;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.InboundSignal;
import org.eclipse.ditto.connectivity.api.MappedInboundExternalMessage;
import org.eclipse.ditto.connectivity.api.messaging.monitoring.logs.LogEntryFactory;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectivityInternalErrorException;
import org.eclipse.ditto.connectivity.model.LogType;
import org.eclipse.ditto.connectivity.model.MetricDirection;
import org.eclipse.ditto.connectivity.model.MetricType;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityErrorResponse;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.mappingoutcome.MappingOutcome;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.DefaultConnectionMonitorRegistry;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.InfoProviderFactory;
import org.eclipse.ditto.connectivity.service.messaging.validation.ConnectionValidator;
import org.eclipse.ditto.connectivity.service.placeholders.ConnectivityPlaceholders;
import org.eclipse.ditto.connectivity.service.util.ConnectivityMdcEntryKey;
import org.eclipse.ditto.edge.service.acknowledgements.AcknowledgementAggregatorActorStarter;
import org.eclipse.ditto.edge.service.acknowledgements.AcknowledgementConfig;
import org.eclipse.ditto.edge.service.acknowledgements.message.MessageCommandAckRequestSetter;
import org.eclipse.ditto.edge.service.acknowledgements.message.MessageCommandResponseAcknowledgementProvider;
import org.eclipse.ditto.edge.service.acknowledgements.things.ThingCommandResponseAcknowledgementProvider;
import org.eclipse.ditto.edge.service.acknowledgements.things.ThingLiveCommandAckRequestSetter;
import org.eclipse.ditto.edge.service.acknowledgements.things.ThingModifyCommandAckRequestSetter;
import org.eclipse.ditto.edge.service.headers.DittoHeadersValidator;
import org.eclipse.ditto.internal.models.signal.CommandHeaderRestoration;
import org.eclipse.ditto.internal.models.signal.correlation.MatchingValidationResult;
import org.eclipse.ditto.internal.models.signal.type.SemanticSignalType;
import org.eclipse.ditto.internal.models.signal.type.SignalTypeCategory;
import org.eclipse.ditto.internal.models.signal.type.SignalTypeFormatException;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.placeholders.ExpressionResolver;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.placeholders.PlaceholderFilter;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.TopicPathBuilder;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapter;
import org.eclipse.ditto.protocol.mappingstrategies.IllegalAdaptableException;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingErrorResponse;
import org.eclipse.ditto.thingsearch.model.signals.commands.WithSubscriptionId;
import org.eclipse.ditto.thingsearch.model.signals.commands.subscription.CreateSubscription;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.ActorSelection;
import akka.japi.pf.PFBuilder;
import akka.pattern.Patterns;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import scala.PartialFunction;
import scala.util.Either;
import scala.util.Left;
import scala.util.Right;

/**
 * This Sink dispatches inbound messages after they are mapped.
 */
public final class InboundDispatchingSink
        implements MappingOutcome.Visitor<MappedInboundExternalMessage, Optional<Signal<?>>> {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "inboundDispatching";

    private static final String UNKNOWN_MAPPER_ID = "?";

    private final ThreadSafeDittoLogger logger;

    private final HeaderTranslator headerTranslator;
    private final Connection connection;
    private final ActorSelection proxyActor;
    private final ActorRef connectionActor;
    private final ActorRef outboundMessageMappingProcessorActor;
    private final ActorRef clientActor;

    private final ExpressionResolver connectionIdResolver;
    private final DefaultConnectionMonitorRegistry connectionMonitorRegistry;
    private final ConnectionMonitor responseMappedMonitor;
    private final DittoRuntimeExceptionToErrorResponseFunction toErrorResponseFunction;
    private final AcknowledgementAggregatorActorStarter ackregatorStarter;
    private final AcknowledgementConfig acknowledgementConfig;

    @SuppressWarnings("ununsed")
    private InboundDispatchingSink(final Connection connection,
            final HeaderTranslator headerTranslator,
            final ActorSelection proxyActor,
            final ActorRef connectionActor,
            final ActorRef outboundMessageMappingProcessorActor,
            final ActorRef clientActor,
            final ConnectivityConfig connectivityConfig,
            final ActorRefFactory actorRefFactory,
            final DittoHeadersValidator dittoHeadersValidator,
            @Nullable final Consumer<MatchingValidationResult.Failure> responseValidationFailureConsumer) {

        this.connection = checkNotNull(connection, "connection");
        this.headerTranslator = checkNotNull(headerTranslator, "headerTranslator");
        this.proxyActor = checkNotNull(proxyActor, "proxyActor");
        this.connectionActor = checkNotNull(connectionActor, "connectionActor");
        this.outboundMessageMappingProcessorActor = checkNotNull(outboundMessageMappingProcessorActor,
                "outboundMessageMappingProcessorActor");
        this.clientActor = checkNotNull(clientActor, "clientActor");
        checkNotNull(connectivityConfig, "connectivityConfig");
        checkNotNull(actorRefFactory, "actorRefFactory");

        logger = DittoLoggerFactory.getThreadSafeLogger(InboundDispatchingSink.class)
                .withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID, connection.getId());

        connectionIdResolver = PlaceholderFactory.newExpressionResolver(
                ConnectivityPlaceholders.newConnectionIdPlaceholder(),
                connection.getId());

        connectionMonitorRegistry = DefaultConnectionMonitorRegistry.fromConfig(connectivityConfig);
        responseMappedMonitor = connectionMonitorRegistry.forResponseMapped(connection);
        toErrorResponseFunction = DittoRuntimeExceptionToErrorResponseFunction.of(dittoHeadersValidator);
        acknowledgementConfig = connectivityConfig.getConnectionConfig().getAcknowledgementConfig();
        ackregatorStarter = AcknowledgementAggregatorActorStarter.of(actorRefFactory,
                acknowledgementConfig,
                headerTranslator,
                responseValidationFailureConsumer,
                List.of(
                        ThingModifyCommandAckRequestSetter.getInstance(),
                        ThingLiveCommandAckRequestSetter.getInstance(),
                        MessageCommandAckRequestSetter.getInstance()
                ),
                List.of(
                        ThingCommandResponseAcknowledgementProvider.getInstance(),
                        MessageCommandResponseAcknowledgementProvider.getInstance()
                )
        );
    }

    /**
     * Creates a Sink that dispatches inbound messages after they are mapped.
     *
     * @param connection the connection
     * @param headerTranslator the headerTranslator to use.
     * @param proxyActor the actor used to send signals into the ditto cluster.
     * @param connectionActor the connection actor acting as the grandparent of this actor.
     * @param outboundMessageMappingProcessorActor used to publish errors.
     * @param clientActor the client actor ref to forward commands to.
     * @param actorRefFactory the ActorRefFactory to use in order to create new actors in.
     * @param connectivityConfig the connectivity configuration including potential overwrites.
     * @param responseValidationFailureConsumer optional handler for response validation failures.
     * @return the Sink.
     * @throws java.lang.NullPointerException if any of the passed arguments was {@code null}.
     */
    public static Sink<Object, NotUsed> createSink(final Connection connection,
            final HeaderTranslator headerTranslator,
            final ActorSelection proxyActor,
            final ActorRef connectionActor,
            final ActorRef outboundMessageMappingProcessorActor,
            final ActorRef clientActor,
            final ActorRefFactory actorRefFactory,
            final ConnectivityConfig connectivityConfig,
            final DittoHeadersValidator dittoHeadersValidator,
            @Nullable final Consumer<MatchingValidationResult.Failure> responseValidationFailureConsumer) {

        final var inboundDispatchingSink = new InboundDispatchingSink(
                connection,
                headerTranslator,
                proxyActor,
                connectionActor,
                outboundMessageMappingProcessorActor,
                clientActor,
                connectivityConfig,
                actorRefFactory,
                dittoHeadersValidator,
                responseValidationFailureConsumer
        );
        return inboundDispatchingSink.getSink();
    }

    private Sink<Object, NotUsed> getSink() {
        return Flow.create()
                .divertTo(dispatchError(), InboundDispatchingSink::isInboundMappingOutcomesWithError)
                .divertTo(dispatchMapped(), InboundMappingOutcomes.class::isInstance)
                .divertTo(onDittoRuntimeException(), DittoRuntimeException.class::isInstance)
                .to(Sink.foreach(message -> logger.warn("Received unknown message <{}>.", message)));
    }

    private static boolean isInboundMappingOutcomesWithError(final Object streamingElement) {
        return streamingElement instanceof InboundMappingOutcomes inboundMappingOutcomes &&
                inboundMappingOutcomes.hasError();
    }

    private Sink<Object, NotUsed> onDittoRuntimeException() {
        return Flow.fromFunction(DittoRuntimeException.class::cast)
                .to(Sink.foreach(this::onDittoRuntimeException));
    }

    private void onDittoRuntimeException(final DittoRuntimeException dittoRuntimeException) {
        onError(UNKNOWN_MAPPER_ID, dittoRuntimeException, null, null);
    }

    private Sink<Object, NotUsed> dispatchError() {
        return Flow.fromFunction(InboundMappingOutcomes.class::cast)
                .to(Sink.foreach(this::dispatchError));
    }

    private void dispatchError(final InboundMappingOutcomes outcomes) {
        onError(UNKNOWN_MAPPER_ID, outcomes.getError(), null, outcomes.getExternalMessage());
    }

    private Sink<Object, NotUsed> dispatchMapped() {
        return Flow.fromFunction(InboundMappingOutcomes.class::cast)
                .to(Sink.foreach(this::dispatchMapped));
    }

    private void dispatchMapped(final InboundMappingOutcomes outcomes) {
        final var sender = outcomes.getSender();
        final var dispatchResponsesAndSearchCommands =
                dispatchResponsesAndSearchCommands(sender, outcomes);
        final var actualOutcomes = outcomes.getOutcomes();
        final var ackRequestingSignalCount = actualOutcomes.stream()
                .map(this::eval)
                .flatMap(Optional::stream)
                .flatMap(dispatchResponsesAndSearchCommands::apply)
                .mapToInt(this::dispatchIncomingSignal)
                .sum();
        logger.debug("OnMapped from <{}>: <{}>", sender, outcomes);
        sender.tell(ResponseCollectorActor.setCount(ackRequestingSignalCount), ActorRef.noSender());
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
    public Optional<Signal<?>> onMapped(final String mapperId,
            final MappedInboundExternalMessage mappedInboundMessage) {

        final ExternalMessage incomingMessage = mappedInboundMessage.getSource();
        final String source = getAddress(incomingMessage);
        final Signal<?> signal = mappedInboundMessage.getSignal();
        final AuthorizationContext authorizationContext = getAuthorizationContextOrThrow(incomingMessage);
        final ConnectionMonitor.InfoProvider infoProvider = InfoProviderFactory.forExternalMessage(incomingMessage);
        final ConnectionMonitor mappedMonitor = connectionMonitorRegistry.forInboundMapped(connection, source);

        final DittoHeaders mappedHeaders =
                applyInboundHeaderMapping(signal, incomingMessage, authorizationContext,
                        mappedInboundMessage.getTopicPath(), incomingMessage.getInternalHeaders())
                        .toBuilder()
                        .putHeaders(signal.getDittoHeaders()) // headers from signal take precedence
                        .build();

        logger.withCorrelationId(mappedHeaders).info("onMapped mappedHeaders {}", mappedHeaders);

        final Signal<?> adjustedSignal = appendConnectionAcknowledgementsToSignal(incomingMessage,
                signal.setDittoHeaders(mappedHeaders));

        // enforce signal ID after header mapping was done
        connectionMonitorRegistry.forInboundEnforced(connection, source)
                .wrapExecution(adjustedSignal)
                .execute(() -> applySignalIdEnforcement(incomingMessage, signal));
        // the above throws an exception if signal id enforcement fails

        mappedMonitor.success(infoProvider, "Successfully mapped incoming signal with mapper <{0}>", mapperId);
        return Optional.of(adjustedSignal);
    }

    @Override
    public Optional<Signal<?>> onDropped(final String mapperId, @Nullable final ExternalMessage incomingMessage) {
        logger.withCorrelationId(Optional.ofNullable(incomingMessage)
                .map(ExternalMessage::getHeaders)
                .map(h -> h.get(DittoHeaderDefinition.CORRELATION_ID.getKey()))
                .orElse(null)
        ).debug("Message mapping returned null, message is dropped.");
        if (incomingMessage != null) {
            final String source = getAddress(incomingMessage);
            final ConnectionMonitor.InfoProvider infoProvider = InfoProviderFactory.forExternalMessage(incomingMessage);
            final ConnectionMonitor droppedMonitor = connectionMonitorRegistry.forInboundDropped(connection, source);
            droppedMonitor.success(infoProvider,
                    "Payload mapping of mapper <{0}> returned null, incoming message is dropped", mapperId);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Signal<?>> onError(final String mapperId,
            @Nullable final Exception e,
            @Nullable final TopicPath topicPath,
            @Nullable final ExternalMessage message) {

        final Optional<Signal<?>> result;
        logger.debug("OnError mapperId=<{}> exception=<{}> topicPath=<{}> message=<{}>",
                mapperId, e, topicPath, message);
        if (e instanceof DittoRuntimeException dittoRuntimeException) {
            if (isIllegalAdaptableException(dittoRuntimeException)) {
                final var illegalAdaptableException = (IllegalAdaptableException) dittoRuntimeException;
                if (isAboutInvalidLiveResponse(illegalAdaptableException)) {
                    result = handleIllegalAdaptableExceptionForLiveResponse(illegalAdaptableException, message);
                } else {
                    result = handleGenericDittoRuntimeException(dittoRuntimeException, topicPath, message, mapperId);
                }
            } else {
                result = handleGenericDittoRuntimeException(dittoRuntimeException, topicPath, message, mapperId);
            }
        } else if (e != null) {
            if (null != message) {
                responseMappedMonitor.getLogger()
                        .failure(InfoProviderFactory.forExternalMessage(message),
                                "Got unknown exception when processing external message: {0}", e.getMessage());
            } else {
                responseMappedMonitor.getLogger()
                        .failure("Got unknown exception when processing external message: {0}", e.getMessage());
            }
            logger.withCorrelationId(Optional.ofNullable(message)
                    .map(ExternalMessage::getInternalHeaders)
                    .orElseGet(DittoHeaders::empty)
            ).warn("Got unknown exception <{}> when processing external message with mapper <{}>: <{}>",
                    e.getClass().getSimpleName(), mapperId, e.getMessage());
            result = Optional.empty();
        } else {
            result = Optional.empty();
        }
        return result;
    }

    private static boolean isIllegalAdaptableException(final DittoRuntimeException dittoRuntimeException) {
        return IllegalAdaptableException.ERROR_CODE.equals(dittoRuntimeException.getErrorCode());
    }

    private boolean isAboutInvalidLiveResponse(final IllegalAdaptableException illegalAdaptableException) {
        return Signal.isChannelLive(illegalAdaptableException) && isAboutResponse(illegalAdaptableException);
    }

    private boolean isAboutResponse(final IllegalAdaptableException illegalAdaptableException) {
        return illegalAdaptableException.getSignalType()
                .flatMap(this::tryToGetSemanticSignalType)
                .map(SemanticSignalType::getSignalTypeCategory)
                .filter(SignalTypeCategory.RESPONSE::equals)
                .isPresent();
    }

    private Optional<SemanticSignalType> tryToGetSemanticSignalType(final CharSequence signalType) {
        try {
            return Optional.of(SemanticSignalType.parseSemanticSignalType(signalType));
        } catch (final SignalTypeFormatException e) {
            logger.error("Failed to get {}: {}", SemanticSignalType.class.getSimpleName(), e.getMessage(), e);
            return Optional.empty();
        }
    }

    private Optional<Signal<?>> handleIllegalAdaptableExceptionForLiveResponse(
            final IllegalAdaptableException illegalAdaptableException,
            @Nullable final ExternalMessage inboundMessage
    ) {
        final Optional<Signal<?>> result;
        countAndLogIllegalAdaptableExceptionForLiveResponse(illegalAdaptableException, inboundMessage);
        if (isResponseRequired(illegalAdaptableException)) {
            result = Optional.of(toErrorResponseFunction.apply(illegalAdaptableException,
                    illegalAdaptableException.getTopicPath()));
        } else {
            result = Optional.empty();
        }
        return result;
    }

    private void countAndLogIllegalAdaptableExceptionForLiveResponse(
            final IllegalAdaptableException illegalAdaptableException,
            @Nullable final ExternalMessage inboundMessage
    ) {
        final var logEntry = LogEntryFactory.getLogEntryForIllegalCommandResponseAdaptable(
                illegalAdaptableException,
                MessageFormat.format("Live response is invalid: {0}", illegalAdaptableException.getMessage())
        );
        final var connectionMonitor = connectionMonitorRegistry.getMonitor(connection,
                getMetricTypeOrThrow(logEntry.getLogType()),
                MetricDirection.INBOUND,
                logEntry.getLogType(),
                logEntry.getLogCategory(),
                logEntry.getAddress().orElseGet(() -> getAddress(inboundMessage)));
        final var counter = connectionMonitor.getCounter();
        counter.recordFailure();
        final var connectionLogger = connectionMonitor.getLogger();
        connectionLogger.logEntry(logEntry);
    }

    private static MetricType getMetricTypeOrThrow(final LogType logType) {
        return MetricType.forName(logType.getType()).orElseThrow();
    }

    private static String getAddress(final ExternalMessage incomingMessage) {
        return incomingMessage.getSourceAddress().orElse("unknown");
    }

    private static boolean isResponseRequired(final WithDittoHeaders withDittoHeaders) {
        final var dittoHeaders = withDittoHeaders.getDittoHeaders();
        return dittoHeaders.isResponseRequired();
    }

    private Optional<Signal<?>> handleGenericDittoRuntimeException(final DittoRuntimeException dittoRuntimeException,
            @Nullable final TopicPath topicPath,
            @Nullable final ExternalMessage message,
            final String mapperId) {

        final var errorResponse = toErrorResponseFunction.apply(dittoRuntimeException, topicPath);
        final DittoHeaders mappedHeaders;
        if (message != null) {
            final var authorizationContext = getAuthorizationContext(message).orElse(null);
            final var mappedMonitor = connectionMonitorRegistry.forInboundMapped(connection, getAddress(message));
            mappedMonitor.getLogger()
                    .failure(InfoProviderFactory.forExternalMessage(message),
                            "Got exception <{0}> when processing external message with mapper <{1}>: {2}",
                            dittoRuntimeException.getErrorCode(),
                            mapperId,
                            dittoRuntimeException.getMessage());
            mappedHeaders = applyInboundHeaderMapping(errorResponse, message, authorizationContext,
                    message.getTopicPath().orElse(null), message.getInternalHeaders());
            final var l = logger.withCorrelationId(mappedHeaders);
            l.info("Got exception <{}> when processing external message with mapper <{}>: <{}>",
                    dittoRuntimeException.getErrorCode(),
                    mapperId,
                    dittoRuntimeException.getMessage());
            l.info("Resolved mapped headers of {} : with HeaderMapping {} : and external headers {}",
                    mappedHeaders, message.getHeaderMapping(), message.getHeaders());
        } else {
            mappedHeaders = dittoRuntimeException.getDittoHeaders();
        }
        outboundMessageMappingProcessorActor.tell(errorResponse.setDittoHeaders(mappedHeaders), ActorRef.noSender());
        return Optional.empty();
    }

    private PartialFunction<Signal<?>, Stream<IncomingSignal>> dispatchResponsesAndSearchCommands(final ActorRef sender,
            final InboundMappingOutcomes outcomes) {
        final Set<AcknowledgementLabel> declaredAckLabels = getDeclaredAckLabels(outcomes);
        final PartialFunction<Signal<?>, Signal<?>> appendConnectionId = new PFBuilder<Signal<?>, Signal<?>>()
                .match(Acknowledgements.class, this::appendConnectionIdToAcknowledgements)
                .match(CommandResponse.class, ack -> appendConnectionIdToAcknowledgementOrResponse(ack))
                .matchAny(x -> x)
                .build();

        final PartialFunction<Signal<?>, Stream<IncomingSignal>> dispatchSignal =
                new PFBuilder<Signal<?>, Stream<IncomingSignal>>()
                        .match(Acknowledgement.class, ack ->
                                forwardAcknowledgement(ack, declaredAckLabels, outcomes))
                        .match(Acknowledgements.class, acks ->
                                forwardAcknowledgements(acks, declaredAckLabels, outcomes))
                        .match(CommandResponse.class, ProtocolAdapter::isLiveSignal, liveResponse ->
                                forwardToClientActor(liveResponse, ActorRef.noSender())
                        )
                        .match(CreateSubscription.class, cmd -> forwardToConnectionActor(cmd, sender))
                        .match(WithSubscriptionId.class, cmd -> forwardToClientActor(cmd, sender))
                        .match(SubscribeForPersistedEvents.class, cmd -> forwardToConnectionActor(cmd, sender))
                        .match(WithStreamingSubscriptionId.class, cmd -> forwardToClientActor(cmd, sender))
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
        final Optional<EntityId> entityIdOptional = WithEntityId.getEntityId(signal);
        if (incomingSignal.isAckRequesting && entityIdOptional.isPresent() && signal instanceof Command<?> command) {
            try {
                startAckregatorAndForwardSignal(entityIdOptional.get(), command, sender);
            } catch (final DittoRuntimeException e) {
                handleErrorDuringStartingOfAckregator(e, signal.getDittoHeaders(), sender);
            }

            return 1;
        } else {
            if (sender != null && isLive(signal)) {
                final DittoHeaders originalHeaders = signal.getDittoHeaders();
                Patterns.ask(proxyActor, signal, originalHeaders.getTimeout()
                                .orElse(acknowledgementConfig.getForwarderFallbackTimeout())
                        ).thenApply(response -> {
                            if (response instanceof WithDittoHeaders) {
                                return CommandHeaderRestoration.restoreCommandConnectivityHeaders(
                                        (DittoHeadersSettable<?>) response,
                                        originalHeaders);
                            } else {
                                final String messageTemplate =
                                        "Expected response <%s> to be of type <%s> but was of type <%s>.";
                                final String errorMessage =
                                        String.format(messageTemplate, response, WithDittoHeaders.class.getName(),
                                                response.getClass().getName());
                                final ConnectivityInternalErrorException dre =
                                        ConnectivityInternalErrorException.newBuilder()
                                                .cause(new ClassCastException(errorMessage))
                                                .build();

                                return ConnectivityErrorResponse.of(dre, originalHeaders);
                            }
                        })
                        .thenAccept(response -> sender.tell(response, ActorRef.noSender()))
                        .exceptionally(throwable -> {
                            logger.withCorrelationId(incomingSignal.signal)
                                    .warn("Error during dispatching incoming signal <{}>: <{}: {}>", incomingSignal,
                                            throwable.getClass().getSimpleName(), throwable.getMessage());
                            return null;
                        });
            } else {
                proxyActor.tell(signal, sender);
            }

            return 0;
        }
    }

    private static boolean isLive(final Signal<?> signal) {
        return MessageCommand.isMessageCommand(signal) ||
                ThingCommand.isThingCommand(signal) && Signal.isChannelLive(signal);
    }

    private void startAckregatorAndForwardSignal(final EntityId entityId,
            final Command<?> command,
            @Nullable final ActorRef sender) {

        ackregatorStarter.doStart(entityId, command, null,
                responseSignal -> {

                    // potentially publish response/aggregated acks to reply target
                    final var signalDittoHeaders = command.getDittoHeaders();
                    if (signalDittoHeaders.isResponseRequired()) {
                        outboundMessageMappingProcessorActor.tell(responseSignal, ActorRef.noSender());
                    }

                    // forward acks to the original sender for consumer settlement
                    if (sender != null) {
                        sender.tell(responseSignal, ActorRef.noSender());
                    }

                    clientActor.tell(BaseClientActor.Control.ACKREGATOR_STOPPED, ActorRef.noSender());
                },
                (ackregator, adjustedSignal) -> {
                    clientActor.tell(BaseClientActor.Control.ACKREGATOR_STARTED, ActorRef.noSender());
                    proxyActor.tell(adjustedSignal, ackregator);
                    return null;
                });
    }

    private void handleErrorDuringStartingOfAckregator(final DittoRuntimeException e,
            final DittoHeaders dittoHeaders,
            @Nullable final ActorRef sender) {

        logger.withCorrelationId(dittoHeaders)
                .info("Got 'DittoRuntimeException' during 'startAcknowledgementAggregator': {}: <{}>",
                        e.getClass().getSimpleName(),
                        e.getMessage());
        responseMappedMonitor.getLogger()
                .failure(InfoProviderFactory.forHeaders(dittoHeaders),
                        "Got exception <{0}> when processing external message: {1}", e.getErrorCode(), e.getMessage());
        final ErrorResponse<?> errorResponse = toErrorResponseFunction.apply(e, null);

        // tell sender the error response for consumer settlement
        if (sender != null) {
            sender.tell(errorResponse, ActorRef.noSender());
        }

        // publish error response
        outboundMessageMappingProcessorActor.tell(errorResponse.setDittoHeaders(dittoHeaders), ActorRef.noSender());
    }

    /**
     * Only special Signals must be forwarded to the {@code ClientActor}:
     * <ul>
     * <li>{@code Acknowledgement}s which were received via an incoming connection source</li>
     * <li>live {@code CommandResponse}s which were received via an incoming connection source</li>
     * <li>{@code SearchCommand}s which were received via an incoming connection source</li>
     * </ul>
     *
     * @param signal the Signal to forward to the clientActor
     * @param sender the sender which shall receive the response
     * @param <T> type of elements for the next step..
     * @return an empty source of Signals
     */
    private <T> Stream<T> forwardToClientActor(final Signal<?> signal, @Nullable final ActorRef sender) {
        // wrap response or search command for dispatching by entity ID
        clientActor.tell(InboundSignal.of(signal), sender);

        return Stream.empty();
    }

    private <T> Stream<T> forwardToConnectionActor(final Command<?> command, @Nullable final ActorRef sender) {
        connectionActor.tell(command, sender);

        return Stream.empty();
    }

    private <T> Stream<T> forwardAcknowledgement(final Acknowledgement ack,
            final Collection<AcknowledgementLabel> declaredAckLabels,
            final InboundMappingOutcomes outcomes) {

        final Stream<T> result;
        if (declaredAckLabels.contains(ack.getLabel())) {
            result = forwardToClientActor(ack, outboundMessageMappingProcessorActor);
        } else {
            onError(UNKNOWN_MAPPER_ID,
                    AcknowledgementLabelNotDeclaredException.of(ack.getLabel(), ack.getDittoHeaders()),
                    getTopicPath(ack),
                    outcomes.getExternalMessage());
            result = Stream.empty();
        }

        return result;
    }

    private <T> Stream<T> forwardAcknowledgements(final Acknowledgements acks,
            final Collection<AcknowledgementLabel> declaredAckLabels,
            final InboundMappingOutcomes outcomes) {

        final Stream<T> result;
        final var ackLabelNotDeclaredException = acks.stream()
                .map(Acknowledgement::getLabel)
                .filter(label -> !declaredAckLabels.contains(label))
                .map(label -> AcknowledgementLabelNotDeclaredException.of(label, acks.getDittoHeaders()))
                .findAny();
        if (ackLabelNotDeclaredException.isPresent()) {
            onError(UNKNOWN_MAPPER_ID,
                    ackLabelNotDeclaredException.get(),
                    getTopicPath(acks),
                    outcomes.getExternalMessage());
            result = Stream.empty();
        } else {
            result = forwardToClientActor(acks, outboundMessageMappingProcessorActor);
        }

        return result;
    }

    private static TopicPath getTopicPath(final Acknowledgement ack) {
        return newTopicPathBuilder(ack, ack).acks().label(ack.getLabel()).build();
    }

    private static TopicPath getTopicPath(final Acknowledgements acks) {
        return newTopicPathBuilder(acks, acks).acks().aggregatedAcks().build();
    }

    private static TopicPathBuilder newTopicPathBuilder(final WithEntityId withEntityId,
            final WithDittoHeaders withDittoHeaders) {

        final var builder = ProtocolFactory.newTopicPathBuilder(ThingId.of(withEntityId.getEntityId()));
        if (Signal.isChannelLive(withDittoHeaders)) {
            builder.live();
        } else {
            builder.twin();
        }

        return builder;
    }

    @Nullable
    private ActorRef getReturnAddress(final ActorRef sender, final boolean isAcksRequesting, final Signal<?> signal) {
        final ActorRef result;

        // acks-requesting signals: all replies should be directed to the sender address (ack. aggregator actor)
        // other commands: set this actor as return address to receive any errors to publish.
        if (isAcksRequesting) {
            result = sender;
        } else {
            final var signalDittoHeaders = signal.getDittoHeaders();
            if (Command.isCommand(signal) && signalDittoHeaders.isResponseRequired()) {
                result = outboundMessageMappingProcessorActor;
            } else {
                result = ActorRef.noSender();
            }
        }

        return result;
    }

    private Signal<?> appendConnectionAcknowledgementsToSignal(final ExternalMessage message, final Signal<?> signal) {
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

        final var signalDittoHeaders = signal.getDittoHeaders();
        if (additionalAcknowledgementRequests.isEmpty() || explicitlyNoAcksRequested(signalDittoHeaders)) {
            // do not change the signal's header if no additional acknowledgementRequests are defined in the Source
            // to preserve the default behavior for signals without the header 'requested-acks'
            return RequestedAcksFilter.filterAcknowledgements(signal, message, filter, connection.getId());
        } else {
            // The Source's acknowledgementRequests get appended to the requested-acks DittoHeader of the mapped signal
            final var combinedRequestedAcks = signalDittoHeaders.getAcknowledgementRequests();
            combinedRequestedAcks.addAll(additionalAcknowledgementRequests);

            final Signal<?> signalWithCombinedAckRequests = signal.setDittoHeaders(signalDittoHeaders
                    .toBuilder()
                    .acknowledgementRequests(combinedRequestedAcks)
                    .build());

            return RequestedAcksFilter.filterAcknowledgements(signalWithCombinedAckRequests,
                    message,
                    filter,
                    connection.getId());
        }
    }

    private static boolean explicitlyNoAcksRequested(final DittoHeaders dittoHeaders) {
        return dittoHeaders.containsKey(DittoHeaderDefinition.REQUESTED_ACKS.getKey()) &&
                dittoHeaders.getAcknowledgementRequests().isEmpty();
    }

    /**
     * Helper applying the {@link org.eclipse.ditto.connectivity.model.EnforcementFilter} of the passed in
     * {@link org.eclipse.ditto.connectivity.api.ExternalMessage} by throwing a
     * {@link org.eclipse.ditto.connectivity.model.ConnectionSignalIdEnforcementFailedException} if the enforcement
     * failed.
     */
    private void applySignalIdEnforcement(final ExternalMessage externalMessage, final Signal<?> signal) {
        externalMessage.getEnforcementFilter().ifPresent(enforcementFilter -> {
            logger.withCorrelationId(signal)
                    .debug("Connection Signal ID Enforcement enabled - matching Signal <{}> with filter <{}>.",
                            signal, enforcementFilter);
            enforcementFilter.match(signal);
        });
    }

    /**
     * Helper applying the {@link org.eclipse.ditto.connectivity.model.HeaderMapping}.
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
                                    connection.getId());

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

        builder.putHeaders(extraInternalHeaders).origin(connection.getId());
        if (authorizationContext != null) {
            builder.authorizationContext(authorizationContext);
        }
        if (appendRandomCorrelationId && extraInternalHeaders.getCorrelationId().isEmpty()) {
            builder.randomCorrelationId();
        }

        return builder;
    }

    /**
     * Appends the ConnectionId to the processed {@code commandResponse} payload.
     *
     * @param commandResponse the CommandResponse (or Acknowledgement as subtype) to append the ConnectionId to
     * @param <T> the type of the CommandResponse
     * @return the CommandResponse with appended ConnectionId.
     */
    private <T extends CommandResponse<T>> T appendConnectionIdToAcknowledgementOrResponse(final T commandResponse) {
        final DittoHeaders newHeaders = commandResponse.getDittoHeaders()
                .toBuilder()
                .putHeader(DittoHeaderDefinition.CONNECTION_ID.getKey(), connection.getId().toString())
                .build();

        return commandResponse.setDittoHeaders(newHeaders);
    }

    private Acknowledgements appendConnectionIdToAcknowledgements(final Acknowledgements acknowledgements) {
        final List<Acknowledgement> acksList = acknowledgements.stream()
                .map(this::appendConnectionIdToAcknowledgementOrResponse)
                .toList();

        // Uses EntityId and StatusCode from input acknowledges expecting these were set when Acknowledgements was created
        return Acknowledgements.of(acknowledgements.getEntityId(), acksList, acknowledgements.getHttpStatus(),
                acknowledgements.getDittoHeaders());
    }

    private static Optional<AuthorizationContext> getAuthorizationContext(final ExternalMessage externalMessage) {
        final Either<RuntimeException, AuthorizationContext> result = getAuthorizationContextAsEither(externalMessage);
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

    private static <C extends DittoHeadersSettable<? extends C>> boolean isApplicable(
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
