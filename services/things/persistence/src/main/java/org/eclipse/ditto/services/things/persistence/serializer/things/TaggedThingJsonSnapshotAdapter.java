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
package org.eclipse.ditto.services.things.persistence.serializer.things;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;

import akka.actor.ActorSystem;

/**
 * This adapter writes a Thing's JSON as snapshot but it extends the Thing's JSON with a tag field first. The key for
 * the field is {@value #TAG_JSON_KEY}. This tag field is used for snapshot management. If a Thing loaded from
 * persistence does not have this JSON field its tag is assumed to be {@link SnapshotTag#UNPROTECTED}.
 */
@NotThreadSafe
public final class TaggedThingJsonSnapshotAdapter extends MongoThingSnapshotAdapter<ThingWithSnapshotTag> {

    /**
     * JSON key for the snapshot tag.
     */
    public static final String TAG_JSON_KEY = "__snapshotTag";

    /**
     * Constructs a new {@code TaggedThingJsonSnapshotAdapter} object.
     *
     * @param system if specified, provides the logger which is used if adapting the SnapshotOffer to a Thing failed.
     */
    public TaggedThingJsonSnapshotAdapter(@Nullable final ActorSystem system) {
        super(system);
    }

    @Nonnull
    @Override
    protected JsonObject convertToJson(@Nonnull final ThingWithSnapshotTag snapshotEntity) {
        final JsonObject jsonObject = super.convertToJson(snapshotEntity);
        final SnapshotTag snapshotTag = snapshotEntity.getSnapshotTag();
        return jsonObject.setValue(TAG_JSON_KEY, snapshotTag.name());
    }

    @Nullable
    @Override
    protected ThingWithSnapshotTag createThingFrom(@Nonnull final JsonObject jsonObject) {
        final Thing thing = ThingsModelFactory.newThing(jsonObject);
        final SnapshotTag snapshotTag = jsonObject.getValue(TAG_JSON_KEY)
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .flatMap(SnapshotTag::getValueFor)
                .orElse(SnapshotTag.UNPROTECTED);
        return ThingWithSnapshotTag.newInstance(thing, snapshotTag);
    }

}
