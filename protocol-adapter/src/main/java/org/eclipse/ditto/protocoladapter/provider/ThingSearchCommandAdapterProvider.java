/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocoladapter.provider;

import org.eclipse.ditto.protocoladapter.Adapter;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.CommandResponse;

/**
 * Interface providing the modify command adapter and the modify command response adapter.
 *
 * @param <M> the type of modify commands
 * @param <R> the type of modify command responses
 */
interface ThingSearchCommandAdapterProvider<M extends Signal<?>, R extends CommandResponse<?>> {

    /**
     * @return the modify command adapter
     */
    Adapter<M> getSearchCommandAdapter();

    /**
     * @return the modify command response adapter
     */
    Adapter<R> getThingSearchCommandResponseAdapter();

}
