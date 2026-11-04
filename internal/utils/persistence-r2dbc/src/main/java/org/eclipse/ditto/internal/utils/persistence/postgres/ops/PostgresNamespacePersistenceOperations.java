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

import org.eclipse.ditto.internal.utils.persistence.api.operations.NamespacePersistenceOperations;

import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.javadsl.Source;

import reactor.core.publisher.Mono;

/**
 * PostgreSQL namespace-purge {@link NamespacePersistenceOperations}, the Postgres mirror of
 * {@code MongoNamespacePersistenceOperations} (task B2). It deletes <em>all</em> persisted rows of a namespace from the
 * three per-entity tables ({@code <e>_journal}, {@code <e>_journal_seq}, {@code <e>_snaps}) via the real SQL
 * {@code DELETE … WHERE pid LIKE '<prefix><namespace>:%'} primitive on {@link PostgresPersistenceOperations}, selecting
 * by the persistence-id prefix exactly as the Mongo selection provider's namespace pid-prefix regex does.
 * <p>
 * The {@code purge} contract mirrors {@code MongoOpsUtil}: the returned {@code Source} emits a single
 * {@code List<Throwable>} — empty on success, a singleton list carrying the failure on error — so the
 * {@code AbstractPersistenceOperationsActor} treats the two backends identically.
 *
 * @since 4.0.0
 */
public final class PostgresNamespacePersistenceOperations implements NamespacePersistenceOperations {

    private final PostgresPersistenceOperations operations;
    private final String pidPrefix;

    private PostgresNamespacePersistenceOperations(final PostgresPersistenceOperations operations,
            final String pidPrefix) {
        this.operations = checkNotNull(operations, "operations");
        this.pidPrefix = checkNotNull(pidPrefix, "pidPrefix");
    }

    /**
     * @param operations the shared persistence operations bound to the entity's tables.
     * @param pidPrefix the per-entity persistence-id prefix including the trailing colon (e.g. {@code "thing:"}).
     * @return the namespace-purge operations.
     */
    public static PostgresNamespacePersistenceOperations of(final PostgresPersistenceOperations operations,
            final String pidPrefix) {
        return new PostgresNamespacePersistenceOperations(operations, pidPrefix);
    }

    @Override
    public Source<List<Throwable>, NotUsed> purge(final CharSequence namespace) {
        checkNotNull(namespace, "namespace");
        // purgeNamespace is a Mono<Void> (no element on success); thenReturn the empty error list so exactly one
        // List<Throwable> is emitted, and onErrorResume converts a failure into the singleton-error list (MongoOpsUtil
        // parity). The whole delete transaction is wrapped, so an error from any of the three table deletes surfaces here.
        final Mono<List<Throwable>> result = operations.purgeNamespace(pidPrefix, namespace)
                .thenReturn(List.<Throwable>of())
                .onErrorResume(throwable -> Mono.just(List.of(throwable)));
        return Source.fromPublisher(result);
    }

}
