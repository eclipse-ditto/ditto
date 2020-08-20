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

import static org.apache.qpid.jms.message.JmsMessageSupport.ACCEPTED;
import static org.apache.qpid.jms.message.JmsMessageSupport.MODIFIED_FAILED;
import static org.apache.qpid.jms.message.JmsMessageSupport.MODIFIED_FAILED_UNDELIVERABLE;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

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
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.Enforcement;
import org.eclipse.ditto.model.connectivity.EnforcementFactoryFactory;
import org.eclipse.ditto.model.connectivity.EnforcementFilterFactory;
import org.eclipse.ditto.model.connectivity.ResourceStatus;
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
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.config.InstanceIdentifierSupplier;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.StartedTimer;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;

/**
 * Actor which receives message from an AMQP source and forwards them to a {@code MessageMappingProcessorActor}.
 */
final class AmqpConsumerActor extends BaseConsumerActor implements MessageListener, MessageRateLimiterBehavior<String> {

    /**
     * The name prefix of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME_PREFIX = "amqpConsumerActor-";

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
    private final EnforcementFilterFactory<Map<String, String>, CharSequence> headerEnforcementFilterFactory;

    private final MessageRateLimiter<String> messageRateLimiter;

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
                consumerData.getSource());
        final ConnectionConfig connectionConfig =
                DittoConnectivityConfig.of(
                        DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config()))
                        .getConnectionConfig();
        final Amqp10Config amqp10Config = connectionConfig.getAmqp10Config();
        this.messageConsumer = consumerData.getMessageConsumer();
        this.consumerData = consumerData;
        this.jmsActor = checkNotNull(jmsActor, "jmsActor");
        jmsActorAskTimeout = connectionConfig.getClientActorAskTimeout();

        messageRateLimiter = initMessageRateLimiter(amqp10Config);

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
        final Receive messageHandlingBehavior = ReceiveBuilder.create()
                .match(JmsMessage.class, this::handleJmsMessage)
                .match(ResourceStatus.class, this::handleAddressStatus)
                .match(RetrieveAddressStatus.class, ras -> getSender().tell(getCurrentSourceStatus(), getSelf()))
                .match(ConsumerClosedStatusReport.class, this::matchesOwnConsumer, this::handleConsumerClosed)
                .match(CreateMessageConsumerResponse.class, this::messageConsumerCreated)
                .match(Status.Failure.class, this::messageConsumerFailed)
                .build();
        final Receive rateLimiterBehavior = getRateLimiterBehavior();
        final Receive matchAnyBehavior = ReceiveBuilder.create()
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
        return messageHandlingBehavior
                .orElse(rateLimiterBehavior)
                .orElse(matchAnyBehavior);
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

    @Override
    public void stopMessageConsumerDueToRateLimit(final String reason) {
        inboundMonitor.getCounter().recordFailure();
        inboundMonitor.getLogger()
                .failure("Source <{0}> is rate-limited due to {1}.", sourceAddress, reason);
        stopMessageConsumer();
    }

    @Override
    public void startMessageConsumerDueToRateLimit() {
        inboundMonitor.getLogger()
                .success("Rate limit on source <{0}> is lifted.", sourceAddress);
        startMessageConsumer();
    }

    @Override
    public MessageRateLimiter<String> getMessageRateLimiter() {
        return messageRateLimiter;
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

    private void handleJmsMessage(final JmsMessage message) {
        final StartedTimer timer = DittoMetrics.expiringTimer("amqp_receive_to_ack").build();
        Map<String, String> headers = null;
        try {
            recordIncomingForRateLimit(message.getJMSMessageID());
            if (log.isDebugEnabled()) {
                log.debug("Received JmsMessage from AMQP 1.0: {} with Properties: {} and AckType {}",
                        message.toString(),
                        message.getAllPropertyNames().toString(), message.getAcknowledgeCallback().getAckType());
            }
            headers = extractHeadersMapFromJmsMessage(message);
            final ExternalMessageBuilder builder = ExternalMessageFactory.newExternalMessageBuilder(headers);
            final ExternalMessage externalMessage = extractPayloadFromMessage(message, builder)
                    .withAuthorizationContext(source.getAuthorizationContext())
                    .withEnforcement(headerEnforcementFilterFactory.getFilter(headers))
                    .withHeaderMapping(source.getHeaderMapping().orElse(null))
                    .withSourceAddress(sourceAddress)
                    .withPayloadMapping(consumerData.getSource().getPayloadMapping())
                    .build();
            inboundMonitor.success(externalMessage);
            final Map<String, String> externalMessageHeaders = externalMessage.getHeaders();
            LogUtil.enhanceLogWithCorrelationId(log,
                    externalMessage.findHeader(DittoHeaderDefinition.CORRELATION_ID.getKey()));
            log.info("Received message from AMQP 1.0 ({}): {}", externalMessageHeaders,
                    externalMessage.getTextPayload().orElse("binary"));
            forwardToMappingActor(externalMessage,
                    () -> acknowledge(message, true, false, timer, externalMessageHeaders),
                    redeliver -> acknowledge(message, false, redeliver, timer, externalMessageHeaders)
            );
        } catch (final DittoRuntimeException e) {
            log.info("Got DittoRuntimeException '{}' when command was parsed: {}", e.getErrorCode(), e.getMessage());
            if (headers != null) {
                // forwarding to messageMappingProcessor only make sense if we were able to extract the headers,
                // because we need a reply-to address to send the error response
                inboundMonitor.failure(headers, e);
                forwardToMappingActor(e.setDittoHeaders(DittoHeaders.of(headers)));
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
        }
    }

    /**
     * Acknowledge an incoming message with a given acknowledgement type.
     *
     * @param message The incoming message.
     * @param isSuccess Whether this ackType is considered a success.
     * @param redeliver whether redelivery should be requested.
     * @param timer timer to stop after acknowledging.
     * @param externalMessageHeaders used for logging to correlate ack with received log.
     */
    private void acknowledge(final JmsMessage message, final boolean isSuccess, final boolean redeliver,
            final StartedTimer timer, final Map<String, String> externalMessageHeaders) {
        try {
            final String messageId = message.getJMSMessageID();
            recordAckForRateLimit(messageId, isSuccess, redeliver);
            // Beware: JMS client may make JmsMessageSupport constants ACCEPTED, etc. package-private.
            final int ackType;
            final String ackTypeName;
            if (isSuccess) {
                ackType = ACCEPTED;
                ackTypeName = "accepted";
            } else {
                ackType = redeliver ? MODIFIED_FAILED : MODIFIED_FAILED_UNDELIVERABLE;
                ackTypeName = redeliver ? "modified[delivery-failed]" : "modified[delivery-failed,undeliverable-here]";
            }
            log.info("Acking <" + messageId +
                            "> with original external message headers=<{}>, isSuccess=<{}>, ackType=<{} {}>",
                    externalMessageHeaders,
                    isSuccess, ackType,
                    ackTypeName);
            message.getAcknowledgeCallback().setAckType(ackType);
            message.acknowledge();
            timer.tag("success", isSuccess).stop();
            if (isSuccess) {
                inboundAcknowledgedMonitor.getLogger().success("Sending acknowledgement {0}", ackTypeName);
            } else {
                inboundAcknowledgedMonitor.exception("Sending negative acknowledgement {0}.", ackTypeName);
            }
        } catch (final JMSException e) {
            log.error(e, "Failed to ack an AMQP message");
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
            if (log.isDebugEnabled()) {
                final Destination destination = message.getJMSDestination();
                final Map<String, String> headersMapFromJmsMessage = extractHeadersMapFromJmsMessage(message);
                log.debug("Received message at '{}' of unsupported type ({}) with headers: {}",
                        destination, message.getClass().getName(), headersMapFromJmsMessage);
            }
        }
        return builder;
    }

    private Map<String, String> extractHeadersMapFromJmsMessage(final JmsMessage message) {
        return JMSPropertyMapper.getPropertiesAndApplicationProperties(message);
    }

    @Override
    public DittoDiagnosticLoggingAdapter log() {
        return log;
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
