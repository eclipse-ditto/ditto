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
package org.eclipse.ditto.base.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.eclipse.ditto.base.service.config.ServiceSpecificConfig;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.api.PersistenceBackendBootstrap;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

/**
 * Pins that {@link DittoService#start()} runs the persistence backend check before anything with side effects: a
 * service that requires a backend fails with a {@link DittoConfigError} before the limits are read (the first step
 * after the check), so before any system property is set, Kamon is started or the actor system is created.
 * <p>
 * The test classpath of this module has no {@link PersistenceBackendBootstrap} provider (see
 * {@link #noBootstrapOnThisClasspath()}). The service config mock throws a sentinel from
 * {@code getLimitsConfig()}, so that a service that gets past the check stops there.
 */
public final class DittoServiceStartBackendCheckTest {

    private static final String SENTINEL = "past the backend check";

    private static final ServiceSpecificConfig CONFIG = mock(ServiceSpecificConfig.class);
    private static final AtomicInteger ROOT_PROPS_REQUESTS = new AtomicInteger();

    @Before
    public void setUp() {
        reset(CONFIG);
        when(CONFIG.getLimitsConfig()).thenThrow(new IllegalStateException(SENTINEL));
        ROOT_PROPS_REQUESTS.set(0);
    }

    @Test
    public void noBootstrapOnThisClasspath() {
        assertThat(PersistenceBackendBootstrap.loadAll(ProbeService.class.getClassLoader())).isEmpty();
    }

    @Test
    public void requiredBackendMissingFailsBeforeTheActorSystemExists() {
        final ProbeService service = new ProbeService(true);

        assertThatThrownBy(service::start)
                .isInstanceOf(DittoConfigError.class)
                .hasMessageStartingWith("No persistence backend is on the classpath.")
                .hasMessageContaining("DITTO_PERSISTENCE_BACKEND is ");
        assertThat(ROOT_PROPS_REQUESTS.get()).isZero();
        verify(CONFIG, never()).getLimitsConfig();
    }

    @Test
    public void notRequiredGetsPastTheCheck() {
        final ProbeService service = new ProbeService(false);

        assertThatThrownBy(service::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(SENTINEL);
        verify(CONFIG).getLimitsConfig();
        assertThat(ROOT_PROPS_REQUESTS.get()).isZero();
    }

    private static final class ProbeService extends DittoService<ServiceSpecificConfig> {

        private final boolean required;

        private ProbeService(final boolean required) {
            super(LoggerFactory.getLogger(DittoServiceStartBackendCheckTest.class), "backend-check-probe",
                    "probe-root");
            this.required = required;
        }

        @Override
        protected ServiceSpecificConfig getServiceSpecificConfig(final ScopedConfig dittoConfig) {
            return CONFIG;
        }

        @Override
        protected Props getMainRootActorProps(final ServiceSpecificConfig serviceSpecificConfig,
                final ActorRef pubSubMediator) {

            ROOT_PROPS_REQUESTS.incrementAndGet();
            throw new IllegalStateException("the root actor must not be requested");
        }

        @Override
        protected boolean requiresPersistenceBackend() {
            return required;
        }

    }

}
