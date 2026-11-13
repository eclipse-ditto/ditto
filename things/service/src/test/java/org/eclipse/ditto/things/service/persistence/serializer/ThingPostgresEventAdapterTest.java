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
package org.eclipse.ditto.things.service.persistence.serializer;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingLifecycle;
import org.eclipse.ditto.things.model.signals.events.ThingCreated;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.persistence.journal.EventAdapter;
import org.apache.pekko.persistence.journal.EventAdapters;
import org.apache.pekko.persistence.journal.EventSeq;
import org.apache.pekko.persistence.journal.IdentityEventAdapter;
import org.apache.pekko.persistence.journal.Tagged;
import scala.jdk.javaapi.CollectionConverters;

/**
 * Drives a <em>real</em> {@link ThingEvent} through the journal {@code event-adapter-binding} configured for the
 * Postgres profile ({@link ThingPostgresEventAdapter}) and replays it the way {@code PostgresJournalOps} does, proving
 * the journal seam that {@code ditto-postgres-things-journal} now wires up:
 * <ul>
 *   <li>{@code toJournal} produces a JSONB <em>String</em> payload (not a raw domain event the journal cannot bind, and
 *       not a {@code BsonValue} only the Mongo path understands) wrapped in a {@link Tagged}, so the journal can persist
 *       JSONB <em>and</em> the journal tags;</li>
 *   <li>{@code fromJournal} decodes the stored JSONB text back to the identical {@link ThingEvent}, so the persistent
 *       actor's {@code match(getEventClass())} matches on recovery instead of silently dropping a bare String.</li>
 * </ul>
 * <p>
 * The {@code resolvesThe*BindingSeam}/{@code recoversAReal*ThroughThe*BindingSeam} tests are the load-bearing guard
 * for the C-1 read-side defect: they do NOT call {@code underTest.fromJournal(...)} directly. Instead they build a real
 * {@link EventAdapters} from the journal HOCON (the same {@code event-adapters} / {@code event-adapter-bindings} the
 * production {@code ditto-postgres-things-journal} ships) and exercise Pekko's own binding resolution
 * {@code EventAdapters.get(payload.getClass())} — exactly what {@code WriteJournalBase.adaptFromJournal} runs on replay,
 * resolving by {@code repr.payload().getClass()}. Because {@code PostgresJournalOps.toRepr} replays a
 * {@code java.lang.String} payload, only a {@code "java.lang.String"} binding routes it to this adapter; without it
 * {@code get(String.class)} falls back to Pekko's {@link IdentityEventAdapter} and every event is silently dropped on
 * recovery. The direct {@code toJournal}/{@code fromJournal} tests below can decode a String regardless of bindings, so
 * a green run there is not by itself proof the seam is wired — these binding-resolution tests are.
 */
public final class ThingPostgresEventAdapterTest {

    private static final ExtendedActorSystem SYSTEM =
            (ExtendedActorSystem) ActorSystem.create("test", ConfigFactory.load("test"));

    private static final Set<String> JOURNAL_TAGS = Set.of("always-alive", "priority");

    /**
     * The exact {@code event-adapters} / {@code event-adapter-bindings} the production
     * {@code ditto-postgres-things-journal} block ships (see
     * {@code internal/utils/persistence-r2dbc/src/main/resources/ditto-postgres-persistence.conf}). Inlined here
     * because the {@code things-service} module deliberately does not depend on the {@code persistence-r2dbc} module
     * (the Postgres profile resource is not on this classpath). The peer
     * {@code ditto-postgres-persistence.conf} content is independently guarded against drift by
     * {@code PostgresJournalEventAdapterBindingTest} in the {@code persistence-r2dbc} module, which parses the real
     * shipped resource and asserts every journal block carries the {@code "java.lang.String"} read binding.
     */
    private static final Config THINGS_JOURNAL_CONFIG = ConfigFactory.parseString(
            "event-adapters {\n"
                    + "  postgresjsonb = \"" + ThingPostgresEventAdapter.class.getName() + "\"\n"
                    + "}\n"
                    + "event-adapter-bindings {\n"
                    + "  \"org.eclipse.ditto.things.model.signals.events.ThingEvent\" = postgresjsonb\n"
                    + "  \"java.lang.String\" = postgresjsonb\n"
                    + "}\n");

    private final ThingPostgresEventAdapter underTest = new ThingPostgresEventAdapter(SYSTEM);

    private static Thing sampleThing() {
        return Thing.newBuilder()
                .setId(ThingId.of("pap.th.tMJyAjktUVP:YlmZXbTQ"))
                .setModified(Instant.parse("2021-02-24T14:17:37.581679843Z"))
                .setCreated(Instant.parse("2021-02-24T14:17:37.581679843Z"))
                .setLifecycle(ThingLifecycle.ACTIVE)
                .setRevision(1)
                .setPolicyId(PolicyId.of("pap.th.tMJyAjktUVP:YlmZXbTQ"))
                .setAttributes(Attributes.newBuilder().set("hello", "cloud").build())
                .build();
    }

    private static ThingCreated sampleEvent() {
        final DittoHeaders headers = DittoHeaders.newBuilder().journalTags(JOURNAL_TAGS).build();
        return ThingCreated.of(sampleThing(), 1L, Instant.parse("2021-02-24T14:17:37.581679843Z"), headers, null);
    }

