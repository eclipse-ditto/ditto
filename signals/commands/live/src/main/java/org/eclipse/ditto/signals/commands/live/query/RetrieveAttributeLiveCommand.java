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
