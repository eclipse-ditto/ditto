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
package org.eclipse.ditto.base.model.headers;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;

/**
 * Mutable builder with a fluent API for creating {@link org.eclipse.ditto.base.model.headers.DittoHeaders}.
 */
@NotThreadSafe
final class DefaultDittoHeadersBuilder extends AbstractDittoHeadersBuilder<DefaultDittoHeadersBuilder, DittoHeaders> {

    private static final DittoHeaders EMPTY_DITTO_HEADERS = ImmutableDittoHeaders.of(Collections.emptyMap());
    private static final EnumSet<DittoHeaderDefinition> DEFINITIONS = EnumSet.allOf(DittoHeaderDefinition.class);

    private DefaultDittoHeadersBuilder(final Map<String, String> headers) {
        super(headers, DEFINITIONS, DefaultDittoHeadersBuilder.class);
    }

    private DefaultDittoHeadersBuilder(final DittoHeaders dittoHeaders) {
        super(dittoHeaders, DEFINITIONS, DefaultDittoHeadersBuilder.class);
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
     * @throws org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException if {@code headers} contains a value
     * that did not represent its appropriate Java type.
     */
    static DefaultDittoHeadersBuilder of(final Map<String, String> headers) {
        if (headers instanceof DittoHeaders) {
            return new DefaultDittoHeadersBuilder((DittoHeaders) headers);
        }
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

    /**
     * Returns an empty {@code DittoHeaders} object.
     *
     * @return empty DittoHeaders.
     */
    static DittoHeaders getEmptyHeaders() {
        return EMPTY_DITTO_HEADERS;
    }

    @Override
    protected DittoHeaders doBuild(final DittoHeaders dittoHeaders) {
        return dittoHeaders;
    }

}
