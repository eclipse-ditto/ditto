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

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableEvent;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.events.EventJsonDeserializer;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Subject;

/**
 * This event is emitted after several {@link org.eclipse.ditto.policies.model.Subject}s of a Policy in multiple policy entries were modified/created.
 *
 * @since 2.0.0
 */
@Immutable
@JsonParsableEvent(name = SubjectsModifiedPartially.NAME, typePrefix = PolicyEvent.TYPE_PREFIX)
public final class SubjectsModifiedPartially extends AbstractPolicyActionEvent<SubjectsModifiedPartially> {

    /**
     * Name of this event
     */
    public static final String NAME = "subjectsModifiedPartially";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    public static final JsonFieldDefinition<JsonObject> JSON_MODIFIED_SUBJECTS =
            JsonFactory.newJsonObjectFieldDefinition("modifiedSubjects", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final Map<Label, Collection<Subject>> modifiedSubjects;

    private SubjectsModifiedPartially(
            final PolicyId policyId,
            final Map<Label, Collection<Subject>> modifiedSubjects,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(TYPE, checkNotNull(policyId, "policyId"), revision, timestamp, dittoHeaders, metadata);
        // Copying and unmodifiable wrapping happen in the factory method.
        // Constructor does not copy in order to share the known unmodifiable field between instances.
        this.modifiedSubjects = modifiedSubjects;
    }

    private SubjectsModifiedPartially(
            final PolicyId policyId,
            final JsonObject modifiedSubjects,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(TYPE, checkNotNull(policyId, "policyId"), revision, timestamp, dittoHeaders, metadata);
        this.modifiedSubjects =
                modifiedSubjectsFromJson(checkNotNull(modifiedSubjects, "modifiedSubjects"));
    }

    /**
     * Constructs a new {@code SubjectsModifiedPartially} object.
     *
     * @param policyId the policy ID.
     * @param activatedSubjects subjects that are modified indexed by their policy entry labels.
     * @param revision the revision of the Policy.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param metadata the metadata to apply for the event.
     * @return the created SubjectsModifiedPartially.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static SubjectsModifiedPartially of(final PolicyId policyId,
            final Map<Label, Collection<Subject>> activatedSubjects,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        return new SubjectsModifiedPartially(policyId,
                Collections.unmodifiableMap(
                        new LinkedHashMap<>(checkNotNull(activatedSubjects, "activatedSubjects"))),
                revision, timestamp, dittoHeaders, metadata);
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
                    return new SubjectsModifiedPartially(policyId, modifiedSubjects, revision, timestamp, dittoHeaders,
                            metadata);
                });
    }

    /**
     * Returns the modified subjects indexed by the labels of policy entries they belong to.
     *
     * @return the modified subjects.
     */
    public Map<Label, Collection<Subject>> getModifiedSubjects() {
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
        return new SubjectsModifiedPartially(getPolicyEntityId(), modifiedSubjects, revision,
                getTimestamp().orElse(null), getDittoHeaders(), getMetadata().orElse(null));
    }

    @Override
    public SubjectsModifiedPartially setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new SubjectsModifiedPartially(getPolicyEntityId(), modifiedSubjects, getRevision(),
                getTimestamp().orElse(null), dittoHeaders, getMetadata().orElse(null));
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_MODIFIED_SUBJECTS, modifiedSubjectsToJson(modifiedSubjects), predicate);
    }

    @Override
    public SubjectsModifiedPartially aggregateWith(final Collection<PolicyActionEvent<?>> otherPolicyActionEvents) {
        return aggregateWithSubjectCreatedOrModified(modifiedSubjects, otherPolicyActionEvents);
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

    private static JsonObject subjectsToJsonWithId(final Collection<Subject> subjects) {
        return subjects.stream()
                .map(subject -> JsonField.newInstance(subject.getId(), subject.toJson()))
                .collect(JsonCollectors.fieldsToObject());
    }

    private static Collection<Subject> subjectsFromJsonWithId(final JsonObject jsonObject) {
        if (jsonObject.getSize() != 1) {
            throw JsonParseException.newBuilder()
                    .message("Unexpected subject with ID format")
                    .build();
        }
        return jsonObject.stream()
                .map(jsonField -> PoliciesModelFactory.newSubject(
                        jsonField.getKeyName(), jsonField.getValue().asObject()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static JsonObject modifiedSubjectsToJson(final Map<Label, Collection<Subject>> modifiedSubjects) {
        return modifiedSubjects.entrySet()
                .stream()
                .map(entry -> JsonField.newInstance(entry.getKey(), subjectsToJsonWithId(entry.getValue())))
                .collect(JsonCollectors.fieldsToObject());
    }

    /**
     * Transform the passed {@code jsonObject} to a map of modified subjects as expected in the payload of this event.
     *
     * @param jsonObject the json object to read the modified subjects from.
     * @return the map.
     */
    public static Map<Label, Collection<Subject>> modifiedSubjectsFromJson(final JsonObject jsonObject) {
        final Map<Label, Collection<Subject>> map = jsonObject.stream()
                .collect(Collectors.toMap(field -> Label.of(field.getKeyName()),
                        field -> subjectsFromJsonWithId(field.getValue().asObject()),
                        (u, v) -> {
                            throw new IllegalStateException(String.format("Duplicate key %s", u));
                        },
                        LinkedHashMap::new));
        return Collections.unmodifiableMap(map);
    }
}
