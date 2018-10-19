/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.models.connectivity.placeholder;

/**
 * Factory that creates instances of {@link Placeholder}s.
 */
public final class PlaceholderFactory {

    /**
     * @return new instance of the {@link HeadersPlaceholder}
     */
    public static HeadersPlaceholder newHeadersPlaceholder() {
        return ImmutableHeadersPlaceholder.INSTANCE;
    }

    /**
     * @return new instance of the {@link ThingPlaceholder}
     */
    public static ThingPlaceholder newThingPlaceholder() {
        return ImmutableThingPlaceholder.INSTANCE;
    }

    /**
     * @return new instance of the {@link SourceAddressPlaceholder}
     */
    public static SourceAddressPlaceholder newSourceAddressPlaceholder() {
        return ImmutableSourceAddressPlaceholder.INSTANCE;
    }

    private PlaceholderFactory() {
        throw new AssertionError();
    }
}
