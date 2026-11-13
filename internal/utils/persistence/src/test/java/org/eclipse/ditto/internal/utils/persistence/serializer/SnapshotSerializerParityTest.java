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
package org.eclipse.ditto.internal.utils.persistence.serializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Optional;

import org.bson.BsonValue;
import org.eclipse.ditto.internal.utils.persistence.api.serializer.NeutralSnapshotAdapter;
import org.eclipse.ditto.internal.utils.persistence.api.serializer.SnapshotSerializer;
import org.eclipse.ditto.internal.utils.persistence.mongo.AbstractMongoSnapshotAdapter;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoBsonJson;
import org.eclipse.ditto.internal.utils.persistence.mongo.MongoSnapshotCodec;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingLifecycle;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import org.apache.pekko.persistence.SnapshotMetadata;
import org.apache.pekko.persistence.SnapshotOffer;

/**
 * Pins the contract the two snapshot envelopes share: the per-service {@link SnapshotSerializer} renders a snapshot to
 * one {@link JsonObject}, and only the storage envelope differs per backend — BSON through {@link MongoSnapshotCodec},
 * JSONB text through the PostgreSQL codec in {@code persistence-r2dbc} (whose accepted shapes its own
 * {@code PostgresSnapshotCodecTest} covers). So the JSON text a PostgreSQL snapshot row holds must equal the JSON the
 * BSON envelope carries, both must recover the identical {@link Thing}, and the production composition
 * {@link NeutralSnapshotAdapter} must behave like the legacy MongoDB adapter it replaced.
 */
public final class SnapshotSerializerParityTest {

    /** Concrete Thing snapshot serializer mirroring ThingMongoSnapshotAdapter's pure logic (no pubsub side-effect). */
    private static final class ThingSnapshotSerializer extends AbstractMongoSnapshotAdapter<Thing> {

        private ThingSnapshotSerializer() {
            super(LoggerFactory.getLogger(ThingSnapshotSerializer.class));
        }

        @Override
        protected Thing createJsonifiableFrom(final JsonObject jsonObject) {
            return ThingsModelFactory.newThing(jsonObject);
        }

        @Override
        protected boolean isDeleted(final Thing snapshotEntity) {
            return snapshotEntity.hasLifecycle(ThingLifecycle.DELETED);
        }

        @Override
        protected JsonField getDeletedLifecycleJsonField() {
            final var field = Thing.JsonFields.LIFECYCLE;
            return JsonField.newInstance(field.getPointer().getRoot().orElseThrow(),
                    JsonValue.of(ThingLifecycle.DELETED.name()), field);
        }

        @Override
        protected Optional<JsonField> getRevisionJsonField(final Thing entity) {
            final var field = Thing.JsonFields.REVISION;
            return entity.getRevision().map(revision ->
                    JsonField.newInstance(field.getPointer().getRoot().orElseThrow(),
                            JsonValue.of(revision.toLong())));
        }
    }

    private static Thing sampleThing() {
        return ThingsModelFactory.newThingBuilder()
                .setId(ThingId.of("org.eclipse.ditto", "thing-parity"))
                .setAttribute(JsonFactory.newPointer("manufacturer"), JsonValue.of("ACME"))
                .setAttribute(JsonFactory.newPointer("count"), JsonValue.of(42))
                .setFeature("sensor", ThingsModelFactory.newFeaturePropertiesBuilder()
                        .set("temperature", 21.5)
                        .set("active", true)
                        .build())
                .setRevision(7L)
                .setLifecycle(ThingLifecycle.ACTIVE)
                .build();
    }

    @Test
    public void bsonEnvelopeCarriesTheSerializerJson() {
        final ThingSnapshotSerializer serializer = new ThingSnapshotSerializer();
        final Thing thing = sampleThing();

        // Mongo envelope: domain -> BSON -> JsonObject
        final BsonValue mongoStored = (BsonValue) serializer.toSnapshotStore(thing);
        final JsonObject mongoJson = DittoBsonJson.getInstance().serialize(mongoStored).asObject();

        // Field-level parity through Ditto's canonical JSON rendering. Raw value-type identity is NOT asserted because
        // BSON and JSONB normalize numbers differently; the contract is identical canonical JSON.
        assertThat(mongoJson.toString()).isEqualTo(serializer.toJson(thing).toString());
    }

    @Test
    public void jsonTextAndBsonEnvelopeRecoverTheSameThing() {
        final ThingSnapshotSerializer serializer = new ThingSnapshotSerializer();
        final Thing thing = sampleThing();

        // What a PostgreSQL snapshot row holds: the serializer's JSON rendered as text, parsed back on load.
        final String jsonText = serializer.toJson(thing).toString();
        final Thing fromJsonText = serializer.fromJson(JsonFactory.newObject(jsonText));
        final Thing fromBson = serializer.fromSnapshotStore(snapshotOffer(serializer.toSnapshotStore(thing)));

        assertThat(fromJsonText).isEqualTo(thing);
        assertThat(fromBson).isEqualTo(thing);
    }

    @Test
    public void neutralAdapterWithMongoCodecMatchesTheLegacyMongoAdapter() {
        final ThingSnapshotSerializer serializer = new ThingSnapshotSerializer();
        final NeutralSnapshotAdapter<Thing> neutral =
                new NeutralSnapshotAdapter<>(serializer, MongoSnapshotCodec.INSTANCE);
        final Thing thing = sampleThing();

        final BsonValue viaNeutral = (BsonValue) neutral.toSnapshotStore(thing);
        final BsonValue viaLegacy = (BsonValue) serializer.toSnapshotStore(thing);

        assertThat(viaNeutral).isEqualTo(viaLegacy);
        assertThat(neutral.fromSnapshotStore(snapshotOffer(viaLegacy))).isEqualTo(thing);
    }

    @Test
    public void deletedThingRoundTripsToNullOnBothEnvelopes() {
        final ThingSnapshotSerializer serializer = new ThingSnapshotSerializer();
        final Thing deleted = sampleThing().toBuilder().setLifecycle(ThingLifecycle.DELETED).build();

        final BsonValue mongoStored = (BsonValue) serializer.toSnapshotStore(deleted);
        final JsonObject mongoJson = DittoBsonJson.getInstance().serialize(mongoStored).asObject();
        final String jsonText = serializer.toJson(deleted).toString();

        assertThat(mongoJson.toString()).isEqualTo(jsonText);
        // Both decode a deleted snapshot to null (entity is deleted).
        assertThat(serializer.fromJson(JsonFactory.newObject(jsonText))).isNull();
        assertThat(serializer.fromSnapshotStore(snapshotOffer(mongoStored))).isNull();
    }

    @Test
    public void bsonEnvelopeRejectsJsonTextPayload() {
        // The BSON envelope decodes BsonValue only; decoding JSONB text is the PostgreSQL codec's job. That is why the
        // envelope is a SnapshotCodec composed into NeutralSnapshotAdapter and not a snapshot adapter per backend.
        final ThingSnapshotSerializer serializer = new ThingSnapshotSerializer();
        final String jsonText = serializer.toJson(sampleThing()).toString();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> serializer.fromSnapshotStore(snapshotOffer(jsonText)));
    }

    private static SnapshotOffer snapshotOffer(final Object snapshot) {
        return new SnapshotOffer(new SnapshotMetadata("pid", 7L, 0L), snapshot);
    }

}
