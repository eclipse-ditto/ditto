/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
