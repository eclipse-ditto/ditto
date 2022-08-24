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
package org.eclipse.ditto.things.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.entity.metadata.MetadataBuilder;
import org.eclipse.ditto.base.model.entity.metadata.MetadataModelFactory;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonKeyInvalidException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;

/**
 * Factory that creates new {@code things} objects.
 */
@Immutable
public final class ThingsModelFactory {

    public static final JsonKey FEATURE_ID_WILDCARD = JsonKey.of("*");

    /*
     * Inhibit instantiation of this utility class.
     */
    private ThingsModelFactory() {
        throw new AssertionError();
    }

    /**
     * Returns a new immutable empty {@link Attributes}.
     *
     * @return the new immutable empty {@code Attributes}.
     */
    public static Attributes emptyAttributes() {
        return AttributesModelFactory.emptyAttributes();
    }

    /**
     * Returns a new immutable {@link Attributes} which represents {@code null}.
     *
     * @return the new {@code null}-like {@code Attributes}.
     */
    public static Attributes nullAttributes() {
        return AttributesModelFactory.nullAttributes();
    }

    /**
     * Returns a new immutable {@link Attributes} which is initialised with the values of the given JSON object.
     *
     * @param jsonObject provides the initial values of the result.
     * @return the new immutable initialised {@code Attributes}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    public static Attributes newAttributes(final JsonObject jsonObject) {
        return AttributesModelFactory.newAttributes(jsonObject);
    }

    /**
     * Returns a new immutable {@link Attributes} which is initialised with the values of the given JSON string. This
     * string is required to be a valid {@link JsonObject}.
     *
     * @param jsonString provides the initial values of the result;
     * @return the new immutable initialised {@code Attributes}.
     * @throws DittoJsonException if {@code jsonString} cannot be parsed to {@code Attributes}.
     */
    public static Attributes newAttributes(final String jsonString) {
        return AttributesModelFactory.newAttributes(jsonString);
    }

    /**
     * Returns a new empty builder for a {@link Attributes}.
     *
     * @return the builder.
     */
    public static AttributesBuilder newAttributesBuilder() {
        return AttributesModelFactory.newAttributesBuilder();
    }

    /**
     * Returns a new builder for a {@link Attributes} which is initialised with the values of the given JSON object.
     *
     * @param jsonObject provides the initial values of the result.
     * @return the builder.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    public static AttributesBuilder newAttributesBuilder(final JsonObject jsonObject) {
        return AttributesModelFactory.newAttributesBuilder(jsonObject);
    }

    /**
     * Returns a new immutable {@link ThingDefinition} which is initialised with the parsed {@code thingDefinition}.
     *
     * @param thingDefinition Definition identifier which should be parsed as ThingDefinition.
     * @return new ThingDefinition with the parsed definition identifier.
     */
    public static ThingDefinition newDefinition(@Nullable final CharSequence thingDefinition) {
        if (null != thingDefinition) {
            return ImmutableThingDefinition.ofParsed(thingDefinition);
        }
        return nullDefinition();
    }

    /**
     * Returns a new immutable {@link ThingDefinition} which represents {@code null}.
     *
     * @return the new {@code null}-like {@code ThingDefinition}.
     */
    public static ThingDefinition nullDefinition() {
        return NullThingDefinition.getInstance();
    }

    /**
     * Returns an immutable instance of {@link DefinitionIdentifier}.
     *
     * @param namespace the namespace of the returned Identifier.
     * @param name the name of the returned Identifier.
     * @param version the version of the returned Identifier.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if any argument is empty.
     */
    public static DefinitionIdentifier newFeatureDefinitionIdentifier(final CharSequence namespace,
            final CharSequence name, final CharSequence version) {

        return ImmutableFeatureDefinitionIdentifier.getInstance(namespace, name, version);
    }

