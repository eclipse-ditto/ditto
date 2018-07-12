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
package org.eclipse.ditto.services.base.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Unit test of {@link HeadersConfigReader}.
 */
public final class HeadersConfigReaderTest {

    @Test
    public void testDefaultConfiguration() {
        final Config rawConfig = defaultRawConfig();
        final HeadersConfigReader underTest = HeadersConfigReader.fromRawConfig(rawConfig);

        assertThat(underTest.compatibilityMode()).isFalse();
        assertThat(underTest.blacklist()).contains("authorization", "origin", "thing-id", "feature-id");
    }

    @Test
    public void testCompatibleConfiguration() {
        final Config rawConfig = ConfigFactory.parseString("ditto.headers.compatibility-mode=true")
                .withFallback(defaultRawConfig());
        final HeadersConfigReader underTest = HeadersConfigReader.fromRawConfig(rawConfig);

        assertThat(underTest.compatibilityMode()).isTrue();
        assertThat(underTest.blacklist()).contains("authorization", "origin");
        assertThat(underTest.blacklist()).doesNotContain("thing-id", "feature-id");
    }

    private static Config defaultRawConfig() {
        return ConfigFactory.load(HeadersConfigReader.class.getClassLoader(), "ditto-service-base");
    }
}
