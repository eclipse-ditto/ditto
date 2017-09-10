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
package org.eclipse.ditto.model.thingsearch.assertions;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.eclipse.ditto.model.thingsearch.SearchFilter;

/**
 * An assert for {@link SearchFilter}.
 *
 * @param <F> the type of the checked SearchFilter.
 */
abstract class SearchFilterAssert<S extends AbstractAssert<S, F>, F extends SearchFilter> extends AbstractAssert<S, F> {

    /**
     * Constructs a new {@code SearchFilterAssert} object.
     *
     * @param actual the search query to be checked.
     * @param selfType the type of the actual value.
     */
    protected SearchFilterAssert(final F actual, final Class<? extends SearchFilterAssert> selfType) {
        super(actual, selfType);
    }

    public S hasType(final SearchFilter.Type expectedType) {
        isNotNull();
        final SearchFilter.Type actualType = actual.getType();
        Assertions.assertThat(actualType) //
                .overridingErrorMessage("Expected SearchFilter to have type \n<%s> but it had \n<%s>", expectedType,
                        actualType) //
                .isSameAs(expectedType);
        return myself;
    }

    public S hasStringRepresentation(final String expectedStringRepresentation) {
        isNotNull();
        final String actualStringRepresentation = actual.toString();
        Assertions.assertThat(actualStringRepresentation) //
                .overridingErrorMessage("Expected string representation of SearchFilter to be \n<%s> but it was \n<%s>",
                        expectedStringRepresentation, actualStringRepresentation) //
                .isEqualTo(expectedStringRepresentation);
        return myself;
    }

}
