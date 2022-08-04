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
package org.eclipse.ditto.policies.model.signals.events;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableEvent;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.events.EventJsonDeserializer;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Subjects;


/**
 * This event is emitted after {@link org.eclipse.ditto.policies.model.Subjects} were modified.
 */
@Immutable
@JsonParsableEvent(name = SubjectsModified.NAME, typePrefix= PolicyEvent.TYPE_PREFIX)
public final class SubjectsModified extends AbstractPolicyEvent<SubjectsModified>
        implements PolicyEvent<SubjectsModified> {

    /**
     * Name of this event
     */
    public static final String NAME = "subjectsModified";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonObject> JSON_SUBJECTS =
            JsonFactory.newJsonObjectFieldDefinition("subjects", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final Label label;
    private final Subjects subjects;

    private SubjectsModified(final PolicyId policyId,
            final Label label,
            final Subjects subjects,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(TYPE, checkNotNull(policyId, "Policy identifier"), revision, timestamp, dittoHeaders, metadata);
        this.label = checkNotNull(label, "Label");
        this.subjects = checkNotNull(subjects, "Subjects");
    }

    /**
     * Constructs a new {@code SubjectsModified} object.
     *
     * @param policyId the identifier of the Policy to which the modified subjects belongs
     * @param label the label of the Policy Entry to which the modified subjects belongs
     * @param subjects the modified {@link org.eclipse.ditto.policies.model.Subjects}
     * @param revision the revision of the Policy.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param metadata the metadata to apply for the event.
     * @return the created SubjectsModified.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static SubjectsModified of(final PolicyId policyId,
            final Label label,
            final Subjects subjects,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        return new SubjectsModified(policyId, label, subjects, revision, timestamp, dittoHeaders, metadata);
    }

    /**
     * Creates a new {@code SubjectsModified} from a JSON string.
     *
     * @param jsonString the JSON string from which a new SubjectsModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code SubjectsModified} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected 'SubjectsModified'
     * format.
     */
    public static SubjectsModified fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code SubjectsModified} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new SubjectsModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code SubjectsModified} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected 'SubjectsModified'
     * format.
     */
    public static SubjectsModified fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<SubjectsModified>(TYPE, jsonObject).deserialize(
                (revision, timestamp, metadata) -> {
            final String extractedPolicyId = jsonObject.getValueOrThrow(PolicyEvent.JsonFields.POLICY_ID);
            final PolicyId policyId = PolicyId.of(extractedPolicyId);
            final Label label = Label.of(jsonObject.getValueOrThrow(JSON_LABEL));
            final JsonObject subjectsJsonObject = jsonObject.getValueOrThrow(JSON_SUBJECTS);
            final Subjects extractedModifiedSubjects = PoliciesModelFactory.newSubjects(subjectsJsonObject);

            return of(policyId, label, extractedModifiedSubjects, revision, timestamp, dittoHeaders, metadata);
        });
    }

    /**
     * Returns the label of the Policy Entry to which the modified subjects belongs.
     *
     * @return the label
     */
    public Label getLabel() {
        return label;
    }

    /**
     * Returns the modified {@link org.eclipse.ditto.policies.model.Subjects}.
     *
     * @return the modified {@link org.eclipse.ditto.policies.model.Subjects}.
     */
    public Subjects getSubjects() {
        return subjects;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(subjects.toJson(schemaVersion, FieldType.regularOrSpecial()));
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/entries/" + label + "/subjects";
        return JsonPointer.of(path);
    }

    @Override
    public SubjectsModified setRevision(final long revision) {
        return of(getPolicyEntityId(), label, subjects, revision, getTimestamp().orElse(null), getDittoHeaders(),
                getMetadata().orElse(null));
    }

    @Override
    public SubjectsModified setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getPolicyEntityId(), label, subjects, getRevision(), getTimestamp().orElse(null), dittoHeaders,
                getMetadata().orElse(null));
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
        jsonObjectBuilder.set(JSON_SUBJECTS, subjects.toJson(schemaVersion, thePredicate), predicate);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(label);
        result = prime * result + Objects.hashCode(subjects);
        return result;
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (null == o || getClass() != o.getClass()) {
            return false;
        }
        final SubjectsModified that = (SubjectsModified) o;
        return that.canEqual(this) && Objects.equals(label, that.label) && Objects.equals(subjects, that.subjects)
                && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SubjectsModified;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", label=" + label + ", subjects=" + subjects +
                "]";
    }

}
