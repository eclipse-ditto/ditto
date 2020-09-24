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

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.entity.id.EntityIdWithType;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.connectivity.messaging.BasePublisherActor;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.AbstractMqttValidator;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.MqttPublishTarget;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.CommandResponse;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;

import akka.actor.Props;
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
    private static final AcknowledgementLabel NO_ACK_LABEL = AcknowledgementLabel.of("ditto-mqtt-diagnostic");

    private final Function<P, CompletableFuture<R>> client;
    private final boolean dryRun;

    AbstractMqttPublisherActor(final Connection connection, final Function<P, CompletableFuture<R>> client,
            final boolean dryRun) {

        super(connection);
        this.client = client;
        this.dryRun = dryRun;
    }

    /**
     * Map an external message into a PUBLISH.
     *
     * @param mqttTarget the target to publish at.
     * @param qos the QoS.
     * @param externalMessage the external message.
     * @return the PUBLISH message with the content of the external message.
     */
    abstract P mapExternalMessageToMqttMessage(MqttPublishTarget mqttTarget, MqttQos qos,
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
     * Create Props object for this publisher actor.
     *
     * @param connection the connection the publisher actor belongs to.
     * @param client the HiveMQ client.
     * @param dryRun whether this publisher is only created for a test or not.
     * @return the Props object.
     */
    public static Props props(final Connection connection, final Mqtt3Client client, final boolean dryRun) {
        return Props.create(AbstractMqttPublisherActor.class, connection, client, dryRun);
    }

    /**
     * Convert an acknowledgement from broker to a Ditto acknowledgement.
     * Does not use the publish result by default. Override this method to use it.
     *
     * @param signal the signal.
     * @param target the target at which this signal is published, or null if the signal is published at a reply-target.
     * @param result the result of the PUBLISH message according to the broker as encapsulated by the client.
     * @return the acknowledgement.
     */
    protected Acknowledgement toAcknowledgement(final Signal<?> signal, @Nullable final Target target, final R result) {

        // acks for non-thing-signals are for local diagnostics only, therefore it is safe to fix entity type to Thing.
        final EntityIdWithType entityIdWithType = ThingId.of(signal.getEntityId());
        final DittoHeaders dittoHeaders = signal.getDittoHeaders();
        final AcknowledgementLabel label = getAcknowledgementLabel(target).orElse(NO_ACK_LABEL);

        return Acknowledgement.of(label, entityIdWithType, HttpStatusCode.OK, dittoHeaders);
    }

    @Override
    protected boolean shouldPublishAcknowledgement(final Acknowledgement acknowledgement) {
        return !NO_ACK_LABEL.equals(acknowledgement.getLabel());
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
    protected CompletionStage<CommandResponse<?>> publishMessage(final Signal<?> signal,
            @Nullable final Target autoAckTarget,
            final MqttPublishTarget publishTarget,
            final ExternalMessage message,
            final int maxTotalMessageSize,
            int ackSizeQuota) {

        try {
            final MqttQos qos = determineQos(autoAckTarget);
            final P mqttMessage = mapExternalMessageToMqttMessage(publishTarget, qos, message);
            if (logger.isDebugEnabled()) {
                logger.withCorrelationId(signal)
                        .debug("Publishing MQTT message to topic <{}>: {}", getTopic(mqttMessage),
                                decodeAsHumanReadable(getPayload(mqttMessage).orElse(null), message));
            }
            return client.apply(mqttMessage).thenApply(result -> toAcknowledgement(signal, autoAckTarget, result));
        } catch (final Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static MqttQos determineQos(@Nullable final Target autoAckTarget) {
        if (autoAckTarget == null) {
            return MqttQos.AT_MOST_ONCE;
        } else {
            final int qos = autoAckTarget.getQos().orElse(DEFAULT_TARGET_QOS);
            return AbstractMqttValidator.getHiveQoS(qos);
        }
    }

    private boolean isDryRun(final Object message) {
        return dryRun;
    }

}
