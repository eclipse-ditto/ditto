/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.httppush;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.config.HttpPushConfig;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public final class HttpPushSpecificConfigTest {

    private HttpPushConfig httpConfig;
    private Connection connection;

    @Before
    public void setup() {
        httpConfig = Mockito.mock(HttpPushConfig.class);
        when(httpConfig.getRequestTimeout()).thenReturn(Duration.ofSeconds(2));
        connection = Mockito.mock(Connection.class);
    }

    @Test
    public void parseHttpSpecificConfig() {
        final Map<String, String> configuredSpecificConfig = new HashMap<>();
        configuredSpecificConfig.put(HttpPushSpecificConfig.IDLE_TIMEOUT, "3s");

        when(connection.getSpecificConfig()).thenReturn(configuredSpecificConfig);
        final var specificConfig = HttpPushSpecificConfig.fromConnection(connection, httpConfig);

        assertThat(specificConfig.idleTimeout()).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    public void defaultConfig() {
        when(connection.getSpecificConfig()).thenReturn(Collections.emptyMap());
        final HttpPushSpecificConfig specificConfig = HttpPushSpecificConfig.fromConnection(connection, httpConfig);

        assertThat(specificConfig.idleTimeout()).isEqualTo(Duration.ofSeconds(2));
    }

}
