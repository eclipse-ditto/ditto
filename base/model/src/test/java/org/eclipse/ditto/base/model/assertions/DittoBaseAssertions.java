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
package org.eclipse.ditto.base.model.assertions;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;

/**
 * Project specific {@link org.assertj.core.api.Assertions} to extends the set of assertions which are provided by FEST.
 */
public class DittoBaseAssertions extends DittoJsonAssertions {

    /**
     * Returns an assert for the given {@link JsonField}.
     *
     * @param jsonField the object to be checked.
     * @return an assert for {@code jsonField}.
     */
    public static JsonFieldAssert assertThat(final JsonField jsonField) {
        return new JsonFieldAssert(jsonField);
    }

    /**
     * Returns an assert for the given {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException}.
     *
     * @param dittoRuntimeException the DittoRuntimeException to be checked.
     * @return the assert for {@code DittoRuntimeException}.
     */
    public static DittoRuntimeAssert assertThat(final DittoRuntimeException dittoRuntimeException) {
        return new DittoRuntimeAssert(dittoRuntimeException);
    }

    /**
     * Returns an assert for the given {@link org.eclipse.ditto.base.model.assertions.DittoHeadersAssert}.
     *
     * @param dittoHeaders the DittoHeaders to be checked.
     * @return the assert for {@code dittoHeaders}.
     */
    public static DittoHeadersAssert assertThat(final DittoHeaders dittoHeaders) {
        return new DittoHeadersAssert(dittoHeaders);
    }

}
