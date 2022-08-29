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

import java.text.MessageFormat;
import java.util.regex.Pattern;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.base.model.exceptions.DittoHeaderNotSupportedException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.Thing;

/**
 * Validates the wildcard expression in the {@code get-metadata} or {@code delete-metadata} header.
 * If the expression is invalid, a {@link DittoHeaderInvalidException} is thrown.
 */
@Immutable
final class MetadataWildcardValidator {

    public static final JsonPointer ROOT_PATH = JsonPointer.of("/");
    public static final JsonPointer FEATURES_PATH = Thing.JsonFields.FEATURES.getPointer();
    public static final String FEATURE_PATH = FEATURES_PATH + "/.*";

    private static final String THING_FEATURES_AND_PROPERTIES_WILDCARD_REGEX =
            "/?features/\\*/(properties|desiredProperties)/\\*/(?!\\*).*";
    private static final String THING_FEATURES_WITH_ID_ONLY_WILDCARD_REGEX =
            "/?features/\\*/(properties|desiredProperties)/(?!\\*).*/(?!\\*).*";
    private static final String THING_FEATURES_WITH_PROPERTIES_ONLY_WILDCARD_REGEX =
            "/?features/(?!\\*).*/(properties|desiredProperties)/\\*/(?!\\*/).*";
    private static final String FEATURES_WILDCARD_REGEX = "/?\\*/(properties|desiredProperties)/\\*/(?!\\*).*";
    private static final String FEATURES_WITH_ID_ONLY_WILDCARD_REGEX =
            "/?\\*/(properties|desiredProperties)/(?!\\*).*/(?!\\*).*";
    private static final String FEATURES_WITH_PROPERTIES_ONLY_WILDCARD_REGEX =
            "/?(?!\\*).*/(properties|desiredProperties)/\\*/(?!\\*).*";
    private static final String FEATURE_PROPERTY_WILDCARD_REGEX = "/?(properties|desiredProperties)/\\*/(?!\\*).*";

    private static final String THING_FEATURES_DEFINITION_WILDCARD_REGEX = "/?features/\\*/definition/(?!\\*).*";
    private static final String FEATURES_DEFINITION_WILDCARD_REGEX = "/?\\*/definition/(?!\\*).*";
    private static final String ATTRIBUTES_WILDCARD_REGEX = "/?attributes/\\*/(?!\\*).*";
    private static final String LEAF_WILDCARD_REGEX = "^/?\\*/(?!\\*/).*";

    private MetadataWildcardValidator() {
        throw new AssertionError();
    }

    /**
     * Checks if the {@code metaDataWildcardExpression} for the command type is valid.
     *
     * @param resourcePath the resourcePath to validate against.
     * @param metaDataWildcardExpression the wildcard expression.
     * @param headerKey the header key.
     * @throws DittoHeaderInvalidException if {@code metaDataWildcardExpression} is not valid for the commandType.
     */
    public static void validateMetadataWildcard(final JsonPointer resourcePath, final String metaDataWildcardExpression,
            final String headerKey) {
        final int levelCount = resourcePath.getLevelCount();
        final String resourcePathAsString = resourcePath.toString();

        if (resourcePath.equals(ROOT_PATH)) {
            validateWildcardExpressionOnRootLevel(metaDataWildcardExpression, headerKey);
        } else if (resourcePath.equals(FEATURES_PATH)) {
            validateWildcardExpressionOnFeaturesLevel(metaDataWildcardExpression, headerKey);
        } else if (levelCount == 2 && resourcePathAsString.matches(FEATURE_PATH)) {
            validateWildcardExpressionOnFeatureLevel(metaDataWildcardExpression, headerKey);
        } else {
            throw getDittoHeaderNotSupportedException(metaDataWildcardExpression, headerKey);
        }
    }

    private static void validateWildcardExpressionOnRootLevel(final String metaDataWildcardExpression,
            final String headerKey) {
        if (!Pattern.matches(THING_FEATURES_AND_PROPERTIES_WILDCARD_REGEX, metaDataWildcardExpression)
                && !Pattern.matches(THING_FEATURES_WITH_PROPERTIES_ONLY_WILDCARD_REGEX, metaDataWildcardExpression)
                && !Pattern.matches(THING_FEATURES_WITH_ID_ONLY_WILDCARD_REGEX, metaDataWildcardExpression)
                && !Pattern.matches(THING_FEATURES_DEFINITION_WILDCARD_REGEX, metaDataWildcardExpression)
                && !Pattern.matches(ATTRIBUTES_WILDCARD_REGEX, metaDataWildcardExpression)
                && !Pattern.matches(LEAF_WILDCARD_REGEX, metaDataWildcardExpression)) {
            throw getDittoHeaderInvalidException(metaDataWildcardExpression, headerKey);
        }
    }

