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
package org.eclipse.ditto.base.model.headers;

import java.util.Optional;

import javax.annotation.Nullable;

/**
 * Common interface for all classes which have {@link org.eclipse.ditto.base.model.headers.DittoHeaders} available.
 */
public interface WithDittoHeaders {

    /**
     * Returns the {@link org.eclipse.ditto.base.model.headers.DittoHeaders} which are associated with this object.
     *
     * @return the DittoHeaders of this object.
     */
    DittoHeaders getDittoHeaders();

    /**
     * Returns the optional correlation ID of the specified argument's headers.
     *
     * @param signal the signal to get the optional correlation ID from.
     * @return the optional correlation ID. The optional is empty if {@code signal} is {@code null}.
     * @since 3.0.0
     */
    static Optional<String> getCorrelationId(@Nullable final WithDittoHeaders signal) {
        final Optional<String> result;
        if (null != signal) {
            final DittoHeaders signalDittoHeaders = signal.getDittoHeaders();
            result = signalDittoHeaders.getCorrelationId();
        } else {
            result = Optional.empty();
        }

        return result;
    }
}
