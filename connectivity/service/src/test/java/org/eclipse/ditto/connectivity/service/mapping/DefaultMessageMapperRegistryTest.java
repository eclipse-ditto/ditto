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
package org.eclipse.ditto.connectivity.service.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.connectivity.model.ConnectivityModelFactory.newPayloadMapping;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Tests {@link DefaultMessageMapperRegistry}.
 */
public class DefaultMessageMapperRegistryTest {

    private DefaultMessageMapperRegistry underTest;
    private MessageMapper defaultMapper;
    private MessageMapper mockMapper1;
    private MessageMapper mockMapper2;
    private MessageMapper fallbackMapper;

    @Before
    public void setUp() {
        defaultMapper = Mockito.mock(MessageMapper.class, "defaultMapper");
        mockMapper1 = Mockito.mock(MessageMapper.class, "mockMapper1");
        mockMapper2 = Mockito.mock(MessageMapper.class, "mockMapper2");
        fallbackMapper = Mockito.mock(MessageMapper.class, "fallbackMapper");

        final Map<String, MessageMapper> customMappers = new HashMap<>();
        customMappers.put("mock1", mockMapper1);
        customMappers.put("mock2", mockMapper2);
        final Map<String, MessageMapper> fallbackMappers = new HashMap<>();
        fallbackMappers.put("mock1", fallbackMapper); // custom mapper with same ID takes precedence
        fallbackMappers.put("fallback", fallbackMapper);
        underTest = DefaultMessageMapperRegistry.of(defaultMapper, customMappers, fallbackMappers);
    }

    @Test
    public void testMapperRegistry() {
        assertThat(underTest.getDefaultMapper()).isSameAs(defaultMapper);
        assertThat(underTest.getMappers(newPayloadMapping("mock1")))
                .containsExactly(mockMapper1);
        assertThat(underTest.getMappers(newPayloadMapping("mock2")))
                .containsExactly(mockMapper2);
        assertThat(underTest.getMappers(newPayloadMapping("mock1", "mock2")))
                .containsExactly(mockMapper1, mockMapper2);
        assertThat(underTest.getMappers(newPayloadMapping("mock1", "mock1")))
                .containsExactly(mockMapper1, mockMapper1);
        assertThat(underTest.getMappers(newPayloadMapping("mock1", "mock2", "mock1")))
                .containsExactly(mockMapper1, mockMapper2, mockMapper1);
        assertThat(underTest.getMappers(newPayloadMapping("mock2", "mock1")))
                .containsExactly(mockMapper2, mockMapper1);
        assertThat(underTest.getMappers(newPayloadMapping("mock1", "fallback")))
                .containsExactly(mockMapper1, fallbackMapper);
    }

    @Test
    public void testNonExistentMapperResolvesToDefaultMapper() {
        assertThat(underTest.getMappers(newPayloadMapping("eclipse"))).containsExactly(defaultMapper);
    }
}
