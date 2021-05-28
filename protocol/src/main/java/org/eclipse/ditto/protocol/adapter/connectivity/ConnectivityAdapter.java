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

import java.util.EnumSet;
import java.util.Set;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.Adapter;

/**
 * Mixin for connectivity adapters.
 *
 * @since 2.1.0
 */
interface ConnectivityAdapter<T extends Signal<?>> extends Adapter<T> {

    @Override
    default Set<TopicPath.Group> getGroups() {
        return EnumSet.of(TopicPath.Group.CONNECTIONS);
    }

    @Override
    default Set<TopicPath.Channel> getChannels() {
        return EnumSet.of(TopicPath.Channel.NONE);
    }

}
