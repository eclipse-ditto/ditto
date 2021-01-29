/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.commands.base;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.common.HttpStatus;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Abstract implementation of the {@link CommandResponse} interface.
 *
 * @param <T> the type of the implementing class.
 */
@Immutable
public abstract class AbstractCommandResponse<T extends AbstractCommandResponse<T>> implements CommandResponse<T> {

    private final String responseType;
    private final HttpStatus httpStatus;
    private final DittoHeaders dittoHeaders;

    /**
     * Constructs a new {@code AbstractCommandResponse} object.
     *
     * @param responseType the type of this response.
     * @param statusCode the HTTP statusCode of this response.
     * @param dittoHeaders the headers of the CommandType which caused this CommandResponseType.
     * @throws NullPointerException if any argument is {@code null}.
     * @deprecated as of 2.0.0 please use {@link #AbstractCommandResponse(String, HttpStatus, DittoHeaders)} instead.
     */
    @Deprecated
    protected AbstractCommandResponse(final String responseType, final HttpStatusCode statusCode,
            final DittoHeaders dittoHeaders) {

        this.responseType = checkNotNull(responseType, "responseType");
        httpStatus = checkNotNull(statusCode, "statusCode").getAsHttpStatus();
        this.dittoHeaders = ensureNoResponseRequired(checkNotNull(dittoHeaders, "dittoHeaders"));
    }

    /**
     * Constructs a new {@code AbstractCommandResponse} object.
     *
     * @param responseType the type of this response.
     * @param httpStatus the HTTP status of this response.
     * @param dittoHeaders the headers of the CommandType which caused this CommandResponseType.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 2.0.0
     */
    protected AbstractCommandResponse(final String responseType, final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        this.responseType = checkNotNull(responseType, "responseType");
        this.httpStatus = checkNotNull(httpStatus, "httpStatus");
        this.dittoHeaders = ensureNoResponseRequired(checkNotNull(dittoHeaders, "dittoHeaders"));
    }

    private static DittoHeaders ensureNoResponseRequired(final DittoHeaders dittoHeaders) {
        final DittoHeaders result;
        if (dittoHeaders.isResponseRequired()) {
            result = DittoHeaders.newBuilder(dittoHeaders).responseRequired(false).build();
        } else {
            result = dittoHeaders;
        }
        return result;
    }

    @Override
    public HttpStatusCode getStatusCode() {
        return HttpStatusCode.forInt(httpStatus.getCode()).orElseThrow(() -> {

            // This might happen at runtime when httpStatus has a code which is
            // not reflected as constant in HttpStatusCode.
            final String msgPattern = "Found no HttpStatusCode for int <{0}>!";
            return new IllegalStateException(MessageFormat.format(msgPattern, httpStatus.getCode()));
        });
    }

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
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
                .set(JsonFields.STATUS, httpStatus.getCode(), predicate);

        appendPayload(jsonObjectBuilder, schemaVersion, thePredicate);

        return jsonObjectBuilder.build();
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067"})
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
                && Objects.equals(httpStatus, that.httpStatus)
                && Objects.equals(responseType, that.responseType);
    }

    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof AbstractCommandResponse;
    }

    @Override
    public int hashCode() {
        return Objects.hash(dittoHeaders, httpStatus, responseType);
    }

    @Override
    public String toString() {
        return "dittoHeaders=" + dittoHeaders + ", responseType=" + responseType + ", httpStatus=" + httpStatus;
    }

}
