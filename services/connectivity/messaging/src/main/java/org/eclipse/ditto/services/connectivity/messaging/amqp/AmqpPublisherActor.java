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
package org.eclipse.ditto.services.connectivity.messaging.amqp;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkArgument;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.jms.BytesMessage;
import javax.jms.CompletionListener;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.qpid.jms.message.JmsMessage;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.common.Placeholders;
import org.eclipse.ditto.model.base.entity.id.EntityIdWithType;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.MessageSendingFailedException;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.connectivity.config.Amqp10Config;
import org.eclipse.ditto.services.connectivity.config.ConnectionConfig;
import org.eclipse.ditto.services.connectivity.messaging.BasePublisherActor;
import org.eclipse.ditto.services.connectivity.messaging.amqp.status.ProducerClosedStatusReport;
import org.eclipse.ditto.services.connectivity.messaging.backoff.BackOffActor;
import org.eclipse.ditto.services.connectivity.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.internal.ImmutableConnectionFailure;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.CommandResponse;

import akka.Done;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.Pair;
import akka.japi.pf.PFBuilder;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.KillSwitch;
import akka.stream.KillSwitches;
import akka.stream.Materializer;
import akka.stream.OverflowStrategy;
import akka.stream.QueueOfferResult;
import akka.stream.UniqueKillSwitch;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.SourceQueueWithComplete;
import akka.stream.scaladsl.Sink;

/**
 * Responsible for creating JMS {@link MessageProducer}s and sending {@link ExternalMessage}s as JMSMessages to those.
 */
public final class AmqpPublisherActor extends BasePublisherActor<AmqpTarget> {

    /**
     * The name prefix of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME_PREFIX = "amqpPublisherActor";

    private static final Object START_PRODUCER = new Object();

    private static final AcknowledgementLabel NO_ACK_LABEL = AcknowledgementLabel.of("ditto-amqp-diagnostic");
    private static final String TOO_MANY_IN_FLIGHT_MESSAGE_DESCRIPTION = "This can have the following reasons:\n" +
            "a) The AMQP consumer does not consume the messages fast enough.\n" +
            "b) The client count of this connection is not configured high enough.";

    private final Session session;
    private final LinkedHashMap<Destination, MessageProducer> dynamicTargets;
    private final Map<Destination, MessageProducer> staticTargets;
    private final int producerCacheSize;
    private final ActorRef backOffActor;
    private final SourceQueueWithComplete<Pair<ExternalMessage, AmqpMessageContext>> sourceQueue;
    private final KillSwitch killSwitch;

    private boolean isInBackOffMode;

    @SuppressWarnings("unused")
    private AmqpPublisherActor(final Connection connection, final Session session,
            final ConnectionConfig connectionConfig, final String clientId) {

        super(connection, clientId);
        this.session = checkNotNull(session, "session");

        final Executor jmsDispatcher = JMSConnectionHandlingActor.getOwnDispatcher(getContext().system());

        final Amqp10Config config = connectionConfig.getAmqp10Config();
        final Materializer materializer = Materializer.createMaterializer(this::getContext);
        final Pair<SourceQueueWithComplete<Pair<ExternalMessage, AmqpMessageContext>>, UniqueKillSwitch> materialized =
                Source.<Pair<ExternalMessage, AmqpMessageContext>>queue(config.getMaxQueueSize(),
                        OverflowStrategy.dropNew())
                        .mapAsync(config.getPublisherParallelism(), msg -> triggerPublishAsync(msg, jmsDispatcher))
                        .recover(new PFBuilder<Throwable, Object>()
                                .matchAny(
                                        x -> Done.getInstance()) // the "Done" instance is not used, this just means to not fail the stream for any Throwables
                                .build()
                        )
                        .viaMat(KillSwitches.single(), Keep.both())
                        .toMat(Sink.ignore(), Keep.left())
                        .run(materializer);

        sourceQueue = materialized.first();
        killSwitch = materialized.second();

        staticTargets = new HashMap<>();
        dynamicTargets = new LinkedHashMap<>(); // insertion order important for maintenance of producer cache
        producerCacheSize = checkArgument(config.getProducerCacheSize(), i -> i > 0,
                () -> "producer-cache-size must be 1 or more");

        backOffActor = getContext().actorOf(BackOffActor.props(config.getBackOffConfig()));
        isInBackOffMode = false;
    }

    /**
     * Wrap 'publishing the message' in a Future for async map stage in stream.
     *
     * @param messageToPublish The Element in the Stream, the message to publish and its context.
     * @param jmsDispatcher Executor, which triggers the async publishing via JMS.
     * @return A future, which is done, when the publishing was triggered.
     */
    private static CompletableFuture<Object> triggerPublishAsync(
            final Pair<ExternalMessage, AmqpMessageContext> messageToPublish,
            final Executor jmsDispatcher) {
        final ExternalMessage message = messageToPublish.first();
        final AmqpMessageContext context = messageToPublish.second();
        return CompletableFuture.supplyAsync(() -> context.onPublishMessage(message), jmsDispatcher)
                .thenCompose(Function.identity())
                .exceptionally(t -> null)
                .thenApply(x -> x);
    }

