/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.model.assertions;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.eclipse.ditto.thingsearch.model.SearchFilter;

/**
 * An assert for {@link org.eclipse.ditto.thingsearch.model.SearchFilter}.
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
