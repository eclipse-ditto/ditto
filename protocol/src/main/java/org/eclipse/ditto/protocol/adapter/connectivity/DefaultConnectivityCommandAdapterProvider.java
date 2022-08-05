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

import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.connectivity.model.signals.announcements.ConnectivityAnnouncement;
import org.eclipse.ditto.protocol.adapter.Adapter;

/**
 * Instantiates and provides {@link Adapter}s used to process Connectivity commands, responses and errors and
 * announcements.
 *
 * @since 2.1.0
 */
public final class DefaultConnectivityCommandAdapterProvider implements ConnectivityCommandAdapterProvider {

    private final ConnectivityAnnouncementAdapter announcementAdapter;

    public DefaultConnectivityCommandAdapterProvider(final HeaderTranslator headerTranslator) {
        announcementAdapter = ConnectivityAnnouncementAdapter.of(headerTranslator);
    }

    public Adapter<ConnectivityAnnouncement<?>> getAnnouncementAdapter() {
        return announcementAdapter;
    }

    @Override
    public List<Adapter<?>> getAdapters() {
        return Collections.singletonList(
                announcementAdapter
        );
    }

}
