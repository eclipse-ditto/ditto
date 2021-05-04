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
package org.eclipse.ditto.thingsearch.service.persistence.util;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * BlockingBiConsumer.
 *
 * @param <T> the type of the result
 */
public final class BlockingBiConsumer<T> implements BiConsumer<T, Throwable>, ResultGetter<T> {

    private final CountDownLatch countDownLatch = new CountDownLatch(1);
    private T value;
    private Throwable t;

    @Override
    public void accept(final T value, final Throwable t) {
        this.value = value;
        this.t = t;
        countDownLatch.countDown();
    }

    @Override
    public T get() {
        try {
            countDownLatch.await(2, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            throw new IllegalStateException(e);
        }

        if (t != null) {
            throw new IllegalStateException(t);
        }
        return value;
    }
}
