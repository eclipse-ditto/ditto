/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
import org.eclipse.ditto.base.model.signals.FeatureToggle;
import org.eclipse.ditto.base.model.signals.UnsupportedSchemaVersionException;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.AttributesModelFactory;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandSizeValidator;
import org.eclipse.ditto.things.model.signals.commands.exceptions.AttributePointerInvalidException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.PoliciesConflictingException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingIdNotExplicitlySettableException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingMergeInvalidException;

/**
 * /**
 * This command merges an existing Thing with the supplied modification. The command contains a {@code path} and a
 * {@code value} describing the change that should be applied. The {@code value} at the given {@code path} is merged
 * with the existing thing according to <a href="https://tools.ietf.org/html/rfc7396">RFC7396 - JSON Merge Patch</a>.
 *
 * @since 2.0.0
 */
@Immutable
@JsonParsableCommand(typePrefix = ThingCommand.TYPE_PREFIX, name = MergeThing.NAME)
public final class MergeThing extends AbstractCommand<MergeThing> implements ThingModifyCommand<MergeThing> {

    /**
     * Name of the "Merge Thing" command.
     */
    public static final String NAME = "mergeThing";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    /**
     * Json Field definition for the optional initial "inline" policy for usage in getEntity().
     */
    public static final JsonFieldDefinition<JsonObject> JSON_INLINE_POLICY =
            JsonFactory.newJsonObjectFieldDefinition(Policy.INLINED_FIELD_NAME, FieldType.REGULAR, JsonSchemaVersion.V_2);

