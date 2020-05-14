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
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.common.ByteBufferUtils;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.entity.id.EntityIdWithType;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
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
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.datatypes.Mqtt5UserProperties;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;

import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor responsible for publishing messages to an MQTT 5 broker using the given {@link Mqtt5Client}.
 *
 * @since 1.1.0
 */
public final class HiveMqtt5PublisherActor extends BasePublisherActor<MqttPublishTarget> {

    // for target the default is qos=0 because we have qos=0 all over the akka cluster
    private static final int DEFAULT_TARGET_QOS = 0;
    private static final AcknowledgementLabel NO_ACK_LABEL = AcknowledgementLabel.of("ditto-mqtt5-diagnostic");
    static final String NAME = "HiveMqtt5PublisherActor";

    private static final HashSet<String> MQTT_HEADER_MAPPING = new HashSet<>();

    static {
        MQTT_HEADER_MAPPING.add(DittoHeaderDefinition.CORRELATION_ID.getKey());
        MQTT_HEADER_MAPPING.add(ExternalMessage.REPLY_TO_HEADER);
        MQTT_HEADER_MAPPING.add(ExternalMessage.CONTENT_TYPE_HEADER);
    }

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
    private final Mqtt5AsyncClient client;
    private final boolean dryRun;

    @SuppressWarnings("squid:UnusedPrivateConstructor") // used by akka
    private HiveMqtt5PublisherActor(final Connection connection, final Mqtt5Client client, final boolean dryRun) {
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
    public static Props props(final Connection connection, final Mqtt5Client client, final boolean dryRun) {
        return Props.create(HiveMqtt5PublisherActor.class, connection, client, dryRun);
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
            @Nullable final Target target, final MqttPublishTarget publishTarget,
            final ExternalMessage message, int ackSizeQuota) {

        try {
            final MqttQos qos = determineQos(target);
            final Mqtt5Publish mqttMessage = mapExternalMessageToMqttMessage(publishTarget, qos, message);
            if (log().isDebugEnabled()) {
                log().debug("Publishing MQTT message to topic <{}>: {}", mqttMessage.getTopic(),
                        decodeAsHumanReadable(mqttMessage.getPayload().orElse(null), message));
            }
            return client.publish(mqttMessage).thenApply(msg -> toAcknowledgement(signal, target));
        } catch (final Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private Acknowledgement toAcknowledgement(final Signal<?> signal,
            @Nullable final Target target) {

        // acks for non-thing-signals are for local diagnostics only, therefore it is safe to fix entity type to Thing.
        final EntityIdWithType entityIdWithType = ThingId.of(signal.getEntityId());
        final DittoHeaders dittoHeaders = signal.getDittoHeaders();
        final AcknowledgementLabel label = getAcknowledgementLabel(target).orElse(NO_ACK_LABEL);

        return Acknowledgement.of(label, entityIdWithType, HttpStatusCode.OK, dittoHeaders);
    }

    private MqttQos determineQos(@Nullable final Target target) {
        if (target == null) {
            return MqttQos.AT_MOST_ONCE;
        } else {
            final int qos = target.getQos().orElse(DEFAULT_TARGET_QOS);
            return AbstractMqttValidator.getHiveQoS(qos);
        }
    }

    private Mqtt5Publish mapExternalMessageToMqttMessage(final MqttPublishTarget mqttTarget, final MqttQos qos,
            final ExternalMessage externalMessage) {

        final Charset charset = getCharsetFromMessage(externalMessage);

        final ByteBuffer payload;
        if (externalMessage.isTextMessage()) {
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

        final ByteBuffer correlationData = ByteBuffer.wrap(externalMessage.getHeaders()
                .getOrDefault(DittoHeaderDefinition.CORRELATION_ID.getKey(), "").getBytes(charset));

        final String responseTopic = externalMessage.getHeaders().get(ExternalMessage.REPLY_TO_HEADER);

        final String contentType = externalMessage.getHeaders().get(ExternalMessage.CONTENT_TYPE_HEADER);

        final Mqtt5UserProperties userProperties = externalMessage.getHeaders()
                .entrySet()
                .stream()
                .filter(header -> !MQTT_HEADER_MAPPING.contains(header.getKey()))
                .reduce(Mqtt5UserProperties.builder(),
                        (builder, entry) -> builder.add(entry.getKey(), entry.getValue()),
                        (builder1, builder2) -> builder1.addAll(builder2.build().asList())
                )
                .build();

        return Mqtt5Publish.builder()
                .topic(mqttTarget.getTopic())
                .qos(qos)
                .payload(payload)
                .correlationData(correlationData)
                .responseTopic(responseTopic)
                .contentType(contentType)
                .userProperties(userProperties)
                .build();
    }

    private boolean isDryRun(final Object message) {
        return dryRun;
    }

}
