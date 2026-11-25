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
package org.eclipse.ditto.internal.utils.persistence.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.io.Closeable;
import java.util.List;

import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.javadsl.Source;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.internal.utils.persistence.api.operations.EntityPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.api.operations.NamespacePersistenceOperations;
import org.junit.Test;

/**
 * Unit test for the backend-neutral {@link PersistenceOperationsCollaborators} bundle.
 * <p>
 * The bundle carries the two neutral ops collaborators (either of which may be {@code null} per entity type) and a
 * non-null {@link Closeable} the ops actor closes on {@code postStop}. These tests pin that neutral shape so the
 * backend factories (MongoDB, PostgreSQL) and the ops-actor wiring share one contract. Fakes are plain
 * anonymous classes to keep this test on the existing junit/assertj-only persistence-api test classpath.
 */
public final class PersistenceOperationsCollaboratorsTest {

    private static final NamespacePersistenceOperations NAMESPACE_OPS =
            namespace -> Source.single(List.of());
    private static final EntityPersistenceOperations ENTITIES_OPS = new EntityPersistenceOperations() {
        @Override
        public Source<List<Throwable>, NotUsed> purgeEntity(final EntityId entityId) {
            return Source.single(List.of());
        }
    };
    private static final Closeable CLOSEABLE = () -> {};

    @Test
    public void carriesBothCollaboratorsAndCloseable() {
        final PersistenceOperationsCollaborators underTest =
                PersistenceOperationsCollaborators.of(NAMESPACE_OPS, ENTITIES_OPS, CLOSEABLE);

        assertThat(underTest.namespaceOps()).isSameAs(NAMESPACE_OPS);
        assertThat(underTest.entitiesOps()).isSameAs(ENTITIES_OPS);
        assertThat(underTest.closeable()).isSameAs(CLOSEABLE);
    }

    @Test
    public void namespaceOpsMayBeNull() {
        final PersistenceOperationsCollaborators underTest =
                PersistenceOperationsCollaborators.of(null, ENTITIES_OPS, CLOSEABLE);

        assertThat(underTest.namespaceOps()).isNull();
        assertThat(underTest.entitiesOps()).isSameAs(ENTITIES_OPS);
        assertThat(underTest.closeable()).isSameAs(CLOSEABLE);
    }

    @Test
    public void entitiesOpsMayBeNull() {
        final PersistenceOperationsCollaborators underTest =
                PersistenceOperationsCollaborators.of(NAMESPACE_OPS, null, CLOSEABLE);

        assertThat(underTest.namespaceOps()).isSameAs(NAMESPACE_OPS);
        assertThat(underTest.entitiesOps()).isNull();
        assertThat(underTest.closeable()).isSameAs(CLOSEABLE);
    }

    @Test
    public void closeableIsMandatory() {
        assertThatNullPointerException()
                .isThrownBy(() -> PersistenceOperationsCollaborators.of(NAMESPACE_OPS, null, null));
    }
}
