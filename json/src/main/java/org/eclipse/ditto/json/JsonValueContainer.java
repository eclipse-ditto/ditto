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
package org.eclipse.ditto.json;

import java.util.stream.Stream;

/**
 * Abstract representation of a data structure which somehow holds JSON values.
 */
public interface JsonValueContainer<T> extends Iterable<T> {

    /**
     * Indicates whether this container is empty.
     *
     * @return {@code true} if this container does not contain any values, {@code false} else.
     */
    boolean isEmpty();

    /**
     * Returns the size of this container, i. e. the number of contained values.
     *
     * @return the number of values this container contains.
     */
    int getSize();

    /**
     * Returns a sequential {@code Stream} with the values of this container as its source.
     *
     * @return a sequential stream of the values of this container.
     */
    Stream<T> stream();

}
