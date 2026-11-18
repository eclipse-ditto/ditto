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

import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.api.PersistenceBackendBootstrap;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultMongoDbConfig;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Unit tests for {@link MongoPersistenceBackendBootstrap}.
 */
public final class MongoPersistenceBackendBootstrapTest {

    private static final String MONGO_URI_PATH = "pekko.contrib.persistence.mongodb.mongo.mongouri";

    private static final Config MONGO_CONFIG = ConfigFactory.parseString(String.join("\n",
            "ditto.mongodb.uri = \"mongodb://u:p@h:27017/db\"",
            "ditto.mongodb.options.ssl = true",
            "ditto.mongodb.options.extra-uri-options { maxPoolSize = 7 }",
            "ditto.foo = 1"));

    @Test
    public void injectsSameUriAsDefaultMongoDbConfig() {
        final Config result = new MongoPersistenceBackendBootstrap().adjustRawConfig(MONGO_CONFIG);

        final String expected = DefaultMongoDbConfig.of(DefaultScopedConfig.dittoScoped(MONGO_CONFIG)).getMongoDbUri();
        final String actual = result.getString(MONGO_URI_PATH);
        assertThat(actual).isEqualTo(expected)
                .startsWith("mongodb://u:p@h:27017/db?")
                .contains("ssl=true")
                .contains("maxPoolSize=7");
        assertThat(result.getInt("ditto.foo")).isEqualTo(1);
        assertThat(MONGO_CONFIG.hasPath(MONGO_URI_PATH)).isFalse();
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

}
