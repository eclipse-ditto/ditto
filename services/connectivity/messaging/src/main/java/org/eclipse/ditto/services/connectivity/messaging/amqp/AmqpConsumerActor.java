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

import static java.util.stream.Collectors.toMap;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;
import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

import org.apache.qpid.jms.JmsMessageConsumer;
import org.apache.qpid.jms.message.JmsMessage;
import org.apache.qpid.jms.message.facade.JmsMessageFacade;
import org.apache.qpid.jms.provider.amqp.message.AmqpJmsMessageFacade;
import org.apache.qpid.proton.amqp.Symbol;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Enforcement;
import org.eclipse.ditto.model.connectivity.ResourceStatus;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.placeholders.EnforcementFactoryFactory;
import org.eclipse.ditto.model.placeholders.EnforcementFilterFactory;
import org.eclipse.ditto.model.placeholders.PlaceholderFactory;
import org.eclipse.ditto.services.connectivity.messaging.BaseConsumerActor;
import org.eclipse.ditto.services.connectivity.messaging.config.Amqp10Config;
import org.eclipse.ditto.services.connectivity.messaging.config.DittoConnectivityConfig;
import org.eclipse.ditto.services.connectivity.messaging.internal.RetrieveAddressStatus;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageBuilder;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.routing.ConsistentHashingRouter;

/**
 * Actor which receives message from an AMQP source and forwards them to a {@code MessageMappingProcessorActor}.
 */
final class AmqpConsumerActor extends BaseConsumerActor implements MessageListener {

