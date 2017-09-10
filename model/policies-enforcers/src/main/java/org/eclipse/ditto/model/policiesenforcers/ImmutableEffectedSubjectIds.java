/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.policiesenforcers;

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * An immutable implementation of {@link EffectedSubjectIds}.
 */
@Immutable
public final class ImmutableEffectedSubjectIds implements EffectedSubjectIds {

    private final Set<String> grantedSubjectIds;
    private final Set<String> revokedSubjectIds;

    private ImmutableEffectedSubjectIds(final Collection<String> grantedSubjectIds,
            final Collection<String> revokedSubjectIds) {

        this.grantedSubjectIds = makeUnmodifiable(checkNotNull(grantedSubjectIds, "granted Subject IDs"));
        this.revokedSubjectIds = makeUnmodifiable(checkNotNull(revokedSubjectIds, "revoked Subject IDs"));
    }

    private ImmutableEffectedSubjectIds(final Builder builder) {
        this(builder.grantedSubjectIds, builder.revokedSubjectIds);
    }

    private static Set<String> makeUnmodifiable(final Collection<String> subjectIds) {
        return Collections.unmodifiableSet(new HashSet<>(subjectIds));
    }

    /**
     * Returns an {@code ImmutableEffectedSubjectIds} object.
     *
     * @param grantedSubjectIds the subject IDs which have granted permissions.
     * @param revokedSubjectIds the subject IDs which have revoked permissions.
     * @return the constructed object.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ImmutableEffectedSubjectIds of(final Collection<String> grantedSubjectIds,
            final Collection<String> revokedSubjectIds) {

        return new ImmutableEffectedSubjectIds(grantedSubjectIds, revokedSubjectIds);
    }

    /**
     * Returns an {@code ImmutableEffectedSubjectIds} object with subject IDs having granted permissions.
     *
     * @param grantedSubjectIds the subject IDs which have granted permissions.
     * @return the constructed object.
     * @throws NullPointerException if {@code grantedSubjectIds} is {@code null}.
     */
    public static ImmutableEffectedSubjectIds ofGranted(final Collection<String> grantedSubjectIds) {
        return of(grantedSubjectIds, Collections.emptySet());
    }

    /**
     * Returns an {@code ImmutableEffectedSubjectIds} object with subject IDs having revoked permissions.
     *
     * @param revokedSubjectIds the subject IDs which have revoked permissions.
     * @return the constructed object.
     * @throws NullPointerException if {@code revokedSubjectIds} is {@code null}.
     */
    public static ImmutableEffectedSubjectIds ofRevoked(final Collection<String> revokedSubjectIds) {
        return of(Collections.emptySet(), revokedSubjectIds);
    }

    /**
     * Returns a new mutable builder with a fluent API for a {@code ImmutableEffectedSubjectIds}.
     *
     * @return the builder.
     */
    public static Builder getBuilder() {
        return new Builder();
    }

    @Override
    public Set<String> getGranted() {
        return grantedSubjectIds;
    }

    @Override
    public Set<String> getRevoked() {
        return revokedSubjectIds;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableEffectedSubjectIds that = (ImmutableEffectedSubjectIds) o;
        return Objects.equals(grantedSubjectIds, that.grantedSubjectIds) &&
                Objects.equals(revokedSubjectIds, that.revokedSubjectIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(grantedSubjectIds, revokedSubjectIds);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "grantedSubjectIds=" + grantedSubjectIds +
                ", revokedSubjectIds=" + revokedSubjectIds +
                "]";
    }

    /**
     * A mutable builder with a fluent API for a {@code ImmutableEffectedSubjectIds}.
     */
    @NotThreadSafe
    public static final class Builder {

        private final Set<String> grantedSubjectIds;
        private final Set<String> revokedSubjectIds;

        private Builder() {
            grantedSubjectIds = new HashSet<>();
            revokedSubjectIds = new HashSet<>();
        }

        /**
         * Adds the specified subject ID which is known to has granted permissions.
         *
         * @param grantedSubjectId the subject ID to be added.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code grantedSubjectId} is {@code null}.
         * @throws IllegalArgumentException if {@code grantedSubjectId} is empty.
         */
        public Builder withGranted(final CharSequence grantedSubjectId) {
            argumentNotEmpty(grantedSubjectId, "subject ID with granted permissions");
            if (!revokedSubjectIds.contains(grantedSubjectId.toString())) {
                grantedSubjectIds.add(grantedSubjectId.toString());
            }
            return this;
        }

        /**
         * Adds the specified subject ID which is known to has revoked permissions.
         *
         * @param revokedSubjectId the subject ID to be added.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code grantedSubjectId} is {@code null}.
         * @throws IllegalArgumentException if {@code grantedSubjectId} is empty.
         */
        public Builder withRevoked(final CharSequence revokedSubjectId) {
            argumentNotEmpty(revokedSubjectId, "subject ID with revoked permissions");
            revokedSubjectIds.add(revokedSubjectId.toString());
            grantedSubjectIds.remove(revokedSubjectId.toString());
            return this;
        }

        /**
         * Builds the new {@code ImmutableEffectedSubjectIds} instance.
         *
         * @return the instance.
         */
        public ImmutableEffectedSubjectIds build() {
            return new ImmutableEffectedSubjectIds(this);
        }

    }

}
