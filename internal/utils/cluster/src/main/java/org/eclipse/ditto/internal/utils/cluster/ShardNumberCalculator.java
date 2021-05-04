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
package org.eclipse.ditto.internal.utils.cluster;

import java.text.MessageFormat;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;

/**
 * Calculates the shard number for a particular entity ID based on the number of shards.
 *
 * @since 2.0.0
 */
@Immutable
public final class ShardNumberCalculator {

    private final int numberOfShards;

    private ShardNumberCalculator(final int numberOfShards) {
        this.numberOfShards = numberOfShards;
    }

    /**
     * Returns a new instance of ShardIdCalculator.
     *
     * @return the instance.
     * @throws IllegalArgumentException if {@code numberOfShards} is less than one.
     */
    public static ShardNumberCalculator newInstance(final int numberOfShards) {
        if (1 > numberOfShards) {
            throw new IllegalArgumentException(MessageFormat.format("The number of shards <{0}> is less than one.",
                    numberOfShards));
        }
        return new ShardNumberCalculator(numberOfShards);
    }

    /**
     * Calculates the number of the shard the specified entity ID belongs to.
     *
     * @param entityId the entity ID to get the shard number for.
     * @return the shard number.
     * @throws NullPointerException if {@code entityId} is {@code null}.
     */
    public int calculateShardNumber(final String entityId) {
        ConditionChecker.checkNotNull(entityId, "entityId");
        final var nonNegativeHashCode = ensureNonNegative(entityId.hashCode());
        return nonNegativeHashCode % numberOfShards;
    }

    // Make sure not to negate Integer.MIN_VALUE because -Integer.MIN_VALUE == Integer.MIN_VALUE < 0.
    private static int ensureNonNegative(final int hashCode) {
        return Integer.MIN_VALUE == hashCode ? 0 : Math.abs(hashCode);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (ShardNumberCalculator) o;
        return numberOfShards == that.numberOfShards;
    }

    @Override
    public int hashCode() {
        return Objects.hash(numberOfShards);
    }

}