    @Test
    public void toJournalProducesJsonbTextNotRawEventOrBson() {
        final Object journalEntry = underTest.toJournal(sampleEvent());

        assertThat(journalEntry).isInstanceOf(Tagged.class);
        final Tagged tagged = (Tagged) journalEntry;
        // PostgresJournalOps.toInsert -> toJsonText requires a JsonValue or its JSON text; a raw event throws.
        assertThat(tagged.payload())
                .as("Postgres journal payload must be JSONB text the journal can bind to a ::jsonb column")
                .isInstanceOf(String.class);
        // The text is parseable JSON carrying the event type, not a BSON value.
        assertThat((String) tagged.payload()).contains(sampleEvent().getType());
    }

    @Test
    public void toJournalCarriesJournalTagsSoThePingPathCanWakeAlwaysAliveEntities() {
        final Tagged tagged = (Tagged) underTest.toJournal(sampleEvent());

        assertThat(CollectionConverters.asJava(tagged.tags()))
                .as("journal tags must reach PostgresJournalOps so they are persisted (H-5)")
                .containsExactlyInAnyOrderElementsOf(JOURNAL_TAGS);
    }

    @Test
    public void fromJournalRecoversIdenticalEventFromTheJsonbTextString() {
        final ThingCreated original = sampleEvent();
        final Tagged tagged = (Tagged) underTest.toJournal(original);

        // PostgresJournalOps.toRepr replays the stored JSONB text (a String) with the manifest -> fromJournal.
        final EventSeq recoveredSeq = underTest.fromJournal(tagged.payload(), underTest.manifest(original));
        final List<Object> recovered = CollectionConverters.asJava(recoveredSeq.events());

        assertThat(recovered).as("the actor's match(getEventClass()) must see a real ThingEvent on recovery")
                .hasSize(1)
                .allMatch(ThingEvent.class::isInstance);
        // Recovery resets the revision to the default; comparing on the recovered shape proves a faithful round-trip.
        final ThingCreated recoveredEvent = (ThingCreated) recovered.get(0);
        assertThat(recoveredEvent.getThing()).isEqualTo(original.getThing());
        assertThat(recoveredEvent.getType()).isEqualTo(original.getType());
    }

    @Test
    public void fromJournalAlsoDecodesJsonbDeliveredAsUtf8Bytes() {
        final Tagged tagged = (Tagged) underTest.toJournal(sampleEvent());
        final byte[] jsonbBytes = ((String) tagged.payload()).getBytes(StandardCharsets.UTF_8);

        final EventSeq recoveredSeq = underTest.fromJournal(jsonbBytes, underTest.manifest(sampleEvent()));

        assertThat(CollectionConverters.asJava(recoveredSeq.events()))
                .hasSize(1)
                .allMatch(ThingEvent.class::isInstance);
    }

    /**
     * THE C-1 read-side guard. Builds Pekko's real {@link EventAdapters} from the journal HOCON and proves that the
     * type Postgres replays — {@code java.lang.String} (the JSONB column rendered to text by
     * {@code PostgresJournalOps.toRepr}) — resolves to the JSONB adapter, NOT to {@link IdentityEventAdapter}. Before
     * the {@code "java.lang.String"} binding was added this assertion failed: {@code get(String.class)} returned the
     * identity adapter, so on replay the persistent actor received a bare String its {@code match(getEventClass())}
     * never matched and every event was silently dropped.
     */
    @Test
    public void resolvesTheStringReplayPayloadToTheJsonbAdapterNotIdentity() {
        final EventAdapters eventAdapters = EventAdapters.apply(SYSTEM, THINGS_JOURNAL_CONFIG);

        final EventAdapter forStringPayload = eventAdapters.get(String.class);

        assertThat(forStringPayload)
                .as("Pekko resolves the read adapter by repr.payload().getClass(); PostgresJournalOps.toRepr replays "
                        + "a java.lang.String, so get(String.class) must be the JSONB adapter, not IdentityEventAdapter")
                .isNotInstanceOf(IdentityEventAdapter.class)
                .isInstanceOf(ThingPostgresEventAdapter.class);
    }

    /**
     * End-to-end replay through the binding seam: encodes a real {@link ThingEvent} to a JSONB String the way
     * {@code toJournal} does, then recovers it via {@code EventAdapters.get(payload.getClass()).fromJournal(...)} — the
     * exact call {@code WriteJournalBase.adaptFromJournal} makes on replay. A real {@link ThingEvent} (not a bare
     * String) must come back, proving recovery no longer silently drops events.
     */
    @Test
    public void recoversARealThingEventThroughTheStringBindingSeam() {
        final ThingCreated original = sampleEvent();
        final Tagged tagged = (Tagged) underTest.toJournal(original);
        final Object replayedPayload = tagged.payload(); // PostgresJournalOps.toRepr replays this as a String.
        assertThat(replayedPayload).isInstanceOf(String.class);

        final EventAdapters eventAdapters = EventAdapters.apply(SYSTEM, THINGS_JOURNAL_CONFIG);
        // Mirror WriteJournalBase.adaptFromJournal: resolve by payload class, then fromJournal(payload, manifest).
        final EventAdapter resolved = eventAdapters.get(replayedPayload.getClass());
        final EventSeq recoveredSeq = resolved.fromJournal(replayedPayload, underTest.manifest(original));
        final List<Object> recovered = CollectionConverters.asJava(recoveredSeq.events());

        assertThat(recovered)
                .as("replay through the real binding seam must yield a ThingEvent, not a dropped/bare-String event")
                .hasSize(1)
                .allMatch(ThingEvent.class::isInstance);
        final ThingCreated recoveredEvent = (ThingCreated) recovered.get(0);
        assertThat(recoveredEvent.getThing()).isEqualTo(original.getThing());
        assertThat(recoveredEvent.getType()).isEqualTo(original.getType());
    }

}
