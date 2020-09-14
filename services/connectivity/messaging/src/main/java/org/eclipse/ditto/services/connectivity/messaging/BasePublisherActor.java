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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.model.base.headers.DittoHeaderDefinition.CORRELATION_ID;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.common.CharsetDeterminer;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.entity.id.EntityIdWithType;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.GenericTarget;
import org.eclipse.ditto.model.connectivity.HeaderMapping;
import org.eclipse.ditto.model.connectivity.MessageSendingFailedException;
import org.eclipse.ditto.model.connectivity.ReplyTarget;
import org.eclipse.ditto.model.connectivity.ResourceStatus;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.placeholders.ExpressionResolver;
import org.eclipse.ditto.model.placeholders.PlaceholderFilter;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.connectivity.messaging.config.ConnectionConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.ConnectivityConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.DittoConnectivityConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.MonitoringConfig;
import org.eclipse.ditto.services.connectivity.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.internal.ImmutableConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.internal.RetrieveAddressStatus;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitorRegistry;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.DefaultConnectionMonitorRegistry;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.ConnectionLoggerRegistry;
import org.eclipse.ditto.services.connectivity.util.ConnectionLogUtil;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageBuilder;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.config.InstanceIdentifierSupplier;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.acks.base.Acknowledgements;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponse;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionEvent;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.DiagnosticLoggingAdapter;
import akka.event.LoggingAdapter;
import akka.japi.Pair;
import akka.japi.pf.ReceiveBuilder;

/**
 * Base class for publisher actors. Holds the map of configured targets.
 *
 * @param <T> the type of targets for this actor
 */
public abstract class BasePublisherActor<T extends PublishTarget> extends AbstractActor {

    private static final MessageSendingFailedException NULL_ACK_EXCEPTION = MessageSendingFailedException.newBuilder()
            .message("Message sending terminated without the expected acknowledgement.")
            .description("Please contact the service team.")
            .build();

    protected final Connection connection;
    protected final ConnectionId connectionId;
    protected final List<Target> targets;
    protected final Map<Target, ResourceStatus> resourceStatusMap;

    protected final ConnectionConfig connectionConfig;
    protected final ConnectionLogger connectionLogger;
    protected final ConnectionMonitor responsePublishedMonitor;
    protected final ConnectionMonitor responseDroppedMonitor;
    private final ConnectionMonitorRegistry<ConnectionMonitor> connectionMonitorRegistry;
    private final List<Optional<ReplyTarget>> replyTargets;
    private final int acknowledgementSizeBudget;

