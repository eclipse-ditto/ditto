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
package org.eclipse.ditto.connectivity.model.signals.commands.query;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.WithEntity;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommandResponse;

/**
 * Aggregates all {@link org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommandResponse} which respond to a
 * {@link ConnectivityQueryCommand}.
 *
 * @param <T> the type of the implementing class.
 */
public interface ConnectivityQueryCommandResponse<T extends ConnectivityQueryCommandResponse<T>> extends
        ConnectivityCommandResponse<T>, WithEntity<T> {

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

}
