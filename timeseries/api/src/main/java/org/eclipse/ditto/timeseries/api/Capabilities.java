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
package org.eclipse.ditto.timeseries.api;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.timeseries.model.Aggregation;
import org.eclipse.ditto.timeseries.model.FillStrategy;

/**
 * Declares what a {@link TimeseriesAdapter} can compute <em>natively</em> (i.e. push down into its
 * backend) versus what must be computed in the shared, backend-neutral compute kernel.
 * <p>
 * A query planner reads this to decide how to run each query: whether the adapter's own
 * {@code query(...)} is a complete executor ({@link #supportsNativeQuery()}), which aggregations the
 * backend computes in its engine ({@link #getPushableAggregations()}), and which gap-fill strategies
 * it applies natively ({@link #getNativeFillStrategies()}). Advertising a capability only ever
 * changes <em>where</em> a result is computed, never the result — the kernel remains the reference
 * answer.
 * <p>
 * Instances are immutable; the declared sets are defensively copied and returned unmodifiable.
 *
 * @since 4.0.0
 */
@Immutable
public final class Capabilities {

    private final boolean supportsNativeQuery;
    private final Set<Aggregation> pushableAggregations;
    private final Set<FillStrategy> nativeFillStrategies;

    private Capabilities(final boolean supportsNativeQuery,
            final Set<Aggregation> pushableAggregations,
            final Set<FillStrategy> nativeFillStrategies) {

        this.supportsNativeQuery = supportsNativeQuery;
        this.pushableAggregations = immutableCopy(pushableAggregations, Aggregation.class);
        this.nativeFillStrategies = immutableCopy(nativeFillStrategies, FillStrategy.class);
    }

    /**
     * The capabilities of a minimal backend that can only store and return raw points: nothing is
     * pushed down, so the planner computes every query in the kernel over {@code scan(...)} output.
     *
     * @return the minimal (scan-only) capabilities.
     */
    public static Capabilities minimal() {
        return new Capabilities(false, EnumSet.noneOf(Aggregation.class),
                EnumSet.noneOf(FillStrategy.class));
    }

    /**
     * The capabilities of a backend whose {@code query(...)} is a complete executor — the planner
     * delegates whole queries to it and never needs {@link TimeseriesAdapter#scan}. This is the
     * default for any adapter that implements the required {@code query(...)} without opting into
     * scan-based planning; a scan-only backend overrides {@code capabilities()} to return
     * {@link #minimal()} instead.
     *
     * @return capabilities declaring a complete native query and nothing else.
     */
    public static Capabilities nativeQuery() {
        return new Capabilities(true, EnumSet.noneOf(Aggregation.class),
                EnumSet.noneOf(FillStrategy.class));
    }

    /**
     * @return a new builder for declaring richer capabilities.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return {@code true} if this adapter's
     * {@link TimeseriesAdapter#query(org.eclipse.ditto.timeseries.model.TimeseriesQuery)} is a
     * complete executor able to answer any valid query itself. The planner then delegates the whole
     * query to the adapter (the fast path). A backend for which this is {@code false} is driven by
     * the planner via {@link TimeseriesAdapter#scan} + the compute kernel (the portable path).
     */
    public boolean supportsNativeQuery() {
        return supportsNativeQuery;
    }

    /**
     * @param aggregation the aggregation to check.
     * @return {@code true} if the backend computes this aggregation natively rather than the kernel
     * computing it from scanned points.
     * @throws NullPointerException if {@code aggregation} is {@code null}.
     */
    public boolean canPushDown(final Aggregation aggregation) {
        return pushableAggregations.contains(checkNotNull(aggregation, "aggregation"));
    }

    /**
     * @param fillStrategy the fill strategy to check.
     * @return {@code true} if the backend fills gaps for this strategy natively rather than the
     * kernel filling them.
     * @throws NullPointerException if {@code fillStrategy} is {@code null}.
     */
    public boolean canFillNatively(final FillStrategy fillStrategy) {
        return nativeFillStrategies.contains(checkNotNull(fillStrategy, "fillStrategy"));
    }

    /**
     * @return the aggregations the backend can compute natively (per {@code step} bucket).
     */
    public Set<Aggregation> getPushableAggregations() {
        return pushableAggregations;
    }

    /**
     * @return the fill strategies the backend applies natively.
     */
    public Set<FillStrategy> getNativeFillStrategies() {
        return nativeFillStrategies;
    }

    private static <E extends Enum<E>> Set<E> immutableCopy(final Set<E> set, final Class<E> type) {
        checkNotNull(set, "capability set");
        return set.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(set));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Capabilities that = (Capabilities) o;
        return supportsNativeQuery == that.supportsNativeQuery
                && pushableAggregations.equals(that.pushableAggregations)
                && nativeFillStrategies.equals(that.nativeFillStrategies);
    }

    @Override
    public int hashCode() {
        return Objects.hash(supportsNativeQuery, pushableAggregations, nativeFillStrategies);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " ["
                + "supportsNativeQuery=" + supportsNativeQuery
                + ", pushableAggregations=" + pushableAggregations
                + ", nativeFillStrategies=" + nativeFillStrategies
                + "]";
    }

    /**
     * Mutable builder for {@link Capabilities}. Unset properties default to the
     * {@linkplain #minimal() minimal} (scan-only) values.
     */
    public static final class Builder {

        private boolean supportsNativeQuery = false;
        private Set<Aggregation> pushableAggregations = EnumSet.noneOf(Aggregation.class);
        private Set<FillStrategy> nativeFillStrategies = EnumSet.noneOf(FillStrategy.class);

        private Builder() {
            // use Capabilities.builder()
        }

        /**
         * @param supported whether the adapter's {@code query(...)} is a complete executor (the
         * planner may delegate whole queries to it).
         * @return this builder.
         */
        public Builder supportsNativeQuery(final boolean supported) {
            this.supportsNativeQuery = supported;
            return this;
        }

        /**
         * @param aggregations the aggregations the backend can compute natively per bucket.
         * @return this builder.
         * @throws NullPointerException if {@code aggregations} is {@code null}.
         */
        public Builder pushableAggregations(final Set<Aggregation> aggregations) {
            this.pushableAggregations = checkNotNull(aggregations, "aggregations");
            return this;
        }

        /**
         * @param fillStrategies the fill strategies the backend applies natively.
         * @return this builder.
         * @throws NullPointerException if {@code fillStrategies} is {@code null}.
         */
        public Builder nativeFillStrategies(final Set<FillStrategy> fillStrategies) {
            this.nativeFillStrategies = checkNotNull(fillStrategies, "fillStrategies");
            return this;
        }

        /**
         * @return the immutable {@link Capabilities}.
         */
        public Capabilities build() {
            return new Capabilities(supportsNativeQuery, pushableAggregations, nativeFillStrategies);
        }
    }
}
