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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorSystem;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistentactors.commands.AbstractCommandStrategies;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.things.model.devops.commands.WotValidationConfigCommand;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigEvent;
import org.eclipse.ditto.wot.api.config.DefaultWotConfig;

/**
 * Collection of command strategies for WoT validation config commands.
 * <p>
 * This class registers and manages all command strategies that handle WoT validation config commands, such as create,
 * modify, delete, and retrieve operations for both the main config and dynamic config sections. It acts as a singleton
 * entry point for command strategy resolution in the persistence layer.
 * </p>
 *
 * @since 3.8.0
 */
public final class WotValidationConfigCommandStrategies
        extends AbstractCommandStrategies<Command<?>, WotValidationConfig, WotValidationConfigId, WotValidationConfigEvent<?>> {

    @SuppressWarnings("java:S3077") // volatile because of double checked locking pattern
    @Nullable
    private static volatile WotValidationConfigCommandStrategies instance;

    /**
     * Constructs a new {@code WotValidationConfigCommandStrategies} object.
     *
     * @param system the Pekko ActorSystem to use for strategy registration.
     */
    private WotValidationConfigCommandStrategies(final ActorSystem system) {
        super(WotValidationConfigCommand.class);
        addStrategies(system);
    }

    /**
     * Returns the singleton instance of {@code WotValidationConfigCommandStrategies}.
     *
     * @param system the Pekko ActorSystem to use.
     * @return the instance.
     */
    public static WotValidationConfigCommandStrategies getInstance(final ActorSystem system) {
        WotValidationConfigCommandStrategies localInstance = instance;
        if (null == localInstance) {
            synchronized (WotValidationConfigCommandStrategies.class) {
                localInstance = instance;
                if (null == localInstance) {
                    instance = localInstance = new WotValidationConfigCommandStrategies(system);
                }
            }
        }
        return localInstance;
    }

    /**
     * Registers all command strategies for WoT validation config commands.
     *
     * @param system the Pekko ActorSystem to use for strategy registration.
     */
    private void addStrategies(final ActorSystem system) {
        final var dittoScopedConfig = DefaultScopedConfig.dittoScoped(system.settings().config());
        final var wotConfig = DefaultWotConfig.of(dittoScopedConfig.getConfig("things"));
        final var ddata = WotValidationConfigDData.of(system);

        final var staticConfig = wotConfig.getValidationConfig();

        addStrategy(new ModifyWotValidationConfigStrategy(ddata));
        addStrategy(new CreateWotValidationConfigStrategy(ddata));
        addStrategy(new DeleteWotValidationConfigStrategy(ddata));
        addStrategy(new RetrieveWotValidationConfigStrategy());
        addStrategy(new RetrieveMergedWotValidationConfigStrategy(staticConfig));
        addStrategy(new RetrieveDynamicConfigSectionStrategy());
        addStrategy(new DeleteDynamicConfigSectionStrategy(ddata));
        addStrategy(new RetrieveAllDynamicConfigSectionsStrategy());
        addStrategy(new MergeDynamicConfigSectionStrategy(ddata));
    }

    @Override
    protected Result<WotValidationConfigEvent<?>> getEmptyResult() {
        return ResultFactory.emptyResult();
    }
}