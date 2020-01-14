/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.model.connectivity;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.placeholders.Expression;

/**
 * Simple placeholder that currently only supports {@code {{ source:address }}} as a placeholder.
 * In the context of an incoming MQTT message the placeholder is resolved with the message topic.
 */
@Immutable
final class ImmutableSourceAddressPlaceholder implements SourceAddressPlaceholder {

    /**
     * Singleton instance of the ImmutableSourceAddressPlaceholder.
     */
    static final ImmutableSourceAddressPlaceholder INSTANCE = new ImmutableSourceAddressPlaceholder();

    private static final String PREFIX = "source";
    private static final String VALUE = "address";

    private static final List<String> VALID_VALUES = Collections.unmodifiableList(
            Collections.singletonList(PREFIX + Expression.SEPARATOR + VALUE));

    @Override
    public String getPrefix() {
        return PREFIX;
    }

    @Override
    public List<String> getSupportedNames() {
        return VALID_VALUES;
    }

    @Override
    public boolean supports(final String name) {
        return VALUE.equalsIgnoreCase(name);
    }

    @Override
    public Optional<String> resolve(final String input, final String name) {
        return supports(name) ? Optional.of(input) : Optional.empty();
    }

    private ImmutableSourceAddressPlaceholder() {
    }
}
