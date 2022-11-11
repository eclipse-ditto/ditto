/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.cluster;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.io.IOException;
import java.io.NotSerializableException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.util.Map;

import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.Tag;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.TagSet;
import org.eclipse.ditto.json.CborFactory;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.json.cbor.BinaryToHexConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.io.BufferPool;
import akka.io.DirectByteBufferPool;
import akka.serialization.ByteBufferSerializer;
import akka.serialization.SerializerWithStringManifest;

/**
 * Serializer of Eclipse Ditto for {@link JsonValue}s via CBOR.
 */
public final class CborJsonValueSerializer extends SerializerWithStringManifest implements ByteBufferSerializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(CborJsonValueSerializer.class);

    /**
     * The unique identifier of this serializer.
     */
    static final int UNIQUE_IDENTIFIER = 709446437;

    /**
     * The manifest for supporting (de-)serialization of {@link JsonValue}s.
     */
    static final String JSON_VALUE_MANIFEST = "JsonValue";

    private static final String SERIALIZER_NAME = "CBOR_JSON_VALUE";

    private final DirectBufferConfig bufferConfig;
    private final BufferPool bufferPool;
    private final CborFactory cborFactory;
    private final Counters counters;

    /**
     * Constructs a new {@code CborJsonValueSerializer} object.
     *
     * @param actorSystem the actor system for getting the byte buffer config.
     */
    public CborJsonValueSerializer(final ExtendedActorSystem actorSystem) {
        bufferConfig = DirectBufferConfig.newInstance(actorSystem);
        bufferPool = new DirectByteBufferPool(bufferConfig.getBufferSize(), bufferConfig.getMaxPoolEntries());
        final var cborFactoryLoader = CborFactoryLoader.getInstance();
        cborFactory = cborFactoryLoader.getCborFactoryOrThrow();
        counters = Counters.newInstance();
    }

    @Override
    public int identifier() {
        return UNIQUE_IDENTIFIER;
    }

    @Override
    public String manifest(final Object o) {
        checkIfSerializable(o);
        return JSON_VALUE_MANIFEST;
    }

    private static void checkIfSerializable(final Object o) {
        if (!isJsonValue(o)) {
            throw new SerializerExceptions.NotSerializable(SERIALIZER_NAME, o);
        }
    }

    private static boolean isJsonValue(final Object o) {
        return o instanceof JsonValue;
    }

    @Override
    public byte[] toBinary(final Object o) {
        checkIfSerializable(o);
        final var byteBuffer = bufferPool.acquire();
        try {
            return toBinaryWithBuffer(o, byteBuffer);
        } finally {
            bufferPool.release(byteBuffer);
        }
    }

    private byte[] toBinaryWithBuffer(final Object o, final ByteBuffer byteBuffer) {
        toBinary(o, byteBuffer);
        byteBuffer.flip();
        final var bytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytes);
        return bytes;
    }

    @Override
    public void toBinary(final Object o, final ByteBuffer buf) {
        checkIfSerializable(o);
        final var jsonValue = (JsonValue) o;
        LOGGER.trace("Serializing <{}>.", jsonValue);
        tryToWriteJsonValueAsCborBinary(jsonValue, buf);
        counters.out.increment();
    }

    @SuppressWarnings("java:S3457")
    private void tryToWriteJsonValueAsCborBinary(final JsonValue jsonValue, final ByteBuffer byteBuffer) {
        try {
            cborFactory.writeToByteBuffer(jsonValue, byteBuffer);
        } catch (final BufferOverflowException e) {
            final var pattern = "Buffer overflow when serializing object <{0}>. Max buffer size was <{1}>.";
            final var message = MessageFormat.format(pattern, jsonValue, bufferConfig.getBufferSize());
            final var serializationFailedException = new SerializerExceptions.SerializationFailed(message, e);
            LOGGER.error("Serialization failed.", serializationFailedException);
            throw serializationFailedException;
        } catch (final IOException e) {
            final var pattern = "Failed to serialize <{0}>: {1}";
            final var message = MessageFormat.format(pattern, jsonValue, e.getMessage());
            final var serializationFailedException = new SerializerExceptions.SerializationFailed(message, e);
            LOGGER.error("Serialization failed.", serializationFailedException);
            throw serializationFailedException;
        }
    }

    @Override
    public JsonValue fromBinary(final byte[] bytes, final String manifest) throws NotSerializableException {
        return fromBinary(ByteBuffer.wrap(bytes), manifest);
    }

    @Override
    public JsonValue fromBinary(final ByteBuffer buf, final String manifest) throws NotSerializableException {
        validateManifest(manifest);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Deserializing <{}>.",
                    BinaryToHexConverter.createDebugMessageByTryingToConvertToHexString(buf));
        }
        final var result = tryToReadJsonValueFromCborBinary(buf, manifest);
        counters.in.increment();
        return result;
    }

    private static void validateManifest(final String manifest) {
        if (!JSON_VALUE_MANIFEST.equals(manifest)) {
            throw new SerializerExceptions.UnsupportedManifest(manifest);
        }
    }

    private JsonValue tryToReadJsonValueFromCborBinary(final ByteBuffer byteBuffer, final String manifest)
            throws NotSerializableException {

        try {
            return readJsonValueFromCborBinary(byteBuffer);
        } catch (final JsonParseException e) {
            LOGGER.error("Deserialization failed.", e);
            throw new NotSerializableException(manifest);
        }
    }

    private JsonValue readJsonValueFromCborBinary(final ByteBuffer byteBuffer) {
        return cborFactory.readFrom(byteBuffer);
    }

    private static final class DirectBufferConfig {

        private static final String KEY_PREFIX = "akka.actor.serializers-json";
        private static final String KEY_SIZE = KEY_PREFIX + ".direct-buffer-size";
        private static final String KEY_POOL_LIMIT = KEY_PREFIX + ".direct-buffer-pool-limit";
        private static final String DEFAULT_KEY_SIZE = "64 KiB";
        private static final String DEFAULT_KEY_POOL_LIMIT = "500";

        private final Config config;

        private DirectBufferConfig(final Config config) {
            this.config = config;
        }

        static DirectBufferConfig newInstance(final ExtendedActorSystem extendedActorSystem) {
            checkNotNull(extendedActorSystem, "extendedActorSystem");
            return new DirectBufferConfig(getActorSystemConfig(extendedActorSystem).withFallback(getFallbackConfig()));
        }

        private static Config getActorSystemConfig(final ActorSystem actorSystem) {
            final var settings = actorSystem.settings();
            return settings.config();
        }

        private static Config getFallbackConfig() {
            return ConfigFactory.parseMap(Map.of(KEY_SIZE, ConfigValueFactory.fromAnyRef(DEFAULT_KEY_SIZE),
                    KEY_POOL_LIMIT, ConfigValueFactory.fromAnyRef(DEFAULT_KEY_POOL_LIMIT)));
        }

        int getBufferSize() {
            final var bytes = config.getBytes(KEY_SIZE);
            return bytes.intValue();
        }

        int getMaxPoolEntries() {
            return config.getInt(KEY_POOL_LIMIT);
        }

    }

    private static final class Counters {

        private final Counter in;
        private final Counter out;

        private Counters(final Counter in, final Counter out) {
            this.in = in;
            this.out = out;
        }

        static Counters newInstance() {
            return new Counters(getCounter("in"), getCounter("out"));
        }

        private static Counter getCounter(final String metricDirection) {
            return DittoMetrics.counter(
                    SERIALIZER_NAME + "_serializer_messages",
                    TagSet.ofTag(Tag.of("direction", metricDirection))
            );
        }

    }

}
