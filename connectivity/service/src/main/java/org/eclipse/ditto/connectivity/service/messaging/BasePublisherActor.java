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
package org.eclipse.ditto.connectivity.service.messaging;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.base.model.headers.DittoHeaderDefinition.CORRELATION_ID;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.common.CharsetDeterminer;
import org.eclipse.ditto.base.model.common.Placeholders;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.events.streaming.StreamingSubscriptionEvent;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.OutboundSignal;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.GenericTarget;
import org.eclipse.ditto.connectivity.model.HeaderMapping;
import org.eclipse.ditto.connectivity.model.ReplyTarget;
import org.eclipse.ditto.connectivity.model.ResourceStatus;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.service.config.ConnectionConfig;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.MonitoringConfig;
import org.eclipse.ditto.connectivity.service.config.MonitoringLoggerConfig;
import org.eclipse.ditto.connectivity.service.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.connectivity.service.messaging.internal.RetrieveAddressStatus;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitorRegistry;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.DefaultConnectionMonitorRegistry;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.connectivity.service.messaging.validation.ConnectionValidator;
import org.eclipse.ditto.connectivity.service.placeholders.ConnectivityPlaceholders;
import org.eclipse.ditto.connectivity.service.util.ConnectivityMdcEntryKey;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.config.InstanceIdentifierSupplier;
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
import org.eclipse.ditto.internal.utils.tracing.span.SpanOperationName;
import org.eclipse.ditto.internal.utils.tracing.span.SpanTagKey;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.placeholders.ExpressionResolver;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapter;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionEvent;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.japi.pf.ReceiveBuilder;

/**
 * Base class for publisher actors. Holds the map of configured targets.
 *
 * @param <T> the type of targets for this actor
 */
public abstract class BasePublisherActor<T extends PublishTarget> extends AbstractActor {

    protected final Connection connection;
    protected final Map<Target, ResourceStatus> resourceStatusMap;

    protected final ConnectivityConfig connectivityConfig;
    protected final ConnectionConfig connectionConfig;
    protected final ConnectionLogger connectionLogger;
    protected final ConnectivityStatusResolver connectivityStatusResolver;
    protected final ExpressionResolver connectionIdResolver;

    /**
     * Common logger for all sub-classes of BasePublisherActor as its MDC already contains the connection ID.
     */
    protected final ThreadSafeDittoLoggingAdapter logger;

    private final ConnectionMonitor responseDroppedMonitor;
    private final ConnectionMonitor responsePublishedMonitor;
    private final ConnectionMonitor responseAcknowledgedMonitor;
    private final ConnectionMonitorRegistry<ConnectionMonitor> connectionMonitorRegistry;
    private final List<Optional<ReplyTarget>> replyTargets;
    private final int acknowledgementSizeBudget;

