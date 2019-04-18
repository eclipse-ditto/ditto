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

package org.eclipse.ditto.signals.commands.connectivity.modify;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableCommandResponse;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommandResponse;

/**
 * Response to a {@link ResetConnectionLogs} command.
 */
@Immutable
@JsonParsableCommandResponse(type = ResetConnectionLogsResponse.TYPE)
public final class ResetConnectionLogsResponse extends AbstractCommandResponse<ResetConnectionLogsResponse>
    implements ConnectivityModifyCommandResponse<ResetConnectionLogsResponse> {

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + ResetConnectionLogs.NAME;

    private final String connectionId;

    private ResetConnectionLogsResponse(final String connectionId, final DittoHeaders dittoHeaders) {
        super(TYPE, HttpStatusCode.OK, dittoHeaders);
        this.connectionId = connectionId;
    }

    /**
     * Creates a new instance of {@code ResetConnectionLogsResponse}.
     *
     * @param connectionId the connection for which logging should be reset.
     * @param dittoHeaders the headers of the request.
     * @return a new instance of the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ResetConnectionLogsResponse of(final String connectionId, final DittoHeaders dittoHeaders) {
        checkNotNull(connectionId, "Connection ID");
        return new ResetConnectionLogsResponse(connectionId, dittoHeaders);
    }

    /**
     * Creates a new {@code ResetConnectionLogsResponse} from a JSON string.
     * @param jsonString the JSON containing the command.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ResetConnectionLogsResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code ResetConnectionLogsResponse} from a JSON object.
     * @param jsonObject the JSON containing the command.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ResetConnectionLogsResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<ResetConnectionLogsResponse>(TYPE, jsonObject).deserialize(statusCode -> {
            final String readConnectionId = jsonObject.getValueOrThrow(ConnectivityCommandResponse.JsonFields.JSON_CONNECTION_ID);

            return of(readConnectionId, dittoHeaders);
        });
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ConnectivityCommandResponse.JsonFields.JSON_CONNECTION_ID, connectionId, predicate);
    }

    @Override
    public String getConnectionId() {
        return connectionId;
    }

    @Override
    public ResetConnectionLogsResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(connectionId, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof ResetConnectionLogsResponse);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final ResetConnectionLogsResponse that = (ResetConnectionLogsResponse) o;
        return Objects.equals(connectionId, that.connectionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), connectionId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", connectionId=" + connectionId +
                "]";
    }

}
