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
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

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
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.SubjectAlias;

/**
 * This event is emitted after an existing {@link SubjectAlias} was modified.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableEvent(name = SubjectAliasModified.NAME, typePrefix = PolicyEvent.TYPE_PREFIX)
public final class SubjectAliasModified extends AbstractPolicyEvent<SubjectAliasModified>
        implements PolicyEvent<SubjectAliasModified> {

    /**
     * Name of this event.
     */
    public static final String NAME = "subjectAliasModified";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonObject> JSON_SUBJECT_ALIAS =
            JsonFactory.newJsonObjectFieldDefinition("subjectAlias", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final Label label;
    private final SubjectAlias subjectAlias;

    private SubjectAliasModified(final PolicyId policyId,
            final Label label,
            final SubjectAlias subjectAlias,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(TYPE, checkNotNull(policyId, "policyId"), revision, timestamp, dittoHeaders, metadata);
        this.label = checkNotNull(label, "label");
        this.subjectAlias = checkNotNull(subjectAlias, "subjectAlias");
    }

    /**
     * Constructs a new {@code SubjectAliasModified} object.
     *
     * @param policyId the identifier of the Policy to which the modified subject alias belongs.
     * @param label the label of the modified subject alias.
     * @param subjectAlias the modified {@link SubjectAlias}.
     * @param revision the revision of the Policy.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param metadata the metadata to apply for the event.
     * @return the created SubjectAliasModified.
     * @throws NullPointerException if any argument but {@code timestamp} and {@code metadata} is {@code null}.
     */
    public static SubjectAliasModified of(final PolicyId policyId,
            final Label label,
            final SubjectAlias subjectAlias,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        return new SubjectAliasModified(policyId, label, subjectAlias, revision, timestamp, dittoHeaders, metadata);
    }

    /**
     * Creates a new {@code SubjectAliasModified} from a JSON string.
     *
     * @param jsonString the JSON string from which a new SubjectAliasModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code SubjectAliasModified} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'SubjectAliasModified' format.
     */
    public static SubjectAliasModified fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code SubjectAliasModified} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new SubjectAliasModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code SubjectAliasModified} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'SubjectAliasModified' format.
     */
    public static SubjectAliasModified fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<SubjectAliasModified>(TYPE, jsonObject).deserialize(
                (revision, timestamp, metadata) -> {
                    final String extractedPolicyId = jsonObject.getValueOrThrow(PolicyEvent.JsonFields.POLICY_ID);
                    final PolicyId policyId = PolicyId.of(extractedPolicyId);
                    final Label label = Label.of(jsonObject.getValueOrThrow(JSON_LABEL));
                    final JsonObject aliasJsonObject = jsonObject.getValueOrThrow(JSON_SUBJECT_ALIAS);
                    final SubjectAlias subjectAlias =
                            PoliciesModelFactory.newSubjectAlias(label, aliasJsonObject);

                    return of(policyId, label, subjectAlias, revision, timestamp, dittoHeaders, metadata);
                });
    }

    /**
     * Returns the label of the modified subject alias.
     *
     * @return the label.
     */
    public Label getLabel() {
        return label;
    }

    /**
     * Returns the modified {@link SubjectAlias}.
     *
     * @return the modified subject alias.
     */
    public SubjectAlias getSubjectAlias() {
        return subjectAlias;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(subjectAlias.toJson(schemaVersion, FieldType.regularOrSpecial()));
    }

    @Override
    public SubjectAliasModified setEntity(final JsonValue entity) {
        return of(getPolicyEntityId(), label,
                PoliciesModelFactory.newSubjectAlias(label, entity.asObject()),
                getRevision(), getTimestamp().orElse(null), getDittoHeaders(), getMetadata().orElse(null));
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/subjectAliases/" + label;
        return JsonPointer.of(path);
    }

    @Override
    public SubjectAliasModified setRevision(final long revision) {
        return of(getPolicyEntityId(), label, subjectAlias, revision, getTimestamp().orElse(null),
                getDittoHeaders(), getMetadata().orElse(null));
    }

    @Override
    public SubjectAliasModified setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getPolicyEntityId(), label, subjectAlias, getRevision(), getTimestamp().orElse(null),
                dittoHeaders, getMetadata().orElse(null));
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
        jsonObjectBuilder.set(JSON_SUBJECT_ALIAS, subjectAlias.toJson(schemaVersion, thePredicate), predicate);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(label);
        result = prime * result + Objects.hashCode(subjectAlias);
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
        final SubjectAliasModified that = (SubjectAliasModified) o;
        return that.canEqual(this) &&
                Objects.equals(label, that.label) &&
                Objects.equals(subjectAlias, that.subjectAlias) &&
                super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SubjectAliasModified;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", label=" + label +
                ", subjectAlias=" + subjectAlias + "]";
    }

}
