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
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttribute;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttributes;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeature;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDesiredProperties;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureProperties;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatures;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.junit.Test;

/**
 * Unit test for {@link MetadataWildcardValidator}.
 */
public class MetadataWildcardValidatorTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(MetadataWildcardValidator.class, areImmutable());
    }

    @Test
    public void validateValidMetadataWildcardForRetrieveThing() {
        final List<String> metadataWildcardExprList =
                List.of("features/*/properties/*/key", "features/*/properties/humidity/key",
                        "features/lamp/properties/*/key", "/attributes/*/key", "*/yeah");

        metadataWildcardExprList.forEach(wildcardExpr ->
                assertThatNoException()
                        .isThrownBy(() -> MetadataWildcardValidator.validateMetadataWildcard(RetrieveThing.TYPE,
                                wildcardExpr))
        );
    }

    @Test
    public void validateValidMetadataWildcardForRetrieveFeatures() {
        final List<String> metadataWildcardExprList = List.of("/*/properties/*/key",
                "/*/properties/humidity*/key", "/f1/properties/*/key", "/f*1*/properties/*/key");

        metadataWildcardExprList.forEach(wildcardExpr ->
                assertThatNoException()
                        .isThrownBy(() -> MetadataWildcardValidator.validateMetadataWildcard(RetrieveFeatures.TYPE,
                                wildcardExpr))
        );
    }

    @Test
    public void validateValidMetadataWildcardForRetrieveFeature() {
        final String metadataWildcardExpr = "properties/*/key";

        assertThatNoException()
                .isThrownBy(() -> MetadataWildcardValidator.validateMetadataWildcard(RetrieveFeature.TYPE,
                        metadataWildcardExpr));
    }

    @Test
    public void validateValidMetadataWildcardForRetrieveDesiredFeature() {
        final String metadataWildcardExpr = "desiredProperties/*/key";

        assertThatNoException()
                .isThrownBy(() -> MetadataWildcardValidator.validateMetadataWildcard(RetrieveFeature.TYPE,
                        metadataWildcardExpr));
    }

    @Test
    public void validateValidMetadataWildcardForRetrieveFeatureProperties() {
        final List<String> metadataWildcardExprList = List.of("/*/key", "*/metaDataKey");

        metadataWildcardExprList.forEach(metadataWildcardExpr ->
                assertThatNoException()
                        .isThrownBy(
                                () -> MetadataWildcardValidator.validateMetadataWildcard(RetrieveFeatureProperties.TYPE,
                                        metadataWildcardExpr))
        );
    }

    @Test
    public void validateValidMetadataWildcardForRetrieveFeatureDesiredProperties() {
        final List<String> metadataWildcardExprList = List.of("/*/key", "*/metaDataKey");

        metadataWildcardExprList.forEach(metadataWildcardExpr ->
                assertThatNoException()
                        .isThrownBy(
                                () -> MetadataWildcardValidator.validateMetadataWildcard(
                                        RetrieveFeatureDesiredProperties.TYPE,
                                        metadataWildcardExpr))
        );
    }

    @Test
    public void validateValidMetadataWildcardForRetrieveAttributes() {
        final String metadataWildcardExpr = "/*/key";

        assertThatNoException()
                .isThrownBy(() -> MetadataWildcardValidator.validateMetadataWildcard(RetrieveAttributes.TYPE,
                        metadataWildcardExpr));
    }

    @Test
    public void validateInvalidMetadataWildcardForRetrieveThing() {
        final List<String> metadataWildcardExprList =
                List.of("features/*/properties/*/*/key", "features/*/*/properties/*/key", "attributes/*/*/key",
                        "*/*/*/yeah");

        metadataWildcardExprList.forEach(wildcardExpr ->
                assertThatExceptionOfType(DittoHeaderInvalidException.class)
                        .isThrownBy(() -> MetadataWildcardValidator.validateMetadataWildcard(RetrieveThing.TYPE,
                                wildcardExpr))
                        .withMessage(MessageFormat.format(
                                "The wildcard expression ''{0}'' in header 'get-metadata' is not valid.",
                                wildcardExpr))
                        .withNoCause()
        );
    }

    @Test
    public void validateInvalidMetadataWildcardForRetrieveFeatures() {
        final List<String> metadataWildcardExprList = List.of("/*/properties/*/*/key", "/*/*/properties/*/*/key");

        metadataWildcardExprList.forEach(wildcardExpr ->
                assertThatExceptionOfType(DittoHeaderInvalidException.class)
                        .isThrownBy(() -> MetadataWildcardValidator.validateMetadataWildcard(RetrieveFeatures.TYPE,
                                wildcardExpr))
                        .withMessage(MessageFormat.format(
                                "The wildcard expression ''{0}'' in header 'get-metadata' is not valid.",
                                wildcardExpr))
                        .withNoCause()
        );
    }

    @Test
    public void validateInvalidMetadataWildcardForRetrieveFeature() {
        final String metadataWildcardExpr = "/properties/*/*/key";

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> MetadataWildcardValidator.validateMetadataWildcard(RetrieveFeature.TYPE,
                        metadataWildcardExpr))
                .withMessage(MessageFormat.format(
                        "The wildcard expression ''{0}'' in header 'get-metadata' is not valid.",
                        metadataWildcardExpr))
                .withNoCause();
    }

    @Test
    public void validateInvalidMetadataWildcardForRetrieveDesiredFeature() {
        final String metadataWildcardExpr = "/desiredProperties/*/*/key";

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> MetadataWildcardValidator.validateMetadataWildcard(RetrieveFeature.TYPE,
                        metadataWildcardExpr))
                .withMessage(MessageFormat.format(
                        "The wildcard expression ''{0}'' in header 'get-metadata' is not valid.",
                        metadataWildcardExpr))
                .withNoCause();
    }

    @Test
    public void validateInvalidMetadataWildcardForRetrieveFeatureProperties() {
        final List<String> metadataWildcardExprList = List.of("/properties/*/*/key", "/*/*/*/key", "/*");

        metadataWildcardExprList.forEach(metadataWildcardExpr ->
                assertThatExceptionOfType(DittoHeaderInvalidException.class)
                        .isThrownBy(
                                () -> MetadataWildcardValidator.validateMetadataWildcard(RetrieveFeatureProperties.TYPE,
                                        metadataWildcardExpr))
                        .withMessage(MessageFormat.format(
                                "The wildcard expression ''{0}'' in header 'get-metadata' is not valid.",
                                metadataWildcardExpr))
                        .withNoCause()
        );
    }

    @Test
    public void validateInvalidMetadataWildcardForRetrieveAttributes() {
        final String metadataWildcardExpr = "attributes/*/*/key";

        assertThatExceptionOfType(DittoHeaderInvalidException.class)
                .isThrownBy(() -> MetadataWildcardValidator.validateMetadataWildcard(RetrieveAttributes.TYPE,
                        metadataWildcardExpr))
                .withMessage(MessageFormat.format(
                        "The wildcard expression ''{0}'' in header 'get-metadata' is not valid.",
                        metadataWildcardExpr))
                .withNoCause();
    }

    @Test
    public void validateInvalidMetadataWildcardForRetrieveAttribute() {
        final List<String> metadataWildcardExprList = List.of("*/*/key", "/*/*/key", "*startingWith*/key", "*/");

        metadataWildcardExprList.forEach(wildcardExpr ->
                assertThatExceptionOfType(DittoHeaderNotSupportedException.class)
                        .isThrownBy(() -> MetadataWildcardValidator.validateMetadataWildcard(RetrieveAttribute.TYPE,
                                wildcardExpr))
                        .withMessage(MessageFormat.format(
                                "The wildcard expression ''{0}'' in header 'get-metadata' is not supported on this resource level.",
                                wildcardExpr))
                        .withNoCause()
        );
    }

}