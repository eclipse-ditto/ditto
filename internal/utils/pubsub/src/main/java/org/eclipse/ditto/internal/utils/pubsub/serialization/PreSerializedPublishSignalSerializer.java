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
package org.eclipse.ditto.internal.utils.pubsub.serialization;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.serialization.ByteBufferSerializer;
import org.apache.pekko.serialization.SerializerWithStringManifest;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.internal.utils.cluster.CborJsonifiableSerializer;
import org.eclipse.ditto.internal.utils.pubsub.api.PreSerializedPublishSignal;
import org.eclipse.ditto.internal.utils.pubsub.api.PublishSignal;
import org.eclipse.ditto.internal.utils.pubsub.api.SignalBytesHolder;

/**
 * Serializer for {@link PreSerializedPublishSignal}: the transport envelope that lets one published signal be
 * serialized once and reused across all remote fan-out destinations.
 * <p>
 * On {@code toBinary} the (identical) signal payload is obtained from the shared {@link SignalBytesHolder}, which
 * serializes it exactly once (via the standard {@link CborJsonifiableSerializer}) and memoizes the result; each
 * destination only re-appends the small, per-destination {@code groups} map. On {@code fromBinary} the signal is
 * deserialized once and a plain {@link PublishSignal} is reconstructed, so the receiving {@code Subscriber} actor is
 * unchanged.
 * <p>
 * Wire frame (all lengths are 4-byte big-endian ints; strings are length-prefixed UTF-8):
 * <pre>
 *   [signalManifest][groupIndexKey][groupCount]([groupKey][groupSize])*[signalBytesLength][signalBytes]
 * </pre>
 * The signal payload bytes are produced by {@link CborJsonifiableSerializer}; the frame carries that serializer's
 * string-manifest so the same serializer can reconstruct the signal on the receiving side.
 *
 * @since 3.9.4
 */
public final class PreSerializedPublishSignalSerializer extends SerializerWithStringManifest
        implements ByteBufferSerializer {

    /**
     * Unique serializer identifier (distinct from {@link CborJsonifiableSerializer}'s {@code 656329405}).
     */
    static final int UNIQUE_IDENTIFIER = 656329406;

    private static final String MANIFEST = "PreSerializedPublishSignal";

    private final CborJsonifiableSerializer innerSerializer;

    /**
     * Constructs the serializer. Pekko instantiates serializers with the {@link ExtendedActorSystem} constructor.
     *
     * @param actorSystem the actor system, used to construct the inner CBOR serializer for signal payloads.
     */
    public PreSerializedPublishSignalSerializer(final ExtendedActorSystem actorSystem) {
        // A plain construction (not a Serialization lookup), so no SerializationExtension re-entrancy.
        this.innerSerializer = new CborJsonifiableSerializer(actorSystem);
    }

    @Override
    public int identifier() {
        return UNIQUE_IDENTIFIER;
    }

    @Override
    public String manifest(final Object o) {
        return MANIFEST;
    }

    @Override
    public void toBinary(final Object o, final ByteBuffer buf) {
        writeInto(buf, castToEnvelope(o));
    }

    @Override
    public byte[] toBinary(final Object o) {
        final PreSerializedPublishSignal envelope = castToEnvelope(o);
        final ByteBuffer buf = ByteBuffer.allocate(computeSize(envelope));
        writeInto(buf, envelope);
        return buf.array();
    }

    @Override
    public Object fromBinary(final ByteBuffer buf, final String manifest) {
        final String signalManifest = getString(buf);
        final String groupIndexKey = getString(buf);
        final int groupCount = buf.getInt();
        final Map<String, Integer> groups = new HashMap<>(Math.max(1, groupCount));
        for (int i = 0; i < groupCount; i++) {
            final String key = getString(buf);
            final int size = buf.getInt();
            groups.put(key, size);
        }
        final int signalBytesLength = buf.getInt();
        final byte[] signalBytes = new byte[signalBytesLength];
        buf.get(signalBytes);
        // CborJsonifiableSerializer.fromBinary returns a NotSerializableException instance (rather than throwing)
        // when the manifest is unknown/unparseable, so guard the cast instead of letting it fail with an opaque
        // ClassCastException (e.g. an unknown signal type reaching a not-yet-upgraded node during rollout).
        final Object deserializedPayload = innerSerializer.fromBinary(signalBytes, signalManifest);
        if (deserializedPayload instanceof Signal<?> signal) {
            return PublishSignal.of(signal, groups, groupIndexKey);
        }
        throw new IllegalArgumentException(
                "Cannot reconstruct PublishSignal: pre-serialized payload with manifest <" + signalManifest +
                        "> did not deserialize to a Signal but to <" +
                        (deserializedPayload == null ? "null" : deserializedPayload.getClass().getName()) + ">");
    }

    @Override
    public Object fromBinary(final byte[] bytes, final String manifest) {
        return fromBinary(ByteBuffer.wrap(bytes), manifest);
    }

    private void writeInto(final ByteBuffer buf, final PreSerializedPublishSignal envelope) {
        final SignalBytesHolder.Serialized serialized = envelope.getHolder().getOrCompute(innerSerializer);
        putString(buf, serialized.manifest());
        putString(buf, envelope.getGroupIndexKey());
        final Map<String, Integer> groups = envelope.getGroups();
        buf.putInt(groups.size());
        for (final Map.Entry<String, Integer> entry : groups.entrySet()) {
            putString(buf, entry.getKey());
            buf.putInt(entry.getValue());
        }
        final byte[] signalBytes = serialized.bytes();
        buf.putInt(signalBytes.length);
        buf.put(signalBytes);
    }

    private int computeSize(final PreSerializedPublishSignal envelope) {
        final SignalBytesHolder.Serialized serialized = envelope.getHolder().getOrCompute(innerSerializer);
        int size = sizeOfString(serialized.manifest()) + sizeOfString(envelope.getGroupIndexKey());
        size += Integer.BYTES; // group count
        for (final Map.Entry<String, Integer> entry : envelope.getGroups().entrySet()) {
            size += sizeOfString(entry.getKey()) + Integer.BYTES;
        }
        size += Integer.BYTES + serialized.bytes().length; // signal bytes length + bytes
        return size;
    }

    private static PreSerializedPublishSignal castToEnvelope(final Object o) {
        if (o instanceof final PreSerializedPublishSignal envelope) {
            return envelope;
        }
        throw new IllegalArgumentException("Cannot serialize object of type <" + o.getClass().getName() +
                "> with " + PreSerializedPublishSignalSerializer.class.getSimpleName());
    }

    private static int sizeOfString(final String s) {
        return Integer.BYTES + s.getBytes(StandardCharsets.UTF_8).length;
    }

    private static void putString(final ByteBuffer buf, final String s) {
        final byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.putInt(bytes.length);
        buf.put(bytes);
    }

    private static String getString(final ByteBuffer buf) {
        final int length = buf.getInt();
        final byte[] bytes = new byte[length];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
