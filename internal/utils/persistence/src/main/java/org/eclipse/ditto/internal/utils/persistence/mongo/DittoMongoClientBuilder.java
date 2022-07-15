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

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.event.CommandListener;
import com.mongodb.event.ConnectionPoolListener;

/**
 * A mutable builder with a fluent API for step-wise creating an instance of {@link DittoMongoClient}.
 */
@NotThreadSafe
public interface DittoMongoClientBuilder {

    /**
     * Initial step in building a {@code DittoMongoClient}.
     * Here it is determined which kind of connection coordinates are used.
     */
    @NotThreadSafe
    interface ConnectionCoordinatesStep {

        /**
         * Sets the given string as {@code ConnectionString}.
         *
         * @param connectionString the connection string.
         * @return the next step for building a DittoMongoClient.
         * @throws NullPointerException if {@code connectionString} is {@code null}.
         * @throws IllegalArgumentException if {@code connectionString} is invalid.
         */
        GeneralPropertiesStep connectionString(String connectionString);

        /**
         * Sets the host name and the port number of MongoDB.
         *
         * @param hostname the host name of MongoDB.
         * @param portNumber the port number of MongoDB.
         * @return the next step for building a DittoMongoClient.
         * @throws NullPointerException if {@code hostname} is {@code null}.
         */
        DatabaseNameStep hostnameAndPort(CharSequence hostname, int portNumber);

    }

    /**
     * Build step for setting the default database name.
     */
    @NotThreadSafe
    interface DatabaseNameStep {

        /**
         * Sets the name of the default database which is returned by {@link DittoMongoClient#getDefaultDatabase()}.
         * This is the database on which the built DittoMongoClient works by default.
         *
         * @param databaseName the name of the default database.
         * @return the next step for building a DittoMongoClient.
         * @throws NullPointerException if {@code databaseName} is {@code null}.
         */
        GeneralPropertiesStep defaultDatabaseName(CharSequence databaseName);

    }

    /**
     * Step for setting general MongoDB client properties and building an instance on {@link DittoMongoClient}.
     */
    @NotThreadSafe
    interface GeneralPropertiesStep {

        /**
         * Determines whether SSL should be enabled.
         *
         * @param enabled {@code true} if SSL should be enabled, {@code false} else.
         * @return this builder instance to allow method chaining.
         */
        GeneralPropertiesStep enableSsl(boolean enabled);

        /**
         * Sets the maximum duration of a query.
         * Default is defined at {@link org.eclipse.ditto.internal.utils.persistence.mongo.config.MongoDbConfig.MongoDbConfigValue#MAX_QUERY_TIME}.
         *
         * @param maxQueryTime the maximum query time of {@code null} if the default value should be used.
         * @return this builder instance to allow method chaining.
         */
        GeneralPropertiesStep maxQueryTime(@Nullable Duration maxQueryTime);

        /**
         * Sets the minimum number of connections to the database always kept alive in the pool.
         * Default is {@code 0} connections.
         *
         * @param minSize the minimum connection pool size.
         * @return this builder instance to allow method chaining.
         */
        GeneralPropertiesStep connectionPoolMinSize(int minSize);

        /**
         * Sets the maximum allowed number of connections to the database.
         * The connections are kept in a pool.
         * Default is {@code 100} connections.
         *
         * @param maxSize the maximum connection pool size.
         * @return this builder instance to allow method chaining.
         */
        GeneralPropertiesStep connectionPoolMaxSize(int maxSize);

        /**
         * Sets the maximum amount of time a pooled connection is allowed to idle before closing the connection.
         * Default is {@code 0}ms (no upper bound).
         *
         * @param maxConnectionIdleTime maximum allowed time for a connection to idle.
         * @return this builder instance to allow method chaining.
         */
        GeneralPropertiesStep connectionPoolMaxIdleTime(Duration maxConnectionIdleTime);

        /**
         * Sets the maximum time that a thread may wait for a connection to become available.
         * Default is {@code 2} minutes.
         * A value of {@code 0} means that it will not wait.
         * A negative value means it will wait indefinitely.
         *
         * @param maxPoolWaitTime the maximum amount of time to wait.
         * @return this builder instance to allow method chaining.
         * @throws NullPointerException if {@code maxPoolWaitTime} is {@code null}.
         */
        GeneralPropertiesStep connectionPoolMaxWaitTime(Duration maxPoolWaitTime);

        /**
         * Adds a custom CommandListener.
         *
         * @param commandListener the listener to be added.
         * @return this builder instance to allow method chaining.
         */
        GeneralPropertiesStep addCommandListener(@Nullable CommandListener commandListener);

        /**
         * Determines whether a {@code JMXConnectionPoolListener} will be added.
         *
         * @param enabled adds a JMXConnectionPoolListener as ConnectionPoolListener if {@code true}.
         * @return this builder instance to allow method chaining.
         */
        GeneralPropertiesStep enableJmxListener(boolean enabled);

        /**
         * Adds a custom ConnectionPoolListener.
         *
         * @param connectionPoolListener the listener to be added.
         * @return this builder instance to allow method chaining.
         */
        GeneralPropertiesStep addConnectionPoolListener(@Nullable ConnectionPoolListener connectionPoolListener);

        /**
         * Sets the read preference that should be used for the mongo client.
         *
         * @param readPreference the read preference.
         * @return this builder instance with the updated read preference.
         */
        GeneralPropertiesStep setReadPreference(ReadPreference readPreference);

        /**
         * Sets the read concern that should be used for the mongo client.
         *
         * @param readConcern the read concern.
         * @return this builder instance with the updated read concern.
         */
        GeneralPropertiesStep setReadConcern(ReadConcern readConcern);

        /**
         * Sets the write concern that should be used for the mongo client.
         *
         * @param writeConcern the write concern.
         * @return this builder instance with the updated write concern.
         */
        GeneralPropertiesStep setWriteConcern(WriteConcern writeConcern);

        /**
         * Sets if "retryWrites" should be used for the mongo client.
         *
         * @param retryWrites the "retryWrites".
         * @return this builder instance with the updated retryWrites.
         */
        GeneralPropertiesStep setRetryWrites(boolean retryWrites);

        /**
         * Creates a new instance of {@code DittoMongoClient} with the properties of this builder.
         * This builder instance should not be further used after calling this method!
         *
         * @return the new instance.
         */
        DittoMongoClient build();

    }

}
