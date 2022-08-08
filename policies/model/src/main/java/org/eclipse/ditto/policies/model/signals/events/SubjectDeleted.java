/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableEvent;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.events.EventJsonDeserializer;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.SubjectId;

/**
 * This event is emitted after a {@link org.eclipse.ditto.policies.model.Subject} was deleted.
 */
@Immutable
@JsonParsableEvent(name = SubjectDeleted.NAME, typePrefix = PolicyEvent.TYPE_PREFIX)
public final class SubjectDeleted extends AbstractPolicyActionEvent<SubjectDeleted> {

    /**
     * Name of this event.
     */
    public static final String NAME = "subjectDeleted";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_SUBJECT_ID =
            JsonFactory.newStringFieldDefinition("subjectId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final Label label;
    private final SubjectId subjectId;

    private SubjectDeleted(final PolicyId policyId,
            final Label label,
            final SubjectId subjectId,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(TYPE, checkNotNull(policyId, "Policy identifier"), revision, timestamp, dittoHeaders, metadata);
        this.label = checkNotNull(label, "Label");
        this.subjectId = checkNotNull(subjectId, "Subject identifier");
    }

    /**
     * Constructs a new {@code SubjectDeleted} object.
     *
     * @param policyId the identifier of the Policy to which the deleted subject belongs.
     * @param label the label of the Policy Entry to which the deleted subject belongs.
     * @param subjectId the identifier of the deleted {@link org.eclipse.ditto.policies.model.Subject}.
     * @param revision the revision of the Policy.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param metadata the metadata to apply for the event.
     * @return the created SubjectDeleted.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static SubjectDeleted of(final PolicyId policyId,
            final Label label,
            final SubjectId subjectId,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        return new SubjectDeleted(policyId, label, subjectId, revision, timestamp, dittoHeaders, metadata);
    }

    /**
     * Creates a new {@code SubjectDeleted} from a JSON string.
     *
     * @param jsonString the JSON string from which a new SubjectDeleted instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code SubjectDeleted} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'SubjectDeleted' format.
     */
    public static SubjectDeleted fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code SubjectDeleted} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new SubjectDeleted instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code SubjectDeleted} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'SubjectDeleted' format.
     */
    public static SubjectDeleted fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<SubjectDeleted>(TYPE, jsonObject).deserialize(
                (revision, timestamp, metadata) -> {
                    final String extractedPolicyId = jsonObject.getValueOrThrow(PolicyEvent.JsonFields.POLICY_ID);
                    final PolicyId policyId = PolicyId.of(extractedPolicyId);
                    final Label label = Label.of(jsonObject.getValueOrThrow(JSON_LABEL));
                    final SubjectId extractedDeletedSubjectId =
                            SubjectId.newInstance(jsonObject.getValueOrThrow(JSON_SUBJECT_ID));

                    return of(policyId, label, extractedDeletedSubjectId, revision, timestamp, dittoHeaders, metadata);
                });
    }

    /**
     * Returns the label of the Policy Entry to which the modified subject belongs.
     *
     * @return the label
     */
    public Label getLabel() {
        return label;
    }

    /**
     * Returns the deleted {@link org.eclipse.ditto.policies.model.SubjectId}.
     *
     * @return the deleted {@link org.eclipse.ditto.policies.model.SubjectId}.
     */
    public SubjectId getSubjectId() {
        return subjectId;
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/entries/" + label + "/subjects/" + subjectId;
        return JsonPointer.of(path);
    }

    @Override
    public SubjectDeleted setRevision(final long revision) {
        return of(getPolicyEntityId(), label, subjectId, revision, getTimestamp().orElse(null), getDittoHeaders(),
                getMetadata().orElse(null));
    }

    @Override
    public SubjectDeleted setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getPolicyEntityId(), label, subjectId, getRevision(), getTimestamp().orElse(null), dittoHeaders,
                getMetadata().orElse(null));
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
        jsonObjectBuilder.set(JSON_SUBJECT_ID, subjectId.toString(), predicate);
    }

    @Override
    public SubjectsDeletedPartially aggregateWith(final Collection<PolicyActionEvent<?>> otherPolicyActionEvents) {
        final Map<Label, Collection<SubjectId>> initialDeletedSubjectId =
                Stream.of(0).collect(Collectors.toMap(i -> label, i -> Collections.singleton(subjectId),
                        (u, v) -> {
                            throw new IllegalStateException(String.format("Duplicate key %s", u));
                        },
                        LinkedHashMap::new));
        return aggregateWithSubjectDeleted(initialDeletedSubjectId, otherPolicyActionEvents);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(label);
        result = prime * result + Objects.hashCode(subjectId);
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
        final SubjectDeleted that = (SubjectDeleted) o;
        return that.canEqual(this) && Objects.equals(label, that.label) && Objects.equals(subjectId, that.subjectId)
                && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SubjectDeleted;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", label=" + label + ", subjectId=" + subjectId
                + "]";
    }
}
