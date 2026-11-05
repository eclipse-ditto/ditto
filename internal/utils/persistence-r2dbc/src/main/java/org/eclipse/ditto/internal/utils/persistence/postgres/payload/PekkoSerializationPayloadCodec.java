/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.postgres.payload;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Base64;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

import org.apache.pekko.serialization.Serialization;
import org.apache.pekko.serialization.Serializer;
import org.apache.pekko.serialization.Serializers;

import scala.util.Try;

/**
 * The {@link PostgresPayloadCodec} for payloads that are Pekko-serialized bytes by nature: cluster sharding's
 * remember-entities events and state, which Pekko writes through the journal and snapshot store of connectivity's
 * shard region. The JSONB journal cannot interpret them, so this codec wraps them in a JSON envelope
 * <pre>{@code {"serializerId": <int>, "manifest": "<string>", "bytes": "<base64>"}}</pre>
 * on write — the three values Pekko's {@link Serialization} needs to deserialize again — and unwraps them on read, so
 * the persistent actor receives the original message. A payload Pekko cannot serialize is rejected on write; stored
 * text that is not such an envelope, or names a serializer the actor system does not know, is rejected on read.
 */
@Immutable
public final class PekkoSerializationPayloadCodec implements PostgresPayloadCodec {

    static final String SERIALIZER_ID = "serializerId";
    static final String MANIFEST = "manifest";
    static final String BYTES = "bytes";

    private final Serialization serialization;

    private PekkoSerializationPayloadCodec(final Serialization serialization) {
        this.serialization = serialization;
    }

    /**
     * @param serialization the actor system's Pekko serialization.
     * @return the codec.
     */
    public static PekkoSerializationPayloadCodec of(final Serialization serialization) {
        return new PekkoSerializationPayloadCodec(checkNotNull(serialization, "serialization"));
    }

    @Override
    public String encode(final Object payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Unable to Pekko-serialize a null payload for the PostgreSQL envelope.");
        }
        final Serializer serializer;
        final String manifest;
        final byte[] bytes;
        try {
            serializer = serialization.findSerializerFor(payload);
            manifest = Serializers.manifestFor(serializer, payload);
            bytes = serializer.toBinary(payload);
        } catch (final Exception e) {
            throw new IllegalArgumentException("Unable to Pekko-serialize payload of type <" + payload.getClass()
                    + "> for the PostgreSQL envelope: " + e.getMessage(), e);
        }
        return JsonFactory.newObjectBuilder()
                .set(SERIALIZER_ID, serializer.identifier())
                .set(MANIFEST, manifest)
                .set(BYTES, Base64.getEncoder().encodeToString(bytes))
                .build()
                .toString();
    }

    @Override
    public Object decode(final String storedJson) {
        if (storedJson == null) {
            throw notAnEnvelope("null", null);
        }
        final JsonObject envelope = parseEnvelope(storedJson);
        final int serializerId = envelope.getValue(SERIALIZER_ID)
                .filter(JsonValue::isInt)
                .map(JsonValue::asInt)
                .orElseThrow(() -> notAnEnvelope(storedJson, null));
        final String manifest = envelope.getValue(MANIFEST)
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .orElseThrow(() -> notAnEnvelope(storedJson, null));
        final String base64 = envelope.getValue(BYTES)
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .orElseThrow(() -> notAnEnvelope(storedJson, null));
        final byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (final IllegalArgumentException invalidBase64) {
            throw notAnEnvelope(storedJson, invalidBase64);
        }
        final Try<Object> deserialized = serialization.deserialize(bytes, serializerId, manifest);
        if (deserialized.isFailure()) {
            throw new IllegalArgumentException("Unable to deserialize the Pekko-serialized payload of the PostgreSQL "
                    + "envelope (serializerId <" + serializerId + ">, manifest <" + manifest + ">): "
                    + deserialized.failed().get().getMessage(), deserialized.failed().get());
        }
        return deserialized.get();
    }

    private static JsonObject parseEnvelope(final String storedJson) {
        final JsonValue parsed;
        try {
            parsed = JsonFactory.readFrom(storedJson);
        } catch (final RuntimeException notJson) {
            throw notAnEnvelope(storedJson, notJson);
        }
        if (!parsed.isObject()) {
            throw notAnEnvelope(storedJson, null);
        }
        return parsed.asObject();
    }

    private static IllegalArgumentException notAnEnvelope(final String storedJson, @Nullable final Throwable cause) {
        final String preview = storedJson.length() > 120 ? storedJson.substring(0, 120) + "…" : storedJson;
        return new IllegalArgumentException("Stored payload is not a Pekko-serialization envelope {\"" + SERIALIZER_ID
                + "\": <int>, \"" + MANIFEST + "\": <string>, \"" + BYTES + "\": <base64>}: <" + preview + ">", cause);
    }

}
