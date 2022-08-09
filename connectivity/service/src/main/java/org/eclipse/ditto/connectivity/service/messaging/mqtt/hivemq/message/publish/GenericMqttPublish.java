/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish;

import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.ByteBufferUtils;
import org.eclipse.ditto.base.model.common.CharsetDeterminer;
import org.eclipse.ditto.base.model.common.ConditionChecker;

import com.hivemq.client.mqtt.MqttVersion;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttTopic;
import com.hivemq.client.mqtt.datatypes.MqttUtf8String;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.hivemq.client.mqtt.mqtt5.datatypes.Mqtt5UserProperties;
import com.hivemq.client.mqtt.mqtt5.datatypes.Mqtt5UserProperty;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PayloadFormatIndicator;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;

import akka.http.javadsl.model.ContentType;
import akka.http.javadsl.model.ContentTypes;

/**
 * Unifies access to MQTT Publish messages of protocol versions 3 and 5.
 */
@SuppressWarnings("java:S1610")
@NotThreadSafe
/*
 * Abstract class is appropriate in this case to completely hide existing
 * implementations and avoid new implementations.
 * This would neither be possible by sealing nor by converting to
 * an interface.
 */
public abstract class GenericMqttPublish {

    private GenericMqttPublish() {
        super();
    }

    /**
     * Returns a new instance of {@code GenericMqttPublish} that wraps the specified {@code Mqtt3Publish} argument.
     *
     * @param mqtt3Publish the {@code PUBLISH} message to be wrapped.
     * @return the new instance.
     * @throws NullPointerException if {@code mqtt3Publish} is {@code null}.
     */
    public static GenericMqttPublish ofMqtt3Publish(final Mqtt3Publish mqtt3Publish) {
        return new GenericMqtt3Publish(mqtt3Publish);
    }

    /**
     * Returns a new instance of {@code GenericMqttPublish} that wraps the specified {@code Mqtt5Publish} argument.
     *
     * @param mqtt5Publish the {@code PUBLISH} message to be wrapped.
     * @return the new instance.
     * @throws NullPointerException if {@code mqtt5Publish} is {@code null}.
     */
    public static GenericMqttPublish ofMqtt5Publish(final Mqtt5Publish mqtt5Publish) {
        return new GenericMqtt5Publish(mqtt5Publish);
    }

    /**
     * Returns a mutable builder with fluent API for building a {@code GenericMqttPublish} from scratch.
     * <em>Note:</em> the built Publish cannot be acknowledged, i.e. calling the method throws an exception.
     *
     * @param mqttTopic the mandatory topic.
     * @param mqttQos the mandatory QoS.
     * @return the builder.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static Builder builder(final MqttTopic mqttTopic, final MqttQos mqttQos) {
        return new Builder(mqttTopic, mqttQos);
    }

    /**
     * Returns the topic of this Publish message.
     *
     * @return the topic.
     */
    public abstract MqttTopic getTopic();

    public abstract MqttQos getQos();

    /**
     * Indicates whether this Publish message is a retained message.
     *
     * @return {@code true} if this is a retained message, {@code false} else.
     */
    public abstract boolean isRetain();

    public abstract Optional<ByteBuffer> getCorrelationData();

    public abstract Optional<MqttTopic> getResponseTopic();

    public abstract Optional<String> getContentType();

    public abstract Stream<UserProperty> userProperties();

    public abstract Optional<ByteBuffer> getPayload();

    public Optional<String> getPayloadAsHumanReadable() {
        return getPayload()
                .map(payloadByteBuffer -> {
                    final var charsetDeterminer = CharsetDeterminer.getInstance();
                    final var charset = charsetDeterminer.apply(getContentType().orElse(null));
                    final var charBuffer = charset.decode(payloadByteBuffer);
                    return charBuffer.toString();
                });
    }

    public abstract void acknowledge() throws ManualAcknowledgementDisabledException,
            MessageAlreadyAcknowledgedException, AcknowledgementUnsupportedException;

    /**
     * Indicates whether the version of this {@code GenericMqtt5Publish} is the same as the specified
     * {@code MqttVersion} argument.
     *
     * @param mqttVersion the expected MQTT version.
     * @return {@code true} if this {@code GenericMqtt5Publish} has version {@code mqttVersion}, {@code false} else.
     */
    public abstract boolean isMqttVersion(MqttVersion mqttVersion);

