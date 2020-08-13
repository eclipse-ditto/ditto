/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.model.messages;

import java.util.function.BiConsumer;

/**
 * Interface encapsulating a {@link java.util.function.BiConsumer} which is notified about responses with either the
 * response of type {@link R} (if it was successful) or with an {@link Throwable} if there occurred an error.
 * Does also hold the type of the expected Message response.
 *
 * @param <R> the type of the expected response. Can be equal to {@link C} if consumed signal is the actual response.
 * @param <C> the type of the expected consumed signal.
 */
public interface ResponseConsumer<R, C> {

    /**
     * Returns the type of the expected response.
     *
     * @return the type of the expected response.
     */
    Class<R> getResponseType();

    /**
     * The BiConsumer which is notified about responses with either
     * the response of type {@link R} (if it was successful) or with an {@link Throwable} if there occurred an error.
     *
     * @return the BiConsumer notified about responses.
     */
    BiConsumer<C, Throwable> getResponseConsumer();
}
