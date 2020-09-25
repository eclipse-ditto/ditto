/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.model.placeholders;

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.connectivity.ConnectionId;

/**
 * Placeholder implementation that replaces {@code connection:id}.
 * The input value is a String and must be a valid connection ID.
 *
 * @since 1.3.0
 */
@Immutable
final class ImmutableConnectionIdPlaceholder implements ConnectionIdPlaceholder {

    private static final String ID_PLACEHOLDER = "id";

    private static final List<String> SUPPORTED_PLACEHOLDERS = Collections.singletonList(ID_PLACEHOLDER);

    /**
     * Singleton instance of the ImmutableThingPlaceholder.
     */
    static final ImmutableConnectionIdPlaceholder INSTANCE = new ImmutableConnectionIdPlaceholder();

    @Override
    public String getPrefix() {
        return "connection";
    }

    @Override
    public List<String> getSupportedNames() {
        return SUPPORTED_PLACEHOLDERS;
    }

    @Override
    public boolean supports(final String name) {
        return SUPPORTED_PLACEHOLDERS.contains(name);
    }

    @Override
    public Optional<String> resolve(final CharSequence connectionId, final String placeholder) {
        argumentNotEmpty(placeholder, "placeholder");
        checkNotNull(connectionId, "Connection ID");
        if (isConnectionId(connectionId) && ID_PLACEHOLDER.equals(placeholder)) {
            return Optional.of(connectionId.toString());
        }
        return Optional.empty();
    }

    private boolean isConnectionId(final CharSequence connectionId) {
        return connectionId instanceof ConnectionId && !((ConnectionId) connectionId).isDummy();
    }

}
