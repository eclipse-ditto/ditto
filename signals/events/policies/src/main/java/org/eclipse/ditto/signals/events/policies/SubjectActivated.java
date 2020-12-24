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
package org.eclipse.ditto.signals.events.policies;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonParsableEvent;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.signals.events.base.EventJsonDeserializer;

/**
 * This event is emitted after a {@link org.eclipse.ditto.model.policies.Subject} was activated.
 */
@Immutable
@JsonParsableEvent(name = SubjectActivated.NAME, typePrefix = SubjectActivated.TYPE_PREFIX)
public final class SubjectActivated extends AbstractPolicyEvent<SubjectActivated>
        implements PolicyEvent<SubjectActivated> {

    /**
     * Name of this event
     */
    public static final String NAME = "subjectActivated";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final Label label;
    private final Subject subject;

    private SubjectActivated(final PolicyId policyId,
            final Label label,
            final Subject subject,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        super(TYPE, checkNotNull(policyId, "policyId"), revision, timestamp, dittoHeaders);
        this.label = checkNotNull(label, "label");
        this.subject = checkNotNull(subject, "subject");
    }

    /**
     * Constructs a new {@code SubjectActivated} object.
     *
     * @param policyId the policy ID.
     * @param label the label of the Policy Entry to which the activated subject belongs.
     * @param subject the activated {@link org.eclipse.ditto.model.policies.Subject}.
     * @param revision the revision of the Policy.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the created SubjectActivated.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static SubjectActivated of(final PolicyId policyId,
            final Label label,
            final Subject subject,
            final long revision,
            final DittoHeaders dittoHeaders) {

        return of(policyId, label, subject, revision, null, dittoHeaders);
    }

    /**
     * Constructs a new {@code SubjectActivated} object.
     *
     * @param policyId the policy ID.
     * @param label the label of the Policy Entry to which the activated subject belongs.
     * @param subject the activated {@link org.eclipse.ditto.model.policies.Subject}.
     * @param revision the revision of the Policy.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the created SubjectActivated.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static SubjectActivated of(final PolicyId policyId,
            final Label label,
            final Subject subject,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        return new SubjectActivated(policyId, label, subject, revision, timestamp, dittoHeaders);
    }

    /**
     * Creates a new {@code SubjectActivated} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new SubjectActivated instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code SubjectActivated} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected 'SubjectActivated' format.
     */
    public static SubjectActivated fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<SubjectActivated>(TYPE, jsonObject).deserialize(
                (revision, timestamp, metadata) -> {
                    final String extractedPolicyId = jsonObject.getValueOrThrow(PolicyEvent.JsonFields.POLICY_ID);
                    final PolicyId policyId = PolicyId.of(extractedPolicyId);
                    final Label label = Label.of(jsonObject.getValueOrThrow(JsonFields.LABEL));
                    final String subjectId = jsonObject.getValueOrThrow(JsonFields.SUBJECT_ID);
                    final JsonObject subjectJsonObject = jsonObject.getValueOrThrow(JsonFields.SUBJECT);
                    final Subject extractedModifiedSubject =
                            PoliciesModelFactory.newSubject(subjectId, subjectJsonObject);

                    return of(policyId, label, extractedModifiedSubject, revision, timestamp, dittoHeaders);
                });
    }

    /**
     * Returns the label of the Policy Entry to which the activated subject belongs.
     *
     * @return the label
     */
    public Label getLabel() {
        return label;
    }

    /**
     * Returns the activated {@link org.eclipse.ditto.model.policies.Subject}.
     *
     * @return the activated {@link org.eclipse.ditto.model.policies.Subject}.
     */
    public Subject getSubject() {
        return subject;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(subject.toJson(schemaVersion, FieldType.regularOrSpecial()));
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/entries/" + label + "/subjects/" + subject.getId();
        return JsonPointer.of(path);
    }

    @Override
    public SubjectActivated setRevision(final long revision) {
        return of(getPolicyEntityId(), label, subject, revision, getTimestamp().orElse(null), getDittoHeaders());
    }

    @Override
    public SubjectActivated setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getPolicyEntityId(), label, subject, getRevision(), getTimestamp().orElse(null), dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JsonFields.LABEL, label.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.SUBJECT_ID, subject.getId().toString(), predicate);
        jsonObjectBuilder.set(JsonFields.SUBJECT, subject.toJson(schemaVersion, thePredicate), predicate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), label, subject);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (null == o || getClass() != o.getClass()) {
            return false;
        }
        final SubjectActivated that = (SubjectActivated) o;
        return Objects.equals(label, that.label) && Objects.equals(subject, that.subject) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SubjectActivated;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", label=" + label + ", subject=" + subject + "]";
    }

    static final class JsonFields {

        static final JsonFieldDefinition<String> LABEL =
                JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

        static final JsonFieldDefinition<String> SUBJECT_ID =
                JsonFactory.newStringFieldDefinition("subjectId", FieldType.REGULAR, JsonSchemaVersion.V_2);

        static final JsonFieldDefinition<JsonObject> SUBJECT =
                JsonFactory.newJsonObjectFieldDefinition("subject", FieldType.REGULAR, JsonSchemaVersion.V_2);
    }

}
