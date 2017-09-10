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
package org.eclipse.ditto.model.base.headers;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;

/**
 * Checks if a specified CharSequence is a valid representation of a {@link HeaderDefinition}'s Java type. If a checked
 * value is invalid an IllegalArgumentException is thrown. The following types are recognised:
 * <ul>
 *     <li>{@code boolean}/{@code Boolean},</li>
 *     <li>{@code int}/{@code Integer},</li>
 *     <li>{@code long}/{@code Long} and</li>
 *     <li>{@link JsonArray}.</li>
 * </ul>
 * For all other types the validation always succeeds.
 */
@Immutable
public final class HeaderValueValidator implements BiConsumer<HeaderDefinition, CharSequence> {

    private static final HeaderValueValidator INSTANCE = new HeaderValueValidator();
    private static final String MSG_TEMPLATE = "Value <{0}> for key <{1}> is not a valid {2}!";

    private HeaderValueValidator() {
        super();
    }

    /**
     * Returns an instance of {@code HeaderValueValidator}.
     *
     * @return the instance.
     */
    public static HeaderValueValidator getInstance() {
        return INSTANCE;
    }

    @Override
    public void accept(@Nonnull final HeaderDefinition definition, @Nullable final CharSequence charSequence) {
        final Class<?> definitionJavaType = checkNotNull(definition, "Definition").getJavaType();
        if (isInt(definitionJavaType)) {
            validateIntegerValue(definition.getKey(), charSequence);
        } else if (isLong(definitionJavaType)) {
            validateLongValue(definition.getKey(), charSequence);
        } else if (isBoolean(definitionJavaType)) {
            validateBooleanValue(definition.getKey(), charSequence);
        } else if (isJsonArray(definitionJavaType)) {
            validateJsonArrayValue(definition.getKey(), charSequence);
        }
    }

    private static boolean isInt(final Class<?> type) {
        return int.class.equals(type) || Integer.class.equals(type);
    }

    @SuppressWarnings({"ResultOfMethodCallIgnored", "squid:S2201"})
    private static void validateIntegerValue(final String key, @Nullable final CharSequence value) {
        try {
            Integer.parseInt(String.valueOf(value));
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException(MessageFormat.format(MSG_TEMPLATE, value, key, "int"), e);
        }
    }

    private static boolean isLong(final Class<?> type) {
        return long.class.equals(type) || Long.class.equals(type);
    }

    @SuppressWarnings({"ResultOfMethodCallIgnored", "squid:S2201"})
    private static void validateLongValue(final String key, @Nullable final CharSequence value) {
        try {
            Long.parseLong(String.valueOf(value));
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException(MessageFormat.format(MSG_TEMPLATE, value, key, "long"), e);
        }
    }

    private static boolean isBoolean(final Class<?> type) {
        return boolean.class.equals(type) || Boolean.class.equals(type);
    }

    private static void validateBooleanValue(final String key, @Nullable final CharSequence value) {
        final String s = String.valueOf(value);
        if (!"true".equals(s) && !"false".equals(s)) {
            throw new IllegalArgumentException(MessageFormat.format(MSG_TEMPLATE, value, key, "boolean"));
        }
    }

    private static boolean isJsonArray(final Class<?> type) {
        return JsonArray.class.equals(type);
    }

    /*
     * Checks if {@code value} is a JSON array and if all of its values are strings.
     */
    private static void validateJsonArrayValue(final String key, @Nullable final CharSequence value) {
        try {
            final JsonArray jsonArray = JsonFactory.newArray(String.valueOf(value));
            final List<JsonValue> nonStringArrayValues = jsonArray.stream()
                    .filter(jsonValue -> !jsonValue.isString())
                    .collect(Collectors.toList());
            if (!nonStringArrayValues.isEmpty()) {
                final String msgTemplate = "JSON array for <{0}> contained non-String values: {1}!";
                throw new IllegalArgumentException(MessageFormat.format(msgTemplate, key, nonStringArrayValues));
            }
        } catch (final IllegalArgumentException | JsonParseException e) {
            throw new IllegalArgumentException(MessageFormat.format(MSG_TEMPLATE, value, key, "JSON array"), e);
        }
    }

}
