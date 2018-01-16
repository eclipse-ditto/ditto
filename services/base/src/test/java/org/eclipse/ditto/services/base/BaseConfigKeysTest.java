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
package org.eclipse.ditto.services.base;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.eclipse.ditto.services.base.BaseConfigKey.Cluster.MAJORITY_CHECK_ENABLED;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.services.base.BaseConfigKeys} and its Builder.
 */
public final class BaseConfigKeysTest {

    private static final String SERVICE_SPECIFIC_CONFIG_KEY = "service.foo";

    private BaseConfigKeys.Builder builder;

    @Before
    public void setUp() {
        builder = BaseConfigKeys.getBuilder();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(BaseConfigKeys.class,
                areImmutable(),
                assumingFields("values").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void tryToPutNullBaseConfigKey() {
        assertThatNullPointerException()
                .isThrownBy(() -> builder.put(null, SERVICE_SPECIFIC_CONFIG_KEY))
                .withMessage("The %s must not be null!", "base config key")
                .withNoCause();
    }

    @Test
    public void tryToPutNullServiceSpecificConfigKey() {
        assertThatNullPointerException()
                .isThrownBy(() -> builder.put(MAJORITY_CHECK_ENABLED, null))
                .withMessage("The %s must not be null!", "service-specific config key")
                .withNoCause();
    }

    @Test
    public void tryToPutEmptyServiceSpecificConfigKey() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> builder.put(MAJORITY_CHECK_ENABLED, ""))
                .withMessage("The argument '%s' must not be empty!", "service-specific config key")
                .withNoCause();
    }

    @Test
    public void tryToRemoveNullBaseConfigKey() {
        assertThatNullPointerException()
                .isThrownBy(() -> builder.remove(null))
                .withMessage("The %s must not be null!", "base config key")
                .withNoCause();
    }

    @Test
    public void checkForExistenceOfMissingKeys() {
        final BaseConfigKeys emptyConfigKeys = builder.build();

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> emptyConfigKeys.checkExistence(MAJORITY_CHECK_ENABLED,
                        BaseConfigKey.Cluster.MAJORITY_CHECK_DELAY))
                .withMessageStartingWith("The base config keys did not contain")
                .withMessageContaining(MAJORITY_CHECK_ENABLED.toString())
                .withMessageContaining(BaseConfigKey.Cluster.MAJORITY_CHECK_DELAY.toString())
                .withNoCause();
    }

    @Test
    public void tryToGetValueForNullBaseConfigKey() {
        final BaseConfigKeys emptyConfigKeys = builder.build();

        assertThatNullPointerException()
                .isThrownBy(() -> emptyConfigKeys.get(null))
                .withMessage("The %s must not be null!", "base config key")
                .withNoCause();
    }

    @Test
    public void getValueForNotExistingBaseConfigKeyReturnsEmptyOptional() {
        final BaseConfigKeys underTest = builder.build();

        assertThat(underTest.get(MAJORITY_CHECK_ENABLED)).isEmpty();
    }

    @Test
    public void getValueForExistingBaseConfigKeyReturnsExpected() {
        builder.put(MAJORITY_CHECK_ENABLED, SERVICE_SPECIFIC_CONFIG_KEY);
        final BaseConfigKeys underTest = builder.build();

        assertThat(underTest.get(MAJORITY_CHECK_ENABLED)).contains(SERVICE_SPECIFIC_CONFIG_KEY);
    }

    @Test
    public void getOrThrowThrowsNullPointerExceptionForNonExistingBaseConfigKey() {
        final BaseConfigKeys underTest = builder.build();

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.getOrThrow(MAJORITY_CHECK_ENABLED))
                .withMessage("Config key <%s> is unknown!", MAJORITY_CHECK_ENABLED)
                .withNoCause();
    }

    @Test
    public void getOrThrowForExistingBaseConfigKeyReturnsExpected() {
        builder.put(MAJORITY_CHECK_ENABLED, SERVICE_SPECIFIC_CONFIG_KEY);
        final BaseConfigKeys underTest = builder.build();

        assertThat(underTest.getOrThrow(MAJORITY_CHECK_ENABLED)).isEqualTo(SERVICE_SPECIFIC_CONFIG_KEY);
    }

}