    /**
     * Parses the specified CharSequence and returns an immutable instance of {@link DefinitionIdentifier}.
     *
     * @param featureIdentifierAsCharSequence CharSequence-representation of a FeatureDefinition Identifier.
     * @return the instance.
     * @throws NullPointerException if {@code featureIdentifierAsCharSequence} is {@code null}.
     * @throws DefinitionIdentifierInvalidException if {@code featureIdentifierAsCharSequence} is invalid.
     */
    public static DefinitionIdentifier newFeatureDefinitionIdentifier(
            final CharSequence featureIdentifierAsCharSequence) {

        if (featureIdentifierAsCharSequence instanceof DefinitionIdentifier) {
            return (DefinitionIdentifier) featureIdentifierAsCharSequence;
        }
        return ImmutableFeatureDefinitionIdentifier.ofParsed(featureIdentifierAsCharSequence);
    }

    /**
     * Creates a new instance of {@code ImmutableFeatureDefinition} based on the passed {@code definitionIdentifiers}.
     *
     * @param definitionIdentifiers the Identifiers of the FeatureDefinition to be returned.
     * @return the instance.
     * @throws NullPointerException if {@code definitionIdentifiers} is {@code null}.
     * @since 3.0.0
     */
    public static FeatureDefinition newFeatureDefinition(final Collection<DefinitionIdentifier> definitionIdentifiers) {
        return ImmutableFeatureDefinition.of(definitionIdentifiers);
    }

    /**
     * Parses the specified JsonArray and returns an immutable instance of {@code FeatureDefinition} which is
     * initialised with the values of the given JSON array.
     *
     * @param jsonArray JSON array containing the identifiers of the FeatureDefinition to be returned. Non-string values
     * are ignored.
     * @return the instance.
     * @throws NullPointerException if {@code jsonArray} is {@code null}.
     * @throws FeatureDefinitionEmptyException if {@code jsonArray} is empty.
     * @throws DefinitionIdentifierInvalidException if any Identifier string of the array is invalid.
     */
    public static FeatureDefinition newFeatureDefinition(final JsonArray jsonArray) {
        checkNotNull(jsonArray, "JSON array");
        if (!jsonArray.isNull()) {
            return ImmutableFeatureDefinition.fromJson(jsonArray);
        }
        return nullFeatureDefinition();
    }

    /**
     * Returns a new immutable {@link FeatureDefinition} which is initialised with the values of the given JSON string.
     * This string is required to be a valid {@link JsonArray}.
     *
     * @param jsonString provides the initial values of the result;
     * @return the new immutable initialised {@code FeatureDefinition}.
     * @throws DittoJsonException if {@code jsonString} cannot be parsed to {@code FeatureDefinition}.
     * @throws FeatureDefinitionEmptyException if the JSON array is empty.
     * @throws DefinitionIdentifierInvalidException if any Identifier of the JSON array is invalid.
     */
    public static FeatureDefinition newFeatureDefinition(final String jsonString) {
        final JsonArray jsonArray =
                DittoJsonException.wrapJsonRuntimeException(() -> JsonFactory.newArray(jsonString));

        return newFeatureDefinition(jsonArray);
    }

    /**
     * Returns a new immutable {@link FeatureDefinition} which represents {@code null}.
     *
     * @return the new {@code null}-like {@code FeatureDefinition}.
     */
    public static FeatureDefinition nullFeatureDefinition() {
        return NullFeatureDefinition.getInstance();
    }

    /**
     * Parses the specified CharSequence and returns a mutable builder with a fluent API for an immutable {@code
     * FeatureDefinition}. The returned builder is initialised with the parsed Identifier as its first one.
     *
     * @param firstIdentifier CharSequence-representation of the first FeatureDefinition Identifier.
     * @return the instance.
     * @throws NullPointerException if {@code firstIdentifier} is {@code null}.
     * @throws DefinitionIdentifierInvalidException if {@code firstIdentifier} is invalid.
     */
    public static FeatureDefinitionBuilder newFeatureDefinitionBuilder(final CharSequence firstIdentifier) {
        return ImmutableFeatureDefinition.getBuilder(newFeatureDefinitionIdentifier(firstIdentifier));
    }

