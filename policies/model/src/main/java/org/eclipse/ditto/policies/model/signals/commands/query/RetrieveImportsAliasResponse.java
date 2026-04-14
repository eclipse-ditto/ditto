/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.model.signals.commands.query;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseHttpStatusValidator;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;

/**
 * Response to a {@link RetrieveImportsAlias} command.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableCommandResponse(type = RetrieveImportsAliasResponse.TYPE)
public final class RetrieveImportsAliasResponse
        extends AbstractCommandResponse<RetrieveImportsAliasResponse>
        implements PolicyQueryCommandResponse<RetrieveImportsAliasResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveImportsAlias.NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFieldDefinition.ofString("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonObject> JSON_IMPORTS_ALIAS =
            JsonFieldDefinition.ofJsonObject("importsAlias", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<RetrieveImportsAliasResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return new RetrieveImportsAliasResponse(
                                PolicyId.of(jsonObject.getValueOrThrow(
                                        PolicyCommandResponse.JsonFields.JSON_POLICY_ID)),
                                Label.of(jsonObject.getValueOrThrow(JSON_LABEL)),
                                jsonObject.getValueOrThrow(JSON_IMPORTS_ALIAS),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final PolicyId policyId;
    private final Label label;
    private final JsonObject importsAlias;

    private RetrieveImportsAliasResponse(final PolicyId policyId, final Label label,
            final JsonObject importsAlias, final HttpStatus httpStatus, final DittoHeaders dittoHeaders) {

        super(TYPE,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        RetrieveImportsAliasResponse.class),
                dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.label = checkNotNull(label, "label");
        this.importsAlias = checkNotNull(importsAlias, "importsAlias");
    }

    /**
     * Creates a response to a {@code RetrieveImportsAlias} command.
     *
     * @param policyId the Policy ID of the retrieved imports alias.
     * @param label the Label of the ImportsAlias.
     * @param importsAlias the retrieved ImportsAlias as JSON object.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveImportsAliasResponse of(final PolicyId policyId, final Label label,
            final JsonObject importsAlias, final DittoHeaders dittoHeaders) {

        return new RetrieveImportsAliasResponse(policyId, label, importsAlias, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrieveImportsAlias} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveImportsAliasResponse fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {

        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrieveImportsAlias} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveImportsAliasResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    /**
     * Returns the {@code Label} of the {@code ImportsAlias} which was retrieved.
     *
     * @return the label.
     */
    public Label getLabel() {
        return label;
    }

    /**
     * Returns the retrieved ImportsAlias as JSON object.
     *
     * @return the retrieved ImportsAlias as JSON object.
     */
    public JsonObject getImportsAlias() {
        return importsAlias;
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return importsAlias;
    }

    @Override
    public RetrieveImportsAliasResponse setEntity(final JsonValue entity) {
        checkNotNull(entity, "entity");
        return of(policyId, label, entity.asObject(), getDittoHeaders());
    }

    @Override
    public RetrieveImportsAliasResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, label, importsAlias, dittoHeaders);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/importsAliases/" + label);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, String.valueOf(policyId), predicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
        jsonObjectBuilder.set(JSON_IMPORTS_ALIAS, importsAlias, predicate);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveImportsAliasResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RetrieveImportsAliasResponse that = (RetrieveImportsAliasResponse) o;
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(label, that.label) &&
                Objects.equals(importsAlias, that.importsAlias) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, label, importsAlias);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId +
                ", label=" + label + ", importsAlias=" + importsAlias + "]";
    }

}
