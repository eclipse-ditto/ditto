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

import static org.apache.qpid.jms.message.JmsMessageSupport.ACCEPTED;
import static org.apache.qpid.jms.message.JmsMessageSupport.MODIFIED_FAILED;
import static org.apache.qpid.jms.message.JmsMessageSupport.REJECTED;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.connectivity.service.EnforcementFactoryFactory.newEnforcementFilterFactory;
import static org.eclipse.ditto.placeholders.PlaceholderFactory.newHeadersPlaceholder;

import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
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

import org.apache.qpid.jms.JmsAcknowledgeCallback;
import org.apache.qpid.jms.JmsMessageConsumer;
import org.apache.qpid.jms.message.JmsMessage;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageBuilder;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.Enforcement;
import org.eclipse.ditto.connectivity.model.EnforcementFilterFactory;
import org.eclipse.ditto.connectivity.model.ResourceStatus;
import org.eclipse.ditto.connectivity.service.config.Amqp10Config;
import org.eclipse.ditto.connectivity.service.config.ConnectionConfig;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.ConnectivityStatusResolver;
import org.eclipse.ditto.connectivity.service.messaging.LegacyBaseConsumerActor;
import org.eclipse.ditto.connectivity.service.messaging.amqp.status.ConsumerClosedStatusReport;
import org.eclipse.ditto.connectivity.service.messaging.backoff.BackOffActor;
import org.eclipse.ditto.connectivity.service.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.connectivity.service.messaging.internal.RetrieveAddressStatus;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.InfoProviderFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.config.InstanceIdentifierSupplier;
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
import org.eclipse.ditto.internal.utils.tracing.span.SpanOperationName;
import org.eclipse.ditto.internal.utils.tracing.span.TracingSpans;

import akka.Done;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import akka.stream.javadsl.Sink;

/**
 * Actor which receives message from an AMQP source and forwards them to a {@code MessageMappingProcessorActor}.
 */
