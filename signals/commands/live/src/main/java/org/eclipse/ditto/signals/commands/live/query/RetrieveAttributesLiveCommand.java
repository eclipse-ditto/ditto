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
package org.eclipse.ditto.signals.commands.live.query;

import org.eclipse.ditto.signals.commands.live.base.LiveCommand;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributes;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;

/**
 * {@link RetrieveAttributes} live command giving access to the command and all of its special accessors. Also the entry
 * point for creating a {@link RetrieveAttributesLiveCommandAnswerBuilder} capable of answering incoming commands.
 */
public interface RetrieveAttributesLiveCommand
        extends LiveCommand<RetrieveAttributesLiveCommand, RetrieveAttributesLiveCommandAnswerBuilder>,
        ThingQueryCommand<RetrieveAttributesLiveCommand> {
}
