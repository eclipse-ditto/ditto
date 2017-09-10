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
package org.eclipse.services.thingsearch.persistence;

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.services.utils.config.MongoConfig;
import org.eclipse.services.thingsearch.common.util.ConfigKeys;

import com.mongodb.ConnectionString;
import com.mongodb.MongoCredential;
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
public final class MongoClientWrapper {

    private final MongoClient mongoClient;
    private final MongoDatabase mongoDatabase;

    /**
     * Initializes the persistence with a passed in {@code host} and {@code port}.
     *
     * @param host the host name of the mongoDB
     * @param port the port of the mongoDB
     * @param dbName the database of the mongoDB
     * @param config Config containing further mongoDB settings
     */
    public MongoClientWrapper(final String host, final int port, final String dbName, final Config config) {
        this(host, port, null, null, dbName, config);
    }

    /**
     * Initializes the persistence with a passed in {@code host}, {@code port}, {@code username}, {@code password} and
     * {@code database}.
     *
     * @param host the host name of the mongoDB
     * @param port the port of the mongoDB
     * @param username the username of the mongoDB (may be null)
     * @param password the password of the mongoDB (may be null if {@code username} is null, too)
     * @param database the database of the mongoDB (may be null)
     * @param config Config containing further mongoDB settings
     */
    public MongoClientWrapper(final String host, final int port, final String username, final String password,
            final String database, final Config config) {
        final MongoClientSettings.Builder builder = MongoClientSettings.builder()
                .readPreference(ReadPreference.secondaryPreferred())
                .clusterSettings(ClusterSettings.builder()
                        .hosts(Collections.singletonList(new ServerAddress(host, port)))
                        .build());

        if (username != null) {
            builder.credentialList(Collections.singletonList(MongoCredential.createCredential(username, database,
                    requireNonNull(password, "password can not " + "be null if a username is given").toCharArray())));
        }

        final MongoClientSettings mongoClientSettings = buildClientSettings(builder, config);
        mongoClient = MongoClients.create(mongoClientSettings);
        mongoDatabase = mongoClient.getDatabase(database);
    }

    /**
     * Initializes the persistence with a passed in {@code config} containing the {@code uri}.
     *
     * @param config Config containing mongoDB settings including the URI
     */
    public MongoClientWrapper(final Config config) {
        final String uri = MongoConfig.getMongoUri(config);
        final ConnectionString connectionString = new ConnectionString(uri);
        final String database = connectionString.getDatabase();
        final MongoClientSettings.Builder builder =
                MongoClientSettings.builder().readPreference(ReadPreference.secondaryPreferred())
                        .clusterSettings(ClusterSettings.builder().applyConnectionString(connectionString).build())
                        .credentialList(connectionString.getCredentialList())
                        .sslSettings(SslSettings.builder().applyConnectionString(connectionString).build());
        if (connectionString.getWriteConcern() != null) {
            builder.writeConcern(connectionString.getWriteConcern());
        }
        final MongoClientSettings mongoClientSettings = buildClientSettings(builder, config);

        mongoClient = MongoClients.create(mongoClientSettings);
        mongoDatabase = mongoClient.getDatabase(database);
    }

    private MongoClientSettings buildClientSettings(final MongoClientSettings.Builder builder, final Config config) {
        final int maxSize = config.getInt(ConfigKeys.MONGO_CONNECTION_POOL_MAX_SIZE);
        final int maxWaitQueueSize = config.getInt(ConfigKeys.MONGO_CONNECTION_POOL_MAX_WAIT_QUEUE_SIZE);
        final long maxWaitTimeSecs = config.getDuration(ConfigKeys.MONGO_CONNECTION_POOL_MAX_WAIT_TIME).getSeconds();

        builder.connectionPoolSettings(
                ConnectionPoolSettings.builder().maxSize(maxSize).maxWaitQueueSize(maxWaitQueueSize)
                        .maxWaitTime(maxWaitTimeSecs, TimeUnit.SECONDS).build());

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
}
