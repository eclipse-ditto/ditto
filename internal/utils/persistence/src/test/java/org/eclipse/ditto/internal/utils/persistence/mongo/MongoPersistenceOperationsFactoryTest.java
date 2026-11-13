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

import java.io.Closeable;

import org.eclipse.ditto.internal.utils.persistence.api.PersistenceOperationsCollaborators;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;

/**
 * Unit test for {@link MongoPersistenceOperationsFactory}.
 * <p>
 * Pins the per-entityType collaborator wiring against the historical per-service ops actors (behaviour reference):
 * <ul>
 *     <li>{@code thing} &rarr; namespaceOps only (mirrors {@code ThingPersistenceOperationsActor.props}),</li>
 *     <li>{@code policy} &rarr; both ops (mirrors {@code PolicyPersistenceOperationsActor.props}),</li>
 *     <li>{@code connection} &rarr; entitiesOps only (mirrors {@code ConnectionPersistenceOperationsActor.props}).</li>
 * </ul>
 * and pins the {@code Closeable} as the Mongo client (closing it is safe).
 * <p>
 * This is a plain unit test, not a Testcontainers IT: {@code MongoClientWrapper.newInstance} (and the underlying
 * {@code MongoClients.create}) is lazy — it does NOT open a connection until the first database operation — so the
 * factory builds a client and the namespace/entities collaborators against a non-routable test URI without any live
 * MongoDB. The collaborators only touch the database on {@code purge(...)}, which these tests never call.
 */
public final class MongoPersistenceOperationsFactoryTest {

    /**
     * A self-contained config: a (non-routable, never-connected) Mongo URI plus the per-entity
     * {@code pekko-contrib-mongodb-persistence-*} blocks carrying the {@code overrides.*-collection} paths that
     * {@code MongoEventSourceSettings.fromConfig} reads. In production these blocks come from the service
     * {@code reference.conf}s via {@code actorSystem.settings().config()}; here we inline exactly the keys the factory
     * reads so the test needs no service module on the classpath.
     */
    private static final Config CONFIG = ConfigFactory.parseString("""
            ditto.mongodb.uri = "mongodb://localhost:27017/testdb"

            # Plugin-id overrides so the provider resolves the real (non-pluralized) Mongo block names — the same
            # mapping the shipped reference.conf provides. Without these, "connection" would pluralize to
            # "...-connections-*" and the event-source-settings lookup below would miss.
            ditto.extensions.persistence-backend-provider.extension-config.plugin-ids {
              thing.journal       = "pekko-contrib-mongodb-persistence-things-journal"
              thing.snapshot      = "pekko-contrib-mongodb-persistence-things-snapshots"
              policy.journal      = "pekko-contrib-mongodb-persistence-policies-journal"
              policy.snapshot     = "pekko-contrib-mongodb-persistence-policies-snapshots"
              connection.journal  = "pekko-contrib-mongodb-persistence-connection-journal"
              connection.snapshot = "pekko-contrib-mongodb-persistence-connection-snapshots"
            }

            pekko-contrib-mongodb-persistence-things-journal.overrides {
              journal-collection  = "things_journal"
              metadata-collection = "things_metadata"
            }
            pekko-contrib-mongodb-persistence-things-snapshots.overrides {
              snaps-collection = "things_snaps"
            }
            pekko-contrib-mongodb-persistence-policies-journal.overrides {
              journal-collection  = "policies_journal"
              metadata-collection = "policies_metadata"
            }
            pekko-contrib-mongodb-persistence-policies-snapshots.overrides {
              snaps-collection = "policies_snaps"
            }
            pekko-contrib-mongodb-persistence-connection-journal.overrides {
              journal-collection  = "connection_journal"
              metadata-collection = "connection_metadata"
            }
            pekko-contrib-mongodb-persistence-connection-snapshots.overrides {
              snaps-collection = "connection_snaps"
            }
            """).resolve();

    private static ActorSystem system;
    private static MongoPersistenceOperationsFactory factory;

    @BeforeClass
    public static void setUp() {
        system = ActorSystem.create("MongoPersistenceOperationsFactoryTest", CONFIG);
        factory = new MongoPersistenceOperationsFactory(system, CONFIG);
    }

    @AfterClass
    public static void tearDown() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
            system = null;
        }
    }

    @Test
    public void thingYieldsNamespaceOpsOnly() {
        final PersistenceOperationsCollaborators collaborators = factory.operations("thing");

        assertThat(collaborators.namespaceOps()).isNotNull();
        assertThat(collaborators.entitiesOps()).isNull();
        assertCloseableIsClient(collaborators);
    }

    @Test
    public void policyYieldsBothOps() {
        final PersistenceOperationsCollaborators collaborators = factory.operations("policy");

        assertThat(collaborators.namespaceOps()).isNotNull();
        assertThat(collaborators.entitiesOps()).isNotNull();
        assertCloseableIsClient(collaborators);
    }

    @Test
    public void connectionYieldsEntitiesOpsOnly() {
        final PersistenceOperationsCollaborators collaborators = factory.operations("connection");

        assertThat(collaborators.namespaceOps()).isNull();
        assertThat(collaborators.entitiesOps()).isNotNull();
        assertCloseableIsClient(collaborators);
    }

    @Test
    public void buildsAFreshClientPerCallAndDoesNotShareIt() {
        final PersistenceOperationsCollaborators first = factory.operations("thing");
        final PersistenceOperationsCollaborators second = factory.operations("thing");

        // Laziness/ownership contract: every call owns its own client; nothing is cached or shared across ops actors.
        assertThat(first.closeable()).isNotSameAs(second.closeable());
    }

    private static void assertCloseableIsClient(final PersistenceOperationsCollaborators collaborators) {
        final Closeable closeable = collaborators.closeable();
        assertThat(closeable).isInstanceOf(MongoClientWrapper.class);
        // Closing the client must be safe (no live connection was ever opened).
        try {
            closeable.close();
        } catch (final Exception e) {
            throw new AssertionError("closing the Mongo client must be safe", e);
        }
    }
}
