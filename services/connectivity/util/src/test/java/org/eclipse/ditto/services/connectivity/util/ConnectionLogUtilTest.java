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

package org.eclipse.ditto.services.connectivity.util;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import akka.event.DiagnosticLoggingAdapter;

/**
 * Unit test for {@link org.eclipse.ditto.services.connectivity.util.ConnectionLogUtil}.
 */
public final class ConnectionLogUtilTest {

    private static final Map<String, Object> DEFAULT_MDC = new HashMap<>();
    private static final String KNOWN_KEY_1 = "key-1";
    private static final String KNOWN_KEY_2 = "key-2";
    private static final String KNOWN_VALUE_1 = "value-1";
    private static final String KNOWN_VALUE_2 = "value-2";

    private DiagnosticLoggingAdapter log;

    @BeforeClass
    public static void beforeClass() {
        DEFAULT_MDC.put(KNOWN_KEY_1, KNOWN_VALUE_1);
        DEFAULT_MDC.put(KNOWN_KEY_2, KNOWN_VALUE_2);
    }

    @Before
    public void setUp() {
        log = Mockito.mock(DiagnosticLoggingAdapter.class);
        Mockito.when(log.getMDC()).thenReturn(new HashMap<>(DEFAULT_MDC));
    }

    @Test
    public void enhanceLogWithConnectionId() {
        final String connectionId = "theConnection";
        final Map<String, Object> expectedMap = new HashMap<>(DEFAULT_MDC);

        expectedMap.put("connection-id", connectionId);

        ConnectionLogUtil.enhanceLogWithConnectionId(log, connectionId);

        Mockito.verify(log).setMDC(expectedMap);
    }

    @Test
    public void enhanceLogWithCorrelationIdAndConnectionId() {
        final String connectionId = "theConnection";
        final String correlationId = "theCorrelationId";
        final WithDittoHeaders<?> withDittoHeaders =
                RetrieveThing.of("any:Thing", DittoHeaders.newBuilder().correlationId(correlationId).build());

        final Map<String, Object> expectedMap = new HashMap<>(DEFAULT_MDC);

        expectedMap.put("connection-id", connectionId);
        expectedMap.put(LogUtil.X_CORRELATION_ID, correlationId);

        ConnectionLogUtil.enhanceLogWithCorrelationIdAndConnectionId(log, withDittoHeaders, connectionId);

        Mockito.verify(log).setMDC(expectedMap);
    }

    @Test
    public void enhanceLogWithCorrelationIdAndConnectionId1() {
        final String connectionId = "theConnection";
        final String correlationId = "theCorrelationId";

        final Map<String, Object> expectedMap = new HashMap<>(DEFAULT_MDC);

        expectedMap.put("connection-id", connectionId);
        expectedMap.put(LogUtil.X_CORRELATION_ID, correlationId);

        ConnectionLogUtil.enhanceLogWithCorrelationIdAndConnectionId(log, correlationId, connectionId);

        Mockito.verify(log).setMDC(expectedMap);
    }

}
