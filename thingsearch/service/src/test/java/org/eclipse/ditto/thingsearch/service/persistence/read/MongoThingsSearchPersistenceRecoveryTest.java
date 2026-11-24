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
package org.eclipse.ditto.thingsearch.service.persistence.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.assertj.core.api.SoftAssertions;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.DocumentCodec;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.persistence.api.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.persistence.api.model.Metadata;
import org.eclipse.ditto.thingsearch.persistence.api.model.SearchIndexDocument;
import org.eclipse.ditto.thingsearch.persistence.api.model.ThingDeleteModel;
import org.eclipse.ditto.thingsearch.persistence.api.model.ThingWriteModel;
import org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants;
import org.eclipse.ditto.thingsearch.service.persistence.write.mapping.SearchIndexDocumentMongoEncoder;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Tests {@link MongoThingsSearchPersistence#documentToWriteModel(Document)}, which recovers the last write model of a
 * thing from its stored search index document.
 */
public final class MongoThingsSearchPersistenceRecoveryTest {

    private static final ThingId THING_ID = ThingId.of("thing:recovery");

    private static final ThingDeleteModel NOT_INDEXED = ThingDeleteModel.of(Metadata.ofDeleted(THING_ID));

    @Test
    public void completeDocumentIsRecoveredAsThingWriteModel() {
        final BsonDocument bson = storedBson();

        final AbstractWriteModel result = MongoThingsSearchPersistence.documentToWriteModel(toDocument(bson));

        assertThat(result).isInstanceOf(ThingWriteModel.class);
        assertThat(SearchIndexDocumentMongoEncoder.encode(((ThingWriteModel) result).getDocument())).isEqualTo(bson);
    }

    @Test
    public void recoveredDocumentCarriesTheStoredBson() {
        final AbstractWriteModel result = MongoThingsSearchPersistence.documentToWriteModel(toDocument(storedBson()));

        assertThat(result).isInstanceOfSatisfying(ThingWriteModel.class, recovered ->
                assertThat(recovered.getDocument().encoded(BsonDocument.class, d -> {
                    throw new AssertionError("recovered document encoded again");
                })).isEqualTo(storedBson()));
    }

    @Test
    public void documentWithoutReferencedPoliciesIsRecoveredAsEmptiedOut() {
        final BsonDocument bson = storedBson();
        bson.remove(PersistenceConstants.FIELD_REFERENCED_POLICIES);

        final AbstractWriteModel result = MongoThingsSearchPersistence.documentToWriteModel(toDocument(bson));

        assertThat(result).isInstanceOf(ThingWriteModel.class);
        assertThat(((ThingWriteModel) result).isEmptiedOut()).isTrue();
    }

    @Test
    public void documentWithAMissingFieldIsRecoveredAsNotIndexed() {
        final List<String> fields = List.of(PersistenceConstants.FIELD_NAMESPACE,
                PersistenceConstants.FIELD_GLOBAL_READ,
                PersistenceConstants.FIELD_REVISION,
                PersistenceConstants.FIELD_POLICY_ID,
                PersistenceConstants.FIELD_POLICY_REVISION,
                PersistenceConstants.FIELD_THING,
                PersistenceConstants.FIELD_POLICY,
                PersistenceConstants.FIELD_F_ARRAY);
        final SoftAssertions softly = new SoftAssertions();
        for (final String field : fields) {
            final BsonDocument bson = storedBson();
            bson.remove(field);
            softly.assertThatCode(() -> softly.assertThat(
                            MongoThingsSearchPersistence.documentToWriteModel(toDocument(bson)))
                    .as(field)
                    .isEqualTo(NOT_INDEXED))
                    .as(field)
                    .doesNotThrowAnyException();
        }
        softly.assertAll();
    }

    @Test
    public void documentWithAFieldOfTheWrongTypeIsRecoveredAsNotIndexed() {
        final Map<String, BsonValue> wrongTypes = Map.of(
                PersistenceConstants.FIELD_F_ARRAY, new BsonString("features"),
                PersistenceConstants.FIELD_GLOBAL_READ, new BsonArray(List.of(new BsonInt32(1))),
                PersistenceConstants.FIELD_POLICY, new BsonString("policy"));
        final SoftAssertions softly = new SoftAssertions();
        wrongTypes.forEach((field, value) -> {
            final BsonDocument bson = storedBson();
            bson.put(field, value);
            softly.assertThatCode(() -> softly.assertThat(
                            MongoThingsSearchPersistence.documentToWriteModel(toDocument(bson)))
                    .as(field)
                    .isEqualTo(NOT_INDEXED))
                    .as(field)
                    .doesNotThrowAnyException();
        });
        softly.assertAll();
    }

    @Test
    public void documentWithAWrongTypedMetadataFieldFailsRecoveryAsOnMaster() {
        final BsonDocument bson = storedBson();
        bson.put(PersistenceConstants.FIELD_REVISION, new BsonString("7"));
        final Document document = toDocument(bson);

        assertThatThrownBy(() -> MongoThingsSearchPersistence.documentToWriteModel(document))
                .isInstanceOf(ClassCastException.class);
    }

    @Test
    public void undecodableDocumentIsLoggedAtWarnWithThingIdAndCause() {
        final BsonDocument bson = storedBson();
        bson.put(PersistenceConstants.FIELD_F_ARRAY, new BsonString("features"));
        final Document document = toDocument(bson);
        final Logger logger = (Logger) LoggerFactory.getLogger(MongoThingsSearchPersistence.class.getName());
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MongoThingsSearchPersistence.documentToWriteModel(document);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertThat(appender.list)
                .filteredOn(event -> event.getLevel() == Level.WARN)
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage())
                        .contains("<" + THING_ID + ">")
                        .contains("BsonInvalidOperationException")
                        .doesNotContain("{}"));
    }

    private static BsonDocument storedBson() {
        final PolicyId policyId = PolicyId.of("policy:recovery");
        final SearchIndexDocument document = SearchIndexDocument.newBuilder(THING_ID)
                .namespace(THING_ID.getNamespace())
                .revision(7L)
                .policyId(policyId)
                .policyRevision(3L)
                .globalRead(List.of("integration:reader", "user:alice"))
                .referencedPolicies(Set.of(PolicyTag.of(policyId, 3L)))
                .thing(JsonObject.newBuilder()
                        .set("thingId", THING_ID.toString())
                        .set(PersistenceConstants.FIELD_MODIFIED, "2026-11-24T10:15:30Z")
                        .build())
                .policyAuth(JsonObject.newBuilder().set("thingId", JsonObject.empty()).build())
                .features(List.of(SearchIndexDocument.FeatureEntry.of("lamp",
                        JsonObject.newBuilder().set("properties", JsonObject.newBuilder().set("on", true).build())
                                .build(),
                        JsonObject.empty())))
                .build();
        return SearchIndexDocumentMongoEncoder.encode(document).clone();
    }

    private static Document toDocument(final BsonDocument bson) {
        return new DocumentCodec().decode(new BsonDocumentReader(bson), DecoderContext.builder().build());
    }

}
