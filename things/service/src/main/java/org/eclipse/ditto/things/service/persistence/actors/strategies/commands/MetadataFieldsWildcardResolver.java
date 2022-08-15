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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.signals.WithFeatureId;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Thing;

@Immutable
final class MetadataFieldsWildcardResolver {

    private static final JsonPointer FEATURES_POINTER = JsonPointer.of("features");
    private static final String PROPERTIES = "properties";
    private static final JsonPointer PROPERTIES_POINTER = JsonPointer.of(PROPERTIES);
    private static final String DESIRED_PROPERTIES = "desiredProperties";
    private static final String DEFINITION = "definition";
    private static final JsonPointer DEFINITION_POINTER = JsonPointer.of(DEFINITION);
    private static final JsonPointer DESIRED_PROPERTIES_POINTER = JsonPointer.of(DESIRED_PROPERTIES);
    private static final String ATTRIBUTES = "attributes";

    private MetadataFieldsWildcardResolver() {
        throw new AssertionError();
    }

    /**
     * Resolves the wildcards inside the {@code jsonPointerWithWildcard}.
     *
     * @param command the command used to determine wildcard replacement.
     * @param thing the thing used to get ids for replacement.
     * @param jsonPointerWithWildcard pointer which contains the wildcards to resolve.
     * @return a set of pointers with replaced wildcards.
     */
    public static Set<JsonPointer> resolve(final Command<?> command, final Thing thing,
            final JsonPointer jsonPointerWithWildcard,
            final String metadataHeaderKey) {
        final JsonPointer resourcePath = command.getResourcePath();
        final int levelCount = resourcePath.getLevelCount();
        final String resourcePathAsString = resourcePath.toString();

        if (resourcePath.equals(MetadataWildcardValidator.ROOT_PATH)) {
            return replaceWildcardForThingBasedCommands(thing, jsonPointerWithWildcard, metadataHeaderKey);
        } else if (resourcePath.equals(MetadataWildcardValidator.FEATURES_PATH)) {
            return replaceWildcardForFeaturesBasedCommands(thing, jsonPointerWithWildcard, metadataHeaderKey);
        } else if (levelCount == 2 && resourcePathAsString.matches(MetadataWildcardValidator.FEATURE_PATH)) {
            return replaceWildcardForFeatureBasedCommands(thing, jsonPointerWithWildcard, metadataHeaderKey, command);
        }

        return Set.of();
    }

