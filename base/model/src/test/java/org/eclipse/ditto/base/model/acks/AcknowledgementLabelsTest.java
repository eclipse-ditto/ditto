/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.acks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.assertj.core.util.Lists;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Unit test for {@link org.eclipse.ditto.base.model.acks.AcknowledgementLabels}.
 */
@RunWith(Enclosed.class)
public final class AcknowledgementLabelsTest {

    public static final class GeneralFunctionalityTest {

        private static final String KNOWN_LABEL_VALUE = "PROCESSING-DONE";

        @Test
        public void assertImmutability() {
            assertInstancesOf(AcknowledgementLabels.class, areImmutable());
        }

        @Test
        public void tryToGetNewLabelForNull() {
            assertThatNullPointerException()
                    .isThrownBy(() -> AcknowledgementLabels.newLabel(null))
                    .withMessage("The label must not be null!")
                    .withNoCause();
        }

        @Test
        public void newLabelReturnsExpected() {
            final AcknowledgementLabel newLabel = AcknowledgementLabels.newLabel(KNOWN_LABEL_VALUE);

            assertThat(newLabel.toString()).hasToString(KNOWN_LABEL_VALUE);
        }

        @Test
        public void newLabelReturnsSameInstanceIfGivenIsAlreadyLabel() {
            final AcknowledgementLabel acknowledgementLabel = AcknowledgementLabels.newLabel(KNOWN_LABEL_VALUE);
            final AcknowledgementLabel underTest = AcknowledgementLabels.newLabel(acknowledgementLabel);

            assertThat((CharSequence) underTest).isSameAs(acknowledgementLabel);
        }

    }

    @RunWith(Parameterized.class)
    public static final class RegexValidationTest {

        @Parameterized.Parameters(name = "{0}")
        public static List<RegexValidationParameter> validationParameters() {
            return Lists.list(
                    RegexValidationParameter.invalid("A"),
                    RegexValidationParameter.invalid("AB"),
                    RegexValidationParameter.valid("ABC"),
                    RegexValidationParameter.valid(IntStream.range(0, 165)
                            .mapToObj(i -> "a")
                            .collect(Collectors.joining())),
                    RegexValidationParameter.invalid(IntStream.range(0, 166)
                            .mapToObj(i -> "b")
                            .collect(Collectors.joining())),
                    RegexValidationParameter.invalid("ab?"),
                    RegexValidationParameter.valid("---"),
                    RegexValidationParameter.valid("___"),
                    RegexValidationParameter.valid("FOO-BAR"),
                    RegexValidationParameter.valid("0123456789"),
                    RegexValidationParameter.valid("{{connection:id}}:foo")
            );
        }

        @Parameterized.Parameter
        public RegexValidationParameter parameter;

        @Test
        public void newLabelReturnsExpectedType() {
            final String labelValue = parameter.labelValue;
            if (parameter.shouldBeValid) {
                final AcknowledgementLabel acknowledgementLabel = AcknowledgementLabels.newLabel(labelValue);

                assertThat(acknowledgementLabel.toString()).hasToString(labelValue);
            } else {
                assertThatExceptionOfType(AcknowledgementLabelInvalidException.class)
                        .isThrownBy(() -> AcknowledgementLabels.newLabel(labelValue))
                        .withMessageContaining(labelValue)
                        .withNoCause();
            }
        }

        private static final class RegexValidationParameter {

            private final String labelValue;
            private final boolean shouldBeValid;

            private RegexValidationParameter(final String labelValue, final boolean shouldBeValid) {
                this.labelValue = labelValue;
                this.shouldBeValid = shouldBeValid;
            }

            static RegexValidationParameter valid(final String labelValue) {
                return new RegexValidationParameter(labelValue, true);
            }

            static RegexValidationParameter invalid(final String labelValue) {
                return new RegexValidationParameter(labelValue, false);
            }

            @Override
            public String toString() {
                return "label=\"" + labelValue + "\", expected=" + (shouldBeValid ? "valid": "invalid");
            }

        }

    }

}
