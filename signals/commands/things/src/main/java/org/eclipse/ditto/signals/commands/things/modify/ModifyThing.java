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

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclNotAllowedException;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;
import org.eclipse.ditto.signals.commands.things.exceptions.PolicyIdNotAllowedException;

/**
 * This command modifies an existing Thing. It contains the full {@link Thing} including the Thing ID which should be
 * used for modification.
 */
public final class ModifyThing extends AbstractCommand<ModifyThing> implements ThingModifyCommand<ModifyThing> {

    /**
     * Name of the "Modify Thing" command.
     */
    public static final String NAME = "modifyThing";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<JsonObject> JSON_THING =
            JsonFactory.newJsonObjectFieldDefinition("thing", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    /**
     * Json Field definition for the optional initial "inline" policy when creating a Thing.
     */
    public static final JsonFieldDefinition<JsonObject> JSON_INITIAL_POLICY =
            JsonFactory.newJsonObjectFieldDefinition("initialPolicy", FieldType.REGULAR, JsonSchemaVersion.V_2);

    /**
     * Json Field definition for the optional initial "inline" policy for usage in getEntity().
     */
    public static final JsonFieldDefinition<JsonObject> JSON_INLINE_POLICY =
            JsonFactory.newJsonObjectFieldDefinition("_policy", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final String thingId;
    private final Thing thing;
    @Nullable private final JsonObject initialPolicy;

    private ModifyThing(final String thingId, final Thing thing, @Nullable final JsonObject initialPolicy,
            final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.thingId = thingId;
        this.thing = thing;
        this.initialPolicy = initialPolicy;
    }

    /**
     * Returns a command for modifying a thing which is passed as argument.
     *
     * @param thingId the Thing's ID.
     * @param thing the {@link Thing} to modify.
     * @param initialPolicy the initial {@code Policy} to set for the Thing when creating it - may be null.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws AclNotAllowedException if the passed {@code thing} contained a Policy or Policy ID but the command was
     * created via API version {@link JsonSchemaVersion#V_1}.
     * @throws PolicyIdNotAllowedException if the passed {@code thing} contained an ACL but the command was created via
     * an API version greater than {@link JsonSchemaVersion#V_1}.
     */
    public static ModifyThing of(final String thingId, final Thing thing, @Nullable final JsonObject initialPolicy,
            final DittoHeaders dittoHeaders) {
        Objects.requireNonNull(thingId, "The Thing identifier must not be null!");
        Objects.requireNonNull(thing, "The modified Thing must not be null!");
        ensureAuthorizationMatchesSchemaVersion(thingId, thing, initialPolicy, dittoHeaders);
        return new ModifyThing(thingId, thing, initialPolicy, dittoHeaders);
    }

    /**
     * Creates a new {@code ModifyThing} from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifyThing fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code ModifyThing} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain a field for {@link
     * ThingModifyCommand.JsonFields#JSON_THING_ID} or {@link #JSON_THING}.
     */
    public static ModifyThing fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<ModifyThing>(TYPE, jsonObject).deserialize(() -> {
            final JsonObject thingJsonObject = jsonObject.getValueOrThrow(JSON_THING);
            final Thing extractedThing = ThingsModelFactory.newThing(thingJsonObject);
            final JsonObject initialPolicyObject = jsonObject.getValue(JSON_INITIAL_POLICY).orElse(null);

            final Optional<String> optionalThingId = jsonObject.getValue(ThingModifyCommand.JsonFields.JSON_THING_ID);
            final String thingId = optionalThingId.orElseGet(() -> extractedThing.getId().orElseThrow(() ->
                    new JsonMissingFieldException(ThingModifyCommand.JsonFields.JSON_THING_ID)
            ));

            return of(thingId, extractedThing, initialPolicyObject, dittoHeaders);
        });
    }

    /**
     * Ensures that the command will not contain inconsistent authorization information. <ul> <li>{@link
     * org.eclipse.ditto.model.base.json.JsonSchemaVersion#V_1} commands may not contain policy information.</li>
     * <li>{@link org.eclipse.ditto.model.base.json.JsonSchemaVersion#LATEST} commands may not contain ACL
     * information.</li> </ul>
     */
    private static void ensureAuthorizationMatchesSchemaVersion(final String thingId,
            final Thing thing,
            @Nullable final JsonObject initialPolicy,
            final DittoHeaders dittoHeaders) {

        final JsonSchemaVersion version = dittoHeaders.getSchemaVersion().orElse(JsonSchemaVersion.LATEST);
        if (JsonSchemaVersion.V_1.equals(version)) {
            // v1 commands may not contain policy information
            final boolean containsPolicy = null != initialPolicy || thing.getPolicyId().isPresent();
            if (containsPolicy) {
                throw PolicyIdNotAllowedException
                        .newBuilder(thingId)
                        .dittoHeaders(dittoHeaders)
                        .build();
            }
        } else {
            // v2 commands may not contain ACL information
            final boolean isCommandAclEmpty = thing
                    .getAccessControlList()
                    .map(AccessControlList::isEmpty)
                    .orElse(true);
            if (!isCommandAclEmpty) {
                throw AclNotAllowedException
                        .newBuilder(thingId)
                        .dittoHeaders(dittoHeaders)
                        .build();
            }
        }
    }

    /**
     * Returns the {@code Thing} to modify.
     *
     * @return the Thing to modify.
     */
    public Thing getThing() {
        return thing;
    }

    @Override
    public String getThingId() {
        return thingId;
    }

    /**
     * @return the initial {@code Policy} if there should be one applied when creating the Thing.
     */
    public Optional<JsonObject> getInitialPolicy() {
        return Optional.ofNullable(initialPolicy);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        final JsonObject thingJson = thing.toJson(schemaVersion, FieldType.regularOrSpecial());
        final JsonObject fullThingJson =
                getInitialPolicy().map(ip -> thingJson.set(JSON_INLINE_POLICY, ip)).orElse(thingJson);
        return Optional.of(fullThingJson);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingModifyCommand.JsonFields.JSON_THING_ID, thingId, predicate);
        jsonObjectBuilder.set(JSON_THING, thing.toJson(schemaVersion, thePredicate), predicate);
        if (initialPolicy != null) {
            jsonObjectBuilder.set(JSON_INITIAL_POLICY, initialPolicy, predicate);
        }
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public ModifyThing setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, thing, initialPolicy, dittoHeaders);
    }

    @Override
    public boolean changesAuthorization() {
        return thing.getPolicyId().isPresent() ||
                thing.getAccessControlList().isPresent();
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, thing, initialPolicy);
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (null == obj || getClass() != obj.getClass()) {
            return false;
        }
        final ModifyThing that = (ModifyThing) obj;
        return that.canEqual(this) && Objects.equals(thingId, that.thingId)
                && Objects.equals(thing, that.thing) && Objects.equals(initialPolicy, that.initialPolicy) &&
                super.equals(obj);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof ModifyThing);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId + ", thing=" + thing +
                ", initialPolicy=" + initialPolicy + "]";
    }

}
