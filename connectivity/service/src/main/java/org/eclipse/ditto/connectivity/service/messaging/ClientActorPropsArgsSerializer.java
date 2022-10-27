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
package org.eclipse.ditto.connectivity.service.messaging;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.json.JsonObject;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.serialization.ByteBufferSerializer;
import akka.serialization.Serialization;
import akka.serialization.SerializationExtension;
import akka.serialization.SerializerWithStringManifest;
import akka.serialization.Serializers;

/**
 * Serializer of {@link org.eclipse.ditto.connectivity.service.messaging.ClientActorPropsArgs}.
 */
public final class ClientActorPropsArgsSerializer
        extends SerializerWithStringManifest implements ByteBufferSerializer {

    private static final int UNIQUE_IDENTIFIER = 597861065;
    private static final Charset CHARSET = StandardCharsets.UTF_8;
    private static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;

    private final ActorSystem actorSystem;
    @Nullable private Serialization serialization;

    /**
     * Constructs a new sharded binary serializer.
     *
     * @param actorSystem the ExtendedActorSystem to use for serialization of wrapped messages.
     */
    public ClientActorPropsArgsSerializer(final ExtendedActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    @Override
    public int identifier() {
        return UNIQUE_IDENTIFIER;
    }

    @Override
    public String manifest(final Object o) {
        return ClientActorPropsArgs.class.getSimpleName();
    }

    @Override
    public void toBinary(final Object o, final ByteBuffer buf) {
        buf.put(toBinary(o));
    }

    @Override
    public byte[] toBinary(final Object o) {
        final var args = (ClientActorPropsArgs) o;
        final var connection = new Field(args.connection().toJsonString());
        final var commandForwarder =
                toFieldWithManifest(args.commandForwarderActor());
        final var connectionActor = toField(args.connectionActor());
        final var dittoHeaders = new Field(args.dittoHeaders().toJsonString());
        final var connectivityConfigOverwrites =
                toFieldWithManifest(args.connectivityConfigOverwrites());

        final var buffer = ByteBuffer.allocate(
                connection.length() +
                        commandForwarder.length() +
                        connectionActor.length() +
                        dittoHeaders.length() +
                        connectivityConfigOverwrites.length()
        );
        buffer.order(BYTE_ORDER);

        connection.write(buffer);
        commandForwarder.write(buffer);
        connectionActor.write(buffer);
        dittoHeaders.write(buffer);
        connectivityConfigOverwrites.write(buffer);

        return buffer.array();
    }

    @Override
    public Object fromBinary(final byte[] bytes, final String manifest) {
        return fromBinary(ByteBuffer.wrap(bytes), manifest);
    }

    @Override
    public Object fromBinary(final ByteBuffer buf, final String manifest) {
        final var originalByteOrder = buf.order();
        buf.order(BYTE_ORDER);
        final var connection = Field.read(buf);
        final var commandForwarder = FieldWithManifest.read(buf);
        final var connectionActor = Field.read(buf);
        final var dittoHeaders = Field.read(buf);
        final var connectivityConfigOverwrites = FieldWithManifest.read(buf);
        buf.order(originalByteOrder);

        return new ClientActorPropsArgs(
                ConnectivityModelFactory.connectionFromJson(JsonObject.of(connection.asString())),
                toActorRef(commandForwarder, commandForwarder.value()),
                toActorRef(commandForwarder, connectionActor),
                DittoHeaders.newBuilder(JsonObject.of(dittoHeaders.asString())).build(),
                toConfig(connectivityConfigOverwrites)
        );
    }

    private Serialization getSerialization() {
        if (serialization == null) {
            serialization = SerializationExtension.get(actorSystem);
        }

        return serialization;
    }

    private ActorRef toActorRef(final FieldWithManifest meta, final Field value) {
        return (ActorRef) getSerialization().deserialize(value.bytes(), meta.id(), meta.manifest().asString()).get();
    }

    private Config toConfig(final FieldWithManifest field) {
        return (Config) getSerialization()
                .deserialize(field.value().bytes(), field.id(), field.manifest().asString()).get();
    }

    private Field toField(final Object value) {
        return new Field(getSerialization().serialize(value).get());
    }

    private FieldWithManifest toFieldWithManifest(final Object value) {
        final var serialization = getSerialization();
        final var serializer = serialization.findSerializerFor(value);
        final int id = serializer.identifier();
        final var manifest = new Field(Serializers.manifestFor(serializer, value));
        final var valueField = new Field(serialization.serialize(value).get());

        return new FieldWithManifest(id, manifest, valueField);
    }

    private record Field(byte[] bytes) {

        private Field(final String string) {
            this(string.getBytes(CHARSET));
        }

        private void write(final ByteBuffer buffer) {
            buffer.putInt(bytes.length);
            buffer.put(bytes);
        }

        private String asString() {
            return new String(bytes, CHARSET);
        }

        private int length() {
            return 4 + bytes.length;
        }

        private static Field read(final ByteBuffer buffer) {
            final var bytes = new byte[buffer.getInt()];
            buffer.get(bytes);

            return new Field(bytes);
        }
    }

    private record FieldWithManifest(int id, Field manifest, Field value) {

        private void write(final ByteBuffer buffer) {
            buffer.putInt(id);
            manifest.write(buffer);
            value.write(buffer);
        }

        private static FieldWithManifest read(final ByteBuffer buffer) {
            final var id = buffer.getInt();
            final var manifest = Field.read(buffer);
            final var value = Field.read(buffer);

            return new FieldWithManifest(id, manifest, value);
        }

        private int length() {
            return 4 + manifest.length() + value.length();
        }
    }

}
