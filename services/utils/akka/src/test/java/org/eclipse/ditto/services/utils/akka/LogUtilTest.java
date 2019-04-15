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

package org.eclipse.ditto.services.utils.akka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import akka.event.DiagnosticLoggingAdapter;

/**
 * Unit tests for {@link org.eclipse.ditto.services.utils.akka.LogUtil}.
 */
public class LogUtilTest {

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
    public void enhanceLogWithCorrelationId() {
        final String correlationId = "theCorrelationId";
        final Map<String, Object> expectedMap = new HashMap<>(DEFAULT_MDC);
        expectedMap.put(LogUtil.X_CORRELATION_ID, correlationId);

        LogUtil.enhanceLogWithCorrelationId(log, correlationId);

        Mockito.verify(log).setMDC(expectedMap);
    }


    @Test
    public void enhanceLogWithCorrelationIdRemovesEmptyId() {
        final String correlationId = "theCorrelationId";
        final Map<String, Object> inputMap = new HashMap<>(DEFAULT_MDC);
        inputMap.put(LogUtil.X_CORRELATION_ID, correlationId);
        Mockito.when(log.getMDC()).thenReturn(inputMap);

        final Map<String, Object> expectedMap = new HashMap<>(DEFAULT_MDC);

        LogUtil.enhanceLogWithCorrelationId(log, (String) null);

        Mockito.verify(log).setMDC(expectedMap);
    }

    @Test
    public void enhanceLogWithCorrelationIdAndAdditionalMdcField() {
        final String otherFieldName = "other-field";
        final String otherFieldValue = "other-value";

        final String correlationId = "theCorrelationId";
        final Map<String, Object> expectedMap = new HashMap<>(DEFAULT_MDC);
        expectedMap.put(LogUtil.X_CORRELATION_ID, correlationId);
        expectedMap.put(otherFieldName, otherFieldValue);

        LogUtil.enhanceLogWithCorrelationId(log, correlationId, LogUtil.newMdcField(otherFieldName, otherFieldValue));

        Mockito.verify(log).setMDC(expectedMap);
    }

    @Test
    public void enhanceLogWithCustomField() {
        final String otherFieldName = "other-field";
        final String otherFieldValue = "other-value";

        final Map<String, Object> expectedMap = new HashMap<>(DEFAULT_MDC);
        expectedMap.put(otherFieldName, otherFieldValue);

        LogUtil.enhanceLogWithCustomField(log, otherFieldName, otherFieldValue);

        Mockito.verify(log).setMDC(expectedMap);
    }

    @Test
    public void enhanceLogWithCustomFieldRemovesNullField() {

        final Map<String, Object> expectedMap = new HashMap<>(DEFAULT_MDC);
        expectedMap.remove(KNOWN_KEY_1);

        LogUtil.enhanceLogWithCustomField(log, KNOWN_KEY_1, null);

        Mockito.verify(log).setMDC(expectedMap);
    }

    @Test
    public void enhanceLogWithCustomFieldRemovesEmptyField() {

        final Map<String, Object> expectedMap = new HashMap<>(DEFAULT_MDC);
        expectedMap.remove(KNOWN_KEY_1);

        LogUtil.enhanceLogWithCustomField(log, KNOWN_KEY_1, "");

        Mockito.verify(log).setMDC(expectedMap);
    }

    @Test
    public void removeCustomField() {
        final Map<String, Object> expectedMap = new HashMap<>(DEFAULT_MDC);
        expectedMap.remove(KNOWN_KEY_1);

        LogUtil.removeCustomField(log, KNOWN_KEY_1);

        Mockito.verify(log).setMDC(expectedMap);
    }

    @Test
    public void newMdcField() {
        final String name = "any-thing";
        final String value = "any-value";

        final LogUtil.MdcField field = LogUtil.newMdcField(name, value);
        assertThat(field.getName()).isEqualTo(name);
        assertThat(field.getValue()).isEqualTo(value);
    }

}
