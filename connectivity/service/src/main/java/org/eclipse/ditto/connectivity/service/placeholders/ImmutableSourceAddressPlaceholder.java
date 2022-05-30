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
package org.eclipse.ditto.connectivity.service.placeholders;

import java.util.Collections;
import java.util.List;

import javax.annotation.concurrent.Immutable;

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

    private static final List<String> VALID_VALUES = Collections.singletonList(VALUE);

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
        return VALID_VALUES.contains(name);
    }

    @Override
    public List<String> resolveValues(final String input, final String name) {
        return supports(name) ? Collections.singletonList(input) : Collections.emptyList();
    }

    private ImmutableSourceAddressPlaceholder() {
    }
}
