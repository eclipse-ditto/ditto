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
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.ThingIdValidator;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;

/**
 * This command deletes one single ACL entry of a Thing. Contains the {@code authorizationSubject} of the ACL to delete.
 */
@Immutable
public final class DeleteAclEntry extends AbstractCommand<DeleteAclEntry>
        implements ThingModifyCommand<DeleteAclEntry> {

    /**
     * Name of this command.
     */
    public static final String NAME = "deleteAclEntry";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_AUTHORIZATION_SUBJECT =
            JsonFactory.newStringFieldDefinition("authorizationSubject", FieldType.REGULAR, JsonSchemaVersion.V_1);

    private final String thingId;
    private final AuthorizationSubject authorizationSubject;

    private DeleteAclEntry(final AuthorizationSubject authorizationSubject, final String thingId,
            final DittoHeaders dittoHeaders) {

        super(TYPE, dittoHeaders);
        ThingIdValidator.getInstance().accept(thingId, dittoHeaders);
        this.thingId = thingId;
        this.authorizationSubject = checkNotNull(authorizationSubject, "authorization subject to be deleted");
    }

    /**
     * Returns a command for deleting one single ACL entry of a Thing. The ACL entry's {@code authorizationSubject} is
     * passed as identifier of which ACL entry to delete.
     *
     * @param thingId the Thing's key.
     * @param authorizationSubject the subject of the ACL entry to delete.
     * @param dittoHeaders the headers of the command.
     * @return a command for deleting a Thing's ACL entry.
     * @throws NullPointerException if any argument but {@code thingId} is {@code null}.
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if {@code thingId} does not comply to {@link
     * org.eclipse.ditto.model.things.Thing#ID_REGEX}.
     */
    public static DeleteAclEntry of(final String thingId, final AuthorizationSubject authorizationSubject,
            final DittoHeaders dittoHeaders) {

        return new DeleteAclEntry(authorizationSubject, thingId, dittoHeaders);
    }

    /**
     * Creates a new {@code DeleteAclEntry} from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if {@code thingId} does not comply to {@link
     * org.eclipse.ditto.model.things.Thing#ID_REGEX}.
     */
    public static DeleteAclEntry fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code DeleteAclEntry} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if {@code thingId} does not comply to {@link
     * org.eclipse.ditto.model.things.Thing#ID_REGEX}.
     */
    public static DeleteAclEntry fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<DeleteAclEntry>(TYPE, jsonObject).deserialize(() -> {
            final String thingId = jsonObject.getValueOrThrow(ThingModifyCommand.JsonFields.JSON_THING_ID);
            final String authSubjectId = jsonObject.getValueOrThrow(JSON_AUTHORIZATION_SUBJECT);
            final AuthorizationSubject extractedAuthSubject =
                    AuthorizationModelFactory.newAuthSubject(authSubjectId);

            return of(thingId, extractedAuthSubject, dittoHeaders);
        });
    }

    /**
     * Returns the subject of the ACL entry to be deleted.
     *
     * @return the subject.
     */
    public AuthorizationSubject getAuthorizationSubject() {
        return authorizationSubject;
    }

    @Override
    public String getThingId() {
        return thingId;
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/acl/" + authorizationSubject.getId();
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingModifyCommand.JsonFields.JSON_THING_ID, thingId, predicate);
        jsonObjectBuilder.set(JSON_AUTHORIZATION_SUBJECT, authorizationSubject.getId(), predicate);
    }

    @Override
    public Category getCategory() {
        return Category.DELETE;
    }

    @Override
    public DeleteAclEntry setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, authorizationSubject, dittoHeaders);
    }

    @Override
    public boolean changesAuthorization() {
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, authorizationSubject);
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final DeleteAclEntry that = (DeleteAclEntry) obj;
        return that.canEqual(this) && Objects.equals(thingId, that.thingId)
                && Objects.equals(authorizationSubject, that.authorizationSubject) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof DeleteAclEntry);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId + ", authorizationSubject="
                + authorizationSubject + "]";
    }

}
