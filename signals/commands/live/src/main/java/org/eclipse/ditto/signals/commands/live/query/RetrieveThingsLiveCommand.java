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

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.eclipse.ditto.model.things.id.ThingId;
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
     * @deprecated the thing ID is now typed. Use {@link #getThingEntityIds()} instead.
     */
    @Nonnull
    @Deprecated
    default List<String> getThingIds() {
        return getThingEntityIds().stream().map(String::valueOf).collect(Collectors.toList());
    }

    @Nonnull
    List<ThingId> getThingEntityIds();

}
