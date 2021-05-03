/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.headers;

/**
 * Common interface for all classes which have {@link org.eclipse.ditto.base.model.headers.DittoHeaders} available.
 *
 * @param <T> the type of the implementing class.
 * @since 2.0.0
 */
public interface DittoHeadersSettable<T extends DittoHeadersSettable<T>> extends WithDittoHeaders {

    /**
     * Sets the {@link org.eclipse.ditto.base.model.headers.DittoHeaders} and returns a new object.
     *
     * @param dittoHeaders the DittoHeaders to set.
     * @return the newly created object with the set DittoHeaders.
     * @throws NullPointerException if the passed {@code dittoHeaders} is null.
     */
    T setDittoHeaders(DittoHeaders dittoHeaders);

}
