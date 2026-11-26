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
package org.eclipse.ditto.internal.utils.persistence.postgres.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.persistence.Persistence;
import org.apache.pekko.persistence.journal.EventAdapter;
import org.apache.pekko.persistence.journal.EventSeq;
import org.apache.pekko.persistence.journal.IdentityEventAdapter;
import org.apache.pekko.persistence.journal.Tagged;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.AfterClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import scala.jdk.javaapi.CollectionConverters;

/**
 * Tests {@link DelegatingPostgresEventAdapter}: the JSONB text envelope around a delegate {@code EventSerializer}, the
 * accepted JSONB representations, the refusals, construction by class name, the decode of the golden journal rows as
 * text and as bytes, and the Pekko {@code java.lang.String} binding seam the PostgreSQL journal replays through.
 */
public final class DelegatingPostgresEventAdapterTest {

    private static final String JOURNAL_PLUGIN_ID = "pekko.persistence.journal.inmem";
    private static final String SEAM_MANIFEST = "seam.events:replayed";

    /**
     * The journal binding shape the shipped {@code ditto-postgres-persistence.conf} uses: the replayed payload type
     * {@code java.lang.String} and the written base {@code Event} interface both name the JSONB adapter.
     */
    private static final ExtendedActorSystem SYSTEM = (ExtendedActorSystem) ActorSystem.create(
            "DelegatingPostgresEventAdapterTest", ConfigFactory.parseString(
                    JOURNAL_PLUGIN_ID + " {\n"
                            + "  event-adapters {\n"
                            + "    postgresjsonb = \"" + TestDelegatingPostgresEventAdapter.class.getName() + "\"\n"
                            + "  }\n"
                            + "  event-adapter-bindings {\n"
                            + "    \"java.lang.String\" = postgresjsonb\n"
                            + "    \"org.eclipse.ditto.base.model.signals.events.Event\" = postgresjsonb\n"
                            + "  }\n"
                            + "}\n"));

    /** Golden journal rows as the PostgreSQL journal stores them (one JSONB payload each), and their manifests. */
    private static final Map<String, String> GOLDEN_FIXTURES = Map.of(
            "thing-postgres.json", "things.events:thingCreated",
            "policy-postgres.json", "policies.events:policyCreated",
            "wot-validation-config-postgres.json", "wot.validation.config.events:wotValidationConfigCreated");

