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
package org.eclipse.ditto.internal.utils.persistence.postgres.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;

import org.eclipse.ditto.internal.utils.persistence.api.PersistenceBackendBootstrap;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Unit tests for {@link PostgresPersistenceBackendBootstrap}.
 */
public final class PostgresPersistenceBackendBootstrapTest {

    @Test
    public void backendNameIsPostgresql() {
        assertThat(new PostgresPersistenceBackendBootstrap().backendName()).isEqualTo("postgresql");
    }

    @Test
    public void adjustRawConfigReturnsSameInstance() {
        final Config rawConfig = ConfigFactory.parseString("ditto.foo = 1");

        assertThat(new PostgresPersistenceBackendBootstrap().adjustRawConfig(rawConfig)).isSameAs(rawConfig);
    }

    @Test
    public void adjustRawConfigRejectsNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> new PostgresPersistenceBackendBootstrap().adjustRawConfig(null))
                .withMessageContaining("rawConfig");
    }

    @Test
    public void isRegisteredViaServiceLoader() {
        final List<PersistenceBackendBootstrap> loaded =
                PersistenceBackendBootstrap.loadAll(getClass().getClassLoader());

        assertThat(loaded).extracting(PersistenceBackendBootstrap::backendName).containsExactly("postgresql");
        assertThat(loaded.get(0)).isInstanceOf(PostgresPersistenceBackendBootstrap.class);
    }

}
