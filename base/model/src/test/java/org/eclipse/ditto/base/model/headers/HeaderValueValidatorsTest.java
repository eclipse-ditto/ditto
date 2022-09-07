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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.eclipse.ditto.base.model.common.DittoDuration;
import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatcher;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatchers;
import org.eclipse.ditto.json.JsonArray;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit test for {@link org.eclipse.ditto.base.model.headers.HeaderValueValidators}.
 */
public final class HeaderValueValidatorsTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(HeaderValueValidators.class, areImmutable());
    }

    @Test
    public void getIntValueValidatorReturnsAppropriateValidator() {
        final ValueValidator validator = HeaderValueValidators.getIntValidator();

        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(validator.canValidate(int.class)).as("int").isTrue();
            softly.assertThat(validator.canValidate(Integer.class)).as("Integer").isTrue();
        }
    }

    @Test
    public void getLongValueValidatorReturnsAppropriateValidator() {
        final ValueValidator validator = HeaderValueValidators.getLongValidator();

        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(validator.canValidate(long.class)).as("long").isTrue();
            softly.assertThat(validator.canValidate(Long.class)).as("Long").isTrue();
        }
    }

    @Test
    public void getBooleanValueValidatorReturnsAppropriateValidator() {
        final ValueValidator validator = HeaderValueValidators.getBooleanValidator();

        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(validator.canValidate(boolean.class)).as("boolean").isTrue();
            softly.assertThat(validator.canValidate(Boolean.class)).as("Boolean").isTrue();
        }
    }

    @Test
    public void getJsonArrayValidatorReturnsAppropriateValidator() {
        final ValueValidator validator = HeaderValueValidators.getJsonArrayValidator();

        assertThat(validator.canValidate(JsonArray.class)).isTrue();
    }

    @Test
    public void getEntityTagValidatorReturnsAppropriateValidator() {
        final ValueValidator validator = HeaderValueValidators.getEntityTagValidator();

        assertThat(validator.canValidate(EntityTag.class)).isTrue();
    }

    @Test
    public void getEntityTagMatcherValidatorReturnsAppropriateValidator() {
        final ValueValidator validator = HeaderValueValidators.getEntityTagMatcherValidator();

        assertThat(validator.canValidate(EntityTagMatcher.class)).isTrue();
    }

    @Test
    public void getEntityTagMatchersValidatorReturnsAppropriateValidator() {
        final ValueValidator validator = HeaderValueValidators.getEntityTagMatchersValidator();

        assertThat(validator.canValidate(EntityTagMatchers.class)).isTrue();
    }

    @Test
    public void getDittoDurationValidatorReturnsAppropriateValidator() {
        final ValueValidator validator = HeaderValueValidators.getDittoDurationValidator();

        assertThat(validator.canValidate(DittoDuration.class)).isTrue();
    }

    @Test
    public void validateStringWithNoOpValidator() {
        final ValueValidator validator = HeaderValueValidators.getNoOpValidator();

        assertThatCode(() -> validator.accept(DittoHeaderDefinition.CONTENT_TYPE, "application/text"))
                .doesNotThrowAnyException();
    }

    @Test
    public void validatorChainIsCompletelyTraversedUntilResponsibleValidator() {
        final ValueValidator noOpValidator = HeaderValueValidators.getNoOpValidator();
        final ValueValidator intValidator = HeaderValueValidators.getIntValidator();
        final ValueValidator booleanValidator = HeaderValueValidators.getBooleanValidator();
        final ValueValidator entityTagValidator = HeaderValueValidators.getEntityTagValidator();
        final ValueValidator jsonArrayValidator = HeaderValueValidators.getJsonArrayValidator();

        final ValueValidator validatorChain = noOpValidator.andThen(intValidator)
                .andThen(booleanValidator)
                .andThen(entityTagValidator)
                .andThen(jsonArrayValidator);

        final String headerKey = "my-json-array";
        final HeaderDefinition headerDefinition = Mockito.mock(HeaderDefinition.class);
        Mockito.when(headerDefinition.getKey()).thenReturn(headerKey);
        Mockito.when(headerDefinition.getJavaType()).thenReturn(JsonArray.class);
        final String invalidJsonArrayString = "{}";

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> validatorChain.accept(headerDefinition, invalidJsonArrayString))
                .withMessageContaining(headerKey)
                .withMessageContaining(invalidJsonArrayString);
    }

}
