/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.concierge.service.enforcement;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;

/**
 * An enforcement task to be scheduled.
 */
final class EnforcementTask {

    private final EntityId entityId;
    private final boolean changesAuthorization;
    private final Supplier<CompletionStage<Contextual<?>>> taskSupplier;

    private EnforcementTask(final EntityId entityId,
            final boolean changesAuthorization, final Supplier<CompletionStage<Contextual<?>>> taskSupplier) {
        this.entityId = entityId;
        this.taskSupplier = taskSupplier;
        this.changesAuthorization = changesAuthorization;
    }

    /**
     * Create an enforcement task to be executed later.
     *
     * @param entityId entity ID for sequentialization.
     * @param changesAuthorization whether dispatching the signal would change authorization for subsequent signals.
     * @param taskSupplier supplier that when called, starts the enforcement task and returns the result as a future.
     * @param <T> type of enforced signals.
     * @return the task.
     */
    @SuppressWarnings("unchecked") // due to parameterized cast
    static <T extends WithDittoHeaders> EnforcementTask of(final EntityId entityId,
            final boolean changesAuthorization,
            final Supplier<CompletionStage<Contextual<T>>> taskSupplier) {
        // The cast is safe: Supplier and CompletionStage are both covariant in its type parameter.
        final Supplier<CompletionStage<Contextual<?>>> theTaskSupplier =
                (Supplier<CompletionStage<Contextual<?>>>) (Object) taskSupplier;
        return new EnforcementTask(entityId, changesAuthorization, theTaskSupplier);
    }

    EntityId getEntityId() {
        return entityId;
    }

    boolean changesAuthorization() {
        return changesAuthorization;
    }

    CompletionStage<Contextual<?>> start() {
        return taskSupplier.get();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "[entityId=" + entityId +
                ",changesAuthorization=" + changesAuthorization +
                "]";
    }
}
