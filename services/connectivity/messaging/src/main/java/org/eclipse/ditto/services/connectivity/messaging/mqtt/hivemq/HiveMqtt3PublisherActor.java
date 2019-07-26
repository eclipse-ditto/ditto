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
import java.util.List;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.common.ByteBufferUtils;
import org.eclipse.ditto.model.base.common.CharsetDeterminer;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.BasePublisherActor;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.MqttPublishTarget;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.MqttValidator;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;

import akka.actor.Props;
import akka.actor.Status;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

// TODO: comment & test
@AllParametersAndReturnValuesAreNonnullByDefault
public final class HiveMqtt3PublisherActor extends BasePublisherActor<MqttPublishTarget> {

    // for target the default is qos=0 because we have qos=0 all over the akka cluster
    private static final int DEFAULT_TARGET_QOS = 0;
    static final String NAME = "HiveMqtt3PublisherActor";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final Mqtt3AsyncClient client;
    private final boolean dryRun;
    private boolean connected;
    @Nullable private Target target;
    private MqttPublishTarget publishTarget;
    private ExternalMessage message;
    private ConnectionMonitor publishedMonitor;

    @SuppressWarnings("squid:UnusedPrivateConstructor") // used by akka
    private HiveMqtt3PublisherActor(final String connectionId, final List<Target> targets, final Mqtt3Client client,
            final boolean dryRun) {
        super(connectionId, targets);
        this.client = checkNotNull(client).toAsync();
        this.connected = false;
        this.dryRun = dryRun;
    }

    public static Props props(final String connectionId, final List<Target> targets, final Mqtt3Client client,
            final boolean dryRun) {
        return Props.create(HiveMqtt3PublisherActor.class, connectionId, targets, client, dryRun);
    }

    @Override
    protected void preEnhancement(final ReceiveBuilder receiveBuilder) {
        receiveBuilder.match(OutboundSignal.WithExternalMessage.class, this::isDryRun,
                outbound -> log().info("Message dropped in dry run mode: {}", outbound))
                .matchEquals(HiveMqtt3ClientActor.HiveMqttClientEvents.CONNECTED, this::onClientConnected)
                .matchEquals(HiveMqtt3ClientActor.HiveMqttClientEvents.RECONNECTED, this::onClientReconnected)
                .matchEquals(HiveMqtt3ClientActor.HiveMqttClientEvents.DISCONNECTED, this::onClientDisconnected);
    }

    @SuppressWarnings("unused") // ignored for speed - method reference is much faster than lambda
    private void onClientConnected(final HiveMqtt3ClientActor.HiveMqttClientEvents connected) {
        this.connected = true;
        getSender().tell(new Status.Success("Successfully initialized publisher"), getSelf());
    }

    @SuppressWarnings("unused") // ignored for speed - method reference is much faster than lambda
    private void onClientReconnected(final HiveMqtt3ClientActor.HiveMqttClientEvents reconnected) {
        this.connected = true;
    }

    @SuppressWarnings("unused") // ignored for speed - method reference is much faster than lambda
    private void onClientDisconnected(final HiveMqtt3ClientActor.HiveMqttClientEvents disconnected) {
        this.connected = false;
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
    protected MqttPublishTarget toReplyTarget(final String replyToAddress) {
        return MqttPublishTarget.of(replyToAddress);
    }

    @Override
    protected DiagnosticLoggingAdapter log() {
        return log;
    }

    @Override
    protected void publishMessage(@Nullable final Target target, final MqttPublishTarget publishTarget,
            final ExternalMessage message,
            final ConnectionMonitor publishedMonitor) {

        final MqttQos targetQoS = determineQos(target);
        publishMessage(publishTarget, targetQoS, message, publishedMonitor);
    }

    // TODO: we should probably always use AT_MOST_ONCE since we have no persistence and stuff.
    private MqttQos determineQos(@Nullable final Target target) {
        if (target == null) {
            return MqttQos.AT_MOST_ONCE;
        } else {
            final int qos = target.getQos().orElse(DEFAULT_TARGET_QOS);
            return MqttValidator.getHiveQoS(qos);
        }
    }

    private void publishMessage(final MqttPublishTarget publishTarget, final MqttQos qos, final ExternalMessage message,
            final ConnectionMonitor publishedMonitor) {
        if (connected) {
            final Mqtt3Publish mqttMessage = mapExternalMessageToMqttMessage(publishTarget, qos, message);
            if (log().isDebugEnabled()) {
                final String humanReadablePayload = mqttMessage.getPayload()
                        .map(getCharsetFromMessage(message)::decode)
                        .map(CharBuffer::toString).orElse("<empty>");
                log().debug("Publishing MQTT message to topic <{}>: {}", mqttMessage.getTopic(),
                        humanReadablePayload);
            }
            client.publish(mqttMessage).whenComplete((mqtt3Publish, throwable) -> {
                if (null == throwable) {
                    log().info("Successfully published to message of type <{}> to target address <{}>",
                            mqttMessage.getType(), publishTarget.getTopic());
                    publishedMonitor.success(message);
                } else {
                    log().error(throwable, "Error while publishing message");
                    final String logMessage =
                            MessageFormat.format("Error while publishing message: {0}", throwable.getMessage());
                    publishedMonitor.exception(message, logMessage);
                }
            });
        } else {
            log().info("Won't publish message, since currently in disconnected state.");
            publishedMonitor.failure(message, "Won't publish message since currently not connected.");
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

    private Charset getCharsetFromMessage(final ExternalMessage message) {
        return message.findContentType()
                .map(this::determineCharset)
                .orElse(StandardCharsets.UTF_8);
    }

    private Charset determineCharset(final CharSequence contentType) {
        return CharsetDeterminer.getInstance().apply(contentType);
    }

    @SuppressWarnings("unused") // ignored for speed - method reference is much faster than lambda
    private boolean isDryRun(final Object message) {
        return dryRun;
    }

}
