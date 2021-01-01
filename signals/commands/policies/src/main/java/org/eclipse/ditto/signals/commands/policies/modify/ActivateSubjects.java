/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.commands.policies.modify;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.model.base.json.FieldType.REGULAR;
import static org.eclipse.ditto.model.base.json.JsonSchemaVersion.V_2;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableCommand;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;

/**
 * This command activates a token subject in all authorized policy entries.
 *
 * @since 2.0.0
 */
@Immutable
@JsonParsableCommand(typePrefix = ActivateSubjects.TYPE_PREFIX, name = ActivateSubjects.NAME)
public final class ActivateSubjects extends AbstractCommand<ActivateSubjects>
        implements PolicyActionCommand<ActivateSubjects> {

    /**
     * NAME of this command.
     */
    public static final String NAME = "activateSubjectForPolicy";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final PolicyId policyId;
    private final SubjectId subjectId;
    private final Instant expiry;
    private final List<Label> labels;

    private ActivateSubjects(final PolicyId policyId, final SubjectId subjectId,
            final Instant expiry, final List<Label> labels,
            final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.policyId = checkNotNull(policyId, "policyId");
        this.subjectId = checkNotNull(subjectId, "subjectId");
        this.expiry = checkNotNull(expiry, "expiry");
        // Null check and copying in the factory method in order to share known unmodifiable fields between instances.
        this.labels = labels;
    }

    /**
     * Creates a command for activating a token subject in all authorized policy entries.
     *
     * @param policyId the identifier of the Policy.
     * @param subjectId subject ID to activate.
     * @param expiry when the subject expires.
     * @param labels labels of policy entries in which to inject the subject.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ActivateSubjects of(final PolicyId policyId, final SubjectId subjectId,
            final Instant expiry, final List<Label> labels, final DittoHeaders dittoHeaders) {

        return new ActivateSubjects(policyId, subjectId, expiry,
                Collections.unmodifiableList(new ArrayList<>(checkNotNull(labels, "labels"))),
                dittoHeaders);
    }

    /**
     * Creates a command for activating a token subject in all authorized policy entries from JSON.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ActivateSubjects fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<ActivateSubjects>(TYPE, jsonObject).deserialize(() -> {
            final String extractedPolicyId = jsonObject.getValueOrThrow(PolicyModifyCommand.JsonFields.JSON_POLICY_ID);
            final PolicyId policyId = PolicyId.of(extractedPolicyId);
            final SubjectId subjectId =
                    PoliciesModelFactory.newSubjectId(jsonObject.getValueOrThrow(JsonFields.SUBJECT_ID));
            final Instant expiry =
                    ActivateSubject.parseExpiry(jsonObject.getValueOrThrow(ActivateSubject.JsonFields.EXPIRY));
            final List<Label> labels = Collections.unmodifiableList(
                    jsonObject.getValueOrThrow(JsonFields.LABELS)
                            .stream()
                            .map(JsonValue::asString)
                            .map(Label::of)
                            .collect(Collectors.toList())
            );
            return new ActivateSubjects(policyId, subjectId, expiry, labels, dittoHeaders);
        });
    }

    @Override
    public SubjectId getSubjectId() {
        return subjectId;
    }

    /**
     * Returns the expiry of the subject ID.
     *
     * @return the expiry.
     */
    public Instant getExpiry() {
        return expiry;
    }

    /**
     * Returns the labels of policy entries in which the subject is to be injected.
     *
     * @return the policy entry labels.
     */
    public List<Label> getLabels() {
        return labels;
    }

    @Override
    public PolicyId getEntityId() {
        return policyId;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(PolicyCommand.JsonFields.JSON_POLICY_ID, String.valueOf(policyId), predicate);
        jsonObjectBuilder.set(JsonFields.SUBJECT_ID, subjectId.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.EXPIRY, expiry.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.LABELS,
                labels.stream().map(JsonValue::of).collect(JsonCollectors.valuesToArray()));
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public ActivateSubjects setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ActivateSubjects(policyId, subjectId, expiry, labels, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ActivateSubjects;
    }

    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (null == obj || getClass() != obj.getClass()) {
            return false;
        }
        final ActivateSubjects that = (ActivateSubjects) obj;
        return Objects.equals(policyId, that.policyId) &&
                Objects.equals(subjectId, that.subjectId) &&
                Objects.equals(expiry, that.expiry) &&
                Objects.equals(labels, that.labels) &&
                super.equals(obj);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyId, subjectId, expiry, labels);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", policyId=" + policyId +
                ", subjectId=" + subjectId +
                ", expiry=" + expiry +
                ", labels=" + labels +
                "]";
    }

    static final class JsonFields {

        static final JsonFieldDefinition<String> SUBJECT_ID =
                JsonFactory.newStringFieldDefinition("subjectId", REGULAR, V_2);

        static final JsonFieldDefinition<String> EXPIRY =
                JsonFactory.newStringFieldDefinition("expiry", REGULAR, V_2);

        static final JsonFieldDefinition<JsonArray> LABELS =
                JsonFactory.newJsonArrayFieldDefinition("labels", REGULAR, V_2);
    }

}
