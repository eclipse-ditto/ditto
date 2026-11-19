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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.persistence.api.PersistenceBackendBootstrap;
import org.junit.Test;

import com.typesafe.config.Config;

/**
 * Unit tests for {@link DittoService#verifyExactlyOneBackend(List, boolean, String)}.
 */
public final class DittoServiceBackendCheckTest {

    private static final PersistenceBackendBootstrap MONGODB = new Named("mongodb");
    private static final PersistenceBackendBootstrap POSTGRESQL = new Named("postgresql");

    @Test
    public void zeroBackendsWhenRequiredIsRefused() {
        assertThatThrownBy(() -> DittoService.verifyExactlyOneBackend(List.of(), true, null))
                .isInstanceOf(DittoConfigError.class)
                .hasMessage(zeroMessage("unset"));
        assertThatThrownBy(() -> DittoService.verifyExactlyOneBackend(List.of(), true, "nonsense"))
                .isInstanceOf(DittoConfigError.class)
                .hasMessage(zeroMessage("nonsense"));
        // control: the refusal comes from "required"
        assertThat(DittoService.verifyExactlyOneBackend(List.of(), false, null)).isNull();
    }

    @Test
    public void emptySelectorIsRenderedAsUnset() {
        assertThatThrownBy(() -> DittoService.verifyExactlyOneBackend(List.of(), true, ""))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining("DITTO_PERSISTENCE_BACKEND is unset.");
    }

    @Test
    public void twoDistinctBackendsAreRefused() {
        assertThatThrownBy(
                () -> DittoService.verifyExactlyOneBackend(List.of(POSTGRESQL, MONGODB), true, "postgresql"))
                .isInstanceOf(DittoConfigError.class)
                .hasMessage(severalMessage("[mongodb, postgresql]", "postgresql"));
        // control: a single backend with the same selector is accepted
        assertThat(DittoService.verifyExactlyOneBackend(List.of(MONGODB), true, "postgresql")).isEqualTo("mongodb");
    }

    @Test
    public void twoDistinctBackendsAreRefusedEvenWhenNotRequired() {
        assertThatThrownBy(() -> DittoService.verifyExactlyOneBackend(List.of(POSTGRESQL, MONGODB), false, null))
                .isInstanceOf(DittoConfigError.class)
                .hasMessage(severalMessage("[mongodb, postgresql]", "unset"));
    }

    @Test
    public void sameBackendTwiceCountsOnce() {
        assertThat(DittoService.verifyExactlyOneBackend(List.of(MONGODB, new Named("mongodb")), true, null))
                .isEqualTo("mongodb");
    }

    @Test
    public void zeroBackendsWhenNotRequiredReturnsNull() {
        assertThat(DittoService.verifyExactlyOneBackend(List.of(), false, null)).isNull();
    }

    @Test
    public void singleBackendWhenNotRequiredIsReturned() {
        assertThat(DittoService.verifyExactlyOneBackend(List.of(POSTGRESQL), false, null)).isEqualTo("postgresql");
    }

    @Test
    public void nullBootstrapsAreRejected() {
        assertThatNullPointerException().isThrownBy(() -> DittoService.verifyExactlyOneBackend(null, true, null));
    }

    private static String zeroMessage(final String selector) {
        return "No persistence backend is on the classpath. DITTO_PERSISTENCE_BACKEND is " + selector +
                ". Set it to a folder under /opt/ditto/backends/ (default: mongodb) and make sure that folder holds" +
                " the backend JARs.";
    }

    private static String severalMessage(final String names, final String selector) {
        return "Several persistence backends are on the classpath: " + names +
                ". Exactly one is allowed. DITTO_PERSISTENCE_BACKEND is " + selector +
                ". Set it to the one you want and remove the other backend's JARs from /opt/ditto/extensions/.";
    }

    private static final class Named implements PersistenceBackendBootstrap {

        private final String name;

        private Named(final String name) {
            this.name = name;
        }

        @Override
        public String backendName() {
            return name;
        }

        @Override
        public Config adjustRawConfig(final Config rawConfig) {
            return rawConfig;
        }

    }

}
