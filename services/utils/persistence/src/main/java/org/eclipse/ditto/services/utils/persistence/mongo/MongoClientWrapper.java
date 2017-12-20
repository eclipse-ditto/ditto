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
package org.eclipse.ditto.services.utils.persistence.mongo;

import java.io.Closeable;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.services.utils.config.MongoConfig;

import com.mongodb.ConnectionString;
import com.mongodb.ReadPreference;
import com.mongodb.ServerAddress;
import com.mongodb.async.client.MongoClientSettings;
import com.mongodb.connection.ClusterSettings;
import com.mongodb.connection.ConnectionPoolSettings;
import com.mongodb.connection.SslSettings;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.typesafe.config.Config;

/**
 * MongoDB Client Wrapper.
 */
public class MongoClientWrapper implements Closeable {
    // not final to test with Mockito

    private final MongoClient mongoClient;
    private final MongoDatabase mongoDatabase;

    /**
     * Initializes the persistence with a passed in {@code database} and {@code clientSettings}.
     *
     * @param database the host name of the mongoDB database.
     * @param mongoClientSettings the settings to use.
     */
    private MongoClientWrapper(final String database, final MongoClientSettings mongoClientSettings) {
        mongoClient = MongoClients.create(mongoClientSettings);
        mongoDatabase = mongoClient.getDatabase(database);
    }

    /**
     * Initializes the persistence with a passed in {@code config} containing the {@code uri}.
     *
     * @param config Config containing mongoDB settings including the URI.
     * @return a new {@code MongoClientWrapper} object.
     */
    public static MongoClientWrapper newInstance(final Config config) {
        final int maxPoolSize = MongoConfig.getPoolMaxSize(config);
        final int maxPoolWaitQueueSize = MongoConfig.getPoolMaxWaitQueueSize(config);
        final Duration maxPoolWaitTime = MongoConfig.getPoolMaxWaitTime(config);
        final String uri = MongoConfig.getMongoUri(config);
        final ConnectionString connectionString = new ConnectionString(uri);
        final String database = connectionString.getDatabase();
        final MongoClientSettings.Builder builder =
                MongoClientSettings.builder()
                        .readPreference(ReadPreference.secondaryPreferred())
                        .clusterSettings(ClusterSettings.builder().applyConnectionString(connectionString).build())
                        .credentialList(connectionString.getCredentialList())
                        .sslSettings(SslSettings.builder().applyConnectionString(connectionString).build());
        if (connectionString.getWriteConcern() != null) {
            builder.writeConcern(connectionString.getWriteConcern());
        }
        final MongoClientSettings mongoClientSettings = buildClientSettings(builder, maxPoolSize,
                maxPoolWaitQueueSize, maxPoolWaitTime);

        return new MongoClientWrapper(database, mongoClientSettings);
    }

    /**
     * Initializes the persistence with a passed in parameters. Does NOT allow to specify credentials, is useful for
     * testing purposes.
     *
     * @param host the host name of the mongoDB
     * @param port the port of the mongoDB
     * @param dbName the database of the mongoDB
     * @param maxPoolSize the max pool size of the db.
     * @param maxPoolWaitQueueSize the max queue size of the pool.
     * @param maxPoolWaitTimeSecs the max wait time in the pool.
     * @return a new {@code MongoClientWrapper} object.
     *
     * @see #newInstance(Config) for production purposes
     */
    public static MongoClientWrapper newInstance(final String host, final int port, final String dbName,
            final int maxPoolSize, final int maxPoolWaitQueueSize, final long maxPoolWaitTimeSecs) {

        final MongoClientSettings.Builder builder = MongoClientSettings.builder()
                .readPreference(ReadPreference.secondaryPreferred())
                .clusterSettings(ClusterSettings.builder()
                        .hosts(Collections.singletonList(new ServerAddress(host, port)))
                        .build());

        final MongoClientSettings mongoClientSettings = buildClientSettings(builder, maxPoolSize,
                maxPoolWaitQueueSize, Duration.of(maxPoolWaitTimeSecs, ChronoUnit.SECONDS));
        return new MongoClientWrapper(dbName, mongoClientSettings);
    }


    private static MongoClientSettings buildClientSettings(final MongoClientSettings.Builder builder,
            final int maxPoolSize,
            final int maxPoolWaitQueueSize,
            final Duration maxPoolWaitTime) {

        builder.connectionPoolSettings(
                ConnectionPoolSettings.builder().maxSize(maxPoolSize).maxWaitQueueSize(maxPoolWaitQueueSize)
                        .maxWaitTime(maxPoolWaitTime.toMillis(), TimeUnit.MILLISECONDS)
                        .build());

        return builder.build();
    }

    /**
     * @return the MongoDB client.
     */
    public MongoClient getMongoClient() {
        return mongoClient;
    }

    /**
     * @return the database.
     */
    public MongoDatabase getDatabase() {
        return mongoDatabase;
    }

    @Override
    public void close() {
        mongoClient.close();
    }
}
