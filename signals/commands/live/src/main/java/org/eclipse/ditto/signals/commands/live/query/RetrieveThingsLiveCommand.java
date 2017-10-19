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

import java.util.List;

import javax.annotation.Nonnull;

import org.eclipse.ditto.signals.commands.base.WithNamespace;
import org.eclipse.ditto.signals.commands.live.base.LiveCommand;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;

/**
 * {@link RetrieveThings} live command giving access to the command and all of its special accessors. Also the entry
 * point for creating a {@link RetrieveThingsLiveCommandAnswerBuilder} capable of answering incoming commands.
 */
public interface RetrieveThingsLiveCommand
        extends LiveCommand<RetrieveThingsLiveCommand, RetrieveThingsLiveCommandAnswerBuilder>,
        ThingQueryCommand<RetrieveThingsLiveCommand>, WithNamespace {

    /**
     * Returns the identifiers of the {@code Thing}s to be retrieved.
     *
     * @return the identifiers
     */
    @Nonnull
    List<String> getThingIds();

}
