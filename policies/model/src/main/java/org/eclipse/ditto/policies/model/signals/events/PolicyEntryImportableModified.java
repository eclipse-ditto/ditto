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
import org.eclipse.ditto.policies.model.ImportableType;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PolicyId;


/**
 * This event is emitted after the {@link ImportableType} of a
 * {@link org.eclipse.ditto.policies.model.PolicyEntry} was modified.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableEvent(name = PolicyEntryImportableModified.NAME, typePrefix = PolicyEvent.TYPE_PREFIX)
public final class PolicyEntryImportableModified
        extends AbstractPolicyEvent<PolicyEntryImportableModified>
        implements PolicyEvent<PolicyEntryImportableModified> {

    /**
     * Name of this event.
     */
    public static final String NAME = "policyEntryImportableModified";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_IMPORTABLE =
            JsonFactory.newStringFieldDefinition("importable", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final Label label;
    private final ImportableType importableType;

    private PolicyEntryImportableModified(final PolicyId policyId,
            final Label label,
            final ImportableType importableType,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(TYPE, checkNotNull(policyId, "Policy identifier"), revision, timestamp, dittoHeaders, metadata);
        this.label = checkNotNull(label, "Label");
        this.importableType = checkNotNull(importableType, "ImportableType");
    }

    /**
     * Constructs a new {@code PolicyEntryImportableModified} object.
     *
     * @param policyId the identifier of the Policy to which the modified importable type belongs.
     * @param label the label of the Policy Entry to which the modified importable type belongs.
     * @param importableType the modified {@link ImportableType}.
     * @param revision the revision of the Policy.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param metadata the metadata to apply for the event.
     * @return the created PolicyEntryImportableModified.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static PolicyEntryImportableModified of(final PolicyId policyId,
            final Label label,
            final ImportableType importableType,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        return new PolicyEntryImportableModified(policyId, label, importableType, revision,
                timestamp, dittoHeaders, metadata);
    }

    /**
     * Creates a new {@code PolicyEntryImportableModified} from a JSON string.
     *
     * @param jsonString the JSON string from which a new PolicyEntryImportableModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code PolicyEntryImportableModified} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'PolicyEntryImportableModified' format.
     */
    public static PolicyEntryImportableModified fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {

        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code PolicyEntryImportableModified} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new PolicyEntryImportableModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code PolicyEntryImportableModified} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'PolicyEntryImportableModified' format.
     */
    public static PolicyEntryImportableModified fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return new EventJsonDeserializer<PolicyEntryImportableModified>(TYPE, jsonObject).deserialize(
                (revision, timestamp, metadata) -> {
                    final String extractedPolicyId = jsonObject.getValueOrThrow(PolicyEvent.JsonFields.POLICY_ID);
                    final PolicyId policyId = PolicyId.of(extractedPolicyId);
                    final Label label = Label.of(jsonObject.getValueOrThrow(JSON_LABEL));
                    final String importableString = jsonObject.getValueOrThrow(JSON_IMPORTABLE);
                    final ImportableType extractedImportableType = ImportableType.forName(importableString)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Unknown ImportableType: " + importableString));

                    return of(policyId, label, extractedImportableType, revision, timestamp, dittoHeaders, metadata);
                });
    }

    /**
     * Returns the label of the Policy Entry to which the modified importable type belongs.
     *
     * @return the label.
     */
    public Label getLabel() {
        return label;
    }

    /**
     * Returns the modified {@link ImportableType}.
     *
     * @return the modified ImportableType.
     */
    public ImportableType getImportableType() {
        return importableType;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(JsonValue.of(importableType.getName()));
    }

    @Override
    public PolicyEntryImportableModified setEntity(final JsonValue entity) {
        final String importableString = entity.asString();
        final ImportableType type = ImportableType.forName(importableString)
                .orElseThrow(() -> new IllegalArgumentException("Unknown ImportableType: " + importableString));
        return of(getPolicyEntityId(), label, type, getRevision(), getTimestamp().orElse(null),
                getDittoHeaders(), getMetadata().orElse(null));
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/entries/" + label + "/importable";
        return JsonPointer.of(path);
    }

    @Override
    public PolicyEntryImportableModified setRevision(final long revision) {
        return of(getPolicyEntityId(), label, importableType, revision, getTimestamp().orElse(null),
                getDittoHeaders(), getMetadata().orElse(null));
    }

    @Override
    public PolicyEntryImportableModified setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getPolicyEntityId(), label, importableType, getRevision(), getTimestamp().orElse(null),
                dittoHeaders, getMetadata().orElse(null));
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_LABEL, label.toString(), predicate);
        jsonObjectBuilder.set(JSON_IMPORTABLE, importableType.getName(), predicate);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(label);
        result = prime * result + Objects.hashCode(importableType);
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
        final PolicyEntryImportableModified that = (PolicyEntryImportableModified) o;
        return that.canEqual(this) && Objects.equals(label, that.label)
                && Objects.equals(importableType, that.importableType)
                && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof PolicyEntryImportableModified;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", label=" + label +
                ", importableType=" + importableType + "]";
    }

}