    /**
     * Json Field definition for the optional feature to copy an existing policy.
     */
    public static final JsonFieldDefinition<String> JSON_COPY_POLICY_FROM =
            JsonFactory.newStringFieldDefinition("_copyPolicyFrom", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final JsonFieldDefinition<JsonObject> JSON_INITIAL_POLICY =
            JsonFactory.newJsonObjectFieldDefinition("initialPolicy", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final JsonFieldDefinition<String> JSON_POLICY_ID_OR_PLACEHOLDER =
            JsonFactory.newStringFieldDefinition("policyIdOrPlaceholder", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final ThingId thingId;
    private final JsonPointer path;
    private final JsonValue value;

    @Nullable private final JsonObject initialPolicy;
    @Nullable private final String policyIdOrPlaceholder;

    private MergeThing(final ThingId thingId, final JsonPointer path, final JsonValue value,
            @Nullable final JsonObject initialPolicy,
            @Nullable final String policyIdOrPlaceholder,
            final DittoHeaders dittoHeaders) {
        super(TYPE, FeatureToggle.checkMergeFeatureEnabled(TYPE, dittoHeaders));
        this.thingId = checkNotNull(thingId, "thingId");
        this.path = checkNotNull(path, "path");
        this.value = checkJsonSize(checkNotNull(value, "value"), dittoHeaders);

        if (policyIdOrPlaceholder != null && initialPolicy != null) {
            throw PoliciesConflictingException.newBuilder(thingId).dittoHeaders(dittoHeaders).build();
        }

        if (policyIdOrPlaceholder != null && !Placeholders.containsAnyPlaceholder(policyIdOrPlaceholder)) {
            PolicyId.of(policyIdOrPlaceholder); //validates
        }

        this.initialPolicy = initialPolicy;
        this.policyIdOrPlaceholder = policyIdOrPlaceholder;

        checkSchemaVersion();
    }

    /**
     * Creates a command for merging the thing identified by {@code thingId} with the given
     * {@code path} and {@code value}.
     *
     * @param thingId the thing id.
     * @param path the path that is merged with the existing thing.
     * @param value the value describing the changes that are merged with the existing thing.
     * @param dittoHeaders the ditto headers.
     * @return the created {@link MergeThing} command.
     */
    public static MergeThing of(final ThingId thingId, final JsonPointer path, final JsonValue value,
            final DittoHeaders dittoHeaders) {
        if (path.isEmpty() && value.isObject()) {
            final JsonObject object = value.asObject();
            final Optional<JsonObject> initialPolicy = initialPolicyForMergeThingFrom(object);
            final Optional<String> policyIdOrPlaceholder = policyIdOrPlaceholderForMergeThingFrom(object);
            return new MergeThing(thingId, path, value, initialPolicy.orElse(null),
                    policyIdOrPlaceholder.orElse(null), dittoHeaders);
        } else {
            return new MergeThing(thingId, path, value, null, null, dittoHeaders);
        }
    }

    /**
     * Creates a command for merging the thing identified by {@code thingId} with the given {@code thing}.
     *
     * @param thingId the thing id.
     * @param thing the thing that is merged with the existing thing.
     * @param dittoHeaders the ditto headers.
     * @return the created {@link MergeThing} command.
     */
    public static MergeThing withThing(final ThingId thingId, final Thing thing, final DittoHeaders dittoHeaders) {
        ensureThingIdMatches(thingId, thing);
        ensureThingIsNotNullOrEmpty(thing, dittoHeaders);
        final JsonObject mergePatch = thing.toJson();
        return new MergeThing(thingId, JsonPointer.empty(), mergePatch, null, null, dittoHeaders);
    }

    /**
     * Creates a command for merging the thing identified by {@code thingId} with the given {@code thing}.
     * Only one of the arguments initialPolicy and policyIdOrPlaceholder must not be null. They are both allowed to be
     * null, but not both to not be null at the same time.
     *
     * @param thingId the thing id.
     * @param thing the thing that is merged with the existing thing.
     * @param policyIdOrPlaceholder the policy id of the {@code Policy} to copy and set for the Thing when creating it.
     * If it's a placeholder it will be resolved to a policy id.
     * Placeholder must be of the syntax: {@code {{ ref:things/<theThingId>/policyId }} }.
     * @param initialPolicy the initial {@code Policy} to set for the Thing when creating it - may be null.
     * @param dittoHeaders the ditto headers.
     * @return the created {@link MergeThing} command.
     * @since 3.3.0
     */
    public static MergeThing withThing(final ThingId thingId, final Thing thing,
            @Nullable final JsonObject initialPolicy, @Nullable final String policyIdOrPlaceholder,
            final DittoHeaders dittoHeaders) {
        ensureThingIdMatches(thingId, thing);
        ensureThingIsNotNullOrEmpty(thing, dittoHeaders);
        final JsonObject mergePatch = thing.toJson();
        return new MergeThing(thingId, JsonPointer.empty(), mergePatch, initialPolicy, policyIdOrPlaceholder,
                dittoHeaders);
    }

    /**
     * Creates a command for merging the thing identified by {@code thingId} with the given {@code policyId}.
     *
     * @param thingId the thing id.
     * @param policyId the policyId that is merged with the existing thing.
     * @param dittoHeaders the ditto headers.
     * @return the created {@link MergeThing} command.
     */
    public static MergeThing withPolicyId(final ThingId thingId, final PolicyId policyId,
            final DittoHeaders dittoHeaders) {
        checkNotNull(policyId, "policyId");
        return new MergeThing(thingId, Thing.JsonFields.POLICY_ID.getPointer(), JsonValue.of(policyId),
                null, null, dittoHeaders);
    }

    /**
     * Creates a command for merging the thing identified by {@code thingId} with the given {@code thingDefinition}.
     *
     * @param thingId the thing id.
     * @param thingDefinition the thing definition that is merged with the existing thing.
     * @param dittoHeaders the ditto headers.
     * @return the created {@link MergeThing} command.
     */
    public static MergeThing withThingDefinition(final ThingId thingId, final ThingDefinition thingDefinition,
            final DittoHeaders dittoHeaders) {
        return new MergeThing(thingId, Thing.JsonFields.DEFINITION.getPointer(), thingDefinition.toJson(),
                null, null, dittoHeaders);
    }

    /**
     * Creates a command for merging the thing identified by {@code thingId} with the given {@code attributes}.
     *
     * @param thingId the thing id.
     * @param attributes the attributes that are merged with the existing thing.
     * @param dittoHeaders the ditto headers.
     * @return the created {@link MergeThing} command.
     */
    public static MergeThing withAttributes(final ThingId thingId, final Attributes attributes,
            final DittoHeaders dittoHeaders) {
        return new MergeThing(thingId, Thing.JsonFields.ATTRIBUTES.getPointer(), attributes.toJson(),
                null, null, dittoHeaders);
    }

    /**
     * Creates a command for merging the thing identified by {@code thingId} with the given
     * {@code attributePath} and {@code attributeValue}.
     *
     * @param thingId the thing id.
     * @param attributePath the path where the attribute value is merged with the existing thing.
     * @param attributeValue the attribute value that is merged with the existing thing.
     * @param dittoHeaders the ditto headers.
     * @return the created {@link MergeThing} command.
     */
    public static MergeThing withAttribute(final ThingId thingId, final JsonPointer attributePath,
            final JsonValue attributeValue, final DittoHeaders dittoHeaders) {
        final JsonPointer absolutePath =
                Thing.JsonFields.ATTRIBUTES.getPointer().append(checkAttributePointer(attributePath, dittoHeaders));
        return new MergeThing(thingId, absolutePath, checkAttributeValue(attributeValue), null, null, dittoHeaders);
    }

    /**
     * Creates a command for merging the thing identified by {@code thingId} with the given {@code features}.
     *
     * @param thingId the thing id.
     * @param features the features that are merged with the existing thing.
     * @param dittoHeaders the ditto headers.
     * @return the created {@link MergeThing} command.
     */
    public static MergeThing withFeatures(final ThingId thingId, final Features features,
            final DittoHeaders dittoHeaders) {
        final JsonPointer absolutePath = Thing.JsonFields.FEATURES.getPointer();
        return new MergeThing(thingId, absolutePath, features.toJson(), null, null, dittoHeaders);
    }

    /**
     * Creates a command for merging the thing identified by {@code thingId} with the given {@code feature}.
     *
     * @param thingId the thing id.
     * @param feature the feature that is merged with the existing thing.
     * @param dittoHeaders the ditto headers.
     * @return the created {@link MergeThing} command.
     */
    public static MergeThing withFeature(final ThingId thingId, final Feature feature,
            final DittoHeaders dittoHeaders) {
        final JsonPointer absolutePath = Thing.JsonFields.FEATURES.getPointer().append(JsonPointer.of(feature.getId()));
        return new MergeThing(thingId, absolutePath, feature.toJson(), null, null, dittoHeaders);
    }

    /**
     * Creates a command for merging the feature identified by {@code thingId} and {@code featureId} with the given
     * {@code featureDefinition}.
     *
     * @param thingId the thing id.
     * @param featureId the feature id identifying the feature.
     * @param featureDefinition the feature definition that is merged with the existing feature.
     * @param dittoHeaders the ditto headers.
     * @return the created {@link MergeThing} command.
     */
    public static MergeThing withFeatureDefinition(final ThingId thingId,
            final String featureId, final FeatureDefinition featureDefinition,
            final DittoHeaders dittoHeaders) {
        final JsonPointer absolutePath = Thing.JsonFields.FEATURES.getPointer()
                .append(JsonPointer.of(featureId))
                .append(Feature.JsonFields.DEFINITION.getPointer());
        return new MergeThing(thingId, absolutePath, featureDefinition.toJson(), null, null, dittoHeaders);
    }

    /**
     * Creates a command for merging the feature identified by {@code thingId} and {@code featureId} with
     * the given {@code featureProperties}.
     *
     * @param thingId the thing id.
     * @param featureId the feature id identifying the feature.
     * @param featureProperties the featureProperties that are merged with the existing feature.
     * @param dittoHeaders the ditto headers.
     * @return the created {@link MergeThing} command.
     */
    public static MergeThing withFeatureProperties(final ThingId thingId,
            final String featureId, final FeatureProperties featureProperties,
            final DittoHeaders dittoHeaders) {
        final JsonPointer absolutePath = Thing.JsonFields.FEATURES.getPointer()
                .append(JsonPointer.of(featureId))
                .append(Feature.JsonFields.PROPERTIES.getPointer());
        return new MergeThing(thingId, absolutePath, featureProperties.toJson(), null, null, dittoHeaders);
    }

    /**
     * Creates a command for merging the feature identified by {@code thingId} and {@code featureId} with
     * the given {@code propertyPath} and {@code propertyValue}.
     *
     * @param thingId the thing id.
     * @param featureId the feature id identifying the feature.
     * @param propertyPath the path where the property value is merged with the existing feature properties.
     * @param propertyValue the property value that is merged with the existing feature properties.
     * @param dittoHeaders the ditto headers.
     * @return the created {@link MergeThing} command.
     */
    public static MergeThing withFeatureProperty(final ThingId thingId,
            final String featureId, final JsonPointer propertyPath, final JsonValue propertyValue,
            final DittoHeaders dittoHeaders) {
        final JsonPointer absolutePath = Thing.JsonFields.FEATURES.getPointer()
                .append(JsonPointer.of(featureId))
                .append(Feature.JsonFields.PROPERTIES.getPointer())
                .append(checkPropertyPointer(propertyPath));
        return new MergeThing(thingId, absolutePath, checkPropertyValue(propertyValue), null, null, dittoHeaders);
    }

    /**
     * Creates a command for merging the feature identified by {@code thingId} and {@code featureId} with
     * the given {@code desiredFeatureProperties}.
     *
     * @param thingId the thing id.
     * @param featureId the feature id identifying the feature.
     * @param desiredFeatureProperties the desired feature properties that are merged with the existing feature.
     * @param dittoHeaders the ditto headers.
     * @return the created {@link MergeThing} command.
     */
    public static MergeThing withFeatureDesiredProperties(final ThingId thingId,
            final String featureId, final FeatureProperties desiredFeatureProperties,
            final DittoHeaders dittoHeaders) {
        final JsonPointer absolutePath = Thing.JsonFields.FEATURES.getPointer()
                .append(JsonPointer.of(featureId))
                .append(Feature.JsonFields.DESIRED_PROPERTIES.getPointer());
        return new MergeThing(thingId, absolutePath, desiredFeatureProperties.toJson(), null, null, dittoHeaders);
    }

    /**
     * Creates a command for merging the feature identified by {@code thingId} and {@code featureId} with
     * the given {@code propertyPath} and {@code propertyValue}.
     *
     * @param thingId the thing id.
     * @param featureId the feature id identifying the feature.
     * @param propertyPath the path where the property value is merged with the existing desired feature properties.
     * @param propertyValue the property value that is merged with the existing desired feature properties.
     * @param dittoHeaders the ditto headers.
     * @return the created {@link MergeThing} command.
     */
    public static MergeThing withFeatureDesiredProperty(final ThingId thingId,
            final String featureId, final JsonPointer propertyPath, final JsonValue propertyValue,
            final DittoHeaders dittoHeaders) {
        final JsonPointer absolutePath = Thing.JsonFields.FEATURES.getPointer()
                .append(JsonPointer.of(featureId))
                .append(Feature.JsonFields.DESIRED_PROPERTIES.getPointer())
                .append(checkPropertyPointer(propertyPath));
        return new MergeThing(thingId, absolutePath, checkPropertyValue(propertyValue), null, null, dittoHeaders);
    }

    /**
     * Retrieves a potentially included "inline policy" from the {@link #JSON_INLINE_POLICY _policy} field of the passed
     * {@code jsonObject}.
     *
     * @param jsonObject the JSON object to look for an inline policy in.
     * @return the potentially contained inline policy as JSON object.
     */
    public static Optional<JsonObject> initialPolicyForMergeThingFrom(final JsonObject jsonObject) {
        return jsonObject.getValue(JSON_INLINE_POLICY)
                .map(JsonValue::asObject);
    }

    /**
     * Retrieves a potentially included "policy id or placeholder" to copy a policy from the
     * {@link #JSON_COPY_POLICY_FROM _copyPolicyFrom} field of the passed {@code jsonObject}.
     *
     * @param jsonObject the JSON object to look for the policy id or placeholder in.
     * @return the potentially contained policy id or placeholder.
     */
    public static Optional<String> policyIdOrPlaceholderForMergeThingFrom(final JsonObject jsonObject) {
        return jsonObject.getValue(JSON_COPY_POLICY_FROM);
    }

    private static JsonPointer checkPropertyPointer(final JsonPointer propertyPointer) {
        return ThingsModelFactory.validateFeaturePropertyPointer(propertyPointer);
    }

    private static JsonValue checkPropertyValue(final JsonValue value) {
        if (value.isObject()) {
            ThingsModelFactory.validateJsonKeys(value.asObject());
        }
        return value;
    }

    private static JsonPointer checkAttributePointer(final JsonPointer pointer, final DittoHeaders dittoHeaders) {
        if (pointer.isEmpty()) {
            throw AttributePointerInvalidException.newBuilder(pointer)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
        return AttributesModelFactory.validateAttributePointer(pointer);
    }

    private static JsonValue checkAttributeValue(final JsonValue value) {
        if (value.isObject()) {
            AttributesModelFactory.validateAttributeKeys(value.asObject());
        }
        return value;
    }

    /**
     * Ensures that the thingId is consistent with the id of the thing.
     *
     * @throws org.eclipse.ditto.things.model.signals.commands.exceptions.ThingIdNotExplicitlySettableException if ids do not match.
     */
    private static void ensureThingIdMatches(final ThingId thingId, final Thing thing) {
        if (!thing.getEntityId().map(id -> id.equals(thingId)).orElse(true)) {
            throw ThingIdNotExplicitlySettableException.forDittoProtocol().build();
        }
    }

    /**
     * Ensures that the thing is not null or empty.
     *
     * @throws org.eclipse.ditto.things.model.signals.commands.exceptions.ThingMergeInvalidException if the thing is null or empty.
     */
    private static void ensureThingIsNotNullOrEmpty(final Thing thing, final DittoHeaders dittoHeaders) {
        if (thing.toJson().isEmpty() &&
                dittoHeaders.getSchemaVersion().filter(JsonSchemaVersion.V_2::equals).isPresent()) {
            throw ThingMergeInvalidException.fromMessage(
                    "The provided json value can not be applied at this resource", dittoHeaders);
        }
    }

    private JsonValue checkJsonSize(final JsonValue value, final DittoHeaders dittoHeaders) {
        ThingCommandSizeValidator.getInstance().ensureValidSize(
                value::getUpperBoundForStringSize,
                () -> value.toString().length(),
                () -> dittoHeaders);
        return value;
    }

    /**
     * Creates a new {@code MergeThing} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the {@code MergeThing} command created from JSON.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain a field for
     * {@link org.eclipse.ditto.things.model.signals.commands.ThingCommand.JsonFields#JSON_THING_ID}, {@link MergeThing.JsonFields#JSON_PATH} or {@link MergeThing.JsonFields#JSON_VALUE}.
     */
    public static MergeThing fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<MergeThing>(TYPE, jsonObject).deserialize(() -> {
            final String thingId = jsonObject.getValueOrThrow(ThingCommand.JsonFields.JSON_THING_ID);
            final String path = jsonObject.getValueOrThrow(JsonFields.JSON_PATH);
            final JsonValue jsonValue = jsonObject.getValueOrThrow(JsonFields.JSON_VALUE);
            final JsonObject initialPolicyObject = jsonObject.getValue(JSON_INITIAL_POLICY).orElse(null);
            final String policyIdOrPlaceholder = jsonObject.getValue(JSON_POLICY_ID_OR_PLACEHOLDER).orElse(null);

            return new MergeThing(ThingId.of(thingId), JsonPointer.of(path), jsonValue, initialPolicyObject,
                    policyIdOrPlaceholder, dittoHeaders);
        });
    }

    /**
     * @return the path where the changes are applied.
     */
    public JsonPointer getPath() {
        return path;
    }

    @Override
    public ThingId getEntityId() {
        return thingId;
    }

    @Override
    public Optional<JsonValue> getEntity() {
        if (path.isEmpty()) {
            final JsonObject thingJson = value.asObject();
            final JsonObject withInlinePolicyThingJson =
                    getInitialPolicy().map(ip -> thingJson.set(JSON_INLINE_POLICY, ip)).orElse(thingJson);
            final JsonObject fullThingJson = getPolicyIdOrPlaceholder().map(
                    containedPolicyIdOrPlaceholder -> withInlinePolicyThingJson.set(JSON_COPY_POLICY_FROM,
                            containedPolicyIdOrPlaceholder)).orElse(withInlinePolicyThingJson);
            return Optional.of(fullThingJson);
        } else {
            return Optional.of(value);
        }
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        return getEntity();
    }

    @Override
    public MergeThing setEntity(final JsonValue entity) {
        if (path.isEmpty()) {
            final JsonObject jsonObject = entity.asObject();
            final JsonObject initialPolicyObject = jsonObject.getValue(JSON_INLINE_POLICY).orElse(null);
            final String localPolicyIdOrPlaceholder = jsonObject.getValue(JSON_COPY_POLICY_FROM).orElse(null);
            return new MergeThing(thingId, path, entity, initialPolicyObject, localPolicyIdOrPlaceholder, getDittoHeaders());
        } else {
            return new MergeThing(thingId, path, entity, initialPolicy, policyIdOrPlaceholder, getDittoHeaders());
        }
    }

    @Override
    public JsonPointer getResourcePath() {
        return path;
    }

    /**
     * @return the initial {@code Policy} if there should be one applied when creating the Thing.
     * @since 3.3.0
     */
    public Optional<JsonObject> getInitialPolicy() {
        return Optional.ofNullable(initialPolicy);
    }

    /**
     * @return The policy id of the {@code Policy} to copy and set for the Thing when creating it.
     * Could also be a placeholder like: {{ ref:things/theThingId/policyId }}.
     * @since 3.3.0
     */
    public Optional<String> getPolicyIdOrPlaceholder() {
        return Optional.ofNullable(policyIdOrPlaceholder);
    }

    @Override
    public boolean changesAuthorization() {
        return Thing.JsonFields.POLICY_ID.getPointer().equals(path) || path.isEmpty() && value.isObject() &&
                value.asObject().contains(Thing.JsonFields.POLICY_ID.getPointer());
    }

    @Override
    public Category getCategory() {
        return Category.MERGE;
    }

    @Override
    public MergeThing setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new MergeThing(thingId, path, value, initialPolicy, policyIdOrPlaceholder, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicateParameter) {
        final Predicate<JsonField> predicate = schemaVersion.and(predicateParameter);
        jsonObjectBuilder.set(ThingCommand.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.JSON_PATH, path.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.JSON_VALUE, value, predicate);
        if (initialPolicy != null) {
            jsonObjectBuilder.set(JSON_INITIAL_POLICY, initialPolicy, predicate);
        }
        if (policyIdOrPlaceholder != null) {
            jsonObjectBuilder.set(JSON_POLICY_ID_OR_PLACEHOLDER, policyIdOrPlaceholder, predicate);
        }
    }

    @Override
    public JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    private void checkSchemaVersion() {
        final JsonSchemaVersion implementedSchemaVersion = getImplementedSchemaVersion();
        if (!implementsSchemaVersion(implementedSchemaVersion)) {
            throw UnsupportedSchemaVersionException.newBuilder(implementedSchemaVersion).build();
        }
    }

    /**
     * @return the value describing the changes that are applied to the existing thing.
     */
    public JsonValue getValue() {
        return value;
    }

    /**
     * An enumeration of the JSON fields of a {@code MergeThing} command.
     */
    @Immutable
    static final class JsonFields {

        static final JsonFieldDefinition<String> JSON_PATH =
                JsonFactory.newStringFieldDefinition("path", FieldType.REGULAR, JsonSchemaVersion.V_2);

        static final JsonFieldDefinition<JsonValue> JSON_VALUE =
                JsonFactory.newJsonValueFieldDefinition("value", FieldType.REGULAR, JsonSchemaVersion.V_2);

        JsonFields() {
            throw new AssertionError();
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final MergeThing that = (MergeThing) o;
        return that.canEqual(this) &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(path, that.path) &&
                Objects.equals(value, that.value) &&
                Objects.equals(initialPolicy, that.initialPolicy) &&
                Objects.equals(policyIdOrPlaceholder, that.policyIdOrPlaceholder);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof MergeThing;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, path, value, initialPolicy, policyIdOrPlaceholder);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", thingId=" + thingId +
                ", path=" + path +
                ", value=" + value +
                ", initialPolicy=" + initialPolicy +
                ", policyIdOrPlaceholder=" + policyIdOrPlaceholder +
                "]";
    }
}
