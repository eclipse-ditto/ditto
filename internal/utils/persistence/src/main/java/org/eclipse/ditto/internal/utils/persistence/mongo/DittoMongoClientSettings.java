/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.mongo;

import java.time.Duration;
import java.time.temporal.TemporalUnit;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.internal.utils.persistence.mongo.config.MongoDbConfig;

/**
 * Additional settings of a {@link DittoMongoClient} which are not already part of the client's
 * {@link com.mongodb.MongoClientSettings}.
 */
@Immutable
public final class DittoMongoClientSettings {

    private final Duration maxQueryTime;
    private final boolean documentDbCompatibilityMode;

    private DittoMongoClientSettings(final Builder builder) {
        maxQueryTime = builder.maxQueryTime;
        documentDbCompatibilityMode = builder.documentDbCompatibilityMode;
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
     * Returns whether to initialize the DittoMongoClient with the "DocumentDB" compatibility mode enabled or not.
     *
     * @return the DocumentDB compatibility mode.
     */
    public boolean isDocumentDbCompatibilityMode() {
        return documentDbCompatibilityMode;
    }

    /**
     * A mutable builder with a fluent API for a {@code DittoMongoClientSettings}.
     */
    @NotThreadSafe
    public static final class Builder {

        private static final Duration DEFAULT_MAX_QUERY_TIME =
                (Duration) MongoDbConfig.MongoDbConfigValue.MAX_QUERY_TIME.getDefaultValue();

        private static final boolean DEFAULT_DOCUMENTDB_COMPATIBILITY_MODE =
                (boolean) MongoDbConfig.MongoDbConfigValue.DOCUMENT_DB_COMPATIBILITY_MODE.getDefaultValue();

        private Duration maxQueryTime;
        private boolean documentDbCompatibilityMode;

        private Builder() {
            maxQueryTime = DEFAULT_MAX_QUERY_TIME;
            documentDbCompatibilityMode = DEFAULT_DOCUMENTDB_COMPATIBILITY_MODE;
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
         * Default is {@link MongoDbConfig.MongoDbConfigValue#MAX_QUERY_TIME} seconds.
         *
         * @return this builder instance to allow method chaining.
         */
        public Builder maxQueryTime(@Nullable final Duration maxQueryTime) {
            this.maxQueryTime = Objects.requireNonNullElse(maxQueryTime, DEFAULT_MAX_QUERY_TIME);
            return this;
        }

        /**
         * Sets the DocumentDB compatibility mode.
         *
         * @param documentDbCompatibilityMode whether to initialize the DittoMongoClient with the "DocumentDB"
         * compatibility mode enabled or not.
         *
         * @return this builder instance to allow method chaining.
         */
        public Builder documentDbCompatibilityMode(final boolean documentDbCompatibilityMode) {
            this.documentDbCompatibilityMode = documentDbCompatibilityMode;
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
