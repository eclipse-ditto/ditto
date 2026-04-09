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

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;

/**
 * Command which retrieves the {@link org.eclipse.ditto.policies.model.SubjectAlias} based on the passed in Policy ID
 * and Label.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableCommand(typePrefix = PolicyCommand.TYPE_PREFIX, name = RetrieveSubjectAlias.NAME)
public final class RetrieveSubjectAlias extends AbstractCommand<RetrieveSubjectAlias>
        implements PolicyQueryCommand<RetrieveSubjectAlias> {

    /**
     * Name of the "Retrieve Subject Alias" command.
     */
    public static final String NAME = "retrieveSubjectAlias";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final PolicyId policyId;
    private final Label label;

    private RetrieveSubjectAlias(final PolicyId policyId, final Label label, final DittoHeaders dittoHeaders) {

        super(TYPE, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.label = checkNotNull(label, "label");
    }

    /**
     * Returns a command for retrieving the SubjectAlias with the given Policy ID and Label.
     *
     * @param policyId the ID of the Policy for which to retrieve the SubjectAlias for.
     * @param label the Label of the SubjectAlias to retrieve.
     * @param dittoHeaders the optional command headers of the request.
     * @return a Command for retrieving the SubjectAlias.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveSubjectAlias of(final PolicyId policyId, final Label label,
            final DittoHeaders dittoHeaders) {

        return new RetrieveSubjectAlias(policyId, label, dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveSubjectAlias} from a JSON string.
     *
     * @param jsonString the JSON string of which a new RetrieveSubjectAlias instance is to be created.
     * @param dittoHeaders the optional command headers of the request.
     * @return the {@code RetrieveSubjectAlias} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveSubjectAlias fromJson(final String jsonString, final DittoHeaders dittoHeaders) {

        final JsonObject jsonObject = JsonFactory.newObject(jsonString);
        return fromJson(jsonObject, dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveSubjectAlias} from a JSON object.
     *
     * @param jsonObject the JSON object of which a new RetrieveSubjectAlias instance is to be created.
     * @param dittoHeaders the optional command headers of the request.
     * @return the {@code RetrieveSubjectAlias} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveSubjectAlias fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {

        return new CommandJsonDeserializer<RetrieveSubjectAlias>(TYPE, jsonObject).deserialize(() -> {
            final String extractedPolicyId = jsonObject.getValueOrThrow(PolicyCommand.JsonFields.JSON_POLICY_ID);
            final PolicyId policyId = PolicyId.of(extractedPolicyId);
            final Label label = Label.of(jsonObject.getValueOrThrow(JSON_LABEL));

            return of(policyId, label, dittoHeaders);
        });
    }

    /**
     * Returns the {@code Label} of the {@code SubjectAlias} to retrieve.
     *
     * @return the Label of the SubjectAlias to retrieve.
     */
    public Label getLabel() {
        return label;
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/subjectAliases/" + label);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommand.JsonFields.JSON_POLICY_ID, String.valueOf(policyId), predicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
    }

    @Override
    public RetrieveSubjectAlias setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, label, dittoHeaders);
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067", "OverlyComplexMethod"})
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final RetrieveSubjectAlias that = (RetrieveSubjectAlias) obj;
        return that.canEqual(this) &&
                Objects.equals(policyId, that.policyId) &&
                Objects.equals(label, that.label) &&
                super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveSubjectAlias;
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, label);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId +
                ", label=" + label + "]";
    }

}
