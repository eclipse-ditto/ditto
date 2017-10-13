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
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.events.base.EventJsonDeserializer;

/**
 * This event is emitted after a {@link org.eclipse.ditto.model.policies.Policy} was deleted.
 */
@Immutable
public final class PolicyDeleted extends AbstractPolicyEvent<PolicyDeleted> implements PolicyEvent<PolicyDeleted> {

    /**
     * Name of this event.
     */
    public static final String NAME = "policyDeleted";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private PolicyDeleted(final String policyId,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        super(TYPE, checkNotNull(policyId, "Policy identifier"), revision, timestamp, dittoHeaders);
    }

    /**
     * Constructs a new {@code PolicyDeleted} object.
     *
     * @param policyId the ID of the deleted {@link org.eclipse.ditto.model.policies.Policy}.
     * @param revision the revision of the Policy.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return a event object indicating the deletion of the Policy
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static PolicyDeleted of(final String policyId, final long revision, final DittoHeaders dittoHeaders) {
        return of(policyId, revision, null, dittoHeaders);
    }

    /**
     * Constructs a new {@code PolicyDeleted} object.
     *
     * @param policyId the ID of the deleted {@link org.eclipse.ditto.model.policies.Policy}.
     * @param revision the revision of the Policy.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return a event object indicating the deletion of the Policy
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static PolicyDeleted of(final String policyId,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        return new PolicyDeleted(policyId, revision, timestamp, dittoHeaders);
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
        return new EventJsonDeserializer<PolicyDeleted>(TYPE, jsonObject).deserialize(
                (revision, timestamp) -> of(jsonObject.getValueOrThrow(JsonFields.POLICY_ID), revision, timestamp,
                        dittoHeaders));
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public PolicyDeleted setRevision(final long revision) {
        return of(getPolicyId(), revision, getTimestamp().orElse(null), getDittoHeaders());
    }

    @Override
    public PolicyDeleted setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getPolicyId(), getRevision(), getTimestamp().orElse(null), dittoHeaders);
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