    private static void validateWildcardExpressionOnFeaturesLevel(final String metaDataWildcardExpression,
            final String headerKey) {
        if (!Pattern.matches(FEATURES_WILDCARD_REGEX, metaDataWildcardExpression)
                && !Pattern.matches(FEATURES_WITH_PROPERTIES_ONLY_WILDCARD_REGEX, metaDataWildcardExpression)
                && !Pattern.matches(FEATURES_WITH_ID_ONLY_WILDCARD_REGEX, metaDataWildcardExpression)
                && !Pattern.matches(FEATURES_DEFINITION_WILDCARD_REGEX, metaDataWildcardExpression)) {
            throw getDittoHeaderInvalidException(metaDataWildcardExpression, headerKey);
        }
    }

    private static void validateWildcardExpressionOnFeatureLevel(final String metaDataWildcardExpression,
            final String headerKey) {
        if (!Pattern.matches(FEATURE_PROPERTY_WILDCARD_REGEX, metaDataWildcardExpression)
                && !Pattern.matches(FEATURES_WITH_ID_ONLY_WILDCARD_REGEX, metaDataWildcardExpression)
                && !Pattern.matches(FEATURES_WITH_PROPERTIES_ONLY_WILDCARD_REGEX, metaDataWildcardExpression)) {
            throw getDittoHeaderInvalidException(metaDataWildcardExpression, headerKey);
        }
    }

    /**
     * Matches the passed {@code wildcardExpression} against wildcard regex
     * {@code THING_FEATURES_AND_PROPERTIES_WILDCARD_REGEX} and returns {@code true} if they match.
     *
     * @param wildcardExpression the wildcard expression that should be checked.
     * @return {@code true} if the wildcardExpression matches the regex and false if not.
     */
    public static boolean matchesThingFeaturesAndPropertiesWildcard(final String wildcardExpression) {
        return Pattern.matches(THING_FEATURES_AND_PROPERTIES_WILDCARD_REGEX, wildcardExpression);
    }

    /**
     * Matches the passed {@code wildcardExpression} against wildcard regex
     * {@code THING_FEATURES_WITH_ID_ONLY_WILDCARD_REGEX} and returns {@code true} if they match.
     *
     * @param wildcardExpression the wildcard expression that should be checked.
     * @return {@code true} if the wildcardExpression matches the regex and false if not.
     */
    public static boolean matchesThingFeaturesWithIdOnlyWildcard(final String wildcardExpression) {
        return Pattern.matches(THING_FEATURES_WITH_ID_ONLY_WILDCARD_REGEX, wildcardExpression);
    }

    /**
     * Matches the passed {@code wildcardExpression} against wildcard regex
     * {@code THING_FEATURES_WITH_PROPERTIES_ONLY_WILDCARD_REGEX} and returns {@code true} if they match.
     *
     * @param wildcardExpression the wildcard expression that should be checked.
     * @return {@code true} if the wildcardExpression matches the regex and false if not.
     */
    public static boolean matchesThingFeaturesWithPropertiesOnlyWildcard(final String wildcardExpression) {
        return Pattern.matches(THING_FEATURES_WITH_PROPERTIES_ONLY_WILDCARD_REGEX, wildcardExpression);
    }

    /**
     * Matches the passed {@code wildcardExpression} against wildcard regex
     * {@code THING_FEATURES_DEFINITION_WILDCARD_REGEX} and returns {@code true} if they match.
     *
     * @param wildcardExpression the wildcard expression that should be checked.
     * @return {@code true} if the wildcardExpression matches the regex and false if not.
     */
    public static boolean matchesThingFeaturesDefinitionWildcard(final String wildcardExpression) {
        return Pattern.matches(THING_FEATURES_DEFINITION_WILDCARD_REGEX, wildcardExpression);
    }

    /**
     * Matches the passed {@code wildcardExpression} against wildcard regex {@code FEATURES_WILDCARD_REGEX}
     * and returns {@code true} if they match.
     *
     * @param wildcardExpression the wildcard expression that should be checked.
     * @return {@code true} if the wildcardExpression matches the regex and false if not.
     */
    public static boolean matchesFeaturesWildcard(final String wildcardExpression) {
        return Pattern.matches(FEATURES_WILDCARD_REGEX, wildcardExpression);
    }

