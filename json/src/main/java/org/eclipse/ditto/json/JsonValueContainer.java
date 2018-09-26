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
