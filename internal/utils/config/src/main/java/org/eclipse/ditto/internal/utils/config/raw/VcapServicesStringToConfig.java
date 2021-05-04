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
package org.eclipse.ditto.internal.utils.config.raw;

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
