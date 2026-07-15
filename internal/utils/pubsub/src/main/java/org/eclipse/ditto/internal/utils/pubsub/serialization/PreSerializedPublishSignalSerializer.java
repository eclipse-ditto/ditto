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

import java.io.NotSerializableException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
        // IMPORTANT: never change the order of this buffer. Artery hands us one of its pooled, reused envelope
        // buffers (little-endian) and relies on it staying little-endian to write/read the frame headers of the
        // *next* messages that reuse it — mutating the order here corrupts those frames (observed as
        // BufferUnderflowException on the remote Artery Decoder, dropped messages, and ddata/pub-sub timeouts).
        // We therefore write in the buffer's native little-endian order and pin the same order on the byte[] path's
        // own buffer below, so both entry points stay interoperable without ever touching a buffer we don't own.
        writeFrame(buf, encode(castToEnvelope(o)));
    }

    @Override
    public byte[] toBinary(final Object o) {
        final EncodedFrame frame = encode(castToEnvelope(o));
        // Match Artery's little-endian framing (ByteBuffer.allocate defaults to big-endian). This is our own buffer,
        // so setting the order here is safe.
        final ByteBuffer buf = ByteBuffer.allocate(frame.size()).order(ByteOrder.LITTLE_ENDIAN);
        writeFrame(buf, frame);
        return buf.array();
    }

    @Override
    public Object fromBinary(final ByteBuffer buf, final String manifest) {
        // Read in the buffer's native order without mutating it (see toBinary(Object, ByteBuffer)): Artery passes a
        // little-endian pooled buffer, and the byte[] entry point below wraps with the same order.
        try {
            final String signalManifest = getString(buf);
            final String groupIndexKey = getString(buf);
            final int groupCount = buf.getInt();
            // The frame comes off the (untrusted) wire, so validate every length prefix against the bytes that
            // actually remain before allocating, to avoid an OutOfMemoryError / NegativeArraySizeException on a
            // truncated, corrupted or misrouted frame. Each group entry needs at least one byte, so groupCount can
            // never legitimately exceed the remaining byte count.
            if (groupCount < 0 || groupCount > buf.remaining()) {
                throw new IllegalArgumentException("Invalid group count <" + groupCount + "> (remaining: " +
                        buf.remaining() + ")");
            }
            final Map<String, Integer> groups = new HashMap<>(Math.max(1, groupCount));
            for (int i = 0; i < groupCount; i++) {
                final String key = getString(buf);
                final int size = buf.getInt();
                groups.put(key, size);
            }
            final int signalBytesLength = buf.getInt();
            if (signalBytesLength < 0 || signalBytesLength > buf.remaining()) {
                throw new IllegalArgumentException("Invalid signal bytes length <" + signalBytesLength +
                        "> (remaining: " + buf.remaining() + ")");
            }
            final byte[] signalBytes = new byte[signalBytesLength];
            buf.get(signalBytes);
            final Object deserializedPayload = innerSerializer.fromBinary(signalBytes, signalManifest);
            if (deserializedPayload instanceof Signal<?> signal) {
                return PublishSignal.of(signal, groups, groupIndexKey);
            }
            // CborJsonifiableSerializer.fromBinary returns a NotSerializableException instance (rather than
            // throwing) when the inner manifest is unknown/unparseable, e.g. a newer signal type reaching a
            // not-yet-upgraded node during rollout. Propagate that instance unchanged so this behaves exactly like
            // the plain PublishSignal path instead of raising an uncaught error on the Artery decoder thread.
            return deserializedPayload;
        } catch (final RuntimeException e) {
            // Truncated / corrupted / misrouted frame: surface a clean, tolerated deserialization failure
            // (matching the inner serializer's NotSerializableException contract) rather than letting a raw
            // BufferUnderflowException / NegativeArraySizeException escape onto the Artery inbound path.
            final NotSerializableException notSerializable = new NotSerializableException(MANIFEST);
            notSerializable.initCause(e);
            return notSerializable;
        }
    }

    @Override
    public Object fromBinary(final byte[] bytes, final String manifest) {
        // Match Artery's little-endian framing (ByteBuffer.wrap defaults to big-endian).
        return fromBinary(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN), manifest);
    }

    private EncodedFrame encode(final PreSerializedPublishSignal envelope) {
        final SignalBytesHolder.Serialized serialized = envelope.getHolder().getOrCompute(innerSerializer);
        final Map<String, Integer> groups = envelope.getGroups();
        final List<EncodedGroup> encodedGroups = new ArrayList<>(groups.size());
        for (final Map.Entry<String, Integer> entry : groups.entrySet()) {
            encodedGroups.add(new EncodedGroup(entry.getKey().getBytes(StandardCharsets.UTF_8), entry.getValue()));
        }
        return new EncodedFrame(serialized.manifest().getBytes(StandardCharsets.UTF_8),
                envelope.getGroupIndexKey().getBytes(StandardCharsets.UTF_8), encodedGroups, serialized.bytes());
    }

    private static void writeFrame(final ByteBuffer buf, final EncodedFrame frame) {
        putBytes(buf, frame.signalManifest());
        putBytes(buf, frame.groupIndexKey());
        buf.putInt(frame.groups().size());
        for (final EncodedGroup group : frame.groups()) {
            putBytes(buf, group.key());
            buf.putInt(group.size());
        }
        putBytes(buf, frame.signalBytes());
    }

    private static PreSerializedPublishSignal castToEnvelope(final Object o) {
        if (o instanceof final PreSerializedPublishSignal envelope) {
            return envelope;
        }
        throw new IllegalArgumentException("Cannot serialize object of type <" + o.getClass().getName() +
                "> with " + PreSerializedPublishSignalSerializer.class.getSimpleName());
    }

    private static void putBytes(final ByteBuffer buf, final byte[] bytes) {
        buf.putInt(bytes.length);
        buf.put(bytes);
    }

    private static String getString(final ByteBuffer buf) {
        final int length = buf.getInt();
        if (length < 0 || length > buf.remaining()) {
            throw new IllegalArgumentException("Invalid length prefix <" + length + "> (remaining: " +
                    buf.remaining() + ")");
        }
        final byte[] bytes = new byte[length];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * The wire frame with every variable-length field UTF-8-encoded exactly once, so sizing and writing share the
     * same byte arrays instead of re-encoding each string.
     *
     * @param signalManifest the inner serializer's string manifest, UTF-8 encoded.
     * @param groupIndexKey the group index key, UTF-8 encoded.
     * @param groups the per-destination groups, keys UTF-8 encoded.
     * @param signalBytes the pre-serialized signal payload (already bytes; shared across destinations).
     */
    private record EncodedFrame(byte[] signalManifest, byte[] groupIndexKey, List<EncodedGroup> groups,
                                byte[] signalBytes) {

        private int size() {
            int size = Integer.BYTES + signalManifest.length +
                    Integer.BYTES + groupIndexKey.length +
                    Integer.BYTES; // group count
            for (final EncodedGroup group : groups) {
                size += Integer.BYTES + group.key().length + Integer.BYTES; // key + size
            }
            return size + Integer.BYTES + signalBytes.length; // signal bytes length + bytes
        }
    }

    private record EncodedGroup(byte[] key, int size) {}
}