    /**
     * Returns this {@code GenericMqttPublish} as {@code Mqtt3Publish}.
     * <em>Note:</em> {@link Mqtt3Publish#acknowledge()} only works if this {@code GenericMqttPublish} was constructed
     * from a {@code Mqtt3Publish}
     *
     * @return the {@code Mqtt3Publish} representation of this {@code GenericMqttPublish}.
     */
    public abstract Mqtt3Publish getAsMqtt3Publish();

    private static Mqtt3Publish getAsMqtt3Publish(final GenericMqttPublish genericMqttPublish) {
        return Mqtt3Publish.builder()
                .topic(genericMqttPublish.getTopic())
                .qos(genericMqttPublish.getQos())
                .retain(genericMqttPublish.isRetain())
                .payload(genericMqttPublish.getPayload().orElse(null))
                .build();
    }

    /**
     * Returns this {@code GenericMqttPublish} as {@code Mqtt5Publish}.
     * <em>Note:</em> {@link Mqtt5Publish#acknowledge()} only works if this {@code GenericMqttPublish} was constructed
     * from a {@code Mqtt5Publish}
     *
     * @return the {@code Mqtt5Publish} representation of this {@code GenericMqttPublish}.
     */
    public abstract Mqtt5Publish getAsMqtt5Publish();

    private static Mqtt5Publish getAsMqtt5Publish(final GenericMqttPublish genericMqttPublish) {
        return Mqtt5Publish.builder()
                .topic(genericMqttPublish.getTopic())
                .qos(genericMqttPublish.getQos())
                .retain(genericMqttPublish.isRetain())
                .payload(genericMqttPublish.getPayload().orElse(null))
                .correlationData(genericMqttPublish.getCorrelationData().orElse(null))
                .responseTopic(genericMqttPublish.getResponseTopic().orElse(null))
                .contentType(genericMqttPublish.getContentType().orElse(null))
                .userProperties(Mqtt5UserProperties.of(genericMqttPublish.userProperties()
                        .map(userProperty -> Mqtt5UserProperty.of(userProperty.name(), userProperty.value()))
                        .collect(Collectors.toList())))
                .build();
    }

    @Override
    public abstract String toString();

    /**
     * Mutable builder with a fluent API for constructing a {@code GenericMqttPublish} from scratch.
     * <em>Note:</em> The built Publish cannot be acknowledged.
     */
    @NotThreadSafe
    public static final class Builder {

        private final MqttTopic mqttTopic;
        private final MqttQos mqttQos;
        private boolean retain;
        @Nullable private ByteBuffer payload;
        @Nullable private ByteBuffer correlationData;
        @Nullable private MqttTopic responseTopic;
        @Nullable private String contentType;
        private Set<UserProperty> userProperties;

        private Builder(final MqttTopic mqttTopic, final MqttQos mqttQos) {
            this.mqttTopic = ConditionChecker.checkNotNull(mqttTopic, "mqttTopic");
            this.mqttQos = ConditionChecker.checkNotNull(mqttQos, "mqttQos");
            retain = false;
            payload = null;
            correlationData = null;
            responseTopic = null;
            contentType = null;
            userProperties = Set.of();
        }

        /**
         * Sets whether the Publish should be retained.
         *
         * @param retain {@code true} if the Publish should be retained, {@code false} else.
         * @return this Builder instance for method chaining.
         */
        public Builder retain(final boolean retain) {
            this.retain = retain;
            return this;
        }

        /**
         * Sets the optional payload.
         *
         * @param payload the payload of the Publish or {@code null} to remove any previously set payload.
         * @return this Builder instance for method chaining.
         */
        public Builder payload(@Nullable final ByteBuffer payload) {
            if (null != payload) {
                this.payload = payload.asReadOnlyBuffer();
            } else {
                this.payload = null;
            }
            return this;
        }

        public Builder correlationData(@Nullable final ByteBuffer correlationData) {
            this.correlationData = correlationData;
            return this;
        }

        public Builder responseTopic(@Nullable final MqttTopic responseTopic) {
            this.responseTopic = responseTopic;
            return this;
        }

        public Builder contentType(@Nullable final String contentType) {
            if (null != contentType) {
                ConditionChecker.checkArgument(contentType,
                        arg -> !arg.isBlank(),
                        () -> "The contentType must not be blank.");
            }
            this.contentType = contentType;
            return this;
        }

        public Builder userProperties(final Set<UserProperty> userProperties) {
            this.userProperties = ConditionChecker.checkNotNull(userProperties, "userProperties");
            return this;
        }

        public GenericMqttPublish build() {
            return new FromScratchGenericMqttPublish(this);
        }

    }

