/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq;

import static org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttHeader.MQTT_QOS;
import static org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttHeader.MQTT_RETAIN;
import static org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttHeader.MQTT_TOPIC;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.OutboundSignal;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.GenericTarget;
import org.eclipse.ditto.connectivity.model.MessageSendingFailedException;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.BasePublisherActor;
import org.eclipse.ditto.connectivity.service.messaging.ConnectivityStatusResolver;
import org.eclipse.ditto.connectivity.service.messaging.SendResult;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.AbstractMqttValidator;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttPublishTarget;

import com.hivemq.client.mqtt.datatypes.MqttQos;

import akka.japi.pf.ReceiveBuilder;
import akka.stream.Materializer;
import akka.stream.OverflowStrategy;
import akka.stream.QueueOfferResult;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.SourceQueueWithComplete;

/**
 * Common implementation for MQTT3 and MQTT5 publisher actors.
 *
 * @param <P> type of PUBLISH messages.
 * @param <R> type of replies of PUBLISH messages as encapsulated by the MQTT client.
 */
abstract class AbstractMqttPublisherActor<P, R> extends BasePublisherActor<MqttPublishTarget> {

    // for target the default is qos=0 because we have qos=0 all over the akka cluster
    private static final int DEFAULT_TARGET_QOS = 0;
    private static final String TOO_MANY_IN_FLIGHT_MESSAGE_DESCRIPTION = "This can have the following reasons:\n" +
            "a) The MQTT broker does not consume the messages fast enough.\n" +
            "b) The client count of this connection is not configured high enough.";

    private final Function<P, CompletableFuture<R>> client;
    private final boolean dryRun;
    private final SourceQueueWithComplete<MqttSendingContext<P>> sourceQueue;

    AbstractMqttPublisherActor(final Connection connection,
            final Function<P, CompletableFuture<R>> client,
            final boolean dryRun,
            final ConnectivityStatusResolver connectivityStatusResolver,
            final ConnectivityConfig connectivityConfig) {

        super(connection, connectivityStatusResolver, connectivityConfig);
        this.client = client;
        this.dryRun = dryRun;
        final Materializer materializer = Materializer.createMaterializer(this::getContext);
        final int maxQueueSize = connectionConfig.getMqttConfig().getMaxQueueSize();
        sourceQueue = Source.<MqttSendingContext<P>>queue(maxQueueSize, OverflowStrategy.dropNew())
                .to(Sink.foreach(this::publishMqttMessage))
                .run(materializer);
    }

    /**
     * Map an external message into a PUBLISH.
     *
     * @param topic the topic to publish at.
     * @param qos the QoS.
     * @param retain the retain flag
     * @param externalMessage the external message.
     * @return the PUBLISH message with the content of the external message.
     */
    abstract P mapExternalMessageToMqttMessage(String topic, MqttQos qos, boolean retain,
            ExternalMessage externalMessage);

    /**
     * Extract the topic from the PUBLISH message.
     *
     * @param message the PUBLISH message.
     * @return its topic.
     */
    abstract String getTopic(P message);

    /**
     * Extract the payload from the PUBLISH message.
     *
     * @param message the PUBLISH message.
     * @return its payload.
     */
    abstract Optional<ByteBuffer> getPayload(P message);

    /**
     * Convert an acknowledgement from broker to a Ditto acknowledgement.
     * Does not use the publish result by default. Override this method to use it.
     *
     * @param signal the signal.
     * @param target the target at which this signal is published, or null if the signal is published at a reply-target.
     * @return the acknowledgement.
     */
    protected SendResult buildResponse(final Signal<?> signal, @Nullable final Target target) {
        final DittoHeaders dittoHeaders = signal.getDittoHeaders();
        final Optional<AcknowledgementLabel> autoAckLabel = getAcknowledgementLabel(target);
        final Optional<EntityId> entityIdOptional =
                WithEntityId.getEntityIdOfType(EntityId.class, signal);
        final Acknowledgement issuedAck;
        if (autoAckLabel.isPresent() && entityIdOptional.isPresent()) {
            final EntityId entityId = entityIdOptional.get();
            issuedAck = Acknowledgement.of(autoAckLabel.get(), entityId, HttpStatus.OK, dittoHeaders);
        } else {
            issuedAck = null;
        }
        return new SendResult(issuedAck, dittoHeaders);
    }

