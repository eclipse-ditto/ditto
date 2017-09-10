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
package org.eclipse.ditto.model.base.assertions;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

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
     * Returns an assert for the given {@link DittoRuntimeException}.
     *
     * @param dittoRuntimeException the DittoRuntimeException to be checked.
     * @return the assert for {@code DittoRuntimeException}.
     */
    public static DittoRuntimeAssert assertThat(final DittoRuntimeException dittoRuntimeException) {
        return new DittoRuntimeAssert(dittoRuntimeException);
    }

    /**
     * Returns an assert for the given {@link DittoHeadersAssert}.
     *
     * @param dittoHeaders the DittoHeaders to be checked.
     * @return the assert for {@code dittoHeaders}.
     */
    public static DittoHeadersAssert assertThat(final DittoHeaders dittoHeaders) {
        return new DittoHeadersAssert(dittoHeaders);
    }

}
