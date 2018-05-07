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
package org.eclipse.ditto.services.connectivity.messaging.persistence;

import javax.annotation.Nonnull;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.services.utils.persistence.mongo.AbstractMongoSnapshotAdapter;
import org.slf4j.LoggerFactory;

/**
 * SnapshotAdapter for {@link String}s persisted to/from MongoDB.
 */
public final class ConnectionMongoSnapshotAdapter extends AbstractMongoSnapshotAdapter<Connection> {

    public ConnectionMongoSnapshotAdapter() {
        super(LoggerFactory.getLogger(ConnectionMongoSnapshotAdapter.class));
    }

    @Override
    protected Connection createJsonifiableFrom(@Nonnull final JsonObject jsonObject) {
        return ConnectivityModelFactory.connectionFromJson(jsonObject);
    }

}