    /**
     * Creates Akka configuration object {@link Props} for this {@code AmqpPublisherActor}.
     *
     * @param connection the connection this publisher belongs to
     * @param session the jms session
     * @param connectionConfig configuration for all connections.
     * @param clientId identifier of the client actor.
     * @return the Akka configuration Props object.
     */
    static Props props(final Connection connection, final Session session, final ConnectionConfig connectionConfig,
            final String clientId) {
        return Props.create(AmqpPublisherActor.class, connection, session, connectionConfig, clientId);
    }

    @Override
    protected boolean shouldPublishAcknowledgement(final Acknowledgement acknowledgement) {
        return !NO_ACK_LABEL.equals(acknowledgement.getLabel());
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        // has to be done synchronously since there might already be messages in the actor's queue.
        // we open producers for static addresses (no placeholders) on startup and try to reopen them when closed.
        // producers for other addresses (with placeholders, reply-to) are opened on demand and may be closed to
        // respect the cache size limit
        handleStartProducer(START_PRODUCER);
    }

    @Override
    protected void preEnhancement(final ReceiveBuilder receiveBuilder) {
        receiveBuilder.match(ProducerClosedStatusReport.class, this::handleProducerClosedStatusReport)
                .matchEquals(START_PRODUCER, this::handleStartProducer);
    }

    private void handleProducerClosedStatusReport(final ProducerClosedStatusReport report) {
        if (!isInBackOffMode) {
            final MessageProducer producer = report.getMessageProducer();
            final String genericLogInfo = "Will try to re-establish the targets after some cool-down period.";
            logger.info("Got closed JMS producer '{}'. {}", producer, genericLogInfo);

            findByValue(dynamicTargets, producer).map(Map.Entry::getKey).forEach(dynamicTargets::remove);

            connectionLogger.failure("Targets were closed due to an error in the target. {0}", genericLogInfo);
            backOff();
        } else {
            logger.info("Got closed JMS producer while already in backOff mode." +
                    " Will ignore the closed info as this should never happen" +
                    " (and also the backOff mechanism will create a producer soon)");
        }
    }

    private void handleStartProducer(final Object startProducer) {
        try {
            isInBackOffMode = false;
            createStaticTargetProducers();
        } catch (final Exception e) {
            logger.warning("Failed to create static target producers: {}", e.getMessage());
            getContext().getParent().tell(new ImmutableConnectionFailure(null, e,
                    "failed to initialize static producers"), getSelf());
            throw e;
        }
    }

    private void createStaticTargetProducers() {
        final List<Target> targets = connection.getTargets();

        // using loop so that already created targets are closed on exception
        for (final Target target : targets) {
            // only targets with static addresses should stay open
            if (!Placeholders.containsAnyPlaceholder(target.getAddress())) {
                createTargetProducer(toPublishTarget(target.getAddress()).getJmsDestination());
            }
        }
    }

    @Override
    protected AmqpTarget toPublishTarget(final String address) {
        return AmqpTarget.fromTargetAddress(address);
    }

    // create a target producer. the previous incarnation, if any, must be closed.
    private void createTargetProducer(final Destination destination) {
        try {
            staticTargets.put(destination, session.createProducer(destination));
            logger.info("Target producer <{}> created", destination);
        } catch (final JMSException jmsException) {
            // target producer not creatable; stop self and request restart by parent
            final String errorMessage = String.format("Failed to create target '%s'", destination);
            logger.error(jmsException, errorMessage);
            final ConnectionFailure failure = new ImmutableConnectionFailure(getSelf(), jmsException, errorMessage);
            final ActorContext context = getContext();
            context.getParent().tell(failure, getSelf());
            context.stop(getSelf());
        }
    }

    private static Stream<Map.Entry<Destination, MessageProducer>> findByValue(
            final Map<Destination, MessageProducer> producerMap, final MessageProducer value) {

        return producerMap.entrySet().stream().filter(entry -> Objects.equals(entry.getValue(), value));
    }

    private void backOff() {
        isInBackOffMode = true;
        backOffActor.tell(BackOffActor.createBackOffWithAnswerMessage(START_PRODUCER), getSelf());
    }

