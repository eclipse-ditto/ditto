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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.signals.base.GlobalErrorRegistry;
import org.eclipse.ditto.signals.commands.base.GlobalCommandRegistry;
import org.eclipse.ditto.signals.commands.base.GlobalCommandResponseRegistry;
import org.eclipse.ditto.signals.events.base.GlobalEventRegistry;

public abstract class AbstractGlobalMappingStrategies extends AbstractMappingStrategies {

    protected AbstractGlobalMappingStrategies(final Map<String, MappingStrategy> strategies) {
        super(withGlobalStrategies(strategies));
    }

    private static Map<String, MappingStrategy> withGlobalStrategies(final Map<String, MappingStrategy> strategies) {
        final Map<String, MappingStrategy> combinedStrategies = new HashMap<>();

        final MappingStrategies mappingStrategies = MappingStrategiesBuilder.newInstance()
                .add(GlobalErrorRegistry.getInstance())
                .add(GlobalCommandRegistry.getInstance())
                .add(GlobalCommandResponseRegistry.getInstance())
                .add(GlobalEventRegistry.getInstance())
                .build();

        combinedStrategies.putAll(mappingStrategies.getStrategies());
        combinedStrategies.putAll(strategies);

        return combinedStrategies;
    }

}
