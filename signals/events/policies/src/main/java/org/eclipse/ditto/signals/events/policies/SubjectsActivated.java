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
 * This event is emitted after a {@link org.eclipse.ditto.model.policies.Subject} was activated in each entry.
 */
@Immutable
@JsonParsableEvent(name = SubjectsActivated.NAME, typePrefix = SubjectsActivated.TYPE_PREFIX)
public final class SubjectsActivated extends AbstractPolicyEvent<SubjectsActivated>
        implements PolicyEvent<SubjectsActivated> {

    /**
     * Name of this event
     */
    public static final String NAME = "subjectsActivated";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final Map<Label, Subject> activatedSubjects;

    private SubjectsActivated(
            final PolicyId policyId,
            final Map<Label, Subject> activatedSubjects,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        super(TYPE, checkNotNull(policyId, "policyId"), revision, timestamp, dittoHeaders);
        // Copying and unmodifiable wrapping happen in the factory method.
        // Constructor does not copy in order to share the known unmodifiable field between instances.
        this.activatedSubjects = activatedSubjects;
    }

    private SubjectsActivated(
            final PolicyId policyId,
            final JsonObject activatedSubjects,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        super(TYPE, checkNotNull(policyId, "policyId"), revision, timestamp, dittoHeaders);
        this.activatedSubjects =
                activatedSubjectsFromJson(checkNotNull(activatedSubjects, "activatedSubjects"));
    }

    /**
     * Constructs a new {@code SubjectsActivated} object.
     *
     * @param policyId the policy ID.
     * @param activatedSubjects subjects that are activated indexed by their policy entry labels.
     * @param revision the revision of the Policy.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the created SubjectsActivated.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static SubjectsActivated of(final PolicyId policyId,
            final Map<Label, Subject> activatedSubjects,
            final long revision,
            final DittoHeaders dittoHeaders) {

        return of(policyId, activatedSubjects, revision, null, dittoHeaders);
    }

    /**
     * Constructs a new {@code SubjectsActivated} object.
     *
     * @param policyId the policy ID.
     * @param activatedSubjects subjects that are activated indexed by their policy entry labels.
     * @param revision the revision of the Policy.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the created SubjectsActivated.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static SubjectsActivated of(final PolicyId policyId,
            final Map<Label, Subject> activatedSubjects,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        return new SubjectsActivated(policyId,
                Collections.unmodifiableMap(
                        new HashMap<>(checkNotNull(activatedSubjects, "activatedSubject"))),
                revision, timestamp, dittoHeaders);
    }

    /**
     * Creates a new {@code SubjectsActivated} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new SubjectsActivated instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code SubjectsActivated} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected 'SubjectsActivated' format.
     */
    public static SubjectsActivated fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<SubjectsActivated>(TYPE, jsonObject).deserialize(
                (revision, timestamp, metadata) -> {
                    final String extractedPolicyId = jsonObject.getValueOrThrow(PolicyEvent.JsonFields.POLICY_ID);
                    final PolicyId policyId = PolicyId.of(extractedPolicyId);
                    final JsonObject activatedSubjects = jsonObject.getValueOrThrow(JsonFields.ACTIVATED_SUBJECT);
                    return new SubjectsActivated(policyId, activatedSubjects, revision, timestamp, dittoHeaders);
                });
    }

    /**
     * Returns the activated subjects indexed by the labels of policy entries they belong to.
     *
     * @return the activated subjects.
     */
    public Map<Label, Subject> getActivatedSubjects() {
        return activatedSubjects;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(activatedSubjectsToJson(activatedSubjects));
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public SubjectsActivated setRevision(final long revision) {
        return new SubjectsActivated(getEntityId(), activatedSubjects, revision, getTimestamp().orElse(null),
                getDittoHeaders());
    }

    @Override
    public SubjectsActivated setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new SubjectsActivated(getEntityId(), activatedSubjects, getRevision(), getTimestamp().orElse(null),
                dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JsonFields.ACTIVATED_SUBJECT, activatedSubjectsToJson(activatedSubjects), predicate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), activatedSubjects);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (null == o || getClass() != o.getClass()) {
            return false;
        }
        final SubjectsActivated that = (SubjectsActivated) o;
        return Objects.equals(activatedSubjects, that.activatedSubjects) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SubjectsActivated;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", activatedSubjects=" + activatedSubjects + "]";
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

    private static JsonObject activatedSubjectsToJson(final Map<Label, Subject> activatedSubjects) {
        return activatedSubjects.entrySet()
                .stream()
                .map(entry -> JsonField.newInstance(entry.getKey(), subjectToJsonWithId(entry.getValue())))
                .collect(JsonCollectors.fieldsToObject());
    }

    private static Map<Label, Subject> activatedSubjectsFromJson(final JsonObject jsonObject) {
        final Map<Label, Subject> map = jsonObject.stream()
                .collect(Collectors.toMap(field -> Label.of(field.getKeyName()),
                        field -> subjectFromJsonWithId(field.getValue().asObject())));
        return Collections.unmodifiableMap(map);
    }

    static final class JsonFields {

        static final JsonFieldDefinition<JsonObject> ACTIVATED_SUBJECT =
                JsonFactory.newJsonObjectFieldDefinition("activatedSubjects", FieldType.REGULAR, JsonSchemaVersion.V_2);
    }

}
