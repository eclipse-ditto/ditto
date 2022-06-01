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
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttributes;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeature;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDesiredProperties;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureProperties;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatures;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;

@Immutable
final class MetadataFieldsWildcardResolver {

    private static final JsonPointer FEATURES_POINTER = JsonPointer.of("features");
    private static final JsonPointer PROPERTIES_POINTER = JsonPointer.of("properties");

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
            final JsonPointer jsonPointerWithWildcard) {

        return switch (command.getType()) {
            case RetrieveThing.TYPE -> replaceWildcardForRetrieveThing(thing, jsonPointerWithWildcard);
            case RetrieveFeatures.TYPE -> replaceWildcardForRetrieveFeatures(thing, jsonPointerWithWildcard);
            case RetrieveFeature.TYPE ->
                    replaceWildcardForRetrieveFeature(thing, jsonPointerWithWildcard, (RetrieveFeature) command);
            case RetrieveFeatureProperties.TYPE ->
                    replaceWildcardForRetrieveFeatureProperties(thing, jsonPointerWithWildcard,
                            (RetrieveFeatureProperties) command);
            case RetrieveFeatureDesiredProperties.TYPE ->
                    replaceWildcardForRetrieveFeatureDesiredProperties(thing, jsonPointerWithWildcard,
                            (RetrieveFeatureDesiredProperties) command);
            case RetrieveAttributes.TYPE -> replaceWildcardForRetrieveAttributes(thing, jsonPointerWithWildcard);
            default -> Set.of();
        };
    }

    private static Set<JsonPointer> replaceWildcardForRetrieveThing(final Thing thing,
            final JsonPointer jsonPointerWithWildcard) {
        final String wildcardExpression = jsonPointerWithWildcard.toString();
        final JsonKey leafFromWildcardExpression = jsonPointerWithWildcard.getLeaf().orElseThrow(() ->
                MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression)
        );
        final Set<JsonPointer> replacedWildcardsPointers = new HashSet<>();
        final List<String> featureIds = getFeatureIdsFromThing(thing);

        if (MetadataWildcardValidator.matchesThingFeaturesAndPropertiesWildcard(wildcardExpression)) {
            featureIds.forEach(featureId -> {
                        final List<String> propertyKeys = getFeaturePropertyKeysFromThing(thing, featureId);
                        propertyKeys.forEach(propertyKey ->
                                replacedWildcardsPointers.add(
                                        JsonPointer.empty().append(FEATURES_POINTER)
                                                .append(JsonPointer.of(featureId))
                                                .append(PROPERTIES_POINTER)
                                                .append(JsonPointer.of(propertyKey))
                                                .append(leafFromWildcardExpression.asPointer())
                                )
                        );
                    }
            );
        } else if (MetadataWildcardValidator.matchesThingFeaturesWithIdOnlyWildcard(wildcardExpression)) {
            final JsonKey propertyKeysFromWildcardExpr = jsonPointerWithWildcard.get(3).orElseThrow(() ->
                    MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression)
            );
            featureIds.forEach(featureId ->
                    replacedWildcardsPointers.add(
                            JsonPointer.empty()
                                    .append(FEATURES_POINTER)
                                    .append(JsonPointer.of(featureId))
                                    .append(PROPERTIES_POINTER)
                                    .append(propertyKeysFromWildcardExpr.asPointer())
                                    .append(leafFromWildcardExpression.asPointer())
                    )
            );
        } else if (MetadataWildcardValidator.matchesThingFeaturesWithPropertiesOnlyWildcard(wildcardExpression)) {
            final JsonKey featureIdKeyFromWildcardExpr = jsonPointerWithWildcard.get(1).orElseThrow(() ->
                    MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression)
            );
            final List<String> propertyKeys =
                    getFeaturePropertyKeysFromThing(thing, featureIdKeyFromWildcardExpr.toString());
            propertyKeys.forEach(propertyKey ->
                    replacedWildcardsPointers.add(
                            JsonPointer.empty()
                                    .append(FEATURES_POINTER)
                                    .append(featureIdKeyFromWildcardExpr.asPointer())
                                    .append(PROPERTIES_POINTER)
                                    .append(JsonPointer.of(propertyKey))
                                    .append(leafFromWildcardExpression.asPointer()
                                    )
                    )
            );
        } else if (MetadataWildcardValidator.matchesAttributesWildcard(wildcardExpression)) {
            final JsonKey metadataKey = jsonPointerWithWildcard.get(2).orElseThrow(() ->
                    MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression)
            );
            final List<String> attributeIds = getAttributesIdsFromThing(thing);
            attributeIds.forEach(attributeId ->
                    replacedWildcardsPointers.add(
                            JsonPointer.empty()
                                    .append(JsonPointer.of("attributes"))
                                    .append(JsonPointer.of(attributeId))
                                    .append(metadataKey.asPointer())
                    )
            );
        } else if (MetadataWildcardValidator.matchesLeafWildcard(wildcardExpression)) {
            final JsonKey metadataKey = jsonPointerWithWildcard.get(1).orElseThrow(() ->
                    MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression)
            );

            replacedWildcardsPointers.add(JsonPointer.of("thingId/" + metadataKey));
            replacedWildcardsPointers.add(JsonPointer.of("policyId/" + metadataKey));

            featureIds.forEach(featureId -> {
                        final List<String> propertyLeafs = getFeaturePropertyLeafsFromThing(thing, featureId);
                        propertyLeafs.forEach(propertyKey ->
                                replacedWildcardsPointers.add(
                                        JsonPointer.empty().append(FEATURES_POINTER)
                                                .append(JsonPointer.of(featureId))
                                                .append(PROPERTIES_POINTER)
                                                .append(JsonPointer.of(propertyKey))
                                                .append(metadataKey.asPointer())
                                )
                        );
                    }
            );

            final List<String> attributeIds = getAttributesIdsFromThing(thing);
            attributeIds.forEach(attributeId ->
                    replacedWildcardsPointers.add(
                            JsonPointer.empty()
                                    .append(JsonPointer.of("attributes"))
                                    .append(JsonPointer.of(attributeId))
                                    .append(metadataKey.asPointer())
                    )
            );
        }

        return replacedWildcardsPointers;
    }

    private static Set<JsonPointer> replaceWildcardForRetrieveFeatures(final Thing thing,
            final JsonPointer jsonPointerWithWildcard) {
        final Set<JsonPointer> replacedWildcardsPointer = new HashSet<>();
        final List<String> featureIds = getFeatureIdsFromThing(thing);
        final String wildcardExpression = jsonPointerWithWildcard.toString();
        final JsonKey leafFromWildcardExpression = jsonPointerWithWildcard.getLeaf().orElseThrow(() ->
                MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression)
        );

        if (MetadataWildcardValidator.matchesFeaturesWildcard(wildcardExpression)) {
            featureIds.forEach(featureId -> {
                        final List<String> propertyKeys = getFeaturePropertyKeysFromThing(thing, featureId);
                        propertyKeys.forEach(propertyKey ->
                                replacedWildcardsPointer.add(
                                        JsonPointer.empty()
                                                .append(JsonPointer.of(featureId))
                                                .append(PROPERTIES_POINTER)
                                                .append(JsonPointer.of(propertyKey))
                                                .append(leafFromWildcardExpression.asPointer())
                                )
                        );
                    }
            );
        } else if (MetadataWildcardValidator.matchesFeaturesWithIdOnlyWildcard(wildcardExpression)) {
            final JsonKey propertyKeyFromWildcardExpr = jsonPointerWithWildcard.get(2).orElseThrow(() ->
                    MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression)
            );
            featureIds.forEach(featureId ->
                    replacedWildcardsPointer.add(
                            JsonPointer.empty()
                                    .append(JsonPointer.of(featureId))
                                    .append(PROPERTIES_POINTER)
                                    .append(propertyKeyFromWildcardExpr.asPointer())
                                    .append(leafFromWildcardExpression.asPointer())
                    )
            );
        } else if (MetadataWildcardValidator.matchesFeaturesWithPropertyOnlyWildcard(wildcardExpression) ||
                MetadataWildcardValidator.matchesFeaturePropertyWildcard(wildcardExpression)) {
            final JsonKey featureIdFromWildcardExpr = jsonPointerWithWildcard.get(1).orElseThrow(() ->
                    MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression)
            );
            final List<String> propertyKeys =
                    getFeaturePropertyKeysFromThing(thing, featureIdFromWildcardExpr.toString());
            propertyKeys.forEach(propertyKey ->
                    replacedWildcardsPointer.add(
                            JsonPointer.empty()
                                    .append(featureIdFromWildcardExpr.asPointer())
                                    .append(PROPERTIES_POINTER)
                                    .append(JsonPointer.of(propertyKey))
                                    .append(leafFromWildcardExpression.asPointer())
                    )
            );
        }

        return replacedWildcardsPointer;
    }

    private static Set<JsonPointer> replaceWildcardForRetrieveFeature(final Thing thing,
            final JsonPointer jsonPointerWithWildcard, final RetrieveFeature retrieveFeature) {
        final Set<JsonPointer> replacedWildcardsPointer = new HashSet<>();
        final String wildcardExpression = jsonPointerWithWildcard.toString();
        final JsonKey leafFromWildcardExpression = jsonPointerWithWildcard.getLeaf().orElseThrow(() ->
                MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression)
        );

        if (MetadataWildcardValidator.matchesFeaturePropertyWildcard(wildcardExpression)) {
            final String featureIdFromCommand = retrieveFeature.getFeatureId();
            final List<String> propertyKeys = getFeaturePropertyKeysFromThing(thing, featureIdFromCommand);
            propertyKeys.forEach(propertyKey ->
                    replacedWildcardsPointer.add(
                            JsonPointer.empty()
                                    .append(PROPERTIES_POINTER)
                                    .append(JsonPointer.of(propertyKey))
                                    .append(leafFromWildcardExpression.asPointer())
                    )
            );
        }

        return replacedWildcardsPointer;
    }

    private static Set<JsonPointer> replaceWildcardForRetrieveFeatureProperties(final Thing thing,
            final JsonPointer jsonPointerWithWildcard, final RetrieveFeatureProperties command) {
        final String wildcardExpression = jsonPointerWithWildcard.toString();
        final JsonKey leafFromWildcardExpression = jsonPointerWithWildcard.getLeaf().orElseThrow(() ->
                MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression)
        );
        final Set<JsonPointer> replacedWildcardsPointer = new HashSet<>();
        final String featureIdFromCommand = command.getFeatureId();
        final List<String> propertyKeys = getFeaturePropertyKeysFromThing(thing, featureIdFromCommand);
        propertyKeys.forEach(propertyKey ->
                replacedWildcardsPointer.add(
                        JsonPointer.empty()
                                .append(JsonPointer.of(propertyKey))
                                .append(leafFromWildcardExpression.asPointer())
                )
        );

        return replacedWildcardsPointer;
    }

    private static Set<JsonPointer> replaceWildcardForRetrieveFeatureDesiredProperties(final Thing thing,
            final JsonPointer jsonPointerWithWildcard, final RetrieveFeatureDesiredProperties command) {
        final String wildcardExpression = jsonPointerWithWildcard.toString();
        final JsonKey leafFromWildcardExpression = jsonPointerWithWildcard.getLeaf().orElseThrow(() ->
                MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression)
        );
        final Set<JsonPointer> replacedWildcardsPointer = new HashSet<>();
        final String featureIdFromCommand = command.getFeatureId();
        final List<String> propertyKeys = getFeatureDesiredPropertyKeysFromThing(thing, featureIdFromCommand);
        propertyKeys.forEach(propertyKey ->
                replacedWildcardsPointer.add(
                        JsonPointer.empty()
                                .append(JsonPointer.of(propertyKey))
                                .append(leafFromWildcardExpression.asPointer())
                )
        );

        return replacedWildcardsPointer;
    }

    private static Set<JsonPointer> replaceWildcardForRetrieveAttributes(final Thing thing,
            final JsonPointer jsonPointerWithWildcard) {
        final String wildcardExpression = jsonPointerWithWildcard.toString();
        final Set<JsonPointer> replacedWildcardsPointer = new HashSet<>();
        final List<String> attributeIds = getAttributesIdsFromThing(thing);

        if (MetadataWildcardValidator.matchesLeafWildcard(wildcardExpression)) {
            final JsonKey attributeKey = jsonPointerWithWildcard.get(1).orElseThrow(() ->
                    MetadataWildcardValidator.getDittoHeaderInvalidException(wildcardExpression)
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
