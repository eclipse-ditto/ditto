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
package org.eclipse.ditto.services.utils.persistence.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import com.mongodb.ConnectionString;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.async.client.MongoClientSettings;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

/**
 * Tests {@link MongoClientWrapper}.
 */
public final class MongoClientWrapperTest {

    private static final int KNOWN_MAX_POOL_SIZE = 100;
    private static final int KNOWN_MAX_POOL_WAIT_QUEUE_SIZE = 5000;
    private static final long KNOWN_MAX_POOL_WAIT_SECS = 10L;
    private static final String KNOWN_DB_NAME = "someGeneratedName";
    private static final String KNOWN_USER = "theUser";
    private static final String KNOWN_PASSWORD = "thePassword";
    private static final String KNOWN_HOST = "xy.example.org";
    private static final int KNOWN_PORT = 27777;
    private static final String KNOWN_SERVER_ADDRESS = KNOWN_HOST + ":" + KNOWN_PORT;
    private static final Config CONFIG = ConfigFactory.load("test");
    private static final String MONGO_URI_CONFIG_KEY = "ditto.services-utils-config.mongodb.uri";
    private static final String MONGO_SSL_CONFIG_KEY = "ditto.services-utils-config.mongodb.options.ssl";

    private static String createUri(final boolean sslEnabled) {
        final ConnectionString connectionString = new ConnectionString(
                "mongodb://" + KNOWN_USER + ":" + KNOWN_PASSWORD + "@" + KNOWN_SERVER_ADDRESS + "/" + KNOWN_DB_NAME +
                        "?ssl=" + sslEnabled);
        return connectionString.getConnectionString();
    }

    @Test
    public void createByUriWithExtraSettings() {
        // prepare
        final boolean sslEnabled = false;
        final Duration maxIdleTime = Duration.ofMinutes(10);
        final Duration maxLifeTime = Duration.ofMinutes(25);
        final String uri = createUri(sslEnabled) + "&maxIdleTimeMS=" + maxIdleTime.toMillis()
                + "&maxLifeTimeMS=" + maxLifeTime.toMillis();

        final Config config = CONFIG.withValue(MONGO_URI_CONFIG_KEY, ConfigValueFactory.fromAnyRef(uri));

        // test
        final MongoClientWrapper underTest = MongoClientWrapper.newInstance(config);

        // verify
        assertThat(underTest.getSettings().getConnectionPoolSettings().
                 getMaxConnectionIdleTime(TimeUnit.MILLISECONDS)).isEqualTo(maxIdleTime.toMillis());
        assertThat(underTest.getSettings().getConnectionPoolSettings().
                getMaxConnectionLifeTime(TimeUnit.MILLISECONDS)).isEqualTo(maxLifeTime.toMillis());
    }

    @Test
    public void createByUriWithSslDisabled() {
        // prepare
        final boolean sslEnabled = false;
        final String uri = createUri(sslEnabled);

        final Config config = CONFIG.withValue(MONGO_URI_CONFIG_KEY, ConfigValueFactory.fromAnyRef(uri));

        // test
        final MongoClientWrapper underTest = MongoClientWrapper.newInstance(config);

        // verify
        assertWithExpected(underTest, false, true);
    }

    @Test (expected = UnsupportedOperationException.class)
    public void createByUriWithSslEnabled() {
        // prepare
        final String uriWithSslEnabled = createUri(true);

        final Config config = CONFIG.withValue(MONGO_URI_CONFIG_KEY, ConfigValueFactory.fromAnyRef(uriWithSslEnabled));

        // test
        final MongoClientWrapper underTest = MongoClientWrapper.newInstance(config);

        // verify
        assertWithExpected(underTest, true, true);
    }

    @Test
    public void createWithSslEnabled() {
        // prepare
        final String uriWithSslEnabled = createUri(true);

        final Config config = CONFIG.withValue(MONGO_URI_CONFIG_KEY, ConfigValueFactory.fromAnyRef(uriWithSslEnabled))
                                 .withValue(MONGO_SSL_CONFIG_KEY, ConfigValueFactory.fromAnyRef("true"));

        // test
        final MongoClientWrapper underTest = MongoClientWrapper.newInstance(config);

        // verify
        assertWithExpected(underTest, true, true);
    }

    @Test
    public void createByHostAndPort() {
        // test
        final DittoMongoClient underTest = MongoClientWrapper.getBuilder()
                .hostnameAndPort(KNOWN_HOST, KNOWN_PORT)
                .defaultDatabaseName(KNOWN_DB_NAME)
                .connectionPoolMaxSize(KNOWN_MAX_POOL_SIZE)
                .connectionPoolMaxWaitQueueSize(KNOWN_MAX_POOL_WAIT_QUEUE_SIZE)
                .connectionPoolMaxWaitTime(Duration.ofSeconds(KNOWN_MAX_POOL_WAIT_SECS))
                .build();

        // verify
        assertWithExpected(underTest, false, false);
    }

    @Test
    public void createByHostAndPortWithSslEnabled() {
        // test
        final DittoMongoClient underTest = MongoClientWrapper.getBuilder()
                .hostnameAndPort(KNOWN_HOST, KNOWN_PORT)
                .defaultDatabaseName(KNOWN_DB_NAME)
                .connectionPoolMaxSize(KNOWN_MAX_POOL_SIZE)
                .connectionPoolMaxWaitQueueSize(KNOWN_MAX_POOL_WAIT_QUEUE_SIZE)
                .connectionPoolMaxWaitTime(Duration.ofSeconds(KNOWN_MAX_POOL_WAIT_SECS))
                .enableSsl(true)
                .build();

        // verify
        assertWithExpected(underTest, true, false);
    }

    private static void assertWithExpected(final DittoMongoClient mongoClient, final boolean sslEnabled,
            final boolean withCredentials) {

        final MongoClientSettings mongoClientSettings = mongoClient.getSettings();
        assertThat(mongoClientSettings.getClusterSettings().getHosts())
                .isEqualTo(Collections.singletonList(new ServerAddress(KNOWN_SERVER_ADDRESS)));

        final List<MongoCredential> expectedCredentials = withCredentials ? Collections.singletonList(
                MongoCredential.createCredential(KNOWN_USER, KNOWN_DB_NAME, KNOWN_PASSWORD.toCharArray())) :
                Collections.emptyList();
        assertThat(mongoClientSettings.getCredentialList()).isEqualTo(
                expectedCredentials);
        assertThat(mongoClientSettings.getSslSettings().isEnabled()).isEqualTo(sslEnabled);

        final MongoDatabase mongoDatabase = mongoClient.getDefaultDatabase();
        assertThat(mongoDatabase).isNotNull();
        assertThat(mongoDatabase.getName()).isEqualTo(KNOWN_DB_NAME);
    }

}
