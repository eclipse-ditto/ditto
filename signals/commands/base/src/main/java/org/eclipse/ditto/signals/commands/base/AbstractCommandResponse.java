/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.signals.commands.base;

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Abstract implementation of the {@link CommandResponse} interface.
 *
 * @param <T> the type of the implementing class.
 */
@Immutable
public abstract class AbstractCommandResponse<T extends AbstractCommandResponse> implements CommandResponse<T> {

    private final String responseType;
    private final HttpStatusCode statusCode;
    private final DittoHeaders dittoHeaders;

    /**
     * Constructs a new {@code AbstractCommandResponse} object.
     *
     * @param responseType the type of this response.
     * @param statusCode the HTTP statusCode of this response.
     * @param dittoHeaders the headers of the CommandType which caused this CommandResponseType.
     * @throws NullPointerException if any argument is {@code null}.
     */
    protected AbstractCommandResponse(final String responseType, final HttpStatusCode statusCode,
            final DittoHeaders dittoHeaders) {
        this.responseType = requireNonNull(responseType, "The response type must not be null!");
        this.statusCode = Objects.requireNonNull(statusCode, "The status code must not be null!");
        this.dittoHeaders = requireNonNull(dittoHeaders, "The command headers must not be null!");
    }

    @Override
    public HttpStatusCode getStatusCode() {
        return statusCode;
    }

    @Override
    public String getType() {
        return responseType;
    }

    @Override
    public DittoHeaders getDittoHeaders() {
        return dittoHeaders;
    }

    @Nonnull
    @Override
    public String getManifest() {
        return getType();
    }

    /**
     * Appends the command response specific custom payload to the passed {@code jsonObjectBuilder}.
     *
     * @param jsonObjectBuilder the JsonObjectBuilder to add the custom payload to.
     * @param schemaVersion the JsonSchemaVersion used in toJson().
     * @param predicate the predicate to evaluate when adding the payload.
     */
    protected abstract void appendPayload(JsonObjectBuilder jsonObjectBuilder, JsonSchemaVersion schemaVersion,
            Predicate<JsonField> predicate);

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder()
                .set(JsonFields.TYPE, responseType, predicate)
                .set(JsonFields.STATUS, statusCode.toInt(), predicate);

        appendPayload(jsonObjectBuilder, schemaVersion, thePredicate);

        return jsonObjectBuilder.build();
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067", "OverlyComplexMethod"})
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractCommandResponse that = (AbstractCommandResponse) o;
        return that.canEqual(this) && Objects.equals(dittoHeaders, that.dittoHeaders)
                && Objects.equals(statusCode, that.statusCode)
                && Objects.equals(responseType, that.responseType);
    }

    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof AbstractCommandResponse;
    }

    @Override
    public int hashCode() {
        return Objects.hash(dittoHeaders, statusCode, responseType);
    }

    @Override
    public String toString() {
        return "dittoHeaders=" + dittoHeaders + ", responseType=" + responseType
                + ", statusCode=" + statusCode;
    }

}
