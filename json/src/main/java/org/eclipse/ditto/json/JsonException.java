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

import java.net.URI;
import java.util.Optional;

/**
 * The methods of this interface are common for all JSON related exceptions.
 */
public interface JsonException {

    /**
     * Returns the error code to uniquely identify this exception.
     *
     * @return the error code.
     */
    String getErrorCode();

    /**
     * Returns the description which should be reported to the user.
     *
     * @return the description.
     */
    default Optional<String> getDescription() {
        return Optional.empty();
    }

    /**
     * Returns a link with which the user can find further information regarding this exception.
     *
     * @return a link to provide the user with further information about this exception.
     */
    default Optional<URI> getHref() {
        return Optional.empty();
    }

}
