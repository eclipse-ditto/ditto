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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.signals.WithFeatureId;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttributes;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeature;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureDesiredProperties;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureProperties;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatures;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttributes;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeature;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDesiredProperties;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureProperties;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatures;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;

@Immutable
final class MetadataFieldsWildcardResolver {

    private static final JsonPointer FEATURES_POINTER = JsonPointer.of("features");
    private static final String PROPERTIES = "properties";
    private static final JsonPointer PROPERTIES_POINTER = JsonPointer.of(PROPERTIES);
    private static final String DESIRED_PROPERTIES = "desiredProperties";
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
    public static Set<JsonPointer> resolve(final Command<?> command,
            final Thing thing,
            final JsonPointer jsonPointerWithWildcard,
            final String metadataHeaderKey) {
        return switch (command.getType()) {
            case RetrieveThing.TYPE, ModifyThing.TYPE, CreateThing.TYPE ->
                    replaceWildcardForThingBasedCommands(thing, jsonPointerWithWildcard, metadataHeaderKey);
            case RetrieveFeatures.TYPE, ModifyFeatures.TYPE ->
                    replaceWildcardForFeaturesBasedCommands(thing, jsonPointerWithWildcard, metadataHeaderKey);
            case RetrieveFeature.TYPE, ModifyFeature.TYPE ->
                    replaceWildcardForFeatureBasedCommands(thing, jsonPointerWithWildcard, metadataHeaderKey,
                            (WithFeatureId) command);
            case RetrieveFeatureProperties.TYPE, ModifyFeatureProperties.TYPE ->
                    replaceWildcardForFeaturePropertiesBasedCommands(thing, jsonPointerWithWildcard, metadataHeaderKey,
                            (WithFeatureId) command);
            case RetrieveFeatureDesiredProperties.TYPE, ModifyFeatureDesiredProperties.TYPE ->
                    replaceWildcardForFeatureDesiredPropertiesBasedCommands(thing, jsonPointerWithWildcard,
                            metadataHeaderKey, (WithFeatureId) command);
            case RetrieveAttributes.TYPE, ModifyAttributes.TYPE ->
                    replaceWildcardForAttributesBasedCommands(thing, jsonPointerWithWildcard, metadataHeaderKey);
            default -> Set.of();
        };
    }

    private static Set<JsonPointer> replaceWildcardForThingBasedCommands(final Thing thing,
            final JsonPointer jsonPointerWithWildcard,
            final String metadataHeaderKey) {
        final String wildcardExpression = jsonPointerWithWildcard.toString();
        final JsonKey leafFromWildcardExpression = jsonPointerWithWildcard.getLeaf().orElseThrow(() ->
                MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression, metadataHeaderKey)
        );
        final List<String> featureIds = getFeatureIdsFromThing(thing);

