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
package org.eclipse.ditto.internal.utils.test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;

/**
 * Retry mechanism based on Awaitility.
 */
public final class Retry {

    /**
     * Retry a supplier until it provides a value.
     *
     * @param throwingSupplier supplier to try to run.
     * @param <T> type of expected values.
     * @return the supplied value, if any.
     */
    public static <T> T untilSuccess(final ThrowingSupplier<T> throwingSupplier) {
        return untilSuccess(throwingSupplier, Function.identity());
    }

    /**
     * Retry a supplier until it provides a value.
     *
     * @param throwingSupplier supplier to try to run.
     * @param conditionFactoryConfigurer function to configure e. g. timeout and poll interval of the Awaitility
     * condition factory.
     * @param <T> type of expected values.
     * @return the supplied value, if any.
     */
    public static <T> T untilSuccess(final ThrowingSupplier<T> throwingSupplier,
            final Function<ConditionFactory, ConditionFactory> conditionFactoryConfigurer) {

        final AtomicReference<T> box = new AtomicReference<>();
        conditionFactoryConfigurer.apply(Awaitility.await())
                .untilAsserted(() -> box.set(throwingSupplier.get()));
        return box.get();
    }

    /**
     * Supplier that may throw any exception.
     *
     * @param <T> Type of supplied values.
     */
    @FunctionalInterface
    public interface ThrowingSupplier<T> {

        /**
         * Try to obtain a value.
         *
         * @return the value.
         * @throws Throwable if no value were obtained.
         */
        T get() throws Throwable;
    }

}