    protected BasePublisherActor(final Connection connection,
            final ConnectivityStatusResolver connectivityStatusResolver,
            final ConnectivityConfig connectivityConfig) {

        this.connection = checkNotNull(connection, "connection");
        resourceStatusMap = new HashMap<>();
        final List<Target> targets = connection.getTargets();
        targets.forEach(target -> resourceStatusMap.put(target, getTargetResourceStatus(target)));
        this.connectivityConfig = connectivityConfig;
        connectionConfig = connectivityConfig.getConnectionConfig();
        final MonitoringConfig monitoringConfig = connectivityConfig.getMonitoringConfig();
        final MonitoringLoggerConfig loggerConfig = monitoringConfig.logger();
        connectionLogger = ConnectionLogger.getInstance(connection.getId(), loggerConfig);
        this.connectivityStatusResolver = checkNotNull(connectivityStatusResolver, "connectivityStatusResolver");
        connectionMonitorRegistry = DefaultConnectionMonitorRegistry.fromConfig(connectivityConfig);
        responseDroppedMonitor = connectionMonitorRegistry.forResponseDropped(connection);
        responsePublishedMonitor = connectionMonitorRegistry.forResponsePublished(connection);
        responseAcknowledgedMonitor = connectionMonitorRegistry.forResponseAcknowledged(connection);
        replyTargets = connection.getSources().stream().map(Source::getReplyTarget).toList();
        acknowledgementSizeBudget = connectionConfig.getAcknowledgementConfig().getIssuedMaxBytes();
        logger = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this)
                .withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID, connection.getId());

        connectionIdResolver = PlaceholderFactory.newExpressionResolver(
                ConnectivityPlaceholders.newConnectionIdPlaceholder(),
                connection.getId());
    }

    @Override
    public Receive createReceive() {
        final ReceiveBuilder receiveBuilder = receiveBuilder();
        preEnhancement(receiveBuilder);

        receiveBuilder.match(OutboundSignal.MultiMapped.class, this::sendMultiMappedOutboundSignal)
                .match(RetrieveAddressStatus.class, ram -> getCurrentTargetStatus().forEach(rs ->
                        getSender().tell(rs, getSelf())));

        postEnhancement(receiveBuilder);
        return receiveBuilder.matchAny(m -> {
            logger.warning("Unknown message: {}", m);
            unhandled(m);
        }).build();
    }

    private Collection<ResourceStatus> getCurrentTargetStatus() {
        return resourceStatusMap.values();
    }

    private void sendMultiMappedOutboundSignal(final OutboundSignal.MultiMapped multiMapped) {
        final int quota = computeMaxAckPayloadBytesForSignal(multiMapped);
        final ExceptionToAcknowledgementConverter errorConverter = getExceptionConverter();
        final List<OutboundSignal.Mapped> mappedOutboundSignals = multiMapped.getMappedOutboundSignals();
        final CompletableFuture<CommandResponse<?>>[] sendMonitorAndResponseFutures = mappedOutboundSignals.stream()
                // message sending step
                .flatMap(outbound -> sendMappedOutboundSignal(outbound, quota))
                // monitor and acknowledge step
                .flatMap(sendingOrDropped -> sendingOrDropped.monitorAndAcknowledge(errorConverter).stream())
                // convert to completable future array for aggregation
                .map(CompletionStage::toCompletableFuture)
                .<CompletableFuture<CommandResponse<?>>>toArray(CompletableFuture[]::new);

        aggregateNonNullFutures(sendMonitorAndResponseFutures)
                .thenAccept(responsesList -> {
                    final ActorRef sender = multiMapped.getSender().orElse(null);

                    final Collection<Acknowledgement> acknowledgements = new ArrayList<>();
                    final Collection<CommandResponse<?>> nonAcknowledgements = new ArrayList<>();
                    responsesList.forEach(response -> {
                        if (response instanceof Acknowledgement acknowledgement) {
                            acknowledgements.add(acknowledgement);
                        } else {
                            nonAcknowledgements.add(response);
                        }
                    });

                    issueAcknowledgements(multiMapped, acknowledgements);
                    sendBackResponses(multiMapped, sender, nonAcknowledgements);
                })
                .exceptionally(e -> {
                    logger.withCorrelationId(multiMapped.getSource())
                            .error(e, "Message sending failed unexpectedly: <{}>", multiMapped);

                    return null;
                });
    }

    private void issueAcknowledgements(final OutboundSignal.MultiMapped multiMapped,
            final Collection<Acknowledgement> ackList) {

        final ThreadSafeDittoLoggingAdapter l = logger.withCorrelationId(multiMapped.getSource());
        if (!ackList.isEmpty()) {
            final DittoHeaders sourceSignalHeaders = multiMapped.getSource().getDittoHeaders();
            final Acknowledgements aggregatedAcks = appendConnectionId(
                    Acknowledgements.of(ackList, sourceSignalHeaders));

            final String ackregatorAddress = aggregatedAcks.getDittoHeaders()
                    .get(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey());
            if (null != ackregatorAddress) {
                final ActorSelection acknowledgementRequester = getContext().actorSelection(ackregatorAddress);
                l.debug("Message sent. Replying to <{}>: <{}>", acknowledgementRequester, aggregatedAcks);
                acknowledgementRequester.tell(aggregatedAcks, ActorRef.noSender());
            } else if (sourceSignalHeaders.getAcknowledgementRequests().isEmpty()) {
                l.info("Aggregated Acknowledgements did not contain header of acknowledgement aggregator, but " +
                        "ignoring ackList to issue as the source signal did not request any ACKs in its " +
                        "headers: <{}> - source signal headers: <{}>", aggregatedAcks, sourceSignalHeaders);
            } else {
                // only log the error if the originating signal even did request acks in the first place
                // the ackList could be a result of an error automatically converted to a negative ACK
                l.error("Aggregated Acknowledgements did not contain header of acknowledgement aggregator " +
                        "address: <{}> - source signal headers: {}", aggregatedAcks, sourceSignalHeaders);
            }
        } else {
            l.debug("Message sent: No acks requested.");
        }
    }

    private void sendBackResponses(final OutboundSignal.MultiMapped multiMapped, @Nullable final ActorRef sender,
            final Collection<CommandResponse<?>> nonAcknowledgementsResponses) {

        final ThreadSafeDittoLoggingAdapter l = logger.withCorrelationId(multiMapped.getSource());
        if (!nonAcknowledgementsResponses.isEmpty() && sender != null) {
            nonAcknowledgementsResponses.forEach(response -> {
                l.debug("CommandResponse created from HTTP response. Replying to <{}>: <{}>", sender,
                        response);
                sender.tell(response, getSelf());
            });
        } else if (nonAcknowledgementsResponses.isEmpty()) {
            l.debug("No CommandResponse created from HTTP response.");
        } else {
            l.error("CommandResponse created from HTTP response, but no sender: <{}>", multiMapped.getSource());
        }
    }

    /**
     * Gets the converter from publisher exceptions to Acknowledgements.
     * Override to handle client-specific exceptions.
     *
     * @return the converter.
     */
    protected ExceptionToAcknowledgementConverter getExceptionConverter() {
        return DefaultExceptionToAcknowledgementConverter.getInstance();
    }

    private static <T> CompletionStage<List<T>> aggregateNonNullFutures(final CompletableFuture<T>[] futures) {
        return CompletableFuture.allOf(futures)
                .thenApply(aVoid -> Arrays.stream(futures)
                        .map(CompletableFuture::join)
                        .filter(Objects::nonNull)
                        .toList());
    }

    private Acknowledgements appendConnectionId(final Acknowledgements acknowledgements) {
        return appendConnectionIdToAcknowledgements(acknowledgements, connection.getId());
    }

    private static Acknowledgements appendConnectionIdToAcknowledgements(final Acknowledgements acknowledgements,
            final ConnectionId connectionId) {
        final List<Acknowledgement> acksList = acknowledgements.stream()
                .map(ack -> appendConnectionIdToAcknowledgementOrResponse(ack, connectionId))
                .toList();
        // Uses EntityId and StatusCode from input acknowledges expecting these were set when Acknowledgements was created
        return Acknowledgements.of(acknowledgements.getEntityId(), acksList, acknowledgements.getHttpStatus(),
                acknowledgements.getDittoHeaders());
    }

    /**
     * Appends the ConnectionId to the processed {@code commandResponse} payload.
     *
     * @param commandResponse the CommandResponse (or Acknowledgement as subtype) to append the ConnectionId to
     * @param connectionId the ConnectionId to append to the CommandResponse's DittoHeader
     * @param <T> the type of the CommandResponse
     * @return the CommandResponse with appended ConnectionId.
     */
    private static <T extends CommandResponse<T>> T appendConnectionIdToAcknowledgementOrResponse(
            final T commandResponse,
            final ConnectionId connectionId) {

        final DittoHeaders newHeaders = commandResponse.getDittoHeaders()
                .toBuilder()
                .putHeader(DittoHeaderDefinition.CONNECTION_ID.getKey(), connectionId.toString())
                .build();
        return commandResponse.setDittoHeaders(newHeaders);
    }

    private int computeMaxAckPayloadBytesForSignal(final OutboundSignal.MultiMapped multiMapped) {
        final List<OutboundSignal.Mapped> mappedOutboundSignals = multiMapped.getMappedOutboundSignals();
        final int numberOfSignals = mappedOutboundSignals.size();
        return 0 == numberOfSignals ? acknowledgementSizeBudget : acknowledgementSizeBudget / numberOfSignals;
    }

    private Stream<SendingOrDropped> sendMappedOutboundSignal(final OutboundSignal.Mapped outbound,
            final int maxPayloadBytesForSignal) {

        final var message = outbound.getExternalMessage();
        final String correlationId = message.getHeaders().get(CORRELATION_ID.getKey());
        final Signal<?> outboundSource = outbound.getSource();
        final List<Target> outboundTargets = outbound.getTargets();

        final ThreadSafeDittoLoggingAdapter l = logger.withCorrelationId(correlationId);
        final Optional<SendingContext> replyTargetSendingContext = getSendingContext(outbound);

        final List<SendingContext> sendingContexts = replyTargetSendingContext
                .map(sendingContext -> {
                    l.debug("Publishing mapped message of type <{}> to replyTarget <{}>",
                            outboundSource.getType(), sendingContext.getGenericTarget().getAddress());
                    return List.of(sendingContext);
                })
                .orElseGet(() -> outboundTargets.stream()
                        .map(target -> {
                            l.debug("Publishing mapped message of type <{}> to targets <{}>", outboundSource.getType(),
                                    outboundTargets);
                            return getSendingContextForTarget(outbound, target);
                        })
                        .toList());


        if (sendingContexts.isEmpty()) {
            // Message dropped: neither reply-target nor target is available for the message.
            if (l.isDebugEnabled()) {
                l.debug("Message without GenericTarget dropped: <{}>", outbound.getExternalMessage());
            } else {
                l.info("Message without GenericTarget dropped.");
            }
            return Stream.empty();
        } else {
            // message not dropped
            final ExpressionResolver resolver = Resolvers.forOutbound(outbound, connection.getId());
            final int acks = (int) sendingContexts.stream().filter(SendingContext::shouldAcknowledge).count();
            final int maxPayloadBytes = acks == 0 ? maxPayloadBytesForSignal : maxPayloadBytesForSignal / acks;
            return sendingContexts.stream()
                    .map(sendingContext -> tryToPublishToGenericTarget(resolver, sendingContext,
                            maxPayloadBytesForSignal, maxPayloadBytes, l));
        }
    }

    private Optional<SendingContext> getSendingContext(final OutboundSignal.Mapped mappedOutboundSignal) {
        final Optional<SendingContext> result;
        if (isResponseOrErrorOrStreamingEvent(mappedOutboundSignal)) {
            final Signal<?> source = mappedOutboundSignal.getSource();
            final DittoHeaders dittoHeaders = source.getDittoHeaders();
            result = dittoHeaders.getReplyTarget()
                    .flatMap(this::getReplyTargetByIndex)
                    .map(replyTarget -> getSendingContextForReplyTarget(mappedOutboundSignal, replyTarget));
        } else {
            result = Optional.empty();
        }
        return result;
    }

    /**
     * Checks whether the passed in {@code outboundSignal} is a response or an error or a streaming event
     * (including search events).
     * Those messages are supposed to be published at the reply target of the source whence the original command came.
     *
     * @param outboundSignal the OutboundSignal to check.
     * @return {@code true} if the OutboundSignal is a response or an error, {@code false} otherwise
     */
    private static boolean isResponseOrErrorOrStreamingEvent(final OutboundSignal.Mapped outboundSignal) {
        final ExternalMessage externalMessage = outboundSignal.getExternalMessage();
        return externalMessage.isResponse() ||
                externalMessage.isError() ||
                outboundSignal.getSource() instanceof SubscriptionEvent ||
                outboundSignal.getSource() instanceof StreamingSubscriptionEvent<?>;
    }

    private Optional<ReplyTarget> getReplyTargetByIndex(final int replyTargetIndex) {
        return 0 <= replyTargetIndex && replyTargetIndex < replyTargets.size()
                ? replyTargets.get(replyTargetIndex)
                : Optional.empty();
    }

    private SendingContext getSendingContextForReplyTarget(final OutboundSignal.Mapped outboundSignal,
            final ReplyTarget replyTarget) {

        return SendingContext.newBuilder()
                .mappedOutboundSignal(outboundSignal)
                .externalMessage(outboundSignal.getExternalMessage())
                .genericTarget(replyTarget)
                .droppedMonitor(responseDroppedMonitor)
                .publishedMonitor(responsePublishedMonitor)
                .acknowledgedMonitor(responseAcknowledgedMonitor)
                .build();
    }

    private SendingContext getSendingContextForTarget(final OutboundSignal.Mapped outboundSignal, final Target target) {
        final String originalAddress = target.getOriginalAddress();
        final ConnectionMonitor publishedMonitor =
                connectionMonitorRegistry.forOutboundPublished(connection, originalAddress);

        final ConnectionMonitor droppedMonitor =
                connectionMonitorRegistry.forOutboundDropped(connection, originalAddress);

        final boolean targetAckRequested = isTargetAckRequested(outboundSignal, target);

        @Nullable final ConnectionMonitor acknowledgedMonitor = targetAckRequested
                ? connectionMonitorRegistry.forOutboundAcknowledged(connection, originalAddress)
                : null;
        @Nullable final Target autoAckTarget = targetAckRequested ? target : null;

        return SendingContext.newBuilder()
                .mappedOutboundSignal(outboundSignal)
                .externalMessage(outboundSignal.getExternalMessage())
                .genericTarget(target)
                .publishedMonitor(publishedMonitor)
                .droppedMonitor(droppedMonitor)
                .acknowledgedMonitor(acknowledgedMonitor)
                .autoAckTarget(autoAckTarget)
                .targetAuthorizationContext(target.getAuthorizationContext())
                .build();
    }

    private SendingOrDropped tryToPublishToGenericTarget(final ExpressionResolver resolver,
            final SendingContext sendingContext,
            final int maxTotalMessageSize,
            final int quota,
            final ThreadSafeDittoLoggingAdapter logger) {

        try {
            return publishToGenericTarget(resolver, sendingContext, maxTotalMessageSize, quota, logger);
        } catch (final Exception e) {
            return new Sending(sendingContext, CompletableFuture.failedFuture(e), connectionIdResolver, logger);
        }
    }

    private SendingOrDropped publishToGenericTarget(final ExpressionResolver resolver,
            final SendingContext sendingContext,
            final int maxTotalMessageSize,
            final int quota,
            final ThreadSafeDittoLoggingAdapter logger) {

        final OutboundSignal.Mapped outbound = sendingContext.getMappedOutboundSignal();
        final GenericTarget genericTarget = sendingContext.getGenericTarget();
        final String address = genericTarget.getAddress();
        final Optional<T> publishTargetOptional = resolveTargetAddress(resolver, address)
                .map(genericTarget::withAddress)
                .map(this::toPublishTarget);
        final Signal<?> outboundSource = outbound.getSource();
        final ThreadSafeDittoLoggingAdapter l = logger.withCorrelationId(outboundSource);

        final SendingOrDropped result;
        if (publishTargetOptional.isPresent()) {
            final T publishTarget = publishTargetOptional.get();
            final Object entityId = outboundSource instanceof WithEntityId wEntityId ? wEntityId.getEntityId() : "?";
            l.info("Publishing mapped message of type <{}> for id <{}> to PublishTarget <{}>",
                    outboundSource.getType(), entityId, publishTarget);
            l.debug("Publishing mapped message of type <{}> for id <{}> to PublishTarget <{}>: {}",
                    outboundSource.getType(),
                    entityId,
                    publishTarget,
                    sendingContext.getExternalMessage());
            @Nullable final Target autoAckTarget = sendingContext.getAutoAckTarget().orElse(null);

            final HeaderMapping headerMapping = genericTarget.getHeaderMapping();
            final ExternalMessage mappedMessage = applyHeaderMapping(resolver, outbound, headerMapping);
            final var startedSpan = DittoTracing.newPreparedSpan(
                            mappedMessage.getHeaders(),
                            SpanOperationName.of(connection.getConnectionType() + "_publish")
                    )
                    .connectionId(connection.getId())
                    .tag(SpanTagKey.CONNECTION_TYPE.getTagForValue(connection.getConnectionType()))
                    .start();
            final var mappedMessageWithTraceContext =
                    mappedMessage.withHeaders(startedSpan.propagateContext(mappedMessage.getHeaders()));

            final CompletionStage<SendResult> responsesFuture = publishMessage(outboundSource,
                    autoAckTarget,
                    publishTarget,
                    mappedMessageWithTraceContext,
                    maxTotalMessageSize,
                    quota,
                    sendingContext.getTargetAuthorizationContext().orElse(null)
            );
            responsesFuture.whenComplete((sr, throwable) -> startedSpan.finish());
            // set the external message after header mapping for the result of header mapping to show up in log
            result = new Sending(sendingContext.setExternalMessage(mappedMessageWithTraceContext), responsesFuture,
                    connectionIdResolver, l);
        } else {
            l.debug("Signal dropped, target address unresolved: <{}>", address);
            result = new Dropped(sendingContext, "Signal dropped, target address unresolved: {0}");
        }
        return result;
    }

    private static ExternalMessage applyHeaderMapping(final ExpressionResolver expressionResolver,
            final OutboundSignal.Mapped outboundSignal, @Nullable final HeaderMapping headerMapping) {

        final OutboundSignalToExternalMessage outboundSignalToExternalMessage =
                OutboundSignalToExternalMessage.newInstance(outboundSignal, expressionResolver, headerMapping);

        return outboundSignalToExternalMessage.get();
    }

    /**
     * Provides the possibility to add custom matchers before applying the default matchers of the BasePublisherActor.
     *
     * @param receiveBuilder the ReceiveBuilder to add other matchers to.
     */
    protected abstract void preEnhancement(ReceiveBuilder receiveBuilder);

    /**
     * Provides the possibility to add custom matchers after applying the default matchers of the BasePublisherActor.
     *
     * @param receiveBuilder the ReceiveBuilder to add other matchers to.
     */
    protected abstract void postEnhancement(ReceiveBuilder receiveBuilder);

    /**
     * Converts the passed {@code address} to a {@link PublishTarget} of type {@code <T>}.
     *
     * @param target the address to convert to a {@link PublishTarget} of type {@code <T>}.
     * @return the instance of type {@code <T>}
     */
    protected abstract T toPublishTarget(GenericTarget target);

    /**
     * Publish a message. Construct the acknowledgement regardless of any request for diagnostic purposes.
     *
     * @param signal the nullable Target for getting even more information about the configured Target to publish to.
     * @param autoAckTarget if set, this is the Target from which {@code Acknowledgement}s should automatically be
     * produced and delivered.
     * @param publishTarget the {@link PublishTarget} to publish to.
     * @param message the {@link org.eclipse.ditto.connectivity.api.ExternalMessage} to publish.
     * @param maxTotalMessageSize the total max message size in bytes of the payload of an automatically created
     * response.
     * @param ackSizeQuota budget in bytes for how large the payload of this acknowledgement can be, or 0 to not
     * send any acknowledgement.
     * @return future of the send result holding a command responses (acknowledgements and potentially responses to
     * live commands / live messages) to reply to sender, or future of null if ackSizeQuota is 0.
     */
    protected abstract CompletionStage<SendResult> publishMessage(Signal<?> signal,
            @Nullable Target autoAckTarget,
            T publishTarget,
            ExternalMessage message,
            int maxTotalMessageSize,
            int ackSizeQuota,
            @Nullable AuthorizationContext targetAuthorizationContext);

    /**
     * Decode a byte buffer according to the charset specified in an external message.
     *
     * @param buffer the byte buffer.
     * @param message the external message.
     * @return the decoded string.
     */
    protected static String decodeAsHumanReadable(@Nullable final ByteBuffer buffer, final ExternalMessage message) {
        if (buffer != null) {
            return getCharsetFromMessage(message).decode(buffer).toString();
        } else {
            return "<empty>";
        }
    }

    /**
     * Determine charset from an external message.
     *
     * @param message the external message.
     * @return its charset.
     */
    protected static Charset getCharsetFromMessage(final ExternalMessage message) {
        return message.findContentType()
                .map(BasePublisherActor::determineCharset)
                .orElse(StandardCharsets.UTF_8);
    }

    /**
     * Escalate an error to the parent to trigger a restart.
     * This is thread safe because self and parent are thread-safe.
     *
     * @param error the encountered failure.
     * @param description description of the failure.
     */
    protected void escalate(final Throwable error, final String description) {
        final ConnectionFailure failure = ConnectionFailure.of(getSelf(), error, description);
        getContext().getParent().tell(failure, getSelf());
    }

    /**
     * Extract acknowledgement label from an auto-ack target.
     *
     * @param target the target.
     * @return the configured auto-ack label if any exists, or an empty optional.
     */
    protected Optional<AcknowledgementLabel> getAcknowledgementLabel(@Nullable final Target target) {
        return Optional.ofNullable(target)
                .flatMap(Target::getIssuedAcknowledgementLabel)
                .flatMap(ackLbl -> ConnectionValidator.resolveConnectionIdPlaceholder(connectionIdResolver, ackLbl));
    }

    /**
     * Resolve target address.
     * If not resolvable, the returned Optional will be empty.
     */
    private static Optional<String> resolveTargetAddress(final ExpressionResolver resolver, final String value) {
        return resolver.resolve(value).findFirst();
    }

    private static Charset determineCharset(final CharSequence contentType) {
        return CharsetDeterminer.getInstance().apply(contentType);
    }

    private boolean isTargetAckRequested(final OutboundSignal.Mapped mapped, final Target target) {
        final Signal<?> source = mapped.getSource();
        final DittoHeaders dittoHeaders = source.getDittoHeaders();
        final Set<AcknowledgementRequest> acknowledgementRequests = dittoHeaders.getAcknowledgementRequests();
        final Set<AcknowledgementLabel> requestedAcks = acknowledgementRequests.stream()
                .map(AcknowledgementRequest::getLabel)
                .collect(Collectors.toSet());

        if (target.getIssuedAcknowledgementLabel()
                .filter(DittoAcknowledgementLabel.LIVE_RESPONSE::equals)
                .isPresent()) {
            return dittoHeaders.isResponseRequired() && isLiveSignal(source);
        } else {
            return target.getIssuedAcknowledgementLabel()
                    .flatMap(ackLabel -> ConnectionValidator.resolveConnectionIdPlaceholder(connectionIdResolver,
                            ackLabel))
                    .filter(requestedAcks::contains)
                    .isPresent();
        }
    }

    private static boolean isLiveSignal(final Signal<?> signal) {
        return signal instanceof MessageCommand ||
                signal instanceof ThingCommand && ProtocolAdapter.isLiveSignal(signal);
    }

    private static ResourceStatus getTargetResourceStatus(final Target target) {
        if (Placeholders.containsAnyPlaceholder(target.getAddress())) {
            // we cannot determine the actual status of targets with placeholders, they are created on-demand
            return ConnectivityModelFactory.newTargetStatus(InstanceIdentifierSupplier.getInstance().get(),
                    ConnectivityStatus.UNKNOWN, target.getAddress(),
                    "Producers for targets with placeholders are started on-demand.", Instant.now());
        } else {
            // for most connection types publishing to a target works without further initialization, hence the
            // default is OPEN, which can be overwritten in the implementations
            return ConnectivityModelFactory.newTargetStatus(InstanceIdentifierSupplier.getInstance().get(),
                    ConnectivityStatus.OPEN, target.getAddress(), "Producer started.",
                    Instant.now());
        }
    }

}
