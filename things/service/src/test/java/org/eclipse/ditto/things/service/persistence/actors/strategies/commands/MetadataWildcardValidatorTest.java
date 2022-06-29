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

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.text.MessageFormat;
import java.util.List;

import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.base.model.exceptions.DittoHeaderNotSupportedException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;
import org.junit.Test;

/**
 * Unit test for {@link MetadataWildcardValidator}.
 */
public class MetadataWildcardValidatorTest {

    private static final String GET_METADATA_HEADER_KEY = DittoHeaderDefinition.GET_METADATA.getKey();
    private static final JsonPointer ROOT_POINTER = JsonPointer.of("/");
    private static final JsonPointer FEATURES_POINTER = JsonPointer.of("/features");
    private static final JsonPointer FEATURE_ID_POINTER = JsonPointer.of("/features/featureId");
    private static final JsonPointer ATTRIBUTES_POINTER = JsonPointer.of("/attributes");

    @Test
    public void assertImmutability() {
        assertInstancesOf(MetadataWildcardValidator.class, areImmutable());
    }

    @Test
    public void validateValidMetadataWildcardOnThingLevel() {
        final List<String> metadataWildcardExprList =
                List.of("features/*/properties/*/metadataKey", "features/*/properties/humidity/metadataKey",
                        "features/lamp/properties/*/metadataKey", "/attributes/*/metadataKey", "*/yeah");

        metadataWildcardExprList.forEach(wildcardExpr ->
                assertThatNoException()
                        .isThrownBy(() -> MetadataWildcardValidator.validateMetadataWildcard(ROOT_POINTER, wildcardExpr,
                                GET_METADATA_HEADER_KEY))
        );
    }

    @Test
    public void validateValidMetadataWildcardOnFeaturesLevel() {
        final List<String> metadataWildcardExprList = List.of("/*/properties/*/metadataKey",
                "/*/properties/humidity*/metadataKey", "/f1/properties/*/metadataKey",
                "/f*1*/properties/*/metadataKey");

        metadataWildcardExprList.forEach(wildcardExpr ->
                assertThatNoException()
                        .isThrownBy(() -> MetadataWildcardValidator.validateMetadataWildcard(FEATURES_POINTER,
                                wildcardExpr, GET_METADATA_HEADER_KEY))
        );
    }

    @Test
    public void validateValidMetadataWildcardOnFeatureLevel() {
        final String metadataWildcardExpr = "properties/*/metadataKey";

        assertThatNoException()
                .isThrownBy(() -> MetadataWildcardValidator.validateMetadataWildcard(FEATURE_ID_POINTER,
                        metadataWildcardExpr, GET_METADATA_HEADER_KEY));
    }

    @Test
    public void validateValidMetadataWildcardOnDesiredFeatureLevel() {
        final String metadataWildcardExpr = "desiredProperties/*/metadataKey";

        assertThatNoException()
                .isThrownBy(() -> MetadataWildcardValidator.validateMetadataWildcard(FEATURE_ID_POINTER,
                        metadataWildcardExpr, GET_METADATA_HEADER_KEY));
    }

    @Test
    public void validateValidMetadataWildcardOnFeatureDesiredPropertiesLevel() {
        final List<String> metadataWildcardExprList = List.of("/*/metadataKey", "*/metaDataKey");

        metadataWildcardExprList.forEach(metadataWildcardExpr ->
                assertThatExceptionOfType(DittoHeaderNotSupportedException.class)
                        .isThrownBy(() -> MetadataWildcardValidator.validateMetadataWildcard(
                                FEATURE_ID_POINTER.addLeaf(JsonKey.of("desiredProperties")),
                                metadataWildcardExpr, GET_METADATA_HEADER_KEY))
                        .withMessage(MessageFormat.format(
                                "The wildcard expression ''{0}'' in header ''{1}'' is not supported on this resource level.",
                                metadataWildcardExpr, GET_METADATA_HEADER_KEY))
                        .withNoCause()
        );
    }

