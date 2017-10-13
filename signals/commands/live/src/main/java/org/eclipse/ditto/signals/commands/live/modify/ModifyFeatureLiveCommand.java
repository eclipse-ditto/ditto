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

import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.signals.base.WithFeatureId;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeature;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;

import org.eclipse.ditto.signals.commands.live.base.LiveCommand;

/**
 * {@link ModifyFeature} live command giving access to the command and all of its special accessors. Also the entry
 * point for creating a {@link ModifyFeatureLiveCommandAnswerBuilder} capable of answering incoming commands.
 */
public interface ModifyFeatureLiveCommand extends
        LiveCommand<ModifyFeatureLiveCommand, ModifyFeatureLiveCommandAnswerBuilder>,
        ThingModifyCommand<ModifyFeatureLiveCommand>,
        WithFeatureId {

    /**
     * Returns the new {@code Feature} to modify.
     *
     * @return the Feature to modify
     */
    @Nonnull
    Feature getFeature();

}
