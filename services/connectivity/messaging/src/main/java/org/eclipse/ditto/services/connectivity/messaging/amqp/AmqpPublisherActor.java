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
import org.eclipse.ditto.model.base.common.Placeholders;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.MessageSendingFailedException;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.connectivity.messaging.BasePublisherActor;
import org.eclipse.ditto.services.connectivity.messaging.amqp.status.ProducerClosedStatusReport;
import org.eclipse.ditto.services.connectivity.messaging.backoff.BackOffActor;
import org.eclipse.ditto.services.connectivity.messaging.config.ConnectionConfig;
import org.eclipse.ditto.services.connectivity.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.internal.ImmutableConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.services.connectivity.util.ConnectionLogUtil;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
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

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final Session session;
    private final LinkedHashMap<Destination, MessageProducer> dynamicTargets;
    private final Map<Destination, MessageProducer> staticTargets;
    private final int producerCacheSize;
    private final ActorRef backOffActor;

    private boolean isInBackOffMode = false;

    @SuppressWarnings("unused")
    private AmqpPublisherActor(final Connection connection, final Session session,
            final ConnectionConfig connectionConfig) {
        super(connection);
        ConnectionLogUtil.enhanceLogWithConnectionId(log, connectionId);
        this.session = checkNotNull(session, "session");
        this.staticTargets = new HashMap<>();
        this.dynamicTargets = new LinkedHashMap<>(); // insertion order important for maintenance of producer cache
        producerCacheSize = checkArgument(connectionConfig.getAmqp10Config().getProducerCacheSize(), i -> i > 0,
                () -> "producer-cache-size must be 1 or more");

        this.backOffActor =
                getContext().actorOf(BackOffActor.props(connectionConfig.getAmqp10Config().getBackOffConfig()));
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        // has to be done synchronously since there might already be messages in the actor's queue.
        // we open producers for static addresses (no placeholders) on startup and try to reopen them when closed.
        // producers for other addresses (with placeholders, reply-to) are opened on demand and may be closed to
        // respect the cache size limit
        this.handleStartProducer(START_PRODUCER);
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
    protected void preEnhancement(final ReceiveBuilder receiveBuilder) {
        receiveBuilder.match(ProducerClosedStatusReport.class, this::handleProducerClosedStatusReport)
                .matchEquals(START_PRODUCER, this::handleStartProducer);
    }

    private void handleStartProducer(final Object startProducer) {
        try {
            this.isInBackOffMode = false;
            createStaticTargetProducers(targets);
        } catch (final Exception e) {
            log.warning("Failed to create static target producers: {}", e.getMessage());
            getContext().getParent().tell(new ImmutableConnectionFailure(null, e,
                    "failed to initialize static producers"), getSelf());
            throw e;
        }
    }

    private void handleProducerClosedStatusReport(final ProducerClosedStatusReport report) {
        if (!this.isInBackOffMode) {
            final MessageProducer producer = report.getMessageProducer();
            final String genericLogInfo = "Will try to re-establish the targets after some cool-down period.";
            log.info("Got closed JMS producer '{}'. {}", producer, genericLogInfo);

            findByValue(dynamicTargets, producer).map(Map.Entry::getKey).forEach(dynamicTargets::remove);

            connectionLogger.failure("Targets were closed due to an error in the target. {0}", genericLogInfo);
            this.backOff();
        } else {
            log.info("Got closed JMS producer while already in backOff mode '{}'." +
                    " Will ignore the closed info as this should never happen " +
                    "(and also the backOff mechanism will create a producer soon)");
        }
    }

    private void backOff() {
        this.isInBackOffMode = true;
        this.backOffActor.tell(BackOffActor.createBackOffWithAnswerMessage(START_PRODUCER), getSelf());
    }

    @Override
    protected void postEnhancement(final ReceiveBuilder receiveBuilder) {
        // noop
    }

    @Override
    protected AmqpTarget toPublishTarget(final String address) {
        return AmqpTarget.fromTargetAddress(address);
    }

    @Override
    protected void publishMessage(@Nullable final Target target, final AmqpTarget publishTarget,
            final ExternalMessage message, final ConnectionMonitor publishedMonitor) {
        if (!this.isInBackOffMode) {
            this.tryToPublishMessage(publishTarget, message, publishedMonitor);
        } else {
            this.handleMessageInBackOffMode(message, publishedMonitor, publishTarget.getJmsDestination());
        }
    }

    private void tryToPublishMessage(final AmqpTarget publishTarget,
            final ExternalMessage message, final ConnectionMonitor publishedMonitor) {
        try {
            final MessageProducer producer = getProducer(publishTarget.getJmsDestination());
            if (producer != null) {
                final Message jmsMessage = toJmsMessage(message);

                final ActorRef sender = getSender();
                log.debug("Attempt to send message {} with producer {}.", message, producer);
                producer.send(jmsMessage, new CompletionListener() {
                    @Override
                    public void onCompletion(final Message jmsMessage) {
                        publishedMonitor.success(message);
                        log.debug("Message {} sent successfully.", jmsMessage);
                    }

                    @Override
                    public void onException(final Message messageFailedToSend, final Exception exception) {
                        handleSendException(message, exception, sender, publishedMonitor);
                    }
                });
            } else {
                // this happens when target address or 'reply-to' are set incorrectly.
                final String errorMessage = String.format("No producer available for target address '%s'",
                        publishTarget.getJmsDestination());
                publishedMonitor.exception(message, errorMessage);
                log.withCorrelationId(message.getInternalHeaders()).info(errorMessage);
                final MessageSendingFailedException sendFailedException = MessageSendingFailedException.newBuilder()
                        .message(errorMessage)
                        .description("Is the target or reply-to address correct?")
                        .dittoHeaders(message.getInternalHeaders())
                        .build();
                tellSenderForNonTwinEvents(message.getTopicPath().orElse(null), getSender(), sendFailedException);
            }
        } catch (final JMSException e) {
            handleSendException(message, e, getSender(), publishedMonitor);
        }
    }

    private void tellSenderForNonTwinEvents(@Nullable final TopicPath topicPath, final ActorRef sender,
            final DittoRuntimeException exception) {

        if (null == topicPath || !isTwinEvent(topicPath)) {
            sender.tell(exception, getSelf());
        } else {
            log.withCorrelationId(exception)
                    .info("Not sending back encountered DittoRuntimeException as topicPath of to-published " +
                                    "message <{}> was twin event. {}: {}", topicPath,
                            exception.getClass().getSimpleName(), exception.getMessage());
        }
    }

    private boolean isTwinEvent(final TopicPath tp) {
        return tp.getChannel() == TopicPath.Channel.TWIN && tp.getCriterion() == TopicPath.Criterion.EVENTS;
    }

    private void handleMessageInBackOffMode(final ExternalMessage message, final ConnectionMonitor publishedMonitor,
            final Destination destination) {
        final String errorMessage = String.format("Producer for target address '%s' is in back off mode, as the " +
                "target configuration seems to contain errors. The message will be dropped.", destination);
        publishedMonitor.exception(message, errorMessage);
        log.withCorrelationId(message.getInternalHeaders()).info(errorMessage);
        final MessageSendingFailedException sendFailedException = MessageSendingFailedException.newBuilder()
                .message(errorMessage)
                .description("Check if the target or the reply-to configuration is correct.")
                .dittoHeaders(message.getInternalHeaders())
                .build();
        tellSenderForNonTwinEvents(message.getTopicPath().orElse(null), getSender(), sendFailedException);
    }

    private void handleSendException(final ExternalMessage message, final Exception e, final ActorRef sender,
            final ConnectionMonitor publishedMonitor) {

        log.withCorrelationId(message.getInternalHeaders())
                .info("Failed to send JMS message: [{}] {}", e.getClass().getSimpleName(), e.getMessage());
        final MessageSendingFailedException sendFailedException = MessageSendingFailedException.newBuilder()
                .cause(e)
                .dittoHeaders(message.getInternalHeaders())
                .build();

        tellSenderForNonTwinEvents(message.getTopicPath().orElse(null), sender, sendFailedException);
        monitorSendFailure(message, sendFailedException, publishedMonitor);
    }

    private void monitorSendFailure(final ExternalMessage message, final Exception exception,
            final ConnectionMonitor publishedMonitor) {
        if (exception instanceof DittoRuntimeException) {
            publishedMonitor.failure(message, (DittoRuntimeException) exception);
        } else {
            publishedMonitor.exception(message, exception);
        }
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
        JMSPropertyMapper.setPropertiesAndApplicationProperties(message, externalMessage.getHeaders(), log);

        // wrap the message to prevent Qpid client from setting properties willy-nilly.
        return JMSMessageWorkaround.wrap((JmsMessage) message);
    }

    @Nullable
    private MessageProducer getProducer(final Destination destination) {
        final MessageProducer messageProducer;
        if (staticTargets.containsKey(destination)) {
            messageProducer = staticTargets.get(destination);
        } else {
            messageProducer = dynamicTargets.computeIfAbsent(destination, this::createProducer);
            maintainReplyToMap();
        }
        return messageProducer;
    }

    private void maintainReplyToMap() {
        // cache maintenance strategy = discard eldest
        while (dynamicTargets.size() > producerCacheSize) {
            final Map.Entry<Destination, MessageProducer> cachedProducer = dynamicTargets.entrySet().iterator().next();
            closeCachedProducer(cachedProducer);
            dynamicTargets.remove(cachedProducer.getKey());
        }
    }

    @Nullable
    private MessageProducer createProducer(final Destination destination) {
        log.debug("Creating AMQP Producer for '{}'", destination);
        try {
            return session.createProducer(destination);
        } catch (final JMSException e) {
            log.warning("Could not create producer for destination '{}': {}.",
                    destination, jmsExceptionToString(e));
            return null;
        }
    }

    private void closeCachedProducer(final Map.Entry<Destination, MessageProducer> cachedProducer) {
        try {
            final Destination target = cachedProducer.getKey();
            final MessageProducer producer = cachedProducer.getValue();
            log.debug("Closing AMQP Producer for '{}'", target);
            if (producer != null) {
                producer.close();
            } else {
                log.warning("Null producer in cache: connection=<{}>, address=<{}>", connectionId, target);
            }
        } catch (final JMSException jmsException) {
            log.debug("Closing producer failed (can be ignored if connection was closed already): {}",
                    jmsExceptionToString(jmsException));
        }
    }

    @Override
    public void postStop() throws Exception {
        super.postStop();
        dynamicTargets.entrySet().forEach(this::closeCachedProducer);
        staticTargets.entrySet().forEach(this::closeCachedProducer);
    }

    @Override
    protected DiagnosticLoggingAdapter log() {
        return log;
    }

    // create a target producer. the previous incarnation, if any, must be closed.
    private void createTargetProducer(final Destination destination) {
        try {
            staticTargets.put(destination, session.createProducer(destination));
            log.info("Target producer <{}> created", destination);
        } catch (final JMSException jmsException) {
            // target producer not creatable; stop self and request restart by parent
            final String errorMessage = String.format("Failed to create target '%s'", destination);
            log.error(jmsException, errorMessage);
            final ConnectionFailure failure = new ImmutableConnectionFailure(getSelf(), jmsException, errorMessage);
            getContext().getParent().tell(failure, getSelf());
            getContext().stop(getSelf());
        }
    }

    private void createStaticTargetProducers(final List<Target> targets) {
        // using loop so that already created targets are closed on exception
        for (final Target target : targets) {
            // only targets with static addresses should stay open
            if (!Placeholders.containsAnyPlaceholder(target.getAddress())) {
                createTargetProducer(toPublishTarget(target.getAddress()).getJmsDestination());
            }
        }
    }

    private static String jmsExceptionToString(final JMSException jmsException) {
        if (jmsException.getCause() != null) {
            return String.format("[%s] %s (cause: %s - %s)", jmsException.getErrorCode(), jmsException.getMessage(),
                    jmsException.getCause().getClass().getSimpleName(), jmsException.getCause().getMessage());
        }

        return String.format("[%s] %s", jmsException.getErrorCode(), jmsException.getMessage());
    }

    private static Stream<Map.Entry<Destination, MessageProducer>> findByValue(
            final Map<Destination, MessageProducer> producerMap, final MessageProducer value) {
        return producerMap.entrySet().stream().filter(entry -> Objects.equals(entry.getValue(), value));
    }

}