    @Test
    public void validateInValidMetadataWildcardOnFeaturePropertiesLevel() {
        final List<String> metadataWildcardExprList = List.of("/*/metadataKey", "*/metaDataKey");

        metadataWildcardExprList.forEach(wildcardExpr ->
                assertThatExceptionOfType(DittoHeaderNotSupportedException.class)
                        .isThrownBy(() -> MetadataWildcardValidator.validateMetadataWildcard(
                                FEATURE_ID_POINTER.addLeaf(JsonKey.of("properties")),
                                wildcardExpr, GET_METADATA_HEADER_KEY))
                        .withMessage(MessageFormat.format(
                                "The wildcard expression ''{0}'' in header ''{1}'' is not supported on this resource level.",
                                wildcardExpr, GET_METADATA_HEADER_KEY))
                        .withNoCause()
        );
    }

    @Test
    public void validateInvalidMetadataWildcardOnThingLevel() {
        final List<String> metadataWildcardExprList =
                List.of("features/*/properties/*/*/metadataKey", "features/*/*/properties/*/metadataKey",
                        "attributes/*/*/metadataKey",
                        "*/*/*/yeah");

        metadataWildcardExprList.forEach(wildcardExpr ->
                assertThatExceptionOfType(DittoHeaderInvalidException.class)
                        .isThrownBy(() -> MetadataWildcardValidator.validateMetadataWildcard(ROOT_POINTER,
                                wildcardExpr, GET_METADATA_HEADER_KEY))
                        .withMessage(MessageFormat.format(
                                "The wildcard expression ''{0}'' in header ''{1}'' is not valid.",
                                wildcardExpr, GET_METADATA_HEADER_KEY))
                        .withNoCause()
        );
    }

    @Test
    public void validateInvalidMetadataWildcardOnFeaturesLevel() {
        final List<String> metadataWildcardExprList =
                List.of("/*/properties/*/*/metadataKey", "/*/*/properties/*/*/metadataKey");

        metadataWildcardExprList.forEach(wildcardExpr ->
                assertThatExceptionOfType(DittoHeaderInvalidException.class)
                        .isThrownBy(() -> MetadataWildcardValidator.validateMetadataWildcard(FEATURES_POINTER,
                                wildcardExpr, GET_METADATA_HEADER_KEY))
                        .withMessage(MessageFormat.format(
                                "The wildcard expression ''{0}'' in header ''{1}'' is not valid.",
                                wildcardExpr, GET_METADATA_HEADER_KEY))
                        .withNoCause()
        );
    }

