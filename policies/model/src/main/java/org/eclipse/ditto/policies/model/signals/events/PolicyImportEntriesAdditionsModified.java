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
import org.eclipse.ditto.policies.model.EntriesAdditions;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyId;

/**
 * This event is emitted after the {@link EntriesAdditions} of a Policy import were modified.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableEvent(name = PolicyImportEntriesAdditionsModified.NAME, typePrefix = PolicyEvent.TYPE_PREFIX)
public final class PolicyImportEntriesAdditionsModified
        extends AbstractPolicyEvent<PolicyImportEntriesAdditionsModified>
        implements PolicyEvent<PolicyImportEntriesAdditionsModified> {

    /**
     * Name of this event.
     */
    public static final String NAME = "policyImportEntriesAdditionsModified";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_IMPORTED_POLICY_ID =
            JsonFactory.newStringFieldDefinition("importedPolicyId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonObject> JSON_ENTRIES_ADDITIONS =
            JsonFactory.newJsonObjectFieldDefinition("entriesAdditions", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final PolicyId importedPolicyId;
    private final EntriesAdditions entriesAdditions;

    private PolicyImportEntriesAdditionsModified(final PolicyId policyId,
            final PolicyId importedPolicyId,
            final EntriesAdditions entriesAdditions,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(TYPE, checkNotNull(policyId, "policyId"), revision, timestamp, dittoHeaders, metadata);
        this.importedPolicyId = checkNotNull(importedPolicyId, "importedPolicyId");
        this.entriesAdditions = checkNotNull(entriesAdditions, "entriesAdditions");
    }

    /**
     * Constructs a new {@code PolicyImportEntriesAdditionsModified} object indicating the modification of the
     * entries additions.
     *
     * @param policyId the identifier of the Policy to which the modified import belongs.
     * @param importedPolicyId the identifier of the imported Policy.
     * @param entriesAdditions the modified {@link EntriesAdditions}.
     * @param revision the revision of the Policy.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param metadata the metadata to apply for the event.
     * @return the created PolicyImportEntriesAdditionsModified.
     * @throws NullPointerException if any argument but {@code timestamp} and {@code metadata} is {@code null}.
     */
    public static PolicyImportEntriesAdditionsModified of(final PolicyId policyId,
            final PolicyId importedPolicyId,
            final EntriesAdditions entriesAdditions,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        return new PolicyImportEntriesAdditionsModified(policyId, importedPolicyId, entriesAdditions, revision,
                timestamp, dittoHeaders, metadata);
    }

    /**
     * Creates a new {@code PolicyImportEntriesAdditionsModified} from a JSON string.
     *
     * @param jsonString the JSON string from which a new PolicyImportEntriesAdditionsModified instance is to be
     * created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code PolicyImportEntriesAdditionsModified} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'PolicyImportEntriesAdditionsModified' format.
     */
    public static PolicyImportEntriesAdditionsModified fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code PolicyImportEntriesAdditionsModified} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new PolicyImportEntriesAdditionsModified instance is to be
     * created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code PolicyImportEntriesAdditionsModified} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'PolicyImportEntriesAdditionsModified' format.
     */
    public static PolicyImportEntriesAdditionsModified fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<PolicyImportEntriesAdditionsModified>(TYPE, jsonObject)
                .deserialize((revision, timestamp, metadata) -> {
                    final String extractedPolicyId =
                            jsonObject.getValueOrThrow(PolicyEvent.JsonFields.POLICY_ID);
                    final PolicyId policyId = PolicyId.of(extractedPolicyId);
                    final PolicyId importedPolicyId =
                            PolicyId.of(jsonObject.getValueOrThrow(JSON_IMPORTED_POLICY_ID));
                    final JsonObject additionsJsonObject = jsonObject.getValueOrThrow(JSON_ENTRIES_ADDITIONS);
                    final EntriesAdditions entriesAdditions =
                            PoliciesModelFactory.newEntriesAdditions(additionsJsonObject);

                    return of(policyId, importedPolicyId, entriesAdditions, revision, timestamp, dittoHeaders,
                            metadata);
                });
    }

    /**
     * Returns the identifier of the imported Policy.
     *
     * @return the identifier of the imported Policy.
     */
    public PolicyId getImportedPolicyId() {
        return importedPolicyId;
    }

    /**
     * Returns the modified {@link EntriesAdditions}.
     *
     * @return the modified entries additions.
     */
    public EntriesAdditions getEntriesAdditions() {
        return entriesAdditions;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(entriesAdditions.toJson(schemaVersion, FieldType.regularOrSpecial()));
    }

    @Override
    public PolicyImportEntriesAdditionsModified setEntity(final JsonValue entity) {
        return of(getPolicyEntityId(), importedPolicyId,
                PoliciesModelFactory.newEntriesAdditions(entity.asObject()),
                getRevision(), getTimestamp().orElse(null), getDittoHeaders(), getMetadata().orElse(null));
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/imports/" + importedPolicyId + "/entriesAdditions";
        return JsonPointer.of(path);
    }

    @Override
    public PolicyImportEntriesAdditionsModified setRevision(final long revision) {
        return of(getPolicyEntityId(), importedPolicyId, entriesAdditions, revision,
                getTimestamp().orElse(null), getDittoHeaders(), getMetadata().orElse(null));
    }

    @Override
    public PolicyImportEntriesAdditionsModified setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getPolicyEntityId(), importedPolicyId, entriesAdditions, getRevision(),
                getTimestamp().orElse(null), dittoHeaders, getMetadata().orElse(null));
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_IMPORTED_POLICY_ID, importedPolicyId.toString(), predicate);
        jsonObjectBuilder.set(JSON_ENTRIES_ADDITIONS,
                entriesAdditions.toJson(schemaVersion, thePredicate), predicate);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(importedPolicyId);
        result = prime * result + Objects.hashCode(entriesAdditions);
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
        final PolicyImportEntriesAdditionsModified that = (PolicyImportEntriesAdditionsModified) o;
        return that.canEqual(this) &&
                Objects.equals(importedPolicyId, that.importedPolicyId) &&
                Objects.equals(entriesAdditions, that.entriesAdditions) &&
                super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof PolicyImportEntriesAdditionsModified;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", importedPolicyId=" + importedPolicyId +
                ", entriesAdditions=" + entriesAdditions +
                "]";
    }

}
