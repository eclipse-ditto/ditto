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
package org.eclipse.ditto.model.base.headers;

/**
 * Common interface for all classes which have {@link DittoHeaders} available.
 *
 * @param <T> the type of the implementing class.
 */
public interface WithDittoHeaders<T extends WithDittoHeaders> {

    /**
     * Returns the {@link DittoHeaders} which are associated with this object.
     *
     * @return the DittoHeaders of this object.
     */
    DittoHeaders getDittoHeaders();

    /**
     * Sets the {@link DittoHeaders} and returns a new object.
     *
     * @param dittoHeaders the DittoHeaders to set.
     * @return the newly created object with the set DittoHeaders.
     * @throws NullPointerException if the passed {@code dittoHeaders} is null.
     */
    T setDittoHeaders(DittoHeaders dittoHeaders);

}
