/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

/**
 * The "cursor" option allows resumption of a previous query for more results.
 */
public interface CursorOption extends Option {

    /**
     * Returns the page size.
     *
     * @return the page size.
     */
    String getCursor();

    /**
     * Returns the string representation of this option. The string consists of the prefix {@code "size("}
     * which is followed by the size value and finally of the suffix {@code ")"}. An
     * example string might look like {@code "size(25)"};
     *
     * @return the string representation of this option.
     */
    @Override
    String toString();

}
