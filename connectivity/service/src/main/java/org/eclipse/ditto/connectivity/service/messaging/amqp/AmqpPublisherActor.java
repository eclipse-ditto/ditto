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
package org.eclipse.ditto.connectivity.service.messaging.amqp;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkArgument;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
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

import org.apache.qpid.jms.JmsOperationTimedOutException;
import org.apache.qpid.jms.message.JmsMessage;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.common.Placeholders;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.GenericTarget;
import org.eclipse.ditto.connectivity.model.MessageSendingFailedException;
import org.eclipse.ditto.connectivity.model.ResourceStatus;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.service.config.Amqp10Config;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.BasePublisherActor;
import org.eclipse.ditto.connectivity.service.messaging.ConnectivityStatusResolver;
import org.eclipse.ditto.connectivity.service.messaging.SendResult;
import org.eclipse.ditto.connectivity.service.messaging.amqp.status.ProducerClosedStatusReport;
import org.eclipse.ditto.connectivity.service.messaging.backoff.BackOffActor;
import org.eclipse.ditto.connectivity.service.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.config.InstanceIdentifierSupplier;

import akka.Done;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
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

    private static final String TOO_MANY_IN_FLIGHT_MESSAGE_DESCRIPTION = "This can have the following reasons:\n" +
            "a) The AMQP 1.0 consumer does not consume the messages fast enough.\n" +
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
    private AmqpPublisherActor(final Connection connection,
            final Session session,
            final ConnectivityStatusResolver connectivityStatusResolver,
            final ConnectivityConfig connectivityConfig) {

        super(connection, connectivityStatusResolver, connectivityConfig);
        this.session = checkNotNull(session, "session");

        final Executor jmsDispatcher = JMSConnectionHandlingActor.getOwnDispatcher(getContext().system());

        final Amqp10Config config = connectionConfig.getAmqp10Config();
        final Materializer materializer = Materializer.createMaterializer(this::getContext);
        final Pair<SourceQueueWithComplete<Pair<ExternalMessage, AmqpMessageContext>>, UniqueKillSwitch> materialized =
                Source.<Pair<ExternalMessage, AmqpMessageContext>>queue(config.getPublisherConfig().getMaxQueueSize(),
                                OverflowStrategy.dropNew())
                        .mapAsync(config.getPublisherConfig().getParallelism(),
                                msg -> triggerPublishAsync(msg, jmsDispatcher))
                        .recover(new PFBuilder<Throwable, Object>()
                                // the "Done" instance is not used, this just means to not fail the stream for any Throwables
                                .matchAny(x -> Done.getInstance())
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
     * @param connectivityStatusResolver connectivity status resolver to resolve occurred exceptions to a connectivity
     * status.
     * @param connectivityConfig the connectivity configuration including potential overwrites.
     * @return the Akka configuration Props object.
     */
    static Props props(final Connection connection,
            final Session session,
            final ConnectivityStatusResolver connectivityStatusResolver,
            final ConnectivityConfig connectivityConfig) {

        return Props.create(AmqpPublisherActor.class,
                connection,
                session,
                connectivityStatusResolver,
                connectivityConfig);
    }

    @Override
    protected void preEnhancement(final ReceiveBuilder receiveBuilder) {
        receiveBuilder.match(ProducerClosedStatusReport.class, this::handleProducerClosedStatusReport)
                .matchEquals(Control.START_PRODUCER, this::handleStartProducer)
                .matchEquals(Control.INITIALIZE, this::initialize);
    }

    private void initialize(final Control initialize) {
        try {
            createStaticTargetProducers();
            getSender().tell(new Status.Success("publisher initialized"), getSelf());
        } catch (final Exception e) {
            getSender().tell(e, getSelf());
            getContext().stop(getSelf());
        }
    }

    private void handleProducerClosedStatusReport(final ProducerClosedStatusReport report) {
        if (!isInBackOffMode) {
            final MessageProducer producer = report.getMessageProducer();
            final Throwable cause = report.getCause();
            final String genericLogInfo = "Will try to re-establish the static targets after some cool-down period";
            logger.info("Got closed AMQP 1.0 producer '{}'. {}", producer, genericLogInfo);
            connectionLogger.failure("Targets were closed due to an error in the target. {0}", genericLogInfo);

            final List<Destination> destinations =
                    findByValue(staticTargets, producer).map(Map.Entry::getKey).toList();
            destinations.forEach(destination -> {
                staticTargets.remove(destination);
                // trigger backoff to recreate closed static targets
                backOff();
                // update resource status of closed targets
                final String statusDetails =
                        String.format("Producer for destination '%s' was closed", destination);
                final String fullDescriptionWithCause = ConnectionFailure.determineFailureDescription(Instant.now(),
                        cause, statusDetails);

                updateTargetResourceStatusForDestination(destination,
                        connectivityStatusResolver.resolve(cause), fullDescriptionWithCause);
            });

            // dynamic targets are not recreated, they are opened on-demand with the next message, no need to backoff
            findByValue(dynamicTargets, producer).map(Map.Entry::getKey).forEach(dynamicTargets::remove);
        } else {
            logger.info("Got closed AMQP 1.0 producer while already in backOff mode." +
                    " Will ignore the closed info as this should never happen" +
                    " (and also the backOff mechanism will create a producer soon)");
        }
    }

    private void updateTargetResourceStatusForDestination(final Destination destination,
            final ConnectivityStatus connectivityStatus, final String statusDetails) {
        // note: several targets may have the same address configured
        connection.getTargets()
                .stream()
                .filter(t -> t.getAddress().equals(destination.toString()))
                .forEach(target -> {
                    final ResourceStatus newStatus =
                            ConnectivityModelFactory.newTargetStatus(InstanceIdentifierSupplier.getInstance().get(),
                                    connectivityStatus, target.getAddress(), statusDetails, Instant.now());
                    resourceStatusMap.put(target, newStatus);
                });
    }

    private void handleStartProducer(final Control startProducer) {
        try {
            isInBackOffMode = false;
            createStaticTargetProducers();
        } catch (final JmsOperationTimedOutException jmsOperationTimedOutException) {
            final String message = "Failed to create target because of JmsOperationTimedOutException";
            logger.warning(message + ": {}", jmsOperationTimedOutException.getMessage());
            notifyParentAndStopSelf(message, jmsOperationTimedOutException);
        } catch (final JMSException jmsException) {
            // target producer not creatable; stop self and request restart by parent
            final String errorMessage = "Failed to create target";
            logger.error(jmsException, errorMessage);
            notifyParentAndStopSelf(errorMessage, jmsException);
        } catch (final Exception e) {
            logger.warning("Failed to create static target producers: {}", e.getMessage());
            getContext().getParent()
                    .tell(ConnectionFailure.of(null, e, "failed to initialize static producers"), getSelf());
            throw e;
        }
    }

    private void notifyParentAndStopSelf(final String message, final Exception exception) {
        final ConnectionFailure failure = ConnectionFailure.of(getSelf(), exception, message);
        final ActorContext context = getContext();
        context.getParent().tell(failure, getSelf());
        context.stop(getSelf());
    }

    private void createStaticTargetProducers() throws JMSException {
        final List<Target> targets = connection.getTargets();

        // using loop so that already created targets are closed on exception
        for (final Target target : targets) {
            // only targets with static addresses should stay open
            if (!Placeholders.containsAnyPlaceholder(target.getAddress())) {
                createStaticTargetProducer(toPublishTarget(target).getJmsDestination());
            }
        }
    }

    @Override
    protected AmqpTarget toPublishTarget(final GenericTarget target) {
        return AmqpTarget.fromTargetAddress(target.getAddress());
    }

    // create a target producer. the previous incarnation, if any, must be closed.
    private void createStaticTargetProducer(final Destination destination) throws JMSException {
        // make sure to create only one producer per destination
        if (!staticTargets.containsKey(destination)) {
            staticTargets.put(destination, session.createProducer(destination));
            updateTargetResourceStatusForDestination(destination, ConnectivityStatus.OPEN, "Producer started.");
            logger.info("Target producer <{}> ({}) created", destination, destination.getClass().getSimpleName());
        } else {
            logger.debug("Producer for destination '{}' already exists.", destination);
        }
    }

    private static Stream<Map.Entry<Destination, MessageProducer>> findByValue(
            final Map<Destination, MessageProducer> producerMap, final MessageProducer value) {

        return producerMap.entrySet().stream().filter(entry -> Objects.equals(entry.getValue(), value));
    }

    private void backOff() {
        isInBackOffMode = true;
        backOffActor.tell(BackOffActor.createBackOffWithAnswerMessage(Control.START_PRODUCER), getSelf());
    }

    @Override
    protected void postEnhancement(final ReceiveBuilder receiveBuilder) {
        // noop
    }

    @Override
    protected CompletionStage<SendResult> publishMessage(final Signal<?> signal,
            @Nullable final Target autoAckTarget,
            final AmqpTarget publishTarget,
            final ExternalMessage message,
            final int maxTotalMessageSize,
            final int ackSizeQuota,
            @Nullable final AuthorizationContext targetAuthorizationContext) {

        if (!isInBackOffMode) {
            final CompletableFuture<SendResult> resultFuture = new CompletableFuture<>();

            final AmqpMessageContext context = newContext(signal, autoAckTarget, publishTarget, resultFuture);
            sourceQueue.offer(Pair.create(message, context))
                    .whenComplete(handleQueueOfferResult(message, resultFuture));
            return resultFuture;
        } else {
            return CompletableFuture.failedStage(getBackOffModeError(message, publishTarget.getJmsDestination()));
        }
    }

    private AmqpMessageContext newContext(final Signal<?> signal,
            @Nullable final Target autoAckTarget,
            final AmqpTarget publishTarget,
            final CompletableFuture<SendResult> resultFuture) {

        final MessageProducer producer = getProducer(publishTarget.getJmsDestination());
        if (producer != null) {
            return newContextWithProducer(signal, autoAckTarget, producer, resultFuture);
        } else {
            return newContextWithoutProducer(publishTarget, resultFuture);
        }
    }

    private AmqpMessageContext newContextWithProducer(
            final Signal<?> signal,
            @Nullable final Target autoAckTarget,
            final MessageProducer producer,
            final CompletableFuture<SendResult> resultFuture) {

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
                        resultFuture.complete(buildResponse(signal, autoAckTarget));
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
            final CompletableFuture<SendResult> resultFuture) {

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
    private BiConsumer<QueueOfferResult, Throwable> handleQueueOfferResult(final ExternalMessage message,
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
        };
    }

    private SendResult buildResponse(final Signal<?> signal, @Nullable final Target autoAckTarget) {

        final DittoHeaders dittoHeaders = signal.getDittoHeaders();
        final Optional<AcknowledgementLabel> acknowledgementLabel = getAcknowledgementLabel(autoAckTarget);
        final Optional<EntityId> entityIdOptional =
                WithEntityId.getEntityIdOfType(EntityId.class, signal);
        final Acknowledgement issuedAck;
        if (acknowledgementLabel.isPresent() && entityIdOptional.isPresent()) {
            issuedAck = Acknowledgement.of(
                    acknowledgementLabel.get(),
                    entityIdOptional.get(),
                    HttpStatus.OK, dittoHeaders);
        } else {
            issuedAck = null;
        }
        return new SendResult(issuedAck, dittoHeaders);
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
            final String jmsExceptionString = jmsExceptionToString(e);
            connectionLogger.failure("Failed to create producer for destination <{0}>: {1}", destination,
                    jmsExceptionString);
            logger.warning("Failed to create producer for destination <{}>: {}.", destination, jmsExceptionString);
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

    /**
     * Actor control messages.
     */
    enum Control {
        /**
         * Message to initialize the actor. The sender will receive the result of initialization. If initialization fails, this actor stops itself.
         */
        INITIALIZE,
        /**
         * Message to trigger the creation of the static message producers.
         */
        START_PRODUCER
    }

}
