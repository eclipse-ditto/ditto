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
package org.eclipse.ditto.internal.utils.cluster;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

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

import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.base.model.signals.JsonParsable;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.Tag;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartInstant;
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
import org.eclipse.ditto.internal.utils.tracing.span.SpanOperationName;
import org.eclipse.ditto.internal.utils.tracing.span.StartedSpan;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.json.cbor.BinaryToHexConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

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
     * @param identifier a unique identifier identifying the serializer.
     * @param actorSystem the ExtendedActorSystem to use in order to dynamically load mapping strategies.
     * @param manifestProvider a function for retrieving string manifest information from arbitrary to map objects.
     * @param serializerName a name to be used for this serializer when reporting metrics, in the log and in error
     * messages.
     */
    protected AbstractJsonifiableWithDittoHeadersSerializer(
            final int identifier,
            final ExtendedActorSystem actorSystem,
            final Function<Object, String> manifestProvider,
            final String serializerName
    ) {
        this.identifier = identifier;
        this.serializerName = serializerName;

        mappingStrategies = MappingStrategies.loadMappingStrategies(actorSystem);
        this.manifestProvider = checkNotNull(manifestProvider, "manifestProvider");

        final var settings = actorSystem.settings();
        final var config = settings.config();
        defaultBufferSize = config.withFallback(FALLBACK_CONF).getBytes(CONFIG_DIRECT_BUFFER_SIZE);
        final var maxPoolEntries = config.withFallback(FALLBACK_CONF).getInt(CONFIG_DIRECT_BUFFER_POOL_LIMIT);
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
        if (object instanceof Jsonifiable<? extends JsonValue> jsonifiable) {
            final var dittoHeaders = getDittoHeadersOrEmpty(object);
            final var startedSpan = startTracingSpanForSerialization(dittoHeaders, object.getClass());
            final var jsonObject = JsonObject.newBuilder()
                    .set(JSON_DITTO_HEADERS, getDittoHeadersWithSpanContextAsJson(dittoHeaders, startedSpan))
                    .set(JSON_PAYLOAD, getAsJsonPayload(jsonifiable, dittoHeaders))
                    .build();
            try {
                serializeIntoByteBuffer(jsonObject, buf);
                LOG.trace("toBinary jsonStr about to send 'out': {}", jsonObject);
                outCounter.increment();
            } catch (final BufferOverflowException e) {
                final var errorMessage = MessageFormat.format(
                        "Could not put bytes of JSON string <{0}> into ByteBuffer due to BufferOverflow",
                        jsonObject
                );
                LOG.error(errorMessage, e);
                startedSpan.tagAsFailed(e);
                throw new IllegalArgumentException(errorMessage, e);
            } catch (final IOException e) {
                final var errorMessage = MessageFormat.format(
                        "Serialization failed with {} on Jsonifiable with string representation <{}>",
                        e.getClass().getName(),
                        jsonObject
                );
                LOG.warn(errorMessage, e);
                startedSpan.tagAsFailed(e);
                throw new RuntimeException(errorMessage, e);
            } finally {
                startedSpan.finish();
            }
        } else {
            LOG.error("Could not serialize class <{}> as it does not implement <{}>!",
                    object.getClass(),
                    Jsonifiable.WithPredicate.class);
            final var error = new NotSerializableException(object.getClass().getName()).getMessage();
            buf.put(CHARSET.encode(error));
        }
    }

    private static StartedSpan startTracingSpanForSerialization(
            final DittoHeaders dittoHeaders,
            final Class<?> typeToSerialize
    ) {
        final var startInstant = StartInstant.now();
        return DittoTracing.newPreparedSpan(
                        dittoHeaders,
                        SpanOperationName.of("serialize " + typeToSerialize.getSimpleName())
                )
                .correlationId(dittoHeaders.getCorrelationId().orElse(null))
                .startAt(startInstant);
    }

    private static JsonObject getDittoHeadersWithSpanContextAsJson(
            final DittoHeaders dittoHeaders,
            final StartedSpan startedSpan
    ) {
        final var dittoHeadersWithSpanContext = DittoHeaders.of(startedSpan.propagateContext(dittoHeaders));
        return dittoHeadersWithSpanContext.toJson();
    }

    @SuppressWarnings("java:S3740")
    private static JsonValue getAsJsonPayload(
            final Jsonifiable<? extends JsonValue> jsonifiable,
            final DittoHeaders dittoHeaders
    ) {
        final JsonValue result;
        if (jsonifiable instanceof Jsonifiable.WithPredicate withPredicate) {
            result = withPredicate.toJson(
                    dittoHeaders.getSchemaVersion().orElse(JsonSchemaVersion.LATEST),
                    FieldType.regularOrSpecial()
            );
        } else {
            result = jsonifiable.toJson();
        }
        return result;
    }

    /**
     * Serializes the passed {@code jsonObject} into the passed {@code byteBuffer}.
     *
     * @param jsonObject the JsonObject to serialize.
     * @param byteBuffer the ByteBuffer to serialize into.
     * @throws IOException in case writing to the ByteBuffer fails.
     */
    protected abstract void serializeIntoByteBuffer(JsonObject jsonObject, ByteBuffer byteBuffer) throws IOException;

    @Override
    public byte[] toBinary(final Object object) {
        final var byteBuffer = byteBufferPool.acquire();

        try {
            toBinary(object, byteBuffer);
            byteBuffer.flip();
            final var bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);
            return bytes;
        } catch (final BufferOverflowException e) {
            final var errorMessage = MessageFormat.format(
                    "BufferOverflow when serializing object <{0}>, max buffer size was: <{1}>",
                    object,
                    defaultBufferSize
            );
            LOG.error(errorMessage, e);
            throw new IllegalArgumentException(errorMessage, e);
        } finally {
            byteBufferPool.release(byteBuffer);
        }
    }

    private static DittoHeaders getDittoHeadersOrEmpty(final Object object) {
        if (object instanceof WithDittoHeaders withDittoHeaders) {
            @Nullable final var dittoHeaders = withDittoHeaders.getDittoHeaders();
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
            final var jsonifiable = tryToCreateKnownJsonifiableFrom(manifest, buf);
            if (LOG.isTraceEnabled()) {
                LOG.trace("fromBinary {} which got 'in': {}",
                        serializerName,
                        BinaryToHexConverter.createDebugMessageByTryingToConvertToHexString(buf));
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

    private Jsonifiable<?> tryToCreateKnownJsonifiableFrom(
            final String manifest,
            final ByteBuffer byteBuffer
    ) throws NotSerializableException {
        try {
            return createJsonifiableFrom(manifest, byteBuffer);
        } catch (final DittoRuntimeException | JsonRuntimeException e) {
            LOG.error(
                    "Got <{}> during deserialization for manifest <{}> and serializer {} while processing message: <{}>.",
                    e.getClass().getSimpleName(),
                    manifest,
                    serializerName,
                    BinaryToHexConverter.createDebugMessageByTryingToConvertToHexString(byteBuffer),
                    e
            );
            throw new NotSerializableException(manifest);
        }
    }

    private Jsonifiable<?> createJsonifiableFrom(
            final String manifest,
            final ByteBuffer byteBuffer
    ) throws NotSerializableException {
        final var beforeDeserializeInstant = StartInstant.now();
        final var jsonObject = deserializeByteBufferAsJsonObjectOrThrow(byteBuffer, manifest);
        final var dittoHeaders = deserializeDittoHeaders(jsonObject);
        final var payload = deserializePayloadAsJsonObject(jsonObject, dittoHeaders);
        final var signalTypeOrErrorCodeOptional = getSignalTypeOrErrorCodeIfPresent(payload);
        final var startedSpan = startTracingSpanForDeserialization(
                dittoHeaders,
                signalTypeOrErrorCodeOptional.orElse(""),
                beforeDeserializeInstant
        );
        final var result =
                deserializeJson(payload, manifest, DittoHeaders.of(startedSpan.propagateContext(dittoHeaders)));
        try {
            return result;
        } finally {
            if (signalTypeOrErrorCodeOptional.isEmpty()) {
                startedSpan.tag(Tag.of("type", result.getClass().getSimpleName()));
            }
            startedSpan.finish();
        }
    }
    
    private JsonObject deserializeByteBufferAsJsonObjectOrThrow(final ByteBuffer byteBuffer, final String manifest) {
        final JsonObject result;
        final var jsonValue = deserializeFromByteBuffer(byteBuffer);
        if (jsonValue.isObject()) {
            result = jsonValue.asObject();
        } else if (jsonValue.isNull()) {
            result = JsonFactory.nullObject();
        } else {
            LOG.warn("Expected object but received value <{}> with manifest <{}> via {}",
                    jsonValue,
                    manifest,
                    serializerName);
            throw JsonParseException.newBuilder()
                    .message(MessageFormat.format("<{}> is not a valid {} object! (It''s a value.)",
                            BinaryToHexConverter.createDebugMessageByTryingToConvertToHexString(byteBuffer),
                            serializerName))
                    .build();
        }
        return result;
    }

    /**
     * Deserializes the passed {@code byteBuffer} into a JsonValue.
     *
     * @param byteBuffer the ByteBuffer to deserialize.
     * @return the deserialized JsonValue.
     */
    protected abstract JsonValue deserializeFromByteBuffer(ByteBuffer byteBuffer);

    private static DittoHeaders deserializeDittoHeaders(final JsonObject jsonObject) {
        return jsonObject.getValue(JSON_DITTO_HEADERS)
                .map(DittoHeaders::newBuilder)
                .map(DittoHeadersBuilder::build)
                .orElseGet(DittoHeaders::empty);
    }

    private static JsonObject deserializePayloadAsJsonObject(
            final JsonObject sourceJsonObject,
            final DittoHeaders dittoHeaders
    ) {
        final JsonObject result;
        final var payloadJsonOptional = sourceJsonObject.getValue(JSON_PAYLOAD);
        if (payloadJsonOptional.isPresent()) {
            final var payloadJson = payloadJsonOptional.get();
            if (!payloadJson.isObject()) {
                throw new DittoJsonException(
                        JsonParseException.newBuilder()
                                .message(MessageFormat.format("Value <{0}> for <{1}> was not of type <{2}>!",
                                        payloadJson,
                                        JSON_PAYLOAD.getPointer(),
                                        JSON_PAYLOAD.getValueType().getSimpleName()))
                                .build(),
                        dittoHeaders
                );
            } else {
                result = payloadJson.asObject();
            }
        } else {
            result = JsonFactory.newObject();
        }

        return result;
    }

    private static Optional<String> getSignalTypeOrErrorCodeIfPresent(final JsonObject jsonObject) {
        return jsonObject.getValue("type")
                .or(() -> jsonObject.getValue("error"))
                .filter(JsonValue::isString)
                .map(JsonValue::asString);
    }

    private static StartedSpan startTracingSpanForDeserialization(
            final DittoHeaders dittoHeaders,
            final String signalTypeOrErrorCode,
            final StartInstant startInstant
    ) {
        return DittoTracing.newPreparedSpan(dittoHeaders, SpanOperationName.of("deserialize " + signalTypeOrErrorCode))
                .correlationId(dittoHeaders.getCorrelationId().orElse(null))
                .startAt(startInstant);
    }

    private Jsonifiable<?> deserializeJson(
            final JsonObject jsonPayload,
            final String manifest,
            final DittoHeaders dittoHeaders
    ) throws NotSerializableException {
        final var mappingStrategy = getMappingStrategyOrThrow(manifest);
        return mappingStrategy.parse(
                jsonPayload,
                dittoHeaders,
                innerJson -> deserializeJson(innerJson, getDefaultManifestOrThrow(innerJson), dittoHeaders)
        );
    }

    private JsonParsable<Jsonifiable<?>> getMappingStrategyOrThrow(
            final String manifest
    ) throws NotSerializableException {
        return mappingStrategies.getMappingStrategy(manifest)
                .orElseThrow(() -> {
                    LOG.warn("No strategy found to map manifest <{}> to a Jsonifiable.WithPredicate!", manifest);
                    return new NotSerializableException(manifest);
                });
    }

    private static String getDefaultManifestOrThrow(final JsonObject jsonObject) throws NotSerializableException {
        return jsonObject.getValue(Command.JsonFields.TYPE)
                .orElseThrow(() -> new NotSerializableException("No type found for inner JSON!"));
    }

}
