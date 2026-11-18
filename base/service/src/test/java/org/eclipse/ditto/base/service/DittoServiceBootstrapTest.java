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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import org.eclipse.ditto.internal.utils.persistence.api.PersistenceBackendBootstrap;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

/**
 * Unit tests for {@link DittoService#applyBootstraps(Config, Iterable)}.
 */
public final class DittoServiceBootstrapTest {

    private static final Config RAW_CONFIG = ConfigFactory.parseString("ditto.foo = 1");

    @Test
    public void appliesAllBootstrapsInOrder() {
        final Config result = DittoService.applyBootstraps(RAW_CONFIG, List.of(new AddA(), new AddBFromA()));

        assertThat(result.getInt("a")).isEqualTo(1);
        assertThat(result.getInt("b")).isEqualTo(2);
        assertThat(result.getInt("ditto.foo")).isEqualTo(1);
        assertThat(RAW_CONFIG.hasPath("a")).isFalse();
        assertThat(RAW_CONFIG.hasPath("b")).isFalse();
    }

    @Test
    public void emptyBootstrapsReturnSameInstance() {
        assertThat(DittoService.applyBootstraps(RAW_CONFIG, List.of())).isSameAs(RAW_CONFIG);
        // control: a non-empty list is applied
        assertThat(DittoService.applyBootstraps(RAW_CONFIG, List.of(new AddA()))).isNotSameAs(RAW_CONFIG);
    }

    @Test
    public void nullResultFromBootstrapIsRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> DittoService.applyBootstraps(RAW_CONFIG, List.of(new AddA(), new ReturnsNull())))
                .withMessageContaining(ReturnsNull.NAME);
    }

    @Test
    public void loadAllReturnsEmptyListWithoutProviders() {
        final List<PersistenceBackendBootstrap> loaded =
                PersistenceBackendBootstrap.loadAll(new URLClassLoader(new URL[0], null));

        assertThat(loaded).isEmpty();
        assertThatThrownBy(() -> loaded.add(new AddA())).isInstanceOf(UnsupportedOperationException.class);
        // control: the regular class loader works as well
        assertThatCode(() -> PersistenceBackendBootstrap.loadAll(getClass().getClassLoader()))
                .doesNotThrowAnyException();
    }

    private static final class AddA implements PersistenceBackendBootstrap {

        @Override
        public String backendName() {
            return "add-a";
        }

        @Override
        public Config adjustRawConfig(final Config rawConfig) {
            return rawConfig.withValue("a", ConfigValueFactory.fromAnyRef(1));
        }

    }

    private static final class AddBFromA implements PersistenceBackendBootstrap {

        @Override
        public String backendName() {
            return "add-b";
        }

        @Override
        public Config adjustRawConfig(final Config rawConfig) {
            return rawConfig.withValue("b", ConfigValueFactory.fromAnyRef(rawConfig.getInt("a") + 1));
        }

    }

    private static final class ReturnsNull implements PersistenceBackendBootstrap {

        private static final String NAME = "returns-null";

        @Override
        public String backendName() {
            return NAME;
        }

        @Override
        public Config adjustRawConfig(final Config rawConfig) {
            return null;
        }

    }

}
