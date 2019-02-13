/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.base.config.raw;

import java.util.Collections;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import javax.annotation.concurrent.Immutable;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Parses a given String to a suitable {@link Config}.
 */
@Immutable
final class VcapServicesStringToConfig implements Function<String, Config> {

    /**
     * Config path which denotes all VCAP services config.
     */
    static final String VCAP_CONFIG_PATH = "vcap";

    private VcapServicesStringToConfig() {
        super();
    }

    /**
     * Returns an instance of {@code VcapServicesStringToConfig}.
     *
     * @return the instance.
     */
    static VcapServicesStringToConfig getInstance() {
        return new VcapServicesStringToConfig();
    }

    /**
     * @throws java.lang.NullPointerException if {@code systemVcapServices} is {@code null}.
     */
    @Override
    public Config apply(final String systemVcapServices) {
        final VcapServicesStringParser parseVcapServicesString = VcapServicesStringParser.getInstance();
        final UnaryOperator<Config> prefixWithVcap = VcapServicesStringToConfig::getPrefixedWithVcap;
        final AliasesAppender appendAliases = AliasesAppender.getInstance();

        return parseVcapServicesString.andThen(prefixWithVcap).andThen(appendAliases).apply(systemVcapServices);
    }

    private static Config getPrefixedWithVcap(final Config servicesConfig) {
        return ConfigFactory.parseMap(Collections.singletonMap(VCAP_CONFIG_PATH, servicesConfig.root()));
    }

}
