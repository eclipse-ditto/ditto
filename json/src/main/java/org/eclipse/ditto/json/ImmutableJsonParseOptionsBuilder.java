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
package org.eclipse.ditto.json;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * A builder for creating instances of {@link JsonParseOptions}.
 */
@NotThreadSafe
final class ImmutableJsonParseOptionsBuilder implements JsonParseOptionsBuilder {

    private boolean applyUrlDecoding;

    private ImmutableJsonParseOptionsBuilder() {
        applyUrlDecoding = false;
    }

    public static ImmutableJsonParseOptionsBuilder newInstance() {
        return new ImmutableJsonParseOptionsBuilder();
    }

    @Override
    public JsonParseOptionsBuilder withUrlDecoding() {
        applyUrlDecoding = true;
        return this;
    }

    @Override
    public JsonParseOptionsBuilder withoutUrlDecoding() {
        applyUrlDecoding = false;
        return this;
    }

    @Override
    public JsonParseOptions build() {
        return ImmutableJsonParseOptions.of(applyUrlDecoding);
    }
}
