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
package org.eclipse.ditto.policies.service.persistence.testhelper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.assertj.core.api.ListAssert;
import org.awaitility.Awaitility;

/**
 * Provides assertions for testing the thing persistence.
 */
public final class Assertions {
    private Assertions() {
        throw new AssertionError();
    }


    /**
     * Asserts a list by applying the given {@code elementAssert} and provides information about the concrete element
     * and index where the assertion failed.
     * @param actual the actual list to be asserted
     * @param elementAssert a {@link java.util.function.BiFunction} providing the actual and expected list
     * @param <T> the type of the list elements
     * @return an instance of {@link ListAssert}
     */
    public static <T> ListAssert<T> assertListWithIndexInfo(final List<T> actual, final BiConsumer<T, T> elementAssert) {
        return new ListAssert<>(actual).usingComparator((actualList, expectedList) -> {
            assertThat(actualList).hasSize(expectedList.size());

            for (int i = 0; i < actualList.size(); i++) {
                final T actualElement = actualList.get(i);
                final T expectedElement = expectedList.get(i);

                try {
                    elementAssert.accept(actualElement, expectedElement);
                } catch (final AssertionError e) {
                    throw new AssertionError(String.format("List assertion failed at index %s:%nActual list: " +
                            "%s%nExpected list: " +
                            "%s%n%nActual element: %s%nExpected element: %s%nDetailed message: %s", i, actualList,
                            expectedList, actualElement, expectedElement, e.getMessage()), e.getCause());
                }
            }

            return 0;
        });
    }

    /**
     * Retries the given runnable {@code retryCount} times with a delay of {@code retryDelayMs} millis.
     * @param waitAtMostMs defines how long to wait at most until the assertions are successful
     * @param retryDelayMs defines the interval between the retries in millis
     * @param r the Runnable containing the assertions to be applied
     */
    public static void retryOnAssertionError(final Runnable r, final int waitAtMostMs, final long retryDelayMs) {
        Awaitility.await()
                .atMost(waitAtMostMs, TimeUnit.MILLISECONDS)
                .pollInterval(retryDelayMs, TimeUnit.MILLISECONDS)
                .untilAsserted(r::run);
    }
}
