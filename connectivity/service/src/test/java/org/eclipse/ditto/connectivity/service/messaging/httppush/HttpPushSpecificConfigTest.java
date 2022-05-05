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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.config.HttpPushConfig;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit test for {@link HttpPushSpecificConfig}.
 */
public final class HttpPushSpecificConfigTest {

    private HttpPushConfig httpConfig;
    private Connection connection;

    @Before
    public void setup() {
        httpConfig = Mockito.mock(HttpPushConfig.class);
        connection = Mockito.mock(Connection.class);
    }

    @Test
    public void parseHttpSpecificConfig() {
        final String omitBodyRequest = "OPTIONS,DELETE";
        final Map<String, String> configuredSpecificConfig = new HashMap<>();
        configuredSpecificConfig.put(HttpPushSpecificConfig.IDLE_TIMEOUT, "3s");
        configuredSpecificConfig.put(HttpPushSpecificConfig.PARALLELISM, "2");
        configuredSpecificConfig.put(HttpPushSpecificConfig.OMIT_REQUEST_BODY, omitBodyRequest);

        when(httpConfig.getRequestTimeout()).thenReturn(Duration.ofSeconds(2));
        when(connection.getSpecificConfig()).thenReturn(configuredSpecificConfig);
        final var specificConfig = HttpPushSpecificConfig.fromConnection(connection, httpConfig);

        assertThat(specificConfig.idleTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(specificConfig.parallelism()).isEqualTo(2);
        assertThat(specificConfig.omitRequestBody())
                .isEqualTo(Arrays.stream(omitBodyRequest.split(",")).toList());
    }

    @Test
    public void defaultConfig() {
        final List<String> expectedOmittedRequestBody = List.of("GET", "DELETE");
        when(connection.getSpecificConfig()).thenReturn(Collections.emptyMap());
        when(httpConfig.getRequestTimeout()).thenReturn(Duration.ofSeconds(60));
        when(httpConfig.getOmitRequestBodyMethods()).thenReturn(expectedOmittedRequestBody);
        final HttpPushSpecificConfig specificConfig = HttpPushSpecificConfig.fromConnection(connection, httpConfig);

        assertThat(specificConfig.idleTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(specificConfig.parallelism()).isEqualTo(1);
        assertThat(specificConfig.omitRequestBody()).isEqualTo(expectedOmittedRequestBody);
    }

}
