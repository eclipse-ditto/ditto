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
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableEvent;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.events.EventJsonDeserializer;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.PolicyId;

/**
 * This event is emitted after a {@link org.eclipse.ditto.policies.model.Policy} was deleted.
 */
@Immutable
@JsonParsableEvent(name = PolicyDeleted.NAME, typePrefix= PolicyEvent.TYPE_PREFIX)
public final class PolicyDeleted extends AbstractPolicyEvent<PolicyDeleted> implements PolicyEvent<PolicyDeleted> {

    /**
     * Name of this event.
     */
    public static final String NAME = "policyDeleted";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private PolicyDeleted(final PolicyId policyId,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        super(TYPE, checkNotNull(policyId, "Policy identifier"), revision, timestamp, dittoHeaders, metadata);
    }

    /**
     * Constructs a new {@code PolicyDeleted} object.
     *
     * @param policyId the ID of the deleted {@link org.eclipse.ditto.policies.model.Policy}.
     * @param revision the revision of the Policy.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @param metadata the metadata to apply for the event.
     * @return a event object indicating the deletion of the Policy
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static PolicyDeleted of(final PolicyId policyId,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders,
            @Nullable final Metadata metadata) {

        return new PolicyDeleted(policyId, revision, timestamp, dittoHeaders, metadata);
    }

    /**
     * Creates a new {@code PolicyDeleted} from a JSON string.
     *
     * @param jsonString the JSON string from which a new PolicyDeleted instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code PolicyDeleted} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'PolicyDeleted' format.
     */
    public static PolicyDeleted fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code PolicyDeleted} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new PolicyDeleted instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code PolicyDeleted} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'PolicyDeleted' format.
     */
    public static PolicyDeleted fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<PolicyDeleted>(TYPE, jsonObject)
                .deserialize((revision, timestamp, metadata) -> {
                    final PolicyId policyId = PolicyId.of(jsonObject.getValueOrThrow(PolicyEvent.JsonFields.POLICY_ID));
                    return of(policyId, revision, timestamp, dittoHeaders, metadata);
                });
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public PolicyDeleted setRevision(final long revision) {
        return of(getPolicyEntityId(), revision, getTimestamp().orElse(null), getDittoHeaders(),
                getMetadata().orElse(null));
    }

    @Override
    public PolicyDeleted setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getPolicyEntityId(), getRevision(), getTimestamp().orElse(null), dittoHeaders,
                getMetadata().orElse(null));
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {
        // no-op
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof PolicyDeleted;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + "]";
    }

}
