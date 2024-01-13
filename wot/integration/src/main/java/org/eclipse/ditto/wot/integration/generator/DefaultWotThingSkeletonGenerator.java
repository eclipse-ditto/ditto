/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.integration.generator;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.japi.Pair;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.pekko.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.AttributesBuilder;
import org.eclipse.ditto.things.model.DefinitionIdentifier;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureBuilder;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.FeaturePropertiesBuilder;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.FeaturesBuilder;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingBuilder;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.wot.integration.provider.WotThingModelFetcher;
import org.eclipse.ditto.wot.model.ArraySchema;
import org.eclipse.ditto.wot.model.BaseLink;
import org.eclipse.ditto.wot.model.DataSchemaType;
import org.eclipse.ditto.wot.model.IRI;
import org.eclipse.ditto.wot.model.IntegerSchema;
import org.eclipse.ditto.wot.model.NumberSchema;
import org.eclipse.ditto.wot.model.ObjectSchema;
import org.eclipse.ditto.wot.model.Properties;
import org.eclipse.ditto.wot.model.Property;
import org.eclipse.ditto.wot.model.SingleDataSchema;
import org.eclipse.ditto.wot.model.StringSchema;
import org.eclipse.ditto.wot.model.ThingDefinitionInvalidException;
import org.eclipse.ditto.wot.model.ThingModel;
import org.eclipse.ditto.wot.model.TmOptional;
import org.eclipse.ditto.wot.model.WotThingModelInvalidException;

/**
 * Default Ditto specific implementation of {@link WotThingSkeletonGenerator}.
 */
@Immutable
final class DefaultWotThingSkeletonGenerator implements WotThingSkeletonGenerator {

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(DefaultWotThingSkeletonGenerator.class);

    private static final String TM_EXTENDS = "tm:extends";

    private static final String TM_SUBMODEL = "tm:submodel";
    private static final String TM_SUBMODEL_INSTANCE_NAME = "instanceName";

    private final WotThingModelFetcher thingModelFetcher;
    private final Executor executor;
    private final WotThingModelExtensionResolver thingModelExtensionResolver;

    DefaultWotThingSkeletonGenerator(final ActorSystem actorSystem, final WotThingModelFetcher thingModelFetcher) {
        this.thingModelFetcher = checkNotNull(thingModelFetcher, "thingModelFetcher");
        executor = actorSystem.dispatchers().lookup("wot-dispatcher");
        thingModelExtensionResolver = new DefaultWotThingModelExtensionResolver(thingModelFetcher, executor);
    }

    @Override
    public CompletionStage<Optional<Thing>> generateThingSkeleton(final ThingId thingId,
            final ThingModel thingModel,
            final URL thingModelUrl,
            final boolean generateDefaultsForOptionalProperties,
            final DittoHeaders dittoHeaders) {

        return thingModelExtensionResolver
                .resolveThingModelExtensions(thingModel, dittoHeaders)
                .thenCompose(thingModelWithExtensions ->
                        thingModelExtensionResolver.resolveThingModelRefs(thingModelWithExtensions, dittoHeaders)
                )
                .thenApply(thingModelWithExtensionsAndImports -> {
                    final Optional<String> dittoExtensionPrefix = thingModelWithExtensionsAndImports.getAtContext()
                            .determinePrefixFor(DittoWotExtension.DITTO_WOT_EXTENSION);

                    LOGGER.withCorrelationId(dittoHeaders)
                            .debug("ThingModel for generating Thing skeleton after resolving extensions + refs: <{}>",
                                    thingModelWithExtensionsAndImports);

                    final ThingBuilder.FromScratch builder = Thing.newBuilder();
                    thingModelWithExtensionsAndImports.getProperties()
                            .map(properties -> {
                                final JsonObjectBuilder jsonObjectBuilder = JsonObject.newBuilder();
                                final Map<String, JsonObjectBuilder> attributesCategories = new LinkedHashMap<>();

                                fillPropertiesInOptionalCategories(
                                        properties,
                                        generateDefaultsForOptionalProperties,
                                        thingModelWithExtensionsAndImports.getTmOptional().orElse(null),
                                        jsonObjectBuilder,
                                        attributesCategories,
                                        property -> dittoExtensionPrefix.flatMap(prefix ->
                                                        property.getValue(prefix + ":" +
                                                                DittoWotExtension.DITTO_WOT_EXTENSION_CATEGORY
                                                        )
                                                )
                                                .filter(JsonValue::isString)
                                                .map(JsonValue::asString)
                                );

                                final AttributesBuilder attributesBuilder = Attributes.newBuilder();
                                if (!attributesCategories.isEmpty()) {
                                    attributesCategories.forEach((attributeCategory, categoryObjBuilder) ->
                                            attributesBuilder.set(attributeCategory, categoryObjBuilder.build())
                                    );
                                }
                                attributesBuilder.setAll(jsonObjectBuilder.build());
                                return attributesBuilder.build();
                            }).ifPresent(builder::setAttributes);

                    return Pair.apply(thingModelWithExtensionsAndImports, builder);
                })
                .thenCompose(pair ->
                        createFeaturesFromSubmodels(pair.first(), generateDefaultsForOptionalProperties, dittoHeaders)
                                .thenApply(features ->
                                        features.map(f -> pair.second().setFeatures(f)).orElse(pair.second())
                                )
                )
                .thenApply(builder -> Optional.of(builder.build()));
    }

