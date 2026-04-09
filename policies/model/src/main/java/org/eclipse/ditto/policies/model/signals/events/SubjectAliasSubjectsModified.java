/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
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
import org.eclipse.ditto.policies.model.SubjectAliasTarget;
import org.eclipse.ditto.policies.model.Subjects;

/**
 * This event is emitted after {@link Subjects} were set through alias fan-out.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableEvent(name = SubjectAliasSubjectsModified.NAME, typePrefix = PolicyEvent.TYPE_PREFIX)
public final class SubjectAliasSubjectsModified extends AbstractPolicyEvent<SubjectAliasSubjectsModified>
        implements PolicyEvent<SubjectAliasSubjectsModified> {

    /**
     * Name of this event.
     */
    public static final String NAME = "subjectAliasSubjectsModified";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_ALIAS_LABEL =
            JsonFactory.newStringFieldDefinition("aliasLabel", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonObject> JSON_SUBJECTS =
            JsonFactory.newJsonObjectFieldDefinition("subjects", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonArray> JSON_TARGETS =
            JsonFactory.newJsonArrayFieldDefinition("targets", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final Label aliasLabel;
    private final Subjects subjects;
    private final List<SubjectAliasTarget> targets;

    private SubjectAliasSubjectsModified(final PolicyId policyId,
            final Label aliasLabel,
            final Subjects subjects,
            final List<SubjectAliasTarget> targets,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(TYPE, checkNotNull(policyId, "policyId"), revision, timestamp, dittoHeaders, metadata);
        this.aliasLabel = checkNotNull(aliasLabel, "aliasLabel");
        this.subjects = checkNotNull(subjects, "subjects");
        this.targets = Collections.unmodifiableList(new ArrayList<>(checkNotNull(targets, "targets")));
    }

    /**
     * Constructs a new {@code SubjectAliasSubjectsModified} object.
     *
     * @param policyId the identifier of the Policy to which the modified subjects belong.
     * @param aliasLabel the label of the alias through which the subjects were modified.
     * @param subjects the modified {@link Subjects}.
     * @param targets the list of {@link SubjectAliasTarget}s that were affected.
     * @param revision the revision of the Policy.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param metadata the metadata to apply for the event.
     * @return the created SubjectAliasSubjectsModified.
     * @throws NullPointerException if any argument but {@code timestamp} and {@code metadata} is {@code null}.
     */
    public static SubjectAliasSubjectsModified of(final PolicyId policyId,
            final Label aliasLabel,
            final Subjects subjects,
            final List<SubjectAliasTarget> targets,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        return new SubjectAliasSubjectsModified(policyId, aliasLabel, subjects, targets, revision, timestamp,
                dittoHeaders, metadata);
    }

    /**
     * Creates a new {@code SubjectAliasSubjectsModified} from a JSON string.
     *
     * @param jsonString the JSON string from which a new SubjectAliasSubjectsModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code SubjectAliasSubjectsModified} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'SubjectAliasSubjectsModified' format.
     */
    public static SubjectAliasSubjectsModified fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code SubjectAliasSubjectsModified} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new SubjectAliasSubjectsModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code SubjectAliasSubjectsModified} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'SubjectAliasSubjectsModified' format.
     */
    public static SubjectAliasSubjectsModified fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return new EventJsonDeserializer<SubjectAliasSubjectsModified>(TYPE, jsonObject).deserialize(
                (revision, timestamp, metadata) -> {
                    final String extractedPolicyId = jsonObject.getValueOrThrow(PolicyEvent.JsonFields.POLICY_ID);
                    final PolicyId policyId = PolicyId.of(extractedPolicyId);
                    final Label aliasLabel = Label.of(jsonObject.getValueOrThrow(JSON_ALIAS_LABEL));
                    final JsonObject subjectsJsonObject = jsonObject.getValueOrThrow(JSON_SUBJECTS);
                    final Subjects subjects = PoliciesModelFactory.newSubjects(subjectsJsonObject);
                    final JsonArray targetsArray = jsonObject.getValueOrThrow(JSON_TARGETS);
                    final List<SubjectAliasTarget> targets = targetsArray.stream()
                            .filter(JsonValue::isObject)
                            .map(JsonValue::asObject)
                            .map(PoliciesModelFactory::newSubjectAliasTarget)
                            .collect(Collectors.toList());

                    return of(policyId, aliasLabel, subjects, targets, revision, timestamp, dittoHeaders, metadata);
                });
    }

    /**
     * Returns the label of the alias through which the subjects were modified.
     *
     * @return the alias label.
     */
    public Label getAliasLabel() {
        return aliasLabel;
    }

    /**
     * Returns the modified {@link Subjects}.
     *
     * @return the modified subjects.
     */
    public Subjects getSubjects() {
        return subjects;
    }

    /**
     * Returns the list of {@link SubjectAliasTarget}s that were affected.
     *
     * @return the unmodifiable list of targets.
     */
    public List<SubjectAliasTarget> getTargets() {
        return targets;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(subjects.toJson(schemaVersion, FieldType.regularOrSpecial()));
    }

    @Override
    public SubjectAliasSubjectsModified setEntity(final JsonValue entity) {
        return of(getPolicyEntityId(), aliasLabel, PoliciesModelFactory.newSubjects(entity.asObject()), targets,
                getRevision(), getTimestamp().orElse(null), getDittoHeaders(), getMetadata().orElse(null));
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/entries/" + aliasLabel + "/subjects";
        return JsonPointer.of(path);
    }

    @Override
    public SubjectAliasSubjectsModified setRevision(final long revision) {
        return of(getPolicyEntityId(), aliasLabel, subjects, targets, revision, getTimestamp().orElse(null),
                getDittoHeaders(), getMetadata().orElse(null));
    }

    @Override
    public SubjectAliasSubjectsModified setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getPolicyEntityId(), aliasLabel, subjects, targets, getRevision(), getTimestamp().orElse(null),
                dittoHeaders, getMetadata().orElse(null));
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_ALIAS_LABEL, aliasLabel.toString(), predicate);
        jsonObjectBuilder.set(JSON_SUBJECTS, subjects.toJson(schemaVersion, thePredicate), predicate);
        final JsonArray targetsArray = targets.stream()
                .map(target -> target.toJson(schemaVersion, thePredicate))
                .collect(JsonCollectors.valuesToArray());
        jsonObjectBuilder.set(JSON_TARGETS, targetsArray, predicate);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(aliasLabel);
        result = prime * result + Objects.hashCode(subjects);
        result = prime * result + Objects.hashCode(targets);
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
        final SubjectAliasSubjectsModified that = (SubjectAliasSubjectsModified) o;
        return that.canEqual(this) &&
                Objects.equals(aliasLabel, that.aliasLabel) &&
                Objects.equals(subjects, that.subjects) &&
                Objects.equals(targets, that.targets) &&
                super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SubjectAliasSubjectsModified;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", aliasLabel=" + aliasLabel +
                ", subjects=" + subjects +
                ", targets=" + targets +
                "]";
    }

}
