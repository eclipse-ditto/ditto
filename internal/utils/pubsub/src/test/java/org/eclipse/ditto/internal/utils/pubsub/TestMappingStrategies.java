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
package org.eclipse.ditto.internal.utils.pubsub;

import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.internal.utils.cluster.GlobalMappingStrategies;
import org.eclipse.ditto.internal.utils.cluster.MappingStrategies;
import org.eclipse.ditto.internal.utils.cluster.MappingStrategiesBuilder;
import org.eclipse.ditto.base.model.signals.JsonParsable;

/**
 * Mapping strategies to serialize Ditto signals for tests.
 */
@Immutable
public final class TestMappingStrategies extends MappingStrategies {

    private TestMappingStrategies(final Map<String, JsonParsable<Jsonifiable<?>>> mappingStrategies) {
        super(mappingStrategies);
    }

    /**
     * Constructs a new TestMappingStrategies object.
     */
    @SuppressWarnings("unused") // used via reflection
    public TestMappingStrategies() {
        this(getMappingStrategies());
    }

    private static MappingStrategies getMappingStrategies() {
        return MappingStrategiesBuilder.newInstance()
                .putAll(GlobalMappingStrategies.getInstance())
                .build();
    }

}
