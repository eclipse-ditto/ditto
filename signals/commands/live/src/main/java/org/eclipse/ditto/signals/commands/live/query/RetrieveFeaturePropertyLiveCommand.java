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
import org.eclipse.ditto.signals.base.WithFeatureId;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperty;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;

import org.eclipse.ditto.signals.commands.live.base.LiveCommand;

/**
 * {@link RetrieveFeatureProperty} live command giving access to the command and all of its special accessors. Also the
 * entry  point for creating a {@link RetrieveFeaturePropertyLiveCommandAnswerBuilder} capable of answering incoming
 * commands.
 */
public interface RetrieveFeaturePropertyLiveCommand
        extends LiveCommand<RetrieveFeaturePropertyLiveCommand, RetrieveFeaturePropertyLiveCommandAnswerBuilder>,
        ThingQueryCommand<RetrieveFeaturePropertyLiveCommand>, WithFeatureId {

    /**
     * Returns the JSON pointer of the Property to retrieve.
     *
     * @return the JSON pointer.
     */
    @Nonnull
    JsonPointer getPropertyPointer();

}
