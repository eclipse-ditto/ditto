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
package org.eclipse.ditto.signals.events.things;

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
import org.eclipse.ditto.model.base.json.JsonParsableEvent;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.ThingPolicyId;
import org.eclipse.ditto.signals.events.base.EventJsonDeserializer;

/**
 * This event is emitted after a Thing's {@code policyId} was created (e.g. before it was in v1 with ACL, so setting
 * a Policy ID will create the Policy ID).
 */
@Immutable
@JsonParsableEvent(name = PolicyIdCreated.NAME, typePrefix = PolicyIdCreated.TYPE_PREFIX)
public final class PolicyIdCreated extends AbstractThingEvent<PolicyIdCreated>
        implements ThingModifiedEvent<PolicyIdCreated> {

    /**
     * Name of this event.
     */
    public static final String NAME = "policyIdCreated";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_POLICY_ID =
            JsonFactory.newStringFieldDefinition("policyId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final ThingPolicyId policyId;

    private PolicyIdCreated(final ThingId thingId, final ThingPolicyId policyId, final long revision,
            @Nullable final Instant timestamp, final DittoHeaders dittoHeaders) {
        super(TYPE, thingId, revision, timestamp, dittoHeaders);
        this.policyId = policyId;
    }

    /**
     * Creates a new {@code PolicyIdCreated} object.
     *
     * @param thingId the ID of the Thing with which this event is associated.
     * @param policyId the ID of the Policy.
     * @param revision the revision of the Thing.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code PolicyIdCreated}
     * @throws NullPointerException if {@code thingId}, {@code revision} or {@code dittoHeaders} are {@code null}.
     * @deprecated Thing ID is now typed. Use
     * {@link #of(ThingId, ThingPolicyId, long, DittoHeaders)}
     * instead.
     */
    @Deprecated
    public static PolicyIdCreated of(final String thingId, final String policyId, final long revision,
            final DittoHeaders dittoHeaders) {
        return of(ThingId.of(thingId), ThingPolicyId.of(policyId), revision, dittoHeaders);
    }

    /**
     * Creates a new {@code PolicyIdCreated} object.
     *
     * @param thingId the ID of the Thing with which this event is associated.
     * @param policyId the ID of the Policy.
     * @param revision the revision of the Thing.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code PolicyIdCreated}
     * @throws NullPointerException if {@code thingId}, {@code revision} or {@code dittoHeaders} are {@code null}.
     */
    public static PolicyIdCreated of(final ThingId thingId, final ThingPolicyId policyId, final long revision,
            final DittoHeaders dittoHeaders) {
        return of(thingId, policyId, revision, null, dittoHeaders);
    }

    /**
     * Creates a new {@code PolicyIdCreated} object.
     *
     * @param thingId the ID of the Thing with which this event is associated.
     * @param policyId the ID of the Policy.
     * @param revision the revision of the Thing.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code PolicyIdCreated}
     * @throws NullPointerException if {@code thingId}, {@code revision} or {@code dittoHeaders} are {@code null}.
     * @deprecated Thing ID is now typed. Use
     * {@link #of(ThingId, ThingPolicyId, long, Instant, DittoHeaders)}
     * instead.
     */
    @Deprecated
    public static PolicyIdCreated of(final String thingId, final String policyId, final long revision,
            @Nullable final Instant timestamp, final DittoHeaders dittoHeaders) {
        return of(ThingId.of(thingId), ThingPolicyId.of(policyId), revision, timestamp, dittoHeaders);
    }

    /**
     * Creates a new {@code PolicyIdCreated} object.
     *
     * @param thingId the ID of the Thing with which this event is associated.
     * @param policyId the ID of the Policy.
     * @param revision the revision of the Thing.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code PolicyIdCreated}
     * @throws NullPointerException if {@code thingId}, {@code revision} or {@code dittoHeaders} are {@code null}.
     */
    public static PolicyIdCreated of(final ThingId thingId, final ThingPolicyId policyId, final long revision,
            @Nullable final Instant timestamp, final DittoHeaders dittoHeaders) {
        return new PolicyIdCreated(thingId, policyId, revision, timestamp, dittoHeaders);
    }

    /**
     * Creates a new {@code PolicyIdCreated} from a JSON string.
     *
     * @param jsonString the JSON string of which a new PolicyIdCreated instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code PolicyIdCreated} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'PolicyIdCreated' format.
     */
    public static PolicyIdCreated fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code PolicyIdCreated} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new PolicyIdCreated instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code PolicyIdCreated} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'PolicyIdCreated' format.
     */
    public static PolicyIdCreated fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<PolicyIdCreated>(TYPE, jsonObject).deserialize((revision, timestamp) -> {
            final String extractedThingId = jsonObject.getValueOrThrow(JsonFields.THING_ID);
            final ThingId thingId = ThingId.of(extractedThingId);
            final String extractedPolicyId = jsonObject.getValueOrThrow(JSON_POLICY_ID);
            final ThingPolicyId thingPolicyId = ThingPolicyId.of(extractedPolicyId);

            return of(thingId, thingPolicyId, revision, timestamp, dittoHeaders);
        });
    }

    /**
     * Returns the created Policy ID.
     *
     * @return the created Policy ID.
     * @deprecated Policy ID of the Thing is now typed. Use {@link #getPolicyEntityId()} instead.
     */
    @Deprecated
    public String getPolicyId() {
        return getPolicyEntityId().toString();
    }

    public ThingPolicyId getPolicyEntityId() {
        return policyId;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.of(policyId).map(String::valueOf).map(JsonFactory::newValue);
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = Thing.JsonFields.POLICY_ID.getPointer().toString();
        return JsonPointer.of(path);
    }

    @Override
    public PolicyIdCreated setRevision(final long revision) {
        return of(getThingEntityId(), policyId, revision, getTimestamp().orElse(null), getDittoHeaders());
    }

    @Override
    public PolicyIdCreated setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getThingEntityId(), policyId, getRevision(), getTimestamp().orElse(null), dittoHeaders);
    }

    @Override
    protected void appendPayloadAndBuild(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> predicate) {
        final Predicate<JsonField> thePredicate = schemaVersion.and(predicate);
        jsonObjectBuilder.set(JSON_POLICY_ID, String.valueOf(policyId), thePredicate);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(policyId);
        return result;
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PolicyIdCreated that = (PolicyIdCreated) o;
        return that.canEqual(this) && Objects.equals(policyId, that.policyId) && super.equals(that);
    }

    @Override
    protected boolean canEqual(final Object other) {
        return (other instanceof PolicyIdCreated);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", policyId=" + policyId + "]";
    }

}
