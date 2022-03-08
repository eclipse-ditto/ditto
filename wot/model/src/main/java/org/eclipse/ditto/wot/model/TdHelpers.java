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
package org.eclipse.ditto.wot.model;

import java.util.Optional;

import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;

/**
 * Package private helpers for commonly used functionality in the WoT (Web of Things) model.
 */
final class TdHelpers {

    private TdHelpers() {
        throw new AssertionError();
    }

    static <T> Optional<T> getValueIgnoringWrongType(final JsonObject jsonObject,
            final JsonFieldDefinition<T> fieldDefinition) {
        try {
            return jsonObject.getValue(fieldDefinition);
        } catch (final JsonParseException e) {
            return Optional.empty();
        }
    }
}