    private final RecordingEventSerializer serializer = new RecordingEventSerializer();
    private final DelegatingPostgresEventAdapter underTest = new DelegatingPostgresEventAdapter(serializer) {};
    private final Event<?> event = mock(Event.class);

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(SYSTEM);
    }

    @Test
    public void toJournalRendersTheSerializerJsonAsTextWithTheSerializerTags() {
        final Object journal = underTest.toJournal(event);

        assertThat(journal).isInstanceOf(Tagged.class);
        final Tagged tagged = (Tagged) journal;
        assertThat(tagged.payload()).isEqualTo(RecordingEventSerializer.CANNED_JSON.toString());
        assertThat(CollectionConverters.asJava(tagged.tags()))
                .containsExactlyInAnyOrderElementsOf(RecordingEventSerializer.TAGS);
    }

    @Test
    public void toJournalRefusesANonEvent() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> underTest.toJournal("x"))
                .withMessage("Unable to toJournal a non-'Event' object! Was: " + String.class);
        assertThat(serializer.calls).isEmpty();
    }

    @Test
    public void fromJournalAcceptsAJsonObject() {
        assertDecodes(RecordingEventSerializer.CANNED_JSON);
    }

    @Test
    public void fromJournalAcceptsAString() {
        assertDecodes(RecordingEventSerializer.CANNED_JSON.toString());
    }

    @Test
    public void fromJournalAcceptsAnyCharSequence() {
        assertDecodes(new StringBuilder(RecordingEventSerializer.CANNED_JSON.toString()));
    }

    @Test
    public void fromJournalAcceptsUtf8Bytes() {
        assertDecodes(RecordingEventSerializer.CANNED_JSON.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void fromJournalRefusesAnUndecodableType() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> underTest.fromJournal(42, "m"))
                .withMessage("Unable to fromJournal a non JSONB-decodable object (expected JsonObject, String or "
                        + "byte[])! Was: " + Integer.class);
        assertThat(serializer.calls).isEmpty();
    }

    @Test
    public void fromJournalRefusesNull() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> underTest.fromJournal(null, "m"))
                .withMessage("Unable to fromJournal a non JSONB-decodable object (expected JsonObject, String or "
                        + "byte[])! Was: null");
        assertThat(serializer.calls).isEmpty();
    }

    @Test
    public void manifestIsTheSerializerManifest() {
        assertThat(underTest.manifest(event)).isEqualTo(RecordingEventSerializer.MANIFEST);
    }

    @Test
    public void constructionByClassNameInstantiatesTheSerializer() {
        final DelegatingPostgresEventAdapter byName =
                new DelegatingPostgresEventAdapter(SYSTEM, RecordingEventSerializer.class.getName()) {};

        assertThat(byName.manifest(event)).isEqualTo(RecordingEventSerializer.MANIFEST);
        assertThat(byName.fromJournal(RecordingEventSerializer.CANNED_JSON.toString(), "m"))
                .isSameAs(RecordingEventSerializer.RESULT);
    }

    @Test
    public void constructionByAnUnknownClassNameFailsWithTheName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DelegatingPostgresEventAdapter(SYSTEM, "no.such.Serializer") {})
                .withMessageContaining("<no.such.Serializer>")
                .withMessageStartingWith("Could not instantiate EventSerializer")
                .withCauseInstanceOf(ClassNotFoundException.class);
    }

    @Test
    public void constructionByTheNameOfANonSerializerClassFails() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DelegatingPostgresEventAdapter(SYSTEM, String.class.getName()) {})
                .withMessageContaining("<java.lang.String>")
                .havingCause();
    }

    @Test
    public void goldenRowsDecodeAsTextAndAsBytes() throws IOException {
        for (final Map.Entry<String, String> fixture : GOLDEN_FIXTURES.entrySet()) {
            final String text = readFixture(fixture.getKey());
            final String manifest = fixture.getValue();
            final JsonObject expected = JsonFactory.newObject(text);
            assertThat(expected.getValue("type")).as(fixture.getKey()).contains(JsonValue.of(manifest));

            final RecordingEventSerializer textSerializer = new RecordingEventSerializer();
            new DelegatingPostgresEventAdapter(textSerializer) {}.fromJournal(text, manifest);
            final RecordingEventSerializer bytesSerializer = new RecordingEventSerializer();
            new DelegatingPostgresEventAdapter(bytesSerializer) {}
                    .fromJournal(text.getBytes(StandardCharsets.UTF_8), manifest);

            assertThat(textSerializer.receivedJson).as(fixture.getKey()).containsExactly(expected);
            assertThat(textSerializer.receivedManifests).as(fixture.getKey()).containsExactly(manifest);
            assertThat(bytesSerializer.receivedJson).as(fixture.getKey()).containsExactly(expected);
            assertThat(bytesSerializer.receivedManifests).as(fixture.getKey()).containsExactly(manifest);
        }
    }

    /**
     * The PostgreSQL journal replays a {@code java.lang.String} payload, and Pekko resolves the read adapter by the
     * payload class. Without the {@code java.lang.String} binding this resolves to {@link IdentityEventAdapter} and
     * recovery silently drops every event.
     */
    @Test
    public void theJournalResolvesTheStringReplayPayloadToTheJsonbAdapter() {
        final EventAdapter forString = Persistence.get(SYSTEM).adaptersFor(JOURNAL_PLUGIN_ID).get(String.class);

        assertThat(forString)
                .isNotInstanceOf(IdentityEventAdapter.class)
                .isInstanceOf(TestDelegatingPostgresEventAdapter.class);
    }

    @Test
    public void encodeThenDecodeThroughTheResolvedAdaptersHandsTheSerializerTheOriginalJson() {
        final EventAdapter forEvent = Persistence.get(SYSTEM).adaptersFor(JOURNAL_PLUGIN_ID).get(event.getClass());
        final Object payload = ((Tagged) forEvent.toJournal(event)).payload();
        assertThat(payload).isInstanceOf(String.class);

        final EventAdapter forString = Persistence.get(SYSTEM).adaptersFor(JOURNAL_PLUGIN_ID).get(payload.getClass());
        final EventSeq recovered = forString.fromJournal(payload, SEAM_MANIFEST);

        assertThat(recovered).isSameAs(RecordingEventSerializer.RESULT);
        final List<RecordingEventSerializer> seamSerializers = RecordingEventSerializer.CREATED_BY_NAME.stream()
                .filter(recording -> recording.receivedManifests.contains(SEAM_MANIFEST))
                .toList();
        assertThat(seamSerializers).hasSize(1);
        assertThat(seamSerializers.get(0).receivedJson).containsExactly(RecordingEventSerializer.CANNED_JSON);
    }

    private void assertDecodes(final Object stored) {
        final EventSeq result = underTest.fromJournal(stored, "m");

        assertThat(result).isSameAs(RecordingEventSerializer.RESULT);
        assertThat(serializer.receivedJson).containsExactly(RecordingEventSerializer.CANNED_JSON);
        assertThat(serializer.receivedManifests).containsExactly("m");
    }

    private static String readFixture(final String name) throws IOException {
        try (final InputStream in = Objects.requireNonNull(
                DelegatingPostgresEventAdapterTest.class.getResourceAsStream("/golden/journal/" + name), name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

}