    /**
     * Returns a new builder for an immutable {@link FeatureDefinition} which is initialised with the values of the
     * given JSON array.
     *
     * @param jsonArray provides the initial values of the result.
     * @return the builder.
     * @throws NullPointerException if {@code jsonArray} is {@code null}.
     * @throws DefinitionIdentifierInvalidException if any Identifier of the array is invalid.
     */
    public static FeatureDefinitionBuilder newFeatureDefinitionBuilder(final JsonArray jsonArray) {
        return ImmutableFeatureDefinition.Builder.getInstance().addAll(newFeatureDefinition(jsonArray));
    }

    /**
     * Returns a new immutable empty {@link FeatureProperties}.
     *
     * @return the new immutable empty {@code FeatureProperties}.
     */
    public static FeatureProperties emptyFeatureProperties() {
        return ImmutableFeatureProperties.empty();
    }

    /**
     * Returns a new immutable {@link FeatureProperties} which represents {@code null}.
     *
     * @return the new {@code null}-like {@code FeatureProperties}.
     */
    public static FeatureProperties nullFeatureProperties() {
        return NullFeatureProperties.newInstance();
    }

    /**
     * Returns a new immutable {@link FeatureProperties} which is initialised with the values of the given JSON object.
     *
     * @param jsonObject provides the initial values of the result.
     * @return the new immutable initialised {@code FeatureProperties}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if a property name in the passed {@code jsonObject}
     * was not valid according to pattern
     * {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static FeatureProperties newFeatureProperties(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "JSON object for initialization");

        if (!jsonObject.isNull()) {
            return ImmutableFeatureProperties.of(jsonObject);
        } else {
            return nullFeatureProperties();
        }
    }

    /**
     * Returns a new immutable {@link FeatureProperties} which is initialised with the values of the given JSON string.
     * This string is required to be a valid {@link JsonObject}.
     *
     * @param jsonString provides the initial values of the result;
     * @return the new immutable initialised {@code FeatureProperties}.
     * @throws DittoJsonException if {@code jsonString} cannot be parsed to {@code FeatureProperties}.
     */
    public static FeatureProperties newFeatureProperties(final String jsonString) {
        final JsonObject jsonObject =
                DittoJsonException.wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return newFeatureProperties(jsonObject);
    }

    /**
     * Returns a new empty builder for an immutable {@link FeatureProperties}.
     *
     * @return the builder.
     */
    public static FeaturePropertiesBuilder newFeaturePropertiesBuilder() {
        return ImmutableFeaturePropertiesBuilder.empty();
    }

