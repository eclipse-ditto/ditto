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
package org.eclipse.ditto.internal.utils.persistence.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.ditto.internal.utils.persistence.mongo.adapters.ConnectivityMongoEventAdapter;
import org.eclipse.ditto.internal.utils.persistence.mongo.adapters.DefaultPolicyMongoEventAdapter;
import org.eclipse.ditto.internal.utils.persistence.mongo.adapters.DelegatingMongoEventAdapter;
import org.eclipse.ditto.internal.utils.persistence.mongo.adapters.ThingMongoEventAdapter;
import org.eclipse.ditto.internal.utils.persistence.mongo.adapters.WotValidationConfigMongoEventAdapter;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigResolveOptions;
import com.typesafe.config.ConfigValueType;

/**
 * Pins the event adapter wiring of the shipped MongoDB persistence profile {@code ditto-mongo-persistence.conf}.
 * <p>
 * The profile holds the adapter classes as plain strings, so a misspelled or stale name would only fail when the Pekko
 * MongoDB journal plugin starts. For every journal block that declares {@code event-adapters} this test asserts that
 * each alias names a class that loads and is a {@link DelegatingMongoEventAdapter}, that every entry of
 * {@code event-adapter-bindings} names a declared alias, and which adapter each bound type reaches.
 * <p>
 * The binding of the stored payload type {@code org.bson.BsonValue} is the read side: Pekko resolves the read adapter
 * by the class of the replayed payload and falls back to its identity adapter when nothing is bound, in which case the
 * persistence actor would receive a raw BSON document instead of an event.
 */
public final class MongoJournalEventAdapterBindingTest {

    private static final String STORED_PAYLOAD = "org.bson.BsonValue";
    private static final String BASE_EVENT = "org.eclipse.ditto.base.model.signals.events.Event";
    private static final String THING_EVENT = "org.eclipse.ditto.things.model.signals.events.ThingEvent";
    private static final String WOT_VALIDATION_CONFIG_EVENT =
            "org.eclipse.ditto.things.model.devops.events.WotValidationConfigEvent";

    private static final String THINGS_JOURNAL = "pekko-contrib-mongodb-persistence-things-journal";
    private static final String WOT_VALIDATION_CONFIG_JOURNAL =
            "pekko-contrib-mongodb-persistence-wot-validation-config-journal";
    private static final String POLICIES_JOURNAL = "pekko-contrib-mongodb-persistence-policies-journal";
    private static final String CONNECTION_JOURNAL = "pekko-contrib-mongodb-persistence-connection-journal";

    private static final String EVENT_ADAPTERS = "event-adapters";
    private static final String EVENT_ADAPTER_BINDINGS = "event-adapter-bindings";

    private final Config profile = ConfigFactory.parseResources("ditto-mongo-persistence.conf")
            .resolve(ConfigResolveOptions.defaults().setUseSystemEnvironment(false).setAllowUnresolved(true));

    /**
     * A journal block that declares event adapters without being listed here would ship without any of the checks of
     * this class.
     */
    @Test
    public void theJournalBlocksDeclaringEventAdaptersAreTheOnesThisTestPins() {
        assertThat(profile.isEmpty()).as("ditto-mongo-persistence.conf must be on the classpath").isFalse();

        assertThat(blocksDeclaringEventAdapters()).containsExactlyInAnyOrder(THINGS_JOURNAL,
                WOT_VALIDATION_CONFIG_JOURNAL, POLICIES_JOURNAL, CONNECTION_JOURNAL);
    }

    @Test
    public void everyEventAdapterAliasNamesALoadableDelegatingMongoEventAdapter() throws ClassNotFoundException {
        final Set<String> journalBlocks = blocksDeclaringEventAdapters();
        assertThat(journalBlocks).isNotEmpty();

        for (final String journalBlock : journalBlocks) {
            final Config adapters = profile.getConfig(journalBlock + "." + EVENT_ADAPTERS);
            assertThat(adapters.root().keySet()).as("%s: declared adapter aliases", journalBlock).isNotEmpty();
            for (final String alias : adapters.root().keySet()) {
                assertThat(DelegatingMongoEventAdapter.class)
                        .as("%s: event-adapters alias <%s> must name a loadable MongoDB event adapter class",
                                journalBlock, alias)
                        .isAssignableFrom(adapterClass(journalBlock, alias));
            }
        }
    }

