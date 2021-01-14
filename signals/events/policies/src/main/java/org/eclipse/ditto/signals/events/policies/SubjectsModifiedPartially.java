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
import java.util.Collection;
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
import org.eclipse.ditto.json.JsonParseException;
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
 * This event is emitted after several {@link org.eclipse.ditto.model.policies.Subject}s of a Policy
 * in multiple policy entries were modified/created.
 *
 * @since 2.0.0
 */
@Immutable
@JsonParsableEvent(name = SubjectsModifiedPartially.NAME, typePrefix = SubjectsModifiedPartially.TYPE_PREFIX)
public final class SubjectsModifiedPartially extends AbstractPolicyActionEvent<SubjectsModifiedPartially> {

    /**
     * Name of this event
     */
    public static final String NAME = "subjectsModifiedPartially";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<JsonObject> JSON_MODIFIED_SUBJECTS =
            JsonFactory.newJsonObjectFieldDefinition("modifiedSubjects", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final Map<Label, Subject> modifiedSubjects;

    private SubjectsModifiedPartially(
            final PolicyId policyId,
            final Map<Label, Subject> modifiedSubjects,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        super(TYPE, checkNotNull(policyId, "policyId"), revision, timestamp, dittoHeaders);
        // Copying and unmodifiable wrapping happen in the factory method.
        // Constructor does not copy in order to share the known unmodifiable field between instances.
        this.modifiedSubjects = modifiedSubjects;
    }

    private SubjectsModifiedPartially(
            final PolicyId policyId,
            final JsonObject modifiedSubjects,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        super(TYPE, checkNotNull(policyId, "policyId"), revision, timestamp, dittoHeaders);
        this.modifiedSubjects =
                modifiedSubjectsFromJson(checkNotNull(modifiedSubjects, "modifiedSubjects"));
    }

    /**
     * Constructs a new {@code SubjectsModifiedPartially} object.
     *
     * @param policyId the policy ID.
     * @param activatedSubjects subjects that are modified indexed by their policy entry labels.
     * @param revision the revision of the Policy.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the created SubjectsModifiedPartially.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static SubjectsModifiedPartially of(final PolicyId policyId,
            final Map<Label, Subject> activatedSubjects,
            final long revision,
            final DittoHeaders dittoHeaders) {

        return of(policyId, activatedSubjects, revision, null, dittoHeaders);
    }

    /**
     * Constructs a new {@code SubjectsModifiedPartially} object.
     *
     * @param policyId the policy ID.
     * @param activatedSubjects subjects that are modified indexed by their policy entry labels.
     * @param revision the revision of the Policy.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the created SubjectsModifiedPartially.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static SubjectsModifiedPartially of(final PolicyId policyId,
            final Map<Label, Subject> activatedSubjects,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        return new SubjectsModifiedPartially(policyId,
                Collections.unmodifiableMap(
                        new HashMap<>(checkNotNull(activatedSubjects, "activatedSubjects"))),
                revision, timestamp, dittoHeaders);
    }

    /**
     * Creates a new {@code SubjectsModifiedPartially} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new SubjectsModifiedPartially instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code SubjectsModifiedPartially} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'SubjectsModifiedPartially' format.
     */
    public static SubjectsModifiedPartially fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<SubjectsModifiedPartially>(TYPE, jsonObject).deserialize(
                (revision, timestamp, metadata) -> {
                    final String extractedPolicyId = jsonObject.getValueOrThrow(PolicyEvent.JsonFields.POLICY_ID);
                    final PolicyId policyId = PolicyId.of(extractedPolicyId);
                    final JsonObject modifiedSubjects = jsonObject.getValueOrThrow(JSON_MODIFIED_SUBJECTS);
                    return new SubjectsModifiedPartially(policyId, modifiedSubjects, revision, timestamp, dittoHeaders);
                });
    }

    /**
     * Returns the modified subjects indexed by the labels of policy entries they belong to.
     *
     * @return the modified subjects.
     */
    public Map<Label, Subject> getModifiedSubjects() {
        return modifiedSubjects;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(modifiedSubjectsToJson(modifiedSubjects));
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public SubjectsModifiedPartially setRevision(final long revision) {
        return new SubjectsModifiedPartially(getEntityId(), modifiedSubjects, revision, getTimestamp().orElse(null),
                getDittoHeaders());
    }

    @Override
    public SubjectsModifiedPartially setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new SubjectsModifiedPartially(getEntityId(), modifiedSubjects, getRevision(),
                getTimestamp().orElse(null),
                dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_MODIFIED_SUBJECTS, modifiedSubjectsToJson(modifiedSubjects), predicate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), modifiedSubjects);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (null == o || getClass() != o.getClass()) {
            return false;
        }
        final SubjectsModifiedPartially that = (SubjectsModifiedPartially) o;
        return Objects.equals(modifiedSubjects, that.modifiedSubjects) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SubjectsModifiedPartially;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", modifiedSubjects=" + modifiedSubjects +
                "]";
    }

    private static JsonObject subjectToJsonWithId(final Subject subject) {
        return JsonObject.newBuilder()
                .set(subject.getId(), subject.toJson())
                .build();
    }

    private static Subject subjectFromJsonWithId(final JsonObject jsonObject) {
        if (jsonObject.getSize() != 1) {
            throw JsonParseException.newBuilder()
                    .message("Unexpected subject with ID format")
                    .build();
        }
        final JsonField jsonField = jsonObject.iterator().next();
        return PoliciesModelFactory.newSubject(jsonField.getKeyName(), jsonField.getValue().asObject());
    }

    private static JsonObject modifiedSubjectsToJson(final Map<Label, Subject> modifiedSubjects) {
        return modifiedSubjects.entrySet()
                .stream()
                .map(entry -> JsonField.newInstance(entry.getKey(), subjectToJsonWithId(entry.getValue())))
                .collect(JsonCollectors.fieldsToObject());
    }

    private static Map<Label, Subject> modifiedSubjectsFromJson(final JsonObject jsonObject) {
        final Map<Label, Subject> map = jsonObject.stream()
                .collect(Collectors.toMap(field -> Label.of(field.getKeyName()),
                        field -> subjectFromJsonWithId(field.getValue().asObject())));
        return Collections.unmodifiableMap(map);
    }

    @Override
    public SubjectsModifiedPartially aggregateWith(final Collection<PolicyActionEvent<?>> otherPolicyActionEvents) {
        return aggregateWithSubjectCreatedOrModified(modifiedSubjects, otherPolicyActionEvents);
    }
}
