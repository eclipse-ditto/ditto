/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.headers;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;

/**
 * This validator checks if a normalized CharSequence denote an enum value.
 *
 * @since 2.3.0
 */
@Immutable
final class EnumValueValidator extends AbstractHeaderValueValidator {

    private final Set<String> enumValueSet;
    private final String errorDescription;

    private EnumValueValidator(final Enum<?>[] enumValues) {
        super(String.class::equals);
        enumValueSet = groupByNormalizedName(enumValues);
        errorDescription = formatErrorDescription(enumValueSet);
    }

    /**
     * Returns an instance of {@code DittoChannelValueValidator}.
     *
     * @return the instance.
     */
    static EnumValueValidator getInstance(final Enum<?>[] enumValues) {
        return new EnumValueValidator(enumValues);
    }

    @Override
    protected void validateValue(final HeaderDefinition definition, final CharSequence value) {
        final String normalizedValue = normalize(value);
        if (!enumValueSet.contains(normalizedValue)) {
            throw DittoHeaderInvalidException.newInvalidTypeBuilder(definition, value,
                            DittoHeaderDefinition.ON_LIVE_CHANNEL_TIMEOUT.getKey())
                    .description(errorDescription)
                    .build();
        }
    }

    private static String normalize(final CharSequence charSequence) {
        return charSequence.toString().trim().toLowerCase(Locale.ENGLISH);
    }

    private static Set<String> groupByNormalizedName(final Enum<?>[] enumValues) {
        final Set<String> set = Arrays.stream(enumValues)
                .map(Enum::toString)
                .map(EnumValueValidator::normalize)
                .collect(Collectors.toSet());
        return Collections.unmodifiableSet(set);
    }

    private static String formatErrorDescription(final Collection<String> normalizedNames) {
        final String valuesString = normalizedNames.stream()
                .map(name -> "<" + name + ">")
                .collect(Collectors.joining(", "));
        return MessageFormat.format("The value must either be one of: {0}.", valuesString);
    }
}
