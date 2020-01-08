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
package org.eclipse.ditto.signals.commands.connectivity.query;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommand;

/**
 * Aggregates all {@link ConnectivityCommand}s which query the state of a {@link Connection}.
 *
 * @param <T> the type of the implementing class.
 */
public interface ConnectivityQueryCommand<T extends ConnectivityQueryCommand> extends ConnectivityCommand<T> {

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

}
