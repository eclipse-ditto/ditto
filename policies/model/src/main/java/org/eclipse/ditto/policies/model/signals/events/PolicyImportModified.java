/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;

/**
 * This event is emitted after a {@link PolicyImport} was modified.
 *
 * @since 3.x.0 TODO ditto#298
 */
@Immutable
@JsonParsableEvent(name = PolicyImportModified.NAME, typePrefix = PolicyEvent.TYPE_PREFIX)
public final class PolicyImportModified extends AbstractPolicyEvent<PolicyImportModified>
        implements PolicyEvent<PolicyImportModified> {

    /**
     * Name of this event.
     */
    public static final String NAME = "policyImportModified";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_IMPORTED_POLICY_ID =
            JsonFactory.newStringFieldDefinition("importedPolicyId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonObject> JSON_POLICY_IMPORT =
            JsonFactory.newJsonObjectFieldDefinition("policyImport", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final PolicyImport policyImport;

    private PolicyImportModified(final PolicyId policyId,
            final PolicyImport policyImport,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(TYPE, checkNotNull(policyId, "policyId"), revision, timestamp, dittoHeaders, metadata);
        this.policyImport = checkNotNull(policyImport, "policyImport");
    }

    /**
     * Constructs a new {@code PolicyImportModified} object indicating the creation of the import.
     *
     * @param policyId the identifier of the Policy to which the modified import belongs
     * @param policyImport the modified {@link PolicyImport}
     * @param revision the revision of the Policy.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param metadata the metadata to apply for the event.
     * @return the created PolicyImportModified.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static PolicyImportModified of(final PolicyId policyId,
            final PolicyImport policyImport,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        return new PolicyImportModified(policyId, policyImport, revision, timestamp, dittoHeaders, metadata);
    }

    /**
     * Creates a new {@code PolicyImportModified} from a JSON string.
     *
     * @param jsonString the JSON string from which a new PolicyImportModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code PolicyImportModified} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected 'PolicyImportModified'
     * format.
     */
    public static PolicyImportModified fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code PolicyImportModified} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new PolicyImportModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code PolicyImportModified} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected 'PolicyImportModified'
     * format.
     */
    public static PolicyImportModified fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<PolicyImportModified>(TYPE, jsonObject)
                .deserialize((revision, timestamp, metadata) -> {
                    final String extractedPolicyId = jsonObject.getValueOrThrow(PolicyEvent.JsonFields.POLICY_ID);
                    final PolicyId policyId = PolicyId.of(extractedPolicyId);
                    final String importedPolicyId = jsonObject.getValueOrThrow(JSON_IMPORTED_POLICY_ID);
                    final JsonObject policyImportJsonObject = jsonObject.getValueOrThrow(JSON_POLICY_IMPORT);
                    final PolicyImport extractedModifiedPolicyImport =
                            PoliciesModelFactory.newPolicyImport(PolicyId.of(importedPolicyId), policyImportJsonObject);

                    return of(policyId, extractedModifiedPolicyImport, revision, timestamp, dittoHeaders, metadata);
                });
    }

    /**
     * Returns the modified {@link PolicyImport}.
     *
     * @return the modified {@link PolicyImport}.
     */
    public PolicyImport getPolicyImport() {
        return policyImport;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(policyImport.toJson(schemaVersion, FieldType.regularOrSpecial()));
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/imports/" + policyImport.getImportedPolicyId();
        return JsonPointer.of(path);
    }

    @Override
    public PolicyImportModified setRevision(final long revision) {
        return of(getPolicyEntityId(), policyImport, revision, getTimestamp().orElse(null), getDittoHeaders(),
                getMetadata().orElse(null));
    }

    @Override
    public PolicyImportModified setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getPolicyEntityId(), policyImport, getRevision(), getTimestamp().orElse(null), dittoHeaders,
                getMetadata().orElse(null));
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_IMPORTED_POLICY_ID, policyImport.getImportedPolicyId().toString(), predicate);
        jsonObjectBuilder.set(JSON_POLICY_IMPORT, policyImport.toJson(schemaVersion, thePredicate), predicate);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(policyImport);
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
        final PolicyImportModified that = (PolicyImportModified) o;
        return that.canEqual(this) &&
                Objects.equals(policyImport, that.policyImport) &&
                super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof PolicyImportModified;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", policyImport=" + policyImport +
                "]";
    }

}