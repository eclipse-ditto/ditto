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
package org.eclipse.ditto.model.thingsearch;

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
