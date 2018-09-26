/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
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
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.ThingIdValidator;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;

/**
 * This command modifies a single ACL Entry of a Thing.
 */
@Immutable
public final class ModifyAclEntry extends AbstractCommand<ModifyAclEntry>
        implements ThingModifyCommand<ModifyAclEntry> {

    /**
     * Name of this command.
     */
    public static final String NAME = "modifyAclEntry";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<JsonObject> JSON_ACL_ENTRY =
            JsonFactory.newJsonObjectFieldDefinition("aclEntry", FieldType.REGULAR, JsonSchemaVersion.V_1);

    private final String thingId;
    private final AclEntry aclEntry;

    private ModifyAclEntry(final String thingId, final AclEntry aclEntry, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        ThingIdValidator.getInstance().accept(thingId, dittoHeaders);
        this.thingId = thingId;
        this.aclEntry = checkNotNull(aclEntry, "ACL Entry which should be applied");
    }

    /**
     * Returns a command for modifying a single ACL entry of a Thing.
     *
     * @param thingId the ID of the Thing on which to modify the ACL Entry.
     * @param aclEntry the ACL Entry which should be modified.
     * @param dittoHeaders the headers of the command.
     * @return a command for modifying the provided ACL Entry.
     * @throws NullPointerException if any argument but {@code thingId} is {@code null}.
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if the parsed thing ID did not comply to {@link
     * org.eclipse.ditto.model.things.Thing#ID_REGEX}.
     */
    public static ModifyAclEntry of(final String thingId, final AclEntry aclEntry,
            final DittoHeaders dittoHeaders) {

        return new ModifyAclEntry(thingId, aclEntry, dittoHeaders);
    }

    /**
     * Creates a new {@code ModifyAclEntry} from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if the parsed thing ID did not comply to {@link
     * org.eclipse.ditto.model.things.Thing#ID_REGEX}.
     */
    public static ModifyAclEntry fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code ModifyAclEntry} from a JSON object.
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
    public static ModifyAclEntry fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<ModifyAclEntry>(TYPE, jsonObject).deserialize(() -> {
            final String thingId = jsonObject.getValueOrThrow(ThingModifyCommand.JsonFields.JSON_THING_ID);
            final JsonObject aclEntryJsonObject = jsonObject.getValueOrThrow(JSON_ACL_ENTRY);
            final AclEntry extractedAclEntry = ThingsModelFactory.newAclEntry(aclEntryJsonObject);

            return of(thingId, extractedAclEntry, dittoHeaders);
        });
    }

    /**
     * Returns the {@code AclEntry} to be modified.
     *
     * @return the ACL Entry.
     */
    public AclEntry getAclEntry() {
        return aclEntry;
    }

    @Override
    public String getThingId() {
        return thingId;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.ofNullable(aclEntry.toJson(FieldType.regularOrSpecial()));
    }

    @Override
    public JsonPointer getResourcePath() {
        final String path = "/acl/" + aclEntry.getAuthorizationSubject().getId();
        return JsonPointer.of(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingModifyCommand.JsonFields.JSON_THING_ID, thingId, predicate);
        jsonObjectBuilder.set(JSON_ACL_ENTRY, aclEntry.toJson(schemaVersion, thePredicate), predicate);
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public ModifyAclEntry setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, aclEntry, dittoHeaders);
    }

    @Override
    public boolean changesAuthorization() {
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, aclEntry);
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067", "OverlyComplexMethod"})
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final ModifyAclEntry that = (ModifyAclEntry) obj;
        return that.canEqual(this) && Objects.equals(thingId, that.thingId) && Objects.equals(aclEntry, that.aclEntry)
                && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof ModifyAclEntry);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId + ", aclEntry=" + aclEntry
                + "]";
    }

}
