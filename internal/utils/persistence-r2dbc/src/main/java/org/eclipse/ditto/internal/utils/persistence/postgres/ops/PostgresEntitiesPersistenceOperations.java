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
package org.eclipse.ditto.internal.utils.persistence.postgres.ops;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.List;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.internal.utils.persistence.api.operations.EntityPersistenceOperations;

import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.javadsl.Source;

import reactor.core.publisher.Mono;

/**
 * PostgreSQL entity-purge {@link EntityPersistenceOperations}, the Postgres mirror of
 * {@code MongoEntitiesPersistenceOperations} (task B2). It deletes <em>all</em> persisted rows of a single entity from
 * the three per-entity tables ({@code <e>_journal}, {@code <e>_journal_seq}, {@code <e>_snaps}) via the real SQL
 * {@code DELETE … WHERE pid = '<prefix><entityId>'} primitive on {@link PostgresPersistenceOperations}, selecting by the
 * exact persistence id exactly as the Mongo selection provider's entity pid equality does.
 * <p>
 * The {@code purgeEntity} contract mirrors {@code MongoOpsUtil}: the returned {@code Source} emits a single
 * {@code List<Throwable>} — empty on success, a singleton list carrying the failure on error.
 *
 * @since 4.0.0
 */
public final class PostgresEntitiesPersistenceOperations implements EntityPersistenceOperations {

    private final PostgresPersistenceOperations operations;
    private final String pidPrefix;

    private PostgresEntitiesPersistenceOperations(final PostgresPersistenceOperations operations,
            final String pidPrefix) {
        this.operations = checkNotNull(operations, "operations");
        this.pidPrefix = checkNotNull(pidPrefix, "pidPrefix");
    }

    /**
     * @param operations the shared persistence operations bound to the entity's tables.
     * @param pidPrefix the per-entity persistence-id prefix including the trailing colon (e.g. {@code "connection:"}).
     * @return the entity-purge operations.
     */
    public static PostgresEntitiesPersistenceOperations of(final PostgresPersistenceOperations operations,
            final String pidPrefix) {
        return new PostgresEntitiesPersistenceOperations(operations, pidPrefix);
    }

    @Override
    public Source<List<Throwable>, NotUsed> purgeEntity(final EntityId entityId) {
        checkNotNull(entityId, "entityId");
        // The persistence id is the entity-type prefix concatenated with the entity id, identical to the per-service
        // ops actors (e.g. ConnectionPersistenceActor.PERSISTENCE_ID_PREFIX + entityId).
        final String pid = pidPrefix + entityId;
        // purgeEntity is a Mono<Void> (no element on success); thenReturn the empty error list so exactly one
        // List<Throwable> is emitted, and onErrorResume converts a failure into the singleton-error list (MongoOpsUtil
        // parity). The delete is one transaction across the three tables.
        final Mono<List<Throwable>> result = operations.purgeEntity(pid)
                .thenReturn(List.<Throwable>of())
                .onErrorResume(throwable -> Mono.just(List.of(throwable)));
        return Source.fromPublisher(result);
    }

}
