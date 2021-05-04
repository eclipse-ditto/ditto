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

import java.util.Map;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

/**
 * Immutable default implementation of the {@code DittoHeaders} interface.
 */
@Immutable
final class ImmutableDittoHeaders extends AbstractDittoHeaders implements DittoHeaders {

    private ImmutableDittoHeaders(final Map<String, String> headers) {
        super(headers);
    }

    private ImmutableDittoHeaders(final Map<String, Header> headers, final boolean flag) {
        super(headers, flag);
    }

    /**
     * Returns an instance of {@code ImmutableDittoHeaders} which is based on the specified map.
     *
     * @param headers the key-value-pairs of the result.
     * @return the instance.
     * @throws NullPointerException if {@code headers} is {@code null}.
     */
    static ImmutableDittoHeaders of(final Map<String, String> headers) {
        return new ImmutableDittoHeaders(headers);
    }

    static ImmutableDittoHeaders fromBuilder(final Map<String, Header> builderHeaders) {
        return new ImmutableDittoHeaders(builderHeaders, true);
    }

    @Override
    protected Optional<HeaderDefinition> getSpecificDefinitionByKey(final CharSequence key) {
        // there is no specific header defined for this class; all headers are already defined
        // in the enum DittoHeaderDefinition for AbstractDittoHeaders.
        return Optional.empty();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + "]";
    }

}