    /**
     * Returns a new builder for an immutable {@link FeatureProperties} which is initialised with the values of the
     * given JSON object.
     *
     * @param jsonObject provides the initial values of the result.
     * @return the builder.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    public static FeaturePropertiesBuilder newFeaturePropertiesBuilder(final JsonObject jsonObject) {
        return ImmutableFeaturePropertiesBuilder.of(jsonObject);
    }

    /**
     * Returns a new immutable {@link Feature} which represents {@code null}.
     *
     * @param featureId the ID of the new Feature.
     * @return the new {@code null}-like {@code Feature}.
     * @throws NullPointerException if {@code featureId} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if {@code featureId} was not valid according to pattern
     * {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static Feature nullFeature(final String featureId) {
        return NullFeature.of(featureId);
    }

    /**
     * Returns a new immutable {@link Feature} with the given ID.
     *
     * @param featureId the ID of the new Feature.
     * @return the new immutable {@code Feature}.
     * @throws NullPointerException if {@code featureId} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if {@code featureId} was not valid according to pattern
     * {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static Feature newFeature(final String featureId) {
        return ImmutableFeature.of(featureId);
    }

    /**
     * Returns a new immutable {@link Feature} with the given ID and properties.
     *
     * @param featureId the ID of the new feature.
     * @param featureProperties the properties of the new Feature or {@code null}.
     * @return the new immutable {@code Feature}.
     * @throws NullPointerException if {@code featureId} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if {@code featureId} was not valid according to pattern
     * {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static Feature newFeature(final String featureId, @Nullable final FeatureProperties featureProperties) {
        return ImmutableFeature.of(featureId, featureProperties);
    }

    /**
     * Returns a new immutable {@link Feature} with the given ID, properties and Definition.
     *
     * @param featureId the ID of the new feature.
     * @param featureDefinition the Definition of the new Feature or {@code null}.
     * @return the new immutable {@code Feature}.
     * @throws NullPointerException if {@code featureId} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if {@code featureId} was not valid according to pattern
     * {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static Feature newFeature(final String featureId, @Nullable final FeatureDefinition featureDefinition) {
        return ImmutableFeature.of(featureId, featureDefinition, null);
    }

    /**
     * Returns a new immutable {@link Feature} with the given ID, properties and Definition.
     *
     * @param featureId the ID of the new feature.
     * @param featureDefinition the Definition of the new Feature or {@code null}.
     * @param featureProperties the properties of the new Feature or {@code null}.
     * @return the new immutable {@code Feature}.
     * @throws NullPointerException if {@code featureId} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if {@code featureId} was not valid according to pattern
     * {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static Feature newFeature(final String featureId, @Nullable final FeatureDefinition featureDefinition,
            @Nullable final FeatureProperties featureProperties) {

        return ImmutableFeature.of(featureId, featureDefinition, featureProperties);
    }
    /**
     * Returns a new immutable {@link Feature} with the given ID, properties, desired Properties and Definition.
     *
     * @param featureId the ID of the new feature.
     * @param featureDefinition the Definition of the new Feature or {@code null}.
     * @param featureProperties the properties of the new Feature or {@code null}.
     * @param desiredFeatureProperties the desired properties of the new Feature or {@code null}.
     * @return the new immutable {@code Feature}.
     * @throws NullPointerException if {@code featureId} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if {@code featureId} was not valid according to pattern
     * {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     * @since 1.5.0
     */
    public static Feature newFeature(final CharSequence featureId,
            @Nullable final FeatureDefinition featureDefinition,
            @Nullable final FeatureProperties featureProperties,
            @Nullable FeatureProperties desiredFeatureProperties) {

        return ImmutableFeature.of(featureId, featureDefinition, featureProperties, desiredFeatureProperties);
    }


    /**
     * Returns a new builder for an immutable {@link Feature} from scratch with a fluent API.
     *
     * @return the builder.
     */
    public static FeatureBuilder.FromScratchBuildable newFeatureBuilder() {
        return ImmutableFeatureFromScratchBuilder.newFeatureFromScratch();
    }

    /**
     * Returns a new builder for an immutable {@link Feature} which is initialised with the values of the given Feature.
     *
     * @param feature provides the initial values for the result.
     * @return the builder.
     * @throws NullPointerException if {@code feature} is {@code null}.
     */
    public static FeatureBuilder.FromCopyBuildable newFeatureBuilder(final Feature feature) {
        return ImmutableFeatureFromCopyBuilder.of(feature);
    }

    /**
     * Returns a new builder for an immutable {@link Feature} which is initialised with the values of the given JSON
     * object.
     *
     * @param jsonObject provides the initial values for the result.
     * @return the builder.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    public static FeatureBuilder.FromJsonBuildable newFeatureBuilder(final JsonObject jsonObject) {
        return ImmutableFeatureFromScratchBuilder.newFeatureFromJson(jsonObject);
    }

    /**
     * Returns a new builder for an immutable {@link Feature} which is initialised with the values of the given JSON
     * string. The JSON string is parsed in a fault tolerant way. I. e. all properties which cannot be deserialized are
     * supposed to not exist.
     *
     * @param jsonString string the JSON string representation of a Feature.
     * @return the builder.
     * @throws DittoJsonException if {@code jsonString} cannot be parsed to {@code Feature}.
     * @see #newFeatureBuilder(JsonObject)
     */
    public static FeatureBuilder.FromJsonBuildable newFeatureBuilder(final String jsonString) {
        final JsonObject jsonObject =
                DittoJsonException.wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return newFeatureBuilder(jsonObject);
    }

    /**
     * Returns a new immutable empty {@link Features}.
     *
     * @return the new immutable empty {@code Features}.
     */
    public static Features emptyFeatures() {
        return ImmutableFeatures.empty();
    }

