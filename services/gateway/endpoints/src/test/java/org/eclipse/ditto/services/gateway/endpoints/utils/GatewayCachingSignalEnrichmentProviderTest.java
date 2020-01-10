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
package org.eclipse.ditto.services.gateway.endpoints.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.services.base.config.DefaultSignalEnrichmentConfig;
import org.eclipse.ditto.services.base.config.SignalEnrichmentConfig;
import org.eclipse.ditto.services.models.signalenrichment.CachingSignalEnrichmentFacade;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpRequest;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link GatewayCachingSignalEnrichmentProvider}.
 */
public final class GatewayCachingSignalEnrichmentProviderTest {

    private ActorSystem actorSystem;
    private SignalEnrichmentConfig signalEnrichmentConfig;

    @Before
    public void createActorSystem() {
        signalEnrichmentConfig =
                DefaultSignalEnrichmentConfig.of(ConfigFactory.load("gateway-caching-provider-test"));
        actorSystem =
                ActorSystem.create(getClass().getSimpleName(), ConfigFactory.load("gateway-caching-provider-test"));
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
            final GatewaySignalEnrichmentProvider underTest =
                    GatewaySignalEnrichmentProvider.load(actorSystem, getRef(), getRef(), signalEnrichmentConfig);
            assertThat(underTest).isInstanceOf(GatewayCachingSignalEnrichmentProvider.class);
        }};
    }

    @Test
    public void loadProviderWithIncorrectConfig() {
        new TestKit(actorSystem) {{
            final SignalEnrichmentConfig badConfig = DefaultSignalEnrichmentConfig.of(signalEnrichmentConfig.render()
                    .withValue("signal-enrichment.provider-config.ask-timeout",
                            ConfigValueFactory.fromAnyRef("This is not a duration")));
            assertThatExceptionOfType(ConfigException.class)
                    .isThrownBy(() -> GatewaySignalEnrichmentProvider.load(actorSystem, getRef(), getRef(), badConfig));
        }};
    }

    /**
     * GatewayCachingSignalEnrichmentProvider uses a "WeakReference" Set of created instances of
     * CachingSignalEnrichmentFacade which are notified whenever PolicyId changes were made.
     * <p>
     * This tests the construct be explicitly dereferencing created facades and ensuring that they are evicted from the
     * "WeakReference" Set.
     */
    @Test
    public void instantiateFacadesEnsureThatUnreferencedFacadesAreRemovedFromWeakSet() {
        new TestKit(actorSystem) {{
            final GatewayCachingSignalEnrichmentProvider underTest =
                    new GatewayCachingSignalEnrichmentProvider(actorSystem, getRef(), getRef(),
                            signalEnrichmentConfig);

            // WHEN: creating 3 facades
            final HttpRequest httpRequestMock = Mockito.mock(HttpRequest.class);
            CachingSignalEnrichmentFacade facade1 =
                    (CachingSignalEnrichmentFacade) underTest.createFacade(httpRequestMock);
            CachingSignalEnrichmentFacade facade2 =
                    (CachingSignalEnrichmentFacade) underTest.createFacade(httpRequestMock);
            CachingSignalEnrichmentFacade facade3 =
                    (CachingSignalEnrichmentFacade) underTest.createFacade(httpRequestMock);

            final PolicyId policyId = PolicyId.of("test:policy");
            underTest.accept(policyId);

            // WHEN: explicitly removing the only reference to facade1 and facade2
            facade1 = null;
            facade2 = null;

            // THEN: after the next GC cycle (or some GC cycles, not really deterministic)
            Awaitility.await()
                    .atMost(Duration.FIVE_SECONDS)
                    .until(() -> {
                        System.gc();
                        return underTest.getCreatedFacades().size() == 1;
                    });

            // there must be only one still referenced facade left:
            Assertions.assertThat(underTest.getCreatedFacades().size()).isEqualTo(1);
        }};
    }

}