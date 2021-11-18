/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol.placeholders;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;

/**
 * Placeholder implementation that replaces:
 * <ul>
 * <li>{@code misc:current-timestamp-iso8601} ->
 *     the current system timestamp in ISO-8601 format, e.g.: {@code "2021-11-17T09:44:08Z"}</li>
 * <li>{@code misc:current-timestamp-epoch-millis} ->
 *     the current system timestamp in milliseconds since the epoch of {@code 1970-01-01T00:00:00Z}</li>
 * </ul>
 * The input value is any Object and is not used.
 */
@Immutable
final class ImmutableMiscPlaceholder implements MiscPlaceholder {

    /**
     * Singleton instance of the ImmutableMiscPlaceholder.
     */
    static final ImmutableMiscPlaceholder INSTANCE = new ImmutableMiscPlaceholder();

    private static final String CURRENT_TIMESTAMP_ISO_8601_PLACEHOLDER = "current-timestamp-iso8601";
    private static final String CURRENT_TIMESTAMP_EPOCH_MILLIS_PLACEHOLDER = "current-timestamp-epoch-millis";

    private static final List<String> SUPPORTED = Collections.unmodifiableList(
            Arrays.asList(CURRENT_TIMESTAMP_ISO_8601_PLACEHOLDER, CURRENT_TIMESTAMP_EPOCH_MILLIS_PLACEHOLDER));

    private ImmutableMiscPlaceholder() {
    }

    @Override
    public String getPrefix() {
        return PREFIX;
    }

    @Override
    public List<String> getSupportedNames() {
        return SUPPORTED;
    }

    @Override
    public boolean supports(final String name) {
        return SUPPORTED.contains(name);
    }

    @Override
    public Optional<String> resolve(final Object someObject, final String placeholder) {
        ConditionChecker.argumentNotEmpty(placeholder, "placeholder");
        switch (placeholder) {
            case CURRENT_TIMESTAMP_ISO_8601_PLACEHOLDER:
                return Optional.of(Instant.now().toString());
            case CURRENT_TIMESTAMP_EPOCH_MILLIS_PLACEHOLDER:
                return Optional.of(String.valueOf(Instant.now().toEpochMilli()));
            default:
                return Optional.empty();
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[]";
    }
}
