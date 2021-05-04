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
package org.eclipse.ditto.internal.utils.health;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;

/**
 * Response of {@link ResetHealthEvents}.
 */
@Immutable
@JsonParsableCommandResponse(type = ResetHealthEventsResponse.TYPE)
public final class ResetHealthEventsResponse extends AbstractCommandResponse<ResetHealthEventsResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = "status." + TYPE_QUALIFIER + ":" + ResetHealthEvents.NAME;

    private ResetHealthEventsResponse(final DittoHeaders headers) {
        super(TYPE, HttpStatus.NO_CONTENT, headers);
    }

    /**
     * Create a ResetHealthEventsResponse.
     *
     * @param dittoHeaders the Ditto headers.
     * @return the ResetHealthEventsResponse.
     */
    public static ResetHealthEventsResponse of(final DittoHeaders dittoHeaders) {
        return new ResetHealthEventsResponse(dittoHeaders);
    }

    /**
     * Create a ResetHealthEventsResponse from its JSON representation.
     *
     * @param jsonObject JSON of the response.
     * @param dittoHeaders headers of the response.
     * @return the response.
     */
    public static ResetHealthEventsResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        // Json object is ignored -- this command response has no payload.
        return of(dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {
        // nothing to append
    }

    @Override
    public ResetHealthEventsResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ResetHealthEventsResponse(dittoHeaders);
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
        if (o instanceof ResetHealthEventsResponse) {
            return super.equals(o);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + "]";
    }

}
