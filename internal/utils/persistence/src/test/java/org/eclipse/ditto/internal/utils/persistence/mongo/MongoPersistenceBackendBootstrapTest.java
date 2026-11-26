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
package org.eclipse.ditto.internal.utils.persistence.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ServiceLoader;

import org.eclipse.ditto.internal.utils.persistence.api.PersistenceBackendBootstrap;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Unit tests for {@link MongoPersistenceBackendBootstrap}.
 * <p>
 * The expected URIs are literals that pin what the URI code ({@code DefaultMongoDbConfig} and
 * {@code MongoDbUriSupplier}) builds from {@code ditto.mongodb}. The URI code appends no
 * auth-related option, so the credentials case pins that credentials and the source query pass through untouched.
 * The order of the appended options is {@code HashMap} iteration order; it is pinned on purpose.
 */
public final class MongoPersistenceBackendBootstrapTest {

    private static final String MONGO_URI_PATH = "pekko.contrib.persistence.mongodb.mongo.mongouri";

    private static final String PLAIN_URI = "ditto.mongodb.uri = \"mongodb://h:27017/db\"";
    private static final String CREDENTIALS_URI =
            "ditto.mongodb.uri = \"mongodb://user:secret@h:27017/db?authSource=admin\"";
    private static final String SSL = "ditto.mongodb.options.ssl = true";
    private static final String EXTRA_URI_OPTIONS =
            "ditto.mongodb.options.extra-uri-options { maxPoolSize = 7, retryWrites = false }";

    private static final Config MONGO_CONFIG = ConfigFactory.parseString(String.join("\n",
            "ditto.mongodb.uri = \"mongodb://u:p@h:27017/db\"",
            "ditto.mongodb.options.ssl = true",
            "ditto.mongodb.options.extra-uri-options { maxPoolSize = 7 }",
            "ditto.foo = 1"));

    @Test
    public void sslOptionIsAppended() {
        assertThat(injectedUri(PLAIN_URI, SSL)).isEqualTo("mongodb://h:27017/db?ssl=true");
    }

    @Test
    public void credentialsAndAuthSourcePassThrough() {
        assertThat(injectedUri(CREDENTIALS_URI))
                .isEqualTo("mongodb://user:secret@h:27017/db?authSource=admin&ssl=false");
    }

    @Test
    public void extraUriOptionsAreAppendedInStableOrder() {
        assertThat(injectedUri(PLAIN_URI, EXTRA_URI_OPTIONS))
                .isEqualTo("mongodb://h:27017/db?retryWrites=false&maxPoolSize=7&ssl=false");
    }

    @Test
    public void sslCredentialsAndExtraOptionsCombined() {
        final Config config = ConfigFactory.parseString(String.join("\n", CREDENTIALS_URI, SSL, EXTRA_URI_OPTIONS,
                "ditto.foo = 1"));

        final Config result = new MongoPersistenceBackendBootstrap().adjustRawConfig(config);

        assertThat(result.getString(MONGO_URI_PATH)).isEqualTo(
                "mongodb://user:secret@h:27017/db?authSource=admin&retryWrites=false&maxPoolSize=7&ssl=true");
        assertThat(result.getInt("ditto.foo")).isEqualTo(1);
        assertThat(config.hasPath(MONGO_URI_PATH)).isFalse();
    }

    @Test
    public void returnsSameInstanceWithoutDittoMongodb() {
        final MongoPersistenceBackendBootstrap underTest = new MongoPersistenceBackendBootstrap();
        final Config config = ConfigFactory.parseString("ditto.foo = 1\npekko.bar = 2");

        final Config result = underTest.adjustRawConfig(config);

        assertThat(result).isSameAs(config);
        assertThat(result.hasPath(MONGO_URI_PATH)).isFalse();
        // control: the same instance does inject the URI when ditto.mongodb is present
        assertThat(underTest.adjustRawConfig(MONGO_CONFIG).hasPath(MONGO_URI_PATH)).isTrue();
    }

    @Test
    public void backendNameIsMongodb() {
        assertThat(new MongoPersistenceBackendBootstrap().backendName()).isEqualTo("mongodb");
    }

    @Test
    public void serviceLoaderFindsMongoBootstrap() {
        assertThat(ServiceLoader.load(PersistenceBackendBootstrap.class).stream().map(ServiceLoader.Provider::get))
                .anyMatch(MongoPersistenceBackendBootstrap.class::isInstance);
        assertThat(PersistenceBackendBootstrap.loadAll(getClass().getClassLoader()))
                .anyMatch(MongoPersistenceBackendBootstrap.class::isInstance);
    }

    @Test
    public void rejectsNullConfig() {
        assertThatNullPointerException()
                .isThrownBy(() -> new MongoPersistenceBackendBootstrap().adjustRawConfig(null));
    }

    private static String injectedUri(final String... configLines) {
        final Config config = ConfigFactory.parseString(String.join("\n", configLines));
        return new MongoPersistenceBackendBootstrap().adjustRawConfig(config).getString(MONGO_URI_PATH);
    }

}
