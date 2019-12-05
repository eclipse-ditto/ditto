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
package org.eclipse.ditto.services.utils.cluster;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.NotSerializableException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.BinaryToHexConverter;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.counter.Counter;
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
 * Abstract {@link SerializerWithStringManifest} which handles serializing and deserializing {@link Jsonifiable}s
 * {@link WithDittoHeaders}.
 */
public abstract class AbstractJsonifiableWithDittoHeadersSerializer extends SerializerWithStringManifest
        implements ByteBufferSerializer {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractJsonifiableWithDittoHeadersSerializer.class);

    protected static final Charset CHARSET = StandardCharsets.UTF_8;

    private static final JsonFieldDefinition<JsonObject> JSON_DITTO_HEADERS =
            JsonFactory.newJsonObjectFieldDefinition("dittoHeaders");

    private static final JsonFieldDefinition<JsonValue> JSON_PAYLOAD =
            JsonFactory.newJsonValueFieldDefinition("payload");

    private static final String CONFIG_DIRECT_BUFFER_SIZE = "akka.actor.serializers-json.direct-buffer-size";
    private static final String CONFIG_DIRECT_BUFFER_POOL_LIMIT =
            "akka.actor.serializers-json.direct-buffer-pool-limit";

    private static final Config FALLBACK_CONF = ConfigFactory.empty()
            .withValue(CONFIG_DIRECT_BUFFER_SIZE, ConfigValueFactory.fromAnyRef("64 KiB"))
            .withValue(CONFIG_DIRECT_BUFFER_POOL_LIMIT, ConfigValueFactory.fromAnyRef("500"));

    private static final String METRIC_NAME_SUFFIX = "_serializer_messages";
    private static final String METRIC_DIRECTION = "direction";

    private final int identifier;
    private final MappingStrategies mappingStrategies;
    private final Function<Object, String> manifestProvider;
    private final BufferPool byteBufferPool;
    private final Long defaultBufferSize;
    private final Counter inCounter;
    private final Counter outCounter;
    private final String serializerName;

    /**
     * Constructs a new {@code AbstractJsonifiableWithDittoHeadersSerializer} object.
     *
     * @param serializerName A name to be used for this serializer when reporting metrics, in the log and in error messages.
     */
    protected AbstractJsonifiableWithDittoHeadersSerializer(final int identifier, final ExtendedActorSystem actorSystem,
            final Function<Object, String> manifestProvider, final String serializerName) {

        this.identifier = identifier;
        this.serializerName = serializerName;

        mappingStrategies = MappingStrategies.loadMappingStrategies(actorSystem);
        this.manifestProvider = requireNonNull(manifestProvider, "manifest provider");

        final ActorSystem.Settings settings = actorSystem.settings();
        final Config config = settings.config();
        defaultBufferSize = config.withFallback(FALLBACK_CONF).getBytes(CONFIG_DIRECT_BUFFER_SIZE);
        final int maxPoolEntries = config.withFallback(FALLBACK_CONF).getInt(CONFIG_DIRECT_BUFFER_POOL_LIMIT);
        byteBufferPool = new DirectByteBufferPool(defaultBufferSize.intValue(), maxPoolEntries);

        inCounter = DittoMetrics.counter(serializerName.toLowerCase() + METRIC_NAME_SUFFIX)
                .tag(METRIC_DIRECTION, "in");
        outCounter = DittoMetrics.counter(serializerName.toLowerCase() + METRIC_NAME_SUFFIX)
                .tag(METRIC_DIRECTION, "out");
    }

    @Override
    public int identifier() {
        return identifier;
    }

    @Override
    public String manifest(final Object o) {
        return manifestProvider.apply(o);
    }

    @Override
    public void toBinary(final Object object, final ByteBuffer buf) {
        if (object instanceof Jsonifiable) {
            final JsonObjectBuilder jsonObjectBuilder = JsonObject.newBuilder();
            final DittoHeaders dittoHeaders = getDittoHeadersOrEmpty(object);
            jsonObjectBuilder.set(JSON_DITTO_HEADERS, dittoHeaders.toJson());

            final JsonValue jsonValue;

            if (object instanceof Jsonifiable.WithPredicate) {
                final JsonSchemaVersion schemaVersion =
                        dittoHeaders.getSchemaVersion().orElse(JsonSchemaVersion.LATEST);

                jsonValue = ((Jsonifiable.WithPredicate) object).toJson(schemaVersion, FieldType.regularOrSpecial());
            } else {
                jsonValue = ((Jsonifiable) object).toJson();
            }

            jsonObjectBuilder.set(JSON_PAYLOAD, jsonValue);
            final JsonObject jsonObject = jsonObjectBuilder.build();
            try {
                serializeIntoByteBuffer(jsonObject, buf);
                if (LOG.isTraceEnabled()){
                    LOG.trace("toBinary jsonStr about to send 'out': {}", jsonObject.toString());
                }
                outCounter.increment();
            } catch (final BufferOverflowException e) {
                LOG.warn("Could not put bytes of JSON string <{}> into ByteBuffer due to BufferOverflow", jsonObject.toString(), e);
                throw e;
            } catch (IOException e) {
                final String errorMessage = MessageFormat.format(
                        "Serialization failed with {} on Jsonifiable with string representation <{}>",
                        e.getClass().getName(), jsonObject);
                LOG.warn(errorMessage, e);
                throw new RuntimeException(errorMessage, e);
            }
        } else {
            LOG.error("Could not serialize class <{}> as it does not implement <{}>!", object.getClass(),
                    Jsonifiable.WithPredicate.class);
            final String error = new NotSerializableException(object.getClass().getName()).getMessage();
            buf.put(CHARSET.encode(error));
        }
    }

    protected abstract void serializeIntoByteBuffer(JsonObject jsonObject, ByteBuffer byteBuffer) throws IOException;

    @Override
    public byte[] toBinary(final Object object) {
        final ByteBuffer buf = byteBufferPool.acquire();

        try {
            toBinary(object, buf);
            buf.flip();
            final byte[] bytes = new byte[buf.remaining()];
            buf.get(bytes);
            return bytes;
        } catch (final BufferOverflowException e) {
            LOG.error("BufferOverflow when serializing object <{}>, max buffer size was: <{}>", object,
                    defaultBufferSize, e);
            throw new IllegalArgumentException(e);
        } finally {
            byteBufferPool.release(buf);
        }
    }

    private static DittoHeaders getDittoHeadersOrEmpty(final Object object) {
        if (object instanceof WithDittoHeaders) {
            @Nullable final DittoHeaders dittoHeaders = ((WithDittoHeaders) object).getDittoHeaders();
            if (null != dittoHeaders) {
                return dittoHeaders;
            }
            LOG.warn("Object <{}> did not contain DittoHeaders although it should! Using empty DittoHeaders instead.",
                    object);
        }
        return DittoHeaders.empty();
    }

    @Override
    public Object fromBinary(final ByteBuffer buf, final String manifest) {
        try {
            final Jsonifiable jsonifiable = tryToCreateKnownJsonifiableFrom(manifest, buf);
            if (LOG.isTraceEnabled()){
                LOG.trace("fromBinary {} which got 'in': {}", serializerName,tryToConvertToHexString(buf));
            }
            inCounter.increment();
            return jsonifiable;
        } catch (final NotSerializableException e) {
            return e;
        }
    }

    @Override
    public Object fromBinary(final byte[] bytes, final String manifest) {
        return fromBinary(ByteBuffer.wrap(bytes), manifest);
    }

    private Jsonifiable tryToCreateKnownJsonifiableFrom(final String manifest, final ByteBuffer byteBuffer)
            throws NotSerializableException {
        try {
            return createJsonifiableFrom(manifest, byteBuffer);
        } catch (final DittoRuntimeException | JsonRuntimeException | IOException e) {
            LOG.error("Got <{}> during deserialization for manifest <{}> and serializer {} while processing message: <{}>.",
                    e.getClass().getSimpleName(), manifest, serializerName, tryToConvertToHexString(byteBuffer), e);
            throw new NotSerializableException(manifest);
        }
    }

    private Jsonifiable createJsonifiableFrom(final String manifest, final ByteBuffer bytebuffer)
            throws IOException {

        final Optional<MappingStrategy> mappingStrategy = this.mappingStrategies.getMappingStrategyFor(manifest);

        if (!mappingStrategy.isPresent()) {
            LOG.warn("No strategy found to map manifest <{}> to a Jsonifiable.WithPredicate!", manifest);
            throw new NotSerializableException(manifest);
        }

        final JsonValue jsonValue = deserializeFromByteBuffer(bytebuffer);
        final JsonObject jsonObject;
        if (jsonValue.isObject()) {
            jsonObject = jsonValue.asObject();
        } else {
            LOG.warn("Expected object but received value <{}> with manifest <{}> via {}", jsonValue, manifest, serializerName);
            final String errorMessage = MessageFormat.format("<{}> is not a valid {} object! (It's a value.)",
                    tryToConvertToHexString(bytebuffer), serializerName);
            throw JsonParseException.newBuilder().message(errorMessage).build();
        }

        final JsonObject payload = getPayload(jsonObject);

        final DittoHeadersBuilder dittoHeadersBuilder = jsonObject.getValue(JSON_DITTO_HEADERS)
                .map(DittoHeaders::newBuilder)
                .orElseGet(DittoHeaders::newBuilder);

        return mappingStrategy.get().map(payload, dittoHeadersBuilder.build());
    }

    protected abstract JsonValue deserializeFromByteBuffer(ByteBuffer byteBuffer) throws IOException;

    private static JsonObject getPayload(final JsonObject sourceJsonObject) {
        final JsonObject result;

        final Optional<JsonValue> payloadJsonOptional = sourceJsonObject.getValue(JSON_PAYLOAD);
        if (payloadJsonOptional.isPresent()) {
            final JsonValue payloadJson = payloadJsonOptional.get();
            if (!payloadJson.isObject()) {
                final String msgPattern = "Value <{0}> for <{1}> was not of type <{2}>!";
                final String simpleName = JSON_PAYLOAD.getValueType().getSimpleName();
                final String msg = MessageFormat.format(msgPattern, payloadJson, JSON_PAYLOAD.getPointer(), simpleName);
                throw new DittoJsonException(new IllegalArgumentException(msg));
            } else {
                result = payloadJson.asObject();
            }
        } else {
            result = JsonFactory.newObject();
        }

        return result;
    }

    private static String tryToConvertToHexString(ByteBuffer byteBuffer){
        try {
            return BinaryToHexConverter.toHexString(byteBuffer);
        } catch (IOException e) {
            return "Could not convert ByteBuffer to String due to " + e.getClass().getSimpleName();
        }
    }

}
