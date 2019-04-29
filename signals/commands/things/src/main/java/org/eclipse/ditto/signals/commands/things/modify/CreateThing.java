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
import org.eclipse.ditto.model.base.common.Placeholders;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonParsableCommand;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingIdInvalidException;
import org.eclipse.ditto.model.things.ThingPolicyIdValidator;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;
import org.eclipse.ditto.signals.commands.things.ThingCommandSizeValidator;

/**
 * This command creates a new Thing. It contains the full {@link Thing} including the Thing ID which should be used for
 * creation. If the Thing ID is already in the system, a response with a status code {@code 409} (Conflict) will be
 * generated.
 */
@Immutable
@JsonParsableCommand(typePrefix = CreateThing.TYPE_PREFIX, name = CreateThing.NAME)
public final class CreateThing extends AbstractCommand<CreateThing> implements ThingModifyCommand<CreateThing> {

    /**
     * Name of the "Create Thing" command.
     */
    public static final String NAME = "createThing";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<JsonObject> JSON_THING =
            JsonFactory.newJsonObjectFieldDefinition("thing", FieldType.REGULAR, JsonSchemaVersion.V_1,
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

        ThingCommandSizeValidator.getInstance().ensureValidSize(() -> thing.toJsonString().length(), () ->
                dittoHeaders);
    }

    private CreateThing(final Thing thing, final String policyIdOrPlaceholder, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.thing = thing;
        this.initialPolicy = null;
        this.policyIdOrPlaceholder = policyIdOrPlaceholder;
        if (!Placeholders.containsAnyPlaceholder(policyIdOrPlaceholder)) {
            ThingPolicyIdValidator.getInstance().accept(policyIdOrPlaceholder, dittoHeaders);
        }

        ThingCommandSizeValidator.getInstance().ensureValidSize(() -> thing.toJsonString().length(), () ->
                dittoHeaders);
    }

    /**
     * Returns a Command for creating a new Thing which is passed as argument.
     *
     * @param newThing the new {@link Thing} to create.
     * @param initialPolicy the initial {@code Policy} to set for the Thing - may be null.
     * @param dittoHeaders the headers of the command.
     * @return a Command for creating the provided new Thing.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws ThingIdInvalidException if the {@link Thing}'s ID is not valid.
     * @throws org.eclipse.ditto.model.things.AclInvalidException if the Access Control List of {@code thing} does not
     * contain at least one authorization subject which has the permissions {@link org.eclipse.ditto.model.things.Permission#READ},
     * {@link org.eclipse.ditto.model.things.Permission#WRITE} and {@link org.eclipse.ditto.model.things.Permission#WRITE}.
     */
    public static CreateThing of(final Thing newThing, @Nullable final JsonObject initialPolicy,
            final DittoHeaders dittoHeaders) {
        checkNotNull(newThing, "new Thing");
        ensureThingIdPresence(newThing, dittoHeaders);
        return new CreateThing(newThing, initialPolicy, dittoHeaders);
    }

    /**
     * Returns a Command for creating a new Thing which is passed as argument. The created thing will have a policy
     * copied from a policy with athe given policyIdOrPlaceholder.
     *
     * @param newThing the new {@link Thing} to create.
     * @param policyIdOrPlaceholder the policy id of the {@code Policy} to copy and set for the Thing when creating it.
     * If its a placeholder it will be resolved to a policy id.
     * Placeholder must be of the syntax: {{ ref:things/theThingId/policyId }}.
     * @param dittoHeaders the headers of the command.
     * @return a Command for creating the provided new Thing.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws ThingIdInvalidException if the {@link Thing}'s ID is not valid.
     * @throws org.eclipse.ditto.model.things.AclInvalidException if the Access Control List of {@code thing} does not
     * contain at least one authorization subject which has the permissions {@link org.eclipse.ditto.model.things.Permission#READ},
     * {@link org.eclipse.ditto.model.things.Permission#WRITE} and {@link org.eclipse.ditto.model.things.Permission#WRITE}.
     */
    public static CreateThing withCopiedPolicy(final Thing newThing, final String policyIdOrPlaceholder,
            final DittoHeaders dittoHeaders) {
        checkNotNull(newThing, "new Thing");
        checkNotNull(newThing, "policyIdOrPlaceholder");
        ensureThingIdPresence(newThing, dittoHeaders);
        return new CreateThing(newThing, policyIdOrPlaceholder, dittoHeaders);
    }

    /**
     * Returns a Command for creating a new Thing which is passed as argument. The created thing will have a policy
     * copied from a policy with athe given policyIdOrPlaceholder.
     *
     * @param newThing the new {@link Thing} to create.
     * @param initialPolicy the initial {@code Policy} to set for the Thing - may be null.
     * @param policyIdOrPlaceholder the policy id of the {@code Policy} to copy and set for the Thing when creating it.
     * If its a placeholder it will be resolved to a policy id.
     * Placeholder must be of the syntax: {{ ref:things/theThingId/policyId }}.
     * @param dittoHeaders the headers of the command.
     * @return a Command for creating the provided new Thing.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws ThingIdInvalidException if the {@link Thing}'s ID is not valid.
     * @throws org.eclipse.ditto.model.things.AclInvalidException if the Access Control List of {@code thing} does not
     * contain at least one authorization subject which has the permissions {@link org.eclipse.ditto.model.things.Permission#READ},
     * {@link org.eclipse.ditto.model.things.Permission#WRITE} and {@link org.eclipse.ditto.model.things.Permission#WRITE}.
     */
    public static CreateThing of(final Thing newThing, @Nullable final JsonObject initialPolicy,
            @Nullable final String policyIdOrPlaceholder, final DittoHeaders dittoHeaders) {
        final String thingId = String.valueOf(newThing.getId().orElse(null));
        ThingModifyCommand.ensurePolicyCopyFromDoesNotConflictWithInlinePolicy(thingId, initialPolicy,
                policyIdOrPlaceholder, dittoHeaders);
        if (policyIdOrPlaceholder == null) {
            return of(newThing, initialPolicy, dittoHeaders);
        } else {
            return withCopiedPolicy(newThing, policyIdOrPlaceholder, dittoHeaders);
        }
    }

    private static void ensureThingIdPresence(final Thing newThing, final DittoHeaders dittoHeaders) {
        if (!newThing.getId().isPresent()) {
            throw ThingIdInvalidException.newBuilder("")
                    .message("Thing ID must be present in 'CreateThing' payload")
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
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
    public Optional<String> getPolicyIdOrPlaceholder() { return Optional.ofNullable(policyIdOrPlaceholder);}

    @Override
    public String getThingId() {
        return thing.getId().orElseThrow(() -> new NullPointerException("Thing has no ID!"));
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        final JsonObject thingJson = thing.toJson(schemaVersion, FieldType.regularOrSpecial());
        final JsonObject withInlinePolicyThingJson =
                getInitialPolicy().map(ip -> thingJson.set(JSON_INLINE_POLICY, ip)).orElse(thingJson);
        final JsonObject fullThingJson = getPolicyIdOrPlaceholder().map(
                containedPolicyIdOrPlaceholder -> withInlinePolicyThingJson.set(JSON_POLICY_ID_OR_PLACEHOLDER,
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
        return Category.MODIFY;
    }

    @Override
    public CreateThing setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(thing, initialPolicy, policyIdOrPlaceholder, dittoHeaders);
    }

    @Override
    public boolean changesAuthorization() {
        return true;
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
