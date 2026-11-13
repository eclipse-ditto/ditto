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
package org.eclipse.ditto.internal.utils.persistence.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Drift guard for the C-1 read-side binding in the SHIPPED Postgres persistence profile.
 *
 * <h2>The defect this guards against</h2>
 * Pekko's {@code WriteJournalBase.adaptFromJournal} resolves the read {@code EventAdapter} by
 * {@code repr.payload().getClass()} and {@code EventAdapters.get(Class)} matches {@code event-adapter-bindings} by
 * {@code Class.isAssignableFrom}, falling back to {@code IdentityEventAdapter} on no match. On Postgres replay,
 * {@code PostgresJournalOps.toRepr} builds the {@code PersistentRepr} with a {@code java.lang.String} payload
 * ({@code JournalRow.eventJson} is the JSONB column rendered as text). None of the domain event classes
 * ({@code ThingEvent}/{@code PolicyEvent}/{@code ConnectivityEvent}/{@code WotValidationConfigEvent}) is assignable
 * from {@code String}, so without an explicit {@code "java.lang.String"} binding {@code get(String.class)} returns the
 * identity adapter, the actor receives a bare String its {@code match(getEventClass())} never matches, and every event
 * is silently dropped on recovery. The Mongo profile avoids this with its {@code "org.bson.BsonValue" = mongodbobject}
 * binding (its journal replays a {@code BsonValue}); the Postgres profile needs the {@code String} peer on EVERY
 * journal block.
 *
 * <h2>What this test asserts</h2>
 * It parses the real shipped {@code ditto-postgres-persistence.conf} resource (the one the services include) and
 * asserts that EACH {@code ditto-postgres-*-journal} block maps the replayed payload type {@code "java.lang.String"} to
 * the same adapter alias its domain event type maps to. The behavioural proof that {@code String} routing actually
 * decodes a real event lives in the per-entity {@code *PostgresEventAdapterTest} (e.g.
 * {@code ThingPostgresEventAdapterTest.recoversARealThingEventThroughTheStringBindingSeam}); this test guards that the
 * shipped config keeps the binding present for every entity so those adapters are reachable in production.
 */
public final class PostgresJournalEventAdapterBindingTest {

    private static final String STRING_PAYLOAD = "java.lang.String";

    private final Config profile = ConfigFactory.parseResources("ditto-postgres-persistence.conf").resolve();

    /**
     * For a journal block, asserts the {@code "java.lang.String"} read binding exists and resolves to the SAME adapter
     * alias the domain {@code eventClass} resolves to (so the replayed String payload routes to the very adapter that
     * encoded the write). Reads the binding map raw so a missing key fails the test rather than silently returning a
     * default.
     */
    private void assertStringRoutesToSameAdapterAs(final String journalBlock, final String eventClass) {
        final Config bindings = profile.getConfig(journalBlock + ".event-adapter-bindings");

        assertThat(bindings.hasPath("\"" + STRING_PAYLOAD + "\""))
                .as("%s must bind the replayed payload type \"%s\" (peer of the Mongo \"org.bson.BsonValue\" binding); "
                        + "without it EventAdapters.get(String.class) falls back to IdentityEventAdapter and events are "
                        + "silently dropped on recovery", journalBlock, STRING_PAYLOAD)
                .isTrue();

        final String stringAlias = bindings.getString("\"" + STRING_PAYLOAD + "\"");
        final String eventAlias = bindings.getString("\"" + eventClass + "\"");
        assertThat(stringAlias)
                .as("%s: the replayed String payload must route to the SAME adapter that encodes the domain event "
                        + "<%s> so write and replay share one adapter/registry", journalBlock, eventClass)
                .isEqualTo(eventAlias);

        // The alias must resolve to a concrete adapter class declared in this block's event-adapters.
        final Config adapters = profile.getConfig(journalBlock + ".event-adapters");
        assertThat(adapters.hasPath(stringAlias))
                .as("%s: the String binding alias <%s> must be declared in event-adapters", journalBlock, stringAlias)
                .isTrue();
        assertThat(adapters.getString(stringAlias))
                .as("%s: the String binding must point at a Postgres JSONB event adapter class", journalBlock)
                .contains("Postgres");
    }

    @Test
    public void thingsJournalRoutesStringReplayPayloadToTheThingAdapter() {
        assertStringRoutesToSameAdapterAs("ditto-postgres-things-journal",
                "org.eclipse.ditto.things.model.signals.events.ThingEvent");
    }

    @Test
    public void policiesJournalRoutesStringReplayPayloadToThePolicyAdapter() {
        assertStringRoutesToSameAdapterAs("ditto-postgres-policies-journal",
                "org.eclipse.ditto.policies.model.signals.events.PolicyEvent");
    }

    @Test
    public void connectionsJournalRoutesStringReplayPayloadToTheConnectivityAdapter() {
        assertStringRoutesToSameAdapterAs("ditto-postgres-connections-journal",
                "org.eclipse.ditto.connectivity.model.signals.events.ConnectivityEvent");
    }

    @Test
    public void wotJournalRoutesStringReplayPayloadToTheWotAdapter() {
        assertStringRoutesToSameAdapterAs("ditto-postgres-wot-journal",
                "org.eclipse.ditto.things.model.devops.events.WotValidationConfigEvent");
    }

    /**
     * The things journal must route a String to exactly ONE adapter. A single block cannot disambiguate two adapters
     * by payload class, so WoT-validation-config events must NOT also bind here (they have their own
     * {@code ditto-postgres-wot-journal}); otherwise the String binding would be ambiguous and replay could route a
     * Thing event to the WoT registry (or vice versa).
     */
    @Test
    public void thingsJournalDoesNotAlsoRouteWotEventsThroughItsStringBinding() {
        final Config bindings = profile.getConfig("ditto-postgres-things-journal.event-adapter-bindings");
        assertThat(bindings.hasPath("\"org.eclipse.ditto.things.model.devops.events.WotValidationConfigEvent\""))
                .as("WoT events use their own ditto-postgres-wot-journal; binding them in the things journal would "
                        + "make the single \"java.lang.String\" replay binding ambiguous")
                .isFalse();
    }

}
