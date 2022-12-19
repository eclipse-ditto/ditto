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
package org.eclipse.ditto.connectivity.api;

import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.base.model.signals.GlobalErrorRegistry;
import org.eclipse.ditto.base.model.signals.JsonParsable;
import org.eclipse.ditto.base.model.signals.commands.GlobalCommandRegistry;
import org.eclipse.ditto.base.model.signals.commands.GlobalCommandResponseRegistry;
import org.eclipse.ditto.base.model.signals.events.GlobalEventRegistry;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.ResourceStatus;
import org.eclipse.ditto.internal.models.streaming.BatchedEntityIdWithRevisions;
import org.eclipse.ditto.internal.utils.cluster.MappingStrategies;
import org.eclipse.ditto.internal.utils.cluster.MappingStrategiesBuilder;
import org.eclipse.ditto.policies.api.PoliciesMappingStrategies;
import org.eclipse.ditto.things.api.ThingsMappingStrategies;

/**
 * {@link MappingStrategies} for the Connectivity service containing all {@link Jsonifiable} types known to this
 * service.
 */
@Immutable
public final class ConnectivityMappingStrategies extends MappingStrategies {

    @Nullable private static ConnectivityMappingStrategies instance = null;

    private ConnectivityMappingStrategies(final Map<String, JsonParsable<Jsonifiable<?>>> strategies) {
        super(strategies);
    }

    /**
     * Constructs a new ConnectivityMappingStrategies object.
     */
    @SuppressWarnings("unused") // used via reflection
    public ConnectivityMappingStrategies() {
        this(getConnectivityMappingStrategies());
    }

    /**
     * Returns an instance of ConnectivityMappingStrategies.
     *
     * @return the instance.
     */
    public static ConnectivityMappingStrategies getInstance() {
        ConnectivityMappingStrategies result = instance;
        if (null == result) {
            result = new ConnectivityMappingStrategies(getConnectivityMappingStrategies());
            instance = result;
        }
        return result;
    }

    private static MappingStrategies getConnectivityMappingStrategies() {
        final MappingStrategies strategies = MappingStrategiesBuilder.newInstance()
                .putAll(ThingsMappingStrategies.getInstance())
                .putAll(PoliciesMappingStrategies.getInstance())
                .add(GlobalCommandRegistry.getInstance())
                .add(GlobalCommandResponseRegistry.getInstance())
                .add(GlobalEventRegistry.getInstance())
                .add(GlobalErrorRegistry.getInstance())
                .add(Connection.class, ConnectivityModelFactory::connectionFromJson)
                .add("ImmutableConnection", ConnectivityModelFactory::connectionFromJson)
                .add("HonoConnection", ConnectivityModelFactory::connectionFromJson)
                .add(ResourceStatus.class, ConnectivityModelFactory::resourceStatusFromJson)
                .add("ImmutableResourceStatus", ConnectivityModelFactory::resourceStatusFromJson)
                .add(ConnectivityStatus.class, ConnectivityStatus::fromJson)
                .add(BaseClientState.class, BaseClientState::fromJson)
                .add(ConnectionTag.class, ConnectionTag::fromJson)
                .add(BatchedEntityIdWithRevisions.typeOf(ConnectionTag.class),
                        BatchedEntityIdWithRevisions.deserializer(ConnectionTag::fromJson))
                .build();

        final MappingStrategies specialStrategies = MappingStrategiesBuilder.newInstance()
                .add(OutboundSignal.class,
                        jsonObject -> OutboundSignalFactory.outboundSignalFromJson(jsonObject, strategies))
                .add(InboundSignal.class, jsonObject -> InboundSignal.fromJson(jsonObject, strategies))
                .add("UnmappedOutboundSignal",
                        jsonObject -> OutboundSignalFactory.outboundSignalFromJson(jsonObject, strategies))
                .build();

        return MappingStrategiesBuilder.newInstance()
                .putAll(strategies)
                .putAll(specialStrategies)
                .build();
    }

}