    private static final class FromScratchGenericMqttPublish extends GenericMqttPublish {

        private final MqttTopic mqttTopic;
        private final MqttQos mqttQos;
        private final boolean retain;
        @Nullable private final ByteBuffer payload;
        @Nullable private final ByteBuffer correlationData;
        @Nullable private final MqttTopic responseTopic;
        @Nullable private final String contentType;
        private final Set<UserProperty> userProperties;

        private FromScratchGenericMqttPublish(final Builder builder) {
            mqttTopic = builder.mqttTopic;
            mqttQos = builder.mqttQos;
            retain = builder.retain;
            payload = builder.payload;
            correlationData = builder.correlationData;
            responseTopic = builder.responseTopic;
            contentType = builder.contentType;
            userProperties = new LinkedHashSet<>(builder.userProperties);
        }

        @Override
        public MqttTopic getTopic() {
            return mqttTopic;
        }

        @Override
        public MqttQos getQos() {
            return mqttQos;
        }

        @Override
        public boolean isRetain() {
            return retain;
        }

        @Override
        public Optional<ByteBuffer> getCorrelationData() {
            return Optional.ofNullable(correlationData);
        }

        @Override
        public Optional<MqttTopic> getResponseTopic() {
            return Optional.ofNullable(responseTopic);
        }

        @Override
        public Optional<String> getContentType() {
            return Optional.ofNullable(contentType);
        }

        @Override
        public Stream<UserProperty> userProperties() {
            return userProperties.stream();
        }

        @Override
        public Optional<ByteBuffer> getPayload() {
            return Optional.ofNullable(payload)
                    .map(ByteBufferUtils::clone);
        }

        @Override
        public void acknowledge() throws AcknowledgementUnsupportedException {
            throw new AcknowledgementUnsupportedException(
                    MessageFormat.format("A {0} that was built from scratch cannot be acknowledged.",
                            GenericMqttPublish.class.getSimpleName())
            );
        }

        @Override
        public boolean isMqttVersion(final MqttVersion mqttVersion) {
            return false;
        }

        @Override
        public Mqtt3Publish getAsMqtt3Publish() {
            return GenericMqttPublish.getAsMqtt3Publish(this);
        }

        @Override
        public Mqtt5Publish getAsMqtt5Publish() {
            return GenericMqttPublish.getAsMqtt5Publish(this);
        }

