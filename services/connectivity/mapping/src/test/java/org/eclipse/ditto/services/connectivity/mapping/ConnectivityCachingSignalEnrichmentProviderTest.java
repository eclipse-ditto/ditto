/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;

import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.services.base.config.DefaultSignalEnrichmentConfig;
import org.eclipse.ditto.services.base.config.SignalEnrichmentConfig;
import org.eclipse.ditto.services.models.signalenrichment.CachingSignalEnrichmentFacade;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link ConnectivityCachingSignalEnrichmentProvider}.
 */
public final class ConnectivityCachingSignalEnrichmentProviderTest {

    private static final Config DISPATCHER_CONFIG = ConfigFactory.parseString("signal-enrichment-cache-dispatcher {\n" +
            "  type = Dispatcher\n" +
            "  executor = \"fork-join-executor\"\n" +
            "  fork-join-executor {\n" +
            "    parallelism-min = 4\n" +
            "    parallelism-factor = 3.0\n" +
            "    parallelism-max = 32\n" +
            "  }\n" +
            "  throughput = 5\n" +
            "}");

    private final SignalEnrichmentConfig config = DefaultSignalEnrichmentConfig.of(ConfigFactory.empty()
            .withValue("signal-enrichment.provider",
                    ConfigValueFactory.fromAnyRef(ConnectivityCachingSignalEnrichmentProvider.class.getCanonicalName()))
            .withValue("signal-enrichment.provider-config.ask-timeout",
                    ConfigValueFactory.fromAnyRef(Duration.ofDays(1L))));

    private ActorSystem actorSystem;

    @Before
    public void createActorSystem() {
        actorSystem = ActorSystem.create(getClass().getSimpleName(), DISPATCHER_CONFIG);
    }

    @After
    public void shutdownActorSystem() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Test
    public void loadProvider() {
        new TestKit(actorSystem) {{
            final ConnectivitySignalEnrichmentProvider
                    underTest = ConnectivitySignalEnrichmentProvider.load(actorSystem, getRef(), getRef(), config);
            assertThat(underTest).isInstanceOf(ConnectivityCachingSignalEnrichmentProvider.class);
        }};
    }

    @Test
    public void loadProviderWithIncorrectConfig() {
        final SignalEnrichmentConfig badConfig =
                withValue("signal-enrichment.provider-config.ask-timeout", "This is not a duration");
        new TestKit(actorSystem) {{
            assertThatExceptionOfType(ConfigException.class)
                    .isThrownBy(() -> ConnectivitySignalEnrichmentProvider.load(actorSystem, getRef(), getRef(),
                            badConfig));
        }};
    }

    private DefaultSignalEnrichmentConfig withValue(final String key, final String value) {
        return DefaultSignalEnrichmentConfig.of(config.render().withValue(key, ConfigValueFactory.fromAnyRef(value)));
    }

    /**
     * ConnectivityCachingSignalEnrichmentProvider uses a "WeakReference" Set of created instances of
     * CachingSignalEnrichmentFacade which are notified whenever PolicyId changes were made.
     * <p>
     * This tests the construct be explicitly dereferencing created facades and ensuring that they are evicted from the
     * "WeakReference" Set.
     */
    @Test
    public void instantiateFacadesEnsureThatUnreferencedFacadesAreRemovedFromWeakSet() {
        new TestKit(actorSystem) {{
            final ConnectivityCachingSignalEnrichmentProvider underTest =
                    new ConnectivityCachingSignalEnrichmentProvider(actorSystem, getRef(), getRef(), config);

            // WHEN: creating 3 facades
            final ConnectionId connectionId = ConnectionId.dummy();
            CachingSignalEnrichmentFacade facade1 =
                    (CachingSignalEnrichmentFacade) underTest.createFacade(connectionId);
            CachingSignalEnrichmentFacade facade2 =
                    (CachingSignalEnrichmentFacade) underTest.createFacade(connectionId);
            CachingSignalEnrichmentFacade facade3 =
                    (CachingSignalEnrichmentFacade) underTest.createFacade(connectionId);

            final PolicyId policyId = PolicyId.of("test:policy");
            underTest.accept(policyId);

            // WHEN: explicitly removing the only reference to facade1 and facade2
            facade1 = null;
            facade2 = null;

            // THEN: after the next GC cycle (or some GC cycles, not really deterministic)
            Awaitility.await()
                    .atMost(org.awaitility.Duration.FIVE_SECONDS)
                    .until(() -> {
                        System.gc();
                        return underTest.getCreatedFacades().size() != 1;
                    });

            // there must be only one still referenced facade left:
            Assertions.assertThat(underTest.getCreatedFacades().size()).isEqualTo(1);
        }};
    }
}
