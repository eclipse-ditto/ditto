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
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttributes;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeature;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDesiredProperties;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureProperties;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatures;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;

/**
 * Validates thw wildcard expression in the {@code get-metadata} header.
 * If the expression is invalid, a {@link DittoHeaderInvalidException} is thrown.
 */
@Immutable
final class MetadataWildcardValidator {

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
    private static final String ATTRIBUTES_WILDCARD_REGEX = "/?attributes/\\*/(?!\\*).*";
    private static final String LEAF_WILDCARD_REGEX = "^/?\\*/(?!\\*/).*";

    private MetadataWildcardValidator() {
        throw new AssertionError();
    }

    /**
     * Checks if the metaDataWildcardExpression for the command type is valid.
     *
     * @param commandType teh commandType to validate.
     * @param metaDataWildcardExpression the wildcard expression used in the get-metadata header.
     * @throws DittoHeaderInvalidException if {@code metaDataWildcardExpression} is not valid for the commandType.
     */
    public static void validateMetadataWildcard(final String commandType, final String metaDataWildcardExpression) {

        switch (commandType) {
            case RetrieveThing.TYPE -> {
                if (!Pattern.matches(THING_FEATURES_AND_PROPERTIES_WILDCARD_REGEX, metaDataWildcardExpression)
                        &&
                        !Pattern.matches(THING_FEATURES_WITH_PROPERTIES_ONLY_WILDCARD_REGEX, metaDataWildcardExpression)
                        && !Pattern.matches(THING_FEATURES_WITH_ID_ONLY_WILDCARD_REGEX, metaDataWildcardExpression)
                        && !Pattern.matches(ATTRIBUTES_WILDCARD_REGEX, metaDataWildcardExpression)
                        && !Pattern.matches(LEAF_WILDCARD_REGEX, metaDataWildcardExpression)) {
                    throw getDittoHeaderInvalidException(metaDataWildcardExpression);
                }
            }
            case RetrieveFeatures.TYPE -> {
                if (!Pattern.matches(FEATURES_WILDCARD_REGEX, metaDataWildcardExpression)
                        && !Pattern.matches(FEATURES_WITH_PROPERTIES_ONLY_WILDCARD_REGEX, metaDataWildcardExpression)
                        && !Pattern.matches(FEATURES_WITH_ID_ONLY_WILDCARD_REGEX, metaDataWildcardExpression)) {
                    throw getDittoHeaderInvalidException(metaDataWildcardExpression);
                }
            }
            case RetrieveFeature.TYPE -> {
                if (!Pattern.matches(FEATURE_PROPERTY_WILDCARD_REGEX, metaDataWildcardExpression)
                        && !Pattern.matches(FEATURES_WITH_ID_ONLY_WILDCARD_REGEX, metaDataWildcardExpression)
                        && !Pattern.matches(FEATURES_WITH_PROPERTIES_ONLY_WILDCARD_REGEX, metaDataWildcardExpression)) {
                    throw getDittoHeaderInvalidException(metaDataWildcardExpression);
                }
            }
            case RetrieveFeatureDesiredProperties.TYPE, RetrieveFeatureProperties.TYPE, RetrieveAttributes.TYPE -> {
                if (!Pattern.matches(LEAF_WILDCARD_REGEX, metaDataWildcardExpression)) {
                    throw getDittoHeaderInvalidException(metaDataWildcardExpression);
                }
            }
            default -> throw getDittoHeaderNotSupportedException(metaDataWildcardExpression);
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
     * @return the DittoHeaderInvalidException.
     */
    public static DittoHeaderInvalidException getDittoHeaderInvalidException(final String metaDataWildcardExpression) {
        return DittoHeaderInvalidException.newBuilder()
                .withInvalidHeaderKey(DittoHeaderDefinition.GET_METADATA.getKey())
                .message(MessageFormat.format(
                        "The wildcard expression ''{0}'' in header 'get-metadata' is not valid.",
                        metaDataWildcardExpression))
                .description("Verify that the value of the 'get-metadata' header is valid and try again.")
                .build();
    }

    /**
     * Returns an instance of {@code DittoHeaderNotSupportedException}.
     *
     * @param metaDataWildcardExpression the metadata wildcard expression.
     * @return the DittoHeaderNotSupportedException.
     */
    public static DittoHeaderNotSupportedException getDittoHeaderNotSupportedException(
            final String metaDataWildcardExpression) {
        return DittoHeaderNotSupportedException.newBuilder()
                .withNotSupportedHeaderKey(DittoHeaderDefinition.GET_METADATA.getKey())
                .message(MessageFormat.format(
                        "The wildcard expression ''{0}'' in header 'get-metadata' is not supported on this resource level.",
                        metaDataWildcardExpression))
                .description(
                        "Verify that the resource level and the value of the 'get-metadata' header is valid and try again.")
                .build();
    }

}
