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
import org.eclipse.ditto.policies.model.ImportsAliasTarget;
import org.eclipse.ditto.policies.model.SubjectId;

/**
 * This event is emitted after a {@link org.eclipse.ditto.policies.model.Subject} was deleted through alias fan-out.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableEvent(name = ImportsAliasSubjectDeleted.NAME, typePrefix = PolicyEvent.TYPE_PREFIX)
public final class ImportsAliasSubjectDeleted extends AbstractPolicyEvent<ImportsAliasSubjectDeleted>
        implements PolicyEvent<ImportsAliasSubjectDeleted> {

    /**
     * Name of this event.
     */
    public static final String NAME = "importsAliasSubjectDeleted";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_ALIAS_LABEL =
            JsonFactory.newStringFieldDefinition("aliasLabel", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_SUBJECT_ID =
            JsonFactory.newStringFieldDefinition("subjectId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonArray> JSON_TARGETS =
            JsonFactory.newJsonArrayFieldDefinition("targets", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final Label aliasLabel;
    private final SubjectId subjectId;
    private final List<ImportsAliasTarget> targets;

    private ImportsAliasSubjectDeleted(final PolicyId policyId,
            final Label aliasLabel,
            final SubjectId subjectId,
            final List<ImportsAliasTarget> targets,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(TYPE, checkNotNull(policyId, "policyId"), revision, timestamp, dittoHeaders, metadata);
        this.aliasLabel = checkNotNull(aliasLabel, "aliasLabel");
        this.subjectId = checkNotNull(subjectId, "subjectId");
        this.targets = Collections.unmodifiableList(new ArrayList<>(checkNotNull(targets, "targets")));
    }

    /**
     * Constructs a new {@code ImportsAliasSubjectDeleted} object.
     *
     * @param policyId the identifier of the Policy from which the subject was deleted.
     * @param aliasLabel the label of the alias through which the subject was deleted.
     * @param subjectId the identifier of the deleted {@link org.eclipse.ditto.policies.model.Subject}.
     * @param targets the list of {@link ImportsAliasTarget}s that were affected.
     * @param revision the revision of the Policy.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param metadata the metadata to apply for the event.
     * @return the created ImportsAliasSubjectDeleted.
     * @throws NullPointerException if any argument but {@code timestamp} and {@code metadata} is {@code null}.
     */
    public static ImportsAliasSubjectDeleted of(final PolicyId policyId,
            final Label aliasLabel,
            final SubjectId subjectId,
            final List<ImportsAliasTarget> targets,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        return new ImportsAliasSubjectDeleted(policyId, aliasLabel, subjectId, targets, revision, timestamp,
                dittoHeaders, metadata);
    }

    /**
     * Creates a new {@code ImportsAliasSubjectDeleted} from a JSON string.
     *
     * @param jsonString the JSON string from which a new ImportsAliasSubjectDeleted instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code ImportsAliasSubjectDeleted} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'ImportsAliasSubjectDeleted' format.
     */
    public static ImportsAliasSubjectDeleted fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code ImportsAliasSubjectDeleted} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new ImportsAliasSubjectDeleted instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code ImportsAliasSubjectDeleted} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'ImportsAliasSubjectDeleted' format.
     */
    public static ImportsAliasSubjectDeleted fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return new EventJsonDeserializer<ImportsAliasSubjectDeleted>(TYPE, jsonObject).deserialize(
                (revision, timestamp, metadata) -> {
                    final String extractedPolicyId = jsonObject.getValueOrThrow(PolicyEvent.JsonFields.POLICY_ID);
                    final PolicyId policyId = PolicyId.of(extractedPolicyId);
                    final Label aliasLabel = Label.of(jsonObject.getValueOrThrow(JSON_ALIAS_LABEL));
                    final SubjectId subjectId =
                            SubjectId.newInstance(jsonObject.getValueOrThrow(JSON_SUBJECT_ID));
                    final JsonArray targetsArray = jsonObject.getValueOrThrow(JSON_TARGETS);
                    final List<ImportsAliasTarget> targets = targetsArray.stream()
                            .filter(JsonValue::isObject)
                            .map(JsonValue::asObject)
                            .map(PoliciesModelFactory::newImportsAliasTarget)
                            .collect(Collectors.toList());

                    return of(policyId, aliasLabel, subjectId, targets, revision, timestamp, dittoHeaders, metadata);
                });
    }

    /**
     * Returns the label of the alias through which the subject was deleted.
     *
     * @return the alias label.
     */
    public Label getAliasLabel() {
        return aliasLabel;
    }

    /**
     * Returns the deleted {@link SubjectId}.
     *
     * @return the deleted subject ID.
     */
    public SubjectId getSubjectId() {
        return subjectId;
    }

    /**
     * Returns the list of {@link ImportsAliasTarget}s that were affected.
     *
     * @return the unmodifiable list of targets.
     */
    public List<ImportsAliasTarget> getTargets() {
        return targets;
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/entries/" + aliasLabel + "/subjects/" + subjectId;
        return JsonPointer.of(path);
    }

    @Override
    public ImportsAliasSubjectDeleted setRevision(final long revision) {
        return of(getPolicyEntityId(), aliasLabel, subjectId, targets, revision, getTimestamp().orElse(null),
                getDittoHeaders(), getMetadata().orElse(null));
    }

    @Override
    public ImportsAliasSubjectDeleted setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getPolicyEntityId(), aliasLabel, subjectId, targets, getRevision(), getTimestamp().orElse(null),
                dittoHeaders, getMetadata().orElse(null));
    }

    @Override
    public ImportsAliasSubjectDeleted setEntity(final JsonValue entity) {
        return this;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_ALIAS_LABEL, aliasLabel.toString(), predicate);
        jsonObjectBuilder.set(JSON_SUBJECT_ID, subjectId.toString(), predicate);
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
        result = prime * result + Objects.hashCode(subjectId);
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
        final ImportsAliasSubjectDeleted that = (ImportsAliasSubjectDeleted) o;
        return that.canEqual(this) &&
                Objects.equals(aliasLabel, that.aliasLabel) &&
                Objects.equals(subjectId, that.subjectId) &&
                Objects.equals(targets, that.targets) &&
                super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ImportsAliasSubjectDeleted;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", aliasLabel=" + aliasLabel +
                ", subjectId=" + subjectId +
                ", targets=" + targets +
                "]";
    }

}
