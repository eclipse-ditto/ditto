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
package org.eclipse.ditto.protocol.adapter;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.TopicPath;

/**
 * Resolves the matching {@link Adapter} for the given {@link org.eclipse.ditto.protocol.Adaptable}.
 */
interface AdapterResolver {

    /**
     * Select the correct {@link Adapter} (e.g. things/policy, query/modify/...) for the given {@link Adaptable}.
     *
     * @param adaptable the adaptable that is converted to a {@link Signal}.
     * @return the appropriate {@link Adaptable} capable of converting the {@link Adaptable} to a {@link Signal}
     */
    Adapter<? extends Signal<?>> getAdapter(Adaptable adaptable);

    /**
     * Select the correct {@link Adapter} (e.g. things/policy, query/modify/...) for the given {@link Signal}.
     *
     * @param signal the signal that should be converted via the returned {@link Adapter}.
     * @param channel the channel to retrieve the adapter for.
     * @return the appropriate {@link Adaptable} capable of converting the passed {@link Signal}
     * @since 2.2.0
     */
     Adapter<Signal<?>> getAdapter(Signal<?> signal, TopicPath.Channel channel);
}