    private static void fillPropertiesInOptionalCategories(final Properties properties,
            final boolean generateDefaultsForOptionalProperties,
            @Nullable final TmOptional tmOptionalElements,
            final JsonObjectBuilder jsonObjectBuilder,
            final Map<String, JsonObjectBuilder> propertiesCategories,
            final Function<Property, Optional<String>> propertyCategoryExtractor) {

        properties.values().stream()
                .filter(property -> generateDefaultsForOptionalProperties || Optional.ofNullable(tmOptionalElements)
                        .stream()
                        .noneMatch(optionals -> optionals.stream()
                                .anyMatch(optionalEl ->
                                        // filter out optional elements - don't create skeleton values for those:
                                        optionalEl.toString().equals("/properties/" + property.getPropertyName())
                                )
                        )
                )
                .forEach(property -> determineInitialPropertyValue(property).ifPresent(val ->
                                propertyCategoryExtractor.apply(property)
                                        .ifPresentOrElse(attributeCategory -> {
                                                    if (!propertiesCategories.containsKey(attributeCategory)) {
                                                        propertiesCategories.put(attributeCategory,
                                                                JsonObject.newBuilder());
                                                    }
                                                    propertiesCategories.get(attributeCategory)
                                                            .set(property.getPropertyName(), val);
                                                }, () ->
                                                        jsonObjectBuilder.set(property.getPropertyName(), val)
                                        )
                        )
                );
    }

    private CompletionStage<Optional<Features>> createFeaturesFromSubmodels(final ThingModel thingModel,
            final boolean generateDefaultsForOptionalProperties, final DittoHeaders dittoHeaders) {

        final FeaturesBuilder featuresBuilder = Features.newBuilder();
        final List<CompletableFuture<Optional<Feature>>> futureList = thingModel.getLinks()
                .map(links -> links.stream()
                        .filter(baseLink -> baseLink.getRel().filter(TM_SUBMODEL::equals).isPresent())
                        .map(baseLink -> {
                                    final String instanceName = baseLink.getValue(TM_SUBMODEL_INSTANCE_NAME)
                                            .filter(JsonValue::isString)
                                            .map(JsonValue::asString)
                                            .orElseThrow(() -> WotThingModelInvalidException
                                                    .newBuilder("The required 'instanceName' field of the " +
                                                            "'tm:submodel' link was not provided."
                                                    ).dittoHeaders(dittoHeaders)
                                                    .build()
                                            );
                                    LOGGER.withCorrelationId(dittoHeaders)
                                            .debug("Resolved TM submodel with instanceName <{}> and href <{}>",
                                                    instanceName, baseLink.getHref());
                                    return new Submodel(instanceName, baseLink.getHref());
                                }
                        )
                )
                .orElseGet(Stream::empty)
                .map(submodel -> thingModelFetcher.fetchThingModel(submodel.href, dittoHeaders)
                        .thenComposeAsync(subThingModel ->
                                generateFeatureSkeleton(submodel.instanceName,
                                        subThingModel,
                                        submodel.href,
                                        generateDefaultsForOptionalProperties,
                                        dittoHeaders
                                ), executor)
                        .toCompletableFuture()
                )
                .toList();

        return CompletableFuture.allOf(futureList.toArray(CompletableFuture<?>[]::new))
                .thenApplyAsync(v -> {
                            if (futureList.isEmpty()) {
                                return Optional.empty();
                            } else {
                                featuresBuilder.setAll(futureList.stream()
                                        .map(CompletableFuture::join)
                                        .filter(Optional::isPresent)
                                        .map(Optional::get)
                                        .toList());
                                return Optional.of(featuresBuilder.build());
                            }
                        },
                        executor
                );
    }

