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
package org.eclipse.ditto.thingsearch.service.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.base.service.config.limits.DefaultLimitsConfig;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.thingsearch.persistence.api.SearchPersistenceProvider;
import org.eclipse.ditto.thingsearch.service.persistence.read.query.MongoQueryBuilderFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Proves that the shipped default extension key {@code ditto.extensions.search-persistence-provider} resolves to
 * {@link MongoSearchPersistenceProvider}, exactly as the search service actors resolve it at runtime (mirrors
 * {@code MongoPersistenceBackendProviderTest} for the persistence-backend provider).
 */
public final class MongoSearchPersistenceProviderTest {

    private static Config config;
    private static ActorSystem system;

    @BeforeClass
    public static void setUp() {
        config = ConfigFactory.load("actors-test.conf");
        system = ActorSystem.create(MongoSearchPersistenceProviderTest.class.getSimpleName(), config);
    }

    @AfterClass
    public static void tearDown() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
            system = null;
        }
    }

    @Test
    public void defaultExtensionKeyResolvesMongoProvider() {
        final SearchPersistenceProvider provider =
                SearchPersistenceProvider.get(system, ScopedConfig.dittoExtension(config));

        assertThat(provider)
                .as("the shipped default search-persistence-provider must resolve to the Mongo implementation")
                .isInstanceOf(MongoSearchPersistenceProvider.class);
    }

    @Test
    public void resolvedProviderCachedPerActorSystem() {
        final SearchPersistenceProvider first =
                SearchPersistenceProvider.get(system, ScopedConfig.dittoExtension(config));
        final SearchPersistenceProvider second =
                SearchPersistenceProvider.get(system, ScopedConfig.dittoExtension(config));

        assertThat(second)
                .as("DittoExtensionPoint caches one provider instance per actor system")
                .isSameAs(first);
    }

    @Test
    public void queryBuilderFactoryIsMongoBacked() {
        final SearchPersistenceProvider provider =
                SearchPersistenceProvider.get(system, ScopedConfig.dittoExtension(config));
        final var limitsConfig = DefaultLimitsConfig.of(config.getConfig("ditto"));

        assertThat(provider.queryBuilderFactory(limitsConfig))
                .isInstanceOf(MongoQueryBuilderFactory.class);
    }
}
