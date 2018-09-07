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

import java.net.URI;
import java.net.URISyntaxException;
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
import org.eclipse.ditto.model.base.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTagMatcher;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTagMatchers;

/**
 * Checks if a specified CharSequence is a valid representation of a {@link HeaderDefinition}'s Java type. If a checked
 * value is invalid a {@link DittoHeaderInvalidException} is thrown. This class recognises a defined set of types
 * and provides validation for these. For all other types, the validation succeeds.
 */
@Immutable
public final class HeaderValueValidator implements BiConsumer<HeaderDefinition, CharSequence> {

    private static final String RFC_7232_SECTION_2_3 = "https://tools.ietf.org/html/rfc7232#section-2.3";
    private static final HeaderValueValidator INSTANCE = new HeaderValueValidator();

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
        } else if (isEntityTag(definitionJavaType)) {
            validateEntityTag(definition.getKey(), charSequence);
        } else if (isEntityTagMatchers(definitionJavaType)) {
            validateEntityTagMatchers(definition.getKey(), charSequence);
        }
    }

    private static boolean isInt(final Class<?> type) {
        return int.class.equals(type) || Integer.class.equals(type);
    }

    @SuppressWarnings({"ResultOfMethodCallIgnored", "squid:S2201"})
    private static void validateIntegerValue(final String key, @Nullable final CharSequence value) {
        final String headerValue = String.valueOf(value);
        try {
            Integer.parseInt(headerValue);
        } catch (final NumberFormatException e) {
            throw DittoHeaderInvalidException.newInvalidTypeBuilder(key, headerValue, "int").build();
        }
    }

    private static boolean isLong(final Class<?> type) {
        return long.class.equals(type) || Long.class.equals(type);
    }

    @SuppressWarnings({"ResultOfMethodCallIgnored", "squid:S2201"})
    private static void validateLongValue(final String key, @Nullable final CharSequence value) {
        final String headerValue = String.valueOf(value);
        try {
            Long.parseLong(headerValue);
        } catch (final NumberFormatException e) {
            throw DittoHeaderInvalidException.newInvalidTypeBuilder(key, headerValue, "long").build();
        }
    }

    private static boolean isBoolean(final Class<?> type) {
        return boolean.class.equals(type) || Boolean.class.equals(type);
    }

    private static void validateBooleanValue(final String key, @Nullable final CharSequence value) {
        final String headerValue = String.valueOf(value);
        if (!"true".equals(headerValue) && !"false".equals(headerValue)) {
            throw DittoHeaderInvalidException.newInvalidTypeBuilder(key, headerValue, "boolean").build();
        }
    }

    private static boolean isJsonArray(final Class<?> type) {
        return JsonArray.class.equals(type);
    }

    /*
     * Checks if {@code value} is a JSON array and if all of its values are strings.
     */
    private static void validateJsonArrayValue(final String key, @Nullable final CharSequence value) {
        final String headerValue = String.valueOf(value);
        try {
            final JsonArray jsonArray = JsonFactory.newArray(headerValue);
            final List<JsonValue> nonStringArrayValues = jsonArray.stream()
                    .filter(jsonValue -> !jsonValue.isString())
                    .collect(Collectors.toList());
            if (!nonStringArrayValues.isEmpty()) {
                final String msgTemplate = "JSON array for ''{0}'' contained non-String values.";
                throw DittoHeaderInvalidException.newCustomMessageBuilder(MessageFormat.format(msgTemplate, key))
                        .build();
            }
        } catch (final JsonParseException e) {
            throw DittoHeaderInvalidException.newInvalidTypeBuilder(key, headerValue, "JSON array").build();
        }
    }

    private static boolean isEntityTagMatchers(final Class<?> type) {
        return EntityTagMatchers.class.equals(type);
    }

    private void validateEntityTagMatchers(final String key, @Nullable final CharSequence charSequence) {
        final String headerValue = String.valueOf(charSequence);
        final String[] entityTagMatchers = headerValue.split("\\s*,\\s*");

        for (String entityTagMatcher : entityTagMatchers) {
            validateEntityTagMatcher(key, entityTagMatcher);
        }
    }

    private void validateEntityTagMatcher(final String key, @Nullable final CharSequence charSequence) {
        final String headerValue = String.valueOf(charSequence);
        if (!EntityTagMatcher.isValid(headerValue)) {

            final DittoRuntimeExceptionBuilder<DittoHeaderInvalidException> exceptionBuilder =
                    DittoHeaderInvalidException.newInvalidTypeBuilder(key, headerValue, "entity-tag");

            try {
                exceptionBuilder.href(new URI(RFC_7232_SECTION_2_3));
            } catch (final URISyntaxException e) {
                // Do nothing. If this happens, there is no href appended to the exception builder.
            }

            throw exceptionBuilder.build();
        }
    }

    private static boolean isEntityTag(final Class<?> type) {
        return EntityTag.class.equals(type);
    }

    private void validateEntityTag(final String key, @Nullable final CharSequence charSequence) {
        final String headerValue = String.valueOf(charSequence);
        if (!EntityTag.isValid(headerValue)) {

            final DittoRuntimeExceptionBuilder<DittoHeaderInvalidException> exceptionBuilder =
                    DittoHeaderInvalidException.newInvalidTypeBuilder(key, headerValue, "entity-tag");

            try {
                exceptionBuilder.href(new URI(RFC_7232_SECTION_2_3));
            } catch (final URISyntaxException e) {
                // Do nothing. If this happens, there is no href appended to the exception builder.
            }

            throw exceptionBuilder.build();
        }
    }
}
