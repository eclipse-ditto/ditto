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
package org.eclipse.ditto.internal.utils.cluster;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import javax.annotation.Nullable;

import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.serialization.ByteBufferSerializer;
import akka.serialization.Serialization;
import akka.serialization.SerializationExtension;
import akka.serialization.SerializerWithStringManifest;
import akka.serialization.Serializers;

/**
 * Serializer of {@link ShardedBinarySerializer}.
 */
public final class ShardedBinarySerializer
        extends SerializerWithStringManifest implements ByteBufferSerializer {

    private static final int UNIQUE_IDENTIFIER = 1259836351;
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    private final ActorSystem actorSystem;
    @Nullable private Serialization serialization;

    /**
     * Constructs a new sharded binary serializer.
     *
     * @param actorSystem the ExtendedActorSystem to use for serialization of wrapped messages.
     */
    public ShardedBinarySerializer(final ExtendedActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    @Override
    public int identifier() {
        return UNIQUE_IDENTIFIER;
    }

    @Override
    public String manifest(final Object o) {
        final var envelope = (ShardedBinaryEnvelope) o;
        final var message = envelope.message();
        return Serializers.manifestFor(getSerialization().findSerializerFor(message), message);
    }

    @Override
    public void toBinary(final Object o, final ByteBuffer buf) {
        buf.put(toBinary(o));
    }

    @Override
    public byte[] toBinary(final Object o) {
        final var envelope = (ShardedBinaryEnvelope) o;
        final var message = envelope.message();
        final var serialization = getSerialization();
        final int serializerId = serialization.findSerializerFor(message).identifier();
        final byte[] messageBytes = serialization.serialize(message).get();
        final byte[] entityNameBytes = envelope.entityName().getBytes(CHARSET);
        final var buffer = ByteBuffer.allocate(4 + 4 + entityNameBytes.length + messageBytes.length);
        buffer.putInt(serializerId);
        buffer.putInt(entityNameBytes.length);
        buffer.put(entityNameBytes);
        buffer.put(messageBytes);
        return buffer.array();
    }

    @Override
    public Object fromBinary(final byte[] bytes, final String manifest) {
        return fromBinary(ByteBuffer.wrap(bytes), manifest);
    }

    @Override
    public Object fromBinary(final ByteBuffer buf, final String manifest) {
        final int serializerId = buf.getInt();
        final int entityNameLength = buf.getInt();
        final byte[] entityNameBytes = new byte[entityNameLength];
        buf.get(entityNameBytes);
        final byte[] messageBytes = new byte[buf.remaining()];
        buf.get(messageBytes);
        final var message = getSerialization().deserialize(messageBytes, serializerId, manifest).get();
        final var entityName = new String(entityNameBytes, CHARSET);
        return new ShardedBinaryEnvelope(message, entityName);
    }

    private Serialization getSerialization() {
        if (serialization == null) {
            serialization = SerializationExtension.get(actorSystem);
        }
        return serialization;
    }
}
