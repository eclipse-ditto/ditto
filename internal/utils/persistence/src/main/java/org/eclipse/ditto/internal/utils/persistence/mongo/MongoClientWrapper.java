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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.net.ssl.SSLContext;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.MongoDbConfig;
import org.reactivestreams.Publisher;

import com.mongodb.ClientSessionOptions;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;
import com.mongodb.connection.ClusterDescription;
import com.mongodb.connection.ServerDescription;
import com.mongodb.connection.netty.NettyStreamFactoryFactory;
import com.mongodb.event.CommandListener;
import com.mongodb.event.ConnectionPoolListener;
import com.mongodb.management.JMXConnectionPoolListener;
import com.mongodb.reactivestreams.client.ChangeStreamPublisher;
import com.mongodb.reactivestreams.client.ClientSession;
import com.mongodb.reactivestreams.client.ListDatabasesPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

/**
 * Default implementation of DittoMongoClient.
 */
@NotThreadSafe
public final class MongoClientWrapper implements DittoMongoClient {

    private final MongoClient mongoClient;
    private final MongoClientSettings clientSettings;
    private final MongoDatabase defaultDatabase;
    private final DittoMongoClientSettings dittoMongoClientSettings;
    @Nullable private final EventLoopGroup eventLoopGroup;

    private MongoClientWrapper(final MongoClientSettings clientSettings,
            final String defaultDatabaseName,
            final DittoMongoClientSettings theDittoMongoClientSettings,
            @Nullable final EventLoopGroup theEventLoopGroup) {

        this.clientSettings = clientSettings;
        mongoClient = MongoClients.create(clientSettings);
        defaultDatabase = mongoClient.getDatabase(defaultDatabaseName);
        dittoMongoClientSettings = theDittoMongoClientSettings;
        eventLoopGroup = theEventLoopGroup;
    }

    /**
     * Initializes the persistence with a passed in {@code config} containing the MongoDB URI.
     *
     * @param mongoDbConfig provides MongoDB settings including the URI.
     * @return a new {@code MongoClientWrapper} object.
     * @throws NullPointerException if {@code mongoDbConfig} is {@code null}.
     */
    public static MongoClientWrapper newInstance(final MongoDbConfig mongoDbConfig) {
        return (MongoClientWrapper) getBuilder(mongoDbConfig).build();
    }

    /**
     * Returns a new builder for creating an instance of {@code MongoClientWrapper} from scratch.
     *
     * @return the new builder instance.
     */
    public static DittoMongoClientBuilder.ConnectionCoordinatesStep getBuilder() {
        return MongoClientWrapperBuilder.newInstance();
    }

    /**
     * Returns a new builder for creating an instance of {@code MongoClientWrapper} from scratch.
     *
     * @param mongoDbConfig provides the initial MongoDB settings of the returned builder.
     * @return the new builder instance.
     * @throws NullPointerException if {@code mongoConfig} is {@code null}.
     */
    public static DittoMongoClientBuilder.GeneralPropertiesStep getBuilder(final MongoDbConfig mongoDbConfig) {
        return MongoClientWrapperBuilder.newInstance(mongoDbConfig);
    }

    @Override
    public MongoDatabase getDefaultDatabase() {
        return defaultDatabase;
    }

    @Override
    public MongoCollection<Document> getCollection(final CharSequence name) {
        return defaultDatabase.getCollection(checkNotNull(name, "collection name").toString());
    }

    @Override
    public DittoMongoClientSettings getDittoSettings() {
        return dittoMongoClientSettings;
    }

    @Override
    public MongoDatabase getDatabase(final String name) {
        return mongoClient.getDatabase(name);
    }

    @Override
    public Publisher<String> listDatabaseNames() {
        return mongoClient.listDatabaseNames();
    }

    @Override
    public Publisher<String> listDatabaseNames(final ClientSession clientSession) {
        return mongoClient.listDatabaseNames(clientSession);
    }

