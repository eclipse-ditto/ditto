/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
import org.eclipse.ditto.thingsearch.model.signals.commands.SearchErrorResponse;

/**
 * Interface providing the search error response adapter.
 *
 * @since 2.2.0
 */
interface SearchErrorResponseAdapterProvider {

    /**
     * @return the error response adapter
     */
    Adapter<SearchErrorResponse> getSearchErrorResponseAdapter();
}
