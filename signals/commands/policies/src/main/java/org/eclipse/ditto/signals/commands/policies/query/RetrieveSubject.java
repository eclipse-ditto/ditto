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
package org.eclipse.ditto.signals.commands.policies.query;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;

/**
 * Command which retrieves the {@code Subject} based on the passed in Policy ID, Label and Subject ID.
 */
@Immutable
public final class RetrieveSubject extends AbstractCommand<RetrieveSubject>
        implements PolicyQueryCommand<RetrieveSubject> {

    /**
     * Name of the retrieve "Retrieve Subject" command.
     */
    public static final String NAME = "retrieveSubject";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_SUBJECT_ID =
            JsonFactory.newStringFieldDefinition("subjectId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final String policyId;
    private final Label label;
    private final SubjectId subjectId;

    private RetrieveSubject(final String policyId,
            final Label label,
            final SubjectId subjectId,
            final DittoHeaders dittoHeaders) {

        super(TYPE, dittoHeaders);

        this.policyId = checkNotNull(policyId, "Policy identifier");
        this.label = checkNotNull(label, "Label");
        this.subjectId = checkNotNull(subjectId, "Subject identifier");
    }

    /**
     * Returns a command for retrieving the Subject with the given Policy ID, Label and Subject ID.
     *
     * @param policyId the ID of the Policy for which to retrieve the Subject for.
     * @param label the specified label of the Policy entry for which to retrieve the Subject for.
     * @param subjectId the ID of the Subject to retrieve.
     * @param dittoHeaders the optional command headers of the request.
     * @return a Command for retrieving the Subject with the {@code policyId}, {@code label} and {@code subjectId} which
     * is readable from the passed authorization context.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveSubject of(final String policyId,
            final Label label,
            final SubjectId subjectId,
            final DittoHeaders dittoHeaders) {

        return new RetrieveSubject(policyId, label, subjectId, dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveSubject} from a JSON string.
     *
     * @param jsonString the JSON string of which a new RetrieveSubject instance is to be created.
     * @param dittoHeaders the optional command headers of the request.
     * @return the {@code RetrieveSubject} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveSubject fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        final JsonObject jsonObject = JsonFactory.newObject(jsonString);

        return fromJson(jsonObject, dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveSubject} from a JSON object.
     *
     * @param jsonObject the JSON object of which a new RetrieveSubject instance is to be created.
     * @param dittoHeaders the optional command headers of the request.
     * @return the {@code RetrieveSubjects} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveSubject fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<RetrieveSubject>(TYPE, jsonObject).deserialize(() -> {
            final String policyId = jsonObject.getValueOrThrow(PolicyQueryCommand.JsonFields.JSON_POLICY_ID);
            final Label extractedLabel = Label.of(jsonObject.getValueOrThrow(JSON_LABEL));
            final String subjectIdValue = jsonObject.getValueOrThrow(JSON_SUBJECT_ID);
            final SubjectId extractedSubjectId = SubjectId.newInstance(subjectIdValue);

            return of(policyId, extractedLabel, extractedSubjectId, dittoHeaders);
        });
    }

    /**
     * Returns the {@code Label} of the {@code PolicyEntry} for which to retrieve the Subject for.
     *
     * @return the Label of the PolicyEntry for which to retrieve the Subject for.
     */
    public Label getLabel() {
        return label;
    }

    /**
     * Returns the ID of the {@code Subject} to retrieve.
     *
     * @return the ID of the Subject to retrieve.
     */
    public SubjectId getSubjectId() {
        return subjectId;
    }

    /**
     * Returns the identifier of the {@code Policy} for which to retrieve the Subject for.
     *
     * @return the identifier of the Policy for which to retrieve the Subject for.
     */
    @Override
    public String getId() {
        return policyId;
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/entries/" + label + "/subjects/" + subjectId;
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyQueryCommand.JsonFields.JSON_POLICY_ID, policyId, predicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
        jsonObjectBuilder.set(JSON_SUBJECT_ID, subjectId.toString(), predicate);
    }

    @Override
    public RetrieveSubject setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policyId, label, subjectId, dittoHeaders);
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
        final RetrieveSubject that = (RetrieveSubject) obj;
        return that.canEqual(this) && Objects.equals(policyId, that.policyId) && Objects.equals(label, that.label)
                && Objects.equals(subjectId, that.subjectId) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveSubject;
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, label, subjectId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId + ", label=" + label
                + ", subjectId=" + subjectId + "]";
    }

}
