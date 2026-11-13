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
package org.eclipse.ditto.connectivity.service.messaging.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.persistence.journal.Tagged;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.persistentactors.EmptyEvent;
import org.junit.Test;
import scala.jdk.javaapi.CollectionConverters;

import com.typesafe.config.ConfigFactory;

/**
 * Proves that {@link ConnectivityPostgresEventAdapter#toJournal(Object)} accepts an {@link EmptyEvent} — the
 * "internal" event {@code ConnectionPersistenceActor} persists for the always-alive / priority-update journal-tag
 * path (see {@code ConnectionPersistenceActor} lines 533-535 and 848-850) — and not only concrete
 * {@code ConnectivityEvent}s.
 * <p>
 * {@link EmptyEvent} implements only the base {@code org.eclipse.ditto.base.model.signals.events.Event} interface,
 * not {@code ConnectivityEvent}. Without a base-interface {@code event-adapter-bindings} entry in the Postgres
 * connections journal config, Pekko routes an {@link EmptyEvent} to the identity adapter instead of this one, and the
 * raw domain object reaches the JSONB write path, which rejects it (crashing/rejecting the write and dropping the
 * event's journal tags). This test exercises the adapter directly (not the HOCON binding — that is covered by
 * {@code PostgresProfileHoconLintTest#everyPostgresJournalBindsTheBaseEventInterface} in the
 * {@code persistence-r2dbc} module) to prove the adapter itself tolerates a non-{@code ConnectivityEvent} payload and
 * still carries the journal tags through the {@link Tagged} envelope.
 */
public final class ConnectivityPostgresEventAdapterEmptyEventTest {

    @Test
    public void emptyEventSerializesToTaggedJsonb() {
        // ConnectivityPostgresEventAdapter's constructor builds a DefaultConnectionConfig (needs
        // ditto.connectivity.connection.supervisor etc.) from the system config; the connectivity-service test.conf
        // (the same config every other persistence test in this module loads) supplies it. A bare default ActorSystem
        // config lacks this and fails the adapter constructor with a DittoConfigError, not a test failure of the
        // behaviour under test.
        final ActorSystem system = ActorSystem.create("EmptyEventAdapterTest", ConfigFactory.load("test"));
        try {
            final ConnectivityPostgresEventAdapter adapter =
                    new ConnectivityPostgresEventAdapter(ExtendedActorSystem.class.cast(system));
            final DittoHeaders headers = DittoHeaders.newBuilder()
                    .journalTags(Set.of("always-alive", "priority-3")).build();
            final EmptyEvent emptyEvent = new EmptyEvent(EmptyEvent.EFFECT_ALWAYS_ALIVE, 5L, headers);

            final Object journalEntry = adapter.toJournal(emptyEvent);

            assertThat(journalEntry).isInstanceOf(Tagged.class);
            final Tagged tagged = (Tagged) journalEntry;
            // tagged.tags() is a scala.collection.immutable.Set<String>, not a java.lang.Iterable; convert before
            // handing it to AssertJ (mirrors ThingPostgresEventAdapterTest's pattern for the same Tagged.tags()).
            assertThat(CollectionConverters.asJava(tagged.tags())).contains("always-alive", "priority-3");
            assertThat(tagged.payload()).isInstanceOf(String.class); // JSONB text
        } finally {
            TestKit.shutdownActorSystem(system);
        }
    }

}
