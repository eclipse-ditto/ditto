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
package org.eclipse.ditto.thingsearch.service.starter.config;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.ReadConcern;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.ReadPreference;
import org.eclipse.ditto.thingsearch.service.common.config.DittoSearchConfig;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DittoSearchConfig}.
 */
public final class DittoSearchConfigTest {

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DittoSearchConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testQueryPersistenceConfig() {
        final var config = ConfigFactory.load("search-test.conf");
        final var underTest = DittoSearchConfig.of(DefaultScopedConfig.dittoScoped(config));

        final var queryPersistenceConfig = underTest.getQueryPersistenceConfig();
        assertThat(queryPersistenceConfig.readConcern()).isEqualTo(ReadConcern.LINEARIZABLE);
        assertThat(queryPersistenceConfig.readPreference()).isEqualTo(ReadPreference.NEAREST);
    }

}