    /**
     * Returns a new immutable {@link Features} which represents {@code null}.
     *
     * @return the new {@code null}-like {@code Features}.
     */
    public static Features nullFeatures() {
        return NullFeatures.newInstance();
    }

    /**
     * Returns a new immutable {@link Features} which is initialised with the features of the given Iterable.
     *
     * @param features the features to initialise the result with.
     * @return the new immutable {@code Features} which is initialised with {@code features}.
     * @throws NullPointerException if {@code features} is {@code null}.
     */
    public static Features newFeatures(final Iterable<Feature> features) {
        return ImmutableFeatures.of(features);
    }

    /**
     * Returns a new immutable {@link Features} based on the given JSON object.
     *
     * @param jsonObject provides the initial values for the result.
     * @return the new immutable {@code Features} which is initialised by the data of {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws DittoJsonException if any JSON field which is supposed to represent a Feature is not a JSON object.
     */
    public static Features newFeatures(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "Features JSON object");

        final Features result;

        if (!jsonObject.isNull()) {
            final Set<Feature> features = jsonObject.stream()
                    .filter(field -> !Objects.equals(field.getKey(), JsonSchemaVersion.getJsonKey()))
                    .peek(field -> {
                        if (!(field.getValue().isObject())) {
                            final String errorMsgTemplate =
                                    "The Feature value is not an object for Feature with ID ''{0}'': {1}";
                            final String errorMsg =
                                    MessageFormat.format(errorMsgTemplate, field.getKey(), field.getValue());
                            throw new DittoJsonException(new JsonParseException(errorMsg));
                        }
                    })
                    .map(field -> ImmutableFeatureFromScratchBuilder.newFeatureFromJson(field.getValue().asObject())
                            .useId(field.getKeyName())
                            .build())
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            result = ImmutableFeatures.of(features);
        } else {
            result = nullFeatures();
        }

