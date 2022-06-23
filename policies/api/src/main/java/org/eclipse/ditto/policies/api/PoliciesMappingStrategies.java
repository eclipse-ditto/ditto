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
package org.eclipse.ditto.policies.api;

import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.base.model.signals.JsonParsable;
import org.eclipse.ditto.internal.models.streaming.BatchedEntityIdWithRevisions;
import org.eclipse.ditto.internal.utils.cluster.GlobalMappingStrategies;
import org.eclipse.ditto.internal.utils.cluster.MappingStrategies;
import org.eclipse.ditto.internal.utils.cluster.MappingStrategiesBuilder;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;

/**
 * {@link MappingStrategies} for the Policies service containing all {@link Jsonifiable} types known to Policies.
 */
@Immutable
public final class PoliciesMappingStrategies extends MappingStrategies {

    @Nullable private static PoliciesMappingStrategies instance = null;

    private PoliciesMappingStrategies(final Map<String, JsonParsable<Jsonifiable<?>>> policiesMappingStrategies) {
        super(policiesMappingStrategies);
    }

    /**
     * Constructs a new policiesMappingStrategies object.
     */
    @SuppressWarnings("unused") // used via reflection
    public PoliciesMappingStrategies() {
        this(getPoliciesMappingStrategies());
    }

    /**
     * Returns an instance of PoliciesMappingStrategies.
     *
     * @return the instance.
     */
    public static PoliciesMappingStrategies getInstance() {
        PoliciesMappingStrategies result = instance;
        if (null == result) {
            result = new PoliciesMappingStrategies(getPoliciesMappingStrategies());
            instance = result;
        }
        return result;
    }

    private static MappingStrategies getPoliciesMappingStrategies() {
        return MappingStrategiesBuilder.newInstance()
                .add(Policy.class, jsonObject -> PoliciesModelFactory.newPolicy(jsonObject))
                .add(PolicyTag.class, PolicyTag::fromJson)
                .add(BatchedEntityIdWithRevisions.typeOf(PolicyTag.class),
                        BatchedEntityIdWithRevisions.deserializer(PolicyTag::fromJson))
                .putAll(GlobalMappingStrategies.getInstance())
                .build();
    }

}
