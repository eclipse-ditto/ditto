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
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonParsableNotification;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.PolicyId;

/**
 * Notification that some subjects of a policy are about to expire.
 */
@Immutable
@JsonParsableNotification(type = SubjectExpiryNotification.TYPE)
public final class SubjectExpiryNotification extends AbstractPolicyNotification<SubjectExpiryNotification> {

    private static final String NAME = "subject.expiry";

    /**
     * Type of this notification.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private static final JsonFieldDefinition<String> JSON_EXPIRY =
            JsonFactory.newStringFieldDefinition("expiry", JsonSchemaVersion.V_2, FieldType.REGULAR);

    private static final JsonFieldDefinition<JsonArray> JSON_EXPIRING_SUBJECTS =
            JsonFactory.newJsonArrayFieldDefinition("expiringSubjects", JsonSchemaVersion.V_2, FieldType.REGULAR);

    private final Instant expiry;
    private final Collection<AuthorizationSubject> expiringSubjects;

    private SubjectExpiryNotification(final PolicyId policyId, final Instant expiry,
            final Collection<AuthorizationSubject> expiringSubjects,
            final DittoHeaders dittoHeaders) {
        super(policyId, dittoHeaders);
        this.expiry = checkNotNull(expiry, "expiry");
        this.expiringSubjects =
                Collections.unmodifiableList(new ArrayList<>(checkNotNull(expiringSubjects, "expiringSubjects")));
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
            final Collection<AuthorizationSubject> expiringSubjects, final DittoHeaders dittoHeaders) {

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
        final Instant expiry = parseExpiry(jsonObject.getValueOrThrow(JSON_EXPIRY));
        final Collection<AuthorizationSubject> expiringSubjects = jsonObject.getValueOrThrow(JSON_EXPIRING_SUBJECTS)
                .stream()
                .map(value -> AuthorizationSubject.newInstance(value.asString()))
                .collect(Collectors.toList());
        return of(policyId, expiry, expiringSubjects, dittoHeaders);
    }

    @Override
    public SubjectExpiryNotification setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new SubjectExpiryNotification(getEntityId(), expiry, expiringSubjects, dittoHeaders);
    }

    @Override
    protected void appendPolicyNotificationPayload(final JsonObjectBuilder jsonObjectBuilder,
            final Predicate<JsonField> predicate) {

        jsonObjectBuilder.set(JSON_EXPIRY, expiry.toString(), predicate);
        jsonObjectBuilder.set(JSON_EXPIRING_SUBJECTS, toArray(expiringSubjects), predicate);
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

    private static JsonArray toArray(final Collection<AuthorizationSubject> collection) {
        return collection.stream()
                .map(AuthorizationSubject::getId)
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() +
                ", expiry=" + expiry +
                ", expiringSubjects=" + expiringSubjects +
                "]";
    }

    @Override
    public boolean equals(final Object other) {
        if (super.equals(other)) {
            final SubjectExpiryNotification that = (SubjectExpiryNotification) other;
            return Objects.equals(expiry, that.expiry) && Objects.equals(expiringSubjects, that.expiringSubjects);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(expiry, expiringSubjects, super.hashCode());
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
}
