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
