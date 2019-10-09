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
package org.eclipse.ditto.services.connectivity.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
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

    @Before
    public void setUp() {
        defaultMapper = Mockito.mock(MessageMapper.class);
        mockMapper1 = Mockito.mock(MessageMapper.class);
        mockMapper2 = Mockito.mock(MessageMapper.class);

        final Map<String, MessageMapper> map = new HashMap<>();
        map.put("mock1", mockMapper1);
        map.put("mock2", mockMapper2);

        underTest = DefaultMessageMapperRegistry.of(defaultMapper, map);
    }

    @Test
    public void testMapperRegistry() {
        assertThat(underTest.getDefaultMapper()).isSameAs(defaultMapper);
        assertThat(underTest.getMappers(Collections.singletonList("mock1"))).containsExactly(mockMapper1);
        assertThat(underTest.getMappers(Collections.singletonList("mock2"))).containsExactly(mockMapper2);
        assertThat(underTest.getMappers(Arrays.asList("mock1", "mock2"))).containsExactly(mockMapper1, mockMapper2);
        assertThat(underTest.getMappers(Arrays.asList("mock1", "mock1"))).containsExactly(mockMapper1, mockMapper1);
        assertThat(underTest.getMappers(Arrays.asList("mock1", "mock2", "mock1"))).containsExactly(mockMapper1,
                mockMapper2, mockMapper1);
        assertThat(underTest.getMappers(Arrays.asList("mock2", "mock1"))).containsExactly(mockMapper2, mockMapper1);
    }

    @Test
    public void testNonExistentMapperResolvesToDefaultMapper() {
        assertThat(underTest.getMappers(Collections.singletonList("eclipse"))).containsExactly(defaultMapper);
    }
}