        if (MetadataWildcardValidator.matchesThingFeaturesAndPropertiesWildcard(wildcardExpression)) {
            return replaceFeaturesAndPropertiesWildcard(featureIds, jsonPointerWithWildcard, thing,
                    leafFromWildcardExpression.asPointer());
        } else if (MetadataWildcardValidator.matchesThingFeaturesWithIdOnlyWildcard(wildcardExpression)) {
            final JsonKey propertyKeyFromWildcardExpr = jsonPointerWithWildcard.get(3).orElseThrow(() ->
                    MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression, metadataHeaderKey)
            );
            return replaceFeatureIdWildcard(featureIds, jsonPointerWithWildcard,
                    propertyKeyFromWildcardExpr.asPointer(),
                    leafFromWildcardExpression.asPointer());
        } else if (MetadataWildcardValidator.matchesThingFeaturesWithPropertiesOnlyWildcard(wildcardExpression)) {
            final JsonKey featureIdKeyFromWildcardExpr = jsonPointerWithWildcard.get(1).orElseThrow(() ->
                    MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression, metadataHeaderKey)
            );
            final String featureId = featureIdKeyFromWildcardExpr.toString();

            if (containsProperties(jsonPointerWithWildcard)) {
                return getReplacedWildcardPointersForPropertyKeysOnFeaturesLevel(
                        getFeaturePropertyKeysFromThing(thing, featureId),
                        featureIdKeyFromWildcardExpr.asPointer(),
                        PROPERTIES_POINTER, leafFromWildcardExpression.asPointer());
            } else if (containsDesiredProperties(jsonPointerWithWildcard)) {
                return getReplacedWildcardPointersForPropertyKeysOnFeaturesLevel(
                        getFeatureDesiredPropertyKeysFromThing(thing, featureId),
                        featureIdKeyFromWildcardExpr.asPointer(), DESIRED_PROPERTIES_POINTER,
                        leafFromWildcardExpression.asPointer());
            }
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

    private static Set<JsonPointer> replaceFeaturesAndPropertiesWildcard(final List<String> featureIds,
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
        }).flatMap(Collection::stream).collect(Collectors.toSet());
    }

    private static Set<JsonPointer> replaceFeatureIdWildcard(final List<String> featureIds,
            final JsonPointer jsonPointerWithWildcard,
            final JsonPointer propertyKeyFromWildcardExpr,
            final JsonPointer leafFromWildcardExpression) {
        return featureIds.stream().map(featureId -> {
                    if (containsProperties(jsonPointerWithWildcard)) {
                        return getReplacedWildcardPointersForFeatureIdOnFeaturesLevel(JsonPointer.of(featureId),
                                PROPERTIES_POINTER, propertyKeyFromWildcardExpr, leafFromWildcardExpression);
                    } else if (containsDesiredProperties(jsonPointerWithWildcard)) {
                        return getReplacedWildcardPointersForFeatureIdOnFeaturesLevel(JsonPointer.of(featureId),
                                DESIRED_PROPERTIES_POINTER, propertyKeyFromWildcardExpr, leafFromWildcardExpression);
                    }
                    return JsonPointer.empty();
                }
        ).collect(Collectors.toSet());
    }

    private static Set<JsonPointer> replaceWildcardForFeaturesBasedCommands(final Thing thing,
            final JsonPointer jsonPointerWithWildcard,
            final String headerKey) {
        final List<String> featureIds = getFeatureIdsFromThing(thing);
        final String wildcardExpression = jsonPointerWithWildcard.toString();
        final JsonKey leafFromWildcardExpression = jsonPointerWithWildcard.getLeaf().orElseThrow(() ->
                MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression, headerKey)
        );

        if (MetadataWildcardValidator.matchesFeaturesWildcard(wildcardExpression)) {
            return replaceFeatureAndPropertyWildcardForFeaturesBasedCommands(featureIds, jsonPointerWithWildcard, thing,
                    leafFromWildcardExpression.asPointer());
        } else if (MetadataWildcardValidator.matchesFeaturesWithIdOnlyWildcard(wildcardExpression)) {
            final JsonKey propertyKeyFromWildcardExpr = jsonPointerWithWildcard.get(2).orElseThrow(() ->
                    MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression, headerKey)
            );
            return replaceFeatureIdWildcardForFeaturesBasedCommands(featureIds, jsonPointerWithWildcard,
                    propertyKeyFromWildcardExpr.asPointer(), leafFromWildcardExpression.asPointer());
        } else if (MetadataWildcardValidator.matchesFeaturesWithPropertyOnlyWildcard(wildcardExpression) ||
                MetadataWildcardValidator.matchesFeaturePropertyWildcard(wildcardExpression)) {
            final JsonKey featureIdFromWildcardExpr = jsonPointerWithWildcard.get(0).orElseThrow(() ->
                    MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression, headerKey)
            );
            final String featureId = featureIdFromWildcardExpr.toString();
            if (containsProperties(jsonPointerWithWildcard)) {
                return replacedWildcardPointersForPropertyKeyOnFeatureLevel(
                        getFeaturePropertyKeysFromThing(thing, featureId), JsonPointer.of(featureId),
                        PROPERTIES_POINTER, leafFromWildcardExpression.asPointer());
            } else if (containsDesiredProperties(jsonPointerWithWildcard)) {
                return replacedWildcardPointersForPropertyKeyOnFeatureLevel(
                        getFeatureDesiredPropertyKeysFromThing(thing, featureId),
                        JsonPointer.of(featureId), DESIRED_PROPERTIES_POINTER, leafFromWildcardExpression.asPointer());
            }
        }

        return Set.of();
    }

    private static Set<JsonPointer> replaceFeatureAndPropertyWildcardForFeaturesBasedCommands(
            final List<String> featureIds,
            final JsonPointer jsonPointerWithWildcard,
            final Thing thing, final JsonPointer leafFromWildcardExpression) {
        return featureIds.stream().map(featureId -> {
                    Set<JsonPointer> replacedPointers = new HashSet<>();
                    if (containsProperties(jsonPointerWithWildcard)) {
                        replacedPointers = replacedWildcardPointersForPropertyKeyOnFeatureLevel(
                                getFeaturePropertyKeysFromThing(thing, featureId), JsonPointer.of(featureId),
                                PROPERTIES_POINTER, leafFromWildcardExpression);
                    } else if (containsDesiredProperties(jsonPointerWithWildcard)) {
                        final List<String> desiredPropertyKeys =
                                getFeatureDesiredPropertyKeysFromThing(thing, featureId);
                        replacedPointers = replacedWildcardPointersForPropertyKeyOnFeatureLevel(desiredPropertyKeys,
                                JsonPointer.of(featureId), DESIRED_PROPERTIES_POINTER,
                                leafFromWildcardExpression);
                    }
                    return replacedPointers;
                }
        ).flatMap(Collection::stream).collect(Collectors.toSet());
    }

    private static Set<JsonPointer> replaceFeatureIdWildcardForFeaturesBasedCommands(final List<String> featureIds,
            final JsonPointer jsonPointerWithWildcard,
            final JsonPointer propertyKeyFromWildcardExpr,
            final JsonPointer leafFromWildcardExpression) {
        return featureIds.stream().map(featureId -> {
            if (containsProperties(jsonPointerWithWildcard)) {
                return replacedWildcardPointersForFeatureIdOnFeatureLevel(JsonPointer.of(featureId),
                        PROPERTIES_POINTER, propertyKeyFromWildcardExpr, leafFromWildcardExpression);
            } else if (containsDesiredProperties(jsonPointerWithWildcard)) {
                return replacedWildcardPointersForFeatureIdOnFeatureLevel(JsonPointer.of(featureId),
                        DESIRED_PROPERTIES_POINTER, propertyKeyFromWildcardExpr, leafFromWildcardExpression);
            }
            return JsonPointer.empty();
        }).collect(Collectors.toSet());
    }

    private static Set<JsonPointer> replaceWildcardForFeatureBasedCommands(final Thing thing,
            final JsonPointer jsonPointerWithWildcard,
            final String headerKey,
            final WithFeatureId command) {
        final String wildcardExpression = jsonPointerWithWildcard.toString();
        final String featureId = command.getFeatureId();
        final List<String> propertyKeys = getFeaturePropertyKeysFromThing(thing, featureId);
        final JsonKey leafFromWildcardExpression = jsonPointerWithWildcard.getLeaf().orElseThrow(() ->
                MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression, headerKey)
        );

        return propertyKeys.stream().map(propertyKey -> {
                    Set<JsonPointer> replacedPointers = new HashSet<>();
                    if (containsProperties(jsonPointerWithWildcard)) {
                        replacedPointers =
                                getReplacedWildcardPointersForPropertyKeysOnPropertyLevel(propertyKeys, PROPERTIES_POINTER,
                                        leafFromWildcardExpression.asPointer());
                    } else if (containsDesiredProperties(jsonPointerWithWildcard)) {
                        final List<String> desiredPropertyKeys =
                                getFeatureDesiredPropertyKeysFromThing(thing, featureId);
                        replacedPointers = getReplacedWildcardPointersForPropertyKeysOnPropertyLevel(desiredPropertyKeys,
                                DESIRED_PROPERTIES_POINTER, leafFromWildcardExpression.asPointer());
                    }
                    return replacedPointers;
                }
        ).flatMap(Collection::stream).collect(Collectors.toSet());
    }

    private static Set<JsonPointer> replaceWildcardForFeaturePropertiesBasedCommands(final Thing thing,
            final JsonPointer jsonPointerWithWildcard,
            final String headerKey,
            final WithFeatureId command) {
        final String wildcardExpression = jsonPointerWithWildcard.toString();
        final String featureIdFromCommand = command.getFeatureId();
        final List<String> propertyKeys = getFeaturePropertyKeysFromThing(thing, featureIdFromCommand);
        final JsonKey leafFromWildcardExpression = jsonPointerWithWildcard.getLeaf().orElseThrow(() ->
                MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression, headerKey)
        );

        return getReplacedWildcardPointersForPropertyKeysOnPropertiesLevel(propertyKeys,
                leafFromWildcardExpression.asPointer());
    }

    private static Set<JsonPointer> replaceWildcardForFeatureDesiredPropertiesBasedCommands(final Thing thing,
            final JsonPointer jsonPointerWithWildcard,
            final String headerKey,
            final WithFeatureId command) {
        final String wildcardExpression = jsonPointerWithWildcard.toString();
        final String featureIdFromCommand = command.getFeatureId();
        final JsonKey leafFromWildcardExpression = jsonPointerWithWildcard.getLeaf().orElseThrow(() ->
                MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression, headerKey)
        );

        return getReplacedWildcardPointersForPropertyKeysOnPropertiesLevel(
                getFeatureDesiredPropertyKeysFromThing(thing, featureIdFromCommand),
                leafFromWildcardExpression.asPointer());
    }

    private static Set<JsonPointer> replaceWildcardForAttributesBasedCommands(final Thing thing,
            final JsonPointer jsonPointerWithWildcard,
            final String headerKey) {
        final String wildcardExpression = jsonPointerWithWildcard.toString();
        final List<String> attributeIds = getAttributesIdsFromThing(thing);

        if (MetadataWildcardValidator.matchesLeafWildcard(wildcardExpression)) {
            final JsonKey attributeKey = jsonPointerWithWildcard.get(1).orElseThrow(() ->
                    MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression, headerKey)
            );

            return attributeIds.stream().map(attributeId ->
                    JsonPointer.empty()
                            .append(JsonPointer.of(attributeId))
                            .append(attributeKey.asPointer())
            ).collect(Collectors.toSet());
        }
        return Set.of();
    }

    private static Set<JsonPointer> getReplacedWildcardPointersForPropertyKeysOnFeaturesLevel(
            final List<String> propertyKeys,
            final JsonPointer featureId,
            final JsonPointer propertiesPointer,
            final JsonPointer leafFromWildcardExpression) {
        return propertyKeys.stream().map(propertyKey ->
                getReplacedWildcardPointersForFeatureIdOnFeaturesLevel(featureId, propertiesPointer,
                        JsonPointer.of(propertyKey), leafFromWildcardExpression)
        ).collect(Collectors.toSet());
    }

    private static JsonPointer getReplacedWildcardPointersForFeatureIdOnFeaturesLevel(final JsonPointer featureId,
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
            final List<String> propertyKeys,
            final JsonPointer featureId,
            final JsonPointer propertiesPointer,
            final JsonPointer leafFromWildcardExpression) {
        return propertyKeys.stream().map(propertyKey ->
                        replacedWildcardPointersForFeatureIdOnFeatureLevel(featureId, propertiesPointer,
                                JsonPointer.of(propertyKey), leafFromWildcardExpression))
                .collect(Collectors.toSet());
    }

    private static JsonPointer replacedWildcardPointersForFeatureIdOnFeatureLevel(final JsonPointer featureId,
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
            final List<String> propertyKeys,
            final JsonPointer propertiesPointer,
            final JsonPointer leafFromWildcardExpression) {
        return propertyKeys.stream().map(propertyKey ->
                        JsonPointer.empty()
                                .append(propertiesPointer)
                                .append(JsonPointer.of(propertyKey))
                                .append(leafFromWildcardExpression))
                .collect(Collectors.toSet());
    }

    private static Set<JsonPointer> getReplacedWildcardsPointersForLeafs(final List<String> featureIds,
            final Thing thing,
            final JsonKey metadataKey) {
        final Set<JsonPointer> replacedWildcardsPointers = new LinkedHashSet<>();
        replacedWildcardsPointers.add(JsonPointer.of("thingId/" + metadataKey));
        replacedWildcardsPointers.add(JsonPointer.of("policyId/" + metadataKey));

        replacedWildcardsPointers.addAll(
                getReplacedWildcardPointersForAttributeIds(getAttributesLeafsFromThing(thing),
                        metadataKey.asPointer()));

        featureIds.forEach(featureId -> {
                    replacedWildcardsPointers.addAll(
                            getReplacedWildcardPointersForPropertyKeysOnFeaturesLevel(
                                    getFeaturePropertyLeafsFromThing(thing, featureId), JsonPointer.of(featureId),
                                    PROPERTIES_POINTER, metadataKey.asPointer()));

                    replacedWildcardsPointers.addAll(
                            getReplacedWildcardPointersForPropertyKeysOnFeaturesLevel(
                                    getFeatureDesiredPropertyKeysFromThing(thing, featureId),
                                    JsonPointer.of(featureId), DESIRED_PROPERTIES_POINTER, metadataKey.asPointer()));
                }
        );

        return replacedWildcardsPointers;
    }

    private static Set<JsonPointer> getReplacedWildcardPointersForPropertyKeysOnPropertiesLevel(
            final List<String> propertyKeys,
            final JsonPointer leafFromWildcardExpression) {
        return propertyKeys.stream().map(propertyKey ->
                        JsonPointer.empty()
                                .append(JsonPointer.of(propertyKey))
                                .append(leafFromWildcardExpression))
                .collect(Collectors.toSet());
    }

    private static Set<JsonPointer> getReplacedWildcardPointersForAttributeIds(final List<String> attributeIds,
            final JsonPointer metadataKey) {
        return attributeIds.stream().map(attributeId ->
                        JsonPointer.empty()
                                .append(JsonPointer.of(ATTRIBUTES))
                                .append(JsonPointer.of(attributeId))
                                .append(metadataKey))
                .collect(Collectors.toSet());
    }

    private static Set<JsonPointer> replacedWildcardPointersForAttributeIds(final List<String> attributeIds,
            final JsonPointer metadataKey) {
        return attributeIds.stream().map(attributeId ->
                        JsonPointer.empty()
                                .append(JsonPointer.of(ATTRIBUTES))
                                .append(JsonPointer.of(attributeId))
                                .append(metadataKey))
                .collect(Collectors.toSet());
    }

    private static boolean containsProperties(final JsonPointer wildcardPointer) {
        return wildcardPointer.toString().contains(PROPERTIES);
    }

    private static boolean containsDesiredProperties(final JsonPointer wildcardPointer) {
        return wildcardPointer.toString().contains(DESIRED_PROPERTIES);
    }

    private static List<String> getFeatureIdsFromThing(final Thing thing) {
        return thing.getFeatures()
                .map(features -> features.stream().map(Feature::getId).toList())
                .orElseGet(List::of);
    }

    private static List<String> getFeaturePropertyKeysFromThing(final Thing thing, final String featureId) {
        return thing.getFeatures()
                .flatMap(features -> features.getFeature(featureId))
                .flatMap(Feature::getProperties)
                .map(JsonObject::getKeys)
                .map(jsonKeys -> jsonKeys.stream().map(Objects::toString).toList())
                .orElse(List.of());
    }

    private static List<String> getFeaturePropertyLeafsFromThing(final Thing thing, final String featureId) {
        final List<String> propertyKeysFromThing = getFeaturePropertyKeysFromThing(thing, featureId);
        final List<JsonPointer> jsonPointerPropertyKeys = propertyKeysFromThing.stream().map(JsonPointer::of).toList();

        final FeatureProperties featureProperties = thing.getFeatures()
                .flatMap(features -> features.getFeature(featureId))
                .flatMap(Feature::getProperties)
                .orElse(null);

        if (featureProperties != null) {
            return getFeaturePropertyLeafs(jsonPointerPropertyKeys, featureProperties);
        }

        return List.of();
    }

    private static List<String> getFeaturePropertyLeafs(final List<JsonPointer> propertyKeysFromThing,
            final FeatureProperties featureProperties) {
        final List<String> featurePropertyLeafs = new ArrayList<>();
        propertyKeysFromThing.forEach(jsonPointer ->
                getLeafsFromObject(JsonPointer.empty(), featureProperties.get(jsonPointer), featurePropertyLeafs)
        );

        return featurePropertyLeafs;
    }

    private static void getLeafsFromObject(final JsonPointer path, final JsonValue entity, final List<String> leafs) {
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

    private static List<String> getFeatureDesiredPropertyKeysFromThing(final Thing thing, final String featureId) {
        return thing.getFeatures()
                .flatMap(features -> features.getFeature(featureId))
                .flatMap(Feature::getDesiredProperties)
                .map(JsonObject::getKeys)
                .map(jsonKeys -> jsonKeys.stream().map(Objects::toString).toList())
                .orElse(List.of());
    }

    private static List<String> getAttributesIdsFromThing(final Thing thing) {
        return thing.getAttributes()
                .map(JsonObject::getKeys)
                .map(jsonKeys -> jsonKeys.stream().map(Objects::toString).toList())
                .orElse(List.of());
    }


    private static List<String> getAttributesLeafsFromThing(final Thing thing) {
        final List<String> attributesLeafs = new ArrayList<>();
        getLeafsFromObject(JsonPointer.empty(), thing.getAttributes().orElseThrow(), attributesLeafs);

        return attributesLeafs;
    }

}
