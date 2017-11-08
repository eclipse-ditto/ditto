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
package org.eclipse.ditto.services.thingsearch.persistence.util;

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
