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
package org.eclipse.ditto.services.gateway.streaming;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.assertj.core.api.SoftAssertions;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link CloseWebSocket}.
 */
public final class CloseWebSocketTest {

    private static final String CONNECTION_CORRELATION_ID ="my-correlation-id";

    private static DittoRuntimeException reason;

    @BeforeClass
    public static void setUpClass() {
        reason = GatewayInternalErrorException.newBuilder()
                .dittoHeaders(DittoHeaders.newBuilder()
                        .correlationId(CONNECTION_CORRELATION_ID)
                        .build())
                .build();
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(CloseWebSocket.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void tryToGetInstanceWithNullReason() {
        assertThatNullPointerException()
                .isThrownBy(() -> CloseWebSocket.getInstance(null, CONNECTION_CORRELATION_ID))
                .withMessage("The reason must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToGetInstanceWithNullConnectionCorrelationId() {
        assertThatNullPointerException()
                .isThrownBy(() -> CloseWebSocket.getInstance(reason, null))
                .withMessage("The connectionCorrelationId must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToGetInstanceWithEmptyConnectionCorrelationId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CloseWebSocket.getInstance(reason, ""))
                .withMessage("The argument 'connectionCorrelationId' must not be empty!")
                .withNoCause();
    }

    @Test
    public void gettersReturnExpected() {
        final CloseWebSocket underTest = CloseWebSocket.getInstance(reason, CONNECTION_CORRELATION_ID);

        final SoftAssertions softly = new SoftAssertions();
        softly.assertThat(underTest.getReason()).isEqualTo(reason);
        softly.assertThat(underTest.getConnectionCorrelationId()).isEqualTo(CONNECTION_CORRELATION_ID);
        softly.assertAll();
    }

}