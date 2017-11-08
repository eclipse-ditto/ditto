/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.thingsearch.persistence.write.impl;

import static org.eclipse.ditto.services.thingsearch.common.util.KeyEscapeUtil.FAKE_DOT;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.services.thingsearch.persistence.MongoSortKeyMappingFunction;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MongoSortKeyMappingFunctionTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoSortKeyMappingFunctionTest.class);

    @Test
    public void featureAndProperty() {
        final String expected = "features.feature.props.acc";
        final String result = MongoSortKeyMappingFunction.mapSortKey("features", "feature", "props", "acc");
        LOGGER.info("Mapping result {}", result);
        Assertions.assertThat(result).isEqualTo(expected);
    }

    @Test
    public void featureWithDotsAndProperty() {
        final String expected = "features.feature" + FAKE_DOT + "1.props.acc.x";
        final String result = MongoSortKeyMappingFunction.mapSortKey("features", "feature.1", "props", "acc", "x");
        LOGGER.info("Mapping result {}", result);
        Assertions.assertThat(result).isEqualTo(expected);
    }

    @Test
    public void featureWithDotsAndPropertyWithSlashes() {
        final String expected = "features.feature" + FAKE_DOT + "1.props.acc.x";
        final String result = MongoSortKeyMappingFunction.mapSortKey("features", "feature.1", "props", "acc/x");
        LOGGER.info("Mapping result {}", result);
        Assertions.assertThat(result).isEqualTo(expected);
    }

    @Test
    public void featureWithDotsAndPropertyWithSlashesAndDots() {
        final String expected = "features.feature" + FAKE_DOT + "1.props.acc.x" + FAKE_DOT + "unit";
        final String result = MongoSortKeyMappingFunction.mapSortKey("features", "feature.1", "props", "acc/x.unit");
        LOGGER.info("Mapping result {}", result);
        Assertions.assertThat(result).isEqualTo(expected);
    }

    @Test
    public void featureWithDotsAndPropertyWithDotsAndSlashes() {
        final String expected = "features.feature" + FAKE_DOT + "1.props.acc" + FAKE_DOT + "x.unit";
        final String result = MongoSortKeyMappingFunction.mapSortKey("features", "feature.1", "props", "acc.x/unit");
        LOGGER.info("Mapping result {}", result);
        Assertions.assertThat(result).isEqualTo(expected);
    }

    @Test
    public void featureWithDotsAndPropertyWithMixedDotsAndSlashes() {
        final String expected =
                "features.feature" + FAKE_DOT + "1.props.acc" + FAKE_DOT + "x.unit" + FAKE_DOT + "y" + ".value";
        final String result =
                MongoSortKeyMappingFunction.mapSortKey("features", "feature.1", "props", "acc.x/unit.y", "value");
        LOGGER.info("Mapping result {}", result);
        Assertions.assertThat(result).isEqualTo(expected);
    }
}
