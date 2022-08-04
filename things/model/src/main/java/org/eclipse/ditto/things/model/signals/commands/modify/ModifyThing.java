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
package org.eclipse.ditto.things.model.signals.commands.modify;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.Placeholders;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandSizeValidator;
import org.eclipse.ditto.things.model.signals.commands.exceptions.PoliciesConflictingException;

/**
 * This command modifies an existing Thing. It contains the full {@link org.eclipse.ditto.things.model.Thing} including the Thing ID which should be
 * used for modification.
 */
@JsonParsableCommand(typePrefix = ThingCommand.TYPE_PREFIX, name = ModifyThing.NAME)
public final class ModifyThing extends AbstractCommand<ModifyThing> implements ThingModifyCommand<ModifyThing> {

    /**
     * Name of the "Modify Thing" command.
     */
    public static final String NAME = "modifyThing";

    /**
     * Type of this command.
     */
    public static final String TYPE = ThingCommand.TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<JsonObject> JSON_THING =
            JsonFactory.newJsonObjectFieldDefinition("thing", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);

    /**
     * Json Field definition for the optional initial "inline" policy when creating a Thing.
     */
    public static final JsonFieldDefinition<JsonObject> JSON_INITIAL_POLICY =
            JsonFactory.newJsonObjectFieldDefinition("initialPolicy", FieldType.REGULAR, JsonSchemaVersion.V_2);

    /**
     * Json Field definition for the optional feature to copy an existing policy.
     */
    public static final JsonFieldDefinition<String> JSON_POLICY_ID_OR_PLACEHOLDER =
            JsonFactory.newStringFieldDefinition("policyIdOrPlaceholder", FieldType.REGULAR, JsonSchemaVersion.V_2);

    /**
     * Json Field definition for the optional feature to copy an existing policy.
     */
    public static final JsonFieldDefinition<String> JSON_COPY_POLICY_FROM =
            JsonFactory.newStringFieldDefinition("_copyPolicyFrom", FieldType.REGULAR, JsonSchemaVersion.V_2);

    /**
     * Json Field definition for the optional initial "inline" policy for usage in getEntity().
     */
    public static final JsonFieldDefinition<JsonObject> JSON_INLINE_POLICY =
            JsonFactory.newJsonObjectFieldDefinition("_policy", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final ThingId thingId;
    private final Thing thing;
    @Nullable private final JsonObject initialPolicy;
    @Nullable private final String policyIdOrPlaceholder;

    private ModifyThing(final ThingId thingId, final Thing thing, @Nullable final JsonObject initialPolicy,
            @Nullable final String policyIdOrPlaceholder, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);

        if (policyIdOrPlaceholder != null && initialPolicy != null) {
            throw PoliciesConflictingException.newBuilder(thingId).dittoHeaders(dittoHeaders).build();
        }

        if (policyIdOrPlaceholder != null && !Placeholders.containsAnyPlaceholder(policyIdOrPlaceholder)) {
            PolicyId.of(policyIdOrPlaceholder); //validates
        }

        this.thingId = checkNotNull(thingId, "Thing ID");
        this.thing = checkNotNull(thing, "Thing");
        this.initialPolicy = initialPolicy;
        this.policyIdOrPlaceholder = policyIdOrPlaceholder;

        final JsonObject thingJsonObject = thing.toJson();

        ThingCommandSizeValidator.getInstance().ensureValidSize(
                thingJsonObject::getUpperBoundForStringSize,
                () -> thingJsonObject.toString().length(),
                () -> dittoHeaders);
    }

