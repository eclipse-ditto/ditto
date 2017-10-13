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
package org.eclipse.ditto.signals.commands.things.modify;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

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
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to a {@link DeleteAclEntry} command.
 */
@Immutable
public final class DeleteAclEntryResponse extends AbstractCommandResponse<DeleteAclEntryResponse> implements
        ThingModifyCommandResponse<DeleteAclEntryResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + DeleteAclEntry.NAME;

    static final JsonFieldDefinition<String> JSON_AUTHORIZATION_SUBJECT =
            JsonFactory.newStringFieldDefinition("authorizationSubject", FieldType.REGULAR, JsonSchemaVersion.V_1);

    private final String thingId;
    private final AuthorizationSubject authorizationSubject;

    private DeleteAclEntryResponse(final String thingId, final AuthorizationSubject authorizationSubject,
            final DittoHeaders dittoHeaders) {

        super(TYPE, HttpStatusCode.NO_CONTENT, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thing ID");
        this.authorizationSubject = checkNotNull(authorizationSubject, "authorization subject");
    }

    /**
     * Creates a response to a {@link DeleteAclEntry} command.
     *
     * @param thingId the Thing ID of the deleted ACL entry.
     * @param authorizationSubject the deleted authorization subject.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static DeleteAclEntryResponse of(final String thingId, final AuthorizationSubject authorizationSubject,
            final DittoHeaders dittoHeaders) {

        return new DeleteAclEntryResponse(thingId, authorizationSubject, dittoHeaders);
    }

    /**
     * Creates a response to a {@link DeleteAclEntry} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static DeleteAclEntryResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link DeleteAclEntry} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static DeleteAclEntryResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<DeleteAclEntryResponse>(TYPE, jsonObject)
                .deserialize((statusCode) -> {
                    final String thingId =
                            jsonObject.getValueOrThrow(ThingModifyCommandResponse.JsonFields.JSON_THING_ID);
                    final String authSubjectId = jsonObject.getValueOrThrow(JSON_AUTHORIZATION_SUBJECT);
                    final AuthorizationSubject extractedAuthSubject =
                            AuthorizationModelFactory.newAuthSubject(authSubjectId);

                    return of(thingId, extractedAuthSubject, dittoHeaders);
                });
    }

    @Override
    public String getThingId() {
        return thingId;
    }

    /**
     * Returns the deleted {@code AuthorizationSubject}.
     *
     * @return the authorization subject.
     */
    public AuthorizationSubject getAuthorizationSubject() {
        return authorizationSubject;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonFactory.newPointer("/acl/" + authorizationSubject.getId());
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingModifyCommandResponse.JsonFields.JSON_THING_ID, thingId, predicate);
        jsonObjectBuilder.set(JSON_AUTHORIZATION_SUBJECT, authorizationSubject.getId(), predicate);
    }

    @Override
    public DeleteAclEntryResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, authorizationSubject, dittoHeaders);
    }

    /**
     * DeleteAclEntryResponse is only available in JsonSchemaVersion V_1.
     *
     * @return the supported JsonSchemaVersions of DeleteAclEntryResponse.
     */
    @Override
    public JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_1};
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DeleteAclEntryResponse that = (DeleteAclEntryResponse) o;
        return that.canEqual(this) && super.equals(o) && Objects.equals(thingId, that.thingId) &&
                Objects.equals(authorizationSubject, that.authorizationSubject);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof DeleteAclEntryResponse);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, authorizationSubject);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId
                + ", authorizationSubject=" + authorizationSubject + "]";
    }

}
