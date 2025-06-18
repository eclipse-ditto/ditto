/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.persistence.actors.strategies.events;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.persistentactors.events.AbstractEventStrategies;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.events.DynamicConfigSectionDeleted;
import org.eclipse.ditto.things.model.devops.events.DynamicConfigSectionMerged;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigCreated;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigDeleted;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigEvent;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigModified;

/**
 * This Singleton strategy handles all {@link WotValidationConfigEvent}s.
 *
 * @since 3.8.0
 */
@Immutable
public final class WotValidationConfigEventStrategies
        extends AbstractEventStrategies<WotValidationConfigEvent<?>, WotValidationConfig> {

    private static final WotValidationConfigEventStrategies INSTANCE = new WotValidationConfigEventStrategies();

    /**
     * Returns the {@code EventHandleStrategy} instance.
     *
     * @return the instance.
     */
    public static WotValidationConfigEventStrategies getInstance() {
        return INSTANCE;
    }

    /**
     * Constructs a new {@code WotValidationConfigEventStrategies}.
     */
    private WotValidationConfigEventStrategies() {
        addStrategy(WotValidationConfigCreated.class, new WotValidationConfigCreatedStrategy());
        addStrategy(WotValidationConfigDeleted.class, new WotValidationConfigDeletedStrategy());
        addStrategy(WotValidationConfigModified.class, new WotValidationConfigModifiedStrategy());
        addStrategy(DynamicConfigSectionMerged.class, new DynamicConfigSectionMergedStrategy());
        addStrategy(DynamicConfigSectionDeleted.class, new DynamicConfigSectionDeletedStrategy());
    }
} 