    private CompletionStage<Optional<Feature>> generateFeatureSkeleton(final String featureId,
            final ThingModel thingModel,
            final IRI thingModelIri,
            final boolean generateDefaultsForOptionalProperties,
            final DittoHeaders dittoHeaders) {
        try {
            return generateFeatureSkeleton(featureId, thingModel, new URL(thingModelIri.toString()),
                    generateDefaultsForOptionalProperties, dittoHeaders);
        } catch (final MalformedURLException e) {
            throw ThingDefinitionInvalidException.newBuilder(thingModelIri)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    @Override
    public CompletionStage<Optional<Feature>> generateFeatureSkeleton(final String featureId,
            final ThingModel thingModel,
            final URL thingModelUrl,
            final boolean generateDefaultsForOptionalProperties,
            final DittoHeaders dittoHeaders) {

        return thingModelExtensionResolver
                .resolveThingModelExtensions(thingModel, dittoHeaders)
                .thenCompose(thingModelWithExtensions -> thingModelExtensionResolver
                        .resolveThingModelRefs(thingModelWithExtensions, dittoHeaders))
                .thenCombine(resolveFeatureDefinition(thingModel, thingModelUrl, dittoHeaders),
                        (thingModelWithExtensionsAndImports, featureDefinition) -> {
                            final Optional<String> dittoExtensionPrefix =
                                    thingModelWithExtensionsAndImports.getAtContext()
                                            .determinePrefixFor(DittoWotExtension.DITTO_WOT_EXTENSION);

                            LOGGER.withCorrelationId(dittoHeaders)
                                    .debug("ThingModel for generating Feature skeleton after resolving extensions + refs: <{}>",
                                            thingModelWithExtensionsAndImports);

                            final FeatureBuilder.FromScratchBuildable builder = Feature.newBuilder();
                            thingModelWithExtensionsAndImports.getProperties()
                                    .map(properties -> {
                                        final JsonObjectBuilder jsonObjectBuilder = JsonObject.newBuilder();
                                        final Map<String, JsonObjectBuilder> propertiesCategories =
                                                new LinkedHashMap<>();

                                        fillPropertiesInOptionalCategories(
                                                properties,
                                                generateDefaultsForOptionalProperties,
                                                thingModelWithExtensionsAndImports.getTmOptional().orElse(null),
                                                jsonObjectBuilder,
                                                propertiesCategories,
                                                property -> dittoExtensionPrefix.flatMap(prefix ->
                                                                property.getValue(
                                                                        prefix + ":" +
                                                                                DittoWotExtension.DITTO_WOT_EXTENSION_CATEGORY)
                                                        )
                                                        .filter(JsonValue::isString)
                                                        .map(JsonValue::asString)
                                        );

                                        final FeaturePropertiesBuilder propertiesBuilder =
                                                FeatureProperties.newBuilder();
                                        if (!propertiesCategories.isEmpty()) {
                                            propertiesCategories.forEach((propertyCategory, categoryObjBuilder) ->
                                                    propertiesBuilder.set(propertyCategory, categoryObjBuilder.build())
                                            );
                                        }
                                        propertiesBuilder.setAll(jsonObjectBuilder.build());
                                        return propertiesBuilder.build();
                                    }).ifPresent(builder::properties);

                            builder.definition(featureDefinition);

                            return Optional.of(builder.withId(featureId).build());
                        });
    }

    private static Optional<JsonValue> determineInitialPropertyValue(final SingleDataSchema dataSchema) {

        return dataSchema.getConst()
                .or(dataSchema::getDefault)
                .or(() -> {
                    final SingleDataSchema actualSchema;
                    if (dataSchema instanceof Property property) {
                        actualSchema = resolveActualPropertySchema(property);
                    } else {
                        actualSchema = dataSchema;
                    }

                    if (actualSchema instanceof ObjectSchema objectSchema) {
                        final List<String> required = objectSchema.getRequired();
                        if (!required.isEmpty()) {
                            final JsonObjectBuilder objectBuilder = JsonObject.newBuilder();
                            objectSchema.getProperties().entrySet().stream()
                                    .filter(e -> required.contains(e.getKey()))
                                    .forEach(entry -> objectBuilder.set(entry.getKey(),
                                            determineInitialPropertyValue(entry.getValue())
                                                    .orElse(JsonValue.nullLiteral())));
                            return Optional.of(objectBuilder.build());
                        }
                        return Optional.of(JsonObject.empty());
                    } else if (actualSchema instanceof ArraySchema arraySchema) {
                        final JsonArrayBuilder arrayBuilder = JsonArray.newBuilder();
                        arraySchema.getItems()
                                .ifPresent(itemsSchema -> {
                                    if (itemsSchema instanceof SingleDataSchema singleDataSchema) {
                                        final int neutralElementCount = arraySchema.getMinItems().orElse(1);
                                        provideNeutralElementForDataSchema(singleDataSchema)
                                                .ifPresent(ne -> IntStream.range(0, neutralElementCount)
                                                        .forEach(i -> arrayBuilder.add(ne))
                                                );
                                    }
                                });
                        return Optional.of(arrayBuilder.build());
                    } else {
                        return provideNeutralElementForDataSchema(actualSchema);
                    }
                });
    }

    private static SingleDataSchema resolveActualPropertySchema(final Property property) {
        final SingleDataSchema actualSchema;
        if (property.isBooleanSchema()) {
            actualSchema = property.asBooleanSchema();
        } else if (property.isIntegerSchema()) {
            actualSchema = property.asIntegerSchema();
        } else if (property.isNumberSchema()) {
            actualSchema = property.asNumberSchema();
        } else if (property.isStringSchema()) {
            actualSchema = property.asStringSchema();
        } else if (property.isObjectSchema()) {
            actualSchema = property.asObjectSchema();
        } else if (property.isArraySchema()) {
            actualSchema = property.asArraySchema();
        } else if (property.isNullSchema()) {
            actualSchema = property.asNullSchema();
        } else {
            actualSchema = property;
        }
        return actualSchema;
    }

    private static Optional<JsonValue> provideNeutralElementForDataSchema(final SingleDataSchema dataSchema) {
        final DataSchemaType type = dataSchema.getType().orElse(null);
        if (null == type) {
            return Optional.empty();
        } else {
            switch (type) {
                case BOOLEAN:
                    return Optional.of(JsonValue.of(false));
                case INTEGER:
                    final IntegerSchema integerSchema = (IntegerSchema) dataSchema;
                    final int neutralInt = provideNeutralIntElement(integerSchema.getMinimum().orElse(null),
                            integerSchema.getExclusiveMinimum().orElse(null),
                            integerSchema.getMaximum().orElse(null),
                            integerSchema.getExclusiveMaximum().orElse(null));
                    return Optional.of(JsonValue.of(neutralInt));
                case NUMBER:
                    final NumberSchema numberSchema = ((NumberSchema) dataSchema);
                    final double neutralDouble = provideNeutralDoubleElement(numberSchema.getMinimum().orElse(null),
                            numberSchema.getExclusiveMinimum().orElse(null),
                            numberSchema.getMaximum().orElse(null),
                            numberSchema.getExclusiveMaximum().orElse(null));
                    return Optional.of(JsonValue.of(neutralDouble));
                case STRING:
                    final StringSchema stringSchema = (StringSchema) dataSchema;
                    final String neutralString = provideNeutralStringElement(stringSchema.getMinLength().orElse(null));
                    return Optional.of(JsonValue.of(neutralString));
                case OBJECT:
                    return Optional.of(JsonObject.empty());
                case ARRAY:
                    return Optional.of(JsonArray.empty());
                case NULL:
                    return Optional.of(JsonValue.nullLiteral());
                default:
                    return Optional.empty();
            }
        }
    }

    private static int provideNeutralIntElement(@Nullable final Integer minimum,
            @Nullable final Integer exclusiveMinimum,
            @Nullable final Integer maximum,
            @Nullable final Integer exclusiveMaximum) {

        int result = 0;
        if (null != minimum && minimum > result) {
            result = minimum;
        }
        if (null != exclusiveMinimum && exclusiveMinimum >= result) {
            result = exclusiveMinimum + 1;
        }
        if (null != maximum && maximum < result) {
            result = maximum;
        }
        if (null != exclusiveMaximum && exclusiveMaximum <= result) {
            result = exclusiveMaximum - 1;
        }
        return result;
    }

    private static double provideNeutralDoubleElement(@Nullable final Double minimum,
            @Nullable final Double exclusiveMinimum,
            @Nullable final Double maximum,
            @Nullable final Double exclusiveMaximum) {

        double result = 0.0;
        if (null != minimum && minimum > result) {
            result = minimum;
        }
        if (null != exclusiveMinimum && exclusiveMinimum >= result) {
            result = exclusiveMinimum + 1;
        }
        if (null != maximum && maximum < result) {
            result = maximum;
        }
        if (null != exclusiveMaximum && exclusiveMaximum <= result) {
            result = exclusiveMaximum - 1;
        }
        return result;
    }

    private static String provideNeutralStringElement(@Nullable final Integer minLength) {
        if (null != minLength && minLength > 0) {
            return "_".repeat(minLength);
        }
        return "";
    }

    private CompletionStage<FeatureDefinition> resolveFeatureDefinition(final ThingModel thingModel,
            final URL thingModelUrl,
            final DittoHeaders dittoHeaders) {
        return determineFurtherFeatureDefinitionIdentifiers(thingModel, dittoHeaders)
                .thenApply(definitionIdentifiers -> FeatureDefinition.fromIdentifier(
                        thingModelUrl.toString(),
                        definitionIdentifiers.toArray(DefinitionIdentifier[]::new)
                ));
    }

    private CompletionStage<List<DefinitionIdentifier>> determineFurtherFeatureDefinitionIdentifiers(
            final ThingModel thingModel,
            final DittoHeaders dittoHeaders) {
        return thingModel.getLinks().map(links -> {
            final Optional<BaseLink<?>> extendsLink = links.stream()
                    .filter(baseLink -> baseLink.getRel().filter(TM_EXTENDS::equals).isPresent())
                    .findFirst();

            if (extendsLink.isPresent()) {
                final BaseLink<?> link = extendsLink.get();
                return thingModelFetcher.fetchThingModel(link.getHref(), dittoHeaders)
                        .thenComposeAsync(subThingModel ->
                                determineFurtherFeatureDefinitionIdentifiers( // recurse!
                                        subThingModel,
                                        dittoHeaders
                                ), executor
                        )
                        .thenApply(recursedSubmodels -> {
                            final List<DefinitionIdentifier> combinedIdentifiers = new ArrayList<>();
                            combinedIdentifiers.add(ThingsModelFactory.newFeatureDefinitionIdentifier(link.getHref()));
                            combinedIdentifiers.addAll(recursedSubmodels);
                            return combinedIdentifiers;
                        });
            } else {
                return CompletableFuture.completedFuture(Collections.<DefinitionIdentifier>emptyList());
            }
        }).orElseGet(() -> CompletableFuture.completedFuture(Collections.emptyList()));
    }

    private static class Submodel {

        private final String instanceName;
        private final IRI href;

        public Submodel(final String instanceName, final IRI href) {
            this.instanceName = instanceName;
            this.href = href;
        }
    }
}
