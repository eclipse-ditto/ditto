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
import org.eclipse.ditto.policies.model.Policy;

/**
 * This event is emitted after a {@link org.eclipse.ditto.policies.model.Policy} was created.
 */
@Immutable
@JsonParsableEvent(name = PolicyCreated.NAME, typePrefix= PolicyEvent.TYPE_PREFIX)
public final class PolicyCreated extends AbstractPolicyEvent<PolicyCreated> implements PolicyEvent<PolicyCreated> {

    /**
     * Name of this event.
     */
    public static final String NAME = "policyCreated";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<JsonObject> JSON_POLICY =
            JsonFactory.newJsonObjectFieldDefinition("policy", FieldType.REGULAR, JsonSchemaVersion.V_2);
    private static final String PROPERTY_NAME_POLICY = "Policy";

    private final Policy policy;

    private PolicyCreated(final Policy policy,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(TYPE, checkNotNull(policy, PROPERTY_NAME_POLICY).getEntityId().orElse(null), revision, timestamp,
                dittoHeaders, metadata);
        this.policy = policy;
    }

    /**
     * Constructs a new {@code PolicyCreated} object.
     *
     * @param policy the created {@link org.eclipse.ditto.policies.model.Policy}.
     * @param revision the revision of the Policy.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param metadata the metadata to apply for the event.
     * @return a event object indicating the creation of the Policy.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static PolicyCreated of(final Policy policy,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        return new PolicyCreated(checkNotNull(policy, PROPERTY_NAME_POLICY), revision, timestamp, dittoHeaders,
                metadata);
    }

    /**
     * Creates a new {@code PolicyCreated} from a JSON string.
     *
     * @param jsonString the JSON string from which a new PolicyCreated instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code PolicyCreated} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected 'PolicyCreated' format.
     */
    public static PolicyCreated fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code PolicyCreated} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new PolicyCreated instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code PolicyCreated} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected 'PolicyCreated' format.
     */
    public static PolicyCreated fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<PolicyCreated>(TYPE, jsonObject).deserialize(
                (revision, timestamp, metadata) -> {
            final JsonObject policyJsonObject = jsonObject.getValueOrThrow(JSON_POLICY);
            final Policy extractedModifiedPolicy = PoliciesModelFactory.newPolicy(policyJsonObject);

            return new PolicyCreated(extractedModifiedPolicy, revision, timestamp, dittoHeaders, metadata);
        });
    }

    /**
     * Returns the created {@link org.eclipse.ditto.policies.model.Policy}.
     *
     * @return the created Policy.
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
    public PolicyCreated setRevision(final long revision) {
        return of(policy, revision, getTimestamp().orElse(null), getDittoHeaders(), getMetadata().orElse(null));
    }

    @Override
    public PolicyCreated setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(policy, getRevision(), getTimestamp().orElse(null), dittoHeaders, getMetadata().orElse(null));
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
        final PolicyCreated that = (PolicyCreated) o;
        return that.canEqual(this) && Objects.equals(policy, that.policy) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof PolicyCreated;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policy=" + policy + "]";
    }

}
