/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.thingsearch.persistence.write.streaming;

import java.util.List;

import org.eclipse.ditto.services.thingsearch.persistence.write.model.AbstractWriteModel;

import akka.actor.ActorSystem;

/**
 * Default {@code SearchUpdateListener} for custom search update processing.
 */
public final class DefaultSearchUpdateListener extends SearchUpdateListener{

    /**
     * Instantiate this provider. Called by reflection.
     */
    protected DefaultSearchUpdateListener(final ActorSystem actorSystem) {
        super(actorSystem);
        // Nothing to initialize.
    }

    @Override
    public void processWriteModels(final List<AbstractWriteModel> writeModels) {
        // do nothing
    }
}
