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
package org.eclipse.ditto.base.model.headers;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;

/**
 * Base implementation of {@link org.eclipse.ditto.base.model.headers.ValueValidator} which provides common functionality for header value validation.
 * Sub-classes are supposed to implement the <em>abstract methods only.</em>
 *
 * @since 1.1.0
 */
public abstract class AbstractHeaderValueValidator implements ValueValidator {

    private final Predicate<Class<?>> valueTypePredicate;

    /**
     * Constructs a new AbstractHeaderValueValidator object.
     *
     * @param valueTypePredicate this predicate is used to determine if the constructed validator is responsible for the
     * Java type of a given HeaderDefinition.
     * @throws NullPointerException if {@code valueTypePredicate} is {@code null}.
     */
    protected AbstractHeaderValueValidator(final Predicate<Class<?>> valueTypePredicate) {
        this.valueTypePredicate = checkNotNull(valueTypePredicate, "valueTypePredicate");
    }

    @Override
    public void accept(final HeaderDefinition definition, @Nullable final CharSequence value) {
        checkNotNull(definition, "definition");
        if (isThisValidatorResponsible(definition)) {
            assertValueNotNull(definition, value);
            validateValue(definition, value);
        }
    }

    private boolean isThisValidatorResponsible(final HeaderDefinition definition) {
        return canValidate(definition.getJavaType());
    }

    @Override
    public boolean canValidate(@Nullable final Class<?> valueType) {
        return valueTypePredicate.test(valueType);
    }

    private static void assertValueNotNull(final HeaderDefinition definition, @Nullable final CharSequence value) {
        if (null == value) {
            throw DittoHeaderInvalidException
                    .newInvalidTypeBuilder(definition, value, definition.getJavaType().getSimpleName())
                    .build();
        }
    }

    /**
     * Validates the given value with regards to the given header definition.
     * Both arguments are guaranteed to be not {@code null} it was already determined that this validator is
     * responsible.
     *
     * @param definition the definition of the value to be validated.
     * @param value the value to be validated.
     */
    protected abstract void validateValue(HeaderDefinition definition, CharSequence value);

    @Override
    public ValueValidator andThen(final ValueValidator after) {
        checkNotNull(after, "after");

        return new AbstractHeaderValueValidator(valueType -> true) {
            @Override
            public void accept(final HeaderDefinition definition, @Nullable final CharSequence value) {
                AbstractHeaderValueValidator.this.accept(definition, value);
                after.accept(definition, value);
            }

            @Override
            protected void validateValue(final HeaderDefinition definition, final CharSequence value) {
                // do nothing
            }
        };
    }

}
