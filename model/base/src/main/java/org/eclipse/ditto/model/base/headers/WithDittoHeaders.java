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
