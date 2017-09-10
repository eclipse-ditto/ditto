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
package org.eclipse.ditto.services.policies.persistence.testhelper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import org.assertj.core.api.ListAssert;

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
     * @param elementAssert a {@link BiFunction} providing the actual and expected list
     * @param <T> the type of the list elements
     * @return an instance of {@link ListAssert}
     */
    @SuppressWarnings("unchecked")
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
     * @param retryCount defines how many times to retry
     * @param retryDelayMs defines the interval between the retries in millis
     * @param r the Runnable containing the assertions to be applied
     */
    public static void retryOnAssertionError(final Runnable r, final int retryCount, final long retryDelayMs) {
        int retries = 0;
        AssertionError ex = null;
        while (retries < retryCount) {
            if (retries != 0) {
                try {
                    Thread.sleep(retryDelayMs);
                } catch (final InterruptedException e) {
                    throw new IllegalStateException(e);
                }
            }
            try {
                r.run();
                return;
            } catch (final AssertionError e) {
                ex = e;
            }
            retries +=1;
        }
        if (ex != null) {
            throw ex;
        }
    }
}
