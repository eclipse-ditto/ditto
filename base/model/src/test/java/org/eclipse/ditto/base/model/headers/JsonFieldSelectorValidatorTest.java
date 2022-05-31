/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.json.JsonFieldSelectorInvalidException;
import org.junit.Test;

/**
 * Unit test for {@link JsonFieldSelectorValidator}.
 */
public final class JsonFieldSelectorValidatorTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(JsonFieldSelectorValidator.class, areImmutable());
    }

    @Test
    public void validateJsonFieldSelectorCharSequence() {
        final String jsonFiledSelectorCharSequence = "/thingId,/attributes,features(featureA,featureB/unit)";

        final JsonFieldSelectorValidator underTest = JsonFieldSelectorValidator.getInstance();

        assertThatCode(() -> underTest.accept(DittoHeaderDefinition.GET_METADATA, jsonFiledSelectorCharSequence))
                .doesNotThrowAnyException();
    }

    @Test
    public void validateEmptyJsonFieldSelectorCharSequence() {
        final String emptyJsonFieldSelectorCharSequence = "";

        final JsonFieldSelectorValidator underTest = JsonFieldSelectorValidator.getInstance();

        assertThatCode(() -> underTest.accept(DittoHeaderDefinition.GET_METADATA, emptyJsonFieldSelectorCharSequence))
                .doesNotThrowAnyException();
    }

    @Test
    public void validateInvalidNullJsonFieldSelector() {
        final JsonFieldSelectorValidator underTest = JsonFieldSelectorValidator.getInstance();

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(DittoHeaderDefinition.GET_METADATA, null))
                .withMessage("The value '%s' of the header '%s' is not a valid String.",
                        null, DittoHeaderDefinition.GET_METADATA.getKey())
                .withNoCause();

    }

    @Test
    public void validateInvalidJsonFieldSelectorCharSequence() {
        final String jsonFiledSelectorCharSequence = "/thingId,/attributes,features(featureA,featureB/unit";

        final JsonFieldSelectorValidator underTest = JsonFieldSelectorValidator.getInstance();

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> underTest.accept(DittoHeaderDefinition.GET_METADATA, jsonFiledSelectorCharSequence))
                .withMessage("The value '%s' of the header '%s' is not a valid field selector.",
                        jsonFiledSelectorCharSequence, DittoHeaderDefinition.GET_METADATA.getKey())
                .withCauseInstanceOf(JsonFieldSelectorInvalidException.class);
    }

}
