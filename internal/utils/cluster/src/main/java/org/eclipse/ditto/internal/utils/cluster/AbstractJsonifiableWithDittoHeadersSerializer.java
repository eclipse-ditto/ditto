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
import java.time.Instant;
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
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
import org.eclipse.ditto.internal.utils.tracing.instruments.trace.StartedTrace;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonRuntimeException;
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
import kamon.context.Context;

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
    protected AbstractJsonifiableWithDittoHeadersSerializer(final int identifier, final ExtendedActorSystem actorSystem,
            final Function<Object, String> manifestProvider, final String serializerName) {

        this.identifier = identifier;
        this.serializerName = serializerName;

        mappingStrategies = MappingStrategies.loadMappingStrategies(actorSystem);
        this.manifestProvider = checkNotNull(manifestProvider, "manifestProvider");

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
            final Instant beforeSerializeInstant = DittoTracing.getTracingInstantNow();
            final JsonObjectBuilder jsonObjectBuilder = JsonObject.newBuilder();
            final DittoHeaders dittoHeaders = getDittoHeadersOrEmpty(object);

            final Context context = DittoTracing.extractTraceContext(dittoHeaders);
            final StartedTrace trace = DittoTracing.trace(context, "serialize")
                    .startAt(beforeSerializeInstant);
            dittoHeaders.getCorrelationId().ifPresent(trace::correlationId);
            final DittoHeaders dittoHeadersWithTraceContext =
                    DittoTracing.propagateContext(trace.getContext(), dittoHeaders);

            jsonObjectBuilder.set(JSON_DITTO_HEADERS, dittoHeadersWithTraceContext.toJson());

            final JsonValue jsonValue;

            if (object instanceof Jsonifiable.WithPredicate withPredicate) {
                final JsonSchemaVersion schemaVersion =
                        dittoHeaders.getSchemaVersion().orElse(JsonSchemaVersion.LATEST);

                jsonValue = withPredicate.toJson(schemaVersion, FieldType.regularOrSpecial());
            } else {
                jsonValue = ((Jsonifiable<?>) object).toJson();
            }

            jsonObjectBuilder.set(JSON_PAYLOAD, jsonValue);
            final JsonObject jsonObject = jsonObjectBuilder.build();
            try {
                serializeIntoByteBuffer(jsonObject, buf);
                LOG.trace("toBinary jsonStr about to send 'out': {}", jsonObject);
                outCounter.increment();
            } catch (final BufferOverflowException e) {
                final String errorMessage = MessageFormat.format(
                        "Could not put bytes of JSON string <{0}> into ByteBuffer due to BufferOverflow", jsonObject);
                LOG.error(errorMessage, e);
                trace.fail(e);
                throw new IllegalArgumentException(errorMessage, e);
            } catch (final IOException e) {
                final String errorMessage = MessageFormat.format(
                        "Serialization failed with {} on Jsonifiable with string representation <{}>",
                        e.getClass().getName(), jsonObject);
                LOG.warn(errorMessage, e);
                trace.fail(e);
                throw new RuntimeException(errorMessage, e);
            } finally {
                trace.finish();
            }
        } else {
            LOG.error("Could not serialize class <{}> as it does not implement <{}>!", object.getClass(),
                    Jsonifiable.WithPredicate.class);
            final String error = new NotSerializableException(object.getClass().getName()).getMessage();
            buf.put(CHARSET.encode(error));
        }
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
        final ByteBuffer buf = byteBufferPool.acquire();

        try {
            toBinary(object, buf);
            buf.flip();
            final byte[] bytes = new byte[buf.remaining()];
            buf.get(bytes);
            return bytes;
        } catch (final BufferOverflowException e) {
            final String errorMessage =
                    MessageFormat.format("BufferOverflow when serializing object <{0}>, max buffer size was: <{1}>",
                            object, defaultBufferSize);
            LOG.error(errorMessage, e);
            throw new IllegalArgumentException(errorMessage, e);
        } finally {
            byteBufferPool.release(buf);
        }
    }

    private static DittoHeaders getDittoHeadersOrEmpty(final Object object) {
        if (object instanceof WithDittoHeaders withDittoHeaders) {
            @Nullable final DittoHeaders dittoHeaders = withDittoHeaders.getDittoHeaders();
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
            final Jsonifiable<?> jsonifiable = tryToCreateKnownJsonifiableFrom(manifest, buf);
            if (LOG.isTraceEnabled()) {
                LOG.trace("fromBinary {} which got 'in': {}", serializerName,
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

    private Jsonifiable<?> tryToCreateKnownJsonifiableFrom(final String manifest, final ByteBuffer byteBuffer)
            throws NotSerializableException {
        try {
            return createJsonifiableFrom(manifest, byteBuffer);
        } catch (final DittoRuntimeException | JsonRuntimeException e) {
            LOG.error(
                    "Got <{}> during deserialization for manifest <{}> and serializer {} while processing message: <{}>.",
                    e.getClass().getSimpleName(), manifest, serializerName,
                    BinaryToHexConverter.createDebugMessageByTryingToConvertToHexString(byteBuffer), e);
            throw new NotSerializableException(manifest);
        }
    }

    private Jsonifiable<?> createJsonifiableFrom(final String manifest, final ByteBuffer bytebuffer)
            throws NotSerializableException {

        final Instant beforeDeserializeInstant = DittoTracing.getTracingInstantNow();
        final JsonValue jsonValue = deserializeFromByteBuffer(bytebuffer);

        final JsonObject jsonObject;
        if (jsonValue.isObject()) {
            jsonObject = jsonValue.asObject();
        } else if (jsonValue.isNull()) {
            jsonObject = JsonFactory.nullObject();
        } else {
            LOG.warn("Expected object but received value <{}> with manifest <{}> via {}", jsonValue, manifest,
                    serializerName);
            final String errorMessage = MessageFormat.format("<{}> is not a valid {} object! (It''s a value.)",
                    BinaryToHexConverter.createDebugMessageByTryingToConvertToHexString(bytebuffer), serializerName);
            throw JsonParseException.newBuilder().message(errorMessage).build();
        }

        final JsonObject payload = getPayload(jsonObject);

        final DittoHeadersBuilder<?, ?> dittoHeadersBuilder = jsonObject.getValue(JSON_DITTO_HEADERS)
                .map(DittoHeaders::newBuilder)
                .orElseGet(DittoHeaders::newBuilder);

        final DittoHeaders dittoHeaders = dittoHeadersBuilder.build();
        final StartedTrace trace = DittoTracing.trace(dittoHeaders, "deserialize")
                .startAt(beforeDeserializeInstant);
        try {
            final DittoHeaders dittoHeadersWithTraceContext =
                    DittoTracing.propagateContext(trace.getContext(), dittoHeaders);
            return deserializeJson(payload, manifest, dittoHeadersWithTraceContext);
        } finally {
            trace.finish();
        }
    }

    private Jsonifiable<?> deserializeJson(final JsonObject jsonPayload, final String manifest,
            final DittoHeaders dittoHeaders)
            throws NotSerializableException {
        final JsonParsable<Jsonifiable<?>> mappingStrategy = mappingStrategies.getMappingStrategy(manifest)
                .orElseThrow(() -> {
                    LOG.warn("No strategy found to map manifest <{}> to a Jsonifiable.WithPredicate!", manifest);
                    return new NotSerializableException(manifest);
                });
        return mappingStrategy.parse(jsonPayload, dittoHeaders, innerJson ->
                deserializeJson(innerJson, getDefaultManifest(innerJson), dittoHeaders));
    }

    private String getDefaultManifest(final JsonObject jsonObject) throws NotSerializableException {
        return jsonObject.getValue(Command.JsonFields.TYPE)
                .orElseThrow(() -> new NotSerializableException("No type found for inner JSON!"));
    }

    /**
     * Deserializes the passed {@code byteBuffer} into a JsonValue.
     *
     * @param byteBuffer the ByteBuffer to derserialize.
     * @return the deserialized JsonValue.
     */
    protected abstract JsonValue deserializeFromByteBuffer(ByteBuffer byteBuffer);

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
}
