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

import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatures;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;

import org.eclipse.ditto.signals.commands.live.base.LiveCommand;

/**
 * {@link RetrieveFeatures} live command giving access to the command and all of its special accessors. Also the entry
 * point for creating a {@link RetrieveFeaturesLiveCommandAnswerBuilder} capable of answering incoming commands.
 */
public interface RetrieveFeaturesLiveCommand extends LiveCommand<RetrieveFeaturesLiveCommand,
        RetrieveFeaturesLiveCommandAnswerBuilder>, ThingQueryCommand<RetrieveFeaturesLiveCommand> {
}
