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
package org.eclipse.ditto.model.base.headers;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;

/**
 * Mutable builder with a fluent API for creating {@link DittoHeaders}.
 */
@NotThreadSafe
final class DefaultDittoHeadersBuilder extends AbstractDittoHeadersBuilder<DefaultDittoHeadersBuilder, DittoHeaders> {

    private DefaultDittoHeadersBuilder(final Map<String, String> headers) {
        super(headers, Arrays.asList(DittoHeaderDefinition.values()), DefaultDittoHeadersBuilder.class);
    }

    /**
     * Returns a new instance of {@code DittoHeadersBuilder}.
     *
     * @return a builder for creating {@code DittoHeaders} object.
     */
    static DefaultDittoHeadersBuilder newInstance() {
        return of(new HashMap<>());
    }

    /**
     * Returns a new instance of {@code DittoHeadersBuilder} initialized with the the properties of the given map.
     *
     * @param headers the header map which provides the initial properties of the builder.
     * @return a builder for creating {@code DittoHeaders} object.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     * @throws IllegalArgumentException if {@code headers} contains a value that did not represent its appropriate Java
     * type.
     */
    static DefaultDittoHeadersBuilder of(final Map<String, String> headers) {
        return new DefaultDittoHeadersBuilder(headers);
    }

    /**
     * Returns a new instance of {@code DittoHeadersBuilder} initialized with the the properties of the given
     * {@code jsonObject}.
     *
     * @param jsonObject the JSON object which provides the initial properties of the builder.
     * @return a builder for creating {@code DittoHeaders} object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    static DefaultDittoHeadersBuilder of(final JsonObject jsonObject) {
        return of(toMap(jsonObject));
    }

    @Override
    protected DittoHeaders doBuild(final DittoHeaders dittoHeaders) {
        return dittoHeaders;
    }

}
