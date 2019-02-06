/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.models.connectivity;

import java.util.Set;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.utils.cluster.MappingStrategy;
import org.eclipse.ditto.signals.base.Signal;

/**
 * Creates instances of {@link OutboundSignal}.
 */
public final class OutboundSignalFactory {

    private OutboundSignalFactory() {
        throw new AssertionError();
    }

    /**
     * Creates a OutboundSignal containing a Signal and a Set of Targets where the Signal should be delivered to.
     *
     * @param signal the Signal to be delivered.
     * @param targets the Set of Targets where the Signal should be delivered to.
     * @return the created OutboundSignal.
     */
    public static OutboundSignal newOutboundSignal(final Signal<?> signal, final Set<Target> targets) {
        return new UnmappedOutboundSignal(signal, targets);
    }

    /**
     * Creates a OutboundSignal wrapping an existing {@code outboundSignal} which also is aware of the {@link
     * ExternalMessage} that was mapped from the outbound signal.
     *
     * @param outboundSignal the OutboundSignal to wrap.
     * @param externalMessage the mapped ExternalMessage.
     * @return the created OutboundSignal which is aware of the ExternalMessage
     */
    public static OutboundSignal.WithExternalMessage newMappedOutboundSignal(final OutboundSignal outboundSignal,
            final ExternalMessage externalMessage) {
        return new MappedOutboundSignal(outboundSignal, externalMessage);
    }

    /**
     * Returns an immutable {@link OutboundSignal} based on the given JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the OutboundSignal to be created.
     * @param mappingStrategy the {@link MappingStrategy} to use in order to parse the in the JSON included
     * {@code source} Signal
     * @return a new OutboundSignal which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws NullPointerException if {@code mappingStrategy} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static OutboundSignal outboundSignalFromJson(final JsonObject jsonObject,
            final MappingStrategy mappingStrategy) {
        return UnmappedOutboundSignal.fromJson(jsonObject, mappingStrategy);
    }
}
