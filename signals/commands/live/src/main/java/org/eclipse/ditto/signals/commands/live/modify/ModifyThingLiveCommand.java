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

import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.live.base.LiveCommand;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;

/**
 * {@link ModifyThing} live command giving access to the command and all of its special accessors. Also the entry point
 * for creating a {@link ModifyThingLiveCommandAnswerBuilder} capable of answering incoming commands.
 */
public interface ModifyThingLiveCommand extends LiveCommand<ModifyThingLiveCommand,
        ModifyThingLiveCommandAnswerBuilder>, ThingModifyCommand<ModifyThingLiveCommand> {

    /**
     * Returns the {@code Thing} to modify.
     *
     * @return the Thing to modify.
     * @see ModifyThing#getThing()
     */
    @Nonnull
    Thing getThing();

}