    @Override
    public ListDatabasesPublisher<Document> listDatabases() {
        return mongoClient.listDatabases();
    }

    @Override
    public <TResult> ListDatabasesPublisher<TResult> listDatabases(final Class<TResult> clazz) {
        return mongoClient.listDatabases(clazz);
    }

    @Override
    public ListDatabasesPublisher<Document> listDatabases(final ClientSession clientSession) {
        return mongoClient.listDatabases(clientSession);
    }

    @Override
    public <TResult> ListDatabasesPublisher<TResult> listDatabases(final ClientSession clientSession,
            final Class<TResult> clazz) {

        return mongoClient.listDatabases(clientSession, clazz);
    }

    @Override
    public ChangeStreamPublisher<Document> watch() {
        return mongoClient.watch();
    }

    @Override
    public <TResult> ChangeStreamPublisher<TResult> watch(final Class<TResult> tResultClass) {
        return mongoClient.watch(tResultClass);
    }

    @Override
    public ChangeStreamPublisher<Document> watch(final List<? extends Bson> pipeline) {
        return mongoClient.watch(pipeline);
    }

    @Override
    public <TResult> ChangeStreamPublisher<TResult> watch(final List<? extends Bson> pipeline,
            final Class<TResult> tResultClass) {

        return mongoClient.watch(pipeline, tResultClass);
    }

    @Override
    public ChangeStreamPublisher<Document> watch(final ClientSession clientSession) {
        return mongoClient.watch(clientSession);
    }

    @Override
    public <TResult> ChangeStreamPublisher<TResult> watch(final ClientSession clientSession,
            final Class<TResult> tResultClass) {

        return mongoClient.watch(clientSession, tResultClass);
    }

    @Override
    public ChangeStreamPublisher<Document> watch(final ClientSession clientSession,
            final List<? extends Bson> pipeline) {

        return mongoClient.watch(clientSession, pipeline);
    }

    @Override
    public <TResult> ChangeStreamPublisher<TResult> watch(final ClientSession clientSession,
            final List<? extends Bson> pipeline, final Class<TResult> tResultClass) {

        return mongoClient.watch(clientSession, pipeline, tResultClass);
    }

    @Override
    public Publisher<ClientSession> startSession() {
        return mongoClient.startSession();
    }

    @Override
    public Publisher<ClientSession> startSession(final ClientSessionOptions options) {
        return mongoClient.startSession(options);
    }

    @Override
    public MongoClientSettings getClientSettings() {
        return clientSettings;
    }

    @Override
    public int getMaxWireVersion() {
        return mongoClient.getClusterDescription()
                .getServerDescriptions()
                .stream()
                .mapToInt(ServerDescription::getMaxWireVersion)
                .max()
                .orElse(0);
    }

    @Override
    public ClusterDescription getClusterDescription() {
        return mongoClient.getClusterDescription();
    }

    @Override
    public void close() {
        if (null != eventLoopGroup) {
            eventLoopGroup.shutdownGracefully();
        }
        mongoClient.close();
    }

