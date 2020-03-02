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
import org.eclipse.ditto.signals.commands.base.ErrorResponse;

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
