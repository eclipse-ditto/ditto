/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.connectivity.model;

//import static org.assertj.core.api.Assertions.assertThat;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.SoftAssertions;
import org.junit.Test;

/**
 * Unit test for {@link LogType}.
 */
public class LogTypeTest {

    @Test
    public void consumed() {
        LogTypeAssertions.assertThat(LogType.CONSUMED).supportsCategory(LogCategory.SOURCE);
    }

    @Test
    public void dispatched() {
        LogTypeAssertions.assertThat(LogType.DISPATCHED).supportsCategory(LogCategory.TARGET, LogCategory.RESPONSE);
    }

    @Test
    public void filtered() {
        LogTypeAssertions.assertThat(LogType.FILTERED).supportsCategory(LogCategory.TARGET, LogCategory.RESPONSE);
    }

    @Test
    public void mapped() {
        LogTypeAssertions.assertThat(LogType.MAPPED).supportsCategory(LogCategory.SOURCE, LogCategory.TARGET, LogCategory.RESPONSE);
    }

    @Test
    public void dropped() {
        LogTypeAssertions.assertThat(LogType.DROPPED).supportsCategory(LogCategory.SOURCE, LogCategory.TARGET, LogCategory.RESPONSE);
    }

    @Test
    public void enforced() {
        LogTypeAssertions.assertThat(LogType.ENFORCED).supportsCategory(LogCategory.SOURCE);
    }

    @Test
    public void published() {
        LogTypeAssertions.assertThat(LogType.PUBLISHED).supportsCategory(LogCategory.TARGET, LogCategory.RESPONSE);
    }

    @Test
    public void other() {
        LogTypeAssertions.assertThat(LogType.OTHER).supportsCategory(LogCategory.SOURCE, LogCategory.TARGET, LogCategory.RESPONSE, LogCategory.CONNECTION);
    }

    private static class LogTypeAssert extends AbstractAssert<LogTypeAssert, LogType> {

        private LogTypeAssert(final LogType logType) {
            super(logType, LogTypeAssert.class);
        }

        private LogTypeAssert supportsCategory(final LogCategory category, final LogCategory... otherCategories) {
            isNotNull();
            final SoftAssertions softAssertions = new SoftAssertions();

            final List<LogCategory> shouldNotContain = new ArrayList<>(Arrays.asList(LogCategory.values()));
            shouldNotContain.remove(category);
            shouldNotContain.removeAll(Arrays.asList(otherCategories));

            final List<LogCategory> shouldContain = new ArrayList<>(Arrays.asList(otherCategories));
            shouldContain.add(category);

            final String shouldContainMessage =
                    MessageFormat.format("Expected log type <{0}> to support category <{1}>, but it didn't.", actual,
                            category);
            shouldContain.forEach(c -> {
                softAssertions.assertThat(actual.supportsCategory(c))
                        .overridingErrorMessage(shouldContainMessage)
                        .isTrue();
            });

            final String shouldNotContainMessage =
                    MessageFormat.format("Expected log type <{0}> to not support category <{1}>, but it did.", actual,
                            category);
            shouldNotContain.forEach(c -> {
                softAssertions.assertThat(actual.supportsCategory(c))
                        .overridingErrorMessage(shouldContainMessage)
                        .isFalse();
            });

            softAssertions.assertAll();
            return myself;
        }

    }

    private static class LogTypeAssertions extends Assertions {

        private static LogTypeAssert assertThat(final LogType logType) {
            return new LogTypeAssert(logType);
        }

    }

}
