/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.signals.events.policies;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.signals.events.base.EventJsonDeserializer;

/**
 * This event is emitted after all {@link PolicyEntry}s were modified at once.
 */
@Immutable
public final class PolicyEntriesModified extends AbstractPolicyEvent<PolicyEntriesModified> implements
        PolicyEvent<PolicyEntriesModified> {

    /**
     * Name of this event.
     */
    public static final String NAME = "policyEntriesModified";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<JsonObject> JSON_POLICY_ENTRIES =
            JsonFactory.newJsonObjectFieldDefinition("policyEntries", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final Iterable<PolicyEntry> policyEntries;

    private PolicyEntriesModified(final String policyId,
            final Iterable<PolicyEntry> policyEntries,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        super(TYPE, checkNotNull(policyId, "Policy identifier"), revision, timestamp, dittoHeaders);
        this.policyEntries = checkNotNull(policyEntries, "Policy Entries");
    }

    /**
     * Constructs a new {@code PolicyEntriesModified} object indicating the modification of the entries.
     *
     * @param policyId the identifier of the Policy to which the modified entry belongs
     * @param policyEntries the modified {@link PolicyEntry}s.
     * @param revision the revision of the Policy.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the created PolicyEntriesModified.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static PolicyEntriesModified of(final String policyId,
            final Iterable<PolicyEntry> policyEntries,
            final long revision,
            final DittoHeaders dittoHeaders) {

        return of(policyId, policyEntries, revision, null, dittoHeaders);
    }

    /**
     * Constructs a new {@code PolicyEntriesModified} object indicating the modification of the entries.
     *
     * @param policyId the identifier of the Policy to which the modified entry belongs
     * @param policyEntries the modified {@link PolicyEntry}s.
     * @param revision the revision of the Policy.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the created PolicyEntriesModified.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static PolicyEntriesModified of(final String policyId,
            final Iterable<PolicyEntry> policyEntries,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        return new PolicyEntriesModified(policyId, policyEntries, revision, timestamp, dittoHeaders);
    }

    /**
     * Creates a new {@code PolicyEntriesModified} from a JSON string.
     *
     * @param jsonString the JSON string from which a new PolicyEntriesModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code PolicyEntriesModified} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected 'PolicyEntriesModified'
     * format.
     */
    public static PolicyEntriesModified fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code PolicyEntriesModified} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new PolicyEntriesModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code PolicyEntriesModified} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected 'PolicyEntriesModified'
     * format.
     */
    public static PolicyEntriesModified fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<PolicyEntriesModified>(TYPE, jsonObject)
                .deserialize((revision, timestamp) -> {
                    final String policyId = jsonObject.getValueOrThrow(JsonFields.POLICY_ID);
                    final JsonObject policyEntriesJsonObject = jsonObject.getValueOrThrow(JSON_POLICY_ENTRIES);
                    final Iterable<PolicyEntry> extractedModifiedPolicyEntry =
                            PoliciesModelFactory.newPolicyEntries(policyEntriesJsonObject);

                    return of(policyId, extractedModifiedPolicyEntry, revision, timestamp, dittoHeaders);
                });
    }

    /**
     * Returns the modified {@link PolicyEntry}s.
     *
     * @return the modified {@link PolicyEntry}s.
     */
    public Iterable<PolicyEntry> getPolicyEntries() {
        return policyEntries;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        final JsonObject jsonObject =
                StreamSupport.stream(policyEntries.spliterator(), false)
                        .map(entry -> JsonFactory.newObjectBuilder()
                                .set(entry.getLabel().getJsonFieldDefinition(),
                                        entry.toJson(schemaVersion, FieldType.regularOrSpecial()))
                                .build())
                        .collect(JsonCollectors.objectsToObject());
        return Optional.ofNullable(jsonObject);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/entries");
    }

    @Override
    public PolicyEntriesModified setRevision(final long revision) {
        return of(getPolicyId(), policyEntries, revision, getTimestamp().orElse(null), getDittoHeaders());
    }

    @Override
    public PolicyEntriesModified setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getPolicyId(), policyEntries, getRevision(), getTimestamp().orElse(null), dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_POLICY_ENTRIES, StreamSupport.stream(policyEntries.spliterator(), false)
                .map(entry -> JsonFactory.newObjectBuilder().set(entry.getLabel().getJsonFieldDefinition(),
                        entry.toJson(schemaVersion, thePredicate), predicate).build())
                .collect(JsonCollectors.objectsToObject()), predicate);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(policyEntries);
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
        final PolicyEntriesModified that = (PolicyEntriesModified) o;
        return that.canEqual(this) && Objects.equals(policyEntries, that.policyEntries) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof PolicyEntriesModified;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyEntries=" + policyEntries + "]";
    }

}
