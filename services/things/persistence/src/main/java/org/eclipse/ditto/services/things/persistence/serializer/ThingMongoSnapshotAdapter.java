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

import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.utils.persistence.mongo.AbstractMongoSnapshotAdapter;
import org.slf4j.LoggerFactory;

/**
 * A {@link org.eclipse.ditto.services.utils.persistence.SnapshotAdapter} for snapshotting a
 * {@link org.eclipse.ditto.model.things.Thing}.
 */
@ThreadSafe
public final class ThingMongoSnapshotAdapter extends AbstractMongoSnapshotAdapter<ThingWithSnapshotTag> {

    /**
     * JSON key for the snapshot tag.
     */
    public static final String TAG_JSON_KEY = "__snapshotTag";

    /**
     * Constructs a new {@code ThingMongoSnapshotAdapter}.
     */
    public ThingMongoSnapshotAdapter() {
        super(LoggerFactory.getLogger(ThingMongoSnapshotAdapter.class));
    }

    @Override
    protected JsonObject convertToJson(final ThingWithSnapshotTag snapshotEntity) {
        final JsonObject jsonObject = super.convertToJson(snapshotEntity);
        final SnapshotTag snapshotTag = snapshotEntity.getSnapshotTag();
        return jsonObject.setValue(TAG_JSON_KEY, snapshotTag.name());
    }

    @Override
    protected ThingWithSnapshotTag createJsonifiableFrom(final JsonObject jsonObject) {
        final Thing thing = ThingsModelFactory.newThing(jsonObject);
        final SnapshotTag snapshotTag = jsonObject.getValue(TAG_JSON_KEY)
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .flatMap(SnapshotTag::getValueFor)
                .orElse(SnapshotTag.UNPROTECTED);
        return ThingWithSnapshotTag.newInstance(thing, snapshotTag);
    }

}
