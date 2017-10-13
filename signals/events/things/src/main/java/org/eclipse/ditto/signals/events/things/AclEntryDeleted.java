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
package org.eclipse.ditto.signals.events.things;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.events.base.EventJsonDeserializer;

/**
 * This event is emitted after a Thing ACL entry was deleted.
 */
@Immutable
public final class AclEntryDeleted extends AbstractThingEvent<AclEntryDeleted>
        implements ThingModifiedEvent<AclEntryDeleted> {

    /**
     * Name of this event.
     */
    public static final String NAME = "aclEntryDeleted";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_AUTHORIZATION_SUBJECT =
            JsonFactory.newStringFieldDefinition("authorizationSubject", FieldType.REGULAR, JsonSchemaVersion.V_1);

    private final AuthorizationSubject authorizationSubject;

    private AclEntryDeleted(final String thingId,
            final AuthorizationSubject authorizationSubject,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        super(TYPE, thingId, revision, timestamp, dittoHeaders);
        this.authorizationSubject = Objects.requireNonNull(authorizationSubject, "The ACL subject must not be null!");
    }

    /**
     * Constructs a new {@code AclEntryDeleted} object.
     *
     * @param thingId the ID of the Thing with which this event is associated.
     * @param authorizationSubject the subject of the ACL entry which was deleted.
     * @param revision the revision of the Thing.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the created AclEntryDeleted.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static AclEntryDeleted of(final String thingId,
            final AuthorizationSubject authorizationSubject,
            final long revision,
            final DittoHeaders dittoHeaders) {

        return of(thingId, authorizationSubject, revision, null, dittoHeaders);
    }

    /**
     * Constructs a new {@code AclEntryDeleted} object.
     *
     * @param thingId the ID of the Thing with which this event is associated.
     * @param authorizationSubject the subject of the ACL entry which was deleted.
     * @param revision the revision of the Thing.
     * @param timestamp the timestamp of this event.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the created AclEntryDeleted.
     * @throws NullPointerException if any argument but {@code timestamp} is {@code null}.
     */
    public static AclEntryDeleted of(final String thingId,
            final AuthorizationSubject authorizationSubject,
            final long revision,
            @Nullable final Instant timestamp,
            final DittoHeaders dittoHeaders) {

        return new AclEntryDeleted(thingId, authorizationSubject, revision, timestamp, dittoHeaders);
    }

    /**
     * Creates a new {@code AclEntryDeleted} from a JSON string.
     *
     * @param jsonString the JSON string of which a new AclEntryDeleted instance is to be deleted.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code AclEntryDeleted} which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'AclEntryDeleted' format.
     */
    public static AclEntryDeleted fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code AclEntryDeleted} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new AclEntryDeleted instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code AclEntryDeleted} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * 'AclEntryDeleted' format.
     */
    public static AclEntryDeleted fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<AclEntryDeleted>(TYPE, jsonObject).deserialize((revision, timestamp) -> {
            final String extractedThingId = jsonObject.getValueOrThrow(JsonFields.THING_ID);
            final String authSubjectId = jsonObject.getValueOrThrow(JSON_AUTHORIZATION_SUBJECT);
            final AuthorizationSubject extractedAuthSubject = AuthorizationModelFactory.newAuthSubject(authSubjectId);

            return of(extractedThingId, extractedAuthSubject, revision, timestamp, dittoHeaders);
        });
    }

    /**
     * Returns the Authorized Subject of the deleted ACL entry.
     *
     * @return the Authorization Subject of the deleted ACL entry.
     */
    public AuthorizationSubject getAuthorizationSubject() {
        return authorizationSubject;
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/acl/" + authorizationSubject.getId();
        return JsonPointer.of(path);
    }

    @Override
    public AclEntryDeleted setRevision(final long revision) {
        return of(getThingId(), authorizationSubject, revision, getTimestamp().orElse(null), getDittoHeaders());
    }

    @Override
    public AclEntryDeleted setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getThingId(), authorizationSubject, getRevision(), getTimestamp().orElse(null), dittoHeaders);
    }

    @Override
    protected void appendPayloadAndBuild(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_AUTHORIZATION_SUBJECT, authorizationSubject.getId(), predicate);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(authorizationSubject);
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
        final AclEntryDeleted that = (AclEntryDeleted) o;
        return that.canEqual(this) && Objects.equals(authorizationSubject, that.authorizationSubject)
                && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof AclEntryDeleted;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", authorizationSubject=" + authorizationSubject
                + "]";
    }

}
