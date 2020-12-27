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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonCollectors;
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
 * This event is emitted after a {@link org.eclipse.ditto.model.policies.Subject} was activated in each entry.
 */
@Immutable
@JsonParsableEvent(name = SubjectsDeactivated.NAME, typePrefix = SubjectsDeactivated.TYPE_PREFIX)
public final class SubjectsDeactivated extends AbstractPolicyEvent<SubjectsDeactivated>
        implements PolicyEvent<SubjectsDeactivated> {

    /**
     * Name of this event.
     */
    public static final String NAME = "subjectsDeactivated";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final Map<Label, SubjectId> deactivatedSubjectIds;

    private SubjectsDeactivated(
            final PolicyId policyId,
            final Map<Label, SubjectId> deactivatedSubjectIds,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        super(TYPE, checkNotNull(policyId, "policyId"), revision, timestamp, dittoHeaders);
        // Copying and unmodifiable wrapping happen in the factory method.
        // Constructor does not copy in order to share the known unmodifiable field between instances.
        this.deactivatedSubjectIds = deactivatedSubjectIds;
    }

    private SubjectsDeactivated(
            final PolicyId policyId,
            final JsonObject deactivatedSubjectIds,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        super(TYPE, checkNotNull(policyId, "policyId"), revision, timestamp, dittoHeaders);
        this.deactivatedSubjectIds =
                deactivatedSubjectsFromJson(checkNotNull(deactivatedSubjectIds, "deactivatedSubjectIds"));
    }

    /**
     * Constructs a new {@code SubjectsDeactivated} object.
     *
     * @param policyId the policy ID.
     * @param deactivatedSubjectIds IDs of subjects that are deactivated indexed by their policy entry labels.
     * @param revision the revision of the Policy.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the created SubjectsDeactivated.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static SubjectsDeactivated of(final PolicyId policyId,
            final Map<Label, SubjectId> deactivatedSubjectIds,
            final long revision,
            final DittoHeaders dittoHeaders) {

        return of(policyId, deactivatedSubjectIds, revision, null, dittoHeaders);
    }

    /**
     * Constructs a new {@code SubjectsDeactivated} object.
     *
     * @param policyId the policy ID.
     * @param deactivatedSubjectIds IDs of subjects that are deactivated indexed by their policy entry labels.
     * @param revision the revision of the Policy.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the created SubjectsDeactivated.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static SubjectsDeactivated of(final PolicyId policyId,
            final Map<Label, SubjectId> deactivatedSubjectIds,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        return new SubjectsDeactivated(policyId,
                Collections.unmodifiableMap(
                        new HashMap<>(checkNotNull(deactivatedSubjectIds, "deactivatedSubjectIds"))),
                revision, timestamp, dittoHeaders);
    }

    /**
     * Creates a new {@code SubjectsDeactivated} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new SubjectsDeactivated instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code SubjectsDeactivated} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected 'SubjectsDeactivated' format.
     */
    public static SubjectsDeactivated fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<SubjectsDeactivated>(TYPE, jsonObject).deserialize(
                (revision, timestamp, metadata) -> {
                    final String extractedPolicyId = jsonObject.getValueOrThrow(PolicyEvent.JsonFields.POLICY_ID);
                    final PolicyId policyId = PolicyId.of(extractedPolicyId);
                    final JsonObject activatedSubjects = jsonObject.getValueOrThrow(JsonFields.DEACTIVATED_SUBJECT_IDS);
                    return new SubjectsDeactivated(policyId, activatedSubjects, revision, timestamp, dittoHeaders);
                });
    }

    /**
     * Returns the IDs deactivated subjects indexed by the labels of policy entries they belong to.
     *
     * @return the deactivated subject IDs.
     */
    public Map<Label, SubjectId> getDeactivatedSubjectIds() {
        return deactivatedSubjectIds;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(deactivatedSubjectsToJson(deactivatedSubjectIds));
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public SubjectsDeactivated setRevision(final long revision) {
        return new SubjectsDeactivated(getEntityId(), deactivatedSubjectIds, revision, getTimestamp().orElse(null),
                getDittoHeaders());
    }

    @Override
    public SubjectsDeactivated setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new SubjectsDeactivated(getEntityId(), deactivatedSubjectIds, getRevision(), getTimestamp().orElse(null),
                dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JsonFields.DEACTIVATED_SUBJECT_IDS, deactivatedSubjectsToJson(deactivatedSubjectIds),
                predicate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), deactivatedSubjectIds);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (null == o || getClass() != o.getClass()) {
            return false;
        }
        final SubjectsDeactivated that = (SubjectsDeactivated) o;
        return Objects.equals(deactivatedSubjectIds, that.deactivatedSubjectIds) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SubjectsDeactivated;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", activatedSubjects=" + deactivatedSubjectIds +
                "]";
    }

    private static JsonObject deactivatedSubjectsToJson(final Map<Label, SubjectId> activatedSubjects) {
        return activatedSubjects.entrySet()
                .stream()
                .map(entry -> JsonField.newInstance(entry.getKey(), JsonValue.of(entry.getValue())))
                .collect(JsonCollectors.fieldsToObject());
    }

    private static Map<Label, SubjectId> deactivatedSubjectsFromJson(final JsonObject jsonObject) {
        final Map<Label, SubjectId> map = jsonObject.stream()
                .collect(Collectors.toMap(field -> Label.of(field.getKeyName()),
                        field -> SubjectId.newInstance(field.getValue().asString())));
        return Collections.unmodifiableMap(map);
    }

    static final class JsonFields {

        static final JsonFieldDefinition<JsonObject> DEACTIVATED_SUBJECT_IDS =
                JsonFactory.newJsonObjectFieldDefinition("deactivatedSubjectIds", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);
    }

}
