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
package org.eclipse.ditto.model.base.headers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Test;
import org.mockito.Mockito;

/**
 * Tests {@link org.eclipse.ditto.model.base.headers.DittoHeadersSizeChecker}.
 */
public final class DittoHeadersSizeCheckerTest {

    @Test
    public void handleIntegerOverflowOnSummation() {
        final int mapSize = 1024;

        final CharSequence longString = mockString(Integer.MAX_VALUE / mapSize + 1);

        final DittoHeadersSizeChecker underTest = DittoHeadersSizeChecker.of(Integer.MAX_VALUE, 0);

        final Map<CharSequence, CharSequence> mapWithManyEntries =
                IntStream.range(0, mapSize).boxed().collect(Collectors.toMap(String::valueOf, i -> longString));

        assertThat(underTest.areHeadersTooLarge(mapWithManyEntries)).isTrue();
    }

    @Test
    public void handleIntegerOverflowOnEntry() {
        final CharSequence maxString = mockString(Integer.MAX_VALUE);

        final DittoHeadersSizeChecker underTest = DittoHeadersSizeChecker.of(65536, 65536);

        final Map<CharSequence, CharSequence> singletonMap = Collections.singletonMap(maxString, maxString);

        assertThat(underTest.areHeadersTooLarge(singletonMap)).isTrue();
    }

    @Test
    public void handleIntegerUnderflow() {
        final CharSequence maxString = mockString(Integer.MAX_VALUE);

        final DittoHeadersSizeChecker underTest = DittoHeadersSizeChecker.of(Integer.MIN_VALUE, 65536);

        final Map<CharSequence, CharSequence> singletonMap = Collections.singletonMap(maxString, "");

        assertThat(underTest.areHeadersTooLarge(singletonMap)).isTrue();
    }

    private static CharSequence mockString(final int length) {
        final CharSequence mockString = Mockito.mock(CharSequence.class);
        Mockito.when(mockString.length()).thenReturn(length);
        return mockString;
    }
}
