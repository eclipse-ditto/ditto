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
package org.eclipse.ditto.connectivity.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.UUID;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.AbstractEntityId;
import org.eclipse.ditto.base.model.entity.id.TypedEntityId;

/**
 * Java representation of a connection ID.
 */
@Immutable
@TypedEntityId(type = "connection")
public final class ConnectionId extends AbstractEntityId {

    static final String ID_REGEX = "[a-zA-Z0-9-_:]{1,100}";
    static final Pattern ID_PATTERN = Pattern.compile(ID_REGEX);

    private ConnectionId(final String stringRepresentation, final boolean shouldValidate) {
        super(ConnectivityConstants.ENTITY_TYPE,
                shouldValidate ? validate(stringRepresentation) : stringRepresentation);
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
     * @throws ConnectionIdInvalidException if the provided {@code connectionId} is of invalid format.
     * @return the connection ID.
     */
    public static ConnectionId of(final CharSequence connectionId) {
        if (connectionId instanceof ConnectionId) {
            return (ConnectionId) connectionId;
        }
        return new ConnectionId(checkNotNull(connectionId, "connectionId").toString(), true);
    }

    private static String validate(final @Nullable String entityId) {
        if (entityId == null || !ConnectionIdPatternValidator.getInstance(entityId).isValid()) {
            throw ConnectionIdInvalidException.newBuilder(entityId).build();
        }
        return entityId;
    }

}
