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
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.signals.events.base.EventJsonDeserializer;

/**
 * This event is emitted after a {@link Policy} was modified.
 */
@Immutable
public final class PolicyModified extends AbstractPolicyEvent<PolicyModified> implements PolicyEvent<PolicyModified> {

    /**
     * Name of this event.
     */
    public static final String NAME = "policyModified";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<JsonObject> JSON_POLICY =
            JsonFactory.newJsonObjectFieldDefinition("policy", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final Policy policy;

    private PolicyModified(final Policy policy,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        super(TYPE, checkNotNull(policy, "Policy").getId().orElse(null), revision, timestamp, dittoHeaders);
        this.policy = policy;
    }

    /**
     * Constructs a new {@code PolicyModified} object.
     *
     * @param policy the modified {@link Policy}.
     * @param revision the revision of the Policy.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the created PolicyModified.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static PolicyModified of(final Policy policy, final long revision, final DittoHeaders dittoHeaders) {
        return of(policy, revision, null, dittoHeaders);
    }

    /**
     * Constructs a new {@code PolicyModified} object.
     *
     * @param policy the modified {@link Policy}.
     * @param revision the revision of the Policy.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the created PolicyModified.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static PolicyModified of(final Policy policy,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        return new PolicyModified(policy, revision, timestamp, dittoHeaders);
    }

    /**
     * Creates a new {@code PolicyModified} from a JSON string.
     *
     * @param jsonString the JSON string from which a new PolicyModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code PolicyModified} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected 'PolicyModified' format.
     */
    public static PolicyModified fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code PolicyModified} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new PolicyModified instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code PolicyModified} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected 'PolicyModified' format.
     */
    public static PolicyModified fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<PolicyModified>(TYPE, jsonObject).deserialize((revision, timestamp) -> {
            final JsonObject policyJsonObject = jsonObject.getValueOrThrow(JSON_POLICY);
            final Policy extractedModifiedPolicy = PoliciesModelFactory.newPolicy(policyJsonObject);

            return of(extractedModifiedPolicy, revision, timestamp, dittoHeaders);
        });
    }

    /**
     * Returns the modified {@link Policy}.
     *
     * @return the modified Policy.
     */
    public Policy getPolicy() {
        return policy;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(policy.toJson(schemaVersion, FieldType.regularOrSpecial()));
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public PolicyModified setRevision(final long revision) {
        return of(policy, revision, getTimestamp().orElse(null), getDittoHeaders());
    }

    @Override
    public PolicyModified setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policy, getRevision(), getTimestamp().orElse(null), dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_POLICY, policy.toJson(schemaVersion, thePredicate), predicate);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(policy);
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
        final PolicyModified that = (PolicyModified) o;
        return that.canEqual(this) && Objects.equals(policy, that.policy) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof PolicyModified;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policy=" + policy + "]";
    }

}
