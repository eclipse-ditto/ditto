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
package org.eclipse.ditto.connectivity.service.messaging.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.bson.BsonDocument;
import org.eclipse.ditto.connectivity.model.ConnectionLifecycle;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoBsonJson;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

public class ConnectionMongoSnapshotAdapterTest {

    @Test
    public void makeEmptySnapshotsForDeletedConnections() {
        final var underTest = new ConnectionMongoSnapshotAdapter();
        final var deletedSnapshot = (BsonDocument) underTest.toSnapshotStore(
                TestConstants.createConnection().toBuilder().lifecycle(ConnectionLifecycle.DELETED).build());
        final JsonObject snapshotJson = DittoBsonJson.getInstance().serialize(deletedSnapshot);
        assertThat(snapshotJson).containsOnly(underTest.getDeletedLifecycleJsonField());
    }
}