    @Override
    protected void postEnhancement(final ReceiveBuilder receiveBuilder) {
        // noop
    }

    @Override
    protected CompletionStage<CommandResponse<?>> publishMessage(final Signal<?> signal,
            @Nullable final Target autoAckTarget,
            final AmqpTarget publishTarget,
            final ExternalMessage message,
            final int maxTotalMessageSize,
            final int ackSizeQuota) {

        if (!isInBackOffMode) {
            final CompletableFuture<CommandResponse<?>> resultFuture = new CompletableFuture<>();

            final AmqpMessageContext context = newContext(signal, autoAckTarget, publishTarget, resultFuture);
            sourceQueue.offer(Pair.create(message, context))
                    .handle(handleQueueOfferResult(message, resultFuture));
            return resultFuture;
        } else {
            return CompletableFuture.failedStage(getBackOffModeError(message, publishTarget.getJmsDestination()));
        }
    }

    private AmqpMessageContext newContext(@Nullable final Signal<?> signal,
            @Nullable final Target autoAckTarget,
            final AmqpTarget publishTarget,
            final CompletableFuture<CommandResponse<?>> resultFuture) {

        final MessageProducer producer = getProducer(publishTarget.getJmsDestination());
        if (producer != null) {
            return newContextWithProducer(signal, autoAckTarget, producer, resultFuture);
        } else {
            return newContextWithoutProducer(publishTarget, resultFuture);
        }
    }

    private AmqpMessageContext newContextWithProducer(
            @Nullable final Signal<?> signal,
            @Nullable final Target autoAckTarget,
            final MessageProducer producer,
            final CompletableFuture<CommandResponse<?>> resultFuture) {

        return message -> {
            try {
                final Message jmsMessage = toJmsMessage(message);

                final ThreadSafeDittoLoggingAdapter l;
                if (logger.isDebugEnabled()) {
                    l = logger.withCorrelationId(message.getInternalHeaders());
                } else {
                    l = logger;
                }

                l.debug("Attempt to send message <{}> with producer <{}>.", message, producer);
                producer.send(jmsMessage, new CompletionListener() {
                    @Override
                    public void onCompletion(final Message jmsMessage) {
                        if (signal == null) {
                            resultFuture.complete(null);
                        } else {
                            resultFuture.complete(toAcknowledgement(signal, autoAckTarget));
                        }
                        l.debug("Sent: <{}>", jmsMessage);
                    }

                    @Override
                    public void onException(final Message messageFailedToSend, final Exception exception) {
                        resultFuture.completeExceptionally(getMessageSendingException(message, exception));
                    }
                });
            } catch (final JMSException e) {
                resultFuture.completeExceptionally(getMessageSendingException(message, e));
            }
            return resultFuture;
        };
    }

    private AmqpMessageContext newContextWithoutProducer(final AmqpTarget publishTarget,
            final CompletableFuture<CommandResponse<?>> resultFuture) {

        return message -> {
            // this happens when target address or 'reply-to' are set incorrectly.
            final String errorMessage = String.format("No producer available for target address '%s'",
                    publishTarget.getJmsDestination());
            final MessageSendingFailedException sendFailedException =
                    MessageSendingFailedException.newBuilder()
                            .message(errorMessage)
                            .description("Is the target or reply-to address correct?")
                            .dittoHeaders(message.getInternalHeaders())
                            .build();
            resultFuture.completeExceptionally(sendFailedException);
            return resultFuture;
        };
    }

    // Async callback. Must be thread-safe.
    private BiFunction<QueueOfferResult, Throwable, Void> handleQueueOfferResult(final ExternalMessage message,
            final CompletableFuture<?> resultFuture) {

        return (queueOfferResult, error) -> {
            if (error != null) {
                final String errorDescription = "Source queue failure";
                logger.error(error, errorDescription);
                resultFuture.completeExceptionally(error);
                escalate(error, errorDescription);
            } else if (Objects.equals(queueOfferResult, QueueOfferResult.dropped())) {
                resultFuture.completeExceptionally(MessageSendingFailedException.newBuilder()
                        .message("Outgoing AMQP message dropped: There are too many unsettled messages " +
                                "or too few credits.")
                        .description(TOO_MANY_IN_FLIGHT_MESSAGE_DESCRIPTION)
                        .dittoHeaders(message.getInternalHeaders())
                        .build());
            }
            return null;
        };
    }

