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
package org.eclipse.ditto.internal.utils.persistence.mongo;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.api.PersistenceBackendBootstrap;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultMongoDbConfig;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;

/**
 * MongoDB implementation of {@link PersistenceBackendBootstrap}: injects the MongoDB URI built from the
 * {@code ditto.mongodb} configuration into the configuration of the Pekko MongoDB persistence plugin.
 */
@Immutable
public final class MongoPersistenceBackendBootstrap implements PersistenceBackendBootstrap {

    /**
     * The name of the MongoDB backend.
     */
    public static final String BACKEND_NAME = "mongodb";

    static final String MONGOURI_CONFIG_PATH = "pekko.contrib.persistence.mongodb.mongo.mongouri";

    private static final String DITTO_MONGODB_CONFIG_PATH = "ditto.mongodb";

    /**
     * Constructs a new {@code MongoPersistenceBackendBootstrap}; called by the {@link java.util.ServiceLoader}.
     */
    public MongoPersistenceBackendBootstrap() {
        super();
    }

    @Override
    public String backendName() {
        return BACKEND_NAME;
    }

    @Override
    public Config adjustRawConfig(final Config rawConfig) {
        checkNotNull(rawConfig, "rawConfig");
        if (!rawConfig.hasPath(DITTO_MONGODB_CONFIG_PATH)) {
            return rawConfig;
        }
        final String mongoDbUri =
                DefaultMongoDbConfig.of(DefaultScopedConfig.dittoScoped(rawConfig)).getMongoDbUri();
        return rawConfig.withValue(MONGOURI_CONFIG_PATH, ConfigValueFactory.fromAnyRef(mongoDbUri));
    }

}