    @Test
    public void everyEventAdapterBindingNamesADeclaredAlias() {
        for (final String journalBlock : blocksDeclaringEventAdapters()) {
            final Config adapters = profile.getConfig(journalBlock + "." + EVENT_ADAPTERS);
            final Config bindings = profile.getConfig(journalBlock + "." + EVENT_ADAPTER_BINDINGS);

            for (final String boundType : bindings.root().keySet()) {
                assertThat(adapters.root().keySet())
                        .as("%s: the alias bound to <%s> must be declared in event-adapters", journalBlock, boundType)
                        .contains(bindings.getString(quoted(boundType)));
            }
        }
    }

    @Test
    public void thingsJournalRoutesStoredBsonAndThingEventsToTheThingAdapter() throws ClassNotFoundException {
        assertBindings(THINGS_JOURNAL, Map.of(
                STORED_PAYLOAD, ThingMongoEventAdapter.class,
                THING_EVENT, ThingMongoEventAdapter.class,
                WOT_VALIDATION_CONFIG_EVENT, WotValidationConfigMongoEventAdapter.class));
    }

    /**
     * The WoT validation config journal writes its events through the WoT adapter and replays stored BSON through the
     * thing adapter, exactly like the things journal.
     */
    @Test
    public void wotValidationConfigJournalRoutesItsEventsToTheWotAdapterAndStoredBsonToTheThingAdapter()
            throws ClassNotFoundException {

        assertBindings(WOT_VALIDATION_CONFIG_JOURNAL, Map.of(
                STORED_PAYLOAD, ThingMongoEventAdapter.class,
                THING_EVENT, ThingMongoEventAdapter.class,
                WOT_VALIDATION_CONFIG_EVENT, WotValidationConfigMongoEventAdapter.class));
    }

    @Test
    public void policiesJournalRoutesStoredBsonAndEventsToThePolicyAdapter() throws ClassNotFoundException {
        assertBindings(POLICIES_JOURNAL, Map.of(
                STORED_PAYLOAD, DefaultPolicyMongoEventAdapter.class,
                BASE_EVENT, DefaultPolicyMongoEventAdapter.class));
    }

    @Test
    public void connectionJournalRoutesStoredBsonAndEventsToTheConnectivityAdapter() throws ClassNotFoundException {
        assertBindings(CONNECTION_JOURNAL, Map.of(
                STORED_PAYLOAD, ConnectivityMongoEventAdapter.class,
                BASE_EVENT, ConnectivityMongoEventAdapter.class));
    }

    /**
     * Asserts that the journal block binds exactly the expected types, the stored payload type among them, and that
     * each bound type reaches the expected adapter class through its alias.
     */
    private void assertBindings(final String journalBlock, final Map<String, Class<?>> expectedAdapterByBoundType)
            throws ClassNotFoundException {

        final Config bindings = profile.getConfig(journalBlock + "." + EVENT_ADAPTER_BINDINGS);

        assertThat(bindings.root().keySet())
                .as("%s: bound types; without <%s> a replayed payload reaches the actor undecoded", journalBlock,
                        STORED_PAYLOAD)
                .containsExactlyInAnyOrderElementsOf(expectedAdapterByBoundType.keySet());
        for (final Map.Entry<String, Class<?>> expected : expectedAdapterByBoundType.entrySet()) {
            final String alias = bindings.getString(quoted(expected.getKey()));
            assertThat(adapterClass(journalBlock, alias))
                    .as("%s: adapter bound to <%s> through alias <%s>", journalBlock, expected.getKey(), alias)
                    .isEqualTo(expected.getValue());
        }
    }

    private Class<?> adapterClass(final String journalBlock, final String alias) throws ClassNotFoundException {
        return Class.forName(profile.getString(journalBlock + "." + EVENT_ADAPTERS + "." + alias));
    }

    private Set<String> blocksDeclaringEventAdapters() {
        final ConfigObject root = profile.root();
        return root.keySet().stream()
                .filter(key -> root.get(key).valueType() == ConfigValueType.OBJECT)
                .filter(key -> ((ConfigObject) root.get(key)).containsKey(EVENT_ADAPTERS))
                .collect(Collectors.toSet());
    }

    /** Quotes a fully qualified class name so that its dots are part of one config key. */
    private static String quoted(final String key) {
        return "\"" + key + "\"";
    }

}
