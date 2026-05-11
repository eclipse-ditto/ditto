/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.timeseries.api;

import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.base.model.signals.JsonParsable;
import org.eclipse.ditto.internal.utils.cluster.GlobalMappingStrategies;
import org.eclipse.ditto.internal.utils.cluster.MappingStrategies;
import org.eclipse.ditto.internal.utils.cluster.MappingStrategiesBuilder;
import org.eclipse.ditto.policies.api.PoliciesMappingStrategies;

/**
 * {@link MappingStrategies} for the Timeseries service.
 * <p>
 * The Timeseries service does not introduce its own bespoke {@link Jsonifiable} entity types — the
 * cross-cluster traffic it cares about (RetrieveTimeseries / RetrieveTimeseriesResponse) is
 * already covered by the {@link GlobalMappingStrategies} via {@code @JsonParsableCommand} /
 * {@code @JsonParsableCommandResponse} annotations.
 * <p>
 * It also pulls in {@link PoliciesMappingStrategies} so that {@code PolicyTag} cache-invalidation
 * messages published by the policies-service on the cluster pub-sub topic
 * {@code policy-invalidate-enforcers} can be deserialized here — without that, the
 * {@code CachingPolicyEnforcerProvider} silently drops invalidation messages and the local
 * policy-enforcer cache never reflects subsequent policy updates (manifest: granting a new
 * permission to an existing policy never takes effect on the timeseries-service until restart).
 * Other Ditto services that use the caching enforcer (things, connectivity, search, gateway)
 * include the same pull for the same reason.
 */
@Immutable
public final class TimeseriesMappingStrategies extends MappingStrategies {

    private static final MappingStrategies TIMESERIES_MAPPING_STRATEGIES =
            buildTimeseriesMappingStrategies();

    @Nullable private static TimeseriesMappingStrategies instance;

    private TimeseriesMappingStrategies(final Map<String, JsonParsable<Jsonifiable<?>>> mappingStrategies) {
        super(mappingStrategies);
    }

    /**
     * Constructs a new {@code TimeseriesMappingStrategies} instance. Used via reflection by
     * {@code org.eclipse.ditto.internal.utils.cluster.MappingStrategies#loadMappingStrategies}.
     */
    @SuppressWarnings("unused")
    public TimeseriesMappingStrategies() {
        this(TIMESERIES_MAPPING_STRATEGIES);
    }

    /**
     * @return the singleton instance.
     */
    public static TimeseriesMappingStrategies getInstance() {
        TimeseriesMappingStrategies result = instance;
        if (null == result) {
            result = new TimeseriesMappingStrategies(TIMESERIES_MAPPING_STRATEGIES);
            instance = result;
        }
        return result;
    }

    private static MappingStrategies buildTimeseriesMappingStrategies() {
        return MappingStrategiesBuilder.newInstance()
                .putAll(GlobalMappingStrategies.getInstance())
                .putAll(PoliciesMappingStrategies.getInstance())
                .build();
    }
}
