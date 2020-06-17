/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.common.ByteBufferUtils;
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
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.base.Signal;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;

import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor responsible for publishing messages to an MQTT broker using the given {@link Mqtt3Client}.
 */
public final class HiveMqtt3PublisherActor extends BasePublisherActor<MqttPublishTarget> {

    // for target the default is qos=0 because we have qos=0 all over the akka cluster
    private static final int DEFAULT_TARGET_QOS = 0;
    private static final AcknowledgementLabel NO_ACK_LABEL = AcknowledgementLabel.of("ditto-mqtt3-diagnostic");
    static final String NAME = "HiveMqtt3PublisherActor";

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
    private final Mqtt3AsyncClient client;
    private final boolean dryRun;

    @SuppressWarnings("squid:UnusedPrivateConstructor") // used by akka
    private HiveMqtt3PublisherActor(final Connection connection, final Mqtt3Client client, final boolean dryRun) {
        super(connection);
        this.client = checkNotNull(client).toAsync();
        this.dryRun = dryRun;
    }

    /**
     * Create Props object for this publisher actor.
     *
     * @param connection the connection the publisher actor belongs to.
     * @param client the HiveMQ client.
     * @param dryRun whether this publisher is only created for a test or not.
     * @return the Props object.
     */
    public static Props props(final Connection connection, final Mqtt3Client client, final boolean dryRun) {
        return Props.create(HiveMqtt3PublisherActor.class, connection, client, dryRun);
    }

    @Override
    protected void preEnhancement(final ReceiveBuilder receiveBuilder) {
        receiveBuilder.match(OutboundSignal.Mapped.class, this::isDryRun,
                outbound -> log().info("Message dropped in dry run mode: {}", outbound));
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
    protected DittoDiagnosticLoggingAdapter log() {
        return log;
    }

    @Override
    protected CompletionStage<Acknowledgement> publishMessage(final Signal<?> signal,
            @Nullable final Target autoAckTarget,
            final MqttPublishTarget publishTarget,
            final ExternalMessage message, int ackSizeQuota) {

        try {
            final MqttQos qos = determineQos(autoAckTarget);
            final Mqtt3Publish mqttMessage = mapExternalMessageToMqttMessage(publishTarget, qos, message);
            if (log().isDebugEnabled()) {
                log().debug("Publishing MQTT message to topic <{}>: {}", mqttMessage.getTopic(),
                        decodeAsHumanReadable(mqttMessage.getPayload().orElse(null), message));
            }
            return client.publish(mqttMessage).thenApply(msg -> toAcknowledgement(signal, autoAckTarget));
        } catch (final Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private MqttQos determineQos(@Nullable final Target autoAckTarget) {
        if (autoAckTarget == null) {
            return MqttQos.AT_MOST_ONCE;
        } else {
            final int qos = autoAckTarget.getQos().orElse(DEFAULT_TARGET_QOS);
            return AbstractMqttValidator.getHiveQoS(qos);
        }
    }

    private Mqtt3Publish mapExternalMessageToMqttMessage(final MqttPublishTarget mqttTarget, final MqttQos qos,
            final ExternalMessage externalMessage) {

        final ByteBuffer payload;
        if (externalMessage.isTextMessage()) {
            final Charset charset = getCharsetFromMessage(externalMessage);
            payload = externalMessage
                    .getTextPayload()
                    .map(text -> ByteBuffer.wrap(text.getBytes(charset)))
                    .orElse(ByteBufferUtils.empty());
        } else if (externalMessage.isBytesMessage()) {
            payload = externalMessage.getBytePayload()
                    .orElse(ByteBufferUtils.empty());
        } else {
            payload = ByteBufferUtils.empty();
        }
        return Mqtt3Publish.builder().topic(mqttTarget.getTopic()).qos(qos).payload(payload).build();
    }

    private Acknowledgement toAcknowledgement(final Signal<?> signal,
            @Nullable final Target target) {

        // acks for non-thing-signals are for local diagnostics only, therefore it is safe to fix entity type to Thing.
        final EntityIdWithType entityIdWithType = ThingId.of(signal.getEntityId());
        final DittoHeaders dittoHeaders = signal.getDittoHeaders();
        final AcknowledgementLabel label = getAcknowledgementLabel(target).orElse(NO_ACK_LABEL);

        return Acknowledgement.of(label, entityIdWithType, HttpStatusCode.OK, dittoHeaders);
    }

    private boolean isDryRun(final Object message) {
        return dryRun;
    }

}
