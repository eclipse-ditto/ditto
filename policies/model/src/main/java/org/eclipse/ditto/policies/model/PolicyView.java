/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.model;

import java.util.Locale;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;

/**
 * Supported values of the {@code policy-view} request hint: {@link #ORIGINAL} returns the policy as
 * stored, {@link #RESOLVED} returns the merged view (own entries plus declared imports plus
 * configured namespace-root policies, with references resolved). Imported labels are rewritten as
 * {@code imported-<importedPolicyId>-<originalLabel>} and should be treated as opaque.
 *
 * @since 3.9.0
 */
public enum PolicyView {

    ORIGINAL("original"),
    RESOLVED("resolved");

    private final String value;

    PolicyView(final String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public boolean isResolved() {
        return this == RESOLVED;
    }

    @Override
    public String toString() {
        return value;
    }

    /**
     * Parses a raw query/header value (case-insensitive, trimmed). Returns empty for {@code null} or blank input.
     *
     * @throws PolicyViewInvalidException if {@code raw} is non-blank but does not match a known value.
     */
    public static Optional<PolicyView> fromString(@Nullable final String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        final String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        final String lower = trimmed.toLowerCase(Locale.ROOT);
        for (final PolicyView v : values()) {
            if (v.value.equals(lower)) {
                return Optional.of(v);
            }
        }
        throw PolicyViewInvalidException.forValue(raw);
    }

    /**
     * Reads {@code policy-view} from {@link DittoHeaders}. Same parsing semantics as {@link #fromString}.
     */
    public static Optional<PolicyView> from(final DittoHeaders headers) {
        return fromString(headers.get(DittoHeaderDefinition.POLICY_VIEW.getKey()));
    }

}
