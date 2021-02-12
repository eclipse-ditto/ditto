/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.notifications.policies;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonParsableNotification;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.SubjectId;

/**
 * Notification that some subjects of a policy are about to expire.
 *
 * @since 2.0.0
 */
@Immutable
@JsonParsableNotification(type = SubjectExpiryNotification.TYPE)
public final class SubjectExpiryNotification extends AbstractPolicyNotification<SubjectExpiryNotification> {

    private static final String NAME = "subjectExpiry";

    /**
     * Type of this notification.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final Instant expiry;
    private final Collection<SubjectId> expiringSubjectIds;

    private SubjectExpiryNotification(final PolicyId policyId, final Instant expiry,
            final Collection<SubjectId> expiringSubjectIds,
            final DittoHeaders dittoHeaders) {
        super(policyId, dittoHeaders);
        this.expiry = checkNotNull(expiry, "expiry");
        this.expiringSubjectIds =
                Collections.unmodifiableList(new ArrayList<>(checkNotNull(expiringSubjectIds, "expiringSubjects")));
    }

    /**
     * Create a notification for subject expiry.
     *
     * @param policyId the policy ID.
     * @param expiry when the subjects will expire.
     * @param expiringSubjects what subjects will expire.
     * @param dittoHeaders headers of the notification.
     * @return the notification.
     */
    public static SubjectExpiryNotification of(final PolicyId policyId, final Instant expiry,
            final Collection<SubjectId> expiringSubjects, final DittoHeaders dittoHeaders) {

        return new SubjectExpiryNotification(policyId, expiry, expiringSubjects, dittoHeaders);
    }

    /**
     * Deserialize a subject-expiry notification from JSON.
     *
     * @param jsonObject the serialized JSON.
     * @param dittoHeaders the Ditto headers.
     * @return the deserialized notification.
     */
    public static SubjectExpiryNotification fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final PolicyId policyId = PolicyId.of(jsonObject.getValueOrThrow(JSON_POLICY_ID));
        final Instant expiry = parseExpiry(jsonObject.getValueOrThrow(JsonFields.EXPIRY));
        final Collection<SubjectId> expiringSubjects = jsonObject.getValueOrThrow(
                JsonFields.EXPIRING_SUBJECTS)
                .stream()
                .map(value -> SubjectId.newInstance(value.asString()))
                .collect(Collectors.toList());
        return of(policyId, expiry, expiringSubjects, dittoHeaders);
    }

    @Override
    public SubjectExpiryNotification setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new SubjectExpiryNotification(getEntityId(), expiry, expiringSubjectIds, dittoHeaders);
    }

    @Override
    protected void appendPolicyNotificationPayload(final JsonObjectBuilder jsonObjectBuilder,
            final Predicate<JsonField> predicate) {

        jsonObjectBuilder.set(JsonFields.EXPIRY, expiry.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.EXPIRING_SUBJECTS, toArray(expiringSubjectIds), predicate);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public String getResourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Get the timestamp where the subjects will expire.
     *
     * @return the expiry.
     */
    public Instant getExpiry() {
        return expiry;
    }

    /**
     * Get the IDs of the subjects that will expire.
     *
     * @return the expiring subject IDs.
     */
    public Collection<SubjectId> getExpiringSubjectIds() {
        return expiringSubjectIds;
    }

    private static JsonArray toArray(final Collection<SubjectId> collection) {
        return collection.stream().map(JsonValue::of).collect(JsonCollectors.valuesToArray());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() +
                ", expiry=" + expiry +
                ", expiringSubjects=" + expiringSubjectIds +
                "]";
    }

    @Override
    public boolean equals(final Object other) {
        if (super.equals(other)) {
            final SubjectExpiryNotification that = (SubjectExpiryNotification) other;
            return Objects.equals(expiry, that.expiry) && Objects.equals(expiringSubjectIds, that.expiringSubjectIds);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(expiry, expiringSubjectIds, super.hashCode());
    }

    private static Instant parseExpiry(final String expiryString) {
        try {
            return Instant.parse(expiryString);
        } catch (final DateTimeParseException e) {
            throw JsonParseException.newBuilder()
                    .message(String.format("Expiry timestamp '%s' is not valid. " +
                            "It must be provided as ISO-8601 formatted char sequence.", expiryString))
                    .build();
        }
    }

    /**
     * JSON fields of this notification's payload for use in the Ditto protocol.
     */
    public static final class JsonFields {

        /**
         * JSON field for the expiry timestamp of the subjects.
         */
        public static final JsonFieldDefinition<String> EXPIRY =
                JsonFactory.newStringFieldDefinition("expiry", JsonSchemaVersion.V_2, FieldType.REGULAR);

        /**
         * JSON field for the subjects that will expire.
         */
        public static final JsonFieldDefinition<JsonArray> EXPIRING_SUBJECTS =
                JsonFactory.newJsonArrayFieldDefinition("expiringSubjects", JsonSchemaVersion.V_2, FieldType.REGULAR);
    }
}
