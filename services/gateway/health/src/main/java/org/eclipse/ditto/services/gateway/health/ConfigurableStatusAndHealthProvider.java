/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.gateway.health;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.services.utils.health.StatusInfo;
import org.eclipse.ditto.services.utils.health.status.StatusHealthSupplier;
import org.eclipse.ditto.services.utils.health.status.StatusSupplier;

/**
 * Implementation of {@link StatusAndHealthProvider} which is configurable by means of a {@link StatusSupplier} and
 * {@link StatusHealthSupplier}.
 */
public final class ConfigurableStatusAndHealthProvider implements StatusAndHealthProvider {

    private final StatusSupplier statusSupplier;
    private final StatusHealthSupplier healthSupplier;

    private ConfigurableStatusAndHealthProvider(final StatusSupplier statusSupplier,
            final StatusHealthSupplier healthSupplier) {

        this.statusSupplier = statusSupplier;
        this.healthSupplier = healthSupplier;
    }

    /**
     * Returns a new {@link ConfigurableStatusAndHealthProvider} for the given params.
     *
     * @param statusSupplier supplies the status information.
     * @param healthSupplier supplies the health information.
     *
     * @return the StatusHealthHelper.
     */
    public static StatusAndHealthProvider of(final StatusSupplier statusSupplier,
            final StatusHealthSupplier healthSupplier) {
        requireNonNull(statusSupplier);
        requireNonNull(healthSupplier);

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