    @NotThreadSafe
    static final class MongoClientWrapperBuilder implements DittoMongoClientBuilder,
            DittoMongoClientBuilder.ConnectionCoordinatesStep,
            DittoMongoClientBuilder.DatabaseNameStep,
            DittoMongoClientBuilder.GeneralPropertiesStep {

        private final MongoClientSettings.Builder mongoClientSettingsBuilder;
        private final DittoMongoClientSettings.Builder dittoMongoClientSettingsBuilder;

        @Nullable private ConnectionString connectionString;
        private String defaultDatabaseName;
        private boolean sslEnabled;
        @Nullable private EventLoopGroup eventLoopGroup;

        private MongoClientWrapperBuilder() {
            mongoClientSettingsBuilder = MongoClientSettings.builder();
            mongoClientSettingsBuilder.readPreference(ReadPreference.primaryPreferred());
            dittoMongoClientSettingsBuilder = DittoMongoClientSettings.getBuilder();
            connectionString = null;
            defaultDatabaseName = null;
            sslEnabled = false;
            eventLoopGroup = null;
        }

        /**
         * Returns a new instance of {@code MongoClientWrapperBuilder}
         *
         * @return the new builder.
         */
        static ConnectionCoordinatesStep newInstance() {
            return new MongoClientWrapperBuilder();
        }

        /**
         * Returns a new instance of {@code MongoClientWrapperBuilder} at the step for adding general properties or
         * building the client instance.
         *
         * @param mongoDbConfig the config which provides settings for MongoDB.
         * @return the new builder.
         * @throws NullPointerException if {@code mongoConfig} is {@code null}.
         */
        static GeneralPropertiesStep newInstance(final MongoDbConfig mongoDbConfig) {
            checkNotNull(mongoDbConfig, "MongoDB config");

            final MongoClientWrapperBuilder builder = new MongoClientWrapperBuilder();
            builder.maxQueryTime(mongoDbConfig.getMaxQueryTime());
            builder.connectionString(mongoDbConfig.getMongoDbUri());

            final MongoDbConfig.ConnectionPoolConfig connectionPoolConfig = mongoDbConfig.getConnectionPoolConfig();
            builder.connectionPoolMinSize(connectionPoolConfig.getMinSize());
            builder.connectionPoolMaxSize(connectionPoolConfig.getMaxSize());
            if (!connectionPoolConfig.getMaxIdleTime().isNegative()) {
                builder.connectionPoolMaxIdleTime(connectionPoolConfig.getMaxIdleTime());
            }
            builder.connectionPoolMaxWaitTime(connectionPoolConfig.getMaxWaitTime());
            builder.enableJmxListener(connectionPoolConfig.isJmxListenerEnabled());

            final MongoDbConfig.OptionsConfig optionsConfig = mongoDbConfig.getOptionsConfig();
            builder.enableSsl(optionsConfig.isSslEnabled());
            builder.setReadPreference(optionsConfig.readPreference().getMongoReadPreference());
            builder.setReadConcern(optionsConfig.readConcern().getMongoReadConcern());
            builder.setWriteConcern(optionsConfig.writeConcern());
            builder.setRetryWrites(optionsConfig.isRetryWrites());

            return builder;
        }

        @Override
        public GeneralPropertiesStep connectionString(final String string) {
            connectionString = new ConnectionString(checkNotNull(string, "connection string"));

            mongoClientSettingsBuilder.applyConnectionString(connectionString);
            defaultDatabaseName = connectionString.getDatabase();

            return this;
        }

        @Override
        public MongoClientWrapperBuilder enableSsl(final boolean enabled) {
            sslEnabled = enabled;
            return this;
        }

        @Override
        public GeneralPropertiesStep maxQueryTime(@Nullable final Duration maxQueryTime) {
            dittoMongoClientSettingsBuilder.maxQueryTime(maxQueryTime);
            return this;
        }

        @Override
        public DatabaseNameStep hostnameAndPort(final CharSequence hostname, final int portNumber) {
            checkNotNull(hostname, "hostname");
            mongoClientSettingsBuilder.applyToClusterSettings(builder -> builder
                    .hosts(Collections.singletonList(new ServerAddress(hostname.toString(), portNumber)))
                    .build());
            return this;
        }

        @Override
        public GeneralPropertiesStep defaultDatabaseName(final CharSequence databaseName) {
            defaultDatabaseName = checkNotNull(databaseName, "name of the default database").toString();
            return this;
        }

        @Override
        public GeneralPropertiesStep connectionPoolMinSize(final int minSize) {
            mongoClientSettingsBuilder.applyToConnectionPoolSettings(builder -> builder.minSize(minSize));
            return this;
        }

        @Override
        public MongoClientWrapperBuilder connectionPoolMaxSize(final int maxSize) {
            mongoClientSettingsBuilder.applyToConnectionPoolSettings(builder -> builder.maxSize(maxSize));
            return this;
        }

        @Override
        public MongoClientWrapperBuilder connectionPoolMaxIdleTime(final Duration maxConnectionIdleTime) {
            mongoClientSettingsBuilder.applyToConnectionPoolSettings(builder -> builder.maxConnectionIdleTime(maxConnectionIdleTime.toMillis(), TimeUnit.MILLISECONDS));
            return this;
        }

        @Override
        public MongoClientWrapperBuilder connectionPoolMaxWaitTime(final Duration maxPoolWaitTime) {
            checkNotNull(maxPoolWaitTime, "maxPoolWaitTime");
            mongoClientSettingsBuilder.applyToConnectionPoolSettings(
                    builder -> builder.maxWaitTime(maxPoolWaitTime.toMillis(), TimeUnit.MILLISECONDS));
            return this;
        }

        @Override
        public MongoClientWrapperBuilder addCommandListener(@Nullable final CommandListener commandListener) {
            if (null != commandListener) {
                mongoClientSettingsBuilder.addCommandListener(commandListener);
            }
            return this;
        }

        @Override
        public MongoClientWrapperBuilder enableJmxListener(final boolean enabled) {
            if (enabled) {
                mongoClientSettingsBuilder.applyToConnectionPoolSettings(
                        builder -> builder.addConnectionPoolListener(new JMXConnectionPoolListener()));
            }
            return this;
        }

        @Override
        public MongoClientWrapperBuilder addConnectionPoolListener(
                @Nullable final ConnectionPoolListener connectionPoolListener) {

            if (null != connectionPoolListener) {
                mongoClientSettingsBuilder.applyToConnectionPoolSettings(
                        builder -> builder.addConnectionPoolListener(connectionPoolListener));
            }
            return this;
        }

        @Override
        public GeneralPropertiesStep setReadPreference(final ReadPreference readPreference) {
            mongoClientSettingsBuilder.readPreference(readPreference);
            return this;
        }

        @Override
        public GeneralPropertiesStep setReadConcern(final ReadConcern readConcern) {
            mongoClientSettingsBuilder.readConcern(readConcern);
            return this;
        }

        @Override
        public GeneralPropertiesStep setWriteConcern(final WriteConcern writeConcern) {
            mongoClientSettingsBuilder.writeConcern(writeConcern);
            return this;
        }

        @Override
        public GeneralPropertiesStep setRetryWrites(final boolean retryWrites) {
            mongoClientSettingsBuilder.retryWrites(retryWrites);
            return this;
        }

        @Override
        public MongoClientWrapper build() {
            buildAndApplySslSettings();

            return new MongoClientWrapper(mongoClientSettingsBuilder.build(), defaultDatabaseName,
                    dittoMongoClientSettingsBuilder.build(), eventLoopGroup);
        }

        private void buildAndApplySslSettings() {
            if (sslEnabled) {
                eventLoopGroup = new NioEventLoopGroup();
                mongoClientSettingsBuilder
                        .streamFactoryFactory(NettyStreamFactoryFactory.builder().eventLoopGroup(eventLoopGroup).build())
                        .applyToSslSettings(builder -> builder
                                .context(tryToCreateAndInitSslContext())
                                .enabled(sslEnabled));
            } else if (null != connectionString) {
                eventLoopGroup = null;
                mongoClientSettingsBuilder
                        .applyToSslSettings(builder -> builder
                                .enabled(sslEnabled));
            }
        }

        private static SSLContext tryToCreateAndInitSslContext() {
            try {
                return createAndInitSslContext();
            } catch (final NoSuchAlgorithmException e) {
                throw new IllegalArgumentException("No such Algorithm is supported!", e);
            } catch (final KeyManagementException e) {
                throw new IllegalStateException("KeyManagementException!", e);
            }
        }

        private static SSLContext createAndInitSslContext() throws NoSuchAlgorithmException, KeyManagementException {
            final SSLContext result = SSLContext.getInstance("TLSv1.2");
            result.init(null, null, null);
            return result;
        }

    }

}
