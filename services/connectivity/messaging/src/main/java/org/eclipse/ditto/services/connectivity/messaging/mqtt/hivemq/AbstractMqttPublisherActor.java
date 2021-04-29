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
package org.eclipse.ditto.services.connectivity.messaging.mqtt.hivemq;

import static org.eclipse.ditto.services.connectivity.messaging.mqtt.MqttHeader.MQTT_QOS;
import static org.eclipse.ditto.services.connectivity.messaging.mqtt.MqttHeader.MQTT_RETAIN;
import static org.eclipse.ditto.services.connectivity.messaging.mqtt.MqttHeader.MQTT_TOPIC;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatus;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.entity.id.WithEntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.BasePublisherActor;
import org.eclipse.ditto.services.connectivity.messaging.SendResult;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.AbstractMqttValidator;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.MqttPublishTarget;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.base.Signal;

import com.hivemq.client.mqtt.datatypes.MqttQos;

import akka.japi.pf.ReceiveBuilder;

/**
 * Common implementation for MQTT3 and MQTT5 publisher actors.
 *
 * @param <P> type of PUBLISH messages.
 * @param <R> type of replies of PUBLISH messages as encapsulated by the MQTT client.
 */
abstract class AbstractMqttPublisherActor<P, R> extends BasePublisherActor<MqttPublishTarget> {

    // for target the default is qos=0 because we have qos=0 all over the akka cluster
    private static final int DEFAULT_TARGET_QOS = 0;

    private final Function<P, CompletableFuture<R>> client;
    private final boolean dryRun;

    AbstractMqttPublisherActor(final Connection connection, final Function<P, CompletableFuture<R>> client,
            final boolean dryRun, final String clientId) {

        super(connection, clientId);
        this.client = client;
        this.dryRun = dryRun;
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
     * @param result the result of the PUBLISH message according to the broker as encapsulated by the client.
     * @return the acknowledgement.
     */
    protected SendResult buildResponse(final Signal<?> signal, @Nullable final Target target,
            final R result) {

        final DittoHeaders dittoHeaders = signal.getDittoHeaders();
        final Optional<AcknowledgementLabel> autoAckLabel = getAcknowledgementLabel(target);
        final Optional<EntityId> entityIdOptional =
                WithEntityId.getEntityIdOfType(EntityId.class, signal);
        final Acknowledgement issuedAck;
        if (autoAckLabel.isPresent() && entityIdOptional.isPresent()) {
            final EntityId entityId =entityIdOptional.get();
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
    protected MqttPublishTarget toPublishTarget(final String address) {
        return MqttPublishTarget.of(address);
    }

    @Override
    protected CompletionStage<SendResult> publishMessage(final Signal<?> signal,
            @Nullable final Target autoAckTarget,
            final MqttPublishTarget publishTarget,
            final ExternalMessage message,
            final int maxTotalMessageSize,
            final int ackSizeQuota) {

        try {
            final String topic = determineTopic(publishTarget, message);
            final MqttQos qos = determineQos(message.getHeaders(), autoAckTarget);
            final boolean retain = determineMqttRetainFromHeaders(message.getHeaders());
            final P mqttMessage = mapExternalMessageToMqttMessage(topic, qos, retain, message);
            if (logger.isDebugEnabled()) {
                logger.withCorrelationId(signal)
                        .debug("Publishing MQTT message to topic <{}>: {}", getTopic(mqttMessage),
                                decodeAsHumanReadable(getPayload(mqttMessage).orElse(null), message));
            }
            return client.apply(mqttMessage).thenApply(result -> buildResponse(signal, autoAckTarget, result));
        } catch (final Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private String determineTopic(final MqttPublishTarget publishTarget, final ExternalMessage message) {
        return Optional.of(message)
                .map(ExternalMessage::getHeaders)
                .flatMap(m -> Optional.ofNullable(m.get(MQTT_TOPIC.getName())))
                .orElse(publishTarget.getTopic());
    }

    private static MqttQos determineQos(final Map<String, String> headers, @Nullable final Target autoAckTarget) {
        final Optional<MqttQos> mqttQosFromHeaders = getMqttQosFromHeaders(headers);
        if (mqttQosFromHeaders.isPresent()) {
            return mqttQosFromHeaders.get();
        } else if (autoAckTarget == null) {
            return MqttQos.AT_MOST_ONCE;
        } else {
            final int qos = autoAckTarget.getQos().orElse(DEFAULT_TARGET_QOS);
            return AbstractMqttValidator.getHiveQoS(qos);
        }
    }

    private static Optional<MqttQos> getMqttQosFromHeaders(final Map<String, String> headers) {
        return Optional.ofNullable(headers.get(MQTT_QOS.getName()))
                .flatMap(qosHeader -> {
                    try {
                        return Optional.of(Integer.parseInt(qosHeader));
                    } catch (final NumberFormatException nfe) {
                        return Optional.empty();
                    }
                })
                .map(MqttQos::fromCode);
    }

    private boolean determineMqttRetainFromHeaders(final Map<String, String> headers) {
        return Optional.of(headers)
                .map(h -> h.get(MQTT_RETAIN.getName()))
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    private boolean isDryRun(final Object message) {
        return dryRun;
    }

}
