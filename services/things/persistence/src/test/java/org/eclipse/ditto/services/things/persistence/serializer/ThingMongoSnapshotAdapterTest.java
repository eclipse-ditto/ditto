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
package org.eclipse.ditto.services.things.persistence.serializer;

import static org.assertj.core.api.Assertions.assertThat;

import org.bson.BSONObject;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.things.TestConstants;
import org.eclipse.ditto.services.utils.persistence.mongo.DittoBsonJson;
import org.junit.Before;
import org.junit.Test;

import com.mongodb.DBObject;

import akka.persistence.SnapshotMetadata;
import akka.persistence.SnapshotOffer;

/**
 * Unit test for {@link org.eclipse.ditto.services.things.persistence.serializer.ThingMongoSnapshotAdapter}.
 */
public final class ThingMongoSnapshotAdapterTest {

    private static final String PERSISTENCE_ID = "thing:fajofj904q2";
    private static final SnapshotMetadata SNAPSHOT_METADATA = new SnapshotMetadata(PERSISTENCE_ID, 0, 0);

    private ThingMongoSnapshotAdapter underTest = null;

    @Before
    public void setUp() {
        underTest = new ThingMongoSnapshotAdapter();
    }

    @Test
    public void toSnapshotStoreReturnsExpected() {
        final SnapshotTag snapshotTag = SnapshotTag.PROTECTED;

        final ThingWithSnapshotTag thingWithSnapshotTag =
                ThingWithSnapshotTag.newInstance(TestConstants.Thing.THING_V1, snapshotTag);

        final Object rawSnapshotEntity = underTest.toSnapshotStore(thingWithSnapshotTag);

        assertThat(rawSnapshotEntity).isInstanceOf(DBObject.class);

        final BSONObject dbObject = (BSONObject) rawSnapshotEntity;

        assertThat(dbObject.get(ThingMongoSnapshotAdapter.TAG_JSON_KEY)).isEqualTo(snapshotTag.toString());
    }

    @Test
    public void restoreThingFromSnapshotOfferReturnsExpected() {
        final SnapshotTag snapshotTag = SnapshotTag.PROTECTED;

        final ThingWithSnapshotTag thingWithSnapshotTag =
                ThingWithSnapshotTag.newInstance(TestConstants.Thing.THING_V1, snapshotTag);
        final JsonObject json = thingWithSnapshotTag.toJson(thingWithSnapshotTag.getImplementedSchemaVersion(),
                FieldType.regularOrSpecial());
        final DittoBsonJson dittoBsonJson = DittoBsonJson.getInstance();
        final DBObject snapshotEntity = dittoBsonJson.parse(json);
        snapshotEntity.put(ThingMongoSnapshotAdapter.TAG_JSON_KEY, snapshotTag.toString());

        final SnapshotOffer snapshotOffer = new SnapshotOffer(SNAPSHOT_METADATA, snapshotEntity);

        final ThingWithSnapshotTag restoredThingWithSnapshotTag = underTest.fromSnapshotStore(snapshotOffer);

        assertThat(restoredThingWithSnapshotTag).isEqualTo(thingWithSnapshotTag);
    }

}