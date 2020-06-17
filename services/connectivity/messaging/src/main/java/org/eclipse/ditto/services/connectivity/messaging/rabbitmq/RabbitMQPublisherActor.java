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
package org.eclipse.ditto.services.connectivity.messaging.rabbitmq;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.common.CharsetDeterminer;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.MessageSendingFailedException;
import org.eclipse.ditto.model.connectivity.ResourceStatus;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.connectivity.messaging.BasePublisherActor;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.services.utils.config.InstanceIdentifierSupplier;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.base.Signal;

import com.newmotion.akka.rabbitmq.ChannelCreated;
import com.newmotion.akka.rabbitmq.ChannelMessage;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConfirmListener;
import com.rabbitmq.client.ReturnListener;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

/**
 * Responsible for publishing {@link ExternalMessage}s into RabbitMQ / AMQP 0.9.1.
 * <p>
 * To receive responses the {@code replyTo} header must be set. Responses are sent to the default exchange with the
 * {@code replyTo} header as routing key.
 * </p>
 * The {@code address} of the {@code targets} from the {@link Connection} are interpreted as follows:
 * <ul>
 * <li>no {@code targets} defined: signals are not published at all</li>
 * <li>{@code address="target/routingKey"}: signals are published to exchange {@code target} with routing key {@code
 * routingKey}</li>
 * </ul>
 */
public final class RabbitMQPublisherActor extends BasePublisherActor<RabbitMQTarget> {

    /**
     * The name of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME = "rmqPublisherActor";

    private static final AcknowledgementLabel NO_ACK_LABEL = AcknowledgementLabel.of("ditto-rabbitmq-diagnostic");

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
    private final ConcurrentSkipListMap<Long, OutstandingAck> outstandingAcks = new ConcurrentSkipListMap<>();
    private final ConcurrentHashMap<RabbitMQTarget, Queue<OutstandingAck>> outstandingAcksByTarget =
            new ConcurrentHashMap<>();
    private ConfirmMode confirmMode = ConfirmMode.UNKNOWN;
    @Nullable private ActorRef channelActor;

    @SuppressWarnings("unused")
    private RabbitMQPublisherActor(final Connection connection) {
        super(connection);
    }

    /**
     * Creates Akka configuration object {@link Props} for this {@code RabbitMQPublisherActor}.
     *
     * @param connection the connection this publisher belongs to
     * @return the Akka configuration Props object.
     */
    static Props props(final Connection connection) {

        return Props.create(RabbitMQPublisherActor.class, connection);
    }

    @Override
    protected void preEnhancement(final ReceiveBuilder receiveBuilder) {
        receiveBuilder
                .match(ChannelCreated.class, channelCreated -> {
                    this.channelActor = channelCreated.channel();

                    final ChannelMessage channelMessage = ChannelMessage.apply(this::onChannelCreated, false);
                    channelCreated.channel().tell(channelMessage, getSelf());
                })
                .match(ChannelStatus.class, this::handleChannelStatus)
                .build();
    }

    @Override
    protected void postEnhancement(final ReceiveBuilder receiveBuilder) {
        // noop
    }

    @Override
    protected RabbitMQTarget toPublishTarget(final String address) {
        return RabbitMQTarget.fromTargetAddress(address);
    }

    @Override
    protected DittoDiagnosticLoggingAdapter log() {
        return log;
    }

