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

import java.util.function.Function;

import javax.annotation.Nullable;

/**
 * A {@link Function} capable of converting {@link JsonField}s. Contains a {@link #getKeyConverter() KeyConverter
 * Function} and a {@link #getValueConverter()} ValueConverter Function} for splitting up the {@link JsonField}
 * conversion.
 */
public interface JsonFieldConverter extends Function<JsonField, JsonField> {

    /**
     * @return the converter Function used for converting {@link JsonKey}s or {@code null} if no JsonKey conversion
     * should be applied.
     */
    @Nullable
    Function<JsonKey, JsonKey> getKeyConverter();

    /**
     * @return the converter Function used for converting {@link JsonValue}s or {@code null} if no JsonValue conversion
     * should be applied.
     */
    @Nullable
    Function<JsonValue, JsonValue> getValueConverter();

    /**
     * Applies the {@link JsonField} to {@link JsonField} conversion by applying the {@link #getKeyConverter()} and
     * {@link #getValueConverter()} Functions if they are non-null.
     * <p>
     * Applies the conversion recursively downwards for {@link JsonObject}s and {@link JsonArray}s of {@link JsonObject}s.
     * </p>
     *
     * @param jsonField the JsonField to convert
     * @return the converted JsonField
     */
    default JsonField apply(final JsonField jsonField) {
        final Function<JsonKey, JsonKey> keyConverter = getKeyConverter();
        final Function<JsonValue, JsonValue> valueConverter = getValueConverter();

        if (keyConverter == null && valueConverter == null) {
            // no conversion of the JsonField necessary:
            return jsonField;
        } else {
            final JsonKey convertedKey;
            if (keyConverter != null) {
                convertedKey = keyConverter.apply(jsonField.getKey());
            } else {
                convertedKey = jsonField.getKey();
            }

            final JsonValue convertedValue;
            if (jsonField.getValue().isArray()){
                convertedValue = jsonField.getValue().asArray().stream()
                        .map(value -> value.isObject() ?
                                value.asObject().stream()
                                        .map(this) // recursion for JsonObjects in JsonArrays
                                        .collect(JsonCollectors.fieldsToObject())
                                : value)
                        .collect(JsonCollectors.valuesToArray());
            } else if (jsonField.getValue().isObject()) {
                convertedValue = jsonField.getValue().asObject().stream()
                        .map(this) // recursion for JsonObjects
                        .collect(JsonCollectors.fieldsToObject());
            } else if (valueConverter != null) {
                convertedValue = valueConverter.apply(jsonField.getValue());
            } else {
                convertedValue = jsonField.getValue();
            }
            return JsonField.newInstance(convertedKey, convertedValue);
        }
    }
}
