/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit tests for {@link DefaultHttpPushFactory}.
 */
public final class DefaultHttpPushFactoryTest {

    @Test
    public void ensureParsedParallelismIsAlwaysFactorOfTwo() {
        final var mockConf = Mockito.mock(HttpPushSpecificConfig.class);
        when(mockConf.parallelism()).thenReturn(1);
        assertThat(DefaultHttpPushFactory.parseParallelism(mockConf)).isEqualTo(1);
        when(mockConf.parallelism()).thenReturn(2);
        assertThat(DefaultHttpPushFactory.parseParallelism(mockConf)).isEqualTo(2);
        when(mockConf.parallelism()).thenReturn(3);
        assertThat(DefaultHttpPushFactory.parseParallelism(mockConf)).isEqualTo(4);
        when(mockConf.parallelism()).thenReturn(4);
        assertThat(DefaultHttpPushFactory.parseParallelism(mockConf)).isEqualTo(4);
        when(mockConf.parallelism()).thenReturn(5);
        assertThat(DefaultHttpPushFactory.parseParallelism(mockConf)).isEqualTo(8);
        when(mockConf.parallelism()).thenReturn(8);
        assertThat(DefaultHttpPushFactory.parseParallelism(mockConf)).isEqualTo(8);
        when(mockConf.parallelism()).thenReturn(9);
        assertThat(DefaultHttpPushFactory.parseParallelism(mockConf)).isEqualTo(16);
        when(mockConf.parallelism()).thenReturn(10);
        assertThat(DefaultHttpPushFactory.parseParallelism(mockConf)).isEqualTo(16);
        when(mockConf.parallelism()).thenReturn(16);
        assertThat(DefaultHttpPushFactory.parseParallelism(mockConf)).isEqualTo(16);
    }

}