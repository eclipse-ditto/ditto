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
package org.eclipse.ditto.services.utils.health;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableCommandResponse;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;

/**
 * Response of {@link org.eclipse.ditto.services.utils.health.RetrieveHealth}.
 */
@Immutable
@JsonParsableCommandResponse(type = RetrieveHealthResponse.TYPE)
public final class RetrieveHealthResponse extends AbstractCommandResponse<RetrieveHealthResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = "status.responses:" + RetrieveHealth.NAME;

    /**
     * Json field of the StatusInfo-payload.
     */
    public static final JsonFieldDefinition<JsonObject> STATUS_INFO =
            JsonFactory.newJsonObjectFieldDefinition("statusInfo");

    private final StatusInfo statusInfo;

    private RetrieveHealthResponse(final StatusInfo statusInfo, final DittoHeaders headers) {
        super(TYPE, toHttpStatusCode(statusInfo.getStatus()), headers);
        this.statusInfo = statusInfo;
    }

    /**
     * Create a RetrieveHealthResponse.
     *
     * @param statusInfo the status info.
     * @param dittoHeaders the Ditto headers.
     * @return the RetrieveHealthResponse.
     */
    public static RetrieveHealthResponse of(final StatusInfo statusInfo, final DittoHeaders dittoHeaders) {
        return new RetrieveHealthResponse(statusInfo, dittoHeaders);
    }

    /**
     * Create a RetrieveHealthResponse from its JSON representation.
     *
     * @param jsonObject JSON of the response.
     * @param dittoHeaders headers of the response.
     * @return the response.
     */
    public static RetrieveHealthResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return of(StatusInfo.fromJson(jsonObject.getValueOrThrow(STATUS_INFO)), dittoHeaders);
    }

    /**
     * Return the status info.
     *
     * @return the status info.
     */
    public StatusInfo getStatusInfo() {
        return statusInfo;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {

        jsonObjectBuilder.set(STATUS_INFO, statusInfo.toJson());
    }

    @Override
    public RetrieveHealthResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new RetrieveHealthResponse(statusInfo, dittoHeaders);
    }

    @Override
    public String getId() {
        // no ID
        return "";
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public String getResourceType() {
        // no resource type
        return "";
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof RetrieveHealthResponse) {
            return super.equals(o) && Objects.equals(statusInfo, ((RetrieveHealthResponse) o).statusInfo);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), statusInfo);
    }

    @Override
    public String toString() {
        return "RetrieveHealthResponse[statusInfo=" + statusInfo + ", dittoHeaders=" + getDittoHeaders() + "]";
    }

    private static HttpStatusCode toHttpStatusCode(final StatusInfo.Status status) {
        switch (status) {
            case UP:
            case UNKNOWN:
                return HttpStatusCode.OK;
            case DOWN:
            default:
                return HttpStatusCode.INTERNAL_SERVER_ERROR;
        }
    }
}
