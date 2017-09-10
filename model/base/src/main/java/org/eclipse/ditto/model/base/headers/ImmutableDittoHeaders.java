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
import java.util.Collection;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

/**
 * Immutable default implementation of the {@code DittoHeaders} interface.
 */
@Immutable
final class ImmutableDittoHeaders extends AbstractDittoHeaders implements DittoHeaders {

    private ImmutableDittoHeaders(final Map<String, String> headers) {
        super(headers);
    }

    /**
     * Returns an instance of {@code ImmutableDittoHeaders} which is based on the specified map.
     *
     * @param headers the key-value-pairs of the result.
     * @return the instance.
     * @throws NullPointerException if {@code headers} is {@code null}.
     */
    public static ImmutableDittoHeaders of(final Map<String, String> headers) {
        return new ImmutableDittoHeaders(headers);
    }

    @Override
    protected Collection<HeaderDefinition> getSpecificDefinitions() {
        return Arrays.asList(DittoHeaderDefinition.values());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + "]";
    }

}
