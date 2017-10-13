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
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.Permissions;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to a {@link ModifyAclEntry} command.
 */
@Immutable
public final class ModifyAclEntryResponse extends AbstractCommandResponse<ModifyAclEntryResponse> implements
        ThingModifyCommandResponse<ModifyAclEntryResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ModifyAclEntry.NAME;

    static final JsonFieldDefinition<JsonObject> JSON_ACL_ENTRY =
            JsonFactory.newJsonObjectFieldDefinition("aclEntry", FieldType.REGULAR, JsonSchemaVersion.V_1);

    private final String thingId;
    private final AclEntry modifiedAclEntry;

    private ModifyAclEntryResponse(final String thingId, final AclEntry modifiedAclEntry,
            final HttpStatusCode statusCode,
            final DittoHeaders dittoHeaders) {

        super(TYPE, statusCode, dittoHeaders);
        this.thingId = checkNotNull(thingId, "Thing ID");
        this.modifiedAclEntry = checkNotNull(modifiedAclEntry, "ACL entry");
    }

    /**
     * Returns a new {@code ModifyAclEntryResponse} for a created AclEntry. This corresponds to the HTTP status code
     * {@link HttpStatusCode#CREATED}.
     *
     * @param thingId the Thing ID of the created ACL entry.
     * @param aclEntry the created AclEntry.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a created Thing.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyAclEntryResponse created(final String thingId, final AclEntry aclEntry,
            final DittoHeaders dittoHeaders) {
        return new ModifyAclEntryResponse(thingId, aclEntry, HttpStatusCode.CREATED, dittoHeaders);
    }

    /**
     * Returns a new {@code ModifyAclEntryResponse} for a modified AclEntry. This corresponds to the HTTP status code
     * {@link HttpStatusCode#NO_CONTENT}.
     *
     * @param thingId the Thing ID of the modified ACL entry.
     * @param aclEntry the modified AclEntry.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a modified Thing.
     * @throws NullPointerException any argument is {@code null}.
     */
    public static ModifyAclEntryResponse modified(final String thingId, final AclEntry aclEntry,
            final DittoHeaders dittoHeaders) {
        return new ModifyAclEntryResponse(thingId, aclEntry, HttpStatusCode.NO_CONTENT, dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyAcl} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifyAclEntryResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyAclEntry} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifyAclEntryResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<ModifyAclEntryResponse>(TYPE, jsonObject)
                .deserialize((statusCode) -> {
                    final String thingId =
                            jsonObject.getValueOrThrow(ThingModifyCommandResponse.JsonFields.JSON_THING_ID);
                    final JsonObject aclEntryJsonObject = jsonObject.getValueOrThrow(JSON_ACL_ENTRY);
                    final AclEntry extractedAclEntry = ThingsModelFactory.newAclEntry(aclEntryJsonObject);

                    return new ModifyAclEntryResponse(thingId, extractedAclEntry, statusCode, dittoHeaders);
                });
    }

    @Override
    public String getThingId() {
        return thingId;
    }

    /**
     * Returns the modified ACL entry.
     *
     * @return the ACL entry.
     */
    public AclEntry getAclEntry() {
        return modifiedAclEntry;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        if (!isCreated()) {
            return Optional.empty();
        }
        final Permissions permissions = modifiedAclEntry.getPermissions();
        return Optional.of(permissions.toJson(schemaVersion, FieldType.notHidden()));
    }

    private boolean isCreated() {
        return HttpStatusCode.CREATED == getStatusCode();
    }

    @Override
    public JsonPointer getResourcePath() {
        final AuthorizationSubject authorizationSubject = modifiedAclEntry.getAuthorizationSubject();
        return JsonFactory.newPointer("/acl/" + authorizationSubject.getId());
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {

        final Predicate<JsonField> p = schemaVersion.and(predicate);
        jsonObjectBuilder.set(ThingModifyCommandResponse.JsonFields.JSON_THING_ID, thingId, p);
        jsonObjectBuilder.set(JSON_ACL_ENTRY, modifiedAclEntry.toJson(schemaVersion, predicate), p);
    }

    @Override
    public ModifyAclEntryResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ModifyAclEntryResponse(thingId, modifiedAclEntry, getStatusCode(), dittoHeaders);
    }

    /**
     * ModifyAclEntryResponse is only available in JsonSchemaVersion V_1.
     *
     * @return the supported JsonSchemaVersions of ModifyAclEntryResponse.
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
        final ModifyAclEntryResponse that = (ModifyAclEntryResponse) o;
        return that.canEqual(this) && Objects.equals(thingId, that.thingId)
                && Objects.equals(modifiedAclEntry, that.modifiedAclEntry) && super.equals(o);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifyAclEntryResponse;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, modifiedAclEntry);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId + ", aclEntryCreated=" +
                modifiedAclEntry + "]";
    }

}
