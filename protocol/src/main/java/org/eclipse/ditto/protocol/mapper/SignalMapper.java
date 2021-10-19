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
package org.eclipse.ditto.protocol.mapper;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.TopicPath;

/**
 * Implementations of this interface handle different type of signals (e.g. thing or policy signal).
 *
 * @param <T> the type of the source signal
 */
public interface SignalMapper<T extends Signal<?>> {

    /**
     * Is called during the mapping from a signal to an {@link Adaptable}.
     *
     * @param signal the source {@link Signal} from which to map an {@link Adaptable}
     * @param channel the channel used to send the signal.
     * @return an {@link Adaptable}
     */
    Adaptable mapSignalToAdaptable(T signal, TopicPath.Channel channel);

    /**
     * Maps the provided signal {@code t} to its Ditto Protocol topic path.
     *
     * @param signal the source {@link Signal} from which to map the topic path.
     * @param channel the channel used to send the signal.
     * @return the corresponding topic path.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 2.2.0
     */
    TopicPath mapSignalToTopicPath(T signal, TopicPath.Channel channel);
}
