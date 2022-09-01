/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.headers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Unit test for {@link DittoChannelValueValidator}.
 */
@RunWith(Enclosed.class)
public final class DittoChannelValueValidatorTest {

    public static final class GeneralFunctionalityTest {

        @Test
        public void assertImmutability() {
            assertInstancesOf(DittoDurationValueValidator.class, areImmutable());
        }

        @Test
        public void getInstanceReturnsSomething() {
            final DittoChannelValueValidator instance = DittoChannelValueValidator.getInstance();

            assertThat(instance).isInstanceOf(DittoChannelValueValidator.class);
        }

        @Test
        public void acceptNullDefinition() {
            final DittoChannelValueValidator underTest = DittoChannelValueValidator.getInstance();

            Assertions.assertThatNullPointerException()
                    .isThrownBy(() -> underTest.accept(null, "covfefe"))
                    .withMessage("The definition must not be null!")
                    .withNoCause();
        }

        @Test
        public void acceptNullCharSequence() {
            final DittoChannelValueValidator underTest = DittoChannelValueValidator.getInstance();
            final DittoHeaderDefinition channel = DittoHeaderDefinition.CHANNEL;

            Assertions.assertThatExceptionOfType(DittoHeaderInvalidException.class)
                    .isThrownBy(() -> underTest.accept(channel, null))
                    .withMessage("The value 'null' of the header '%s' is not a valid String.", channel.getKey())
                    .withNoCause();
        }

    }

    @RunWith(Parameterized.class)
    public static final class ParameterizedValueValidationTest {

        @Parameterized.Parameter
        public CharSequence value;

        @Parameterized.Parameter(1)
        public boolean expectedValueToBeValid;

        @Parameterized.Parameters(name = "value: {0}, expected valid: {1}")
        public static List<Object[]> parameters() {
            return Arrays.asList(new Object[][]{
                    {DittoChannelValueValidator.CHANNEL_TWIN, true},
                    {DittoChannelValueValidator.CHANNEL_LIVE, true},
                    {"foo", false},
                    {"   twin", true},
                    {"live   ", true},
                    {" TWIN", true},
                    {"livE", true},
                    {"twins", false},
                    {"alive", false},
            });
        }

        @Test
        public void acceptChannelValue() {
            final DittoChannelValueValidator underTest = DittoChannelValueValidator.getInstance();
            final DittoHeaderDefinition channel = DittoHeaderDefinition.CHANNEL;

            if (expectedValueToBeValid) {
                assertThatNoException()
                        .isThrownBy(() -> underTest.accept(channel, value));
            } else {
                assertThatExceptionOfType(DittoHeaderInvalidException.class)
                        .isThrownBy(() -> underTest.accept(channel, value))
                        .withNoCause();
            }
        }

    }

}
