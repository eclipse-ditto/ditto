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

/* Copyright (c) 2011-2018 Bosch Software Innovations GmbH, Germany. All rights reserved. */
package org.eclipse.ditto.signals.commands.common;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.common.ConditionChecker;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonParsableCommandResponse;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to a {@link Cleanup} command.
 */
@Immutable
@JsonParsableCommandResponse(type = CleanupResponse.TYPE)
public class CleanupResponse extends CommonCommandResponse<CleanupResponse> {

    /**
     * The type of the {@code Cleanup} command.
     */
    public static final String TYPE = TYPE_PREFIX + Cleanup.NAME;

    private final String entityId;

    private CleanupResponse(final String entityId, final HttpStatusCode statusCode, final DittoHeaders dittoHeaders) {
        super(TYPE, statusCode, dittoHeaders);
        this.entityId = ConditionChecker.checkNotNull(entityId, "entityId");
    }

    public static CleanupResponse success(final String entityId) {
        return new CleanupResponse(entityId, HttpStatusCode.OK, DittoHeaders.empty());
    }

    public static CleanupResponse failure(final String entityId) {
        return new CleanupResponse(entityId, HttpStatusCode.INTERNAL_SERVER_ERROR, DittoHeaders.empty());
    }

    @Override
    public CleanupResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new CleanupResponse(this.getId(), this.getStatusCode(), dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {
        jsonObjectBuilder.set(Cleanup.JsonFields.ID, entityId, predicate);
    }

    @Override
    public String getId() {
        return entityId;
    }

    /**
     * Creates a new {@code CleanupResponse} from the given JSON object.
     *
     * @param jsonObject the JSON object of which the CleanupResponse is to be created.
     * @param dittoHeaders the headers.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain
     * {@link Command.JsonFields#ID}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static CleanupResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<CleanupResponse>(TYPE, jsonObject).deserialize(
                (statusCode) -> new CleanupResponse(jsonObject.getValueOrThrow(Command.JsonFields.ID),
                        statusCode,
                        dittoHeaders));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        final CleanupResponse that = (CleanupResponse) o;
        return entityId.equals(that.entityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), entityId);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof CleanupResponse;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                 super.toString() +
                ", entityId=" + entityId +
                "]";
    }
}
