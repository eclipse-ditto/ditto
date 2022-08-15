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
import javax.annotation.concurrent.Immutable;

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
 * This command creates a new Thing. It contains the full {@link org.eclipse.ditto.things.model.Thing} including the
 * Thing ID which should be used for creation. If the Thing ID is already in the system, a response with a status code
 * {@code 409} (Conflict) will be generated.
 */
@Immutable
@JsonParsableCommand(typePrefix = ThingCommand.TYPE_PREFIX, name = CreateThing.NAME)
public final class CreateThing extends AbstractCommand<CreateThing> implements ThingModifyCommand<CreateThing> {

    /**
     * Name of the "Create Thing" command.
     */
    public static final String NAME = "createThing";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    public static final JsonFieldDefinition<JsonObject> JSON_THING =
            JsonFactory.newJsonObjectFieldDefinition("thing", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);

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
     * Json Field definition for the optional initial "inline" policy when creating a Thing.
     */
    public static final JsonFieldDefinition<JsonObject> JSON_INITIAL_POLICY =
            JsonFactory.newJsonObjectFieldDefinition("initialPolicy", FieldType.REGULAR, JsonSchemaVersion.V_2);

    /**
     * Json Field definition for the optional initial "inline" policy for usage in getEntity().
     */
    public static final JsonFieldDefinition<JsonObject> JSON_INLINE_POLICY =
            JsonFactory.newJsonObjectFieldDefinition("_policy", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final Thing thing;
    @Nullable private final JsonObject initialPolicy;

    @Nullable private final String policyIdOrPlaceholder;

    private CreateThing(final Thing thing, @Nullable final JsonObject initialPolicy, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.thing = thing;
        this.initialPolicy = initialPolicy;
        this.policyIdOrPlaceholder = null;

        final JsonObject thingJsonObject = thing.toJson(FieldType.notHidden()
                .or(jsonField -> Objects.equals(Thing.JsonFields.METADATA.getPointer(), jsonField.getKey().asPointer())));

        ThingCommandSizeValidator.getInstance().ensureValidSize(
                thingJsonObject::getUpperBoundForStringSize,
                () -> thingJsonObject.toString().length(),
                () -> dittoHeaders);
    }

    private CreateThing(final Thing thing, final String policyIdOrPlaceholder, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.thing = thing;
        this.initialPolicy = null;
        this.policyIdOrPlaceholder = policyIdOrPlaceholder;
        if (!Placeholders.containsAnyPlaceholder(policyIdOrPlaceholder)) {
            PolicyId.of(policyIdOrPlaceholder); //validates
        }

        final JsonObject thingJsonObject = thing.toJson(FieldType.notHidden()
                .or(jsonField -> Objects.equals(Thing.JsonFields.METADATA.getPointer(), jsonField.getKey().asPointer())));

        ThingCommandSizeValidator.getInstance().ensureValidSize(
                thingJsonObject::getUpperBoundForStringSize,
                () -> thingJsonObject.toString().length(),
                () -> dittoHeaders);
    }

    /**
     * Returns a Command for creating a new Thing which is passed as argument.
     *
     * @param newThing the new {@link org.eclipse.ditto.things.model.Thing} to create.
     * @param initialPolicy the initial {@code Policy} to set for the Thing - may be null.
     * @param dittoHeaders the headers of the command.
     * @return a Command for creating the provided new Thing.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.things.model.ThingIdInvalidException if the {@link org.eclipse.ditto.things.model.Thing}'s ID is not valid.
     */
    public static CreateThing of(final Thing newThing, @Nullable final JsonObject initialPolicy,
            final DittoHeaders dittoHeaders) {
        checkNotNull(newThing, "new Thing");
        return new CreateThing(newThing, initialPolicy, dittoHeaders);
    }

    /**
     * Returns a Command for creating a new Thing which is passed as argument. The created thing will have a policy
     * copied from a policy with athe given policyIdOrPlaceholder.
     *
     * @param newThing the new {@link org.eclipse.ditto.things.model.Thing} to create.
     * @param policyIdOrPlaceholder the policy id of the {@code Policy} to copy and set for the Thing when creating it.
     * If its a placeholder it will be resolved to a policy id.
     * Placeholder must be of the syntax: {{ ref:things/theThingId/policyId }}.
     * @param dittoHeaders the headers of the command.
     * @return a Command for creating the provided new Thing.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.things.model.ThingIdInvalidException if the {@link org.eclipse.ditto.things.model.Thing}'s ID is not valid.
     */
    public static CreateThing withCopiedPolicy(final Thing newThing, final String policyIdOrPlaceholder,
            final DittoHeaders dittoHeaders) {
        checkNotNull(newThing, "new Thing");
        checkNotNull(newThing, "policyIdOrPlaceholder");
        return new CreateThing(newThing, policyIdOrPlaceholder, dittoHeaders);
    }

    /**
     * Returns a Command for creating a new Thing which is passed as argument. The created thing will have a policy
     * copied from a policy with a given policyIdOrPlaceholder.
     *
     * @param newThing the new {@link org.eclipse.ditto.things.model.Thing} to create.
     * @param initialPolicy the initial {@code Policy} to set for the Thing - may be null.
     * @param policyIdOrPlaceholder the policy id of the {@code Policy} to copy and set for the Thing when creating it.
     * If its a placeholder it will be resolved to a policy id.
     * Placeholder must be of the syntax: {{ ref:things/theThingId/policyId }}.
     * @param dittoHeaders the headers of the command.
     * @return a Command for creating the provided new Thing.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.things.model.ThingIdInvalidException if the {@link org.eclipse.ditto.things.model.Thing}'s ID is not valid.
     */
    public static CreateThing of(final Thing newThing, @Nullable final JsonObject initialPolicy,
            @Nullable final String policyIdOrPlaceholder, final DittoHeaders dittoHeaders) {
        final ThingId thingId = newThing.getEntityId().orElse(null);

        if (policyIdOrPlaceholder != null && initialPolicy != null) {
            throw PoliciesConflictingException.newBuilder(thingId).dittoHeaders(dittoHeaders).build();
        }

        final CreateThing createThing;
        if (policyIdOrPlaceholder == null) {
            createThing = of(newThing, initialPolicy, dittoHeaders);
        } else {
            createThing = withCopiedPolicy(newThing, policyIdOrPlaceholder, dittoHeaders);
        }
        return createThing;
    }

    /**
     * Creates a new {@code CreateThing} from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static CreateThing fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code CreateThing} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static CreateThing fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<CreateThing>(TYPE, jsonObject).deserialize(() -> {
            final JsonObject thingJsonObject = jsonObject.getValueOrThrow(JSON_THING);
            final JsonObject initialPolicyObject = jsonObject.getValue(JSON_INITIAL_POLICY).orElse(null);
            final String policyIdOrPlaceholder = jsonObject.getValue(JSON_POLICY_ID_OR_PLACEHOLDER).orElse(null);
            final Thing thing = ThingsModelFactory.newThing(thingJsonObject);

            return of(thing, initialPolicyObject, policyIdOrPlaceholder, dittoHeaders);
        });
    }

    /**
     * Returns the {@code Thing} to create.
     *
     * @return the Thing to create.
     */
    public Thing getThing() {
        return thing;
    }

    /**
     * @return the initial {@code Policy} if there should be one applied when creating the Thing.
     */
    public Optional<JsonObject> getInitialPolicy() {
        return Optional.ofNullable(initialPolicy);
    }

    /**
     * @return the policyIdOrPlaceholder that should be used to copy an existing policy when creating the Thing.
     */
    public Optional<String> getPolicyIdOrPlaceholder() {return Optional.ofNullable(policyIdOrPlaceholder);}

    @Override
    public ThingId getEntityId() {
        return thing.getEntityId().orElseThrow(() -> new NullPointerException("Thing has no ID!"));
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
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
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
        return Category.CREATE;
    }

    @Override
    public CreateThing setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thing, initialPolicy, policyIdOrPlaceholder, dittoHeaders);
    }

    @Override
    public boolean changesAuthorization() {
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thing, initialPolicy, policyIdOrPlaceholder);
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
        final CreateThing that = (CreateThing) obj;
        return that.canEqual(this) && Objects.equals(thing, that.thing)
                && Objects.equals(initialPolicy, that.initialPolicy)
                && Objects.equals(policyIdOrPlaceholder, that.policyIdOrPlaceholder) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof CreateThing;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", thing=" + thing + ", initialPolicy=" +
                initialPolicy + ", policyIdOrPlaceholder=" + policyIdOrPlaceholder + "]";
    }

}
