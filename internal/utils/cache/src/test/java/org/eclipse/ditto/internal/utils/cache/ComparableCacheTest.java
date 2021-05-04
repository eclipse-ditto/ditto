/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.cache;


import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests {@link ComparableCache}.
 */
public class ComparableCacheTest {

    private static final long CACHE_SIZE = 3;

    private static final String KNOWN_KEY = "knownKey";
    private static final long LOWER_VALUE = 1;
    private static final long INITIAL_VALUE = 2;
    private static final long HIGHER_VALUE = 3;

    private ComparableCache<String, Long> underTest;

    @Before
    public void before() {
        underTest = new ComparableCache<>((int) CACHE_SIZE);
    }

    @Test
    public void getOnNonExistingKeyReturnsNull() {
        assertThat(underTest.get("nonExisting")).isNull();
    }

    @Test
    public void initialUpdateAndGet() {
        assertThat(underTest.updateIfNewOrGreater(KNOWN_KEY, INITIAL_VALUE)).isTrue();

        assertThat(underTest.get(KNOWN_KEY)).isEqualTo(INITIAL_VALUE);
    }

    @Test
    public void updateWithLowerValue() {
        // GIVEN
        assertThat(underTest.updateIfNewOrGreater(KNOWN_KEY, INITIAL_VALUE)).isTrue();

        // WHEN / THEN
        assertThat(underTest.updateIfNewOrGreater(KNOWN_KEY, LOWER_VALUE)).isFalse();
        assertThat(underTest.get(KNOWN_KEY)).isEqualTo(INITIAL_VALUE);
    }

    @Test
    public void updateWithSameValue() {
        // GIVEN
        assertThat(underTest.updateIfNewOrGreater(KNOWN_KEY, INITIAL_VALUE)).isTrue();

        // WHEN / THEN
        assertThat(underTest.updateIfNewOrGreater(KNOWN_KEY, INITIAL_VALUE)).isFalse();
        assertThat(underTest.get(KNOWN_KEY)).isEqualTo(INITIAL_VALUE);
    }

    @Test
    public void updateWithHigherValue() {
        // GIVEN
        assertThat(underTest.updateIfNewOrGreater(KNOWN_KEY, INITIAL_VALUE)).isTrue();

        // WHEN / THEN
        assertThat(underTest.updateIfNewOrGreater(KNOWN_KEY, HIGHER_VALUE)).isTrue();
        assertThat(underTest.get(KNOWN_KEY)).isEqualTo(HIGHER_VALUE);
    }

}