    @Test
    public void validateInvalidMetadataWildcardOnFeatureLevel() {
        final String metadataWildcardExpr = "/properties/*/*/metadataKey";

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> MetadataWildcardValidator.validateMetadataWildcard(FEATURES_POINTER,
                        metadataWildcardExpr, GET_METADATA_HEADER_KEY))
                .withMessage(MessageFormat.format(
                        "The wildcard expression ''{0}'' in header ''{1}'' is not valid.",
                        metadataWildcardExpr, GET_METADATA_HEADER_KEY))
                .withNoCause();
    }

    @Test
    public void validateInvalidMetadataWildcardOnDesiredFeatureLevel() {
        final String metadataWildcardExpr = "/desiredProperties/*/*/metadataKey";

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> MetadataWildcardValidator.validateMetadataWildcard(FEATURE_ID_POINTER,
                        metadataWildcardExpr, GET_METADATA_HEADER_KEY))
                .withMessage(MessageFormat.format(
                        "The wildcard expression ''{0}'' in header ''{1}'' is not valid.",
                        metadataWildcardExpr, GET_METADATA_HEADER_KEY))
                .withNoCause();
    }

    @Test
    public void validateInvalidMetadataWildcardOnFeaturePropertiesLevel() {
        final List<String> metadataWildcardExprList =
                List.of("/properties/*/*/metadataKey", "/*/*/*/metadataKey", "/*");

        metadataWildcardExprList.forEach(metadataWildcardExpr ->
                assertThatExceptionOfType(DittoHeaderNotSupportedException.class)
                        .isThrownBy(() -> MetadataWildcardValidator.validateMetadataWildcard(
                                FEATURE_ID_POINTER.addLeaf(JsonKey.of("properties")),
                                metadataWildcardExpr, GET_METADATA_HEADER_KEY))
                        .withMessage(MessageFormat.format(
                                "The wildcard expression ''{0}'' in header ''{1}'' is not supported on this resource level.",
                                metadataWildcardExpr, GET_METADATA_HEADER_KEY))
                        .withNoCause()
        );
    }

    @Test
    public void validateInvalidMetadataWildcardOnAttributesLevel() {
        final String metadataWildcardExpr = "attributes/*/*/metadataKey";

        assertThatExceptionOfType(DittoHeaderNotSupportedException.class)
                .isThrownBy(() -> MetadataWildcardValidator.validateMetadataWildcard(ATTRIBUTES_POINTER,
                        metadataWildcardExpr, GET_METADATA_HEADER_KEY))
                .withMessage(MessageFormat.format(
                        "The wildcard expression ''{0}'' in header ''{1}'' is not supported on this resource level.",
                        metadataWildcardExpr, GET_METADATA_HEADER_KEY))
                .withNoCause();
    }

    @Test
    public void validateInvalidMetadataWildcardOnAttributeLevel() {
        final List<String> metadataWildcardExprList =
                List.of("*/*/metadataKey", "/*/*/metadataKey", "*startingWith*/metadataKey", "*/");

        metadataWildcardExprList.forEach(wildcardExpr ->
                assertThatExceptionOfType(DittoHeaderNotSupportedException.class)
                        .isThrownBy(() -> MetadataWildcardValidator.validateMetadataWildcard(
                                ATTRIBUTES_POINTER.addLeaf(JsonKey.of("attribute1")),
                                wildcardExpr, GET_METADATA_HEADER_KEY))
                        .withMessage(MessageFormat.format(
                                "The wildcard expression ''{0}'' in header ''{1}'' is not supported on this resource level.",
                                wildcardExpr, GET_METADATA_HEADER_KEY))
                        .withNoCause()
        );
    }

    @Test
    public void validateInvalidMetadataWildcardOnFeaturePropertyLevel() {
        final String metadataWildcardExpr = "/*/metadataKey";

        assertThatExceptionOfType(DittoHeaderNotSupportedException.class)
                .isThrownBy(() -> MetadataWildcardValidator.validateMetadataWildcard(
                        FEATURE_ID_POINTER.addLeaf(JsonKey.of("properties")).addLeaf(JsonKey.of("temp")),
                        metadataWildcardExpr, GET_METADATA_HEADER_KEY))
                .withMessage(MessageFormat.format(
                        "The wildcard expression ''{0}'' in header ''{1}'' is not supported on this resource level.",
                        metadataWildcardExpr, GET_METADATA_HEADER_KEY))
                .withNoCause();
    }

    @Test
    public void validateValidDefinitionMetadataWildcardOnThingLevel() {
        final String metadataWildcardExpr = "features/*/definition/metadataKey";

        assertThatNoException()
                .isThrownBy(() -> MetadataWildcardValidator.validateMetadataWildcard(ROOT_POINTER, metadataWildcardExpr,
                        GET_METADATA_HEADER_KEY));
    }

    @Test
    public void validateValidDefinitionMetadataWildcardOnFeaturesLevel() {
        final String metadataWildcardExpr = "/*/definition/metadataKey";

        assertThatNoException()
                .isThrownBy(() -> MetadataWildcardValidator.validateMetadataWildcard(FEATURES_POINTER,
                        metadataWildcardExpr, GET_METADATA_HEADER_KEY));
    }

    @Test
    public void validateInvalidDefinitionMetadataWildcardOnThingLevel() {
        final List<String> metadataWildcardExprList =
                List.of("features/*/*/definition/metadataKey", "features/*/*/definition/metadataKey",
                        "/features/*startingWith*/definition/metadataKey", "features/*/*/definition/*/metadataKey");

        metadataWildcardExprList.forEach(wildcardExpr ->
                assertThatExceptionOfType(DittoHeaderInvalidException.class)
                        .isThrownBy(() -> MetadataWildcardValidator.validateMetadataWildcard(ROOT_POINTER,
                                wildcardExpr, GET_METADATA_HEADER_KEY))
                        .withMessage(MessageFormat.format(
                                "The wildcard expression ''{0}'' in header ''{1}'' is not valid.",
                                wildcardExpr, GET_METADATA_HEADER_KEY))
                        .withNoCause()
        );
    }

}