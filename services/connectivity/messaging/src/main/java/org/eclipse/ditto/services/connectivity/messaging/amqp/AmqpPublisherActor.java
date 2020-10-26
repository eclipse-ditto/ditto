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
import org.eclipse.ditto.services.connectivity.messaging.BasePublisherActor;
import org.eclipse.ditto.services.connectivity.messaging.amqp.status.ProducerClosedStatusReport;
import org.eclipse.ditto.services.connectivity.messaging.backoff.BackOffActor;
import org.eclipse.ditto.services.connectivity.messaging.config.ConnectionConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.MonitoringConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.MonitoringLoggerConfig;
import org.eclipse.ditto.services.connectivity.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.internal.ImmutableConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.ConnectionLoggerRegistry;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.CommandResponse;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;

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

    private final Session session;
    private final LinkedHashMap<Destination, MessageProducer> dynamicTargets;
    private final Map<Destination, MessageProducer> staticTargets;
    private final int producerCacheSize;
    private final ActorRef backOffActor;
    private final ConnectionLogger connectionLogger;

    private boolean isInBackOffMode;

    @SuppressWarnings("unused")
    private AmqpPublisherActor(final Connection connection, final Session session,
            final ConnectionConfig connectionConfig) {

        super(connection);
        this.session = checkNotNull(session, "session");
        staticTargets = new HashMap<>();
        dynamicTargets = new LinkedHashMap<>(); // insertion order important for maintenance of producer cache
        producerCacheSize = checkArgument(connectionConfig.getAmqp10Config().getProducerCacheSize(), i -> i > 0,
                () -> "producer-cache-size must be 1 or more");

        connectionLogger = getConnectionLogger(connection);
        backOffActor = getContext().actorOf(BackOffActor.props(connectionConfig.getAmqp10Config().getBackOffConfig()));
        isInBackOffMode = false;
    }

    private ConnectionLogger getConnectionLogger(final Connection connection) {
        final MonitoringConfig monitoringConfig = connectivityConfig.getMonitoringConfig();
        final MonitoringLoggerConfig loggerConfig = monitoringConfig.logger();
        final ConnectionLoggerRegistry connectionLoggerRegistry = ConnectionLoggerRegistry.fromConfig(loggerConfig);
        return connectionLoggerRegistry.forConnection(connection.getId());
    }

    /**
     * Creates Akka configuration object {@link Props} for this {@code AmqpPublisherActor}.
     *
     * @param connection the connection this publisher belongs to
     * @param session the jms session
     * @param connectionConfig configuration for all connections.
     * @return the Akka configuration Props object.
     */
    static Props props(final Connection connection, final Session session, final ConnectionConfig connectionConfig) {
        return Props.create(AmqpPublisherActor.class, connection, session, connectionConfig);
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
            logger.error(errorMessage, jmsException);
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
            return doPublishMessage(signal, autoAckTarget, publishTarget, message);
        } else {
            final CompletableFuture<CommandResponse<?>> backOffModeFuture = new CompletableFuture<>();
            backOffModeFuture.completeExceptionally(getBackOffModeError(message, publishTarget.getJmsDestination()));
            return backOffModeFuture;
        }
    }

    private CompletionStage<CommandResponse<?>> doPublishMessage(@Nullable final Signal<?> signal,
            @Nullable final Target autoAckTarget,
            final AmqpTarget publishTarget,
            final ExternalMessage message) {

        final CompletableFuture<CommandResponse<?>> sendResult = new CompletableFuture<>();
        try {
            final MessageProducer producer = getProducer(publishTarget.getJmsDestination());
            if (producer != null) {
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
                            sendResult.complete(null);
                        } else {
                            sendResult.complete(toAcknowledgement(signal, autoAckTarget));
                        }
                        l.debug("Sent: <{}>", jmsMessage);
                    }

                    @Override
                    public void onException(final Message messageFailedToSend, final Exception exception) {
                        sendResult.completeExceptionally(getMessageSendingException(message, exception));
                    }
                });
            } else {
                // this happens when target address or 'reply-to' are set incorrectly.
                final String errorMessage = String.format("No producer available for target address '%s'",
                        publishTarget.getJmsDestination());
                final MessageSendingFailedException sendFailedException = MessageSendingFailedException.newBuilder()
                        .message(errorMessage)
                        .description("Is the target or reply-to address correct?")
                        .dittoHeaders(message.getInternalHeaders())
                        .build();
                sendResult.completeExceptionally(sendFailedException);
            }
        } catch (final JMSException e) {
            sendResult.completeExceptionally(getMessageSendingException(message, e));
        }
        return sendResult;
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
    private MessageProducer  createProducer(final Destination destination) throws JMSException {
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
        dynamicTargets.entrySet().forEach(this::closeCachedProducer);
        staticTargets.entrySet().forEach(this::closeCachedProducer);
    }

}
