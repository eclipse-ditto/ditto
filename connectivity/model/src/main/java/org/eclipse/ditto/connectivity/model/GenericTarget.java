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
package org.eclipse.ditto.connectivity.model;

import java.util.Optional;

/**
 * Super type of targets and reply-targets that provides enough information to send 1 message.
 *
 * @since 1.2.0
 */
public interface GenericTarget {

    /**
     * @return the address for an outbound signal.
     */
    String getAddress();

    /**
     * Defines an optional header mapping e.g. to rename, combine etc. headers for an outbound signal. Mapping is
     * applied after payload mapping is applied. The mapping may contain {@code thing:*} and {@code header:*}
     * placeholders.
     *
     * @return the optional header mapping
     */
    HeaderMapping getHeaderMapping();

    /**
     * @return the optional qos value of this target - only applicable for certain {@link ConnectionType}s.
     * @since 2.1.0
     */
    Optional<Integer> getQos();

    /**
     * Create a copy of this object with the target address replaced.
     *
     * @param newAddress new value for the address field.
     * @return a copy of this object retaining the class and all other fields.
     * @since 2.1.0
     */
    GenericTarget withAddress(final String newAddress);
}