    @Override
    protected void preEnhancement(final ReceiveBuilder receiveBuilder) {
        receiveBuilder.match(OutboundSignal.Mapped.class, this::isDryRun,
                outbound -> logger.withCorrelationId(outbound.getSource())
                        .info("Message dropped in dry run mode: {}", outbound));
    }

    @Override
    protected void postEnhancement(final ReceiveBuilder receiveBuilder) {
        // not needed
    }

    @Override
    protected MqttPublishTarget toPublishTarget(final GenericTarget target) {
        return MqttPublishTarget.of(target.getAddress(), target.getQos().orElse(DEFAULT_TARGET_QOS));
    }

    @Override
    protected CompletionStage<SendResult> publishMessage(final Signal<?> signal,
            @Nullable final Target autoAckTarget,
            final MqttPublishTarget publishTarget,
            final ExternalMessage message,
            final int maxTotalMessageSize,
            final int ackSizeQuota,
            @Nullable final AuthorizationContext targetAuthorizationContext) {

        try {
            final var messageHeaders = message.getHeaders();
            final var mqttMessage = mapExternalMessageToMqttMessage(determineTopic(messageHeaders, publishTarget),
                    determineQos(messageHeaders, publishTarget),
                    isMqttRetain(messageHeaders),
                    message);
            return offerToSourceQueue(new MqttSendingContext<>(mqttMessage,
                    signal,
                    new CompletableFuture<>(),
                    message,
                    autoAckTarget));
        } catch (final Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletionStage<SendResult> offerToSourceQueue(final MqttSendingContext<P> mqttSendingContext) {
        return sourceQueue.offer(mqttSendingContext)
                .thenCompose(queueOfferResult -> {
                    if (Objects.equals(queueOfferResult, QueueOfferResult.dropped())) {
                        throw MessageSendingFailedException.newBuilder()
                                .message("Outgoing MQTT message aborted: There are too many in-flight requests.")
                                .description(TOO_MANY_IN_FLIGHT_MESSAGE_DESCRIPTION)
                                .dittoHeaders(mqttSendingContext.getMessage().getInternalHeaders())
                                .build();
                    }
                    return mqttSendingContext.getSendResult();
                })
                .whenComplete((sendResult, error) -> {
                    if (error != null) {
                        final String errorDescription = "Source queue failure";
                        logger.error(error, errorDescription);
                        escalate(error, errorDescription);
                    }
                });
    }

    private void publishMqttMessage(final MqttSendingContext<P> sendingContext) {
        final Signal<?> signal = sendingContext.getSignal();
        final P mqttMessage = sendingContext.getMqttMessage();
        final Target autoAckTarget = sendingContext.getAutoAckTarget();
        if (logger.isDebugEnabled()) {
            final ExternalMessage message = sendingContext.getMessage();
            logger.withCorrelationId(signal)
                    .debug("Publishing MQTT message to topic <{}>: {}", getTopic(mqttMessage),
                            decodeAsHumanReadable(getPayload(mqttMessage).orElse(null), message));
        }
        client.apply(mqttMessage)
                .thenApply(result -> buildResponse(signal, autoAckTarget))
                .thenAccept(sendResult -> sendingContext.getSendResult().complete(sendResult));
    }

    private static String determineTopic(final Map<String, String> headers, final MqttPublishTarget publishTarget) {
        return headers.getOrDefault(MQTT_TOPIC.getName(), publishTarget.getTopic());
    }

    private static MqttQos determineQos(final Map<String, String> headers, final MqttPublishTarget publishTarget) {
        int qosCode;
        try {
            qosCode = Integer.parseInt(headers.get(MQTT_QOS.getName()));
        } catch (final NumberFormatException e) {
            qosCode = publishTarget.getQos();
        }
        return AbstractMqttValidator.getHiveQoS(qosCode);
    }

    private static boolean isMqttRetain(final Map<String, String> headers) {
        return Boolean.parseBoolean(headers.get(MQTT_RETAIN.getName()));
    }

    private boolean isDryRun(final Object message) {
        return dryRun;
    }

}