    @Override
    protected CompletionStage<Acknowledgement> publishMessage(final Signal<?> signal,
            @Nullable final Target autoAckTarget, final RabbitMQTarget publishTarget,
            final ExternalMessage message, int ackSizeQuota) {

        if (channelActor == null) {
            return sendFailedFuture(signal, "No channel available, dropping response.");
        }

        if (publishTarget.getRoutingKey() == null) {
            return sendFailedFuture(signal, "No routing key, dropping message.");
        }

        final Map<String, String> messageHeaders = message.getHeaders();
        final String contentType = messageHeaders.get(ExternalMessage.CONTENT_TYPE_HEADER);
        final String correlationId = messageHeaders.get(DittoHeaderDefinition.CORRELATION_ID.getKey());

        final Map<String, Object> stringObjectMap = messageHeaders.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> (Object) e.getValue()));

        final AMQP.BasicProperties basicProperties = new AMQP.BasicProperties.Builder()
                .contentType(contentType)
                .correlationId(correlationId)
                .headers(stringObjectMap)
                .build();

        final byte[] body;
        if (message.isTextMessage()) {
            body = message.getTextPayload()
                    .map(text -> text.getBytes(CharsetDeterminer.getInstance().apply(contentType)))
                    .orElseThrow(() -> new IllegalArgumentException("Failed to convert text to bytes."));
        } else {
            body = message.getBytePayload()
                    .map(ByteBuffer::array)
                    .orElse(new byte[]{});
        }

        final CompletableFuture<Acknowledgement> resultFuture = new CompletableFuture<>();
        // create consumer outside channel message: need to check actor state and decide whether to handle acks.
        final Consumer<Long> nextPublishSeqNoConsumer =
                computeNextPublishSeqNoConsumer(signal, autoAckTarget, publishTarget, resultFuture);
        final ChannelMessage channelMessage = ChannelMessage.apply(channel -> {
            try {
                log.debug("Publishing to exchange <{}> and routing key <{}>: {}", publishTarget.getExchange(),
                        publishTarget.getRoutingKey(), basicProperties);
                nextPublishSeqNoConsumer.accept(channel.getNextPublishSeqNo());
                channel.basicPublish(publishTarget.getExchange(), publishTarget.getRoutingKey(), true, basicProperties,
                        body);
            } catch (final Exception e) {
                final String errorMessage = String.format("Failed to publish message to RabbitMQ: %s", e.getMessage());
                resultFuture.completeExceptionally(sendFailed(signal, errorMessage, e));
            }
            return null;
        }, false);

        channelActor.tell(channelMessage, getSelf());
        return resultFuture;
    }

    // This method is NOT thread-safe, but its returned consumer MUST be thread-safe.
    private Consumer<Long> computeNextPublishSeqNoConsumer(final Signal<?> signal,
            @Nullable final Target autoAckTarget,
            final RabbitMQTarget publishTarget,
            final CompletableFuture<Acknowledgement> resultFuture) {
        if (confirmMode == ConfirmMode.ACTIVE) {
            // TODO: configure - also in other publisher actors?
            final Duration timeoutDuration = Duration.ofSeconds(60L);
            return seqNo -> addOutstandingAck(seqNo, signal, resultFuture, autoAckTarget, publishTarget, timeoutDuration);
        } else {
            final Acknowledgement unsupportedAck = getUnsupportedAck(signal, autoAckTarget);
            return seqNo -> resultFuture.complete(unsupportedAck);
        }
    }

    // Thread-safe
    private void addOutstandingAck(final Long seqNo, final Signal<?> signal,
            final CompletableFuture<Acknowledgement> resultFuture,
            @Nullable final Target autoAckTarget,
            final RabbitMQTarget publishTarget,
            final Duration timeoutDuration) {

        final OutstandingAck outstandingAck = new OutstandingAck(signal, autoAckTarget, resultFuture);
        final Acknowledgement timeoutAck = getTimeoutAck(signal, autoAckTarget);

        // index the outstanding ack by delivery tag
        outstandingAcks.put(seqNo, outstandingAck);
        resultFuture.completeOnTimeout(timeoutAck, timeoutDuration.toMillis(), TimeUnit.MILLISECONDS)
                .handle((ack, error) -> {
                    // Only remove future from cache. Actual logging/reporting done elsewhere.
                    outstandingAcks.remove(seqNo);
                    return null;
                });

        // index the outstanding ack by publish target in order to generate negative acks on basic.return messages
        outstandingAcksByTarget.compute(publishTarget, (key, queue) -> {
            final Queue<OutstandingAck> result = queue != null ? queue : new ConcurrentLinkedQueue<>();
            result.offer(outstandingAck);
            return result;
        });
        // maintain outstanding-acks-by-target. It need not be accurate because outstandingAcksByTarget is only used
        // on basic.return, which affects all messages published to 1 target but is not precise.
        resultFuture.whenComplete((ignoredAck, ignoredError) -> {
            outstandingAcksByTarget.computeIfPresent(publishTarget, (key, queue) -> {
                queue.poll();
                return queue.isEmpty() ? null : queue;
            });
        });
    }

    private void handleChannelStatus(final ChannelStatus channelStatus) {
        if (channelStatus.confirmationException != null) {
            log.error(channelStatus.confirmationException, "Failed to enter confirm mode.");
            confirmMode = ConfirmMode.INACTIVE;
        } else {
            confirmMode = ConfirmMode.ACTIVE;
        }
        resourceStatusMap.putAll(channelStatus.targetStatus);
    }

    // called by ChannelActor; must be thread-safe.
    private Void onChannelCreated(final Channel channel) {
        final IOException confirmationStatus =
                enterConfirmationMode(channel, outstandingAcks, outstandingAcksByTarget).orElse(null);
        final Map<Target, ResourceStatus> targetStatus =
                declareExchangesPassive(channel, targets, this::toPublishTarget, log);
        getSelf().tell(new ChannelStatus(confirmationStatus, targetStatus), ActorRef.noSender());
        return null;
    }

    private static Acknowledgement getTimeoutAck(final Signal<?> signal, @Nullable final Target autoAckTarget) {
        return buildAcknowledgement(signal, autoAckTarget, HttpStatusCode.REQUEST_TIMEOUT, "No publisher confirm arrived.");
    }

    private static Acknowledgement getUnsupportedAck(final Signal<?> signal, @Nullable final Target autoAckTarget) {
        if (autoAckTarget != null && autoAckTarget.getAcknowledgement().isPresent()) {
            return buildAcknowledgement(signal, autoAckTarget, HttpStatusCode.NOT_IMPLEMENTED,
                    "The external broker does not support RabbitMQ publisher confirms. " +
                            "Acknowledgement is not possible.");
        } else {
            return getSuccessAck(signal, autoAckTarget);
        }
    }

    private static Acknowledgement getSuccessAck(final Signal<?> signal, @Nullable final Target autoAckTarget) {
        return buildAcknowledgement(signal, autoAckTarget, HttpStatusCode.OK, null);
    }

    private static Acknowledgement getFailureAck(final Signal<?> signal, @Nullable final Target autoAckTarget) {
        return buildAcknowledgement(signal, autoAckTarget, HttpStatusCode.SERVICE_UNAVAILABLE,
                "Received negative confirm from the external broker.");
    }

    private static Acknowledgement getReturnAck(final Signal<?> signal, @Nullable final Target autoAckTarget,
            final int replyCode, final String replyText) {
        return buildAcknowledgement(signal, autoAckTarget, HttpStatusCode.SERVICE_UNAVAILABLE,
                String.format("Received basic.return from the external broker: %d %s", replyCode, replyText));
    }

    private static Acknowledgement buildAcknowledgement(final Signal<?> signal, @Nullable final Target autoAckTarget,
            final HttpStatusCode statusCode, @Nullable final String message) {
        final AcknowledgementLabel label =
                Optional.ofNullable(autoAckTarget).flatMap(Target::getAcknowledgement).orElse(NO_ACK_LABEL);
        return Acknowledgement.of(label, ThingId.of(signal.getEntityId()), statusCode, signal.getDittoHeaders(),
                message == null ? null : JsonValue.of(message));
    }

    private static Map<Target, ResourceStatus> declareExchangesPassive(final Channel channel,
            final Collection<Target> targets,
            final Function<String, RabbitMQTarget> toPublishTarget,
            final LoggingAdapter log) {

        final Map<String, Target> exchanges = targets.stream()
                .collect(Collectors.toMap(
                        t -> toPublishTarget.apply(t.getAddress()).getExchange(),
                        Function.identity()
                ));
        final Map<Target, ResourceStatus> declarationStatus = new HashMap<>();
        for (final String exchange : exchanges.keySet()) {
            log.debug("Checking for existence of exchange <{}>", exchange);
            try {
                channel.exchangeDeclarePassive(exchange);
            } catch (final IOException e) {
                log.warning("Failed to declare exchange <{}> passively", exchange);
                final Target target = exchanges.get(exchange);
                if (target != null) {
                    declarationStatus.put(target,
                            ConnectivityModelFactory.newTargetStatus(
                                    InstanceIdentifierSupplier.getInstance().get(),
                                    ConnectivityStatus.FAILED,
                                    target.getAddress(),
                                    "Exchange '" + exchange + "' was missing at " +
                                            Instant.now())
                    );
                }
            }
        }
        return Collections.unmodifiableMap(declarationStatus);
    }

    private static Optional<IOException> enterConfirmationMode(final Channel channel,
            final ConcurrentSkipListMap<Long, OutstandingAck> outstandingAcks,
            final ConcurrentHashMap<RabbitMQTarget, Queue<OutstandingAck>> outstandingAcksByTarget) {
        try {
            channel.confirmSelect();
            channel.clearConfirmListeners();
            channel.clearReturnListeners();
            final ActorConfirmListener confirmListener =
                    new ActorConfirmListener(outstandingAcks, outstandingAcksByTarget);
            channel.addConfirmListener(confirmListener);
            channel.addReturnListener(confirmListener);
            return Optional.empty();
        } catch (final IOException e) {
            return Optional.of(e);
        }
    }

    private static <T> CompletionStage<T> sendFailedFuture(final Signal<?> signal, final String errorMessage) {
        return CompletableFuture.failedFuture(sendFailed(signal, errorMessage, null));
    }

    private static MessageSendingFailedException sendFailed(final Signal<?> signal, final String errorMessage,
            @Nullable final Throwable cause) {
        return MessageSendingFailedException.newBuilder()
                .message(errorMessage)
                .dittoHeaders(signal.getDittoHeaders())
                .cause(cause)
                .build();
    }

    private static final class ChannelStatus {

        @Nullable private final IOException confirmationException;
        private final Map<Target, ResourceStatus> targetStatus;

        private ChannelStatus(@Nullable final IOException confirmationException,
                final Map<Target, ResourceStatus> targetStatus) {
            this.confirmationException = confirmationException;
            this.targetStatus = targetStatus;
        }
    }

    private static final class ActorConfirmListener implements ConfirmListener, ReturnListener {

        private final ConcurrentSkipListMap<Long, OutstandingAck> outstandingAcks;
        private final ConcurrentHashMap<RabbitMQTarget, Queue<OutstandingAck>> outstandingAcksByTarget;

        private ActorConfirmListener(final ConcurrentSkipListMap<Long, OutstandingAck> outstandingAcks,
                final ConcurrentHashMap<RabbitMQTarget, Queue<OutstandingAck>> outstandingAcksByTarget) {
            this.outstandingAcks = outstandingAcks;
            this.outstandingAcksByTarget = outstandingAcksByTarget;
        }

        /**
         * Handle ACK from the broker. ACK messages are always sent after a RETURN message.
         * The channel actor runs this listener in its thread, which guarantees that this.handleAck is always
         * called after this.handleReturn returns.
         *
         * @param deliveryTag The delivery tag of the acknowledged message.
         * @param multiple Whether this acknowledgement applies to multiple messages.
         */
        @Override
        public void handleAck(final long deliveryTag, final boolean multiple) {
            forEach(deliveryTag, multiple, OutstandingAck::completeWithSuccess);
        }

        /**
         * Handle NACK from the broker. NACK messages are always handled after this.handleReturn returns.
         *
         * @param deliveryTag The delivery tag of the acknowledged message.
         * @param multiple Whether this acknowledgement applies to multiple messages.
         */
        @Override
        public void handleNack(final long deliveryTag, final boolean multiple) {
            forEach(deliveryTag, multiple, OutstandingAck::completeWithFailure);
        }

        /**
         * Handle RETURN from the broker. RETURN messages are sent if a routing key does not exist for an existing
         * exchange. Since users can set publish target for each message by the means of placeholders, and because
         * queues can be created and deleted at any time, getting RETURN messages does not imply failure of the whole
         * channel.
         * <p>
         * Here all outstanding acks of an exchange, routing-key pair are completed exceptionally with the reply text
         * and reply code.
         *
         * @param replyCode reply code of this RETURN message.
         * @param replyText textual description of this RETURN message.
         * @param exchange exchange of the outgoing message being returned.
         * @param routingKey routing key of the outgoing message being returned.
         * @param properties AMQP properties of the RETURN message; typically empty regardless of the properties of the
         * outgoing message being returned.
         * @param body body of the returned message.
         */
        @Override
        public void handleReturn(final int replyCode, final String replyText, final String exchange,
                final String routingKey,
                final AMQP.BasicProperties properties, final byte[] body) {

            final RabbitMQTarget rabbitMQTarget = RabbitMQTarget.of(exchange, routingKey);
            final Queue<OutstandingAck> queue = outstandingAcksByTarget.get(rabbitMQTarget);
            // cleanup handled in another thread on completion of each outstanding ack
            if (queue != null) {
                queue.forEach(outstandingAck -> outstandingAck.completeForReturn(replyCode, replyText));
            }
        }

        private void forEach(final long deliveryTag, final boolean multiple,
                final Consumer<OutstandingAck> outstandingAckConsumer) {
            if (multiple) {
                // iterator is in key order because each ConcurrentSkipListMap is a SortedMap
                final Iterator<Map.Entry<Long, OutstandingAck>> it = outstandingAcks.entrySet().iterator();
                while (it.hasNext()) {
                    final Map.Entry<Long, OutstandingAck> entry = it.next();
                    if (entry.getKey() > deliveryTag) {
                        break;
                    }
                    outstandingAckConsumer.accept(entry.getValue());
                    it.remove();
                }
            } else {
                final OutstandingAck outstandingAck = outstandingAcks.get(deliveryTag);
                if (outstandingAck != null) {
                    outstandingAckConsumer.accept(outstandingAck);
                    outstandingAcks.remove(deliveryTag);
                }
            }
        }
    }

    private static final class OutstandingAck {

        private final Signal<?> signal;
        @Nullable private final Target autoAckTarget;
        private final CompletableFuture<Acknowledgement> future;

        private OutstandingAck(final Signal<?> signal,
                @Nullable final Target autoAckTarget,
                final CompletableFuture<Acknowledgement> future) {
            this.signal = signal;
            this.autoAckTarget = autoAckTarget;
            this.future = future;
        }

        private void completeWithSuccess() {
            future.complete(getSuccessAck(signal, autoAckTarget));
        }

        private void completeWithFailure() {
            future.complete(getFailureAck(signal, autoAckTarget));
        }

        private void completeForReturn(final int replyCode, final String replyText) {
            future.complete(getReturnAck(signal, autoAckTarget, replyCode, replyText));
        }
    }

    private enum ConfirmMode {
        UNKNOWN,
        ACTIVE,
        INACTIVE
    }

}
