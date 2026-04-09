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
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectAliasTarget;

/**
 * This event is emitted after a single {@link Subject} was created through alias fan-out.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableEvent(name = SubjectAliasSubjectCreated.NAME, typePrefix = PolicyEvent.TYPE_PREFIX)
public final class SubjectAliasSubjectCreated extends AbstractPolicyEvent<SubjectAliasSubjectCreated>
        implements PolicyEvent<SubjectAliasSubjectCreated> {

    /**
     * Name of this event.
     */
    public static final String NAME = "subjectAliasSubjectCreated";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_ALIAS_LABEL =
            JsonFactory.newStringFieldDefinition("aliasLabel", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_SUBJECT_ID =
            JsonFactory.newStringFieldDefinition("subjectId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonObject> JSON_SUBJECT =
            JsonFactory.newJsonObjectFieldDefinition("subject", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonArray> JSON_TARGETS =
            JsonFactory.newJsonArrayFieldDefinition("targets", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final Label aliasLabel;
    private final Subject subject;
    private final List<SubjectAliasTarget> targets;

    private SubjectAliasSubjectCreated(final PolicyId policyId,
            final Label aliasLabel,
            final Subject subject,
            final List<SubjectAliasTarget> targets,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(TYPE, checkNotNull(policyId, "policyId"), revision, timestamp, dittoHeaders, metadata);
        this.aliasLabel = checkNotNull(aliasLabel, "aliasLabel");
        this.subject = checkNotNull(subject, "subject");
        this.targets = Collections.unmodifiableList(new ArrayList<>(checkNotNull(targets, "targets")));
    }

    /**
     * Constructs a new {@code SubjectAliasSubjectCreated} object.
     *
     * @param policyId the identifier of the Policy to which the created subject belongs.
     * @param aliasLabel the label of the alias through which the subject was created.
     * @param subject the created {@link Subject}.
     * @param targets the list of {@link SubjectAliasTarget}s that were affected.
     * @param revision the revision of the Policy.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param metadata the metadata to apply for the event.
     * @return the created SubjectAliasSubjectCreated.
     * @throws NullPointerException if any argument but {@code timestamp} and {@code metadata} is {@code null}.
     */
    public static SubjectAliasSubjectCreated of(final PolicyId policyId,
            final Label aliasLabel,
            final Subject subject,
            final List<SubjectAliasTarget> targets,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        return new SubjectAliasSubjectCreated(policyId, aliasLabel, subject, targets, revision, timestamp,
                dittoHeaders, metadata);
    }

    /**
     * Creates a new {@code SubjectAliasSubjectCreated} from a JSON string.
     *
     * @param jsonString the JSON string from which a new SubjectAliasSubjectCreated instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code SubjectAliasSubjectCreated} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'SubjectAliasSubjectCreated' format.
     */
    public static SubjectAliasSubjectCreated fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code SubjectAliasSubjectCreated} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new SubjectAliasSubjectCreated instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code SubjectAliasSubjectCreated} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'SubjectAliasSubjectCreated' format.
     */
    public static SubjectAliasSubjectCreated fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return new EventJsonDeserializer<SubjectAliasSubjectCreated>(TYPE, jsonObject).deserialize(
                (revision, timestamp, metadata) -> {
                    final String extractedPolicyId = jsonObject.getValueOrThrow(PolicyEvent.JsonFields.POLICY_ID);
                    final PolicyId policyId = PolicyId.of(extractedPolicyId);
                    final Label aliasLabel = Label.of(jsonObject.getValueOrThrow(JSON_ALIAS_LABEL));
                    final String subjectId = jsonObject.getValueOrThrow(JSON_SUBJECT_ID);
                    final JsonObject subjectJsonObject = jsonObject.getValueOrThrow(JSON_SUBJECT);
                    final Subject subject = PoliciesModelFactory.newSubject(subjectId, subjectJsonObject);
                    final JsonArray targetsArray = jsonObject.getValueOrThrow(JSON_TARGETS);
                    final List<SubjectAliasTarget> targets = targetsArray.stream()
                            .filter(JsonValue::isObject)
                            .map(JsonValue::asObject)
                            .map(PoliciesModelFactory::newSubjectAliasTarget)
                            .collect(Collectors.toList());

                    return of(policyId, aliasLabel, subject, targets, revision, timestamp, dittoHeaders, metadata);
                });
    }

    /**
     * Returns the label of the alias through which the subject was created.
     *
     * @return the alias label.
     */
    public Label getAliasLabel() {
        return aliasLabel;
    }

    /**
     * Returns the created {@link Subject}.
     *
     * @return the created subject.
     */
    public Subject getSubject() {
        return subject;
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
        return Optional.of(subject.toJson(schemaVersion, FieldType.regularOrSpecial()));
    }

    @Override
    public SubjectAliasSubjectCreated setEntity(final JsonValue entity) {
        return of(getPolicyEntityId(), aliasLabel,
                PoliciesModelFactory.newSubject(subject.getId().toString(), entity.asObject()), targets,
                getRevision(), getTimestamp().orElse(null), getDittoHeaders(), getMetadata().orElse(null));
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/entries/" + aliasLabel + "/subjects/" + subject.getId();
        return JsonPointer.of(path);
    }

    @Override
    public SubjectAliasSubjectCreated setRevision(final long revision) {
        return of(getPolicyEntityId(), aliasLabel, subject, targets, revision, getTimestamp().orElse(null),
                getDittoHeaders(), getMetadata().orElse(null));
    }

    @Override
    public SubjectAliasSubjectCreated setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getPolicyEntityId(), aliasLabel, subject, targets, getRevision(), getTimestamp().orElse(null),
                dittoHeaders, getMetadata().orElse(null));
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_ALIAS_LABEL, aliasLabel.toString(), predicate);
        jsonObjectBuilder.set(JSON_SUBJECT_ID, subject.getId().toString(), predicate);
        jsonObjectBuilder.set(JSON_SUBJECT, subject.toJson(schemaVersion, thePredicate), predicate);
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
        result = prime * result + Objects.hashCode(subject);
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
        final SubjectAliasSubjectCreated that = (SubjectAliasSubjectCreated) o;
        return that.canEqual(this) &&
                Objects.equals(aliasLabel, that.aliasLabel) &&
                Objects.equals(subject, that.subject) &&
                Objects.equals(targets, that.targets) &&
                super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SubjectAliasSubjectCreated;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", aliasLabel=" + aliasLabel +
                ", subject=" + subject +
                ", targets=" + targets +
                "]";
    }

}