    /**
     * Returns a command for modifying a thing which is passed as argument.
     *
     * @param thingId the Thing's ID.
     * @param thing the {@link org.eclipse.ditto.things.model.Thing} to modify.
     * @param initialPolicy the initial {@code Policy} to set for the Thing when creating it - may be null.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyThing of(final ThingId thingId, final Thing thing, @Nullable final JsonObject initialPolicy,
            final DittoHeaders dittoHeaders) {

        return new ModifyThing(thingId, thing, initialPolicy, null, dittoHeaders);
    }

    /**
     * Returns a command for modifying a thing which is passed as argument. The thing will have a policy copied from
     * a policy with the given policyIdOrPlaceholder.
     *
     * @param thingId the Thing's ID.
     * @param thing the {@link org.eclipse.ditto.things.model.Thing} to modify.
     * @param policyIdOrPlaceholder the policy id of the {@code Policy} to copy and set for the Thing when creating it.
     * If its a placeholder it will be resolved to a policy id.
     * Placeholder must be of the syntax: {@code {{ ref:things/<theThingId>/policyId }} }.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyThing withCopiedPolicy(final ThingId thingId, final Thing thing,
            final String policyIdOrPlaceholder, final DittoHeaders dittoHeaders) {

        return new ModifyThing(thingId, thing, null, policyIdOrPlaceholder, dittoHeaders);
    }

    /**
     * Returns a command for modifying a thing which is passed as argument.
     * Only one of the arguments initialPolicy and policyIdOrPlaceholder must not be null. They are both allowed to be
     * null, but not both to not be null at the same time.
     *
     * @param thingId the Thing's ID.
     * @param thing the {@link org.eclipse.ditto.things.model.Thing} to modify.
     * @param policyIdOrPlaceholder the policy id of the {@code Policy} to copy and set for the Thing when creating it.
     * If its a placeholder it will be resolved to a policy id.
     * Placeholder must be of the syntax: {@code {{ ref:things/<theThingId>/policyId }} }.
     * @param initialPolicy the initial {@code Policy} to set for the Thing when creating it - may be null.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.things.model.signals.commands.exceptions.PoliciesConflictingException if the passed {@code initialPolicy} is not null and the passed
     * {@code policyIdOrPlaceholder} is not null.
     */
    public static ModifyThing of(final ThingId thingId, final Thing thing, @Nullable final JsonObject initialPolicy,
            @Nullable final String policyIdOrPlaceholder, final DittoHeaders dittoHeaders) {

        return new ModifyThing(thingId, thing, initialPolicy, policyIdOrPlaceholder, dittoHeaders);
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
     * org.eclipse.ditto.things.model.signals.commands.ThingCommand.JsonFields#JSON_THING_ID} or {@link #JSON_THING}.
     */
    public static ModifyThing fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<ModifyThing>(TYPE, jsonObject).deserialize(() -> {
            final JsonObject thingJsonObject = jsonObject.getValueOrThrow(JSON_THING);
            final Thing extractedThing = ThingsModelFactory.newThing(thingJsonObject);
            final JsonObject initialPolicyObject = jsonObject.getValue(JSON_INITIAL_POLICY).orElse(null);
            final String policyIdOrPlaceholder = jsonObject.getValue(JSON_POLICY_ID_OR_PLACEHOLDER).orElse(null);

            final Optional<String> optionalThingId = jsonObject.getValue(ThingCommand.JsonFields.JSON_THING_ID);
            final ThingId thingId = optionalThingId
                    .map(ThingId::of)
                    .orElseGet(() -> extractedThing.getEntityId().orElseThrow(() ->
                            new JsonMissingFieldException(ThingCommand.JsonFields.JSON_THING_ID)
                    ));

            return of(thingId, extractedThing, initialPolicyObject, policyIdOrPlaceholder, dittoHeaders);
        });
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
    public ThingId getEntityId() {
        return thingId;
    }

    /**
     * @return the initial {@code Policy} if there should be one applied when creating the Thing.
     */
    public Optional<JsonObject> getInitialPolicy() {
        return Optional.ofNullable(initialPolicy);
    }

    /**
     * @return The policy id of the {@code Policy} to copy and set for the Thing when creating it.
     * Could also be a placeholder like: {{ ref:things/theThingId/policyId }}.
     */
    public Optional<String> getPolicyIdOrPlaceholder() {
        return Optional.ofNullable(policyIdOrPlaceholder);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        final JsonObject thingJson = thing.toJson(schemaVersion, FieldType.regularOrSpecial());
        final JsonObject withInlinePolicyThingJson =
                getInitialPolicy().map(ip -> thingJson.set(JSON_INLINE_POLICY, ip)).orElse(thingJson);
        final JsonObject fullThingJson = getPolicyIdOrPlaceholder().map(
                containedPolicyIdOrPlaceholder -> withInlinePolicyThingJson.set(JSON_COPY_POLICY_FROM,
                        containedPolicyIdOrPlaceholder)).orElse(withInlinePolicyThingJson);

        return Optional.of(fullThingJson);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingCommand.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(JSON_THING, thing.toJson(schemaVersion, thePredicate), predicate);
        if (initialPolicy != null) {
            jsonObjectBuilder.set(JSON_INITIAL_POLICY, initialPolicy, predicate);
        }
        if (policyIdOrPlaceholder != null) {
            jsonObjectBuilder.set(JSON_POLICY_ID_OR_PLACEHOLDER, policyIdOrPlaceholder, predicate);
        }
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public ModifyThing setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thingId, thing, initialPolicy, policyIdOrPlaceholder, dittoHeaders);
    }

    @Override
    public boolean changesAuthorization() {
        return thing.getPolicyId().isPresent();
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, thing, initialPolicy, policyIdOrPlaceholder);
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
                Objects.equals(policyIdOrPlaceholder, that.policyIdOrPlaceholder) &&
                super.equals(obj);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof ModifyThing);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thingId=" + thingId + ", thing=" + thing +
                ", initialPolicy=" + initialPolicy + ", policyIdOrPlaceholder=" + policyIdOrPlaceholder + "]";
    }

}
