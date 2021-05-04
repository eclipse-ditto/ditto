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
package org.eclipse.ditto.internal.utils.cluster;

import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.base.model.signals.GlobalErrorRegistry;
import org.eclipse.ditto.base.model.signals.JsonParsable;
import org.eclipse.ditto.base.model.signals.commands.GlobalCommandRegistry;
import org.eclipse.ditto.base.model.signals.commands.GlobalCommandResponseRegistry;
import org.eclipse.ditto.base.model.signals.events.GlobalEventRegistry;
import org.eclipse.ditto.base.model.signals.announcements.GlobalAnnouncementRegistry;

@Immutable
public final class GlobalMappingStrategies extends MappingStrategies {

    @Nullable private static GlobalMappingStrategies instance = null;

    private GlobalMappingStrategies(final Map<String, JsonParsable<Jsonifiable<?>>> mappingStrategies) {
        super(mappingStrategies);
    }

    /**
     * Constructs a new GlobalMappingStrategies object.
     */
    @SuppressWarnings("unused") // used via reflection
    public GlobalMappingStrategies() {
        this(getGlobalStrategies());
    }

    /**
     * Returns an instance of GlobalMappingStrategies.
     *
     * @return the instance.
     */
    public static GlobalMappingStrategies getInstance() {
        GlobalMappingStrategies result = instance;
        if (null == result) {
            result = new GlobalMappingStrategies(getGlobalStrategies());
            instance = result;
        }
        return result;
    }

    private static MappingStrategies getGlobalStrategies() {
        return MappingStrategiesBuilder.newInstance()
                .add(GlobalErrorRegistry.getInstance())
                .add(GlobalCommandRegistry.getInstance())
                .add(GlobalCommandResponseRegistry.getInstance())
                .add(GlobalEventRegistry.getInstance())
                .add(GlobalAnnouncementRegistry.getInstance())
                .build();
    }

}
