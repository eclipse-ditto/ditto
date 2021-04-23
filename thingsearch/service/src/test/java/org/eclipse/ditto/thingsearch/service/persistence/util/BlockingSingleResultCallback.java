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
