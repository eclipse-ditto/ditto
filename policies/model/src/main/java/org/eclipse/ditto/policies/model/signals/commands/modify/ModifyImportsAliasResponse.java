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
package org.eclipse.ditto.policies.model.signals.commands.modify;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
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
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ImportsAlias;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;

/**
 * Response to a {@link ModifyImportsAlias} command.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableCommandResponse(type = ModifyImportsAliasResponse.TYPE)
public final class ModifyImportsAliasResponse
        extends AbstractCommandResponse<ModifyImportsAliasResponse>
        implements PolicyModifyCommandResponse<ModifyImportsAliasResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ModifyImportsAlias.NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFieldDefinition.ofString("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonValue> JSON_IMPORTS_ALIAS =
            JsonFieldDefinition.ofJsonValue("importsAlias", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final Set<HttpStatus> HTTP_STATUSES;

    static {
        final Set<HttpStatus> httpStatuses = new HashSet<>();
        Collections.addAll(httpStatuses, HttpStatus.CREATED, HttpStatus.NO_CONTENT);
        HTTP_STATUSES = Collections.unmodifiableSet(httpStatuses);
    }

    private static final CommandResponseJsonDeserializer<ModifyImportsAliasResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();

                        final Label label = Label.of(jsonObject.getValueOrThrow(JSON_LABEL));

                        return newInstance(
                                PolicyId.of(jsonObject.getValueOrThrow(
                                        PolicyCommandResponse.JsonFields.JSON_POLICY_ID)),
                                label,
                                jsonObject.getValue(JSON_IMPORTS_ALIAS)
                                        .map(JsonValue::asObject)
                                        .map(obj -> PoliciesModelFactory.newImportsAlias(label, obj))
                                        .orElse(null),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final PolicyId policyId;
    private final Label label;
    @Nullable private final ImportsAlias importsAlias;

    private ModifyImportsAliasResponse(final PolicyId policyId, final Label label,
            @Nullable final ImportsAlias importsAlias, final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.label = checkNotNull(label, "label");
        this.importsAlias = ConditionChecker.checkArgument(
                importsAlias,
                importsAliasArgument -> {
                    final boolean result;
                    if (HttpStatus.NO_CONTENT.equals(httpStatus)) {
                        result = importsAliasArgument == null;
                    } else {
                        result = importsAliasArgument != null;
                    }
                    return result;
                },
                () -> MessageFormat.format("ImportsAlias <{0}> is illegal in conjunction with <{1}>.",
                        importsAlias,
                        httpStatus));
    }

    /**
     * Creates a response to a {@code ModifyImportsAlias} command for the case when an ImportsAlias was created.
     *
     * @param policyId the Policy ID of the created imports alias.
     * @param importsAliasCreated the ImportsAlias created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyImportsAliasResponse created(final PolicyId policyId,
            final ImportsAlias importsAliasCreated,
            final DittoHeaders dittoHeaders) {

        return newInstance(policyId,
                checkNotNull(importsAliasCreated, "importsAliasCreated").getLabel(),
                importsAliasCreated,
                HttpStatus.CREATED,
                dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyImportsAlias} command for the case when an existing ImportsAlias
     * was modified.
     *
     * @param policyId the Policy ID of the modified imports alias.
     * @param label the Label of the modified ImportsAlias.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyImportsAliasResponse modified(final PolicyId policyId, final Label label,
            final DittoHeaders dittoHeaders) {

        return newInstance(policyId, label, null, HttpStatus.NO_CONTENT, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code ModifyImportsAliasResponse} for the specified arguments.
     *
     * @param policyId the Policy ID of the modified imports alias.
     * @param label the Label of the ImportsAlias.
     * @param importsAlias (optional) the ImportsAlias created.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code ModifyImportsAliasResponse} instance.
     * @throws NullPointerException if any argument but {@code importsAlias} is {@code null}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a
     * {@code ModifyImportsAliasResponse}.
     */
    public static ModifyImportsAliasResponse newInstance(final PolicyId policyId, final Label label,
            @Nullable final ImportsAlias importsAlias, final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new ModifyImportsAliasResponse(policyId,
                label,
                importsAlias,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        HTTP_STATUSES,
                        ModifyImportsAliasResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyImportsAlias} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifyImportsAliasResponse fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {

        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code ModifyImportsAlias} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifyImportsAliasResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    /**
     * Returns the {@code Label} of the {@code ImportsAlias} which was modified.
     *
     * @return the label.
     */
    public Label getLabel() {
        return label;
    }

    /**
     * Returns the created {@code ImportsAlias}.
     *
     * @return the created ImportsAlias.
     */
    public Optional<ImportsAlias> getImportsAliasCreated() {
        return Optional.ofNullable(importsAlias);
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.ofNullable(importsAlias)
                .map(obj -> obj.toJson(schemaVersion, FieldType.notHidden()));
    }

    @Override
    public ModifyImportsAliasResponse setEntity(final JsonValue entity) {
        return newInstance(policyId, label,
                getHttpStatus() == HttpStatus.CREATED ?
                        PoliciesModelFactory.newImportsAlias(label, entity.asObject()) : null,
                getHttpStatus(), getDittoHeaders());
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
        if (null != importsAlias) {
            jsonObjectBuilder.set(JSON_IMPORTS_ALIAS, importsAlias.toJson(schemaVersion, thePredicate), predicate);
        }
    }

    @Override
    public ModifyImportsAliasResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(policyId, label, importsAlias, getHttpStatus(), dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifyImportsAliasResponse;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ModifyImportsAliasResponse that = (ModifyImportsAliasResponse) o;
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
