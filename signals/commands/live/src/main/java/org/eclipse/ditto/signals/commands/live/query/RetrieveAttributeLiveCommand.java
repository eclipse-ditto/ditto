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

import javax.annotation.Nonnull;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.signals.commands.live.base.LiveCommand;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttribute;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;

/**
 * {@link RetrieveAttribute} live command giving access to the command and all of its special accessors. Also the entry
 * point for creating a {@link RetrieveAttributeLiveCommandAnswerBuilder} capable of answering incoming commands.
 */
public interface RetrieveAttributeLiveCommand extends LiveCommand<RetrieveAttributeLiveCommand,
        RetrieveAttributeLiveCommandAnswerBuilder>, ThingQueryCommand<RetrieveAttributeLiveCommand> {

    /**
     * Returns the JSON pointer of the attribute to retrieve.
     *
     * @return the JSON pointer.
     */
    @Nonnull
    JsonPointer getAttributePointer();

}
