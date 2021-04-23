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

package org.eclipse.ditto.connectivity.service.messaging.monitoring.logs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.junit.Test;

/**
 * Unit test for {@link ConnectivityHeaders}.
 */
public final class ConnectivityHeadersTest {

    @Test
    public void headersLogNotAllowedExplicitly() {
        assertThat(ConnectivityHeaders.isHeadersDebugLogEnabled(headers("OFF"))).isFalse();
    }

    @Test
    public void headersLogAllowedExplicitly() {
        assertThat(ConnectivityHeaders.isHeadersDebugLogEnabled(headers("HEADER"))).isTrue();
    }

    @Test
    public void headersLogAllowedImplicitly() {
        assertThat(ConnectivityHeaders.isHeadersDebugLogEnabled(DittoHeaders.empty())).isTrue();
        assertThat(ConnectivityHeaders.isHeadersDebugLogEnabled(headers("ALL"))).isTrue();
    }

    @Test
    public void payloadLogNotAllowedExplicitly() {
        assertThat(ConnectivityHeaders.isPayloadDebugLogEnabled(headers("OFF"))).isFalse();
    }

    @Test
    public void payloadLogAllowedExplicitly() {
        assertThat(ConnectivityHeaders.isPayloadDebugLogEnabled(headers("PAYLOAD"))).isTrue();
    }

    @Test
    public void payloadLogAllowedImplicitly() {
        assertThat(ConnectivityHeaders.isPayloadDebugLogEnabled(DittoHeaders.empty())).isTrue();
        assertThat(ConnectivityHeaders.isPayloadDebugLogEnabled(headers("ALL"))).isTrue();
    }

    private static Map<String, String> headers(final String headerValue) {
        return DittoHeaders.newBuilder()
                .putHeader("connectivity-debug-log", headerValue)
                .build();
    }

}
