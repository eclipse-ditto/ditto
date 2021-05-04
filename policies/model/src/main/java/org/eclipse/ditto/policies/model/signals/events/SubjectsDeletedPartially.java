/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableEvent;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.base.model.signals.events.EventJsonDeserializer;

/**
 * This event is emitted after several {@link org.eclipse.ditto.policies.model.Subject}s of a Policy
 * in multiple policy entries were deleted.
 *
 * @since 2.0.0
 */
@Immutable
@JsonParsableEvent(name = SubjectsDeletedPartially.NAME, typePrefix = PolicyEvent.TYPE_PREFIX)
public final class SubjectsDeletedPartially extends AbstractPolicyActionEvent<SubjectsDeletedPartially> {

    /**
     * Name of this event.
     */
    public static final String NAME = "subjectsDeletedPartially";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<JsonObject> JSON_DELETED_SUBJECT_IDS =
            JsonFactory.newJsonObjectFieldDefinition("deletedSubjectIds", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);

    private final Map<Label, Collection<SubjectId>> deletedSubjectIds;

    private SubjectsDeletedPartially(
            final PolicyId policyId,
            final Map<Label, Collection<SubjectId>> deletedSubjectIds,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(TYPE, checkNotNull(policyId, "policyId"), revision, timestamp, dittoHeaders, metadata);
        // Copying and unmodifiable wrapping happen in the factory method.
        // Constructor does not copy in order to share the known unmodifiable field between instances.
        this.deletedSubjectIds = deletedSubjectIds;
    }

    private SubjectsDeletedPartially(
            final PolicyId policyId,
            final JsonObject deletedSubjectIds,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(TYPE, checkNotNull(policyId, "policyId"), revision, timestamp, dittoHeaders, metadata);
        this.deletedSubjectIds =
                deletedSubjectsFromJson(checkNotNull(deletedSubjectIds, "deletedSubjectIds"));
    }

    /**
     * Constructs a new {@code SubjectsDeletedPartially} object.
     *
     * @param policyId the policy ID.
     * @param deletedSubjectIds IDs of subjects that are deleted indexed by their policy entry labels.
     * @param revision the revision of the Policy.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param metadata the metadata to apply for the event.
     * @return the created SubjectsDeletedPartially.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static SubjectsDeletedPartially of(final PolicyId policyId,
            final Map<Label, Collection<SubjectId>> deletedSubjectIds,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        return new SubjectsDeletedPartially(policyId,
                Collections.unmodifiableMap(
                        new LinkedHashMap<>(checkNotNull(deletedSubjectIds, "deletedSubjectIds"))),
                revision, timestamp, dittoHeaders, metadata);
    }

    /**
     * Creates a new {@code SubjectsDeletedPartially} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new SubjectsDeletedPartially instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code SubjectsDeletedPartially} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'SubjectsDeletedPartially' format.
     */
    public static SubjectsDeletedPartially fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<SubjectsDeletedPartially>(TYPE, jsonObject).deserialize(
                (revision, timestamp, metadata) -> {
                    final String extractedPolicyId = jsonObject.getValueOrThrow(PolicyEvent.JsonFields.POLICY_ID);
                    final PolicyId policyId = PolicyId.of(extractedPolicyId);
                    final JsonObject activatedSubjects = jsonObject.getValueOrThrow(JSON_DELETED_SUBJECT_IDS);
                    return new SubjectsDeletedPartially(policyId, activatedSubjects, revision, timestamp, dittoHeaders,
                            metadata);
                });
    }

    /**
     * Returns the IDs of deleted subjects indexed by the labels of policy entries they belong to.
     *
     * @return the deleted subject IDs.
     */
    public Map<Label, Collection<SubjectId>> getDeletedSubjectIds() {
        return deletedSubjectIds;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(deletedSubjectsToJson(deletedSubjectIds));
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public SubjectsDeletedPartially setRevision(final long revision) {
        return new SubjectsDeletedPartially(getPolicyEntityId(), deletedSubjectIds, revision,
                getTimestamp().orElse(null), getDittoHeaders(), getMetadata().orElse(null));
    }

    @Override
    public SubjectsDeletedPartially setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new SubjectsDeletedPartially(getPolicyEntityId(), deletedSubjectIds, getRevision(),
                getTimestamp().orElse(null), dittoHeaders, getMetadata().orElse(null));
    }

    @Override
    protected void appendPayloadAndBuild(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_DELETED_SUBJECT_IDS, deletedSubjectsToJson(deletedSubjectIds),
                predicate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), deletedSubjectIds);
    }

    @Override
    public SubjectsDeletedPartially aggregateWith(final Collection<PolicyActionEvent<?>> otherPolicyActionEvents) {
        return aggregateWithSubjectDeleted(deletedSubjectIds, otherPolicyActionEvents);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (null == o || getClass() != o.getClass()) {
            return false;
        }
        final SubjectsDeletedPartially that = (SubjectsDeletedPartially) o;
        return Objects.equals(deletedSubjectIds, that.deletedSubjectIds) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SubjectsDeletedPartially;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", deletedSubjectIds=" + deletedSubjectIds +
                "]";
    }

    private static JsonObject deletedSubjectsToJson(final Map<Label, Collection<SubjectId>> deletedSubjects) {
        return deletedSubjects.entrySet()
                .stream()
                .map(entry -> JsonField.newInstance(entry.getKey(), entry.getValue().stream()
                        .map(SubjectId::toString)
                        .map(JsonValue::of)
                        .collect(JsonCollectors.valuesToArray())))
                .collect(JsonCollectors.fieldsToObject());
    }

    private static Map<Label, Collection<SubjectId>> deletedSubjectsFromJson(final JsonObject jsonObject) {
        final Map<Label, Collection<SubjectId>> map = jsonObject.stream()
                .collect(Collectors.toMap(field -> Label.of(field.getKeyName()),
                        field -> field.getValue().asArray().stream()
                                .map(JsonValue::asString)
                                .map(SubjectId::newInstance)
                                .collect(Collectors.toCollection(LinkedHashSet::new)),
                        (u, v) -> {
                            throw new IllegalStateException(String.format("Duplicate key %s", u));
                        },
                        LinkedHashMap::new));
        return Collections.unmodifiableMap(map);
    }

}
