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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatchers;

/**
 * This validator checks if a CharSequence is a comma-separated list of EntityTagMatchers which are valid according to
 * {@link org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatcher#isValid(CharSequence)}.
 * If validation fails, a {@link org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException} is thrown.
 */
@Immutable
final class EntityTagMatchersValueValidator extends AbstractHeaderValueValidator {

    private final ValueValidator entityTagMatcherValueValidator;

    private EntityTagMatchersValueValidator(final ValueValidator entityTagMatcherValueValidator) {
        super(EntityTagMatchers.class::equals);
        this.entityTagMatcherValueValidator = entityTagMatcherValueValidator;
    }

    /**
     * Returns an instance of {@code EntityTagMatchersValueValidator}.
     *
     * @param entityTagMatcherValueValidator the validator to be used for checking each single EntityTagMatcher.
     * @return the instance.
     * @throws NullPointerException if {@code entityTagMatcherValueValidator} is {@code null}.
     */
    static EntityTagMatchersValueValidator getInstance(final ValueValidator entityTagMatcherValueValidator) {
        checkNotNull(entityTagMatcherValueValidator, "entityTagMatcherValueValidator");
        return new EntityTagMatchersValueValidator(entityTagMatcherValueValidator);
    }

    @Override
    protected void validateValue(final HeaderDefinition definition, final CharSequence value) {
        final String[] entityTagMatchers = EntityTagMatchers.ENTITY_TAG_MATCHERS_PATTERN.split(String.valueOf(value));
        for (final String entityTagMatcher : entityTagMatchers) {
            entityTagMatcherValueValidator.accept(definition, entityTagMatcher);
        }
    }

}
