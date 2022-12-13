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
 * This event is emitted after a {@link PolicyImport} was created.
 *
 * @since 3.1.0
 */
@Immutable
@JsonParsableEvent(name = PolicyImportCreated.NAME, typePrefix= PolicyEvent.TYPE_PREFIX)
public final class PolicyImportCreated extends AbstractPolicyEvent<PolicyImportCreated>
        implements PolicyEvent<PolicyImportCreated> {

    /**
     * Name of this event.
     */
    public static final String NAME = "policyImportCreated";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    public static final JsonFieldDefinition<JsonObject> JSON_POLICY_IMPORT =
            JsonFactory.newJsonObjectFieldDefinition("policyImport", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_IMPORTED_POLICY_ID =
            JsonFactory.newStringFieldDefinition("importedPolicyId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final PolicyImport policyImport;

    private PolicyImportCreated(final PolicyId policyId,
            final PolicyImport policyImport,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(TYPE, checkNotNull(policyId, "policyId"), revision, timestamp, dittoHeaders, metadata);
        this.policyImport = checkNotNull(policyImport, "policyImport");
    }

    /**
     * Constructs a new {@code PolicyImportCreated} object indicating the creation of the import.
     *
     * @param policyId the identifier of the Policy to which the created import belongs
     * @param policyImport the created {@link PolicyImport}
     * @param revision the revision of the Policy.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param metadata the metadata to apply for the event.
     * @return the created PolicyImportCreated.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static PolicyImportCreated of(final PolicyId policyId,
            final PolicyImport policyImport,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        return new PolicyImportCreated(policyId, policyImport, revision, timestamp, dittoHeaders, metadata);
    }

    /**
     * Creates a new {@code PolicyImportCreated} from a JSON string.
     *
     * @param jsonString the JSON string from which a new PolicyImportCreated instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code PolicyImportCreated} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected 'PolicyImportCreated'
     * format.
     */
    public static PolicyImportCreated fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code PolicyImportCreated} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new PolicyImportCreated instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code PolicyImportCreated} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected 'PolicyImportCreated'
     * format.
     */
    public static PolicyImportCreated fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<PolicyImportCreated>(TYPE, jsonObject)
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
     * Returns the created {@link PolicyImport}.
     *
     * @return the created {@link PolicyImport}.
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
    public PolicyImportCreated setRevision(final long revision) {
        return of(getPolicyEntityId(), policyImport, revision, getTimestamp().orElse(null), getDittoHeaders(),
                getMetadata().orElse(null));
    }

    @Override
    public PolicyImportCreated setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getPolicyEntityId(), policyImport, getRevision(), getTimestamp().orElse(null), dittoHeaders,
                getMetadata().orElse(null));
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

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
        final PolicyImportCreated that = (PolicyImportCreated) o;
        return that.canEqual(this) &&
                Objects.equals(policyImport, that.policyImport) &&
                super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof PolicyImportCreated;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", policyImport=" + policyImport +
                "]";
    }

}
