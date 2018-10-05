/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.thingsearch.persistence.util;

import java.util.function.BiConsumer;

/**
 * BlockingSingleResultCallback.
 *
 * @param <T> the type of the result
 */
public final class BlockingSingleResultCallback<T> implements BiConsumer<T, Throwable>, ResultGetter<T> {

    private final BlockingBiConsumer<T> delegate = new BlockingBiConsumer<>();

    @Override
    public void accept(final T result, final Throwable t) {
        delegate.accept(result, t);
    }

    @Override
    public T get() {
        return delegate.get();
    }

}
