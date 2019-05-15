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
package org.eclipse.ditto.services.utils.cluster;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public abstract class AbstractMappingStrategies implements MappingStrategies {

    private final Map<String, MappingStrategy> strategies;

    protected AbstractMappingStrategies(final Map<String, MappingStrategy> strategies) {
        this.strategies = Collections.unmodifiableMap(new HashMap<>(strategies));
    }

    @Override
    public Optional<MappingStrategy> getMappingStrategyFor(final String key) {
        return Optional.ofNullable(strategies.get(key));
    }

    @Override
    public boolean containsMappingStrategyFor(final String key) {
        return strategies.containsKey(key);
    }

    @Override
    public Map<String, MappingStrategy> getStrategies() {
        return strategies;
    }

}