    /**
     * The name prefix of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME_PREFIX = "amqpConsumerActor-";
    private static final String RESTART_MESSAGE_CONSUMER = "restartMessageConsumer";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final MessageConsumer messageConsumer;
    private final EnforcementFilterFactory<Map<String, String>, String> headerEnforcementFilterFactory;

    // the configured throttling interval
    private final Duration throttlingInterval;
    // the configured maximum of messages per interval
    private final int throttlingLimit;
    // the state for message throttling
    private final AtomicReference<ThrottleState> throttleState;

    @SuppressWarnings("unused")
    private AmqpConsumerActor(final String connectionId, final String sourceAddress,
            final MessageConsumer messageConsumer,
            final ActorRef messageMappingProcessor, final Source source) {
        super(connectionId, sourceAddress, messageMappingProcessor, source.getAuthorizationContext(),
                source.getHeaderMapping().orElse(null));
        this.messageConsumer = checkNotNull(messageConsumer);
        checkNotNull(source, "source");

        final Amqp10Config amqp10Config = DittoConnectivityConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config())
        ).getConnectionConfig().getAmqp10Config();
        throttlingInterval = amqp10Config.getConsumerThrottlingInterval();
        throttlingLimit = amqp10Config.getConsumerThrottlingLimit();
        throttleState = new AtomicReference<>(new ThrottleState(0L, 0));

        final Enforcement enforcement = source.getEnforcement().orElse(null);

        headerEnforcementFilterFactory = enforcement != null ? EnforcementFactoryFactory
                .newEnforcementFilterFactory(enforcement, PlaceholderFactory.newHeadersPlaceholder()) :
                input -> null;
    }

    /**
     * Creates Akka configuration object {@link Props} for this {@code AmqpConsumerActor}.
     *
     * @param connectionId the connection id
     * @param sourceAddress the source address of messages
     * @param messageConsumer the JMS message consumer
     * @param messageMappingProcessor the message mapping processor where received messages are forwarded to
     * @param source the Source if the consumer
     * @return the Akka configuration Props object.
     */
    static Props props(final String connectionId, final String sourceAddress,
            final MessageConsumer messageConsumer,
            final ActorRef messageMappingProcessor, final Source source) {

        return Props.create(AmqpConsumerActor.class, connectionId, sourceAddress, messageConsumer,
                messageMappingProcessor, source);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(RestartMessageConsumer.class, this::handleRestartMessageConsumer)
                .match(JmsMessage.class, this::handleJmsMessage)
                .match(ResourceStatus.class, this::handleAddressStatus)
                .match(RetrieveAddressStatus.class, ras -> getSender().tell(getCurrentSourceStatus(), getSelf()))
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    @Override
    public void preStart() throws JMSException {
        messageConsumer.setMessageListener(this);
    }

    @Override
    public void postStop() throws Exception {
        super.postStop();
        try {
            log.debug("Closing AMQP Consumer for '{}'", sourceAddress);
            messageConsumer.close();
        } catch (final JMSException jmsException) {
            log.debug("Closing consumer failed (can be ignored if connection was closed already): {}",
                    jmsException.getMessage());
        }
    }

    @Override
    public void onMessage(final Message message) {
        getSelf().tell(message, ActorRef.noSender());
        if (isThrottlingEnabled()) {
            throttleMessageConsumer();
        }
    }

    private boolean isThrottlingEnabled() {
        return throttlingInterval.toMillis() > 0 && throttlingLimit > 0;
    }

    /**
     * Tracks the number of messages consumed within the last {@code throttlingInterval}. If the number exceeds the
     * configured {@code throttlingLimit} the messageConsumer is stopped and scheduled to be restarted after the
     * current interval. The method is always called from the same JMS dispatcher thread.
     */
    private void throttleMessageConsumer() {
        final long interval = System.currentTimeMillis() / throttlingInterval.toMillis();
        final ThrottleState state = throttleState.updateAndGet(previousState -> {
            final int nextMessages;
            if (interval == previousState.currentInterval) {
                nextMessages = previousState.currentMessagePerInterval + 1;
            } else {
                nextMessages = 1;
            }
            return new ThrottleState(interval, nextMessages);
        });
        if (state.currentMessagePerInterval >= throttlingLimit) {
            log.info("Stopping message consumer, message limit of {}/{} exceeded.", throttlingLimit,
                    throttlingInterval);
            ((JmsMessageConsumer) messageConsumer).stop();
            // calculate timestamp of next interval when the consumer should be restarted
            final long restartConsumerAt = (interval + 1) * throttlingInterval.toMillis();
            getSelf().tell(new RestartMessageConsumer(restartConsumerAt), ActorRef.noSender());
        }
    }

    /**
     * Restarts the message consumer either immediately or schedules the restart of the consumer with some delay.
     *
     * @param restartMessageConsumer the message signalling that we should restart the consumer
     */
    private void handleRestartMessageConsumer(final RestartMessageConsumer restartMessageConsumer) {
        final long delay = restartMessageConsumer.getRestartAt() - System.currentTimeMillis();
        if (delay <= 25) { // restart message consumer immediately if delay is negative or too small to schedule
            log.debug("Restarting message consumer.");
            ((JmsMessageConsumer) messageConsumer).start();
        } else { // otherwise schedule restarting of consumer
            log.debug("Scheduling restart of message consumer after {}ms.", delay);
            getTimers().startSingleTimer(RESTART_MESSAGE_CONSUMER, restartMessageConsumer, Duration.ofMillis(delay));
        }
    }

    private void handleJmsMessage(final JmsMessage message) {
        Map<String, String> headers = null;
        String hashKey = "";
        try {
            hashKey = message.getJMSDestination() != null ? message.getJMSDestination().toString() : sourceAddress;
            headers = extractHeadersMapFromJmsMessage(message);
            final ExternalMessageBuilder builder = ExternalMessageFactory.newExternalMessageBuilder(headers);
            final ExternalMessage externalMessage = extractPayloadFromMessage(message, builder)
                    .withAuthorizationContext(authorizationContext)
                    .withEnforcement(headerEnforcementFilterFactory.getFilter(headers)).withHeaderMapping(headerMapping)
                    .withSourceAddress(sourceAddress)
                    .build();
            inboundMonitor.success(externalMessage);

            LogUtil.enhanceLogWithCorrelationId(log,
                    externalMessage.findHeader(DittoHeaderDefinition.CORRELATION_ID.getKey()));
            if (log.isDebugEnabled()) {
                log.debug("Received message from AMQP 1.0 ({}): {}", externalMessage.getHeaders(),
                        externalMessage.getTextPayload().orElse("binary"));
            }
            final Object msg = new ConsistentHashingRouter.ConsistentHashableEnvelope(externalMessage, hashKey);
            messageMappingProcessor.forward(msg, getContext());
        } catch (final DittoRuntimeException e) {
            log.info("Got DittoRuntimeException '{}' when command was parsed: {}", e.getErrorCode(), e.getMessage());
            if (headers != null) {
                // forwarding to messageMappingProcessor only make sense if we were able to extract the headers,
                // because we need a reply-to address to send the error response
                inboundMonitor.failure(headers, e);
                final Object msg = new ConsistentHashingRouter.ConsistentHashableEnvelope(
                        e.setDittoHeaders(DittoHeaders.of(headers)), hashKey);
                messageMappingProcessor.forward(msg, getContext());
            } else {
                inboundMonitor.failure(e);
            }
        } catch (final Exception e) {
            if (null != headers) {
                inboundMonitor.exception(headers, e);
            } else {
                inboundMonitor.exception(e);
            }

            log.error(e, "Unexpected {}: {}", e.getClass().getName(), e.getMessage());
        } finally {
            try {
                // we use the manual acknowledge mode so we always have to ack the message
                message.acknowledge();
            } catch (final JMSException e) {
                log.error(e, "Failed to ack an AMQP message");
            }
        }
    }

    private ExternalMessageBuilder extractPayloadFromMessage(final JmsMessage message,
            final ExternalMessageBuilder builder) throws JMSException {
        if (message instanceof TextMessage) {
            final String payload = ((TextMessage) message).getText();
            builder.withText(payload);
        } else if (message instanceof BytesMessage) {
            final BytesMessage bytesMessage = (BytesMessage) message;
            final long bodyLength = bytesMessage.getBodyLength();
            if (bodyLength >= Integer.MIN_VALUE && bodyLength <= Integer.MAX_VALUE) {
                final int length = (int) bodyLength;
                final ByteBuffer byteBuffer = ByteBuffer.allocate(length);
                bytesMessage.readBytes(byteBuffer.array());
                builder.withBytes(byteBuffer);
            } else {
                throw new IllegalArgumentException("Message too large...");
            }
        } else {
            final Destination destination = message.getJMSDestination();
            final Map<String, String> headersMapFromJmsMessage = extractHeadersMapFromJmsMessage(message);
            log.debug("Received message at '{}' of unsupported type ({}) with headers: {}",
                    destination, message.getClass().getName(), headersMapFromJmsMessage);
        }
        return builder;
    }

    private Map<String, String> extractHeadersMapFromJmsMessage(final JmsMessage message) throws JMSException {

        final Map<String, String> headersFromJmsProperties;

        final JmsMessageFacade facade = message.getFacade();
        if (facade instanceof AmqpJmsMessageFacade) {
            final AmqpJmsMessageFacade amqpJmsMessageFacade = (AmqpJmsMessageFacade) facade;
            final Set<String> names =
                    amqpJmsMessageFacade.getApplicationPropertyNames(amqpJmsMessageFacade.getPropertyNames());
            headersFromJmsProperties = new HashMap<>(names.stream()
                    .map(key -> getPropertyAsEntry(amqpJmsMessageFacade, key))
                    .filter(Objects::nonNull)
                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));

            final Symbol contentType = amqpJmsMessageFacade.getContentType();
            if (null != contentType) {
                headersFromJmsProperties.put(ExternalMessage.CONTENT_TYPE_HEADER, contentType.toString());
            }
        } else {
            throw new JMSException("Message facade was not of type AmqpJmsMessageFacade");
        }

        final String replyTo = message.getJMSReplyTo() != null ? String.valueOf(message.getJMSReplyTo()) : null;
        if (replyTo != null) {
            headersFromJmsProperties.put(ExternalMessage.REPLY_TO_HEADER, replyTo);
        }

        final String jmsCorrelationId = message.getJMSCorrelationID() != null ? message.getJMSCorrelationID() :
                message.getJMSMessageID();
        if (jmsCorrelationId != null) {
            headersFromJmsProperties.put(DittoHeaderDefinition.CORRELATION_ID.getKey(), jmsCorrelationId);
        }

        return headersFromJmsProperties;
    }

    @Nullable
    private Map.Entry<String, String> getPropertyAsEntry(final AmqpJmsMessageFacade message, final String key) {
        try {
            final Object applicationProperty = message.getApplicationProperty(key);
            if (applicationProperty != null) {
                return new AbstractMap.SimpleImmutableEntry<>(key, applicationProperty.toString());
            } else {
                log.debug("Property '{}' was null", key);
                return null;
            }
        } catch (final JMSException e) {
            log.debug("Property '{}' could not be read, dropping...", key);
            return null;
        }
    }

    private static final class RestartMessageConsumer {

        private long restartAt;

        private RestartMessageConsumer(final long restartAt) {
            this.restartAt = restartAt;
        }

        private long getRestartAt() {
            return restartAt;
        }
    }

    private static final class ThrottleState {

        private final long currentInterval;
        private final int currentMessagePerInterval;

        private ThrottleState(final long currentInterval, final int currentMessagePerInterval) {
            this.currentInterval = currentInterval;
            this.currentMessagePerInterval = currentMessagePerInterval;
        }
    }
}
