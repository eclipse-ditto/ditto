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

package org.eclipse.ditto.protocol.adapter.connectivity;

import org.eclipse.ditto.connectivity.model.signals.announcements.ConnectivityAnnouncement;
import org.eclipse.ditto.protocol.adapter.Adapter;
import org.eclipse.ditto.protocol.adapter.provider.AdapterProvider;

/**
 * Provider for all connectivity command adapters. This interface mainly defines the generic type arguments and adds
 * default methods for unsupported command types.
 *
 * @since 2.1.0
 */
public interface ConnectivityCommandAdapterProvider extends AdapterProvider {

    /**
     * Retrieve the announcement adapter.
     *
     * @return the announcement adapter.
     */
    Adapter<ConnectivityAnnouncement<?>> getAnnouncementAdapter();

}
