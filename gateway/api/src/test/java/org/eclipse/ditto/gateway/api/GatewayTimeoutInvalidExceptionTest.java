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
package org.eclipse.ditto.gateway.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.eclipse.ditto.base.model.common.DittoDuration;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

/**
 * Unit test for {@link GatewayTimeoutInvalidException}.
 */
public final class GatewayTimeoutInvalidExceptionTest {

    private static final Duration MAX_TIMEOUT = Duration.ofMinutes(1);

    private static final GatewayTimeoutInvalidException UNDER_TEST =
            GatewayTimeoutInvalidException.newBuilder(DittoDuration.parseDuration("90s"), MAX_TIMEOUT).build();

    private static final JsonObject KNOWN_JSON = JsonFactory.newObject("{\n" +
            "  \"status\": 400,\n" +
            "  \"error\": \"gateway:timeout.invalid\",\n" +
            "  \"message\": \"The timeout <90s> is not inside its allowed bounds <0s - 60s>\",\n" +
            "  \"description\": \"Choose a timeout inside the bounds.\"\n" +
            "}");

    @Test
    public void toJsonReturnsExpected() {
        assertThat(UNDER_TEST.toJson()).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final GatewayTimeoutInvalidException deserialized =
                GatewayTimeoutInvalidException.fromJson(KNOWN_JSON, DittoHeaders.empty());

        assertThat(deserialized).isEqualTo(UNDER_TEST);
    }

    @Test
    public void httpStatusIsBadRequest() {
        assertThat(UNDER_TEST.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void setDittoHeadersReturnsSameExceptionType() {
        final DittoHeaders newHeaders = DittoHeaders.newBuilder().correlationId("test-123").build();

        final DittoRuntimeException result = UNDER_TEST.setDittoHeaders(newHeaders);

        assertThat(result).isInstanceOf(GatewayTimeoutInvalidException.class);
        assertThat(result.getDittoHeaders()).isEqualTo(newHeaders);
    }

    @Test
    public void messageUsesSecondsForTimeoutWithoutUnit() {
        assertThat(messageFor("90", MAX_TIMEOUT))
                .isEqualTo("The timeout <90s> is not inside its allowed bounds <0s - 60s>");
    }

    @Test
    public void messageUsesSecondsForTimeoutInSeconds() {
        assertThat(messageFor("90s", MAX_TIMEOUT))
                .isEqualTo("The timeout <90s> is not inside its allowed bounds <0s - 60s>");
    }

    @Test
    public void messageUsesMillisecondsForTimeoutInMilliseconds() {
        assertThat(messageFor("90000ms", MAX_TIMEOUT))
                .isEqualTo("The timeout <90000ms> is not inside its allowed bounds <0ms - 60000ms>");
    }

    @Test
    public void messageUsesMinutesForTimeoutInMinutes() {
        assertThat(messageFor("2m", MAX_TIMEOUT))
                .isEqualTo("The timeout <2m> is not inside its allowed bounds <0m - 1m>");
    }

    @Test
    public void messageFallsBackToMillisecondsIfMaxTimeoutIsNoMultipleOfUnit() {
        assertThat(messageFor("2m", Duration.ofSeconds(90)))
                .isEqualTo("The timeout <120000ms> is not inside its allowed bounds <0ms - 90000ms>");
        assertThat(messageFor("1h", MAX_TIMEOUT))
                .isEqualTo("The timeout <3600000ms> is not inside its allowed bounds <0ms - 60000ms>");
    }

    private static String messageFor(final String timeout, final Duration maxTimeout) {
        return GatewayTimeoutInvalidException.newBuilder(DittoDuration.parseDuration(timeout), maxTimeout)
                .build()
                .getMessage();
    }

}
