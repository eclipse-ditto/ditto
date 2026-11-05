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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.cluster.sharding.internal.EventSourcedRememberEntitiesCoordinatorStore;
import org.apache.pekko.cluster.sharding.internal.EventSourcedRememberEntitiesShardStore;
import org.apache.pekko.serialization.Serialization;
import org.apache.pekko.serialization.SerializationExtension;
import org.apache.pekko.testkit.javadsl.TestKit;
import scala.jdk.javaapi.CollectionConverters;

/**
 * Unit tests for the two {@link PostgresPayloadCodec}s and their config selection. The Pekko-serialization codec is
 * exercised with the real payloads of Pekko's event-sourced remember-entities stores — the one producer of
 * Pekko-serialized bytes the PostgreSQL backend persists — so the envelope is proven against what connectivity's shard
 * region actually writes.
 */
public final class PostgresPayloadCodecTest {

    private static ActorSystem system;
    private static Serialization serialization;

    @BeforeClass
    public static void startActorSystem() {
        system = ActorSystem.create("PostgresPayloadCodecTest", ConfigFactory.load());
        serialization = SerializationExtension.get(system);
    }

    @AfterClass
    public static void stopActorSystem() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
            system = null;
        }
    }

    private static scala.collection.immutable.Set<String> scalaSet(final String... elements) {
        return CollectionConverters.asScala(Set.of(elements)).toSet();
    }

    // ── JSON text codec: today's behaviour, unchanged ──────────────────────────────────────────────────────────────

    @Test
    public void jsonTextCodecRendersJsonValuesTextAndUtf8BytesAsTheStoredText() {
        final PostgresPayloadCodec codec = JsonTextPayloadCodec.INSTANCE;

        assertThat(codec.encode(JsonFactory.newObject("{\"x\":1}"))).isEqualTo("{\"x\":1}");
        assertThat(codec.encode("{\"x\":2}")).isEqualTo("{\"x\":2}");
        assertThat(codec.encode(new StringBuilder("{\"x\":3}"))).isEqualTo("{\"x\":3}");
        assertThat(codec.encode("{\"x\":4}".getBytes(StandardCharsets.UTF_8))).isEqualTo("{\"x\":4}");
    }

    @Test
    public void jsonTextCodecRejectsAnyOtherPayloadType() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> JsonTextPayloadCodec.INSTANCE.encode(42))
                .withMessageContaining("java.lang.Integer");
    }

    @Test
    public void jsonTextCodecRejectsARawNulCharacter() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> JsonTextPayloadCodec.INSTANCE.encode("{\"x\":\"a\u0000b\"}"))
                .withMessageContaining("U+0000");
    }

    @Test
    public void jsonTextCodecDecodesToTheStoredTextItself() {
        // The per-entity event adapter / snapshot codec parses it afterwards; the journal hands the text through.
        assertThat(JsonTextPayloadCodec.INSTANCE.decode("{\"x\":1}")).isEqualTo("{\"x\":1}");
    }

    // ── Pekko-serialization codec: the envelope ────────────────────────────────────────────────────────────────────

    @Test
    public void pekkoCodecWrapsTheSerializedBytesInTheThreeFieldEnvelope() {
        final PostgresPayloadCodec codec = PekkoSerializationPayloadCodec.of(serialization);
        final Object event = new EventSourcedRememberEntitiesShardStore.EntitiesStarted(scalaSet("conn-a", "conn-b"));

        final String stored = codec.encode(event);

        final JsonObject envelope = JsonFactory.newObject(stored);
        assertThat(envelope.getKeys().stream().map(CharSequence::toString))
                .containsExactlyInAnyOrder("serializerId", "manifest", "bytes");
        assertThat(envelope.getValue("serializerId").map(JsonValue::asInt))
                .contains(serialization.findSerializerFor(event).identifier());
        // Pekko's sharding serializer is a string-manifest serializer, so the manifest names the message type.
        assertThat(envelope.getValue("manifest").map(JsonValue::asString)).isPresent().get().asString().isNotEmpty();
        final byte[] bytes =
                Base64.getDecoder().decode(envelope.getValue("bytes").map(JsonValue::asString).orElseThrow());
        assertThat(bytes).isEqualTo(serialization.findSerializerFor(event).toBinary(event));
    }

    @Test
    public void pekkoCodecRoundTripsTheShardStoreEventsAndState() {
        final PostgresPayloadCodec codec = PekkoSerializationPayloadCodec.of(serialization);
        final List<Object> payloads = List.of(
                new EventSourcedRememberEntitiesShardStore.EntitiesStarted(scalaSet("conn-a", "conn-b")),
                new EventSourcedRememberEntitiesShardStore.EntitiesStopped(scalaSet("conn-a")),
                new EventSourcedRememberEntitiesShardStore.State(scalaSet("conn-b", "conn-c")),
                new EventSourcedRememberEntitiesShardStore.State(scalaSet()));

        for (final Object payload : payloads) {
            assertThat(codec.decode(codec.encode(payload))).as(payload.toString()).isEqualTo(payload);
        }
    }

    @Test
    public void pekkoCodecRoundTripsTheCoordinatorStorePayloadsIncludingPlainStringsWithoutManifest() {
        // The coordinator store persists shard ids as plain Strings (Pekko's primitive-string serializer, no manifest),
        // its migration marker (a case object) and its State snapshot.
        final PostgresPayloadCodec codec = PekkoSerializationPayloadCodec.of(serialization);
        final List<Object> payloads = List.of(
                "17",
                EventSourcedRememberEntitiesCoordinatorStore.MigrationMarker$.MODULE$,
                new EventSourcedRememberEntitiesCoordinatorStore.State(scalaSet("3", "17"), true));

        for (final Object payload : payloads) {
            assertThat(codec.decode(codec.encode(payload))).as(payload.toString()).isEqualTo(payload);
        }
        assertThat(JsonFactory.newObject(codec.encode("17")).getValue("manifest").map(JsonValue::asString))
                .contains("");
    }

    @Test
    public void pekkoCodecRejectsStoredJsonThatIsNoEnvelope() {
        final PostgresPayloadCodec codec = PekkoSerializationPayloadCodec.of(serialization);

        for (final String notAnEnvelope : List.of("{\"foo\":1}", "[1,2]", "{\"serializerId\":\"x\",\"manifest\":\"\","
                + "\"bytes\":\"\"}", "{\"serializerId\":1,\"manifest\":\"\",\"bytes\":\"%%%\"}", "not json at all",
                "")) {
            assertThatIllegalArgumentException().as(notAnEnvelope)
                    .isThrownBy(() -> codec.decode(notAnEnvelope))
                    .withMessageContaining("envelope");
        }
    }

    @Test
    public void pekkoCodecRejectsAnEnvelopeOfAnUnknownSerializer() {
        final PostgresPayloadCodec codec = PekkoSerializationPayloadCodec.of(serialization);
        final String envelope = "{\"serializerId\":987654,\"manifest\":\"\",\"bytes\":\"AAEC\"}";

        assertThatIllegalArgumentException()
                .isThrownBy(() -> codec.decode(envelope))
                .withMessageContaining("987654");
    }

    @Test
    public void pekkoCodecRejectsAPayloadPekkoCannotSerializeAsIllegalArgument() {
        // java serialization is off and a plain Object has no binding: the write must become a rejection, which
        // PostgresJournalOps derives from IllegalArgumentException (anything else would stop the persistent actor)
        final PostgresPayloadCodec codec = PekkoSerializationPayloadCodec.of(serialization);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> codec.encode(new Object()))
                .withMessageContaining("Unable to Pekko-serialize")
                .withMessageContaining("java.lang.Object");
    }

    @Test
    public void bothCodecsRejectNullAsIllegalArgument() {
        final PostgresPayloadCodec pekko = PekkoSerializationPayloadCodec.of(serialization);

        assertThatIllegalArgumentException().isThrownBy(() -> JsonTextPayloadCodec.INSTANCE.encode(null));
        assertThatIllegalArgumentException().isThrownBy(() -> pekko.encode(null));
        assertThatIllegalArgumentException().isThrownBy(() -> pekko.decode(null)).withMessageContaining("envelope");
    }

    // ── config selection ───────────────────────────────────────────────────────────────────────────────────────────

    @Test
    public void fromConfigDefaultsToTheJsonTextCodecWithoutResolvingTheSerialization() {
        // the JSON-text blocks must not pull Pekko serialization: the supplier stays unused
        final java.util.function.Supplier<Serialization> neverResolved = () -> {
            throw new AssertionError("the serialization must not be resolved for the JSON text codec");
        };

        assertThat(PostgresPayloadCodec.fromConfig(ConfigFactory.parseString("entity = things"), neverResolved))
                .isSameAs(JsonTextPayloadCodec.INSTANCE);
        assertThat(PostgresPayloadCodec.fromConfig(ConfigFactory.parseString("payload-codec = json-text"),
                neverResolved)).isSameAs(JsonTextPayloadCodec.INSTANCE);
    }

    @Test
    public void fromConfigSelectsThePekkoSerializationCodec() {
        assertThat(PostgresPayloadCodec.fromConfig(ConfigFactory.parseString("payload-codec = pekko-serialization"),
                () -> serialization)).isInstanceOf(PekkoSerializationPayloadCodec.class);
    }

    @Test
    public void fromConfigRejectsAnUnknownCodecName() {
        assertThatThrownBy(() -> PostgresPayloadCodec.fromConfig(ConfigFactory.parseString("payload-codec = bson"),
                () -> serialization))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining("payload-codec")
                .hasMessageContaining("bson")
                .hasMessageContaining("json-text")
                .hasMessageContaining("pekko-serialization");
    }

}
