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
package org.eclipse.ditto.protocol.adapter.provider;

import org.eclipse.ditto.protocol.adapter.Adapter;
import org.eclipse.ditto.base.model.signals.commands.ErrorResponse;

/**
 * Interface providing the error response adapter.
 *
 * @param <E> the type of error response
 */
interface ErrorResponseAdapterProvider<E extends ErrorResponse<?>> {

    /**
     * @return the error response adapter
     */
    Adapter<E> getErrorResponseAdapter();
}
