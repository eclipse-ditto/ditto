/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.persistence.mongo;


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