    private Acknowledgement toAcknowledgement(final Signal<?> signal, @Nullable final Target autoAckTarget) {

        // acks for non-thing-signals are for local diagnostics only, therefore it is safe to fix entity type to Thing.
        final EntityIdWithType entityIdWithType = ThingId.of(signal.getEntityId());
        final DittoHeaders dittoHeaders = signal.getDittoHeaders();
        final AcknowledgementLabel label = getAcknowledgementLabel(autoAckTarget).orElse(NO_ACK_LABEL);

        return Acknowledgement.of(label, entityIdWithType, HttpStatusCode.OK, dittoHeaders);
    }

    private static MessageSendingFailedException getMessageSendingException(final ExternalMessage message,
            final Throwable e) {

        return MessageSendingFailedException.newBuilder()
                .cause(e)
                .dittoHeaders(message.getInternalHeaders())
                .build();
    }

    private static MessageSendingFailedException getBackOffModeError(final ExternalMessage message,
            final Destination destination) {

        final String errorMessage = String.format("Producer for target address '%s' is in back off mode, as the " +
                "target configuration seems to contain errors. The message will be dropped.", destination);
        return MessageSendingFailedException.newBuilder()
                .message(errorMessage)
                .description("Check if the target or the reply-to configuration is correct.")
                .dittoHeaders(message.getInternalHeaders())
                .build();
    }

    private Message toJmsMessage(final ExternalMessage externalMessage) throws JMSException {
        final Message message;
        final Optional<String> optTextPayload = externalMessage.getTextPayload();
        if (optTextPayload.isPresent()) {
            message = session.createTextMessage(optTextPayload.get());
        } else if (externalMessage.getBytePayload().isPresent()) {
            final BytesMessage bytesMessage = session.createBytesMessage();
            bytesMessage.writeBytes(externalMessage.getBytePayload().map(ByteBuffer::array).orElse(new byte[]{}));
            message = bytesMessage;
        } else {
            message = session.createMessage();
        }
        JMSPropertyMapper.setPropertiesAndApplicationProperties(message, externalMessage.getHeaders(), logger);

        // wrap the message to prevent Qpid client from setting properties willy-nilly.
        return JMSMessageWorkaround.wrap((JmsMessage) message);
    }

    @Nullable
    private MessageProducer getProducer(final Destination destination) {
        final MessageProducer messageProducer;
        if (staticTargets.containsKey(destination)) {
            messageProducer = staticTargets.get(destination);
        } else {
            messageProducer = dynamicTargets.computeIfAbsent(destination, this::tryToCreateProducer);
            maintainReplyToMap();
        }
        return messageProducer;
    }

    @Nullable
    private MessageProducer tryToCreateProducer(final Destination destination) {
        try {
            return createProducer(destination);
        } catch (final JMSException e) {
            logger.warning("Failed to create producer for destination <{}>: {}.", destination, jmsExceptionToString(e));
            return null;
        }
    }

    @Nullable
    private MessageProducer createProducer(final Destination destination) throws JMSException {
        logger.debug("Creating AMQP Producer for destination <{}>.", destination);
        return session.createProducer(destination);
    }

    private void maintainReplyToMap() {
        // cache maintenance strategy = discard eldest
        while (dynamicTargets.size() > producerCacheSize) {
            final Map.Entry<Destination, MessageProducer> cachedProducer = dynamicTargets.entrySet().iterator().next();
            closeCachedProducer(cachedProducer);
            dynamicTargets.remove(cachedProducer.getKey());
        }
    }

    private void closeCachedProducer(final Map.Entry<Destination, MessageProducer> cachedProducer) {
        final Destination destination = cachedProducer.getKey();
        try {
            final MessageProducer producer = cachedProducer.getValue();
            logger.debug("Closing AMQP Producer for destination <{}>.", destination);
            if (null != producer) {
                producer.close();
            } else {
                logger.warning("Null producer in cache for destination <{}>!", destination);
            }
        } catch (final JMSException jmsException) {
            logger.debug("Failed to close producer for destination <{}>! (Can be ignored if connection was already" +
                    " closed.): {}", destination, jmsExceptionToString(jmsException));
        }
    }

    private static String jmsExceptionToString(final JMSException jmsException) {
        if (jmsException.getCause() != null) {
            return String.format("[%s] %s (cause: %s - %s)", jmsException.getErrorCode(), jmsException.getMessage(),
                    jmsException.getCause().getClass().getSimpleName(), jmsException.getCause().getMessage());
        }

        return String.format("[%s] %s", jmsException.getErrorCode(), jmsException.getMessage());
    }

    @Override
    public void postStop() throws Exception {
        super.postStop();
        killSwitch.shutdown();
        dynamicTargets.entrySet().forEach(this::closeCachedProducer);
        staticTargets.entrySet().forEach(this::closeCachedProducer);
    }

}
