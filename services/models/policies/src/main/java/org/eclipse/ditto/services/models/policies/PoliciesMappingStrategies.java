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
package org.eclipse.ditto.services.models.policies;

import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.services.models.streaming.BatchedEntityIdWithRevisions;
import org.eclipse.ditto.services.utils.cluster.GlobalMappingStrategies;
import org.eclipse.ditto.services.utils.cluster.MappingStrategies;
import org.eclipse.ditto.services.utils.cluster.MappingStrategiesBuilder;
import org.eclipse.ditto.services.utils.cluster.MappingStrategy;

/**
 * {@link MappingStrategies} for the Policies service containing all {@link Jsonifiable} types known to Policies.
 */
@Immutable
public final class PoliciesMappingStrategies extends MappingStrategies {

    @Nullable private static PoliciesMappingStrategies instance = null;

    private PoliciesMappingStrategies(final Map<String, MappingStrategy> policiesMappingStrategies) {
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
                .add(PolicyTag.class, jsonObject -> PolicyTag.fromJson(jsonObject))  // do not replace with lambda!
                .add(BatchedEntityIdWithRevisions.typeOf(PolicyTag.class),
                        BatchedEntityIdWithRevisions.deserializer(jsonObject -> PolicyTag.fromJson(jsonObject)))
                .add(PolicyReferenceTag.class, jsonObject -> PolicyReferenceTag.fromJson(jsonObject))
                .putAll(GlobalMappingStrategies.getInstance())
                .build();
    }

}
