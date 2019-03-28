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
package org.eclipse.ditto.model.messages;

import java.util.function.BiConsumer;

/**
 * Interface encapsulating a {@link BiConsumer} which is notified about message responses with either the
 * {@link Message} (if it was successful) or with an {@link Throwable} if there occurred an error.
 * Does also hold the type of the expected Message response.
 *
 * @param <R> the type of the expected message's payload.
 */
public interface MessageResponseConsumer<R> {

    /**
     * Returns the type of the expected Message response.
     *
     * @return the type of the expected Message response.
     */
    Class<R> getResponseType();

    /**
     * The BiConsumer which is notified about message responses with either the
     * {@link Message} (if it was successful) or with an {@link Throwable} if there occurred an error.
     *
     * @return the BiConsumer notified about message responses.
     */
    BiConsumer<Message<R>, Throwable> getResponseConsumer();
}
