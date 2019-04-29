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
import org.eclipse.ditto.signals.base.WithFeatureId;
import org.eclipse.ditto.signals.commands.live.base.LiveCommand;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperty;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;

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
