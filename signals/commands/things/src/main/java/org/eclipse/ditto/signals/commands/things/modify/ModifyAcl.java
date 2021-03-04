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
import org.eclipse.ditto.model.base.json.JsonParsableCommand;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;

/**
 * This command modifies the complete ACL of a Thing.
 *
 * @deprecated AccessControlLists belong to deprecated API version 1. Use API version 2 with policies instead.
 */
@Deprecated
@Immutable
@JsonParsableCommand(typePrefix = ModifyAcl.TYPE_PREFIX, name = ModifyAcl.NAME)
public final class ModifyAcl extends AbstractCommand<ModifyAcl> implements ThingModifyCommand<ModifyAcl> {

    /**
     * Name of this command.
     */
    public static final String NAME = "modifyAcl";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<JsonObject> JSON_ACCESS_CONTROL_LIST =
            JsonFactory.newJsonObjectFieldDefinition("acl", FieldType.REGULAR, JsonSchemaVersion.V_1);

    private final ThingId thingId;
    private final AccessControlList accessControlList;

    private ModifyAcl(final AccessControlList accessControlList, final ThingId thingId,
            final DittoHeaders dittoHeaders) {

        super(TYPE, dittoHeaders);
        this.thingId = checkNotNull(thingId, "Thing ID");
        this.accessControlList = checkNotNull(accessControlList, "ACL which should be applied");
    }

    /**
     * Returns a command for modifying the complete ACL of a Thing.
     *
     * @param thingId the ID of the Thing on which to modify the complete ACL.
     * @param accessControlList the ACL.
     * @param dittoHeaders the headers of the command.
     * @return a command for modifying the provided complete ACL.
     * @throws NullPointerException if any argument but {@code thingId} is {@code null}.
     * @deprecated Thing ID is now typed. Use
     * {@link #of(org.eclipse.ditto.model.things.ThingId, org.eclipse.ditto.model.things.AccessControlList, org.eclipse.ditto.model.base.headers.DittoHeaders)}
     * instead.
     */
    @Deprecated
    public static ModifyAcl of(final String thingId, final AccessControlList accessControlList,
            final DittoHeaders dittoHeaders) {

        return of(ThingId.of(thingId), accessControlList, dittoHeaders);
    }

    /**
     * Returns a command for modifying the complete ACL of a Thing.
     *
     * @param thingId the ID of the Thing on which to modify the complete ACL.
     * @param accessControlList the ACL.
     * @param dittoHeaders the headers of the command.
     * @return a command for modifying the provided complete ACL.
     * @throws NullPointerException if any argument but {@code thingId} is {@code null}.
     */
    public static ModifyAcl of(final ThingId thingId, final AccessControlList accessControlList,
            final DittoHeaders dittoHeaders) {

        return new ModifyAcl(accessControlList, thingId, dittoHeaders);
    }

    /**
     * Creates a new {@code ModifyAcl} from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if the parsed thing ID did not comply to {@link
     * org.eclipse.ditto.model.base.entity.id.RegexPatterns#ID_REGEX}.
     */
    public static ModifyAcl fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code ModifyAcl} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if the parsed thing ID did not comply to {@link
     * org.eclipse.ditto.model.base.entity.id.RegexPatterns#ID_REGEX}.
     */
    public static ModifyAcl fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<ModifyAcl>(TYPE, jsonObject).deserialize(() -> {
            final String extractedThingId = jsonObject.getValueOrThrow(ThingModifyCommand.JsonFields.JSON_THING_ID);
            final ThingId thingId = ThingId.of(extractedThingId);
            final JsonObject aclJsonObject = jsonObject.getValueOrThrow(JSON_ACCESS_CONTROL_LIST);
            final AccessControlList extractedAccessControlList = ThingsModelFactory.newAcl(aclJsonObject);

            return of(thingId, extractedAccessControlList, dittoHeaders);
        });
    }

    /**
     * Returns the {@code AccessControlList} to be modified.
     *
     * @return the AccessControlList.
     */
    public AccessControlList getAccessControlList() {
        return accessControlList;
    }

    @Override
    public ThingId getThingEntityId() {
        return thingId;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return Optional.ofNullable(accessControlList.toJson(FieldType.regularOrSpecial()));
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/acl");
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingModifyCommand.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(JSON_ACCESS_CONTROL_LIST, accessControlList.toJson(schemaVersion, predicate), predicate);
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public ModifyAcl setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, accessControlList, dittoHeaders);
    }

    @Override
    public boolean changesAuthorization() {
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, accessControlList);
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
        final ModifyAcl that = (ModifyAcl) obj;
        return that.canEqual(this) && Objects.equals(thingId, that.thingId)
                && Objects.equals(accessControlList, that.accessControlList) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof ModifyAcl);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId + ", accessControlList="
                + accessControlList + "]";
    }

}
