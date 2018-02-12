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
package org.eclipse.ditto.signals.commands.live.modify;

import javax.annotation.Nonnull;

import org.eclipse.ditto.model.things.FeatureDefinition;
import org.eclipse.ditto.signals.base.WithFeatureId;
import org.eclipse.ditto.signals.commands.live.base.LiveCommand;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDefinition;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;

/**
 * {@link ModifyFeatureDefinition} live command giving access to the command and all of its special accessors. Also the
 * entry point for creating a {@link ModifyFeatureDefinitionLiveCommandAnswerBuilder} as answer for an incoming
 * command.
 */
public interface ModifyFeatureDefinitionLiveCommand extends LiveCommand<ModifyFeatureDefinitionLiveCommand,
        ModifyFeatureDefinitionLiveCommandAnswerBuilder>, ThingModifyCommand<ModifyFeatureDefinitionLiveCommand>,
        WithFeatureId {

    /**
     * Returns the {@link FeatureDefinition} to modify.
     *
     * @return the Properties to modify.
     * @see ModifyFeatureDefinition#getDefinition()
     */
    @Nonnull
    FeatureDefinition getDefinition();

}
