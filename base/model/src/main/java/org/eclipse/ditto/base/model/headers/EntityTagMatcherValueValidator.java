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

import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatcher;

/**
 * This validator checks if a CharSequence is valid according to {@link org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatcher#isValid(CharSequence)}.
 * If validation fails, a {@link org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException} is thrown.
 */
@Immutable
final class EntityTagMatcherValueValidator extends AbstractHeaderValueValidator {

    private EntityTagMatcherValueValidator(final Predicate<Class<?>> valueTypePredicate) {
        super(valueTypePredicate);
    }

    /**
     * Returns an instance of {@code EntityTagMatcherValueValidator}.
     *
     * @return the instance.
     */
    static EntityTagMatcherValueValidator getInstance() {
        return getInstance(EntityTagMatcher.class::equals);
    }

    /**
     * Returns an instance of {@code EntityTagMatcherValueValidator}.
     *
     * @param valueTypePredicate the predicate which determines the responsibility of the returned validator.
     * @return the instance.
     * @throws NullPointerException if {@code valueTypePredicate} is {@code null}.
     */
    static EntityTagMatcherValueValidator getInstance(final Predicate<Class<?>> valueTypePredicate) {
        return new EntityTagMatcherValueValidator(valueTypePredicate);
    }

    @Override
    protected void validateValue(final HeaderDefinition definition, final CharSequence value) {
        if (!EntityTagMatcher.isValid(value)) {
            throw DittoHeaderInvalidException
                    .newInvalidTypeBuilder(definition, value, "entity-tag")
                    .href(EntityTagValueValidator.RFC_7232_SECTION_2_3)
                    .build();
        }
    }

}
