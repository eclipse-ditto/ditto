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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.services.utils.config.MongoConfig;
import org.junit.Test;

import com.mongodb.ConnectionString;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.async.client.MongoClientSettings;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

/**
 * Tests {@link MongoClientWrapper}.
 */
public class MongoClientWrapperTest {

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

    private static String createUri(final boolean sslEnabled) {
        final ConnectionString connectionString = new ConnectionString(
                "mongodb://" + KNOWN_USER + ":" + KNOWN_PASSWORD + "@" + KNOWN_SERVER_ADDRESS + "/" + KNOWN_DB_NAME +
                        "?ssl="
                        + sslEnabled);
        return connectionString.getConnectionString();
    }

    /** */
    @Test
    public void createByUriWithSslDisabled() {
        // prepare
        final boolean sslEnabled = false;
        final String uri = createUri(sslEnabled);

        final Config config = CONFIG.withValue(MongoConfig.URI, ConfigValueFactory.fromAnyRef(uri));


        // test
        final MongoClientWrapper wrapper = MongoClientWrapper.newInstance(config);

        // verify
        assertWithExpected(wrapper, false, true);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void createByUriWithSslEnabled() {
        // prepare
        final String uriWithSslEnabled = createUri(true);

        final Config config = CONFIG.withValue(MongoConfig.URI, ConfigValueFactory.fromAnyRef(uriWithSslEnabled));

        // test
        final MongoClientWrapper wrapper = MongoClientWrapper.newInstance(config);

        // verify
        assertWithExpected(wrapper, false, true);
    }

    /** */
    @Test
    public void createByHostAndPort() {
        // test
        final MongoClientWrapper wrapper = MongoClientWrapper.newInstance(KNOWN_HOST, KNOWN_PORT, KNOWN_DB_NAME,
                KNOWN_MAX_POOL_SIZE, KNOWN_MAX_POOL_WAIT_QUEUE_SIZE, KNOWN_MAX_POOL_WAIT_SECS);

        // verify
        assertWithExpected(wrapper, false, false);
    }

    /** */
    @Test
    public void createByAll() {
        // test
        final MongoClientWrapper wrapper =
                MongoClientWrapper.newInstance(KNOWN_HOST, KNOWN_PORT, KNOWN_DB_NAME,
                        KNOWN_MAX_POOL_SIZE, KNOWN_MAX_POOL_WAIT_QUEUE_SIZE, KNOWN_MAX_POOL_WAIT_SECS);

        // verify
        assertWithExpected(wrapper, false, false);
    }

    private static void assertWithExpected(final MongoClientWrapper wrapper, final boolean sslEnabled,
            final boolean withCredentials) {
        final MongoClient mongoClient = wrapper.getMongoClient();
        assertThat(mongoClient).isNotNull();

        final MongoClientSettings mongoClientSettings = mongoClient.getSettings();
        assertThat(mongoClientSettings.getClusterSettings().getHosts())
                .isEqualTo(Collections.singletonList(new ServerAddress(KNOWN_SERVER_ADDRESS)));
        final List<MongoCredential> expectedCredentials = withCredentials ? Collections.singletonList(
                MongoCredential.createCredential(KNOWN_USER, KNOWN_DB_NAME, KNOWN_PASSWORD.toCharArray())) :
                Collections.emptyList();
        assertThat(mongoClientSettings.getCredentialList()).isEqualTo(
                expectedCredentials);
        assertThat(mongoClientSettings.getSslSettings().isEnabled()).isEqualTo(sslEnabled);
        final MongoDatabase mongoDatabase = wrapper.getDatabase();
        assertThat(mongoDatabase).isNotNull();
        assertThat(mongoDatabase.getName()).isEqualTo(KNOWN_DB_NAME);
    }
}
