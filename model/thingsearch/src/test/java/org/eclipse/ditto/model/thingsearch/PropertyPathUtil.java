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

import static java.util.Objects.requireNonNull;

import org.eclipse.ditto.json.JsonPointer;

final class PropertyPathUtil {

    static String stripLeadingSlash(final JsonPointer pointer) {
        requireNonNull(pointer);

        return stripLeadingSlash(pointer.toString());
    }

    static String stripLeadingSlash(final String input) {
        requireNonNull(input);
        if (input.startsWith("/")) {
            return input.substring(1);
        } else {
            return input;
        }
    }
}
