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
import java.util.function.Function;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.services.models.streaming.BatchedEntityIdWithRevisions;
import org.eclipse.ditto.services.utils.cluster.AbstractGlobalMappingStrategies;
import org.eclipse.ditto.services.utils.cluster.MappingStrategiesBuilder;
import org.eclipse.ditto.services.utils.cluster.MappingStrategy;

/**
 * {@link org.eclipse.ditto.services.utils.cluster.MappingStrategies} for the Policies service containing all {@link Jsonifiable} types known to Policies.
 */
public final class PoliciesMappingStrategies extends AbstractGlobalMappingStrategies {

    public PoliciesMappingStrategies() {
        super(getPoliciesMappingStrategies());
    }

    private static Map<String, MappingStrategy> getPoliciesMappingStrategies() {
        return MappingStrategiesBuilder.newInstance()
                .add(Policy.class, (Function<JsonObject, Jsonifiable<?>>) PoliciesModelFactory::newPolicy)
                .add(PolicyTag.class, jsonObject -> PolicyTag.fromJson(jsonObject))  // do not replace with lambda!
                .add(BatchedEntityIdWithRevisions.typeOf(PolicyTag.class),
                        BatchedEntityIdWithRevisions.deserializer(jsonObject -> PolicyTag.fromJson(jsonObject)))
                .add(PolicyReferenceTag.class, jsonObject -> PolicyReferenceTag.fromJson(jsonObject))
                .build()
                .getStrategies();
    }
}