        return result;
    }

    /**
     * Returns a new immutable {@link Features} based on the given JSON string.
     *
     * @param jsonString provides the initial values of the result.
     * @return the new immutable initialised {@code Features}.
     * @throws DittoJsonException if {@code jsonString} cannot be parsed to {@code Features}.
     */
    public static Features newFeatures(final String jsonString) {
        final JsonObject featuresJsonObject =
                DittoJsonException.wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return newFeatures(featuresJsonObject);
    }

    /**
     * Returns a new immutable {@link Features} which is initialised with the given features.
     *
     * @param feature the initial Feature of the result.
     * @param additionalFeatures additional features of the result.
     * @return the new immutable {@code Features} which is initialised with {@code feature} and potentially with {@code
     * additionalFeatures}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static Features newFeatures(final Feature feature, final Feature... additionalFeatures) {
        return ImmutableFeatures.of(feature, additionalFeatures);
    }

    /**
     * Returns a new mutable builder with a fluent API for an immutable {@link Features}.
     *
     * @return the builder.
     */
    public static FeaturesBuilder newFeaturesBuilder() {
        return ImmutableFeaturesBuilder.newInstance();
    }

    /**
     * Returns a new mutable builder with a fluent API for an immutable {@link Features}. The builder is initialised
     * with the given features.
     *
     * @param features the initial features of the new builder.
     * @return the builder.
     * @throws NullPointerException if {@code features} is {@code null}.
     */
    public static FeaturesBuilder newFeaturesBuilder(final Iterable<Feature> features) {
        final FeaturesBuilder result = ImmutableFeaturesBuilder.newInstance();
        result.setAll(features);
        return result;
    }

    /**
     * Validates the given {@link JsonPointer} to a feature property.
     *
     * @param jsonPointer {@code jsonPointer} that is validated
     * @return the same {@code jsonPointer} if validation was successful
     * @throws JsonKeyInvalidException if {@code jsonPointer} was not valid according to
     * pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     * @since 1.2.0
     */
    public static JsonPointer validateFeaturePropertyPointer(final JsonPointer jsonPointer) {
        return JsonKeyValidator.validate(jsonPointer);
    }

    /**
     * Validates the given {@link JsonObject} containing only valid keys.
     *
     * @param jsonObject {@code jsonObject} that is validated
     * @throws org.eclipse.ditto.json.JsonKeyInvalidException if {@code jsonObject} was not valid according to
     * pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     * @since 1.3.0
     */
    public static void validateJsonKeys(final JsonObject jsonObject) {
        JsonKeyValidator.validateJsonKeys(jsonObject);
    }

    /**
     * Returns a new immutable empty {@link Metadata}.
     *
     * @return the new immutable empty {@code Metadata}.
     * @since 1.2.0
     */
    public static Metadata emptyMetadata() {
        return MetadataModelFactory.emptyMetadata();
    }

    /**
     * Returns a new immutable {@link Metadata} which represents {@code null}.
     *
     * @return the new {@code null}-like {@code Metadata}.
     * @since 1.2.0
     */
    public static Metadata nullMetadata() {
        return MetadataModelFactory.nullMetadata();
    }

    /**
     * Returns a new immutable {@link Metadata} which is initialised with the values of the given JSON object.
     *
     * @param jsonObject provides the initial values of the result.
     * @return the new immutable initialised {@code Metadata}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @since 1.2.0
     */
    public static Metadata newMetadata(final JsonObject jsonObject) {
        return MetadataModelFactory.newMetadata(jsonObject);
    }

    /**
     * Returns a new immutable {@link Metadata} which is initialised with the values of the given JSON string. This
     * string is required to be a valid {@link JsonObject}.
     *
     * @param jsonString provides the initial values of the result;
     * @return the new immutable initialised {@code Metadata}.
     * @throws DittoJsonException if {@code jsonString} cannot be parsed to {@code Metadata}.
     * @since 1.2.0
     */
    public static Metadata newMetadata(final String jsonString) {
        return MetadataModelFactory.newMetadata(jsonString);
    }

    /**
     * Returns a new empty builder for a {@link Metadata}.
     *
     * @return the builder.
     * @since 1.2.0
     */
    public static MetadataBuilder newMetadataBuilder() {
        return MetadataModelFactory.newMetadataBuilder();
    }

    /**
     * Returns a new builder for a {@link Metadata} which is initialised with the values of the given Metadata.
     *
     * @param metadata provides the initial values of the result.
     * @return the builder.
     * @throws NullPointerException if {@code metadata} is {@code null}.
     * @since 1.2.0
     */
    public static MetadataBuilder newMetadataBuilder(final Metadata metadata) {
        return MetadataModelFactory.newMetadataBuilder(metadata);
    }

    /**
     * Returns a new immutable {@link ThingRevision} which is initialised with the given revision number.
     *
     * @param revisionNumber the {@code long} value of the revision.
     * @return the new immutable {@code ThingRevision}.
     */
    public static ThingRevision newThingRevision(final long revisionNumber) {
        return ImmutableThingRevision.of(revisionNumber);
    }

    /**
     * Returns a new immutable {@link Thing} based on the given JSON object.
     *
     * @param jsonObject the JSON object representation of a Thing.
     * @return the new Thing.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws DittoJsonException if {@code jsonObject} cannot be parsed to {@code Thing}.
     */
    public static Thing newThing(final JsonObject jsonObject) {
        return newThingBuilder(jsonObject).build();
    }

    /**
     * Returns a new immutable {@link Thing} based on the given JSON string.
     *
     * @param jsonString the JSON string representation of a Thing.
     * @return the new Thing.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws DittoJsonException if {@code jsonString} cannot be parsed to {@code Thing}.
     */
    public static Thing newThing(final String jsonString) {
        final JsonObject jsonObject =
                DittoJsonException.wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return newThingBuilder(jsonObject).build();
    }

    /**
     * Returns a mutable builder with a fluent API for an immutable {@link Thing} from scratch.
     *
     * @return the new builder.
     */
    public static ThingBuilder.FromScratch newThingBuilder() {
        return ImmutableThingFromScratchBuilder.newInstance();
    }

    /**
     * Returns a mutable builder with a fluent API for an immutable {@link Thing} based on the given JSON object. The
     * JSON object is parsed in a fault-tolerant way. I.e. all properties which cannot be deserialized are supposed to
     * not exist.
     *
     * @param jsonObject the JSON object representation of a Thing.
     * @return the new builder.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws DittoJsonException if {@code jsonObject} cannot be parsed to {@code Thing}.
     */
    public static ThingBuilder.FromCopy newThingBuilder(final JsonObject jsonObject) {
        return DittoJsonException.wrapJsonRuntimeException(() -> ImmutableThingFromCopyBuilder.of(jsonObject));
    }

    /**
     * Returns a mutable builder with a fluent API for an immutable {@link Thing} based on the given JSON string. The
     * JSON string is parsed in a fault-tolerant way. I.e. all properties which cannot be deserialized are supposed to
     * not exist.
     *
     * @param jsonString string the JSON string representation of a Thing.
     * @return the new builder.
     * @throws DittoJsonException if {@code jsonString} cannot be parsed to {@code Thing}.
     * @see #newThingBuilder(JsonObject)
     */
    public static ThingBuilder.FromCopy newThingBuilder(final String jsonString) {
        final JsonObject jsonObject =
                DittoJsonException.wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return newThingBuilder(jsonObject);
    }

    /**
     * Returns a mutable builder with a fluent API for an immutable {@link Thing}. The builder is initialised with the
     * properties of the given Thing.
     *
     * @param thing the Thing which provides the initial properties of the builder.
     * @return the new builder.
     * @throws NullPointerException if {@code thing} is {@code null}.
     */
    public static ThingBuilder.FromCopy newThingBuilder(final Thing thing) {
        return ImmutableThingFromCopyBuilder.of(thing);
    }

    /**
     * Returns a new instance of {@link JsonFieldSelector} with expanded feature id wildcard.
     *
     * @return the new instance.
     * @since 2.3.0
     */
    public static JsonFieldSelector expandFeatureIdWildcards(final Features features,
            final JsonFieldSelector jsonFieldSelector) {
        final Collection<JsonKey> featureIds =
                features.stream().map(Feature::getId).map(JsonKey::of).collect(Collectors.toList());
        return expandFeatureIdWildcards(featureIds, jsonFieldSelector);
    }

    /**
     * Returns a new instance of {@link JsonFieldSelector} with expanded feature id wildcard.
     *
     * @return the new instance.
     * @since 2.3.0
     */
    public static JsonFieldSelector expandFeatureIdWildcards(final Collection<JsonKey> featureIds,
            final JsonFieldSelector jsonFieldSelector) {
        final List<JsonPointer> jsonPointerList = jsonFieldSelector.getPointers().stream()
                .flatMap(jsonPointer -> expandFeatureIdWildcard(featureIds, jsonPointer)).collect(Collectors.toList());
        return JsonFactory.newFieldSelector(jsonPointerList);
    }

    /**
     * Returns a stream of {@link JsonPointer} with expanded feature id wildcard.
     *
     * @return a stream of {@link JsonPointer}.
     * @since 2.3.0
     */
    public static Stream<JsonPointer> expandFeatureIdWildcard(final Collection<JsonKey> featureIds,
            final JsonPointer jsonPointer) {
        if (hasFeatureIdWildcard(jsonPointer)) {
            return featureIds.stream().map(fid -> Thing.JsonFields.FEATURES.getPointer()
                    .append(JsonPointer.of(fid))
                    .append(jsonPointer.getSubPointer(2).orElse(JsonPointer.empty())));
        } else {
            return Stream.of(jsonPointer);
        }
    }

    private static boolean hasFeatureIdWildcard(final JsonPointer pointer) {
        return pointer.getLevelCount() > 1
                && pointer.getRoot()
                .filter(root -> Thing.JsonFields.FEATURES.getPointer().equals(JsonPointer.of(root)))
                .isPresent()
                && pointer.get(1).filter(FEATURE_ID_WILDCARD::equals).isPresent();
    }

}
