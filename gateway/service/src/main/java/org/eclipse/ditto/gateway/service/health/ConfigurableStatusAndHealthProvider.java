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
package org.eclipse.ditto.gateway.service.health;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.internal.utils.health.StatusInfo;
import org.eclipse.ditto.internal.utils.health.status.StatusHealthSupplier;
import org.eclipse.ditto.internal.utils.health.status.StatusSupplier;

/**
 * Implementation of {@link StatusAndHealthProvider} which is configurable by means of a {@link StatusSupplier} and
 * {@link StatusHealthSupplier}.
 */
final class ConfigurableStatusAndHealthProvider implements StatusAndHealthProvider {

    private final StatusSupplier statusSupplier;
    private final StatusHealthSupplier healthSupplier;

    private ConfigurableStatusAndHealthProvider(final StatusSupplier statusSupplier,
            final StatusHealthSupplier healthSupplier) {

        this.statusSupplier = checkNotNull(statusSupplier, "StatusSupplier");
        this.healthSupplier = checkNotNull(healthSupplier, "StatusHealthSupplier");
    }

    /**
     * Returns a new {@code ConfigurableStatusAndHealthProvider} for the given params.
     *
     * @param statusSupplier supplies the status information.
     * @param healthSupplier supplies the health information.
     * @return the StatusHealthHelper.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static StatusAndHealthProvider of(final StatusSupplier statusSupplier,
            final StatusHealthSupplier healthSupplier) {

        return new ConfigurableStatusAndHealthProvider(statusSupplier, healthSupplier);
    }

    @Override
    public CompletionStage<JsonObject> retrieveStatus() {
        return statusSupplier.get();
    }

    @Override
    public CompletionStage<StatusInfo> retrieveHealth() {
        return healthSupplier.get();
    }

}