final class AmqpConsumerActor extends LegacyBaseConsumerActor
        implements MessageListener, MessageRateLimiterBehavior<String> {

    /**
     * The name prefix of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME_PREFIX = "amqpConsumerActor-";

    private final EnforcementFilterFactory<Map<String, String>, Signal<?>> headerEnforcementFilterFactory;
    private final ActorRef backOffActor;

    private final MessageRateLimiter<String> messageRateLimiter;

    // Access to the actor who performs JMS tasks in own thread
    private final ActorRef jmsActor;
    private final Duration jmsActorAskTimeout;
    // data to reconstruct message consumer if something happens to it
    private ConsumerData consumerData;
    @Nullable
    private MessageConsumer messageConsumer;

    @SuppressWarnings("unused")
    private AmqpConsumerActor(final Connection connection, final ConsumerData consumerData,
            final Sink<Object, ?> inboundMappingSink, final ActorRef jmsActor,
            final ConnectivityStatusResolver connectivityStatusResolver,
            final ConnectivityConfig connectivityConfig) {
        super(connection,
                checkNotNull(consumerData, "consumerData").getAddress(),
                inboundMappingSink,
                consumerData.getSource(),
                connectivityStatusResolver,
                connectivityConfig);

        final ConnectionConfig connectionConfig = connectivityConfig.getConnectionConfig();
        final Amqp10Config amqp10Config = connectionConfig.getAmqp10Config();
        this.messageConsumer = consumerData.getMessageConsumer();
        this.consumerData = consumerData;
        this.jmsActor = checkNotNull(jmsActor, "jmsActor");
        jmsActorAskTimeout = connectionConfig.getClientActorAskTimeout();

        // the amqp consumer is OPEN (ready to handle messages) after setMessageListener() was called successfully
        handleAddressStatus(ConnectivityModelFactory.newSourceStatus(InstanceIdentifierSupplier.getInstance().get(),
                ConnectivityStatus.UNKNOWN, sourceAddress, "Consumer is being initialized.", Instant.now()));

        messageRateLimiter = initMessageRateLimiter(amqp10Config);
        backOffActor = getContext().actorOf(BackOffActor.props(amqp10Config.getBackOffConfig()));

        final Enforcement enforcement = consumerData.getSource().getEnforcement().orElse(null);
        headerEnforcementFilterFactory = enforcement != null
                ? newEnforcementFilterFactory(enforcement, newHeadersPlaceholder())
                : input -> null;
    }

    /**
     * Creates Akka configuration object {@link Props} for this {@code AmqpConsumerActor}.
     *
     * @param connection the connection
     * @param consumerData the consumer data.
     * @param inboundMappingSink the message mapping sink where received messages are forwarded to
     * @param jmsActor reference of the {@code JMSConnectionHandlingActor}.
     * @param connectivityStatusResolver connectivity status resolver to resolve occurred exceptions to a connectivity
     * status.
     * @param connectivityConfig the connectivity config related to the given connection.
     * @return the Akka configuration Props object.
     */
    static Props props(final Connection connection, final ConsumerData consumerData,
            final Sink<Object, ?> inboundMappingSink, final ActorRef jmsActor,
            final ConnectivityStatusResolver connectivityStatusResolver,
            final ConnectivityConfig connectivityConfig) {

        return Props.create(AmqpConsumerActor.class, connection, consumerData, inboundMappingSink, jmsActor,
                connectivityStatusResolver, connectivityConfig);
    }

    @Override
    public Receive createReceive() {
        final Receive messageHandlingBehavior = ReceiveBuilder.create()
                .match(JmsMessage.class, this::handleJmsMessage)
                .match(ResourceStatus.class, this::handleAddressStatus)
                .match(RetrieveAddressStatus.class, ras -> getSender().tell(getCurrentSourceStatus(), getSelf()))
                .matchEquals(Control.CREATE_CONSUMER, this::createMessageConsumer)
                .matchEquals(Control.STOP_CONSUMER, this::stopConsumerOnRequest)
                .match(ConsumerClosedStatusReport.class, this::matchesOwnConsumer, this::handleConsumerClosed)
                .match(ConsumerClosedStatusReport.class, this::handleNonMatchingConsumerClosed)
                .match(CreateMessageConsumerResponse.class, this::messageConsumerCreated)
                .match(Status.Failure.class, this::messageConsumerFailed)
                .build();
        final Receive rateLimiterBehavior = getRateLimiterBehavior();
        final Receive matchAnyBehavior = ReceiveBuilder.create()
                .matchAny(m -> {
                    logger.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
        return messageHandlingBehavior
                .orElse(rateLimiterBehavior)
                .orElse(matchAnyBehavior);
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
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

    private void stopConsumerOnRequest(final Control stopConsumer) {
        stopMessageConsumer();
        getSender().tell(Done.getInstance(), getSelf());
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
        inboundMonitor.getLogger().failure("Source <{0}> is rate-limited due to {1}", sourceAddress, reason);
        stopMessageConsumer();
    }

    @Override
    public void startMessageConsumerDueToRateLimit() {
        inboundMonitor.getLogger().success("Rate limit on source <{0}> is lifted", sourceAddress);
        startMessageConsumer();
    }

    @Override
    public MessageRateLimiter<String> getMessageRateLimiter() {
        return messageRateLimiter;
    }

    private void initMessageConsumer() {
        try {
            if (messageConsumer != null) {
                messageConsumer.setMessageListener(this);
                consumerData = consumerData.withMessageConsumer(messageConsumer);
                resetResourceStatus();
            }
        } catch (final Exception e) {
            final ResourceStatus resourceStatus =
                    ConnectivityModelFactory.newStatusUpdate(InstanceIdentifierSupplier.getInstance().get(),
                            connectivityStatusResolver.resolve(e), sourceAddress,
                            "Initialization of message consumer failed: " + e.getMessage(),
                            Instant.now());
            handleAddressStatus(resourceStatus);
            final var failure = ConnectionFailure.of(getSelf(), e, "Failed to initialize message consumers.");
            getContext().getParent().tell(failure, getSelf());
        }
    }

    private void destroyMessageConsumer() {
        if (messageConsumer != null) {
            try {
                logger.debug("Closing AMQP Consumer for '{}'", sourceAddress);
                messageConsumer.close();
            } catch (final JMSException jmsException) {
                logger.debug("Closing consumer failed (can be ignored if connection was closed already): {}",
                        jmsException.getMessage());
            }
            messageConsumer = null;
        }
    }

    private boolean matchesOwnConsumer(final ConsumerClosedStatusReport event) {
        return messageConsumer != null && messageConsumer.equals(event.getMessageConsumer());
    }

    private void handleNonMatchingConsumerClosed(final ConsumerClosedStatusReport event) {
        logger.debug("Received ConsumerClosedStatusReport which is handled by another consumer actor. Ignoring.");
    }

    private void handleConsumerClosed(final ConsumerClosedStatusReport event) {
        // consumer closed
        // update own status
        final String statusDetails = buildStatusDetailsFromStatusReport(event);
        final ResourceStatus addressStatus =
                ConnectivityModelFactory.newStatusUpdate(InstanceIdentifierSupplier.getInstance().get(),
                        connectivityStatusResolver.resolve(event.getCause()), sourceAddress, statusDetails,
                        Instant.now());
        handleAddressStatus(addressStatus);

        // destroy current message consumer in any case
        destroyMessageConsumer();

        logger.info("Consumer for destination '{}' was closed. Will try to recreate after some backoff.",
                sourceAddress);
        backOffActor.tell(BackOffActor.createBackOffWithAnswerMessage(Control.CREATE_CONSUMER),
                getSelf());
    }

    private String buildStatusDetailsFromStatusReport(final ConsumerClosedStatusReport event) {
        if (event.getCause() != null) {
            final Throwable cause = event.getCause();
            final String causeClass = cause.getClass().getName();
            final String causeMessage = cause.getMessage();
            return String.format("Consumer closed. Cause: [%s] %s", causeClass, causeMessage);
        } else {
            return "Consumer closed.";
        }
    }

    private void createMessageConsumer(final Control createConsumer) {
        logger.debug("Trying to create consumer for destination '{}'.", sourceAddress);
        /* ask JMSConnectionHandlingActor for a new consumer */
        final CreateMessageConsumer createMessageConsumer = new CreateMessageConsumer(consumerData);
        final CompletionStage<Object> responseFuture =
                Patterns.ask(jmsActor, createMessageConsumer, jmsActorAskTimeout)
                        .thenApply(response -> {
                            if (response instanceof Throwable throwable) {
                                // create failed future so that Patterns.pipe sends me Status.Failure
                                throw new CompletionException(throwable);
                            }
                            return response;
                        });
        Patterns.pipe(responseFuture, getContext().getDispatcher()).to(getSelf());
    }

    private void messageConsumerCreated(final CreateMessageConsumerResponse response) {
        if (consumerData.equals(response.consumerData)) {
            logger.info("Consumer for destination '{}' created.", sourceAddress);
            destroyMessageConsumer();
            messageConsumer = response.messageConsumer;
            initMessageConsumer();
        } else {
            // got an orphaned message consumer! this is an error.
            logger.error("RESOURCE_LEAK! Got created MessageConsumer <{}> for <{}>, while I have <{}> for <{}>",
                    response.messageConsumer, response.consumerData, messageConsumer, consumerData);
        }
    }

    private void messageConsumerFailed(final Status.Failure failure) {
        // escalate to parent
        final ConnectionFailure connectionFailed = ConnectionFailure.of(getSelf(), failure.cause(),
                "Failed to recreate closed message consumer");
        getContext().getParent().tell(connectionFailed, getSelf());
        final ResourceStatus addressStatus =
                ConnectivityModelFactory.newStatusUpdate(InstanceIdentifierSupplier.getInstance().get(),
                        connectivityStatusResolver.resolve(failure.cause()), sourceAddress,
                        "Failed to recreate closed message consumer.",
                        Instant.now());
        handleAddressStatus(addressStatus);
    }

    private void handleJmsMessage(final JmsMessage message) {
        Map<String, String> headers = null;
        String correlationId = null;
        var startedSpan = TracingSpans.emptyStartedSpan(SpanOperationName.of("amqp_consume"));
        try {
            recordIncomingForRateLimit(message.getJMSMessageID());
            if (logger.isDebugEnabled()) {
                final Integer ackType = Optional.ofNullable(message.getAcknowledgeCallback())
                        .map(JmsAcknowledgeCallback::getAckType)
                        .orElse(null);
                logger.debug("Received JmsMessage from AMQP 1.0: {} with Properties: {} and AckType {}",
                        message.toString(),
                        message.getAllPropertyNames(),
                        ackType);
            }
            headers = extractHeadersMapFromJmsMessage(message);
            correlationId = headers.get(DittoHeaderDefinition.CORRELATION_ID.getKey());
            startedSpan = DittoTracing.newPreparedSpan(headers, startedSpan.getOperationName())
                    .correlationId(correlationId)
                    .connectionId(connectionId)
                    .start();
            headers = startedSpan.propagateContext(headers);
            final ExternalMessageBuilder builder = ExternalMessageFactory.newExternalMessageBuilder(headers);
            final ExternalMessage externalMessage = extractPayloadFromMessage(message, builder, correlationId)
                    .withAuthorizationContext(source.getAuthorizationContext())
                    .withEnforcement(headerEnforcementFilterFactory.getFilter(headers))
                    .withHeaderMapping(source.getHeaderMapping())
                    .withSourceAddress(sourceAddress)
                    .withPayloadMapping(consumerData.getSource().getPayloadMapping())
                    .build();
            inboundMonitor.success(externalMessage);
            final Map<String, String> externalMessageHeaders = externalMessage.getHeaders();
            logger.withCorrelationId(correlationId)
                    .info("Received message from AMQP 1.0 with externalMessageHeaders: {}", externalMessageHeaders);
            if (logger.isDebugEnabled()) {
                logger.withCorrelationId(correlationId).debug("Received message from AMQP 1.0 with payload: {}",
                        externalMessage.getTextPayload().orElse("binary"));
            }
            forwardToMapping(externalMessage,
                    () -> acknowledge(message, true, false, externalMessageHeaders),
                    redeliver -> acknowledge(message, false, redeliver, externalMessageHeaders)
            );
        } catch (final DittoRuntimeException e) {
            logger.withCorrelationId(e)
                    .info("Got DittoRuntimeException '{}' when command was parsed: {}", e.getErrorCode(),
                            e.getMessage());
            startedSpan.tagAsFailed(e);
            if (headers != null) {
                // forwarding to messageMappingProcessor only make sense if we were able to extract the headers,
                // because we need a reply-to address to send the error response
                inboundMonitor.failure(headers, e);
                forwardToMapping(e.setDittoHeaders(DittoHeaders.of(headers)));
            } else {
                inboundMonitor.failure(e);
            }
        } catch (final Exception e) {
            if (null != headers) {
                inboundMonitor.exception(headers, e);
            } else {
                inboundMonitor.exception(e);
            }
            startedSpan.tagAsFailed(e);
            logger.withCorrelationId(correlationId)
                    .error(e, "Unexpected {}: {}", e.getClass().getName(), e.getMessage());
        } finally {
            startedSpan.finish();
        }
    }

    /**
     * Acknowledge an incoming message with a given acknowledgement type.
     *
     * @param message The incoming message.
     * @param isSuccess Whether this ackType is considered a success.
     * @param redeliver whether redelivery should be requested.
     * @param externalMessageHeaders used for logging to correlate ack with received log.
     */
    private void acknowledge(final JmsMessage message, final boolean isSuccess, final boolean redeliver,
            final Map<String, String> externalMessageHeaders) {

        final Optional<String> correlationId = Optional.ofNullable(
                externalMessageHeaders.get(DittoHeaderDefinition.CORRELATION_ID.getKey()));
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
                ackType = redeliver ? MODIFIED_FAILED : REJECTED;
                ackTypeName = redeliver ? "modified[delivery-failed]" : "rejected";
            }
            final String jmsCorrelationID = message.getJMSCorrelationID();
            logger.withCorrelationId(correlationId.orElse(jmsCorrelationID))
                    .info(MessageFormat.format(
                            "Acking <{0}> with original external message headers=<{1}>, isSuccess=<{2}>, ackType=<{3} {4}>",
                            messageId,
                            externalMessageHeaders,
                            isSuccess,
                            ackType,
                            ackTypeName)
                    );
            message.getAcknowledgeCallback().setAckType(ackType);
            message.acknowledge();
            if (isSuccess) {
                inboundAcknowledgedMonitor.success(InfoProviderFactory.forHeaders(externalMessageHeaders),
                        "Sending success acknowledgement: <{0}>", ackTypeName);
            } else {
                inboundAcknowledgedMonitor.exception(externalMessageHeaders,
                        "Sending negative acknowledgement: <{0}>", ackTypeName);
            }
        } catch (final IllegalStateException e) {
            logger.withCorrelationId(correlationId.orElse(null))
                    .warning(e, "Failed to ack an AMQP message because of server side issues");
        } catch (final Exception e) {
            logger.withCorrelationId(correlationId.orElse(null)).error(e, "Failed to ack an AMQP message");
        }
    }

    private ExternalMessageBuilder extractPayloadFromMessage(final JmsMessage message,
            final ExternalMessageBuilder builder, @Nullable final String correlationId) throws JMSException {
        if (message instanceof TextMessage textMessage) {
            final String payload = textMessage.getText();
            if (payload == null) {
                builder.withText(null);
            } else {
                builder.withTextAndBytes(payload, payload.getBytes());
            }
        } else if (message instanceof BytesMessage bytesMessage) {
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
            if (logger.isDebugEnabled()) {
                final Destination destination = message.getJMSDestination();
                final Map<String, String> headersMapFromJmsMessage = extractHeadersMapFromJmsMessage(message);
                logger.withCorrelationId(correlationId)
                        .debug("Received message at '{}' of unsupported type ({}) with headers: {}",
                                destination, message.getClass().getName(), headersMapFromJmsMessage);
            }
        }
        return builder;
    }

    private static Map<String, String> extractHeadersMapFromJmsMessage(final JmsMessage message) {
        return JMSPropertyMapper.getHeadersFromProperties(message);
    }

    @Override
    public ThreadSafeDittoLoggingAdapter log() {
        return logger;
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

    /**
     * Actor control messages.
     */
    enum Control {
        /**
         * Triggers creation of a new message consumer.
         */
        CREATE_CONSUMER,

        /**
         * Triggers stopping of the message consumer.
         */
        STOP_CONSUMER
    }
}