        @Override
        public boolean equals(@Nullable final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final var that = (FromScratchGenericMqttPublish) o;
            return Objects.equals(mqttTopic, that.mqttTopic) &&
                    mqttQos == that.mqttQos &&
                    retain == that.retain &&
                    Objects.equals(payload, that.payload) &&
                    Objects.equals(correlationData, that.correlationData) &&
                    Objects.equals(responseTopic, that.responseTopic) &&
                    Objects.equals(contentType, that.contentType) &&
                    Objects.equals(userProperties, that.userProperties);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mqttTopic,
                    mqttQos,
                    retain,
                    payload,
                    correlationData,
                    responseTopic,
                    contentType,
                    userProperties);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "mqttTopic=" + mqttTopic +
                    ", mqttQos=" + mqttQos +
                    ", retain=" + retain +
                    ", payload=" + getPayloadAsHumanReadable().orElse(null) +
                    ", correlationData=" + correlationData +
                    ", responseTopic=" + responseTopic +
                    ", contentType=" + contentType +
                    ", userProperties=" + userProperties +
                    "]";
        }

    }

    private static final class GenericMqtt3Publish extends GenericMqttPublish {

        private final Mqtt3Publish mqtt3Publish;

        private GenericMqtt3Publish(final Mqtt3Publish mqtt3Publish) {
            this.mqtt3Publish = ConditionChecker.checkNotNull(mqtt3Publish, "mqtt3Publish");
        }

        @Override
        public MqttTopic getTopic() {
            return mqtt3Publish.getTopic();
        }

        @Override
        public MqttQos getQos() {
            return mqtt3Publish.getQos();
        }

        @Override
        public boolean isRetain() {
            return mqtt3Publish.isRetain();
        }

        @Override
        public Optional<ByteBuffer> getCorrelationData() {
            return Optional.empty();
        }

        @Override
        public Optional<MqttTopic> getResponseTopic() {
            return Optional.empty();
        }

        @Override
        public Optional<String> getContentType() {
            return Optional.empty();
        }

        @Override
        public Stream<UserProperty> userProperties() {
            return Stream.empty();
        }

        @Override
        public Optional<ByteBuffer> getPayload() {
            return mqtt3Publish.getPayload()
                    .map(ByteBufferUtils::clone);
        }

        @Override
        public void acknowledge() throws ManualAcknowledgementDisabledException, MessageAlreadyAcknowledgedException {
            try {
                mqtt3Publish.acknowledge();
            } catch (final UnsupportedOperationException e) {
                throw new ManualAcknowledgementDisabledException(e);
            } catch (final IllegalStateException e) {
                throw new MessageAlreadyAcknowledgedException(e);
            }
        }

        @Override
        public boolean isMqttVersion(final MqttVersion mqttVersion) {
            return MqttVersion.MQTT_3_1_1 == mqttVersion;
        }

        @Override
        public Mqtt3Publish getAsMqtt3Publish() {
            return mqtt3Publish;
        }

        @Override
        public Mqtt5Publish getAsMqtt5Publish() {
            return GenericMqttPublish.getAsMqtt5Publish(this);
        }

        @Override
        public boolean equals(@Nullable final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final var that = (GenericMqtt3Publish) o;
            return Objects.equals(mqtt3Publish, that.mqtt3Publish);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mqtt3Publish);
        }

        @Override
        public String toString() {
            return mqtt3Publish.toString();
        }

    }

    private static final class GenericMqtt5Publish extends GenericMqttPublish {

        private final Mqtt5Publish mqtt5Publish;

        private GenericMqtt5Publish(final Mqtt5Publish mqtt5Publish) {
            this.mqtt5Publish = ConditionChecker.checkNotNull(mqtt5Publish, "mqtt5Publish");
        }

        @Override
        public MqttTopic getTopic() {
            return mqtt5Publish.getTopic();
        }

        @Override
        public MqttQos getQos() {
            return mqtt5Publish.getQos();
        }

        @Override
        public boolean isRetain() {
            return mqtt5Publish.isRetain();
        }

        @Override
        public Optional<ByteBuffer> getCorrelationData() {
            return mqtt5Publish.getCorrelationData();
        }

        @Override
        public Optional<MqttTopic> getResponseTopic() {
            return mqtt5Publish.getResponseTopic();
        }

        @Override
        public Optional<String> getContentType() {
            return mqtt5Publish.getContentType()
                    .map(MqttUtf8String::toString)
                    .or(() -> mqtt5Publish.getPayloadFormatIndicator()
                            .map(payloadFormatIndicator -> {
                                final ContentType contentType;
                                if (Mqtt5PayloadFormatIndicator.UTF_8 == payloadFormatIndicator) {
                                    contentType = ContentTypes.TEXT_PLAIN_UTF8;
                                } else {
                                    contentType = ContentTypes.APPLICATION_OCTET_STREAM;
                                }
                                return contentType;
                            })
                            .map(ContentType::toString));
        }

        @Override
        public Stream<UserProperty> userProperties() {
            final var mqtt5PublishUserProperties = mqtt5Publish.getUserProperties();
            final var mqtt5UserPropertiesAsList = mqtt5PublishUserProperties.asList();
            return mqtt5UserPropertiesAsList.stream()
                    .map(mqtt5UserProperty -> new UserProperty(
                            String.valueOf(mqtt5UserProperty.getName()),
                            String.valueOf(mqtt5UserProperty.getValue())
                    ));
        }

        @Override
        public Optional<ByteBuffer> getPayload() {
            return mqtt5Publish.getPayload()
                    .map(ByteBufferUtils::clone);
        }

        @Override
        public void acknowledge() throws ManualAcknowledgementDisabledException, MessageAlreadyAcknowledgedException {
            try {
                mqtt5Publish.acknowledge();
            } catch (final UnsupportedOperationException e) {
                throw new ManualAcknowledgementDisabledException(e);
            } catch (final IllegalStateException e) {
                throw new MessageAlreadyAcknowledgedException(e);
            }
        }

        @Override
        public boolean isMqttVersion(final MqttVersion mqttVersion) {
            return MqttVersion.MQTT_5_0 == mqttVersion;
        }

        @Override
        public Mqtt3Publish getAsMqtt3Publish() {
            return GenericMqttPublish.getAsMqtt3Publish(this);
        }

        @Override
        public Mqtt5Publish getAsMqtt5Publish() {
            return mqtt5Publish;
        }

        @Override
        public boolean equals(@Nullable final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final var that = (GenericMqtt5Publish) o;
            return Objects.equals(mqtt5Publish, that.mqtt5Publish);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mqtt5Publish);
        }

        @Override
        public String toString() {
            return mqtt5Publish.toString();
        }

    }

}
