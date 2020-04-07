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
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.HashSet;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.common.ByteBufferUtils;
import org.eclipse.ditto.model.base.common.CharsetDeterminer;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.BasePublisherActor;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.AbstractMqttValidator;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.MqttPublishTarget;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.datatypes.Mqtt5UserProperties;
import com.hivemq.client.mqtt.mqtt5.datatypes.Mqtt5UserPropertiesBuilder;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;

import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor responsible for publishing messages to an MQTT 5 broker using the given {@link Mqtt5Client}.
 *
 * @since 1.1.0
 */
public final class HiveMqtt5PublisherActor extends BasePublisherActor<MqttPublishTarget> {

    // for target the default is qos=0 because we have qos=0 all over the akka cluster
    private static final int DEFAULT_TARGET_QOS = 0;
    static final String NAME = "HiveMqtt5PublisherActor";

    private static final HashSet<String> MQTT_HEADER_MAPPING = new HashSet<>();

    static {
        MQTT_HEADER_MAPPING.add(DittoHeaderDefinition.CORRELATION_ID.getKey());
        MQTT_HEADER_MAPPING.add(ExternalMessage.REPLY_TO_HEADER);
        MQTT_HEADER_MAPPING.add(ExternalMessage.CONTENT_TYPE_HEADER);
    }

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
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
    protected DiagnosticLoggingAdapter log() {
        return log;
    }

    @Override
    protected void publishMessage(@Nullable final Target target, final MqttPublishTarget publishTarget,
            final ExternalMessage message, final ConnectionMonitor publishedMonitor) {

        final MqttQos targetQoS = determineQos(target);
        publishMessage(publishTarget, targetQoS, message, publishedMonitor);
    }

    private MqttQos determineQos(@Nullable final Target target) {
        if (target == null) {
            return MqttQos.AT_MOST_ONCE;
        } else {
            final int qos = target.getQos().orElse(DEFAULT_TARGET_QOS);
            return AbstractMqttValidator.getHiveQoS(qos);
        }
    }

    private void publishMessage(final MqttPublishTarget publishTarget, final MqttQos qos, final ExternalMessage message,
            final ConnectionMonitor publishedMonitor) {
        try {
            final Mqtt5Publish mqttMessage = mapExternalMessageToMqttMessage(publishTarget, qos, message);
            if (log().isDebugEnabled()) {
                final String humanReadablePayload = mqttMessage.getPayload()
                        .map(getCharsetFromMessage(message)::decode)
                        .map(CharBuffer::toString).orElse("<empty>");
                log().debug("Publishing MQTT message to topic <{}>: {}", mqttMessage.getTopic(),
                        humanReadablePayload);
            }
            client.publish(mqttMessage).whenComplete((mqtt5Publish, throwable) -> {
                if (null == throwable) {
                    log().debug("Successfully published to message of type <{}> to target address <{}>",
                            mqttMessage.getType(), publishTarget.getTopic());
                    publishedMonitor.success(message);
                } else {
                    final String logMessage =
                            MessageFormat.format("Error while publishing message: {0}", throwable.getMessage());
                    log().info(logMessage);
                    publishedMonitor.exception(message, logMessage);
                }
            });
        } catch (final Exception e) {
            log().info("Won't publish message, since currently in disconnected state.");
            publishedMonitor.failure(message, "Won't publish message since currently not connected.");
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

        final Mqtt5UserPropertiesBuilder mqttUserPropertiesBuilder = Mqtt5UserProperties.builder();

        externalMessage.getHeaders()
                .entrySet()
                .stream()
                .filter(header -> !MQTT_HEADER_MAPPING.contains(header.getKey()))
                .forEach(entry -> mqttUserPropertiesBuilder.add(entry.getKey(), entry.getValue()));

        final Mqtt5UserProperties userProperties = mqttUserPropertiesBuilder.build();

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

    private Charset getCharsetFromMessage(final ExternalMessage message) {
        return message.findContentType()
                .map(this::determineCharset)
                .orElse(StandardCharsets.UTF_8);
    }

    private Charset determineCharset(final CharSequence contentType) {
        return CharsetDeterminer.getInstance().apply(contentType);
    }

    private boolean isDryRun(final Object message) {
        return dryRun;
    }

}
