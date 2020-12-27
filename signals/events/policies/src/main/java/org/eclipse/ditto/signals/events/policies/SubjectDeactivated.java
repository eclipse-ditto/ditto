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
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.signals.events.base.EventJsonDeserializer;

/**
 * This event is emitted after a {@link org.eclipse.ditto.model.policies.Subject} was deactivated.
 */
@Immutable
@JsonParsableEvent(name = SubjectDeactivated.NAME, typePrefix = SubjectDeactivated.TYPE_PREFIX)
public final class SubjectDeactivated extends AbstractPolicyEvent<SubjectDeactivated>
        implements PolicyEvent<SubjectDeactivated> {

    /**
     * Name of this event
     */
    public static final String NAME = "subjectDeactivated";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final Label label;
    private final SubjectId subjectId;

    private SubjectDeactivated(final PolicyId policyId,
            final Label label,
            final SubjectId subjectId,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        super(TYPE, checkNotNull(policyId, "policyId"), revision, timestamp, dittoHeaders);
        this.label = checkNotNull(label, "label");
        this.subjectId = checkNotNull(subjectId, "subjectId");
    }

    /**
     * Constructs a new {@code SubjectDeactivated} object.
     *
     * @param policyId the policy ID.
     * @param label the label of the Policy Entry to which the activated subject belongs.
     * @param subjectId ID of the deactivated subject.
     * @param revision the revision of the Policy.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the created SubjectDeactivated.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static SubjectDeactivated of(final PolicyId policyId,
            final Label label,
            final SubjectId subjectId,
            final long revision,
            final DittoHeaders dittoHeaders) {

        return of(policyId, label, subjectId, revision, null, dittoHeaders);
    }

    /**
     * Constructs a new {@code SubjectDeactivated} object.
     *
     * @param policyId the policy ID.
     * @param label the label of the Policy Entry to which the activated subject belongs.
     * @param subjectId ID of the deactivated subject.
     * @param revision the revision of the Policy.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the created SubjectDeactivated.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static SubjectDeactivated of(final PolicyId policyId,
            final Label label,
            final SubjectId subjectId,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        return new SubjectDeactivated(policyId, label, subjectId, revision, timestamp, dittoHeaders);
    }

    /**
     * Creates a new {@code SubjectDeactivated} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new SubjectDeactivated instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code SubjectDeactivated} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected 'SubjectDeactivated' format.
     */
    public static SubjectDeactivated fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<SubjectDeactivated>(TYPE, jsonObject)
                .deserialize((revision, timestamp, metadata) -> {
                    final String extractedPolicyId = jsonObject.getValueOrThrow(PolicyEvent.JsonFields.POLICY_ID);
                    final PolicyId policyId = PolicyId.of(extractedPolicyId);
                    final Label label = Label.of(jsonObject.getValueOrThrow(JsonFields.LABEL));
                    final SubjectId subjectId =
                            SubjectId.newInstance(jsonObject.getValueOrThrow(JsonFields.SUBJECT_ID));
                    return of(policyId, label, subjectId, revision, timestamp, dittoHeaders);
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
    public SubjectId getSubjectId() {
        return subjectId;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(JsonValue.of(subjectId));
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/entries/" + label + "/subjects/" + subjectId;
        return JsonPointer.of(path);
    }

    @Override
    public SubjectDeactivated setRevision(final long revision) {
        return of(getPolicyEntityId(), label, subjectId, revision, getTimestamp().orElse(null), getDittoHeaders());
    }

    @Override
    public SubjectDeactivated setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getPolicyEntityId(), label, subjectId, getRevision(), getTimestamp().orElse(null), dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JsonFields.LABEL, label.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.SUBJECT_ID, subjectId.toString(), predicate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), label, subjectId);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (null == o || getClass() != o.getClass()) {
            return false;
        }
        final SubjectDeactivated that = (SubjectDeactivated) o;
        return Objects.equals(label, that.label) && Objects.equals(subjectId, that.subjectId) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SubjectDeactivated;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", label=" + label + ", subjectId=" + subjectId +
                "]";
    }

    static final class JsonFields {

        static final JsonFieldDefinition<String> LABEL =
                JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

        static final JsonFieldDefinition<String> SUBJECT_ID =
                JsonFactory.newStringFieldDefinition("subjectId", FieldType.REGULAR, JsonSchemaVersion.V_2);
    }

}