    protected BasePublisherActor(final Connection connection) {
        checkNotNull(connection, "connection");
        this.connection = connection;
        connectionId = connection.getId();
        targets = connection.getTargets();
        resourceStatusMap = new HashMap<>();
        final Instant now = Instant.now();
        targets.forEach(target ->
                resourceStatusMap.put(target,
                        ConnectivityModelFactory.newTargetStatus(getInstanceIdentifier(), ConnectivityStatus.OPEN,
                                target.getAddress(), "Started at " + now)));

        final ConnectivityConfig connectivityConfig = DittoConnectivityConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config())
        );
        final MonitoringConfig monitoringConfig = connectivityConfig.getMonitoringConfig();
        connectionConfig = connectivityConfig.getConnectionConfig();
        connectionMonitorRegistry = DefaultConnectionMonitorRegistry.fromConfig(monitoringConfig);
        responseDroppedMonitor = connectionMonitorRegistry.forResponseDropped(connectionId);
        responsePublishedMonitor = connectionMonitorRegistry.forResponsePublished(connectionId);
        connectionLogger =
                ConnectionLoggerRegistry.fromConfig(monitoringConfig.logger()).forConnection(connectionId);
        replyTargets = connection.getSources().stream().map(Source::getReplyTarget).collect(Collectors.toList());
        acknowledgementSizeBudget = connectionConfig.getAcknowledgementConfig().getIssuedMaxBytes();
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
            log().warning("Unknown message: {}", m);
            unhandled(m);
        }).build();
    }

    /**
     * Get the  from publisher errors to Acknowledgements.
     * Override to handle client-specific exceptions.
     *
     * @return the converter.
     */
    protected ErrorConverter getErrorConverter() {
        return ErrorConverter.DEFAULT_INSTANCE;
    }

    private void sendMultiMappedOutboundSignal(final OutboundSignal.MultiMapped multiMapped) {
        final int quota = computeMaxAckPayloadBytesForSignal(multiMapped);
        final ErrorConverter errorConverter = getErrorConverter();
        final CompletableFuture<CommandResponseOrAcknowledgement>[] sendMonitorAndResponseFutures =
                multiMapped.getMappedOutboundSignals()
                        .stream()
                        // message sending step
                        .flatMap(outbound -> sendMappedOutboundSignal(outbound, quota))
                        // monitor and acknowledge step
                        .flatMap(sendingOrDropped -> sendingOrDropped.monitorAndAcknowledge(errorConverter)
                                .stream())
                        // convert to completable future array for aggregation
                        .map(CompletionStage::toCompletableFuture)
                        .<CompletableFuture<CommandResponseOrAcknowledgement>>toArray(CompletableFuture[]::new);

        aggregateNonNullFutures(sendMonitorAndResponseFutures)
                .thenAccept(responsesList -> {
                    final ActorRef sender = multiMapped.getSender().orElse(null);

                    final List<Acknowledgement> ackList = responsesList.stream()
                            .map(CommandResponseOrAcknowledgement::getAcknowledgement)
                            .flatMap(Optional::stream)
                            .collect(Collectors.toList());
                    issueAcknowledgements(multiMapped, sender, ackList);

                    final List<CommandResponse<?>> nonAcknowledgementsResponses = responsesList.stream()
                            .map(CommandResponseOrAcknowledgement::getCommandResponse)
                            .flatMap(Optional::stream)
                            .collect(Collectors.toList());
                    sendBackResponses(multiMapped, sender, nonAcknowledgementsResponses);
                })
                .exceptionally(e -> {
                    log().withCorrelationId(multiMapped.getSource())
                            .error(e, "Message sending failed unexpectedly: <{}>", multiMapped);
                    return null;
                });
    }

    private void issueAcknowledgements(final OutboundSignal.MultiMapped multiMapped, @Nullable final ActorRef sender,
            final List<Acknowledgement> ackList) {
        if (!ackList.isEmpty() && sender != null) {
            final Acknowledgements aggregatedAcks = appendConnectionId(
                    Acknowledgements.of(ackList, multiMapped.getSource().getDittoHeaders()));
            logIfDebug(multiMapped, l ->
                    l.debug("Message sent. Replying to <{}>: <{}>", sender, aggregatedAcks));
            sender.tell(aggregatedAcks, getSelf());
        } else if (ackList.isEmpty()) {
            logIfDebug(multiMapped, l -> l.debug("Message sent: No acks requested."));
        } else {
            log().withCorrelationId(multiMapped.getSource())
                    .error("Message sent: Acks requested, but no sender: <{}>", multiMapped.getSource());
        }
    }

    private void sendBackResponses(final OutboundSignal.MultiMapped multiMapped, @Nullable final ActorRef sender,
            final List<CommandResponse<?>> nonAcknowledgementsResponses) {
        if (!nonAcknowledgementsResponses.isEmpty() && sender != null) {
            nonAcknowledgementsResponses.forEach(response -> {
                logIfDebug(multiMapped, l ->
                        l.debug("CommandResponse created from HTTP response. Replying to <{}>: <{}>", sender,
                                response));
                sender.tell(response, getSelf());
            });
        } else if (nonAcknowledgementsResponses.isEmpty()) {
            logIfDebug(multiMapped, l -> l.debug("No CommandResponse created from HTTP response."));
        } else {
            log().withCorrelationId(multiMapped.getSource())
                    .error("CommandResponse created from HTTP response, but no sender: <{}>", multiMapped.getSource());
        }
    }

    private Acknowledgements appendConnectionId(final Acknowledgements acknowledgements) {
        return MessageMappingProcessorActor.appendConnectionIdToAcknowledgements(acknowledgements, connectionId);
    }

    private void logIfDebug(final OutboundSignal.MultiMapped multiMapped, final Consumer<LoggingAdapter> whatToLog) {
        if (log().isDebugEnabled()) {
            whatToLog.accept(log().withCorrelationId(multiMapped.getSource()));
        }
    }

    private int computeMaxAckPayloadBytesForSignal(final OutboundSignal.MultiMapped multiMapped) {
        final int numberOfSignals = multiMapped.getMappedOutboundSignals().size();
        return numberOfSignals == 0 ? acknowledgementSizeBudget : acknowledgementSizeBudget / numberOfSignals;
    }

    private Stream<SendingOrDropped> sendMappedOutboundSignal(final OutboundSignal.Mapped outbound,
            final int maxPayloadBytesForSignal) {

        final ExternalMessage message = outbound.getExternalMessage();
        final String correlationId = message.getHeaders().get(CORRELATION_ID.getKey());
        ConnectionLogUtil.enhanceLogWithCorrelationIdAndConnectionId(log(), correlationId, connectionId);

        final Signal<?> outboundSource = outbound.getSource();
        log().debug("Publishing mapped message of type <{}> to targets <{}>: {}",
                outboundSource.getType(), outbound.getTargets(), message);

        final Optional<SendingContext> replyTargetSendingContext = outbound.getSource()
                .getDittoHeaders()
                .getReplyTarget()
                .filter(i -> isResponseOrErrorOrSearchEvent(outbound))
                .flatMap(this::getReplyTargetByIndex)
                .map(replyTarget -> sendingContextForReplyTarget(outbound, replyTarget));

        final List<SendingContext> sendingContexts = replyTargetSendingContext.map(List::of)
                .orElseGet(() -> outbound.getTargets()
                        .stream()
                        .map(target -> sendingContextForTarget(outbound, target))
                        .collect(Collectors.toList())
                );

        if (sendingContexts.isEmpty()) {
            // message dropped
            logDroppedMessage(outbound);
            return Stream.empty();
        } else {
            // message not dropped
            final ExpressionResolver resolver = Resolvers.forOutbound(outbound);
            final int acks = (int) sendingContexts.stream().filter(SendingContext::shouldAcknowledge).count();
            final int maxPayloadBytes = acks == 0 ? maxPayloadBytesForSignal : maxPayloadBytesForSignal / acks;
            return sendingContexts.stream()
                    .map(sendingContext -> publishToGenericTarget(resolver, sendingContext, maxPayloadBytesForSignal,
                            maxPayloadBytes));
        }
    }

    /**
     * Log a dropped message. Call only if neither reply-target nor target is available for the message.
     *
     * @param outbound the outbound signal
     */
    private void logDroppedMessage(final OutboundSignal.Mapped outbound) {
        if (log().isDebugEnabled()) {
            log().withCorrelationId(outbound.getSource())
                    .debug("Message without GenericTarget dropped: <{}>", outbound.getExternalMessage());
        } else {
            log().withCorrelationId(outbound.getSource())
                    .info("Message without GenericTarget dropped.");
        }
    }

    private SendingOrDropped publishToGenericTarget(
            final ExpressionResolver resolver,
            final SendingContext sendingContext,
            final int maxTotalMessageSize,
            final int quota) {
        try {
            final OutboundSignal.Mapped outbound = sendingContext.outboundSignal;
            final GenericTarget genericTarget = sendingContext.genericTarget;
            final String address = genericTarget.getAddress();
            final Optional<T> publishTargetOptional =
                    resolveTargetAddress(resolver, address).map(this::toPublishTarget);
            if (publishTargetOptional.isPresent()) {
                log().debug("Publishing mapped message of type <{}> to address <{}>: {}",
                        outbound.getSource().getType(), address, sendingContext.externalMessage);
                final T publishTarget = publishTargetOptional.get();
                @Nullable final Target autoAckTarget = sendingContext.autoAckTarget;
                final HeaderMapping headerMapping = genericTarget.getHeaderMapping().orElse(null);
                final ExternalMessage mappedMessage = applyHeaderMapping(resolver, outbound, headerMapping, log());
                final CompletionStage<CommandResponseOrAcknowledgement> responsesFuture =
                        publishMessage(outbound.getSource(), autoAckTarget, publishTarget, mappedMessage,
                                maxTotalMessageSize, quota);
                // set the external message after header mapping for the result of header mapping to show up in log
                return new Sending(sendingContext.setExternalMessage(mappedMessage), responsesFuture, log());
            } else {
                return new Dropped(sendingContext, log());
            }
        } catch (final Exception e) {
            return new Sending(sendingContext, CompletableFuture.failedFuture(e), log());
        }
    }

    private Optional<ReplyTarget> getReplyTargetByIndex(final int replyTargetIndex) {
        return 0 <= replyTargetIndex && replyTargetIndex < replyTargets.size()
                ? replyTargets.get(replyTargetIndex)
                : Optional.empty();
    }

    private Collection<ResourceStatus> getCurrentTargetStatus() {
        return resourceStatusMap.values();
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
     * @param address the address to convert to a {@link PublishTarget} of type {@code <T>}.
     * @return the instance of type {@code <T>}
     */
    protected abstract T toPublishTarget(String address);

    /**
     * Publish a message. Construct the acknowledgement regardless of any request for diagnostic purposes.
     *
     * @param signal the nullable Target for getting even more information about the configured Target to publish to.
     * @param autoAckTarget if set, this is the Target from which {@code Acknowledgement}s should automatically be
     * produced and delivered.
     * @param publishTarget the {@link PublishTarget} to publish to.
     * @param message the {@link org.eclipse.ditto.services.models.connectivity.ExternalMessage} to publish.
     * @param maxTotalMessageSize the total max message size in bytes of the payload of an automatically created
     * response.
     * @param ackSizeQuota budget in bytes for how large the payload of this acknowledgement can be, or 0 to not
     * send any acknowledgement.
     * @return future of command responses (acknowledgements and potentially responses to live commands / live messages)
     * to reply to sender, or future of null if ackSizeQuota is 0.
     */
    protected abstract CompletionStage<CommandResponseOrAcknowledgement> publishMessage(Signal<?> signal,
            @Nullable Target autoAckTarget, T publishTarget, ExternalMessage message, int maxTotalMessageSize,
            int ackSizeQuota);

    /**
     * @return the logger to use.
     */
    protected abstract DittoDiagnosticLoggingAdapter log();

    private static <T> CompletionStage<List<T>> aggregateNonNullFutures(final CompletableFuture<T>[] futures) {
        return CompletableFuture.allOf(futures)
                .thenApply(aVoid ->
                        Arrays.stream(futures)
                                .map(CompletableFuture::join)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList())
                );
    }

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
        final ConnectionFailure failure = new ImmutableConnectionFailure(getSelf(), error, description);
        getContext().getParent().tell(failure, getSelf());
    }

    /**
     * Extract acknowledgement label from an auto-ack target.
     *
     * @param target the target.
     * @return the configured auto-ack label if any exists, or an empty optional.
     */
    protected static Optional<AcknowledgementLabel> getAcknowledgementLabel(@Nullable final Target target) {
        return Optional.ofNullable(target).flatMap(Target::getIssuedAcknowledgementLabel);
    }

    /**
     * Checks whether the passed in {@code outboundSignal} is a response or an error or a search event.
     * Those messages are supposed to be published at the reply target of the source whence the original command came.
     *
     * @param outboundSignal the OutboundSignal to check.
     * @return {@code true} if the OutboundSignal is a response or an error, {@code false} otherwise
     */
    private static boolean isResponseOrErrorOrSearchEvent(final OutboundSignal.Mapped outboundSignal) {
        return outboundSignal.getExternalMessage().isResponse() ||
                outboundSignal.getExternalMessage().isError() ||
                outboundSignal.getSource() instanceof SubscriptionEvent;
    }

    /**
     * Applies the optional "header mapping" potentially configured on the passed {@code target} on the passed {@code
     * outboundSignal}.
     *
     * @param outboundSignal the OutboundSignal containing the {@link ExternalMessage} with headers potentially
     * containing placeholders.
     * @param mapping headerMappings to apply.
     * @param log the logger to use for logging.
     * @return the ExternalMessage with replaced headers
     */
    static ExternalMessage applyHeaderMapping(final OutboundSignal.Mapped outboundSignal,
            final @Nullable HeaderMapping mapping,
            final DiagnosticLoggingAdapter log) {

        return applyHeaderMapping(Resolvers.forOutbound(outboundSignal), outboundSignal, mapping, log);
    }

    private static ExternalMessage applyHeaderMapping(final ExpressionResolver expressionResolver,
            final OutboundSignal.Mapped outboundSignal,
            final @Nullable HeaderMapping mapping,
            final DiagnosticLoggingAdapter log) {

        final ExternalMessage originalMessage = outboundSignal.getExternalMessage();

        final ExternalMessageBuilder messageBuilder = ExternalMessageFactory.newExternalMessageBuilder(originalMessage);

        if (mapping != null) {
            final Signal<?> sourceSignal = outboundSignal.getSource();

            final Map<String, String> mappedHeaders = mapping.getMapping().entrySet().stream()
                    .flatMap(e -> mapHeaderByResolver(expressionResolver, e.getValue())
                            .stream()
                            .map(resolvedValue -> Pair.create(e.getKey(), resolvedValue))
                    )
                    .collect(Collectors.toMap(Pair::first, Pair::second));

            LogUtil.enhanceLogWithCorrelationId(log, sourceSignal);
            log.debug("Result of header mapping <{}> are these headers to be published: {}", mapping, mappedHeaders);

            messageBuilder.withAdditionalHeaders(mappedHeaders);
        }

        return messageBuilder.build();
    }

    private static Optional<String> mapHeaderByResolver(final ExpressionResolver resolver, final String value) {
        return PlaceholderFilter.applyOrElseDelete(value, resolver);
    }

    /**
     * Resolve target address.
     * If not resolvable, the returned Optional will be empty.
     */
    private static Optional<String> resolveTargetAddress(final ExpressionResolver resolver, final String value) {
        return resolver.resolve(value).toOptional();
    }

    private static String getInstanceIdentifier() {
        return InstanceIdentifierSupplier.getInstance().get();
    }

    private static Charset determineCharset(final CharSequence contentType) {
        return CharsetDeterminer.getInstance().apply(contentType);
    }

    private static boolean isTargetAckRequested(final OutboundSignal.Mapped mapped, final Target target) {
        final Set<AcknowledgementLabel> requestedAcks = mapped.getSource()
                .getDittoHeaders()
                .getAcknowledgementRequests()
                .stream()
                .map(AcknowledgementRequest::getLabel)
                .collect(Collectors.toSet());
        return target.getIssuedAcknowledgementLabel().filter(requestedAcks::contains).isPresent();
    }

    private SendingContext sendingContextForReplyTarget(final OutboundSignal.Mapped outboundSignal,
            final ReplyTarget replyTarget) {
        final ExternalMessage externalMessage = outboundSignal.getExternalMessage();
        return new SendingContext(connectionId, outboundSignal, externalMessage, replyTarget, responsePublishedMonitor,
                null,
                responseDroppedMonitor, null);
    }

    private SendingContext sendingContextForTarget(final OutboundSignal.Mapped outboundSignal, final Target target) {
        final ConnectionMonitor publishedMonitor =
                connectionMonitorRegistry.forOutboundPublished(connectionId, target.getOriginalAddress());
        @Nullable final ConnectionMonitor acknowledgedMonitor =
                isTargetAckRequested(outboundSignal, target) ?
                        connectionMonitorRegistry.forInboundAcknowledged(connectionId, target.getOriginalAddress()) :
                        null;
        @Nullable final Target autoAckTarget = isTargetAckRequested(outboundSignal, target) ? target : null;
        final ExternalMessage externalMessage = outboundSignal.getExternalMessage();
        return new SendingContext(connectionId, outboundSignal, externalMessage, target, publishedMonitor,
                acknowledgedMonitor,
                publishedMonitor, autoAckTarget);
    }

    @Nullable
    private static Acknowledgement convertErrorToAcknowledgement(final SendingContext sendingContext,
            final Exception exception,
            final ErrorConverter errorConverter) {
        final Optional<AcknowledgementLabel> label = getAcknowledgementLabel(sendingContext.autoAckTarget);
        if (label.isEmpty()) {
            // auto ack not requested
            return null;
        } else {
            // no ack possible for non-twin-events, thus entityId must be ThingId
            final ThingId entityId = ThingId.of(sendingContext.outboundSignal.getSource().getEntityId());
            final DittoHeaders dittoHeaders = sendingContext.outboundSignal.getSource().getDittoHeaders();
            if (exception instanceof DittoRuntimeException) {
                // assume DittoRuntimeException payload fits within quota
                return errorConverter.convertDittoRuntimeException((DittoRuntimeException) exception,
                        label.orElseThrow(), entityId, dittoHeaders);
            } else {
                // assume exception message fits within quota
                return errorConverter.convertGenericException(exception, label.orElseThrow(), entityId, dittoHeaders);
            }
        }
    }

    private static final class SendingContext {

        private final ConnectionId connectionId;
        private final OutboundSignal.Mapped outboundSignal;
        private final ExternalMessage externalMessage;
        private final GenericTarget genericTarget;
        private final ConnectionMonitor publishedMonitor;
        @Nullable private final ConnectionMonitor acknowledgedMonitor;
        private final ConnectionMonitor droppedMonitor;
        @Nullable private final Target autoAckTarget;

        private SendingContext(
                final ConnectionId connectionId,
                final OutboundSignal.Mapped outboundSignal,
                final ExternalMessage externalMessage,
                final GenericTarget genericTarget,
                final ConnectionMonitor publishedMonitor,
                @Nullable final ConnectionMonitor acknowledgedMonitor,
                final ConnectionMonitor droppedMonitor,
                @Nullable final Target autoAckTarget) {
            this.connectionId = connectionId;
            this.outboundSignal = outboundSignal;
            this.externalMessage = externalMessage;
            this.genericTarget = genericTarget;
            this.publishedMonitor = publishedMonitor;
            this.acknowledgedMonitor = acknowledgedMonitor;
            this.droppedMonitor = droppedMonitor;
            this.autoAckTarget = autoAckTarget;
        }

        private boolean shouldAcknowledge() {
            return autoAckTarget != null && acknowledgedMonitor != null;
        }

        private SendingContext setExternalMessage(final ExternalMessage externalMessage) {
            return new SendingContext(connectionId, outboundSignal, externalMessage, genericTarget, publishedMonitor,
                    acknowledgedMonitor, droppedMonitor,
                    autoAckTarget);
        }
    }

    /**
     * Either a signal being sent represented by a future acknowledgement, or a dropped signal.
     */
    private interface SendingOrDropped {

        <T> T eval(BiFunction<SendingContext, CompletionStage<CommandResponseOrAcknowledgement>, T> onSending,
                Function<SendingContext, T> onDropped);

        /**
         * @return the logger to use.
         */
        DittoDiagnosticLoggingAdapter log();

        /**
         * Return an optional future acknowledgement capturing the result of message sending.
         * <p>
         * <li>If the optional is empty, then the message is dropped.</li>
         * <li>If the future has the value null, then the acknowledgement is logged and not requested.</li>
         * <li>If the future has a non-null acknowledgement, then that ack is requested and should be sent back.</li>
         *
         * @return the send result optional.
         */
        default Optional<CompletionStage<CommandResponseOrAcknowledgement>> monitorAndAcknowledge(
                final ErrorConverter errorConverter) {
            return eval(
                    (context, sendFuture) -> Optional.of(sendFuture.thenApply(
                            responseOrAck -> {
                                @Nullable final CommandResponse<?> commandResponse = responseOrAck.getCommandResponse()
                                        .orElse(null);

                                if (context.shouldAcknowledge()) {
                                    if (context.autoAckTarget != null &&
                                            context.autoAckTarget.getIssuedAcknowledgementLabel()
                                                    .filter(DittoAcknowledgementLabel.LIVE_RESPONSE::equals)
                                                    .isPresent()) {
                                        final boolean isSendSuccess = commandResponse != null &&
                                                !commandResponse.getStatusCode().isClientError() &&
                                                !commandResponse.getStatusCode().isInternalError();
                                        if (isSendSuccess) {
                                            context.publishedMonitor.success(context.externalMessage);
                                        } else if (commandResponse != null) {
                                            if (context.acknowledgedMonitor != null) {
                                                context.acknowledgedMonitor.failure(context.externalMessage,
                                                        liveResponseToException(commandResponse));
                                            }
                                        } else {
                                            /*
                                             * command response == null; report error.
                                             * This indicates a bug in the publisher actor because if the issued-ack is
                                             * "live-response" the response must be considered as the ack which has to
                                             * be issued by the auto ack target.
                                             */
                                            context.publishedMonitor.failure(context.externalMessage,
                                                    NULL_ACK_EXCEPTION);
                                            return new CommandResponseOrAcknowledgement(
                                                    commandResponse,
                                                    convertErrorToAcknowledgement(context, NULL_ACK_EXCEPTION,
                                                            errorConverter)
                                            );
                                        }

                                    } else if (responseOrAck.getAcknowledgement().isPresent()) {
                                        final Acknowledgement ack = responseOrAck.getAcknowledgement().get();
                                        final boolean isSendSuccess = !context.shouldAcknowledge() ||
                                                !ack.getStatusCode().isClientError() &&
                                                        !ack.getStatusCode().isInternalError();
                                        if (isSendSuccess) {
                                            context.publishedMonitor.success(context.externalMessage);
                                        } else {
                                            if (context.acknowledgedMonitor != null) {
                                                context.acknowledgedMonitor.failure(context.externalMessage,
                                                        ackToException(ack));
                                            }
                                        }
                                    } else {
                                        /*
                                         * ack == null; report error.
                                         * This indicates a bug in the publisher actor because ack is only allowed to
                                         * be null if the issued-ack should be "live-response".
                                         */
                                        context.publishedMonitor.failure(context.externalMessage,
                                                NULL_ACK_EXCEPTION);
                                        return new CommandResponseOrAcknowledgement(
                                                commandResponse,
                                                convertErrorToAcknowledgement(context, NULL_ACK_EXCEPTION,
                                                        errorConverter)
                                        );
                                    }
                                }
                                return responseOrAck;
                            })
                            .exceptionally(error -> {
                                final Exception rootCause = getRootCause(error);
                                monitorSendFailure(context.externalMessage, rootCause, context.publishedMonitor,
                                        context.connectionId, log());
                                return new CommandResponseOrAcknowledgement(null,
                                        convertErrorToAcknowledgement(context, rootCause, errorConverter)
                                );
                            })
                    ),
                    context -> {
                        context.droppedMonitor.success(context.outboundSignal.getSource(),
                                "Signal dropped, target address unresolved: {0}",
                                context.genericTarget.getAddress()
                        );
                        return Optional.empty();
                    }
            );
        }

        private static DittoRuntimeException ackToException(final Acknowledgement ack) {
            return MessageSendingFailedException.newBuilder()
                    .statusCode(ack.getStatusCode())
                    .message("Received negative acknowledgement for label <" + ack.getLabel() + ">.")
                    .description("Payload: " + ack.getEntity().map(JsonValue::toString).orElse("<empty>"))
                    .build();
        }

        private static DittoRuntimeException liveResponseToException(final CommandResponse response) {
            return MessageSendingFailedException.newBuilder()
                    .statusCode(response.getStatusCode())
                    .message("Received negative acknowledgement for label <" +
                            DittoAcknowledgementLabel.LIVE_RESPONSE.toString() + ">.")
                    .description("Payload: " + response.toJson())
                    .build();
        }

        private static Exception getRootCause(final Throwable throwable) {
            if (throwable instanceof CompletionException && throwable.getCause() != null) {
                return getRootCause(throwable.getCause());
            } else if (throwable instanceof Exception) {
                return (Exception) throwable;
            } else {
                return new RuntimeException(throwable);
            }
        }

        private static void monitorSendFailure(final ExternalMessage message, final Exception exception,
                final ConnectionMonitor publishedMonitor, final ConnectionId connectionId,
                final DittoDiagnosticLoggingAdapter log) {

            ConnectionLogUtil.enhanceLogWithConnectionId(log, connectionId);
            if (exception instanceof DittoRuntimeException) {
                final DittoRuntimeException dittoRuntimeException = (DittoRuntimeException) exception;
                publishedMonitor.failure(message, dittoRuntimeException);
                log.withCorrelationId(dittoRuntimeException)
                        .info("Ran into a failure when publishing signal - {}: {}",
                                dittoRuntimeException.getClass().getSimpleName(),
                                dittoRuntimeException.getMessage());
            } else {
                publishedMonitor.exception(message, exception);
                @Nullable final String correlationId = message.getInternalHeaders().getCorrelationId().orElseGet(() ->
                        message.findHeaderIgnoreCase(CORRELATION_ID.getKey())
                                .orElse(null));
                log.withCorrelationId(correlationId)
                        .info("Unexpected failure when publishing signal  - {}: {}",
                                exception.getClass().getSimpleName(),
                                exception.getMessage());
            }
            ConnectionLogUtil.removeConnectionId(log);
        }

    }

    private static final class Sending implements SendingOrDropped {

        private final SendingContext sendingContext;
        private final CompletionStage<CommandResponseOrAcknowledgement> responsesFuture;
        private final DittoDiagnosticLoggingAdapter log;

        private Sending(final SendingContext sendingContext,
                final CompletionStage<CommandResponseOrAcknowledgement> responsesFuture,
                final DittoDiagnosticLoggingAdapter log) {
            this.sendingContext = sendingContext;
            this.responsesFuture = responsesFuture;
            this.log = log;
        }

        @Override
        public DittoDiagnosticLoggingAdapter log() {
            return log;
        }

        @Override
        public <T> T eval(
                final BiFunction<SendingContext, CompletionStage<CommandResponseOrAcknowledgement>, T> onSending,
                final Function<SendingContext, T> onDropped) {
            return onSending.apply(sendingContext, responsesFuture);
        }
    }

    private static final class Dropped implements SendingOrDropped {

        private final SendingContext outboundSignal;
        private final DittoDiagnosticLoggingAdapter log;

        private Dropped(final SendingContext outboundSignal, final DittoDiagnosticLoggingAdapter log) {
            this.outboundSignal = outboundSignal;
            this.log = log;
        }

        @Override
        public DittoDiagnosticLoggingAdapter log() {
            return log;
        }

        @Override
        public <T> T eval(
                final BiFunction<SendingContext, CompletionStage<CommandResponseOrAcknowledgement>, T> onSending,
                final Function<SendingContext, T> onDropped) {
            return onDropped.apply(outboundSignal);
        }
    }

    /**
     * Fully customizable converter from publisher errors to acknowledgements.
     */
    protected static class ErrorConverter {

        private static final ErrorConverter DEFAULT_INSTANCE = new ErrorConverter();

        /**
         * Convert a DittoRuntimeException to an acknowledgement. By default, the payload is the JSON representation
         * of the DittoRuntimeException excluding the field "status", which is used as the status code of
         * the acknowledgement itself.
         *
         * @param dittoRuntimeException the DittoRuntimeException.
         * @param label the desired acknowledgement label.
         * @param entityId the entity ID.
         * @param dittoHeaders the DittoHeaders of the sending context.
         * @return acknowledgement for the DittoRuntimeException.
         */
        protected Acknowledgement convertDittoRuntimeException(final DittoRuntimeException dittoRuntimeException,
                final AcknowledgementLabel label,
                final EntityIdWithType entityId,
                final DittoHeaders dittoHeaders) {
            final HttpStatusCode status = dittoRuntimeException.getStatusCode();
            final JsonObject payload = dittoRuntimeException.toJson(
                    field -> !DittoRuntimeException.JsonFields.STATUS.getPointer()
                            .equals(JsonPointer.of(field.getKey()))
            );
            return Acknowledgement.of(label, entityId, status, dittoHeaders, payload);
        }

        /**
         * Convert a generic exception into an acknowledgement.
         *
         * @param exception the generic exception (non-DittoRuntimeException).
         * @param label the desired acknowledgement label.
         * @param entityId the entity ID.
         * @param dittoHeaders the DittoHeaders of the sending context.
         * @return acknowledgement for the generic exception.
         */
        protected Acknowledgement convertGenericException(
                final Exception exception,
                final AcknowledgementLabel label,
                final EntityIdWithType entityId,
                final DittoHeaders dittoHeaders) {

            final HttpStatusCode status = getStatusCodeForGenericException(exception);
            final String message = getDescriptionForGenericException(exception);
            final JsonObject payload = JsonObject.newBuilder()
                    .set(DittoRuntimeException.JsonFields.MESSAGE,
                            "Encountered '" + exception.getClass().getSimpleName() + "'")
                    .set(DittoRuntimeException.JsonFields.DESCRIPTION, message)
                    .build();
            return Acknowledgement.of(label, entityId, status, dittoHeaders, payload);
        }

        /**
         * Get the acknowledgement status code of a generic exception.
         *
         * @param exception the generic exception (non-DittoRuntimeException).
         * @return the status code of the converted acknowledgement.
         */
        protected HttpStatusCode getStatusCodeForGenericException(final Exception exception) {
            return HttpStatusCode.INTERNAL_SERVER_ERROR;
        }

        /**
         * Get the description of a generic exception.
         *
         * @param exception the generic exception (non-DittoRuntimeException).
         * @return the description in the payload of the converted acknowledgement.
         */
        protected String getDescriptionForGenericException(final Exception exception) {
            final String message = Optional.ofNullable(exception.getMessage()).orElse("Unknown error.");
            if (null != exception.getCause()) {
                return message + " - Cause: '" + exception.getCause().getClass().getSimpleName() + "'";
            } else {
                return message;
            }
        }
    }

    /**
     * A CommandResponse created by publishing a message or an Acknowledgement created by this publishing or even both.
     */
    protected static class CommandResponseOrAcknowledgement {

        @Nullable private final CommandResponse<?> commandResponse;
        @Nullable private final Acknowledgement acknowledgement;

        public CommandResponseOrAcknowledgement(
                @Nullable final CommandResponse<?> commandResponse,
                @Nullable final Acknowledgement acknowledgement) {
            this.commandResponse = commandResponse;
            this.acknowledgement = acknowledgement;
        }

        protected Optional<CommandResponse<?>> getCommandResponse() {
            return Optional.ofNullable(commandResponse);
        }

        protected Optional<Acknowledgement> getAcknowledgement() {
            return Optional.ofNullable(acknowledgement);
        }
    }
}
