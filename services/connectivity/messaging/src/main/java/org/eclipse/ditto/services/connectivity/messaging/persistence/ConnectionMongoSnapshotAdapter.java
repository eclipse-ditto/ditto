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
package org.eclipse.ditto.services.connectivity.messaging.persistence;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.connectivity.Connection;
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
    protected Connection createJsonifiableFrom(final JsonObject jsonObject) {
        return ConnectionMigrationUtil.connectionFromJsonWithMigration(jsonObject);
    }

}
