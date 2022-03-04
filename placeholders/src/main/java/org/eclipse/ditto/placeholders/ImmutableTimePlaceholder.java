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
package org.eclipse.ditto.placeholders;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;

/**
 * Placeholder implementation that replaces:
 * <ul>
 * <li>{@code time:now} ->
 *     the current system timestamp in ISO-8601 format, e.g.: {@code "2021-11-17T09:44:08Z"}</li>
 * <li>{@code time:now_epoch_millis} ->
 *     the current system timestamp in milliseconds since the epoch of {@code 1970-01-01T00:00:00Z}</li>
 * </ul>
 * The input value is any Object and is not used.
 */
@Immutable
final class ImmutableTimePlaceholder implements TimePlaceholder {

    /**
     * Singleton instance of the ImmutableTimePlaceholder.
     */
    static final ImmutableTimePlaceholder INSTANCE = new ImmutableTimePlaceholder();

    private static final String NOW_PLACEHOLDER = "now";
    private static final String NOW_EPOCH_MILLIS_PLACEHOLDER = "now_epoch_millis";

    private static final List<String> SUPPORTED = Collections.unmodifiableList(
            Arrays.asList(NOW_PLACEHOLDER, NOW_EPOCH_MILLIS_PLACEHOLDER));

    private ImmutableTimePlaceholder() {
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
    public List<String> resolveValues(final Object someObject, final String placeholder) {
        ConditionChecker.argumentNotEmpty(placeholder, "placeholder");
        switch (placeholder) {
            case NOW_PLACEHOLDER:
                return Collections.singletonList(Instant.now().toString());
            case NOW_EPOCH_MILLIS_PLACEHOLDER:
                return Collections.singletonList(String.valueOf(Instant.now().toEpochMilli()));
            default:
                return Collections.emptyList();
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[]";
    }
}
