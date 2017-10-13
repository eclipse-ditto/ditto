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
package org.eclipse.ditto.services.models.thingsearch.commands.sudo;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.services.models.thingsearch.SearchNamespaceReportResult;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to a {@link SudoRetrieveNamespaceReport} containing a {@link SearchNamespaceReportResult}.
 */
@Immutable
public final class SudoRetrieveNamespaceReportResponse extends
        AbstractCommandResponse<SudoRetrieveNamespaceReportResponse>
        implements ThingSearchSudoCommandResponse<SudoRetrieveNamespaceReportResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + SudoRetrieveNamespaceReport.NAME;

    private final SearchNamespaceReportResult namespaceReportResult;

    private SudoRetrieveNamespaceReportResponse(final SearchNamespaceReportResult namespaceReportResult,
            final DittoHeaders dittoHeaders) {
        super(TYPE, HttpStatusCode.OK, dittoHeaders);
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
        checkNotNull(namespaceReportResult, "namespace report result");

        return new SudoRetrieveNamespaceReportResponse(namespaceReportResult, dittoHeaders);
    }

    /**
     * Creates a response to a {@link SudoRetrieveNamespaceReportResponse} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the command which caused this response.
     * @return the response.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected format.
     */
    public static SudoRetrieveNamespaceReportResponse fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link SudoRetrieveNamespaceReportResponse} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the command which caused this response.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected format.
     */
    public static SudoRetrieveNamespaceReportResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<SudoRetrieveNamespaceReportResponse>(TYPE, jsonObject)
                .deserialize((statusCode) -> {
                    final JsonObject namespaceReportJson = jsonObject.getValueOrThrow(JsonFields.PAYLOAD).asObject();
                    final SearchNamespaceReportResult namespaceReportResult =
                            SearchNamespaceReportResult.fromJson(namespaceReportJson);

                    return of(namespaceReportResult, dittoHeaders);
                });
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
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
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
        final SudoRetrieveNamespaceReportResponse that = (SudoRetrieveNamespaceReportResponse) o;
        return that.canEqual(this) && Objects.equals(namespaceReportResult, that.namespaceReportResult) && super
                .equals(that);
    }

    @Override
    protected boolean canEqual(final Object other) {
        return (other instanceof SudoRetrieveNamespaceReportResponse);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [namespaceReportResult=" + namespaceReportResult + "]";
    }

}
