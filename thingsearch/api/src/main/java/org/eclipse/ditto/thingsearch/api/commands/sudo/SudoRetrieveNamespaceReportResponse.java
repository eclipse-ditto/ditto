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
package org.eclipse.ditto.thingsearch.api.commands.sudo;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseHttpStatusValidator;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.thingsearch.api.SearchNamespaceReportResult;

/**
 * Response to a {@link SudoRetrieveNamespaceReport} containing a {@link org.eclipse.ditto.thingsearch.api.SearchNamespaceReportResult}.
 */
@Immutable
@JsonParsableCommandResponse(type = SudoRetrieveNamespaceReportResponse.TYPE)
public final class SudoRetrieveNamespaceReportResponse
        extends AbstractCommandResponse<SudoRetrieveNamespaceReportResponse>
        implements ThingSearchSudoCommandResponse<SudoRetrieveNamespaceReportResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + SudoRetrieveNamespaceReport.NAME;

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<SudoRetrieveNamespaceReportResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final var jsonObject = context.getJsonObject();
                        final var payload = jsonObject.getValueOrThrow(JsonFields.PAYLOAD);
                        if (!payload.isObject()) {
                            throw new JsonParseException(
                                    MessageFormat.format("Payload JSON value <{0}> is not an object!", payload));
                        }
                        return new SudoRetrieveNamespaceReportResponse(
                                SearchNamespaceReportResult.fromJson(payload.asObject()),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final SearchNamespaceReportResult namespaceReportResult;

    private SudoRetrieveNamespaceReportResponse(final SearchNamespaceReportResult namespaceReportResult,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        SudoRetrieveNamespaceReportResponse.class),
                dittoHeaders);
        this.namespaceReportResult = namespaceReportResult;
    }

    /**
     * Returns a new instance of {@code SudoRetrieveNamespaceReportResponse}.
     *
     * @param namespaceReportResult the SearchNamespaceReportResult.
     * @param dittoHeaders the headers of the ThingCommand which caused this ThingCommandResponse.
     * @return a new retrieve command response object.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static SudoRetrieveNamespaceReportResponse of(final SearchNamespaceReportResult namespaceReportResult,
            final DittoHeaders dittoHeaders) {

        checkNotNull(namespaceReportResult, "namespaceReportResult");

        return new SudoRetrieveNamespaceReportResponse(namespaceReportResult, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Creates a response to a {@code SudoRetrieveNamespaceReportResponse} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the command which caused this response.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static SudoRetrieveNamespaceReportResponse fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {

        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code SudoRetrieveNamespaceReportResponse} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the command which caused this response.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static SudoRetrieveNamespaceReportResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    /**
     * Returns the SearchNamespaceReportResult.
     *
     * @return the SearchNamespaceReportResult.
     */
    public SearchNamespaceReportResult getNamespaceReportResult() {
        return namespaceReportResult;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final var predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JsonFields.PAYLOAD, namespaceReportResult.toJson(schemaVersion, thePredicate), predicate);
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return namespaceReportResult.toJson();
    }

    @Override
    public SudoRetrieveNamespaceReportResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        return of(SearchNamespaceReportResult.fromJson(entity.asObject()), getDittoHeaders());
    }

    @Override
    public SudoRetrieveNamespaceReportResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(namespaceReportResult, dittoHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), namespaceReportResult);
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067"})
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (SudoRetrieveNamespaceReportResponse) o;
        return that.canEqual(this) &&
                Objects.equals(namespaceReportResult, that.namespaceReportResult) &&
                super.equals(that);
    }

    @Override
    protected boolean canEqual(final Object other) {
        return other instanceof SudoRetrieveNamespaceReportResponse;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [namespaceReportResult=" + namespaceReportResult + "]";
    }

}
