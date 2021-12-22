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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;

/**
 * This validator checks if a normalized CharSequence denotes a known enum value.
 *
 * @since 2.3.0
 */
@Immutable
final class EnumValueValidator extends AbstractHeaderValueValidator {

    private final Set<String> enumValueSet;
    private final String errorDescription;
    private final Class<?> enumDeclaringType;

    private EnumValueValidator(final List<Enum<?>> enumValues) {
        super(String.class::equals);
        enumDeclaringType = enumValues.get(0).getDeclaringClass();
        enumValueSet = Collections.unmodifiableSet(new LinkedHashSet<>(groupByNormalizedName(enumValues)));
        errorDescription = formatErrorDescription(enumValueSet);
    }

    /**
     * Returns an instance of {@code EnumValueValidator}.
     *
     * @param enumValues the known and allowed enum values this EnumValueValidator validates for.
     * @return the enum validator instance.
     * @throws IllegalArgumentException if {@code enumValues} is empty.
     * @throws NullPointerException if {@code enumValues} is {@code null}.
     */
    static EnumValueValidator getInstance(final Enum<?>[] enumValues) {
        checkNotNull(enumValues, "enumValues");
        final List<Enum<?>> enums = Arrays.asList(enumValues);
        checkNotEmpty(enums, "enumValues");
        return new EnumValueValidator(enums);
    }

    @Override
    protected void validateValue(final HeaderDefinition definition, final CharSequence value) {
        final String normalizedValue = normalize(value);
        if (!enumValueSet.contains(normalizedValue)) {
            throw DittoHeaderInvalidException.newInvalidTypeBuilder(definition, value,
                            "enum value of type '" + enumDeclaringType.getSimpleName() + "'")
                    .description(errorDescription)
                    .build();
        }
    }

    private static String normalize(final CharSequence charSequence) {
        return charSequence.toString().trim().toLowerCase(Locale.ENGLISH);
    }

    private static List<String> groupByNormalizedName(final Collection<Enum<?>> enumValues) {
        return enumValues.stream()
                .map(Enum::toString)
                .map(EnumValueValidator::normalize)
                .collect(Collectors.toList());
    }

    private static String formatErrorDescription(final Collection<String> normalizedNames) {
        final String valuesString = String.join("|", normalizedNames);
        return MessageFormat.format("The value must be one of: <{0}>.", valuesString);
    }
}
