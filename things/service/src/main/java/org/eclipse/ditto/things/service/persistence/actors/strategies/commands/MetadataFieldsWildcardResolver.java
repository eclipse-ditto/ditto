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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Thing;
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
    public static Set<JsonPointer> resolve(final Command<?> command, final Thing thing,
            final JsonPointer jsonPointerWithWildcard, final String headerKey) {

        return switch (command.getType()) {
            case RetrieveThing.TYPE, ModifyThing.TYPE ->
                    replaceWildcardForThingBasedCommands(thing, jsonPointerWithWildcard, headerKey);
            case RetrieveFeatures.TYPE, ModifyFeatures.TYPE ->
                    replaceWildcardForFeaturesBasedCommands(thing, jsonPointerWithWildcard, headerKey);
            case RetrieveFeature.TYPE, ModifyFeature.TYPE ->
                    replaceWildcardForFeatureBasedCommands(thing, jsonPointerWithWildcard, headerKey,
                            (RetrieveFeature) command);
            case RetrieveFeatureProperties.TYPE, ModifyFeatureProperties.TYPE ->
                    replaceWildcardForFeaturePropertiesBasedCommands(thing, jsonPointerWithWildcard, headerKey,
                            (RetrieveFeatureProperties) command);
            case RetrieveFeatureDesiredProperties.TYPE, ModifyFeatureDesiredProperties.TYPE ->
                    replaceWildcardForFeatureDesiredPropertiesBasedCommands(thing, jsonPointerWithWildcard, headerKey,
                            (RetrieveFeatureDesiredProperties) command);
            case RetrieveAttributes.TYPE, ModifyAttributes.TYPE ->
                    replaceWildcardForAttributesBasedCommands(thing, jsonPointerWithWildcard, headerKey);
            default -> Set.of();
        };
    }

    private static Set<JsonPointer> replaceWildcardForThingBasedCommands(final Thing thing,
            final JsonPointer jsonPointerWithWildcard, final String headerKey) {
        final String wildcardExpression = jsonPointerWithWildcard.toString();
        final JsonKey leafFromWildcardExpression = jsonPointerWithWildcard.getLeaf().orElseThrow(() ->
                MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression, headerKey)
        );
        final Set<JsonPointer> replacedWildcardsPointers = new HashSet<>();
        final List<String> featureIds = getFeatureIdsFromThing(thing);

        if (MetadataWildcardValidator.matchesThingFeaturesAndPropertiesWildcard(wildcardExpression)) {
            featureIds.forEach(featureId -> {
                        if (containsProperties(jsonPointerWithWildcard)) {
                            final List<String> propertyKeys = getFeaturePropertyKeysFromThing(thing, featureId);
                            addReplacedWildcardPointersForPropertyKeysOnFeaturesLevel(propertyKeys, JsonPointer.of(featureId),
                                    PROPERTIES_POINTER, leafFromWildcardExpression.asPointer(), replacedWildcardsPointers);
                        }
                        if (containsDesiredProperties(jsonPointerWithWildcard)) {
                            final List<String> desiredPropertyKeys =
                                    getFeatureDesiredPropertyKeysFromThing(thing, featureId);
                            addReplacedWildcardPointersForPropertyKeysOnFeaturesLevel(desiredPropertyKeys,
                                    JsonPointer.of(featureId), DESIRED_PROPERTIES_POINTER,
                                    leafFromWildcardExpression.asPointer(), replacedWildcardsPointers);
                        }
                    }
            );
        } else if (MetadataWildcardValidator.matchesThingFeaturesWithIdOnlyWildcard(wildcardExpression)) {
            final JsonKey propertyKeyFromWildcardExpr = jsonPointerWithWildcard.get(3).orElseThrow(() ->
                    MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression, headerKey)
            );
            featureIds.forEach(featureId -> {
                        if (containsProperties(jsonPointerWithWildcard)) {
                            addReplacedWildcardPointersForFeatureIdOnFeaturesLevel(JsonPointer.of(featureId),
                                    PROPERTIES_POINTER, propertyKeyFromWildcardExpr.asPointer(),
                                    leafFromWildcardExpression.asPointer(), replacedWildcardsPointers);
                        }
                        if (containsDesiredProperties(jsonPointerWithWildcard)) {
                            addReplacedWildcardPointersForFeatureIdOnFeaturesLevel(JsonPointer.of(featureId),
                                    DESIRED_PROPERTIES_POINTER, propertyKeyFromWildcardExpr.asPointer(),
                                    leafFromWildcardExpression.asPointer(), replacedWildcardsPointers);
                        }
                    }
            );
        } else if (MetadataWildcardValidator.matchesThingFeaturesWithPropertiesOnlyWildcard(wildcardExpression)) {
            final JsonKey featureIdKeyFromWildcardExpr = jsonPointerWithWildcard.get(1).orElseThrow(() ->
                    MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression, headerKey)
            );
            final String featureId = featureIdKeyFromWildcardExpr.toString();

            if (containsProperties(jsonPointerWithWildcard)) {
                final List<String> propertyKeys = getFeaturePropertyKeysFromThing(thing, featureId);
                addReplacedWildcardPointersForPropertyKeysOnFeaturesLevel(propertyKeys,
                        featureIdKeyFromWildcardExpr.asPointer(),
                        PROPERTIES_POINTER, leafFromWildcardExpression.asPointer(), replacedWildcardsPointers);
            }
            if (containsDesiredProperties(jsonPointerWithWildcard)) {
                final List<String> desiredPropertyKeys =
                        getFeatureDesiredPropertyKeysFromThing(thing, featureId);
                addReplacedWildcardPointersForPropertyKeysOnFeaturesLevel(desiredPropertyKeys,
                        featureIdKeyFromWildcardExpr.asPointer(), DESIRED_PROPERTIES_POINTER,
                        leafFromWildcardExpression.asPointer(), replacedWildcardsPointers);
            }
        } else if (MetadataWildcardValidator.matchesAttributesWildcard(wildcardExpression)) {
            final JsonKey metadataKey = jsonPointerWithWildcard.get(2).orElseThrow(() ->
                    MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression, headerKey)
            );
            final List<String> attributeIds = getAttributesIdsFromThing(thing);
            addReplacedWildcardPointersForAttributeIds(attributeIds, metadataKey.asPointer(),
                    replacedWildcardsPointers);

        } else if (MetadataWildcardValidator.matchesLeafWildcard(wildcardExpression)) {
            final JsonKey metadataKey = jsonPointerWithWildcard.get(1).orElseThrow(() ->
                    MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression, headerKey)
            );

            replacedWildcardsPointers.add(JsonPointer.of("thingId/" + metadataKey));
            replacedWildcardsPointers.add(JsonPointer.of("policyId/" + metadataKey));

            featureIds.forEach(featureId -> {
                        final List<String> propertyLeafs = getFeaturePropertyLeafsFromThing(thing, featureId);
                        addReplacedWildcardPointersForPropertyKeysOnFeaturesLevel(propertyLeafs, JsonPointer.of(featureId),
                                PROPERTIES_POINTER, metadataKey.asPointer(), replacedWildcardsPointers);

                        final List<String> desiredPropertyKeys =
                                getFeatureDesiredPropertyKeysFromThing(thing, featureId);
                        addReplacedWildcardPointersForPropertyKeysOnFeaturesLevel(desiredPropertyKeys,
                                JsonPointer.of(featureId), DESIRED_PROPERTIES_POINTER, metadataKey.asPointer(),
                                replacedWildcardsPointers);
                    }
            );

            final List<String> attributeIds = getAttributesIdsFromThing(thing);
            addReplacedWildcardPointersForAttributeIds(attributeIds, metadataKey.asPointer(),
                    replacedWildcardsPointers);
        }

        return replacedWildcardsPointers;
    }

    private static Set<JsonPointer> replaceWildcardForFeaturesBasedCommands(final Thing thing,
            final JsonPointer jsonPointerWithWildcard, final String headerKey) {
        final Set<JsonPointer> replacedWildcardsPointer = new HashSet<>();
        final List<String> featureIds = getFeatureIdsFromThing(thing);
        final String wildcardExpression = jsonPointerWithWildcard.toString();
        final JsonKey leafFromWildcardExpression = jsonPointerWithWildcard.getLeaf().orElseThrow(() ->
                MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression, headerKey)
        );

        if (MetadataWildcardValidator.matchesFeaturesWildcard(wildcardExpression)) {
            featureIds.forEach(featureId -> {
                        if (containsProperties(jsonPointerWithWildcard)) {
                            final List<String> propertyKeys = getFeaturePropertyKeysFromThing(thing, featureId);
                            addReplacedWildcardPointersForPropertyKeyOnFeatureLevel(propertyKeys, JsonPointer.of(featureId),
                                    PROPERTIES_POINTER, leafFromWildcardExpression.asPointer(), replacedWildcardsPointer);
                        }
                        if (containsDesiredProperties(jsonPointerWithWildcard)) {
                            final List<String> desiredPropertyKeys =
                                    getFeatureDesiredPropertyKeysFromThing(thing, featureId);
                            addReplacedWildcardPointersForPropertyKeyOnFeatureLevel(desiredPropertyKeys,
                                    JsonPointer.of(featureId), DESIRED_PROPERTIES_POINTER,
                                    leafFromWildcardExpression.asPointer(), replacedWildcardsPointer);
                        }
                    }
            );
        } else if (MetadataWildcardValidator.matchesFeaturesWithIdOnlyWildcard(wildcardExpression)) {
            final JsonKey propertyKeyFromWildcardExpr = jsonPointerWithWildcard.get(2).orElseThrow(() ->
                    MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression, headerKey)
            );
            featureIds.forEach(featureId -> {
                if (containsProperties(jsonPointerWithWildcard)) {
                    addReplacedWildcardPointersForFeatureIdOnFeatureLevel(JsonPointer.of(featureId), PROPERTIES_POINTER,
                            propertyKeyFromWildcardExpr.asPointer(), leafFromWildcardExpression.asPointer(),
                            replacedWildcardsPointer);
                }
                if (containsDesiredProperties(jsonPointerWithWildcard)) {
                    addReplacedWildcardPointersForFeatureIdOnFeatureLevel(JsonPointer.of(featureId),
                            DESIRED_PROPERTIES_POINTER, propertyKeyFromWildcardExpr.asPointer(),
                            leafFromWildcardExpression.asPointer(), replacedWildcardsPointer);
                }
            });
        } else if (MetadataWildcardValidator.matchesFeaturesWithPropertyOnlyWildcard(wildcardExpression) ||
                MetadataWildcardValidator.matchesFeaturePropertyWildcard(wildcardExpression)) {
            final JsonKey featureIdFromWildcardExpr = jsonPointerWithWildcard.get(1).orElseThrow(() ->
                    MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression, headerKey)
            );
            final String featureId = featureIdFromWildcardExpr.toString();
            if (containsProperties(jsonPointerWithWildcard)) {
                final List<String> propertyKeys = getFeaturePropertyKeysFromThing(thing, featureId);
                addReplacedWildcardPointersForPropertyKeyOnFeatureLevel(propertyKeys, JsonPointer.of(featureId),
                        PROPERTIES_POINTER, leafFromWildcardExpression.asPointer(), replacedWildcardsPointer);
            }
            if (containsDesiredProperties(jsonPointerWithWildcard)) {
                final List<String> desiredPropertyKeys = getFeatureDesiredPropertyKeysFromThing(thing, featureId);
                addReplacedWildcardPointersForPropertyKeyOnFeatureLevel(desiredPropertyKeys, JsonPointer.of(featureId),
                        DESIRED_PROPERTIES_POINTER, leafFromWildcardExpression.asPointer(), replacedWildcardsPointer);
            }
        }

        return replacedWildcardsPointer;
    }

    private static Set<JsonPointer> replaceWildcardForFeatureBasedCommands(final Thing thing,
            final JsonPointer jsonPointerWithWildcard, final String headerKey, final RetrieveFeature retrieveFeature) {
        final Set<JsonPointer> replacedWildcardsPointer = new HashSet<>();
        final String wildcardExpression = jsonPointerWithWildcard.toString();
        final JsonKey leafFromWildcardExpression = jsonPointerWithWildcard.getLeaf().orElseThrow(() ->
                MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression, headerKey)
        );

        if (MetadataWildcardValidator.matchesFeaturePropertyWildcard(wildcardExpression)) {
            final String featureId = retrieveFeature.getFeatureId();
            final List<String> propertyKeys = getFeaturePropertyKeysFromThing(thing, featureId);
            propertyKeys.forEach(propertyKey -> {
                        if (containsProperties(jsonPointerWithWildcard)) {
                            addReplacedWildcardPointersForPropertyKeysOnPropertyLevel(propertyKeys, PROPERTIES_POINTER,
                                    leafFromWildcardExpression.asPointer(), replacedWildcardsPointer);
                        }
                        if (containsDesiredProperties(jsonPointerWithWildcard)) {
                            final List<String> desiredPropertyKeys =
                                    getFeatureDesiredPropertyKeysFromThing(thing, featureId);
                            addReplacedWildcardPointersForPropertyKeysOnPropertyLevel(desiredPropertyKeys,
                                    DESIRED_PROPERTIES_POINTER, leafFromWildcardExpression.asPointer(),
                                    replacedWildcardsPointer);
                        }
                    }
            );
        }

        return replacedWildcardsPointer;
    }

    private static Set<JsonPointer> replaceWildcardForFeaturePropertiesBasedCommands(final Thing thing,
            final JsonPointer jsonPointerWithWildcard, final String headerKey,
            final RetrieveFeatureProperties command) {
        final String wildcardExpression = jsonPointerWithWildcard.toString();
        final JsonKey leafFromWildcardExpression = jsonPointerWithWildcard.getLeaf().orElseThrow(() ->
                MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression, headerKey)
        );
        final Set<JsonPointer> replacedWildcardsPointer = new HashSet<>();
        final String featureIdFromCommand = command.getFeatureId();
        final List<String> propertyKeys = getFeaturePropertyKeysFromThing(thing, featureIdFromCommand);
        addReplacedWildcardPointersForPropertyKeysOnPropertiesLevel(propertyKeys,
                leafFromWildcardExpression.asPointer(), replacedWildcardsPointer);

        return replacedWildcardsPointer;
    }

    private static Set<JsonPointer> replaceWildcardForFeatureDesiredPropertiesBasedCommands(final Thing thing,
            final JsonPointer jsonPointerWithWildcard, final String headerKey,
            final RetrieveFeatureDesiredProperties command) {
        final String wildcardExpression = jsonPointerWithWildcard.toString();
        final JsonKey leafFromWildcardExpression = jsonPointerWithWildcard.getLeaf().orElseThrow(() ->
                MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression, headerKey)
        );
        final Set<JsonPointer> replacedWildcardsPointer = new HashSet<>();
        final String featureIdFromCommand = command.getFeatureId();
        final List<String> desiredPropertyKeys = getFeatureDesiredPropertyKeysFromThing(thing, featureIdFromCommand);
        addReplacedWildcardPointersForPropertyKeysOnPropertiesLevel(desiredPropertyKeys,
                leafFromWildcardExpression.asPointer(), replacedWildcardsPointer);

        return replacedWildcardsPointer;
    }

    private static Set<JsonPointer> replaceWildcardForAttributesBasedCommands(final Thing thing,
            final JsonPointer jsonPointerWithWildcard, final String headerKey) {
        final String wildcardExpression = jsonPointerWithWildcard.toString();
        final Set<JsonPointer> replacedWildcardsPointer = new HashSet<>();
        final List<String> attributeIds = getAttributesIdsFromThing(thing);

        if (MetadataWildcardValidator.matchesLeafWildcard(wildcardExpression)) {
            final JsonKey attributeKey = jsonPointerWithWildcard.get(1).orElseThrow(() ->
                    MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression, headerKey)
            );
            attributeIds.forEach(attributeId ->
                    replacedWildcardsPointer.add(
                            JsonPointer.empty()
                                    .append(JsonPointer.of(attributeId))
                                    .append(attributeKey.asPointer())
                    )
            );
        }

        return replacedWildcardsPointer;
    }

    private static void addReplacedWildcardPointersForPropertyKeysOnFeaturesLevel(final List<String> propertyKeys,
            final JsonPointer featureId, final JsonPointer propertiesPointer,
            final JsonPointer leafFromWildcardExpression, final Set<JsonPointer> replacedWildcardsPointers) {
        propertyKeys.forEach(propertyKey ->
                addReplacedWildcardPointersForFeatureIdOnFeaturesLevel(featureId, propertiesPointer,
                        JsonPointer.of(propertyKey), leafFromWildcardExpression, replacedWildcardsPointers)
        );
    }

    private static void addReplacedWildcardPointersForFeatureIdOnFeaturesLevel(final JsonPointer featureId,
            final JsonPointer propertiesPointer, final JsonPointer propertyKey,
            final JsonPointer leafFromWildcardExpression,
            final Set<JsonPointer> replacedWildcardsPointers) {
        replacedWildcardsPointers.add(
                JsonPointer.empty()
                        .append(FEATURES_POINTER)
                        .append(JsonPointer.of(featureId))
                        .append(propertiesPointer)
                        .append(propertyKey)
                        .append(leafFromWildcardExpression)
        );
    }

    private static void addReplacedWildcardPointersForPropertyKeyOnFeatureLevel(final List<String> propertyKeys,
            final JsonPointer featureId, final JsonPointer propertiesPointer,
            final JsonPointer leafFromWildcardExpression, final Set<JsonPointer> replacedWildcardsPointers) {
        propertyKeys.forEach(propertyKey ->
                addReplacedWildcardPointersForFeatureIdOnFeatureLevel(featureId, propertiesPointer,
                        JsonPointer.of(propertyKey), leafFromWildcardExpression, replacedWildcardsPointers)
        );
    }

    private static void addReplacedWildcardPointersForFeatureIdOnFeatureLevel(final JsonPointer featureId,
            final JsonPointer propertiesPointer, final JsonPointer propertyKey,
            final JsonPointer leafFromWildcardExpression,
            final Set<JsonPointer> replacedWildcardsPointers) {
        replacedWildcardsPointers.add(
                JsonPointer.empty()
                        .append(JsonPointer.of(featureId))
                        .append(propertiesPointer)
                        .append(propertyKey)
                        .append(leafFromWildcardExpression)
        );
    }

    private static void addReplacedWildcardPointersForPropertyKeysOnPropertyLevel(final List<String> propertyKeys,
            final JsonPointer propertiesPointer, final JsonPointer leafFromWildcardExpression,
            final Set<JsonPointer> replacedWildcardsPointer) {
        propertyKeys.forEach(propertyKey ->
                replacedWildcardsPointer.add(
                        JsonPointer.empty()
                                .append(propertiesPointer)
                                .append(JsonPointer.of(propertyKey))
                                .append(leafFromWildcardExpression)
                )
        );
    }

    private static void addReplacedWildcardPointersForPropertyKeysOnPropertiesLevel(List<String> propertyKeys,
            final JsonPointer leafFromWildcardExpression, final Set<JsonPointer> replacedWildcardsPointer) {
        propertyKeys.forEach(propertyKey ->
                replacedWildcardsPointer.add(
                        JsonPointer.empty()
                                .append(JsonPointer.of(propertyKey))
                                .append(leafFromWildcardExpression)
                )
        );
    }

    private static void addReplacedWildcardPointersForAttributeIds(final List<String> attributeIds,
            final JsonPointer metadataKey, final Set<JsonPointer> replacedWildcardsPointers) {
        attributeIds.forEach(attributeId ->
                replacedWildcardsPointers.add(
                        JsonPointer.empty()
                                .append(JsonPointer.of(ATTRIBUTES))
                                .append(JsonPointer.of(attributeId))
                                .append(metadataKey))
        );
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
        final List<JsonPointer> propertyKeysFromThing = thing.getFeatures()
                .flatMap(features -> features.getFeature(featureId))
                .flatMap(Feature::getProperties)
                .map(JsonObject::getKeys)
                .map(jsonKeys -> jsonKeys.stream().map(JsonPointer::of).toList())
                .orElse(List.of());

        final FeatureProperties featureProperties = thing.getFeatures()
                .flatMap(features -> features.getFeature(featureId))
                .flatMap(Feature::getProperties)
                .orElse(null);

        if (featureProperties != null) {
            return getFeaturePropertyLeafs(propertyKeysFromThing, featureProperties);
        } else {
            return List.of();
        }
    }

    private static List<String> getFeaturePropertyLeafs(List<JsonPointer> propertyKeysFromThing,
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

}
