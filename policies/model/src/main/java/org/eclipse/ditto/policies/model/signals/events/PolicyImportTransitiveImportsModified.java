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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import static org.eclipse.ditto.base.model.exceptions.DittoJsonException.wrapJsonRuntimeException;

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
import org.eclipse.ditto.policies.model.PolicyId;

/**
 * This event is emitted after the resolve transitively configuration of a Policy import was modified.
 *
 * @since 3.9.0
 */
@Immutable
@JsonParsableEvent(name = PolicyImportTransitiveImportsModified.NAME, typePrefix = PolicyEvent.TYPE_PREFIX)
public final class PolicyImportTransitiveImportsModified
        extends AbstractPolicyEvent<PolicyImportTransitiveImportsModified>
        implements PolicyEvent<PolicyImportTransitiveImportsModified> {

    /**
     * Name of this event.
     */
    public static final String NAME = "policyImportTransitiveImportsModified";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_IMPORTED_POLICY_ID =
            JsonFactory.newStringFieldDefinition("importedPolicyId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonArray> JSON_TRANSITIVE_IMPORTS =
            JsonFactory.newJsonArrayFieldDefinition("transitiveImports", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final PolicyId importedPolicyId;
    private final List<PolicyId> transitiveImports;

    private PolicyImportTransitiveImportsModified(final PolicyId policyId,
            final PolicyId importedPolicyId,
            final List<PolicyId> transitiveImports,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(TYPE, checkNotNull(policyId, "policyId"), revision, timestamp, dittoHeaders, metadata);
        this.importedPolicyId = checkNotNull(importedPolicyId, "importedPolicyId");
        this.transitiveImports = Collections.unmodifiableList(
                checkNotNull(transitiveImports, "transitiveImports"));
    }

    /**
     * Constructs a new {@code PolicyImportTransitiveImportsModified} object indicating the modification of the
     * resolve transitively configuration.
     *
     * @param policyId the identifier of the Policy to which the modified import belongs.
     * @param importedPolicyId the identifier of the imported Policy.
     * @param transitiveImports the modified list of Policy IDs to resolve transitively.
     * @param revision the revision of the Policy.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param metadata the metadata to apply for the event.
     * @return the created PolicyImportTransitiveImportsModified.
     * @throws NullPointerException if any argument but {@code timestamp} and {@code metadata} is {@code null}.
     */
    public static PolicyImportTransitiveImportsModified of(final PolicyId policyId,
            final PolicyId importedPolicyId,
            final List<PolicyId> transitiveImports,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        return new PolicyImportTransitiveImportsModified(policyId, importedPolicyId, transitiveImports,
                revision, timestamp, dittoHeaders, metadata);
    }

    /**
     * Creates a new {@code PolicyImportTransitiveImportsModified} from a JSON string.
     *
     * @param jsonString the JSON string from which a new PolicyImportTransitiveImportsModified instance is to be
     * created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code PolicyImportTransitiveImportsModified} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'PolicyImportTransitiveImportsModified' format.
     */
    public static PolicyImportTransitiveImportsModified fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code PolicyImportTransitiveImportsModified} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new PolicyImportTransitiveImportsModified instance is to be
     * created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code PolicyImportTransitiveImportsModified} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'PolicyImportTransitiveImportsModified' format.
     */
    public static PolicyImportTransitiveImportsModified fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<PolicyImportTransitiveImportsModified>(TYPE, jsonObject)
                .deserialize((revision, timestamp, metadata) -> {
                    final String extractedPolicyId =
                            jsonObject.getValueOrThrow(PolicyEvent.JsonFields.POLICY_ID);
                    final PolicyId policyId = PolicyId.of(extractedPolicyId);
                    final PolicyId importedPolicyId =
                            PolicyId.of(jsonObject.getValueOrThrow(JSON_IMPORTED_POLICY_ID));
                    final JsonArray transitiveImportsArray =
                            jsonObject.getValueOrThrow(JSON_TRANSITIVE_IMPORTS);
                    final List<PolicyId> transitiveImports = transitiveImportsArray.stream()
                            .map(JsonValue::asString)
                            .map(PolicyId::of)
                            .collect(Collectors.toList());

                    return of(policyId, importedPolicyId, transitiveImports, revision, timestamp,
                            dittoHeaders, metadata);
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
     * Returns the modified list of Policy IDs to resolve transitively.
     *
     * @return the modified resolve transitively list.
     */
    public List<PolicyId> getTransitiveImports() {
        return transitiveImports;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        final JsonArray jsonArray = transitiveImports.stream()
                .map(PolicyId::toString)
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray());
        return Optional.of(jsonArray);
    }

    @Override
    public PolicyImportTransitiveImportsModified setEntity(final JsonValue entity) {
        return wrapJsonRuntimeException(() -> {
            final List<PolicyId> policyIds = entity.asArray().stream()
                    .map(JsonValue::asString)
                    .map(PolicyId::of)
                    .collect(Collectors.toList());
            return of(getPolicyEntityId(), importedPolicyId, policyIds,
                    getRevision(), getTimestamp().orElse(null), getDittoHeaders(), getMetadata().orElse(null));
        });
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/imports/" + importedPolicyId + "/transitiveImports";
        return JsonPointer.of(path);
    }

    @Override
    public PolicyImportTransitiveImportsModified setRevision(final long revision) {
        return of(getPolicyEntityId(), importedPolicyId, transitiveImports, revision,
                getTimestamp().orElse(null), getDittoHeaders(), getMetadata().orElse(null));
    }

    @Override
    public PolicyImportTransitiveImportsModified setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getPolicyEntityId(), importedPolicyId, transitiveImports, getRevision(),
                getTimestamp().orElse(null), dittoHeaders, getMetadata().orElse(null));
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_IMPORTED_POLICY_ID, importedPolicyId.toString(), predicate);
        final JsonArray jsonArray = transitiveImports.stream()
                .map(PolicyId::toString)
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray());
        jsonObjectBuilder.set(JSON_TRANSITIVE_IMPORTS, jsonArray, predicate);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(importedPolicyId);
        result = prime * result + Objects.hashCode(transitiveImports);
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
        final PolicyImportTransitiveImportsModified that = (PolicyImportTransitiveImportsModified) o;
        return that.canEqual(this) &&
                Objects.equals(importedPolicyId, that.importedPolicyId) &&
                Objects.equals(transitiveImports, that.transitiveImports) &&
                super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof PolicyImportTransitiveImportsModified;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", importedPolicyId=" + importedPolicyId +
                ", transitiveImports=" + transitiveImports +
                "]";
    }

}
