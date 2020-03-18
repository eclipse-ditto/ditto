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

import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ResourceStatus;
import org.eclipse.ditto.services.models.policies.PoliciesMappingStrategies;
import org.eclipse.ditto.services.models.streaming.BatchedEntityIdWithRevisions;
import org.eclipse.ditto.services.models.things.ThingsMappingStrategies;
import org.eclipse.ditto.services.utils.cluster.MappingStrategies;
import org.eclipse.ditto.services.utils.cluster.MappingStrategiesBuilder;
import org.eclipse.ditto.services.utils.cluster.MappingStrategy;
import org.eclipse.ditto.signals.base.GlobalErrorRegistry;
import org.eclipse.ditto.signals.commands.base.GlobalCommandRegistry;
import org.eclipse.ditto.signals.commands.base.GlobalCommandResponseRegistry;
import org.eclipse.ditto.signals.events.base.GlobalEventRegistry;

/**
 * {@link MappingStrategies} for the Connectivity service containing all {@link Jsonifiable} types known to this
 * service.
 */
@Immutable
public final class ConnectivityMappingStrategies extends MappingStrategies {

    @Nullable private static ConnectivityMappingStrategies instance = null;

    private ConnectivityMappingStrategies(final Map<String, MappingStrategy> strategies) {
        super(strategies);
    }

    /**
     * Constructs a new ConnectivityMappingStrategies object.
     */
    private ConnectivityMappingStrategies() {
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
                .add(Connection.class, jsonObject -> ConnectivityModelFactory.connectionFromJson(jsonObject)) // do not replace with lambda!
                .add("ImmutableConnection", jsonObject -> ConnectivityModelFactory.connectionFromJson(jsonObject)) // do not replace with lambda!
                .add(ResourceStatus.class, jsonObject -> ConnectivityModelFactory.resourceStatusFromJson(jsonObject)) // do not replace with lambda!
                .add("ImmutableResourceStatus", jsonObject -> ConnectivityModelFactory.resourceStatusFromJson(jsonObject)) // do not replace with lambda!
                .add(ConnectionTag.class, jsonObject -> ConnectionTag.fromJson(jsonObject))
                .add(BatchedEntityIdWithRevisions.typeOf(ConnectionTag.class),
                        BatchedEntityIdWithRevisions.deserializer(jsonObject -> ConnectionTag.fromJson(jsonObject)))
                .build();

        final MappingStrategies specialStrategies = MappingStrategiesBuilder.newInstance()
                .add(OutboundSignal.class,
                        jsonObject -> OutboundSignalFactory.outboundSignalFromJson(jsonObject, strategies)) // do not replace with lambda!
                .add("UnmappedOutboundSignal",
                        jsonObject -> OutboundSignalFactory.outboundSignalFromJson(jsonObject, strategies))
                .build();// do not replace with lambda!

        return MappingStrategiesBuilder.newInstance()
                .putAll(strategies)
                .putAll(specialStrategies)
                .build();
    }

}
