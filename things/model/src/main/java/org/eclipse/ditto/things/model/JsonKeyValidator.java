/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonKeyInvalidException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.common.Validator;
import org.eclipse.ditto.base.model.entity.validation.NoControlCharactersNoSlashesValidator;

/**
 * Validates keys of {@link JsonObject}s or {@link JsonPointer}s.
 */
final class JsonKeyValidator {

    private JsonKeyValidator() {
        throw new AssertionError();
    }

    /**
     * Validates all keys of a given JsonObject including all nested objects.
     *
     * @param jsonObject the {@link JsonObject} that is validated
     * @return the same instance of {@link JsonObject} if validation was successful
     * @throws JsonKeyInvalidException if a property name in the passed {@code jsonObject} was not valid according to
     * pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    static JsonObject validateJsonKeys(final JsonObject jsonObject) {
        for (final JsonField jsonField : jsonObject) {
            validate(jsonField.getKey());
            final JsonValue value = jsonField.getValue();
            if (value.isObject()) {
                // recurse!
                validateJsonKeys(value.asObject());
            }
        }
        return jsonObject;
    }

    /**
     * Validates the keys of the given {@link org.eclipse.ditto.json.JsonPointer}.
     *
     * @param pointer the {@link JsonPointer} that is validated
     * @return the same {@link JsonPointer} if validation was successful
     * @throws JsonKeyInvalidException if a key in the passed {@code jsonPointer} was not valid according to
     * pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    static JsonPointer validate(final JsonPointer pointer) {
        pointer.forEach(key -> {
            final Validator validator = NoControlCharactersNoSlashesValidator.getInstance(key);
            if (!validator.isValid()) {
                throw JsonKeyInvalidException.newBuilderWithDescription(key, validator.getReason().orElse(null))
                        .build();
            }
        });
        return pointer;
    }

    private static void validate(final JsonKey key) {
        final Validator validator = NoControlCharactersNoSlashesValidator.getInstance(key);
        if (!validator.isValid()) {
            throw JsonKeyInvalidException.newBuilderWithDescription(key, validator.getReason().orElse(null))
                    .build();
        }
    }
}