    private static Set<JsonPointer> replaceWildcardForThingBasedCommands(final Thing thing,
            final JsonPointer jsonPointerWithWildcard,
            final String metadataHeaderKey) {
        final String wildcardExpression = jsonPointerWithWildcard.toString();

        final Set<String> featureIds = getFeatureIdsFromThing(thing);

        if (MetadataWildcardValidator.matchesThingFeaturesAndPropertiesWildcard(wildcardExpression)) {
            final JsonPointer leafFromWildcardExpression = jsonPointerWithWildcard.getSubPointer(4).orElseThrow(() ->
                    MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression, metadataHeaderKey)
            );
            return replaceFeaturesAndPropertiesWildcard(featureIds, jsonPointerWithWildcard, thing,
                    leafFromWildcardExpression);
        } else if (MetadataWildcardValidator.matchesThingFeaturesWithIdOnlyWildcard(wildcardExpression)) {
            final JsonKey propertyKeyFromWildcardExpr = jsonPointerWithWildcard.get(3).orElseThrow(() ->
                    MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression, metadataHeaderKey)
            );
            final JsonPointer leafFromWildcardExpression = jsonPointerWithWildcard.getSubPointer(4).orElseThrow(() ->
                    MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression, metadataHeaderKey)
            );
            return replaceFeatureIdWildcard(featureIds, jsonPointerWithWildcard,
                    propertyKeyFromWildcardExpr.asPointer(),
                    leafFromWildcardExpression);
        } else if (MetadataWildcardValidator.matchesThingFeaturesWithPropertiesOnlyWildcard(wildcardExpression)) {
            final JsonKey featureIdKeyFromWildcardExpr = jsonPointerWithWildcard.get(1).orElseThrow(() ->
                    MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression, metadataHeaderKey)
            );
            final JsonPointer leafFromWildcardExpression = jsonPointerWithWildcard.getSubPointer(4).orElseThrow(() ->
                    MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression, metadataHeaderKey)
            );
            final String featureId = featureIdKeyFromWildcardExpr.toString();

            if (containsProperties(jsonPointerWithWildcard)) {
                return getReplacedWildcardPointersForPropertyKeysOnFeaturesLevel(
                        getFeaturePropertyKeysFromThing(thing, featureId),
                        featureIdKeyFromWildcardExpr.asPointer(), PROPERTIES_POINTER, leafFromWildcardExpression);
            }
            if (containsDesiredProperties(jsonPointerWithWildcard)) {
                return getReplacedWildcardPointersForPropertyKeysOnFeaturesLevel(
                        getFeatureDesiredPropertyKeysFromThing(thing, featureId),
                        featureIdKeyFromWildcardExpr.asPointer(), DESIRED_PROPERTIES_POINTER,
                        leafFromWildcardExpression);
            }
        } else if (MetadataWildcardValidator.matchesThingFeaturesDefinitionWildcard(wildcardExpression)) {
            final JsonPointer leafFromWildcardExpression = jsonPointerWithWildcard.getSubPointer(3).orElseThrow(() ->
                    MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression, metadataHeaderKey)
            );
            return replaceFeatureDefinitionWildcard(featureIds, leafFromWildcardExpression);
        } else if (MetadataWildcardValidator.matchesAttributesWildcard(wildcardExpression)) {
            final List<String> attributeIds = getAttributesIdsFromThing(thing);
            final JsonKey metadataKey = jsonPointerWithWildcard.get(2).orElseThrow(() ->
                    MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression, metadataHeaderKey)
            );
            return replacedWildcardPointersForAttributeIds(attributeIds, metadataKey.asPointer());
        } else if (MetadataWildcardValidator.matchesLeafWildcard(wildcardExpression)) {
            final JsonKey metadataKey = jsonPointerWithWildcard.get(1).orElseThrow(() ->
                    MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression, metadataHeaderKey)
            );

            return getReplacedWildcardsPointersForLeafs(featureIds, thing, metadataKey);
        }

        return Set.of();
    }

    private static Set<JsonPointer> replaceFeatureDefinitionWildcard(final Set<String> featureIds,
            final JsonPointer metadataKey) {
        return featureIds.stream()
                .map(featureId -> getReplacedWildcardPointerForDefinitionLevel(featureId, metadataKey))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<JsonPointer> replaceFeaturesAndPropertiesWildcard(final Set<String> featureIds,
            final JsonPointer jsonPointerWithWildcard,
            final Thing thing,
            final JsonPointer leafFromWildcardExpression) {
        return featureIds.stream().map(featureId -> {
            Set<JsonPointer> replacedPointers = new HashSet<>();
            if (containsProperties(jsonPointerWithWildcard)) {
                replacedPointers = getReplacedWildcardPointersForPropertyKeysOnFeaturesLevel(
                        getFeaturePropertyKeysFromThing(thing, featureId),
                        JsonPointer.of(featureId), PROPERTIES_POINTER, leafFromWildcardExpression);
            } else if (containsDesiredProperties(jsonPointerWithWildcard)) {
                replacedPointers = getReplacedWildcardPointersForPropertyKeysOnFeaturesLevel(
                        getFeatureDesiredPropertyKeysFromThing(thing, featureId),
                        JsonPointer.of(featureId), DESIRED_PROPERTIES_POINTER, leafFromWildcardExpression);
            }
            return replacedPointers;
        }).flatMap(Collection::stream).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<JsonPointer> replaceFeatureIdWildcard(final Set<String> featureIds,
            final JsonPointer jsonPointerWithWildcard,
            final JsonPointer propertyKeyFromWildcardExpr,
            final JsonPointer leafFromWildcardExpression) {
        return featureIds.stream().map(featureId -> {
                    if (containsProperties(jsonPointerWithWildcard)) {
                        return getReplacedWildcardPointerForFeatureIdOnFeaturesLevel(JsonPointer.of(featureId),
                                PROPERTIES_POINTER, propertyKeyFromWildcardExpr, leafFromWildcardExpression);
                    }
                    if (containsDesiredProperties(jsonPointerWithWildcard)) {
                        return getReplacedWildcardPointerForFeatureIdOnFeaturesLevel(JsonPointer.of(featureId),
                                DESIRED_PROPERTIES_POINTER, propertyKeyFromWildcardExpr, leafFromWildcardExpression);
                    }
                    return JsonPointer.empty();
                }
        ).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<JsonPointer> replaceWildcardForFeaturesBasedCommands(final Thing thing,
            final JsonPointer jsonPointerWithWildcard,
            final String headerKey) {
        final Set<String> featureIds = getFeatureIdsFromThing(thing);
        final String wildcardExpression = jsonPointerWithWildcard.toString();
        final JsonPointer leafFromWildcardExpression = jsonPointerWithWildcard.getSubPointer(3).orElseThrow(() ->
                MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression, headerKey)
        );

        if (MetadataWildcardValidator.matchesFeaturesWildcard(wildcardExpression)) {
            return replaceFeatureAndPropertyWildcardForFeaturesBasedCommands(featureIds, jsonPointerWithWildcard, thing,
                    leafFromWildcardExpression);
        } else if (MetadataWildcardValidator.matchesFeaturesWithIdOnlyWildcard(wildcardExpression)) {
            final JsonKey propertyKeyFromWildcardExpr = jsonPointerWithWildcard.get(2).orElseThrow(() ->
                    MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression, headerKey)
            );
            return replaceFeatureIdWildcardForFeaturesBasedCommands(featureIds, jsonPointerWithWildcard,
                    propertyKeyFromWildcardExpr.asPointer(), leafFromWildcardExpression);
        } else if (MetadataWildcardValidator.matchesFeaturesWithPropertyOnlyWildcard(wildcardExpression) ||
                MetadataWildcardValidator.matchesFeaturePropertyWildcard(wildcardExpression)) {
            final JsonKey featureIdFromWildcardExpr = jsonPointerWithWildcard.get(0).orElseThrow(() ->
                    MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression, headerKey)
            );
            final String featureId = featureIdFromWildcardExpr.toString();
            if (containsProperties(jsonPointerWithWildcard)) {
                return replacedWildcardPointersForPropertyKeyOnFeatureLevel(
                        getFeaturePropertyKeysFromThing(thing, featureId), JsonPointer.of(featureId),
                        PROPERTIES_POINTER, leafFromWildcardExpression);
            }
            if (containsDesiredProperties(jsonPointerWithWildcard)) {
                return replacedWildcardPointersForPropertyKeyOnFeatureLevel(
                        getFeatureDesiredPropertyKeysFromThing(thing, featureId),
                        JsonPointer.of(featureId), DESIRED_PROPERTIES_POINTER, leafFromWildcardExpression);
            }
        }

        return Set.of();
    }

    private static Set<JsonPointer> replaceFeatureAndPropertyWildcardForFeaturesBasedCommands(
            final Set<String> featureIds,
            final JsonPointer jsonPointerWithWildcard,
            final Thing thing, final JsonPointer leafFromWildcardExpression) {
        return featureIds.stream().map(featureId -> {
                    Set<JsonPointer> replacedPointers = new HashSet<>();
                    if (containsProperties(jsonPointerWithWildcard)) {
                        replacedPointers = replacedWildcardPointersForPropertyKeyOnFeatureLevel(
                                getFeaturePropertyKeysFromThing(thing, featureId), JsonPointer.of(featureId),
                                PROPERTIES_POINTER, leafFromWildcardExpression);
                    }
                    if (containsDesiredProperties(jsonPointerWithWildcard)) {
                        replacedPointers = replacedWildcardPointersForPropertyKeyOnFeatureLevel(
                                getFeatureDesiredPropertyKeysFromThing(thing, featureId),
                                JsonPointer.of(featureId), DESIRED_PROPERTIES_POINTER,
                                leafFromWildcardExpression);
                    }

                    return replacedPointers;
                }
        ).flatMap(Collection::stream).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<JsonPointer> replaceFeatureIdWildcardForFeaturesBasedCommands(final Set<String> featureIds,
            final JsonPointer jsonPointerWithWildcard,
            final JsonPointer propertyKeyFromWildcardExpr,
            final JsonPointer leafFromWildcardExpression) {
        return featureIds.stream().map(featureId -> {
            if (containsProperties(jsonPointerWithWildcard)) {
                return replacedWildcardPointerForFeatureIdOnFeatureLevel(JsonPointer.of(featureId),
                        PROPERTIES_POINTER, propertyKeyFromWildcardExpr, leafFromWildcardExpression);
            }
            if (containsDesiredProperties(jsonPointerWithWildcard)) {
                return replacedWildcardPointerForFeatureIdOnFeatureLevel(JsonPointer.of(featureId),
                        DESIRED_PROPERTIES_POINTER, propertyKeyFromWildcardExpr, leafFromWildcardExpression);
            }

            return JsonPointer.empty();
        }).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<JsonPointer> replaceWildcardForFeatureBasedCommands(final Thing thing,
            final JsonPointer jsonPointerWithWildcard,
            final String headerKey,
            final Command<?> command) {
        final String featureId = getFeatureIdForCommand(command);
        final String wildcardExpression = jsonPointerWithWildcard.toString();
        final Set<String> propertyKeys = getFeaturePropertyKeysFromThing(thing, featureId);
        final JsonPointer leafFromWildcardExpression = jsonPointerWithWildcard.getSubPointer(2).orElseThrow(() ->
                MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression, headerKey)
        );

        return propertyKeys.stream().map(propertyKey -> {
                    Set<JsonPointer> replacedPointers = new HashSet<>();
                    if (containsProperties(jsonPointerWithWildcard)) {
                        replacedPointers =
                                getReplacedWildcardPointersForPropertyKeysOnPropertyLevel(propertyKeys, PROPERTIES_POINTER,
                                        leafFromWildcardExpression);
                    }
                    if (containsDesiredProperties(jsonPointerWithWildcard)) {
                        final Set<String> desiredPropertyKeys =
                                getFeatureDesiredPropertyKeysFromThing(thing, featureId);
                        replacedPointers = getReplacedWildcardPointersForPropertyKeysOnPropertyLevel(desiredPropertyKeys,
                                DESIRED_PROPERTIES_POINTER, leafFromWildcardExpression);
                    }

                    return replacedPointers;
                }
        ).flatMap(Collection::stream).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<JsonPointer> getReplacedWildcardPointersForPropertyKeysOnFeaturesLevel(
            final Set<String> propertyKeys,
            final JsonPointer featureId,
            final JsonPointer propertiesPointer,
            final JsonPointer leafFromWildcardExpression) {
        return propertyKeys.stream().map(propertyKey ->
                        getReplacedWildcardPointerForFeatureIdOnFeaturesLevel(featureId, propertiesPointer,
                                JsonPointer.of(propertyKey), leafFromWildcardExpression))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static JsonPointer getReplacedWildcardPointerForFeatureIdOnFeaturesLevel(final JsonPointer featureId,
            final JsonPointer propertiesPointer,
            final JsonPointer propertyKey,
            final JsonPointer leafFromWildcardExpression) {
        return JsonPointer.empty()
                .append(FEATURES_POINTER)
                .append(JsonPointer.of(featureId))
                .append(propertiesPointer)
                .append(propertyKey)
                .append(leafFromWildcardExpression);
    }

    private static Set<JsonPointer> replacedWildcardPointersForPropertyKeyOnFeatureLevel(
            final Set<String> propertyKeys,
            final JsonPointer featureId,
            final JsonPointer propertiesPointer,
            final JsonPointer leafFromWildcardExpression) {
        return propertyKeys.stream().map(propertyKey ->
                        replacedWildcardPointerForFeatureIdOnFeatureLevel(featureId, propertiesPointer,
                                JsonPointer.of(propertyKey), leafFromWildcardExpression))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static JsonPointer replacedWildcardPointerForFeatureIdOnFeatureLevel(final JsonPointer featureId,
            final JsonPointer propertiesPointer,
            final JsonPointer propertyKey,
            final JsonPointer leafFromWildcardExpression) {
        return JsonPointer.empty()
                .append(JsonPointer.of(featureId))
                .append(propertiesPointer)
                .append(propertyKey)
                .append(leafFromWildcardExpression);
    }

    private static Set<JsonPointer> getReplacedWildcardPointersForPropertyKeysOnPropertyLevel(
            final Set<String> propertyKeys,
            final JsonPointer propertiesPointer,
            final JsonPointer leafFromWildcardExpression) {
        return propertyKeys.stream().map(propertyKey ->
                        JsonPointer.empty()
                                .append(propertiesPointer)
                                .append(JsonPointer.of(propertyKey))
                                .append(leafFromWildcardExpression))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<JsonPointer> getReplacedWildcardsPointersForLeafs(final Set<String> featureIds,
            final Thing thing, final JsonKey metadataKey) {
        final Set<JsonPointer> replacedWildcardsPointers = new LinkedHashSet<>();
        replacedWildcardsPointers.add(JsonPointer.of("thingId/" + metadataKey));
        replacedWildcardsPointers.add(JsonPointer.of("policyId/" + metadataKey));

        thing.getAttributes().ifPresent(attributes -> replacedWildcardsPointers.addAll(
                getReplacedWildcardPointersForAttributeIds(getAttributesLeafsFromThing(attributes),
                        metadataKey.asPointer())));

        featureIds.forEach(featureId -> {
                    if (isFeatureDefinitionPresent(thing, featureId)) {
                        final JsonPointer jsonPointer =
                                getReplacedWildcardPointerForDefinitionLevel(featureId, metadataKey.asPointer());
                        replacedWildcardsPointers.add(jsonPointer);
                    }

                    replacedWildcardsPointers.addAll(
                            getReplacedWildcardPointersForPropertyKeysOnFeaturesLevel(
                                    getFeaturePropertyLeafsFromThing(thing, featureId), JsonPointer.of(featureId),
                                    PROPERTIES_POINTER, metadataKey.asPointer()));

                    replacedWildcardsPointers.addAll(
                            getReplacedWildcardPointersForPropertyKeysOnFeaturesLevel(
                                    getPropertyLeafsFromThing(thing, featureId), JsonPointer.of(featureId),
                                    DESIRED_PROPERTIES_POINTER, metadataKey.asPointer()));
                }
        );

        return replacedWildcardsPointers;
    }

    private static JsonPointer getReplacedWildcardPointerForDefinitionLevel(final String featureId,
            final JsonPointer metadataKey) {
        return JsonPointer.empty()
                .append(FEATURES_POINTER)
                .append(JsonPointer.of(featureId))
                .append(DEFINITION_POINTER)
                .append(metadataKey);
    }

    private static Set<JsonPointer> getReplacedWildcardPointersForAttributeIds(final Set<String> attributeIds,
            final JsonPointer metadataKey) {
        return attributeIds.stream().map(attributeId ->
                        JsonPointer.empty()
                                .append(JsonPointer.of(ATTRIBUTES))
                                .append(JsonPointer.of(attributeId))
                                .append(metadataKey))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<JsonPointer> replacedWildcardPointersForAttributeIds(final List<String> attributeIds,
            final JsonPointer metadataKey) {
        return attributeIds.stream().map(attributeId ->
                        JsonPointer.empty()
                                .append(JsonPointer.of(ATTRIBUTES))
                                .append(JsonPointer.of(attributeId))
                                .append(metadataKey))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean containsProperties(final JsonPointer wildcardPointer) {
        return wildcardPointer.toString().contains(PROPERTIES);
    }

    private static boolean containsDesiredProperties(final JsonPointer wildcardPointer) {
        return wildcardPointer.toString().contains(DESIRED_PROPERTIES);
    }

    private static Set<String> getFeatureIdsFromThing(final Thing thing) {
        return thing.getFeatures()
                .map(features -> features.stream().map(Feature::getId)
                        .collect(Collectors.toCollection(LinkedHashSet::new)))
                .orElseGet(LinkedHashSet::new);
    }

    private static String getFeatureIdForCommand(final Command<?> command) {
        if (command instanceof WithFeatureId withFeatureId) {
            return withFeatureId.getFeatureId();
        } else {
            return command.getResourcePath()
                    .get(1)
                    .orElseThrow(() -> new IllegalStateException("Feature Id is missing but should be in path."))
                    .toString();
        }
    }

    private static Set<String> getFeaturePropertyKeysFromThing(final Thing thing, final String featureId) {
        return thing.getFeatures()
                .flatMap(features -> features.getFeature(featureId))
                .flatMap(Feature::getProperties)
                .map(JsonObject::getKeys)
                .map(jsonKeys -> jsonKeys.stream()
                        .map(Objects::toString)
                        .collect(Collectors.toCollection(LinkedHashSet::new)))
                .orElseGet(LinkedHashSet::new);
    }

    private static Set<String> getFeatureDesiredPropertyKeysFromThing(final Thing thing, final String featureId) {
        return thing.getFeatures()
                .flatMap(features -> features.getFeature(featureId))
                .flatMap(Feature::getDesiredProperties)
                .map(JsonObject::getKeys)
                .map(jsonKeys -> jsonKeys.stream()
                        .map(Objects::toString)
                        .collect(Collectors.toCollection(LinkedHashSet::new)))
                .orElseGet(LinkedHashSet::new);
    }

    private static Set<String> getFeaturePropertyLeafsFromThing(final Thing thing, final String featureId) {
        final Set<String> propertyKeysFromThing = getFeaturePropertyKeysFromThing(thing, featureId);
        final Set<JsonPointer> jsonPointerPropertyKeys = propertyKeysFromThing.stream().map(JsonPointer::of)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        @Nullable final FeatureProperties featureProperties = thing.getFeatures()
                .flatMap(features -> features.getFeature(featureId))
                .flatMap(Feature::getProperties)
                .orElse(null);

        return getPropertyLeafsFromThing(jsonPointerPropertyKeys, featureProperties);
    }

    private static Set<String> getPropertyLeafsFromThing(final Thing thing, final String featureId) {
        final Set<String> propertyKeysFromThing = getFeatureDesiredPropertyKeysFromThing(thing, featureId);
        final Set<JsonPointer> jsonPointerPropertyKeys = propertyKeysFromThing.stream().map(JsonPointer::of)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        final FeatureProperties featureDesiredProperties = thing.getFeatures()
                .flatMap(features -> features.getFeature(featureId))
                .flatMap(Feature::getDesiredProperties)
                .orElse(null);

        return getPropertyLeafsFromThing(jsonPointerPropertyKeys, featureDesiredProperties);
    }

    private static Set<String> getPropertyLeafsFromThing(final Set<JsonPointer> jsonPointerPropertyKeys,
            @Nullable final FeatureProperties featureProperties) {
        if (featureProperties != null) {
            return getFeaturePropertyLeafs(jsonPointerPropertyKeys, featureProperties);
        }

        return Set.of();
    }

    private static Set<String> getFeaturePropertyLeafs(final Set<JsonPointer> propertyKeysFromThing,
            final FeatureProperties featureProperties) {
        final Set<String> featurePropertyLeafs = new LinkedHashSet<>();
        propertyKeysFromThing.forEach(jsonPointer ->
                getLeafsFromObject(JsonPointer.empty(), featureProperties.get(jsonPointer), featurePropertyLeafs)
        );

        return featurePropertyLeafs;
    }

    private static void getLeafsFromObject(final JsonPointer path, final JsonValue entity, final Set<String> leafs) {
        if (entity.isObject()) {
            final JsonObject jsonObject = entity.asObject();
            jsonObject.stream()
                    .filter(field -> !(field.isMarkedAs(FieldType.SPECIAL) || field.isMarkedAs(FieldType.HIDDEN)))
                    .forEach(jsonField -> {
                        final JsonKey key = jsonField.getKey();
                        getLeafsFromObject(path.append(key.asPointer()), jsonField.getValue(), leafs);
                    });
        } else {
            leafs.add(path.toString());
        }
    }


    private static List<String> getAttributesIdsFromThing(final Thing thing) {
        return thing.getAttributes()
                .map(JsonObject::getKeys)
                .map(jsonKeys -> jsonKeys.stream().map(Objects::toString).toList())
                .orElse(List.of());
    }

    private static Set<String> getAttributesLeafsFromThing(final Attributes attributes) {
        final Set<String> attributesLeafs = new LinkedHashSet<>();
        getLeafsFromObject(JsonPointer.empty(), attributes, attributesLeafs);

        return attributesLeafs;
    }

    private static boolean isFeatureDefinitionPresent(final Thing thing, final String featureId) {
        return thing.getFeatures()
                .flatMap(features -> features.getFeature(featureId))
                .filter(feature -> feature.getDefinition().isPresent())
                .isPresent();
    }

}
