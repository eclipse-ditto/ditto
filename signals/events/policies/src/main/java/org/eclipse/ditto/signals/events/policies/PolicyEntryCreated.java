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

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

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
 * This event is emitted after a {@link PolicyEntry} was created.
 */
@Immutable
public final class PolicyEntryCreated extends AbstractPolicyEvent<PolicyEntryCreated>
        implements PolicyEvent<PolicyEntryCreated> {

    /**
     * Name of this event.
     */
    public static final String NAME = "policyEntryCreated";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_LABEL =
            JsonFactory.newStringFieldDefinition("label", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonObject> JSON_POLICY_ENTRY =
            JsonFactory.newJsonObjectFieldDefinition("policyEntry", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final PolicyEntry policyEntry;

    private PolicyEntryCreated(final String policyId,
            final PolicyEntry policyEntry,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        super(TYPE, checkNotNull(policyId, "Policy identifier"), revision, timestamp, dittoHeaders);
        this.policyEntry = checkNotNull(policyEntry, "Policy Entry");
    }

    /**
     * Constructs a new {@code PolicyEntryCreated} object indicating the creation of the entry.
     *
     * @param policyId the identifier of the Policy to which the created entry belongs
     * @param policyEntry the created {@link PolicyEntry}
     * @param revision the revision of the Policy.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the created PolicyEntryCreated.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static PolicyEntryCreated of(final String policyId,
            final PolicyEntry policyEntry,
            final long revision,
            final DittoHeaders dittoHeaders) {

        return of(policyId, policyEntry, revision, null, dittoHeaders);
    }

    /**
     * Constructs a new {@code PolicyEntryCreated} object indicating the creation of the entry.
     *
     * @param policyId the identifier of the Policy to which the created entry belongs
     * @param policyEntry the created {@link PolicyEntry}
     * @param revision the revision of the Policy.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the created PolicyEntryCreated.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static PolicyEntryCreated of(final String policyId,
            final PolicyEntry policyEntry,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        return new PolicyEntryCreated(policyId, policyEntry, revision, timestamp, dittoHeaders);
    }

    /**
     * Creates a new {@code PolicyEntryCreated} from a JSON string.
     *
     * @param jsonString the JSON string from which a new PolicyEntryCreated instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code PolicyEntryCreated} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected 'PolicyEntryCreated'
     * format.
     */
    public static PolicyEntryCreated fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code PolicyEntryCreated} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new PolicyEntryCreated instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code PolicyEntryCreated} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected 'PolicyEntryCreated'
     * format.
     */
    public static PolicyEntryCreated fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<PolicyEntryCreated>(TYPE, jsonObject)
                .deserialize((revision, timestamp) -> {
                    final String policyId = jsonObject.getValueOrThrow(JsonFields.POLICY_ID);
                    final String policyEntryLabel = jsonObject.getValueOrThrow(JSON_LABEL);
                    final JsonObject policyEntryJsonObject = jsonObject.getValueOrThrow(JSON_POLICY_ENTRY);
                    final PolicyEntry extractedModifiedPolicyEntry =
                            PoliciesModelFactory.newPolicyEntry(policyEntryLabel, policyEntryJsonObject);

                    return of(policyId, extractedModifiedPolicyEntry, revision, timestamp, dittoHeaders);
                });
    }

    /**
     * Returns the created {@link PolicyEntry}.
     *
     * @return the created {@link PolicyEntry}.
     */
    public PolicyEntry getPolicyEntry() {
        return policyEntry;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(policyEntry.toJson(schemaVersion, FieldType.regularOrSpecial()));
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/entries/" + policyEntry.getLabel();
        return JsonPointer.of(path);
    }

    @Override
    public PolicyEntryCreated setRevision(final long revision) {
        return of(getPolicyId(), policyEntry, revision, getTimestamp().orElse(null), getDittoHeaders());
    }

    @Override
    public PolicyEntryCreated setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getPolicyId(), policyEntry, getRevision(), getTimestamp().orElse(null), dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_LABEL, policyEntry.getLabel().toString(), predicate);
        jsonObjectBuilder.set(JSON_POLICY_ENTRY, policyEntry.toJson(schemaVersion, thePredicate), predicate);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(policyEntry);
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
        final PolicyEntryCreated that = (PolicyEntryCreated) o;
        return that.canEqual(this) && Objects.equals(policyEntry, that.policyEntry) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof PolicyEntryCreated;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyEntry=" + policyEntry + "]";
    }

}
