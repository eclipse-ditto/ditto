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
