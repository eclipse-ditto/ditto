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
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.common.CharsetDeterminer;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
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
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionEvent;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.DiagnosticLoggingAdapter;
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
    }

    @Override
    public Receive createReceive() {
        final ReceiveBuilder receiveBuilder = receiveBuilder();
        preEnhancement(receiveBuilder);

        receiveBuilder.match(OutboundSignal.MultiMapped.class, this::sendMultiMappedOutboundSignal)
                .match(RetrieveAddressStatus.class, ram -> getCurrentTargetStatus().forEach(rs ->
                        getSender().tell(rs, getSelf())))
                .matchAny(m -> {
                    log().warning("Unknown message: {}", m);
                    unhandled(m);
                });

        postEnhancement(receiveBuilder);
        return receiveBuilder.build();
    }

    private void sendMultiMappedOutboundSignal(final OutboundSignal.MultiMapped multiMapped) {
        final int quota = computeMaxAckPayloadBytesForSignal(multiMapped);

        final CompletableFuture<Acknowledgement>[] sendMonitorAndAckFutures =
                multiMapped.getMappedOutboundSignals()
                        .stream()
                        // message sending step
                        .flatMap(outbound -> sendMappedOutboundSignal(outbound, quota))
                        // monitor and acknowledge step
                        .flatMap(sendingOrDropped -> sendingOrDropped.monitorAndAcknowledge().stream())
                        // convert to completable future array for aggregation
                        .map(CompletionStage::toCompletableFuture)
                        .<CompletableFuture<Acknowledgement>>toArray(CompletableFuture[]::new);

        aggregateNonNullFutures(sendMonitorAndAckFutures)
                .thenAccept(ackList -> {
                    final ActorRef sender = getContext().getParent();
                    if (!ackList.isEmpty() && sender != null) {
                        final Acknowledgements aggregatedAcks =
                                Acknowledgements.of(ackList, multiMapped.getSource().getDittoHeaders());
                        log().withCorrelationId(aggregatedAcks).debug("Message sent. Replying to <{}>: <{}>",
                                sender, aggregatedAcks);
                        sender.tell(aggregatedAcks, getSelf());
                    } else {
                        log().withCorrelationId(multiMapped.getSource()).debug("Message sent: No acks requested.");
                    }
                })
                .exceptionally(e -> {
                    log().withCorrelationId(multiMapped.getSource())
                            .error(e, "Message sending failed unexpectedly: <{}>", multiMapped);
                    return null;
                });
    }

    private static int computeMaxAckPayloadBytesForSignal(final OutboundSignal.MultiMapped multiMapped) {
        final int numberOfSignals = multiMapped.getMappedOutboundSignals().size();
        // TODO: move to AcknowledgementConfig
        final int budget = 100000;
        final int defaultBudget = 4000;
        return numberOfSignals == 0 ? defaultBudget : budget / numberOfSignals;
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
                    .map(sendingContext -> publishToGenericTarget(resolver, sendingContext, maxPayloadBytes));
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
                final CompletionStage<Acknowledgement> ackFuture =
                        publishMessage(outbound.getSource(), autoAckTarget, publishTarget, mappedMessage, quota);
                // set the external message after header mapping for the result of header mapping to show up in log
                return new Sending(sendingContext.setExternalMessage(mappedMessage), ackFuture);
            } else {
                return new Dropped(sendingContext);
            }
        } catch (final Exception e) {
            return new Sending(sendingContext, CompletableFuture.failedFuture(e));
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
     * @param ackSizeQuota budget in bytes for how large the payload of this acknowledgement can be, or 0 to not
     * send any acknowledgement.
     * @return future of acknowledgement to reply to sender, or future of null if ackSizeQuota is 0.
     */
    protected abstract CompletionStage<Acknowledgement> publishMessage(Signal<?> signal,
            @Nullable Target autoAckTarget, T publishTarget,
            ExternalMessage message, int ackSizeQuota);

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
        return new SendingContext(outboundSignal, externalMessage, replyTarget, responsePublishedMonitor,
                responseDroppedMonitor, null);
    }

    private SendingContext sendingContextForTarget(final OutboundSignal.Mapped outboundSignal, final Target target) {
        final ConnectionMonitor monitor =
                connectionMonitorRegistry.forOutboundPublished(connectionId, target.getOriginalAddress());
        @Nullable final Target autoAckTarget = isTargetAckRequested(outboundSignal, target) ? target : null;
        final ExternalMessage externalMessage = outboundSignal.getExternalMessage();
        return new SendingContext(outboundSignal, externalMessage, target, monitor, monitor, autoAckTarget);
    }

    @Nullable
    private static Acknowledgement convertErrorToAcknowledgement(final SendingContext sendingContext,
            final Exception exception) {
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
                final DittoRuntimeException dittoRuntimeException = (DittoRuntimeException) exception;
                final HttpStatusCode status = dittoRuntimeException.getStatusCode();
                final JsonObject payload = dittoRuntimeException.toJson(
                        field -> !DittoRuntimeException.JsonFields.STATUS.getPointer()
                                .equals(JsonPointer.of(field.getKey()))
                );
                return Acknowledgement.of(label.orElseThrow(), entityId, status, dittoHeaders, payload);
            } else {
                // assume exception message fits within quota
                // TODO: check that common errors have reasonable error messages and status 500 do not cause problems.
                final HttpStatusCode status = HttpStatusCode.INTERNAL_SERVER_ERROR;
                final String message = Optional.ofNullable(exception.getMessage()).orElse("Unknown error.");
                final JsonObject payload = JsonObject.newBuilder()
                        .set(DittoRuntimeException.JsonFields.MESSAGE, message)
                        .build();
                return Acknowledgement.of(label.orElseThrow(), entityId, status, dittoHeaders, payload);
            }
        }
    }

    private static final class SendingContext {

        private final OutboundSignal.Mapped outboundSignal;
        private final ExternalMessage externalMessage;
        private final GenericTarget genericTarget;
        private final ConnectionMonitor publishedMonitor;
        private final ConnectionMonitor droppedMonitor;
        @Nullable private final Target autoAckTarget;

        private SendingContext(
                final OutboundSignal.Mapped outboundSignal,
                final ExternalMessage externalMessage,
                final GenericTarget genericTarget,
                final ConnectionMonitor publishedMonitor,
                final ConnectionMonitor droppedMonitor,
                @Nullable final Target autoAckTarget) {
            this.outboundSignal = outboundSignal;
            this.externalMessage = externalMessage;
            this.genericTarget = genericTarget;
            this.publishedMonitor = publishedMonitor;
            this.droppedMonitor = droppedMonitor;
            this.autoAckTarget = autoAckTarget;
        }

        private boolean shouldAcknowledge() {
            return autoAckTarget != null;
        }

        private SendingContext setExternalMessage(final ExternalMessage externalMessage) {
            return new SendingContext(outboundSignal, externalMessage, genericTarget, publishedMonitor, droppedMonitor,
                    autoAckTarget);
        }
    }

    /**
     * Either a signal being sent represented by a future acknowledgement, or a dropped signal.
     */
    private interface SendingOrDropped {

        <T> T eval(BiFunction<SendingContext, CompletionStage<Acknowledgement>, T> onSending,
                Function<SendingContext, T> onDropped);

        /**
         * Return an optional future acknowledgement capturing the result of message sending.
         * <p>
         * <li>If the optional is empty, then the message is dropped.</li>
         * <li>If the future has the value null, then the acknowledgement is logged and not requested.</li>
         * <li>If the future has a non-null acknowledgement, then that ack is requested and should be sent back.</li>
         *
         * @return the send result optional.
         */
        default Optional<CompletionStage<Acknowledgement>> monitorAndAcknowledge() {
            return eval(
                    (context, sendFuture) -> Optional.of(sendFuture.thenApply(
                            ack -> {
                                final boolean isSendSuccess = !context.shouldAcknowledge() || ack != null &&
                                        !ack.getStatusCode().isClientError() && !ack.getStatusCode().isInternalError();
                                if (isSendSuccess) {
                                    context.publishedMonitor.success(context.externalMessage);
                                    return context.shouldAcknowledge() ? ack : null;
                                } else if (ack != null) {
                                    context.publishedMonitor.failure(context.externalMessage, ackToException(ack));
                                    return ack;
                                } else {
                                    // ack == null; report error.
                                    // This indicates a bug in the publisher actor because ack should never be null.
                                    context.publishedMonitor.failure(context.externalMessage, NULL_ACK_EXCEPTION);
                                    return convertErrorToAcknowledgement(context, NULL_ACK_EXCEPTION);
                                }
                            })
                            .exceptionally(error -> {
                                final Exception rootCause = getRootCause(error);
                                monitorSendFailure(context.externalMessage, rootCause, context.publishedMonitor);
                                return convertErrorToAcknowledgement(context, rootCause);
                            })
                    ),
                    context -> {
                        // TODO: this was logged at level failure. Check if anything breaks logging at "success".
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
                final ConnectionMonitor publishedMonitor) {
            if (exception instanceof DittoRuntimeException) {
                publishedMonitor.failure(message, (DittoRuntimeException) exception);
            } else {
                publishedMonitor.exception(message, exception);
            }
        }

    }

    private static final class Sending implements SendingOrDropped {

        private final SendingContext sendingContext;
        private final CompletionStage<Acknowledgement> future;

        private Sending(final SendingContext sendingContext, final CompletionStage<Acknowledgement> future) {
            this.sendingContext = sendingContext;
            this.future = future;
        }

        @Override
        public <T> T eval(final BiFunction<SendingContext, CompletionStage<Acknowledgement>, T> onSending,
                final Function<SendingContext, T> onDropped) {
            return onSending.apply(sendingContext, future);
        }
    }

    private static final class Dropped implements SendingOrDropped {

        private final SendingContext outboundSignal;

        private Dropped(final SendingContext outboundSignal) {
            this.outboundSignal = outboundSignal;
        }

        @Override
        public <T> T eval(final BiFunction<SendingContext, CompletionStage<Acknowledgement>, T> onSending,
                final Function<SendingContext, T> onDropped) {
            return onDropped.apply(outboundSignal);
        }
    }

}
