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
package org.eclipse.ditto.services.amqpbridge.mapping.mapper;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

/**
 * TODO doc
 */
final class ImmutablePayloadMapperOptions implements PayloadMapperOptions {

    private final Map<String, String> optionsMap;

    /**
     *
     * @param optionsMap
     */
    ImmutablePayloadMapperOptions(final Map<String, String> optionsMap) {
        this.optionsMap = Collections.unmodifiableMap(new HashMap<>(optionsMap));
    }

    @Override
    public Map<String, String> getAsMap() {
        return optionsMap;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImmutablePayloadMapperOptions)) {
            return false;
        }
        final ImmutablePayloadMapperOptions that = (ImmutablePayloadMapperOptions) o;
        return Objects.equals(optionsMap, that.optionsMap);
    }

    @Override
    public int hashCode() {

        return Objects.hash(optionsMap);
    }

    /**
     *
     */
    static final class Builder implements PayloadMapperOptions.Builder {

        private final Map<String, String> options;

        Builder(final Map<String, String> options) {
            this.options = options;
        }

        @Override
        public PayloadMapperOptions build() {
            return new ImmutablePayloadMapperOptions(options);
        }
    }
}
