/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.cacheloaders;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.api.persistence.PersistenceLifecycle;
import org.eclipse.ditto.internal.utils.cache.CacheLookupContext;

/**
 * Immutable implementation of {@link org.eclipse.ditto.internal.utils.cache.CacheLookupContext} in scope of the
 * policy enforcement caching.
 */
@Immutable
public final class EnforcementContext implements CacheLookupContext {

    @Nullable private final PersistenceLifecycle persistenceLifecycle;

    private EnforcementContext(@Nullable final PersistenceLifecycle persistenceLifecycle) {
        this.persistenceLifecycle = persistenceLifecycle;
    }

    /**
     * Creates a new EnforcementContext from the passed optional {@code persistenceLifecycle}.
     *
     * @param persistenceLifecycle the persistence lifecycle of the looked up entity.
     * @return the created context.
     */
    public static EnforcementContext of(@Nullable final PersistenceLifecycle persistenceLifecycle) {
        return new EnforcementContext(persistenceLifecycle);
    }


    /**
     * @return The persistence lifecycle of the entity if known.
     */
    public Optional<PersistenceLifecycle> getPersistenceLifecycle() {
        return Optional.ofNullable(persistenceLifecycle);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final EnforcementContext that = (EnforcementContext) o;
        return Objects.equals(persistenceLifecycle, that.persistenceLifecycle);
    }

    @Override
    public int hashCode() {
        return Objects.hash(persistenceLifecycle);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "persistenceLifecycle=" + persistenceLifecycle +
                "]";
    }
}
