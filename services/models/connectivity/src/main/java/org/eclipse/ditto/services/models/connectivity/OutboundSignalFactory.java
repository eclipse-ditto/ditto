/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.models.connectivity;

import java.util.List;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.connectivity.PayloadMapping;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.services.utils.cluster.MappingStrategies;
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
    public static OutboundSignal newOutboundSignal(final Signal<?> signal, final List<Target> targets) {
        return new UnmappedOutboundSignal(signal, targets);
    }

    /**
     * Creates a OutboundSignal wrapping an existing {@code outboundSignal} which also is aware of the
     * {@link ExternalMessage} that was mapped from the outbound signal.
     *
     * @param outboundSignal the OutboundSignal to wrap.
     * @param adaptable the Ditto protocol message adapted from the outbound signal by the protocol adapter.
     * @param externalMessage the mapped ExternalMessage.
     * @return the created OutboundSignal which is aware of the ExternalMessage
     */
    public static OutboundSignal.Mapped newMappedOutboundSignal(final OutboundSignal outboundSignal,
            final Adaptable adaptable, final ExternalMessage externalMessage) {

        return new MappedOutboundSignal(outboundSignal, adaptable, externalMessage);
    }

    /**
     * Creates a OutboundSignal wrapping an existing {@code outboundSignal} that has the same {@link PayloadMapping}
     * for all targets.
     *
     * @param signal the original signal
     * @param payloadMapping the payload mapping common to all targets
     * @return the created OutboundSignal with the {@link PayloadMapping} that will be used to map the signal
     */
    public static OutboundSignal.Mappable newMappableOutboundSignal(final Signal<?> signal, final List<Target> targets,
            final PayloadMapping payloadMapping) {

        return new MappableOutboundSignal(signal, targets, payloadMapping);
    }

    /**
     * Returns an immutable {@link OutboundSignal} based on the given JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the OutboundSignal to be created.
     * @param mappingStrategies the mapping strategies to use in order to parse the in the JSON included {@code source}
     * Signal.
     * @return a new OutboundSignal which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static OutboundSignal outboundSignalFromJson(final JsonObject jsonObject,
            final MappingStrategies mappingStrategies) {

        return UnmappedOutboundSignal.fromJson(jsonObject, mappingStrategies);
    }

}
