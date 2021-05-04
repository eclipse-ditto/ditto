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
package org.eclipse.ditto.policies.model.signals.announcements;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableAnnouncement;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.SubjectId;

/**
 * Announcement that some subjects of a policy are deleted or about to be deleted.
 *
 * @since 2.0.0
 */
@Immutable
@JsonParsableAnnouncement(type = SubjectDeletionAnnouncement.TYPE)
public final class SubjectDeletionAnnouncement extends AbstractPolicyAnnouncement<SubjectDeletionAnnouncement> {

    private static final String NAME = "subjectDeletion";

    /**
     * Type of this announcement.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final Instant deleteAt;
    private final Set<SubjectId> subjectIds;

    private SubjectDeletionAnnouncement(final PolicyId policyId, final Instant deleteAt,
            final Collection<SubjectId> subjectIds,
            final DittoHeaders dittoHeaders) {
        super(policyId, dittoHeaders);
        this.deleteAt = checkNotNull(deleteAt, "deleteAt");
        this.subjectIds =
                Collections.unmodifiableSet(new LinkedHashSet<>(checkNotNull(subjectIds, "subjectIds")));
    }

    /**
     * Create a announcement for subject deletion.
     *
     * @param policyId the policy ID.
     * @param deleteAt when the subjects will be deleted.
     * @param subjectIds what subjects will be deleted.
     * @param dittoHeaders headers of the announcement.
     * @return the announcement.
     */
    public static SubjectDeletionAnnouncement of(final PolicyId policyId, final Instant deleteAt,
            final Collection<SubjectId> subjectIds, final DittoHeaders dittoHeaders) {

        return new SubjectDeletionAnnouncement(policyId, deleteAt, subjectIds, dittoHeaders);
    }

    /**
     * Deserialize a subject-deletion announcement from JSON.
     *
     * @param jsonObject the serialized JSON.
     * @param dittoHeaders the Ditto headers.
     * @return the deserialized {@code SubjectDeletionAnnouncement}.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'SubjectDeletionAnnouncement' format.
     */
    public static SubjectDeletionAnnouncement fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final PolicyId policyId = PolicyId.of(jsonObject.getValueOrThrow(PolicyAnnouncement.JsonFields.JSON_POLICY_ID));
        final Instant deleteAt = parseInstant(jsonObject.getValueOrThrow(JsonFields.DELETE_AT));
        final Collection<SubjectId> subjectIds = jsonObject.getValueOrThrow(
                JsonFields.SUBJECT_IDS)
                .stream()
                .map(value -> SubjectId.newInstance(value.asString()))
                .collect(Collectors.toList());
        return of(policyId, deleteAt, subjectIds, dittoHeaders);
    }

    @Override
    public SubjectDeletionAnnouncement setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new SubjectDeletionAnnouncement(getEntityId(), deleteAt, subjectIds, dittoHeaders);
    }

    @Override
    protected void appendPolicyAnnouncementPayload(final JsonObjectBuilder jsonObjectBuilder,
            final Predicate<JsonField> predicate) {

        jsonObjectBuilder.set(JsonFields.DELETE_AT, deleteAt.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.SUBJECT_IDS, toArray(subjectIds), predicate);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public String getResourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Get the timestamp where the subjects will be deleted.
     *
     * @return the subject deletion timestamp.
     */
    public Instant getDeleteAt() {
        return deleteAt;
    }

    /**
     * Get the IDs of the subjects that will be deleted.
     *
     * @return the subject IDs to be deleted.
     */
    public Set<SubjectId> getSubjectIds() {
        return subjectIds;
    }

    private static JsonArray toArray(final Collection<SubjectId> collection) {
        return collection.stream().map(JsonValue::of).collect(JsonCollectors.valuesToArray());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() +
                ", deleteAt=" + deleteAt +
                ", subjectIds=" + subjectIds +
                "]";
    }

    @Override
    public boolean equals(final Object other) {
        if (super.equals(other)) {
            final SubjectDeletionAnnouncement that = (SubjectDeletionAnnouncement) other;
            return Objects.equals(deleteAt, that.deleteAt) && Objects.equals(subjectIds, that.subjectIds);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(deleteAt, subjectIds, super.hashCode());
    }

    private static Instant parseInstant(final String deleteAt) {
        try {
            return Instant.parse(deleteAt);
        } catch (final DateTimeParseException e) {
            throw JsonParseException.newBuilder()
                    .message(String.format("Deletion timestamp '%s' is not valid. " +
                            "It must be provided as ISO-8601 formatted char sequence.", deleteAt))
                    .build();
        }
    }

    /**
     * JSON fields of this announcement's payload for use in the Ditto protocol.
     */
    public static final class JsonFields {

        /**
         * JSON field for the timestamp when the subjects will be deleted.
         */
        public static final JsonFieldDefinition<String> DELETE_AT =
                JsonFactory.newStringFieldDefinition("deleteAt", JsonSchemaVersion.V_2, FieldType.REGULAR);

        /**
         * JSON field for the subjects that will will be deleted.
         */
        public static final JsonFieldDefinition<JsonArray> SUBJECT_IDS =
                JsonFactory.newJsonArrayFieldDefinition("subjectIds", JsonSchemaVersion.V_2, FieldType.REGULAR);

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
