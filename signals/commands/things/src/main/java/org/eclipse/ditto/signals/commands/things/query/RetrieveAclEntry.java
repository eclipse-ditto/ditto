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
package org.eclipse.ditto.signals.commands.things.query;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
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
 * Command which retrieves the ACL entry of a {@code Thing} based on the passed in ID and Authorization Subject.
 */
@Immutable
public final class RetrieveAclEntry extends AbstractCommand<RetrieveAclEntry>
        implements ThingQueryCommand<RetrieveAclEntry> {

    /**
     * Name of this command.
     */
    public static final String NAME = "retrieveAclEntry";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_AUTHORIZATION_SUBJECT =
            JsonFactory.newStringFieldDefinition("authorizationSubject", FieldType.REGULAR, JsonSchemaVersion.V_1);

    private final String thingId;
    private final AuthorizationSubject authorizationSubject;
    @Nullable
    private final JsonFieldSelector selectedFields;

    private RetrieveAclEntry(final AuthorizationSubject authorizationSubject,
            @Nullable final JsonFieldSelector selectedFields,
            final String thingId,
            final DittoHeaders dittoHeaders) {

        super(TYPE, dittoHeaders);
        ThingIdValidator.getInstance().accept(thingId, dittoHeaders);
        this.thingId = thingId;
        this.authorizationSubject =
                checkNotNull(authorizationSubject, "Authorization Subject whose ACL Entry to retrieve");
        this.selectedFields = selectedFields;
    }

    /**
     * Returns a command for retrieving a specific ACL entry of a Thing with the given ID.
     *
     * @param thingId the ID of a single Thing whose ACL entry will be retrieved by this command.
     * @param authorizationSubject the specified subject for which to retrieve the ACL entry for.
     * @param dittoHeaders the headers of the command.
     * @return a Command for retrieving one ACL entry of the Thing with the {@code thingId} as its ID which is readable
     * from the passed authorization context.
     * @throws NullPointerException if any argument but {@code thingId} is {@code null}.
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if the parsed thing ID did not comply to {@link
     * org.eclipse.ditto.model.things.Thing#ID_REGEX}.
     */
    public static RetrieveAclEntry of(final String thingId, final AuthorizationSubject authorizationSubject,
            final DittoHeaders dittoHeaders) {

        return new RetrieveAclEntry(authorizationSubject, null, thingId, dittoHeaders);
    }

    /**
     * Returns a command for retrieving a specific ACL entry of a Thing with the given ID.
     *
     * @param thingId the ID of a single Thing whose ACL entry will be retrieved by this command.
     * @param authorizationSubject the specified subject for which to retrieve the ACL entry for.
     * @param selectedFields defines the fields of the JSON representation of the ACL entry to retrieve.
     * @param dittoHeaders the headers of the command.
     * @return a Command for retrieving one ACL entry of the Thing with the {@code thingId} as its ID which is readable
     * from the passed authorization context.
     * @throws NullPointerException if {@code authorizationSubject} or {@code dittoHeaders} is {@code null}.
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if the parsed thing ID did not comply to {@link
     * org.eclipse.ditto.model.things.Thing#ID_REGEX}.
     */
    public static RetrieveAclEntry of(final String thingId,
            final AuthorizationSubject authorizationSubject,
            @Nullable final JsonFieldSelector selectedFields,
            final DittoHeaders dittoHeaders) {

        return new RetrieveAclEntry(authorizationSubject, selectedFields, thingId, dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveAclEntry} from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if any argument is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if the parsed thing ID did not comply to {@link
     * org.eclipse.ditto.model.things.Thing#ID_REGEX}.
     */
    public static RetrieveAclEntry fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveAclEntry} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if the parsed thing ID did not comply to {@link
     * org.eclipse.ditto.model.things.Thing#ID_REGEX}.
     */
    public static RetrieveAclEntry fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<RetrieveAclEntry>(TYPE, jsonObject).deserialize(() -> {
            final String thingId = jsonObject.getValueOrThrow(ThingQueryCommand.JsonFields.JSON_THING_ID);
            final String authSubjectId = jsonObject.getValueOrThrow(JSON_AUTHORIZATION_SUBJECT);
            final AuthorizationSubject extractedAuthSubject = AuthorizationModelFactory.newAuthSubject(authSubjectId);

            return of(thingId, extractedAuthSubject, dittoHeaders);
        });
    }

    /**
     * Returns the {@code AuthorizationSubject} to retrieve the {@code AclEntry} for.
     *
     * @return the AuthorizationSubject.
     */
    public AuthorizationSubject getAuthorizationSubject() {
        return authorizationSubject;
    }

    @Override
    public Optional<JsonFieldSelector> getSelectedFields() {
        return Optional.ofNullable(selectedFields);
    }

    /**
     * RetrieveAclEntry is only available in JsonSchemaVersion V_1.
     *
     * @return the supported JsonSchemaVersions of RetrieveAclEntry.
     */
    @Override
    public JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_1};
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
        jsonObjectBuilder.set(ThingQueryCommand.JsonFields.JSON_THING_ID, thingId, predicate);
        jsonObjectBuilder.set(JSON_AUTHORIZATION_SUBJECT, authorizationSubject.getId(), predicate);
    }

    @Override
    public RetrieveAclEntry setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new RetrieveAclEntry(authorizationSubject, selectedFields, thingId, dittoHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, authorizationSubject, selectedFields);
    }

    @SuppressWarnings("squid:S1067")
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final RetrieveAclEntry that = (RetrieveAclEntry) obj;
        return that.canEqual(this) && Objects.equals(thingId, that.thingId)
                && Objects.equals(authorizationSubject, that.authorizationSubject)
                && Objects.equals(selectedFields, that.selectedFields) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveAclEntry;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId + ", authorizationSubject="
                + authorizationSubject + ", selectedFields=" + selectedFields + "]";
    }

}
