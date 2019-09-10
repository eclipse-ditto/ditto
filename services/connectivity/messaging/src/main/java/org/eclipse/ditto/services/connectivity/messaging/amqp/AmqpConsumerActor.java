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
import java.time.Instant;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
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
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.Enforcement;
import org.eclipse.ditto.model.connectivity.ResourceStatus;
import org.eclipse.ditto.model.placeholders.EnforcementFactoryFactory;
import org.eclipse.ditto.model.placeholders.EnforcementFilterFactory;
import org.eclipse.ditto.model.placeholders.PlaceholderFactory;
import org.eclipse.ditto.services.connectivity.messaging.BaseConsumerActor;
import org.eclipse.ditto.services.connectivity.messaging.amqp.status.ConsumerClosedStatusReport;
import org.eclipse.ditto.services.connectivity.messaging.config.Amqp10Config;
import org.eclipse.ditto.services.connectivity.messaging.config.ConnectionConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.DittoConnectivityConfig;
import org.eclipse.ditto.services.connectivity.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.internal.ImmutableConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.internal.RetrieveAddressStatus;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageBuilder;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.config.InstanceIdentifierSupplier;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;

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
    private final EnforcementFilterFactory<Map<String, String>, CharSequence> headerEnforcementFilterFactory;

    // the configured throttling interval
    private final Duration throttlingInterval;
    // the configured maximum of messages per interval
    private final int throttlingLimit;
    // the state for message throttling
    private final AtomicReference<ThrottleState> throttleState;

    // Access to the actor who performs JMS tasks in own thread
    private final ActorRef jmsActor;
    private final Duration jmsActorAskTimeout;
    // data to reconstruct message consumer if something happens to it
    private ConsumerData consumerData;
    @Nullable
    private MessageConsumer messageConsumer;

    @SuppressWarnings("unused")
    private AmqpConsumerActor(final ConnectionId connectionId, final ConsumerData consumerData,
            final ActorRef messageMappingProcessor, final ActorRef jmsActor) {
        super(connectionId,
                checkNotNull(consumerData, "consumerData").getAddress(),
                messageMappingProcessor,
                consumerData.getSource().getAuthorizationContext(),
                consumerData.getSource().getHeaderMapping().orElse(null));
        final ConnectionConfig connectionConfig =
                DittoConnectivityConfig.of(
                        DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config()))
                        .getConnectionConfig();
        final Amqp10Config amqp10Config = connectionConfig.getAmqp10Config();
        this.messageConsumer = consumerData.getMessageConsumer();
        this.consumerData = consumerData;
        this.jmsActor = checkNotNull(jmsActor, "jmsActor");
        jmsActorAskTimeout = connectionConfig.getClientActorAskTimeout();

        throttlingInterval = amqp10Config.getConsumerThrottlingInterval();
        throttlingLimit = amqp10Config.getConsumerThrottlingLimit();
        throttleState = new AtomicReference<>(new ThrottleState(0L, 0));

        final Enforcement enforcement = consumerData.getSource().getEnforcement().orElse(null);

        headerEnforcementFilterFactory = enforcement != null ? EnforcementFactoryFactory
                .newEnforcementFilterFactory(enforcement, PlaceholderFactory.newHeadersPlaceholder()) :
                input -> null;
    }

    /**
     * Creates Akka configuration object {@link Props} for this {@code AmqpConsumerActor}.
     *
     * @param connectionId the connection id
     * @param consumerData the consumer data.
     * @param messageMappingProcessor the message mapping processor where received messages are forwarded to
     * @param jmsActor reference of the {@code JMSConnectionHandlingActor).
     * @return the Akka configuration Props object.
     */
    static Props props(final ConnectionId connectionId, final ConsumerData consumerData,
            final ActorRef messageMappingProcessor, final ActorRef jmsActor) {

        return Props.create(AmqpConsumerActor.class, connectionId, consumerData, messageMappingProcessor, jmsActor);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(RestartMessageConsumer.class, this::handleRestartMessageConsumer)
                .match(JmsMessage.class, this::handleJmsMessage)
                .match(ResourceStatus.class, this::handleAddressStatus)
                .match(RetrieveAddressStatus.class, ras -> getSender().tell(getCurrentSourceStatus(), getSelf()))
                .match(ConsumerClosedStatusReport.class, this::matchesOwnConsumer, this::handleConsumerClosed)
                .match(CreateMessageConsumerResponse.class, this::messageConsumerCreated)
                .match(Status.Failure.class, this::messageConsumerFailed)
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    @Override
    public void preStart() throws JMSException {
        initMessageConsumer();
    }

    @Override
    public void postStop() throws Exception {
        super.postStop();
        destroyMessageConsumer();
    }

    @Override
    public void onMessage(final Message message) {
        getSelf().tell(message, ActorRef.noSender());
        if (isThrottlingEnabled()) {
            throttleMessageConsumer();
        }
    }

    private void initMessageConsumer() throws JMSException {
        if (messageConsumer != null) {
            messageConsumer.setMessageListener(this);
            consumerData = consumerData.withMessageConsumer(messageConsumer);
        }
    }

    private void destroyMessageConsumer() {
        if (messageConsumer != null) {
            try {
                log.debug("Closing AMQP Consumer for '{}'", sourceAddress);
                messageConsumer.close();
            } catch (final JMSException jmsException) {
                log.debug("Closing consumer failed (can be ignored if connection was closed already): {}",
                        jmsException.getMessage());
            }
            messageConsumer = null;
        }
    }

    private void stopMessageConsumer() {
        if (messageConsumer != null) {
            ((JmsMessageConsumer) messageConsumer).stop();
        }
    }

    private void startMessageConsumer() {
        if (messageConsumer != null) {
            ((JmsMessageConsumer) messageConsumer).start();
        }
    }

    private boolean isThrottlingEnabled() {
        return throttlingInterval.toMillis() > 0 && throttlingLimit > 0;
    }

    private boolean matchesOwnConsumer(final ConsumerClosedStatusReport event) {
        return messageConsumer != null && messageConsumer.equals(event.getMessageConsumer());
    }

    private void handleConsumerClosed(final ConsumerClosedStatusReport event) {
        // consumer closed
        // update own status
        final ResourceStatus addressStatus = ConnectivityModelFactory.newStatusUpdate(
                InstanceIdentifierSupplier.getInstance().get(),
                ConnectivityStatus.FAILED,
                sourceAddress,
                "Consumer closed", Instant.now());
        handleAddressStatus(addressStatus);

        // destroy current message consumer in any case
        destroyMessageConsumer();

        /* ask JMSConnectionHandlingActor for a new consumer */
        final CreateMessageConsumer createMessageConsumer = new CreateMessageConsumer(consumerData);
        final CompletionStage<Object> responseFuture =
                Patterns.ask(jmsActor, createMessageConsumer, jmsActorAskTimeout)
                        .thenApply(response -> {
                            if (response instanceof Throwable) {
                                // create failed future so that Patterns.pipe sends me Status.Failure
                                throw new CompletionException((Throwable) response);
                            }
                            return response;
                        });
        Patterns.pipe(responseFuture, getContext().getDispatcher()).to(getSelf());
    }

    private void messageConsumerCreated(final CreateMessageConsumerResponse response) throws JMSException {
        if (consumerData.equals(response.consumerData)) {
            log.info("Consumer <{}> created", response.messageConsumer);
            destroyMessageConsumer();
            messageConsumer = response.messageConsumer;
            initMessageConsumer();
            resetResourceStatus();
        } else {
            // got an orphaned message consumer! this is an error.
            log.error("RESOURCE_LEAK! Got created MessageConsumer <{}> for <{}>, while I have <{}> for <{}>",
                    response.messageConsumer, response.consumerData, messageConsumer, consumerData);
        }
    }

    private void messageConsumerFailed(final Status.Failure failure) {
        // escalate to parent
        final ConnectionFailure connectionFailed = new ImmutableConnectionFailure(getSelf(), failure.cause(),
                "Failed to recreate closed message consumer");
        getContext().getParent().tell(connectionFailed, getSelf());
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
            // TODO: add monitoring logs after merge
            log.info("Stopping message consumer, message limit of {}/{} exceeded.", throttlingLimit,
                    throttlingInterval);
            stopMessageConsumer();
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
            startMessageConsumer();
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
            forwardToMappingActor(externalMessage, hashKey);
        } catch (final DittoRuntimeException e) {
            log.info("Got DittoRuntimeException '{}' when command was parsed: {}", e.getErrorCode(), e.getMessage());
            if (headers != null) {
                // forwarding to messageMappingProcessor only make sense if we were able to extract the headers,
                // because we need a reply-to address to send the error response
                inboundMonitor.failure(headers, e);
                forwardToMappingActor(e.setDittoHeaders(DittoHeaders.of(headers)), hashKey);
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

    /**
     * Demand a new message consumer be made for this actor.
     */
    static final class CreateMessageConsumer {

        private final ConsumerData consumerData;

        private CreateMessageConsumer(final ConsumerData consumerData) {
            this.consumerData = consumerData;
        }

        ConsumerData getConsumerData() {
            return consumerData;
        }

        Object toResponse(final MessageConsumer messageConsumer) {
            return new CreateMessageConsumerResponse(consumerData, messageConsumer);
        }
    }

    private static final class CreateMessageConsumerResponse {

        private final ConsumerData consumerData;
        private final MessageConsumer messageConsumer;

        private CreateMessageConsumerResponse(final ConsumerData consumerData, final MessageConsumer messageConsumer) {
            this.consumerData = consumerData;
            this.messageConsumer = messageConsumer;
        }
    }
}
