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
/*
 * Copyright (c) 2011-2017 Bosch Software Innovations GmbH, Germany. All rights reserved.
 */
package org.eclipse.ditto.signals.commands.live.modify;

import org.eclipse.ditto.signals.commands.live.base.LiveCommand;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThing;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;

/**
 * {@link DeleteThing} live command giving access to the command and all of its special accessors. Also the entry point
 * for creating a {@link DeleteThingLiveCommandAnswerBuilder} capable of answering incoming commands.
 */
public interface DeleteThingLiveCommand
        extends LiveCommand<DeleteThingLiveCommand, DeleteThingLiveCommandAnswerBuilder>,
        ThingModifyCommand<DeleteThingLiveCommand> {
}
