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
package org.eclipse.ditto.connectivity.api;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.internal.models.streaming.AbstractEntityIdWithRevision;

/**
 * Represents the ID and revision of a connection.
 */
@Immutable
public final class ConnectionTag extends AbstractEntityIdWithRevision<ConnectionId> {

    private ConnectionTag(final ConnectionId id, final long revision) {
        super(id, revision);
    }

    /**
     * Returns a new {@link ConnectionTag}.
     *
     * @param id the connection ID.
     * @param revision the revision.
     * @return a new {@link ConnectionTag}.
     */
    public static ConnectionTag of(final ConnectionId id, final long revision) {
        return new ConnectionTag(id, revision);
    }

    /**
     * Creates a new {@link ConnectionTag} from a JSON object.
     *
     * @param jsonObject the JSON object of which a new {@link ConnectionTag} is to be created.
     * @return the {@link ConnectionTag} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonObject} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} is not valid JSON.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the passed in {@code jsonObject} was not in the
     * expected format.
     */
    public static ConnectionTag fromJson(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "JSON object");
        final ConnectionId connectionId = ConnectionId.of(jsonObject.getValueOrThrow(JsonFields.ENTITY_ID));
        final Long revision = jsonObject.getValueOrThrow(JsonFields.REVISION);
        return new ConnectionTag(connectionId, revision);
    }

}
