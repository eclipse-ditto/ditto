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

import java.io.Closeable;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;

import org.eclipse.ditto.services.utils.config.MongoConfig;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.connection.SslSettings;
import com.mongodb.connection.netty.NettyStreamFactoryFactory;
import com.mongodb.event.CommandListener;
import com.mongodb.event.ConnectionPoolListener;
import com.mongodb.management.JMXConnectionPoolListener;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.typesafe.config.Config;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

/**
 * MongoDB Client Wrapper.
 */
public class MongoClientWrapper implements Closeable {
    // not final to test with Mockito

    private final MongoClient mongoClient;
    private final MongoDatabase mongoDatabase;

    @Nullable
    private final EventLoopGroup eventLoopGroup;

    /**
     * Initializes the persistence with a passed in {@code database} and {@code clientSettings}.
     *
     * @param database the host name of the mongoDB database.
     * @param mongoClientSettings the settings to use.
     * @param eventLoopGroup the event loop group if it should shutdown when closing the client, or null otherwise
     */
    private MongoClientWrapper(final String database,
            final MongoClientSettings mongoClientSettings,
            @Nullable final EventLoopGroup eventLoopGroup) {

        mongoClient = MongoClients.create(mongoClientSettings);
        mongoDatabase = mongoClient.getDatabase(database);
        this.eventLoopGroup = eventLoopGroup;
    }

    /**
     * Initializes the persistence with a passed in {@code config} containing the {@code uri}.
     *
     * @param config Config containing mongoDB settings including the URI.
     * @param customCommandListener the custom {@link CommandListener}
     * @param customConnectionPoolListener the custom {@link ConnectionPoolListener}
     * @return a new {@code MongoClientWrapper} object.
     */
    public static MongoClientWrapper newInstance(final Config config,
            @Nullable final CommandListener customCommandListener,
            @Nullable final ConnectionPoolListener customConnectionPoolListener) {
        final int maxPoolSize = MongoConfig.getPoolMaxSize(config);
        final int maxPoolWaitQueueSize = MongoConfig.getPoolMaxWaitQueueSize(config);
        final Duration maxPoolWaitTime = MongoConfig.getPoolMaxWaitTime(config);
        final boolean jmxListenerEnabled = MongoConfig.getJmxListenerEnabled(config);
        final String uri = MongoConfig.getMongoUri(config);
        final ConnectionString connectionString = new ConnectionString(uri);
        final String database = connectionString.getDatabase();

        final MongoClientSettings.Builder builder =
                MongoClientSettings.builder().applyConnectionString(connectionString);

        if (connectionString.getCredential() != null) {
            builder.credential(connectionString.getCredential());
        }

        final EventLoopGroup eventLoopGroup;
        if (MongoConfig.getSSLEnabled(config)) {
            eventLoopGroup = new NioEventLoopGroup();
            builder.streamFactoryFactory(NettyStreamFactoryFactory.builder().eventLoopGroup(eventLoopGroup).build())
                    .applyToSslSettings(MongoClientWrapper::buildSSLSettings);
        } else {
            eventLoopGroup = null;
            builder.applyToSslSettings(sslBuilder -> sslBuilder.applyConnectionString(connectionString));
        }

        if (customCommandListener != null) {
            builder.addCommandListener(customCommandListener);
        }

        if (connectionString.getWriteConcern() != null) {
            builder.writeConcern(connectionString.getWriteConcern());
        }
        final MongoClientSettings mongoClientSettings = buildClientSettings(builder, maxPoolSize,
                maxPoolWaitQueueSize, maxPoolWaitTime, jmxListenerEnabled, customConnectionPoolListener);

        return new MongoClientWrapper(database, mongoClientSettings, eventLoopGroup);
    }

    /**
     * Initializes the persistence with a passed in {@code config} containing the {@code uri}.
     *
     * @param config Config containing mongoDB settings including the URI.
     * @return a new {@code MongoClientWrapper} object.
     */
    public static MongoClientWrapper newInstance(final Config config) {
        return newInstance(config, null, null);
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
     * @see #newInstance(Config) for production purposes
     */
    public static MongoClientWrapper newInstance(final String host, final int port, final String dbName,
            final int maxPoolSize, final int maxPoolWaitQueueSize, final long maxPoolWaitTimeSecs) {

        final MongoClientSettings.Builder builder =
                MongoClientSettings.builder().applyToClusterSettings(clusterBuilder ->
                        clusterBuilder.hosts(Collections.singletonList(new ServerAddress(host, port))));

        final MongoClientSettings mongoClientSettings = buildClientSettings(builder, maxPoolSize,
                maxPoolWaitQueueSize, Duration.of(maxPoolWaitTimeSecs, ChronoUnit.SECONDS), false, null);
        return new MongoClientWrapper(dbName, mongoClientSettings, null);
    }

    private static MongoClientSettings buildClientSettings(final MongoClientSettings.Builder builder,
            final int maxPoolSize,
            final int maxPoolWaitQueueSize,
            final Duration maxPoolWaitTime,
            final boolean jmxListenerEnabled,
            @Nullable final ConnectionPoolListener customConnectionPoolListener) {

        builder.applyToConnectionPoolSettings(connectionPoolSettingsBuilder -> {
            connectionPoolSettingsBuilder.maxSize(maxPoolSize)
                    .maxWaitQueueSize(maxPoolWaitQueueSize)
                    .maxWaitTime(maxPoolWaitTime.toMillis(), TimeUnit.MILLISECONDS);

            if (jmxListenerEnabled) {
                connectionPoolSettingsBuilder.addConnectionPoolListener(new JMXConnectionPoolListener());
            }

            if (customConnectionPoolListener != null) {
                connectionPoolSettingsBuilder.addConnectionPoolListener(customConnectionPoolListener);
            }

        });

        return builder.build();
    }

    private static void buildSSLSettings(final SslSettings.Builder builder) {

        final SSLContext sslContext;
        try {
            sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(null, null, null);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("No such Algorithm is supported ", e);
        } catch (KeyManagementException e) {
            throw new IllegalStateException("KeyManagementException ", e);
        }

        builder.context(sslContext).enabled(true).build();
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
        if (eventLoopGroup != null) {
            eventLoopGroup.shutdownGracefully();
        }
        mongoClient.close();
    }
}
