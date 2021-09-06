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

import java.util.Map;

import org.junit.Test;

/**
 * Unit tests for {@link DefaultHttpPushFactory}.
 */
public final class DefaultHttpPushFactoryTest {

    @Test
    public void ensureParsedParallelismIsAlwaysFactorOfTwo() {
        assertThat(DefaultHttpPushFactory.parseParallelism(Map.of(HttpPushFactory.PARALLELISM_JSON_KEY, "1")))
                .isEqualTo(1);
        assertThat(DefaultHttpPushFactory.parseParallelism(Map.of(HttpPushFactory.PARALLELISM_JSON_KEY, "2")))
                .isEqualTo(2);
        assertThat(DefaultHttpPushFactory.parseParallelism(Map.of(HttpPushFactory.PARALLELISM_JSON_KEY, "3")))
                .isEqualTo(4);
        assertThat(DefaultHttpPushFactory.parseParallelism(Map.of(HttpPushFactory.PARALLELISM_JSON_KEY, "4")))
                .isEqualTo(4);
        assertThat(DefaultHttpPushFactory.parseParallelism(Map.of(HttpPushFactory.PARALLELISM_JSON_KEY, "5")))
                .isEqualTo(8);
        assertThat(DefaultHttpPushFactory.parseParallelism(Map.of(HttpPushFactory.PARALLELISM_JSON_KEY, "8")))
                .isEqualTo(8);
        assertThat(DefaultHttpPushFactory.parseParallelism(Map.of(HttpPushFactory.PARALLELISM_JSON_KEY, "9")))
                .isEqualTo(16);
        assertThat(DefaultHttpPushFactory.parseParallelism(Map.of(HttpPushFactory.PARALLELISM_JSON_KEY, "10")))
                .isEqualTo(16);
        assertThat(DefaultHttpPushFactory.parseParallelism(Map.of(HttpPushFactory.PARALLELISM_JSON_KEY, "16")))
                .isEqualTo(16);
    }

}