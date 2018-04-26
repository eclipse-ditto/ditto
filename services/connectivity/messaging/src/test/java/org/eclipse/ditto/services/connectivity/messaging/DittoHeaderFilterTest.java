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
package org.eclipse.ditto.services.connectivity.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.services.connectivity.messaging.DittoHeadersFilter.Mode.EXCLUDE;
import static org.eclipse.ditto.services.connectivity.messaging.DittoHeadersFilter.Mode.INCLUDE;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

@SuppressWarnings("ConstantConditions")
public class DittoHeaderFilterTest {

    private static final String[] headerNames = new String[]{"A", "B", "C"};
    private static final DittoHeaders headers =
            DittoHeaders.of(Arrays.stream(headerNames).collect(Collectors.toMap(n -> n, n -> n)));

    private static final DittoHeadersFilter EXCLUDE_FILTER = new DittoHeadersFilter(EXCLUDE, headerNames[0]);
    private static final DittoHeadersFilter EMPTY_EXCLUDE_FILTER = new DittoHeadersFilter(EXCLUDE);
    private static final DittoHeadersFilter INCLUDE_FILTER = new DittoHeadersFilter(INCLUDE, headerNames[0]);
    private static final DittoHeadersFilter EMPTY_INCLUDE_FILTER = new DittoHeadersFilter(INCLUDE);

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DittoHeadersFilter.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DittoHeadersFilter.class, areImmutable());
    }

    @Test(expected = NullPointerException.class)
    public void expectNullPointerExceptionForNullHeadersArray() {
        new DittoHeadersFilter(DittoHeadersFilter.Mode.EXCLUDE, (String[]) null);
    }

    @Test(expected = NullPointerException.class)
    public void expectNullPointerExceptionForNullHeadersCollection() {
        new DittoHeadersFilter(DittoHeadersFilter.Mode.EXCLUDE, (Collection<String>) null);
    }

    @Test(expected = NullPointerException.class)
    public void expectNullPointerExceptionForNullMode() {
        new DittoHeadersFilter(null, "ditto");
    }

    @Test
    public void emptyExcludeFilter() {
        final DittoHeaders filtered = doFilter(headers, EMPTY_EXCLUDE_FILTER);
        assertExpectedHeadersArePresent(filtered, headerNames);
    }

    @Test
    public void emptyIncludeFilter() {
        final DittoHeaders filtered = doFilter(headers, EMPTY_INCLUDE_FILTER);
        assertExpectedHeadersArePresent(filtered);
    }

    @Test
    public void excludeHeaders() {
        final DittoHeaders filtered = doFilter(headers, EXCLUDE_FILTER);
        assertExpectedHeadersArePresent(filtered, headerNames[1], headerNames[2]);
    }

    @Test
    public void includeHeaders() {
        final DittoHeaders filtered = doFilter(headers, INCLUDE_FILTER);
        assertExpectedHeadersArePresent(filtered, headerNames[0]);
    }

    private DittoHeaders doFilter(final DittoHeaders headers, final DittoHeadersFilter filter) {
        assertExpectedHeadersArePresent(headers, headerNames);
        return filter.apply(headers);
    }

    private void assertExpectedHeadersArePresent(final DittoHeaders headers, final String... expectedHeaders) {
        assertThat(headers).containsKeys(expectedHeaders);
        final Set<String> blacklistedHeaders = new HashSet<>(Arrays.asList(headerNames));
        blacklistedHeaders.removeAll(Arrays.asList(expectedHeaders));
        assertThat(headers).doesNotContainKeys(blacklistedHeaders.toArray(new String[blacklistedHeaders.size()]));
    }
}