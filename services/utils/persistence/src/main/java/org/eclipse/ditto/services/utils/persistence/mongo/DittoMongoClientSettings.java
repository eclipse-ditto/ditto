/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.persistence.mongo;

import java.time.Duration;
import java.time.temporal.TemporalUnit;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.services.utils.config.MongoConfig;

/**
 * Additional settings of a {@link DittoMongoClient} which are not already part of the client's
 * {@link com.mongodb.async.client.MongoClientSettings}.
 */
@Immutable
public final class DittoMongoClientSettings {

    private final Duration maxQueryTime;

    private DittoMongoClientSettings(final Builder builder) {
        maxQueryTime = builder.maxQueryTime;
    }

    /**
     * Returns an empty builder with a fluent API for constructing an instance of {@code DittoMongoClientSettings}.
     *
     * @return the builder object.
     */
    public static Builder getBuilder() {
        return new Builder();
    }

    /**
     * Returns the maximum amount of time for a query.
     *
     * @return the max query time.
     */
    public Duration getMaxQueryTime() {
        return maxQueryTime;
    }

    /**
     * A mutable builder with a fluent API for a {@code DittoMongoClientSettings}.
     */
    @NotThreadSafe
    public static final class Builder {

        private Duration maxQueryTime;

        private Builder() {
            maxQueryTime = Duration.ofSeconds(MongoConfig.MAX_QUERY_TIME_DEFAULT_SECS);
        }

        /**
         * Sets the maximum amount of time a Mongo query may last.
         *
         * @param amount the amount of time.
         * @param temporalUnit the temporal unit, which defines {@code amount}.
         * @return this builder instance to allow method chaining.
         * @throws NullPointerException if {@code temporalUnit} is {@code null}.
         */
        public Builder maxQueryTime(final long amount, final TemporalUnit temporalUnit) {
            return maxQueryTime(Duration.of(amount, temporalUnit));
        }

        /**
         * Sets the maximum amount of time a Mongo query may last.
         *
         * @param maxQueryTime the maximum query duration or {@code null} if the default value should be used.
         * Default is {@value org.eclipse.ditto.services.utils.config.MongoConfig#MAX_QUERY_TIME_DEFAULT_SECS} seconds.
         *
         * @return this builder instance to allow method chaining.
         */
        public Builder maxQueryTime(@Nullable final Duration maxQueryTime) {
            if (null != maxQueryTime) {
                this.maxQueryTime = maxQueryTime;
            } else {
                this.maxQueryTime = Duration.ofSeconds(MongoConfig.MAX_QUERY_TIME_DEFAULT_SECS);
            }
            return this;
        }

        /**
         * Constructs a new {@code DittoMongoClientBuilder} object using the properties of this builder.
         *
         * @return the new DittoMongoClientBuilder object.
         */
        public DittoMongoClientSettings build() {
            return new DittoMongoClientSettings(this);
        }

    }

}
