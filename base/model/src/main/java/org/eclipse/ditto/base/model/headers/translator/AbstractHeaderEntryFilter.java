/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.headers.translator;

import javax.annotation.Nullable;

/**
 * Base implementation of {@link HeaderEntryFilter} which provides common functionality for header entry filtering.
 * Sub-classes are supposed to implement the <em>abstract methods only.</em>
 */
abstract class AbstractHeaderEntryFilter implements HeaderEntryFilter {

    @Override
    @Nullable
    public final String apply(final String key, @Nullable final String value) {
        if (null == value) {
            return null;
        }
        return filterValue(key, value);
    }

    @Nullable
    protected abstract String filterValue(String key, String value);

}
