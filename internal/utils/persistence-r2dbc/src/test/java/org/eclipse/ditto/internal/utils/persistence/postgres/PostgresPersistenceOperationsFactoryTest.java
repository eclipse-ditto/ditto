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

import org.eclipse.ditto.internal.utils.persistence.postgres.client.PostgresClientExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.internal.utils.persistence.api.PersistenceOperationsCollaborators;
import org.eclipse.ditto.internal.utils.persistence.api.PersistenceOperationsFactory;
import org.eclipse.ditto.internal.utils.persistence.api.streaming.NoOpCloseable;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresEntitiesPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresNamespacePersistenceOperations;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link PostgresPersistenceOperationsFactory} (mirroring the Mongo wiring test): the
 * per-entityType collaborator shape (thing→namespace-only, policy→both, connection→entity-only), the no-op closeable,
 * and the clear failure on an unknown entity type.
 * <p>
 * Runs offline: building the factory's collaborators resolves the single shared pool from {@code PostgresClientExtension}
 * (which constructs an R2DBC pool but never connects with {@code ssl.mode=disable}); no SQL is executed here.
 */
public final class PostgresPersistenceOperationsFactoryTest {

    private static ActorSystem system;
    private static PersistenceOperationsFactory factory;

    @BeforeClass
    public static void setUp() {
        // A complete-enough ditto.postgresql so PostgresClientExtension can build the pool without connecting.
        system = ActorSystem.create("PostgresPersistenceOperationsFactoryTest",
                ConfigFactory.parseString(
                                "ditto.postgresql.uri = \"r2dbc:postgresql://localhost:5432/ditto\"\n"
                                        + "ditto.postgresql.username = \"ditto\"\n"
                                        + "ditto.postgresql.password = \"secret\"\n"
                                        + "ditto.postgresql.ssl.mode = \"disable\"\n")
                        .withFallback(ConfigFactory.load()));
        factory = PostgresPersistenceOperationsFactory.of(system);
    }

    @AfterClass
    public static void tearDown() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
            system = null;
        }
    }

    @Test
    public void thingWiresNamespaceOpsOnly() {
        final PersistenceOperationsCollaborators bundle = factory.operations("thing");

        assertThat(bundle.namespaceOps()).isInstanceOf(PostgresNamespacePersistenceOperations.class);
        assertThat(bundle.entitiesOps()).isNull();
        assertThat(bundle.closeable()).isInstanceOf(NoOpCloseable.class);
    }

    @Test
    public void policyWiresBothNamespaceAndEntityOps() {
        final PersistenceOperationsCollaborators bundle = factory.operations("policy");

        assertThat(bundle.namespaceOps()).isInstanceOf(PostgresNamespacePersistenceOperations.class);
        assertThat(bundle.entitiesOps()).isInstanceOf(PostgresEntitiesPersistenceOperations.class);
        assertThat(bundle.closeable()).isInstanceOf(NoOpCloseable.class);
    }

    @Test
    public void connectionWiresEntityOpsOnly() {
        final PersistenceOperationsCollaborators bundle = factory.operations("connection");

        assertThat(bundle.namespaceOps()).isNull();
        assertThat(bundle.entitiesOps()).isInstanceOf(PostgresEntitiesPersistenceOperations.class);
        assertThat(bundle.closeable()).isInstanceOf(NoOpCloseable.class);
    }

    @Test
    public void unknownEntityTypeFailsClearly() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> factory.operations("gateway"))
                .withMessageContaining("gateway");
    }
}