    /**
     * Matches the passed {@code wildcardExpression} against wildcard regex {@code FEATURES_WITH_ID_ONLY_WILDCARD_REGEX}
     * and returns {@code true} if they match.
     *
     * @param wildcardExpression the wildcard expression that should be checked.
     * @return {@code true} if the wildcardExpression matches the regex and false if not.
     */
    public static boolean matchesFeaturesWithIdOnlyWildcard(final String wildcardExpression) {
        return Pattern.matches(FEATURES_WITH_ID_ONLY_WILDCARD_REGEX, wildcardExpression);
    }

    /**
     * Matches the passed {@code wildcardExpression} against wildcard regex
     * {@code FEATURES_WITH_PROPERTIES_ONLY_WILDCARD_REGEX} and returns {@code true} if they match.
     *
     * @param wildcardExpression the wildcard expression that should be checked.
     * @return {@code true} if the wildcardExpression matches the regex and false if not.
     */
    public static boolean matchesFeaturesWithPropertyOnlyWildcard(final String wildcardExpression) {
        return Pattern.matches(FEATURES_WITH_PROPERTIES_ONLY_WILDCARD_REGEX, wildcardExpression);
    }

    /**
     * Matches the passed {@code wildcardExpression} against wildcard regex {@code FEATURE_PROPERTY_WILDCARD_REGEX}
     * and returns {@code true} if they match.
     *
     * @param wildcardExpression the wildcard expression that should be checked.
     * @return {@code true} if the wildcardExpression matches the regex and false if not.
     */
    public static boolean matchesFeaturePropertyWildcard(final String wildcardExpression) {
        return Pattern.matches(FEATURE_PROPERTY_WILDCARD_REGEX, wildcardExpression);
    }

    /**
     * Matches the passed {@code wildcardExpression} against wildcard regex {@code ATTRIBUTES_WILDCARD_REGEX}
     * and returns {@code true} if they match.
     *
     * @param wildcardExpression the wildcard expression that should be checked.
     * @return {@code true} if the wildcardExpression matches the regex and false if not.
     */
    public static boolean matchesAttributesWildcard(final String wildcardExpression) {
        return Pattern.matches(ATTRIBUTES_WILDCARD_REGEX, wildcardExpression);
    }

    /**
     * Matches the passed {@code wildcardExpression} against wildcard regex {@code LEAF_WILDCARD_REGEX}
     * and returns {@code true} if they match.
     *
     * @param wildcardExpression the wildcard expression that should be checked.
     * @return {@code true} if the wildcardExpression matches the regex and false if not.
     */
    public static boolean matchesLeafWildcard(final String wildcardExpression) {
        return Pattern.matches(LEAF_WILDCARD_REGEX, wildcardExpression);
    }

    /**
     * Returns an instance of {@code DittoHeaderInvalidException}.
     *
     * @param metaDataWildcardExpression the metadata wildcard expression.
     * @param headerKey the key of the header which is invalid.
     * @return the DittoHeaderInvalidException.
     */
    public static DittoHeaderInvalidException getDittoHeaderInvalidException(final String metaDataWildcardExpression,
            final String headerKey) {
        return DittoHeaderInvalidException.newBuilder()
                .withInvalidHeaderKey(headerKey)
                .message(MessageFormat.format(
                        "The wildcard expression ''{0}'' in header ''{1}'' is not valid.",
                        metaDataWildcardExpression, headerKey))
                .description(MessageFormat.format("Verify that the value of the ''{0}'' header is valid and try again.",
                        headerKey))
                .build();
    }

    /**
     * Returns an instance of {@code DittoHeaderNotSupportedException}.
     *
     * @param metaDataWildcardExpression the metadata wildcard expression.
     * @param headerKey the key of the header which is not supported.
     * @return the DittoHeaderNotSupportedException.
     */
    public static DittoHeaderNotSupportedException getDittoHeaderNotSupportedException(
            final String metaDataWildcardExpression, final String headerKey) {
        return DittoHeaderNotSupportedException.newBuilder()
                .withNotSupportedHeaderKey(headerKey)
                .message(MessageFormat.format(
                        "The wildcard expression ''{0}'' in header ''{1}'' is not supported on this resource level.",
                        metaDataWildcardExpression, headerKey))
                .description(MessageFormat.format(
                        "Verify that the resource level and the value of the ''{0}'' header is valid and try again.",
                        headerKey))
                .build();
    }

}
