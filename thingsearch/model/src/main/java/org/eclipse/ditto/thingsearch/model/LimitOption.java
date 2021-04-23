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
package org.eclipse.ditto.thingsearch.model;

import javax.annotation.concurrent.Immutable;

/**
 * The limit option determines the pagination of the search result.
 */
@Immutable
public interface LimitOption extends Option {

    /**
     * Returns the offset of this option.
     *
     * @return the offset.
     */
    int getOffset();

    /**
     * Returns the count of this option.
     *
     * @return the count.
     */
    int getCount();

    /**
     * Returns the string representation of this limit option. The string consists of the prefix {@code "limit("}
     * which is followed by the comma-separated offset and count value and finally of the suffix {@code ")"}. An
     * example string might look like {@code "limit(0,25)"};
     *
     * @return the string representation of this limit option.
     */
    @Override
    String toString();

}
