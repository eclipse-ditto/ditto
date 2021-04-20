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

import java.util.List;

import org.eclipse.ditto.protocol.adapter.Adapter;

/**
 * Provider for all available {@link Adapter}s of a certain type (things or policies).
 */
public interface AdapterProvider {

    /**
     * Retrieve all adapters known to this provider.
     *
     * @return the list of adapters.
     */
    List<Adapter<?>> getAdapters();

}
