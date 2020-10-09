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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.entity.id.EntityId;

/**
 * Java representation of a connection ID.
 */
@Immutable
public final class ConnectionId implements EntityId {

    static final String ID_REGEX = "[a-zA-Z0-9-_]{1,60}";
    static final Pattern ID_PATTERN = Pattern.compile(ID_REGEX);

    private static final ConnectionId DUMMY_ID = ConnectionId.of("_");

    private final String stringRepresentation;

    private ConnectionId(final String stringRepresentation, final boolean shouldValidate) {
        this.stringRepresentation = shouldValidate ? validate(stringRepresentation) : stringRepresentation;
    }

    /**
     * Generates a random unique connection ID.
     *
     * @return the generated connection ID.
     */
    public static ConnectionId generateRandom() {
        return new ConnectionId(UUID.randomUUID().toString(), false);
    }

    /**
     * Creates a connection ID based on the given char sequence. May return the same instance as the parameter if
     * it is already an instance of ConnectionId.
     *
     * @param connectionId the connection id.
     * @return the connection ID.
     */
    public static ConnectionId of(final CharSequence connectionId) {
        if (connectionId instanceof ConnectionId) {
            return (ConnectionId) connectionId;
        }
        return new ConnectionId(checkNotNull(connectionId, "connectionId").toString(), true);
    }

    /**
     * Returns a dummy {@link ConnectionId}. This ID should not be used. It can be identified by
     * checking {@link ConnectionId#isDummy()}.
     *
     * @return the dummy ID.
     */
    public static ConnectionId dummy() {
        return DUMMY_ID;
    }

    @Override
    public boolean isDummy() {
        return DUMMY_ID.equals(this);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ConnectionId connectionId = (ConnectionId) o;
        return Objects.equals(stringRepresentation, connectionId.stringRepresentation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stringRepresentation);
    }


    @Override
    public String toString() {
        return stringRepresentation;
    }

    private String validate(final @Nullable String entityId) {
        if (entityId == null || !ConnectionIdPatternValidator.getInstance(entityId).isValid()) {
            throw ConnectionIdInvalidException.newBuilder(entityId).build();
        }
        return entityId;
    }

}
