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
package org.eclipse.ditto.base.model.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

/**
 * Tests {@link EnforcementTimeoutException}.
 */
public final class EnforcementTimeoutExceptionTest {

    private static final EnforcementTimeoutException UNDER_TEST =
            EnforcementTimeoutException.newBuilder().build();

    private static final JsonObject KNOWN_JSON = JsonFactory.newObject("{\n" +
            "  \"status\": 503,\n" +
            "  \"error\": \"enforcement.timeout\",\n" +
            "  \"message\": \"The enforcement of the signal timed out, please retry later.\",\n" +
            "  \"description\": \"The signal could not be enforced within the configured timeout. " +
            "Please retry the performed action in order to improve resiliency.\"\n" +
            "}");

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject jsonObject = UNDER_TEST.toJson();
        assertThat(jsonObject).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final EnforcementTimeoutException deserialized =
                EnforcementTimeoutException.fromJson(KNOWN_JSON, DittoHeaders.empty());
        assertThat(deserialized).isEqualTo(UNDER_TEST);
    }

    @Test
    public void httpStatusIsServiceUnavailable() {
        assertThat(UNDER_TEST.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    public void errorCodeIsCorrect() {
        assertThat(UNDER_TEST.getErrorCode()).isEqualTo("enforcement.timeout");
    }

    @Test
    public void setDittoHeadersReturnsSameExceptionType() {
        final DittoHeaders newHeaders = DittoHeaders.newBuilder().correlationId("test-123").build();
        final DittoRuntimeException result = UNDER_TEST.setDittoHeaders(newHeaders);
        assertThat(result).isInstanceOf(EnforcementTimeoutException.class);
        assertThat(result.getDittoHeaders()).isEqualTo(newHeaders);
    }
}
