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
package org.eclipse.ditto.services.thingsearch.persistence.write.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.json.JsonFactory.newValue;
import static org.eclipse.ditto.model.base.auth.AuthorizationModelFactory.newAuthSubject;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.assertj.core.api.Assertions;
import org.bson.BsonDocument;
import org.bson.Document;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.Subjects;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcer;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcers;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.models.policies.Permission;
import org.eclipse.ditto.services.thingsearch.persistence.AbstractThingSearchPersistenceITBase;
import org.eclipse.ditto.services.thingsearch.persistence.write.ThingMetadata;
import org.eclipse.ditto.services.thingsearch.querymodel.query.PolicyRestrictedSearchAggregation;
import org.eclipse.ditto.signals.events.things.AclEntryDeleted;
import org.eclipse.ditto.signals.events.things.AclEntryModified;
import org.eclipse.ditto.signals.events.things.AclModified;
import org.eclipse.ditto.signals.events.things.AttributeCreated;
import org.eclipse.ditto.signals.events.things.AttributeDeleted;
import org.eclipse.ditto.signals.events.things.AttributeModified;
import org.eclipse.ditto.signals.events.things.AttributesCreated;
import org.eclipse.ditto.signals.events.things.AttributesDeleted;
import org.eclipse.ditto.signals.events.things.FeatureCreated;
import org.eclipse.ditto.signals.events.things.FeatureDeleted;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesDeleted;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesModified;
import org.eclipse.ditto.signals.events.things.FeaturePropertyDeleted;
import org.eclipse.ditto.signals.events.things.FeaturePropertyModified;
import org.eclipse.ditto.signals.events.things.FeaturesDeleted;
import org.eclipse.ditto.signals.events.things.FeaturesModified;
import org.eclipse.ditto.signals.events.things.ThingDeleted;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;

import com.mongodb.MongoWriteException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteError;
import com.mongodb.reactivestreams.client.MongoCollection;

import akka.NotUsed;
import akka.stream.javadsl.Source;
import scala.PartialFunction;

/**
 * Tests for search updater persistence.
 */
@RunWith(Enclosed.class)
public final class MongoThingsSearchUpdaterPersistenceIT extends AbstractThingSearchPersistenceITBase {

    private static final String KNOWN_THING_ID = ":myThing1";
    private static final String KEY1 = "key1";
    private static final String KEY2 = "key2";
    private static final String KEY3 = "key3";
    private static final String KEY4 = "key4";
    private static final String KEY5 = "key5";
    private static final String NULL_ATTRIBUTE = "nullAttribute";
    private static final String PROP1 = "prop1";
    private static final String PROP1_EXTENDED = "prop1xyz";
    private static final String PROP2 = "prop2";
    private static final String PROP3 = "prop3";
    private static final String PROP4 = "prop4";
    private static final String PROP5 = "prop5";
    private static final String PROP6 = "prop6";
    private static final String PROP7 = "prop7";
    private static final String PROP8 = "prop8";
    private static final String PROP_VALUE1 = "propval1";
    private static final String PROP_VALUE2 = "propval2";
    private static final Integer PROP_VALUE3 = 42;
    private static final Boolean PROP_VALUE4 = false;
    private static final String PROP_VALUE5 = "propval5";
    private static final String PROP_VALUE6 = "propval6";
    private static final String PROP_VALUE8 = "propval8";
    private static final String DOTTED_PROP = "dotted.property";
    private static final String VALUE1 = "value1";
    private static final Integer VALUE2 = 5;
    private static final Integer NEW_VALUE_2 = 10;
    private static final Boolean VALUE3 = true;
    private static final String VALUE4 = null;
    private static final Double VALUE5 = 123.45;
    private static final String FEATURE_ID1 = "feature1";
    private static final String FEATURE_ID2 = "feature2";
    private static final String FEATURE_WITH_DOTS = "feature.with.dots";
    private static final List<String> DEFAULT_POLICY_SUBJECTS = Collections.singletonList("some:mySid");


    private static abstract class BaseClass extends AbstractThingSearchPersistenceITBase {

        PolicyEnforcer policyEnforcer;

        MongoCollection<Document> writeThingsCollectionSpy;
        MongoCollection<Document> writePoliciesCollectionSpy;

        @Before
        public void setUpBaseStructures() {
            policyEnforcer = PolicyEnforcers.defaultEvaluator(createPolicy1());
            // spy the collections inside the persistence
            spyWriteCollections();
        }

        private void spyWriteCollections() {
            this.writeThingsCollectionSpy = replaceWithSpy(writePersistence, "collection");
            this.writePoliciesCollectionSpy = replaceWithSpy(writePersistence, "policiesCollection");
        }

        private static <T> T replaceWithSpy(final Object object, final String fieldName) {
            try {
                final Field thingCollectionField = object.getClass().getDeclaredField(fieldName);
                thingCollectionField.setAccessible(true);

                final Object spyObj = Mockito.spy(thingCollectionField.get(object));
                @SuppressWarnings("unchecked") final T spy = (T) spyObj;
                thingCollectionField.set(object, spy);

                return spy;
            } catch (final NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * <p>
         * Simulates {@code ThingUpdater.endSyncWithPolicy} from the module {@code search-updater-starter}. For unit
         * tests only.
         */
        void insertBlockingAndResetMocks(final boolean isV2, final Thing thing, final long thingRevision, final long
                policyRevision,
                final PolicyEnforcer policyEnforcer, final Object... mocks) {
            if (isV2) {
                checkNotNull(this.policyEnforcer, "policyEnforcer");
                runBlocking(writePersistence.insertOrUpdate(thing, thingRevision, policyRevision)
                        .flatMapConcat(u -> writePersistence.updatePolicy(thing, policyEnforcer)));
            } else {
                insertOrUpdateThing(thing, thingRevision, policyRevision);
            }

            Mockito.reset(mocks);
        }

    }


    @RunWith(Parameterized.class)
    public static class MongoThingsSearchUpdaterPersistenceParameterizedTests extends BaseClass {

        @Parameterized.Parameter
        public static JsonSchemaVersion apiVersion;

        @Parameterized.Parameters(name = "v{0}")
        public static List<JsonSchemaVersion> apiVersions() {
            return Arrays.asList(JsonSchemaVersion.values());
        }

        private boolean isV2;


        /** */
        @Before
        public void setUp() {
            isV2 = apiVersion.toInt() == JsonSchemaVersion.V_2.toInt();
        }

        /** */
        @Test
        public void insertAndExists() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            insertBlockingAndResetMocks(isV2, thing, 0, 0, policyEnforcer);

            exists(KNOWN_THING_ID);
        }


        /** */
        @Test
        public void insertAndExistsWithDots() {

            final Thing dottedThing = addDottedAttributesFeaturesAndProperties(createThing(KNOWN_THING_ID, VALUE1,
                    isV2));
            insertBlockingAndResetMocks(isV2, dottedThing, 0, 0, policyEnforcer);

            exists(KNOWN_THING_ID);
        }

        private void exists(final String thingId) {
            // verify
            final PolicyRestrictedSearchAggregation aggregation =
                    abf.newBuilder(cf.any()).authorizationSubjects(KNOWN_SUBJECTS_2).build();
            final List<String> foundAll = findAll(aggregation);
            assertThat(foundAll).contains(thingId);
        }


        private static Thing addDottedAttributesFeaturesAndProperties(final Thing thing) {
            return thing
                    .setAttribute("attribute.with.dots", "some.value")
                    .setFeatureProperty("feature.with.dots", "prop.with.dots", "some.more.dots");
        }


        /** */
        @Test
        public void insertWithHigherRevision() {
            insertBlockingAndResetMocks(isV2, createThing(KNOWN_THING_ID, VALUE1, isV2), 2, 0,
                    policyEnforcer);
            insertBlockingAndResetMocks(isV2, createThing(KNOWN_THING_ID, "anotherValue", isV2), 3, 0,
                    policyEnforcer);

            verifyInsertWithHigherRevision();
        }

        private void verifyInsertWithHigherRevision() {
            final PolicyRestrictedSearchAggregation aggregation1 =
                    abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq("anotherValue")))
                            .authorizationSubjects(KNOWN_SUBJECTS_2)
                            .build();

            final PolicyRestrictedSearchAggregation aggregation2 =
                    abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(VALUE1)))
                            .authorizationSubjects(KNOWN_SUBJECTS_2)
                            .build();

            assertThat(findAll(aggregation1)).contains(KNOWN_THING_ID);
            assertThat(findAll(aggregation2)).isEmpty();
        }

        /** */
        @Test
        public void insertWithSameRevision() {
            Assertions.assertThat(
                    runBlockingWithReturn(
                            writePersistence.insertOrUpdate(createThing(KNOWN_THING_ID, VALUE1, isV2), 2, 0)))
                    .isTrue();
            Assertions.assertThat(runBlockingWithReturn(
                    writePersistence.insertOrUpdate(createThing(KNOWN_THING_ID, "anotherValue", isV2), 2, 0)))
                    .isFalse();
        }

        /** */
        @Test
        public void deleteWithSameRevision() {
            // prepare
            insertBlockingAndResetMocks(isV2, createThing(KNOWN_THING_ID, VALUE1, isV2), 2, 0, policyEnforcer);

            // test
            delete(KNOWN_THING_ID, 2);

            final PolicyRestrictedSearchAggregation aggregation =
                    abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(VALUE1)))
                            .authorizationSubjects(KNOWN_SUBJECTS_2)
                            .build();

            // verify
            assertThat(findAll(aggregation)).contains(KNOWN_THING_ID);
        }


        /** */
        @Test
        public void deleteWithHigherRevision() {
            // prepare
            insertBlockingAndResetMocks(isV2, createThing(KNOWN_THING_ID, VALUE1, isV2), 2, 0, policyEnforcer);

            // test
            delete(KNOWN_THING_ID, 3);

            final PolicyRestrictedSearchAggregation aggregation =
                    abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(VALUE1)))
                            .authorizationSubjects(KNOWN_SUBJECTS_2)
                            .build();

            // verify
            assertThat(findAll(aggregation)).isEmpty();
        }


        /** */
        @Test
        public void insertDeleteAndInsertAgain() {
            insertBlockingAndResetMocks(isV2, createThing(KNOWN_THING_ID, VALUE1, isV2), 2, 0, policyEnforcer);
            delete(KNOWN_THING_ID, 3);
            insertBlockingAndResetMocks(isV2, createThing(KNOWN_THING_ID, "anotherValue", isV2), 4, 0, policyEnforcer);

            verifyInsertWithHigherRevision();
        }

        /** */
        @Test
        public void updateAllAttributes() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            insertBlockingAndResetMocks(isV2, thing, 1L, -1L, policyEnforcer, writeThingsCollectionSpy,
                    writePoliciesCollectionSpy);
            verifyUpdateAllAttributes(KNOWN_ATTRIBUTE_1);
        }

        /** */
        @Test
        public void updateAllAttributesWithDots() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            insertBlockingAndResetMocks(isV2, thing, 1L, -1L, policyEnforcer, writeThingsCollectionSpy,
                    writePoliciesCollectionSpy);
            verifyUpdateAllAttributes("some.attribute.with.dot");
        }

        private void verifyUpdateAllAttributes(final String attributeName) {
            final Attributes newAttributes = Attributes.newBuilder().set(attributeName, KNOWN_NEW_VALUE).build();
            final AttributesCreated attributesCreated =
                    AttributesCreated.of(KNOWN_THING_ID, newAttributes, 2L, DittoHeaders.empty());

            final List<ThingEvent> writes = Collections.singletonList(
                    wrapEvent(attributesCreated, apiVersion));

            Assertions.assertThat(
                    runBlockingWithReturn(
                            writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes, policyEnforcer,
                                    2L)))
                    .isTrue();

            verify(writeThingsCollectionSpy).bulkWrite((List) any(), any());
            verifyNoMoreInteractions(writeThingsCollectionSpy);
            if (isV2) {
                // only policies updates for policies stuff
                verify(writePoliciesCollectionSpy).bulkWrite((List) any(), any());
                verifyNoMoreInteractions(writePoliciesCollectionSpy);
            } else {
                verifyNoMoreInteractions(writePoliciesCollectionSpy);
            }


            final PolicyRestrictedSearchAggregation aggregation1 =
                    abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(attributeName), cf.eq(KNOWN_NEW_VALUE)))
                            .authorizationSubjects(KNOWN_SUBJECTS_2)
                            .build();

            final PolicyRestrictedSearchAggregation aggregation2 =
                    abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(VALUE1)))
                            .authorizationSubjects(KNOWN_SUBJECTS_2)
                            .build();

            final Collection<String> result = findAll(aggregation1);
            assertThat(result).contains(KNOWN_THING_ID);

            final Collection<String> result2 = findAll(aggregation2);
            assertThat(result2).isEmpty();
        }


        @Test
        public void addNewSingleSimpleAttribute() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            insertBlockingAndResetMocks(isV2, thing, 1L, -1L, policyEnforcer, writeThingsCollectionSpy,
                    writePoliciesCollectionSpy);

            verifyUpdateSingleExistingSimpleAttribute(KNOWN_ATTRIBUTE_1, KNOWN_NEW_VALUE);
        }

        @Test
        public void addNewSingleSimpleAttributeWithDots() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            insertBlockingAndResetMocks(isV2, thing, 1L, -1L, policyEnforcer, writeThingsCollectionSpy,
                    writePoliciesCollectionSpy);

            verifyUpdateSingleExistingSimpleAttribute("new.attribute", KNOWN_NEW_VALUE);
        }

        @Test
        public void updateSingleExistingSimpleAttribute() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            insertBlockingAndResetMocks(isV2, thing, 1L, -1L, policyEnforcer, writeThingsCollectionSpy,
                    writePoliciesCollectionSpy);

            verifyUpdateSingleExistingSimpleAttribute(KEY2, KNOWN_NEW_VALUE);
        }

        private void verifyUpdateSingleExistingSimpleAttribute(final String key, final String value) {
            final JsonPointer pointer = JsonFactory.newPointer(key);
            final AttributeModified attributeModified =
                    AttributeModified.of(KNOWN_THING_ID, pointer, JsonValue.of(value), 2L, DittoHeaders.empty());

            final List<ThingEvent> writes = Collections.singletonList(
                    wrapEvent(attributeModified, apiVersion));

            Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes,
                    policyEnforcer, 2L)))
                    .isTrue();

            verifyCollectionWrites();

            final PolicyRestrictedSearchAggregation aggregation1 =
                    abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(key), cf.eq(value)))
                            .authorizationSubjects(KNOWN_SUBJECTS_2)
                            .build();
            final Collection<String> result = findAll(aggregation1);
            assertThat(result).contains(KNOWN_THING_ID);
        }

        @Test
        public void updateSimpleAttributeByNonExistingPrefixName() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            insertBlockingAndResetMocks(isV2, thing, 1L, -1L,
                    policyEnforcer, writeThingsCollectionSpy,
                    writePoliciesCollectionSpy);

            final JsonPointer pointer = JsonFactory.newPointer("key");
            final JsonValue value = newValue(NEW_VALUE_2);
            final AttributeCreated attributeCreated =
                    AttributeCreated.of(KNOWN_THING_ID, pointer, value, 2L, DittoHeaders.empty());

            final List<ThingEvent> writes =
                    Collections.singletonList(wrapEvent(attributeCreated, apiVersion));

            Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes,
                    policyEnforcer, 2L)))
                    .isTrue();
            verifyCollectionWrites();

            final PolicyRestrictedSearchAggregation aggregation1 =
                    abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY2), cf.eq(NEW_VALUE_2)))
                            .authorizationSubjects(KNOWN_SUBJECTS_2)
                            .build();

            final Collection<String> result = findAll(aggregation1);
            assertThat(result).isEmpty();
        }

        @Test
        public void addNewSingleComplexAttribute() {
            insertBlockingAndResetMocks(isV2, createThing(KNOWN_THING_ID, VALUE1, isV2), 1L, -1L,
                    policyEnforcer, writeThingsCollectionSpy,
                    writePoliciesCollectionSpy);

            final JsonPointer pointer = JsonFactory.newPointer("new1/new2/new3");
            final JsonObject value =
                    JsonFactory.newObject().setValue("bool1", true).setValue(KNOWN_ATTRIBUTE_1, KNOWN_NEW_VALUE);
            final AttributeModified attributeModified =
                    AttributeModified.of(KNOWN_THING_ID, pointer, value, 2L, DittoHeaders.empty());

            final List<ThingEvent> writes =
                    Collections.singletonList(wrapEvent(attributeModified, apiVersion));

            Assertions.assertThat(
                    runBlockingWithReturn(
                            writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes, policyEnforcer,
                                    2L)))
                    .isTrue();
            verifyCollectionWrites();

            verifyAddNewSingleComplexAttribute();
        }

        private void verifyAddNewSingleComplexAttribute() {
            final PolicyRestrictedSearchAggregation aggregation1 =
                    abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute("new1/new2/new3/bool1"), cf.eq(Boolean.TRUE)))
                            .authorizationSubjects(KNOWN_SUBJECTS_2)
                            .build();

            final Collection<String> result = findAll(aggregation1);
            assertThat(result).contains(KNOWN_THING_ID);

            final PolicyRestrictedSearchAggregation aggregation2 = abf.newBuilder(
                    cf.fieldCriteria(fef.filterByAttribute("new1/new2/new3/" + KNOWN_ATTRIBUTE_1),
                            cf.eq(KNOWN_NEW_VALUE)))
                    .authorizationSubjects(KNOWN_SUBJECTS_2)
                    .build();

            final Collection<String> result2 = findAll(aggregation2);
            assertThat(result2).contains(KNOWN_THING_ID);

            final PolicyRestrictedSearchAggregation aggregation3 =
                    abf.newBuilder(cf.existsCriteria(fef.existsByAttribute("new1/new2")))
                            .authorizationSubjects(KNOWN_SUBJECTS_2)
                            .build();

            final Collection<String> result3 = findAll(aggregation3);
            assertThat(result3).contains(KNOWN_THING_ID);
        }

        @Test
        public void replaceSimpleWithComplexAttribute() {
            insertBlockingAndResetMocks(isV2, createThing(KNOWN_THING_ID, VALUE1, isV2), 1L, -1L,
                    policyEnforcer, writeThingsCollectionSpy,
                    writePoliciesCollectionSpy);

            final JsonObject value = JsonFactory.newObjectBuilder()
                    .set("bool1", true)
                    .set(KNOWN_ATTRIBUTE_1, KNOWN_NEW_VALUE)
                    .build();
            final AttributeModified attributeModified = createAttributeModified(KEY1, value, 2L);

            final List<ThingEvent> writes =
                    Collections.singletonList(wrapEvent(attributeModified, apiVersion));

            Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes,
                    policyEnforcer, 2L)))
                    .isTrue();

            verifyCollectionWrites();

            final PolicyRestrictedSearchAggregation aggregation1 =
                    abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute("key1/bool1"), cf.eq(Boolean.TRUE)))
                            .authorizationSubjects(KNOWN_SUBJECTS_2)
                            .build();

            final Collection<String> result = findAll(aggregation1);
            assertThat(result).contains(KNOWN_THING_ID);

            final PolicyRestrictedSearchAggregation aggregation2 =
                    abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(VALUE1)))
                            .authorizationSubjects(KNOWN_SUBJECTS_2)
                            .build();
            final Collection<String> result2 = findAll(aggregation2);
            assertThat(result2).isEmpty();

            Mockito.reset(writeThingsCollectionSpy, writePoliciesCollectionSpy);

            final List<ThingEvent> writes2 =
                    Collections.singletonList(wrapEvent(createAttributeModified(KEY1, newValue(NEW_VALUE_2), 3L),
                            apiVersion));

            Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes2,
                    policyEnforcer, 3L)))
                    .isTrue();

            verifyCollectionWrites();

            final PolicyRestrictedSearchAggregation aggregation3 =
                    abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(NEW_VALUE_2)))
                            .authorizationSubjects(KNOWN_SUBJECTS_2)
                            .build();

            final Collection<String> result3 = findAll(aggregation3);
            assertThat(result3).contains(KNOWN_THING_ID);
        }


        @Test
        public void deleteSingleAttribute() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            insertBlockingAndResetMocks(isV2, thing, 1L, -1L, policyEnforcer, writeThingsCollectionSpy,
                    writePoliciesCollectionSpy);

            final JsonPointer attributePointer = JsonFactory.newPointer(KEY1);
            final AttributeDeleted attributeDeleted =
                    AttributeDeleted.of(KNOWN_THING_ID, attributePointer, 2L, DittoHeaders.empty());

            final List<ThingEvent> writes =
                    Collections.singletonList(wrapEvent(attributeDeleted, apiVersion));

            Assertions.assertThat(
                    runBlockingWithReturn(writePersistence.executeCombinedWrites(thing.getId().orElse(null), writes,
                            policyEnforcer, 2L)))
                    .isTrue();
            verifyCollectionWrites();

            final PolicyRestrictedSearchAggregation aggregation1 =
                    abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(VALUE1)))
                            .authorizationSubjects(KNOWN_SUBJECTS_2)
                            .build();

            final Collection<String> result = findAll(aggregation1);

            assertThat(result).isEmpty();
        }


        @Test
        public void deleteAllAttributes() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            insertBlockingAndResetMocks(isV2, thing, 1L, -1L, policyEnforcer, writePoliciesCollectionSpy,
                    writeThingsCollectionSpy);

            final List<ThingEvent> writes =
                    Collections.singletonList(wrapEvent(AttributesDeleted.of(KNOWN_THING_ID, 2L,
                            DittoHeaders.empty()), apiVersion));

            Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes,
                    policyEnforcer, 2L)))
                    .isTrue();

            verifyCollectionWrites();

            final PolicyRestrictedSearchAggregation aggregation1 =
                    abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(VALUE1)))
                            .authorizationSubjects(KNOWN_SUBJECTS_2)
                            .build();

            final Collection<String> result = findAll(aggregation1);
            assertThat(result).isEmpty();

            final PolicyRestrictedSearchAggregation aggregation2 =
                    abf.newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP1), cf.eq(PROP_VALUE1)))
                            .authorizationSubjects(KNOWN_SUBJECTS_2)
                            .build();

            final Collection<String> result2 = findAll(aggregation2);
            assertThat(result2).contains(KNOWN_THING_ID);
        }

        @Test
        public void deleteExistingFeature() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            insertBlockingAndResetMocks(isV2, thing, 1L, -1L, policyEnforcer, writeThingsCollectionSpy,
                    writePoliciesCollectionSpy);

            final PolicyRestrictedSearchAggregation aggregation1 =
                    abf.newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP1), cf.eq(PROP_VALUE1)))
                            .authorizationSubjects(KNOWN_SUBJECTS_2)
                            .build();

            final Collection<String> result = findAll(aggregation1);

            assertThat(result).contains(KNOWN_THING_ID);

            final FeatureDeleted featureDeleted =
                    FeatureDeleted.of(KNOWN_THING_ID, FEATURE_ID1, 2L, DittoHeaders.empty());

            final List<ThingEvent> writes = Collections.singletonList(wrapEvent(featureDeleted, apiVersion));

            Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes,
                    policyEnforcer, 2L)))
                    .isTrue();
            verifyCollectionWrites();

            assertThat(findAll(aggregation1)).isEmpty();

            final PolicyRestrictedSearchAggregation aggregation2 =
                    abf.newBuilder(cf.existsCriteria(fef.existsByFeatureId(FEATURE_ID1)))
                            .authorizationSubjects(KNOWN_SUBJECTS_2)
                            .build();

            final Collection<String> result2 = findAll(aggregation2);
            assertThat(result2).isEmpty();
        }

        private void verifyCollectionWrites() {
            verify(writeThingsCollectionSpy).bulkWrite((List) any(), any());
            verifyNoMoreInteractions(writeThingsCollectionSpy);
            if (isV2) {
                verify(writePoliciesCollectionSpy).bulkWrite((List) any(), any());
            }
            verifyNoMoreInteractions(writePoliciesCollectionSpy);
        }


        @Test
        public void addNewFeature() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            insertBlockingAndResetMocks(isV2, thing, 1L, -1L, policyEnforcer, writeThingsCollectionSpy,
                    writePoliciesCollectionSpy);

            final PolicyRestrictedSearchAggregation aggregation1 =
                    abf.newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP5), cf.eq(PROP_VALUE5)))
                            .authorizationSubjects(KNOWN_SUBJECTS_2)
                            .build();
            final Collection<String> result = findAll(aggregation1);
            assertThat(result).isEmpty();

            final List<ThingEvent> writes =
                    Collections.singletonList(wrapEvent(createFeatureCreated(FEATURE_ID2), apiVersion));

            Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes,
                    policyEnforcer, 1L)))
                    .isTrue();
            verifyCollectionWrites();
            assertThat(findAll(aggregation1)).contains(KNOWN_THING_ID);

            final PolicyRestrictedSearchAggregation aggregation2 =
                    abf.newBuilder(cf.existsCriteria(fef.existsByFeatureId(FEATURE_ID2)))
                            .authorizationSubjects(KNOWN_SUBJECTS_2)
                            .build();

            assertThat(findAll(aggregation2)).contains(KNOWN_THING_ID);
        }


        @Test
        public void createThingWithNullAttribute() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            final Thing thingWithNulLAttribute = thing.setAttribute(NULL_ATTRIBUTE, JsonFactory.nullLiteral());
            insertBlockingAndResetMocks(isV2, thingWithNulLAttribute, 0, 0, policyEnforcer);
            final PolicyRestrictedSearchAggregation aggregation =
                    abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(NULL_ATTRIBUTE), cf.eq(null)))
                            .authorizationSubjects(KNOWN_SUBJECTS_2)
                            .build();

            assertThat(findAll(aggregation)).contains(KNOWN_THING_ID);
        }


        @Test
        public void updateExistingFeature() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            insertBlockingAndResetMocks(isV2, thing, 1L, -1L, policyEnforcer, writeThingsCollectionSpy,
                    writePoliciesCollectionSpy);

            verifyCreateFeature(FEATURE_ID1);
        }

        @Test
        public void updateExistingDottedFeatureV1() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            insertBlockingAndResetMocks(isV2, thing, 1L, -1L, policyEnforcer, writeThingsCollectionSpy,
                    writePoliciesCollectionSpy);

            verifyCreateFeature("feature.with.dots");
        }

        private void verifyCreateFeature(final String featureId) {
            final long targetRevision = 1L;
            final List<ThingEvent> writes = Collections.singletonList(
                    wrapEvent(createFeatureCreated(featureId), apiVersion));

            Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID,
                    writes, policyEnforcer, targetRevision)))
                    .isTrue();

            verifyCollectionWrites();

            final PolicyRestrictedSearchAggregation aggregation1 = abf
                    .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP5), cf.eq(PROP_VALUE5)))
                    .authorizationSubjects(KNOWN_SUBJECTS_2)
                    .build();

            final Collection<String> result = findAll(aggregation1);
            assertThat(result).contains(KNOWN_THING_ID);

            final PolicyRestrictedSearchAggregation aggregation2 = abf
                    .newBuilder(cf.existsCriteria(fef.existsByFeatureProperty(PROP5)))
                    .authorizationSubjects(KNOWN_SUBJECTS_2)
                    .build();

            assertThat(findAll(aggregation2)).contains(KNOWN_THING_ID);
        }

        @Test
        public void deletePropertiesOfExistingFeature() {
            insertBlockingAndResetMocks(isV2, createThing(KNOWN_THING_ID, VALUE1, isV2), 1L, -1L, policyEnforcer,
                    writeThingsCollectionSpy, writePoliciesCollectionSpy);

            final List<ThingEvent> writes =
                    Collections.singletonList(wrapEvent(createFeaturePropertiesDeleted(FEATURE_ID1), apiVersion));

            Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID,
                    writes, policyEnforcer, 1L)))
                    .isTrue();

            verifyCollectionWrites();

            final PolicyRestrictedSearchAggregation aggregation1 = abf
                    .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP1), cf.eq(PROP_VALUE1)))
                    .authorizationSubjects(KNOWN_SUBJECTS_2)
                    .build();

            final Collection<String> result = findAll(aggregation1);
            assertThat(result).isEmpty();

            final PolicyRestrictedSearchAggregation aggregation2 = abf
                    .newBuilder(cf.existsCriteria(fef.existsByFeatureId(FEATURE_ID1)))
                    .authorizationSubjects(KNOWN_SUBJECTS_2)
                    .build();

            final Collection<String> result2 = findAll(aggregation2);
            assertThat(result2).contains(KNOWN_THING_ID);
        }

        @Test
        public void updatePropertiesOfExistingFeature() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            insertBlockingAndResetMocks(isV2, thing, 1L, -1L, policyEnforcer, writeThingsCollectionSpy,
                    writePoliciesCollectionSpy);

            final List<ThingEvent> writes = Collections.singletonList(
                    wrapEvent(createFeaturePropertiesModified(FEATURE_ID1), apiVersion));

            Assertions.assertThat(runBlockingWithReturn(
                    writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes, policyEnforcer, 1L)))
                    .isTrue();

            verifyCollectionWrites();

            final PolicyRestrictedSearchAggregation aggregation1 = abf
                    .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP6), cf.eq(PROP_VALUE6)))
                    .authorizationSubjects(KNOWN_SUBJECTS_2)
                    .build();

            final Collection<String> result = findAll(aggregation1);
            assertThat(result).contains(KNOWN_THING_ID);
        }

        @Test
        public void updatePropertiesOfNotExistingFeature() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            insertBlockingAndResetMocks(isV2, thing, 1L, -1L, policyEnforcer, writeThingsCollectionSpy,
                    writePoliciesCollectionSpy);

            final List<ThingEvent> writes = Collections.singletonList(
                    wrapEvent(createFeaturePropertiesModified(FEATURE_ID2), apiVersion));

            Assertions.assertThat(runBlockingWithReturn(
                    writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes, policyEnforcer, 1L)))
                    .isTrue();

            verifyCollectionWrites();

            final PolicyRestrictedSearchAggregation aggregation1 = abf
                    .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP6), cf.eq(PROP_VALUE6)))
                    .authorizationSubjects(KNOWN_SUBJECTS_2)
                    .build();

            final Collection<String> result = findAll(aggregation1);
            assertThat(result).contains(KNOWN_THING_ID);

            final PolicyRestrictedSearchAggregation aggregation2 = abf
                    .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP1), cf.eq(PROP_VALUE1)))
                    .authorizationSubjects(KNOWN_SUBJECTS_2)
                    .build();

            final Collection<String> result2 = findAll(aggregation2);
            assertThat(result2).contains(KNOWN_THING_ID);
        }


        @Test
        public void deleteFeatureProperty() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            insertBlockingAndResetMocks(isV2, thing, 1L, -1L, policyEnforcer, writeThingsCollectionSpy,
                    writePoliciesCollectionSpy);

            final List<ThingEvent> writes = Collections.singletonList(
                    wrapEvent(createFeaturePropertyDeleted(FEATURE_ID1, PROP1, 2L), apiVersion));

            Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID,
                    writes, policyEnforcer, 2L)))
                    .isTrue();

            verifyCollectionWrites();

            final PolicyRestrictedSearchAggregation aggregation1 = abf
                    .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP1), cf.eq(PROP_VALUE1)))
                    .authorizationSubjects(KNOWN_SUBJECTS_2)
                    .build();

            final Collection<String> result = findAll(aggregation1);
            assertThat(result).isEmpty();

            final PolicyRestrictedSearchAggregation aggregation2 = abf
                    .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP1_EXTENDED), cf.eq(PROP_VALUE1)))
                    .authorizationSubjects(KNOWN_SUBJECTS_2)
                    .build();

            final Collection<String> result2 = findAll(aggregation2);
            assertThat(result2).contains(KNOWN_THING_ID);
        }

        @Test
        public void deleteFeaturePropertyWithWrongSourceSequenceNumber() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            insertBlockingAndResetMocks(isV2, thing, 1L, -1L, policyEnforcer, writeThingsCollectionSpy,
                    writePoliciesCollectionSpy);
            final List<ThingEvent> writes = Collections.singletonList(
                    wrapEvent(createFeaturePropertyDeleted(FEATURE_ID1, PROP1, 1L), apiVersion));

            Assertions.assertThat(runBlockingWithReturn(
                    writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes, policyEnforcer, 1L)))
                    .isFalse();
        }


        @Test
        public void updateFeatureProperty() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            insertBlockingAndResetMocks(isV2, thing, 1L, -1L, policyEnforcer, writeThingsCollectionSpy,
                    writePoliciesCollectionSpy);

            final List<ThingEvent> writes = Collections.singletonList(
                    wrapEvent(createFeaturePropertyModified(FEATURE_ID1, PROP1, newValue(true),
                            2L), apiVersion));

            Assertions.assertThat(runBlockingWithReturn(
                    writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes, policyEnforcer, 2L)))
                    .isTrue();

            verifyCollectionWrites();

            final PolicyRestrictedSearchAggregation aggregation1 = abf
                    .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP1), cf.eq(Boolean.TRUE)))
                    .authorizationSubjects(KNOWN_SUBJECTS_2)
                    .build();

            final Collection<String> result = findAll(aggregation1);
            assertThat(result).contains(KNOWN_THING_ID);

            final PolicyRestrictedSearchAggregation aggregation2 = abf
                    .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP1), cf.eq(PROP_VALUE1)))
                    .authorizationSubjects(KNOWN_SUBJECTS_2)
                    .build();

            final Collection<String> result2 = findAll(aggregation2);
            assertThat(result2).isEmpty();
        }


        @Test
        public void updateFeaturePropertyByOverridingComplexProperty() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            insertBlockingAndResetMocks(isV2, thing, 1L, -1L, policyEnforcer, writeThingsCollectionSpy,
                    writePoliciesCollectionSpy);

            final List<ThingEvent> writes = Collections.singletonList(
                    wrapEvent(createFeaturePropertyModified(FEATURE_ID1, PROP7, newValue("simple"), 2L),
                            apiVersion));

            Assertions.assertThat(runBlockingWithReturn(
                    writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes, policyEnforcer, 2L)))
                    .isTrue();

            verifyCollectionWrites();

            final PolicyRestrictedSearchAggregation aggregation1 = abf
                    .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP7), cf.eq("simple")))
                    .authorizationSubjects(KNOWN_SUBJECTS_2)
                    .build();

            final Collection<String> result = findAll(aggregation1);
            assertThat(result).contains(KNOWN_THING_ID);

            final PolicyRestrictedSearchAggregation aggregation2 = abf
                    .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP7 + "/" + PROP8), cf.eq(PROP_VALUE8)))
                    .authorizationSubjects(KNOWN_SUBJECTS_2)
                    .build();

            final Collection<String> result2 = findAll(aggregation2);
            assertThat(result2).isEmpty();
        }

        @Test
        public void deleteFeatures() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            insertBlockingAndResetMocks(isV2, thing, 1L, -1L, policyEnforcer, writeThingsCollectionSpy,
                    writePoliciesCollectionSpy);

            final List<ThingEvent> writes = Collections.singletonList(
                    wrapEvent(FeaturesDeleted.of(KNOWN_THING_ID, 2L, DittoHeaders.empty()),
                            apiVersion));

            Assertions.assertThat(runBlockingWithReturn(
                    writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes, policyEnforcer, 2L)))
                    .isTrue();

            verifyCollectionWrites();

            final PolicyRestrictedSearchAggregation aggregation1 = abf
                    .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP1), cf.eq(PROP_VALUE1)))
                    .authorizationSubjects(KNOWN_SUBJECTS_2)
                    .build();

            final Collection<String> result = findAll(aggregation1);
            assertThat(result).isEmpty();

            final PolicyRestrictedSearchAggregation aggregation2 = abf
                    .newBuilder(cf.existsCriteria(fef.existsByFeatureId(FEATURE_ID1)))
                    .authorizationSubjects(KNOWN_SUBJECTS_2)
                    .build();

            final Collection<String> result2 = findAll(aggregation2);
            assertThat(result2).isEmpty();
        }

        @Test
        public void updateFeatures() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            insertBlockingAndResetMocks(isV2, thing, 1L, -1L, policyEnforcer, writeThingsCollectionSpy,
                    writePoliciesCollectionSpy);
            verifyUpdateFeatures("f3", createFeatures(), 1L);
        }

        @Test
        public void updateFeaturesWithDottedFeatureId() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            insertBlockingAndResetMocks(isV2, thing, 1L, -1L, policyEnforcer, writeThingsCollectionSpy,
                    writePoliciesCollectionSpy);
            verifyUpdateFeatures(FEATURE_WITH_DOTS, createFeaturesWithDottedFeatureId(FEATURE_WITH_DOTS), 1L);
        }

        @Test
        public void updateFeaturesWithDottedPropertyNames() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            insertBlockingAndResetMocks(isV2, thing, 1L, -1L, policyEnforcer, writeThingsCollectionSpy,
                    writePoliciesCollectionSpy);
            verifyUpdateFeatures(FEATURE_WITH_DOTS, createFeaturesWithDottedPropertyNames(FEATURE_WITH_DOTS), 1L);
        }

        @Test
        public void updateFeaturesV2WithDottedPropertyNames() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            insertBlockingAndResetMocks(isV2, thing, 1L, -1L, policyEnforcer, writeThingsCollectionSpy,
                    writePoliciesCollectionSpy);
            verifyUpdateFeatures(FEATURE_WITH_DOTS, createFeaturesWithDottedPropertyNames(FEATURE_WITH_DOTS), 1L);
        }

        private void verifyUpdateFeatures(final String expectedFeatureId, final Features features, final long
                targetRevision) {

            final List<ThingEvent> writes = Collections.singletonList(
                    wrapEvent(createFeaturesModified(features), apiVersion));

            Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID,
                    writes, policyEnforcer, targetRevision)))
                    .isTrue();

            verifyCollectionWrites();

            final PolicyRestrictedSearchAggregation aggregation1 = abf
                    .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP1), cf.eq(PROP_VALUE1)))
                    .authorizationSubjects(KNOWN_SUBJECTS_2)
                    .build();

            final Collection<String> result = findAll(aggregation1);
            assertThat(result).isEmpty();

            final PolicyRestrictedSearchAggregation aggregation2 = abf
                    .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP5), cf.eq(PROP_VALUE5)))
                    .authorizationSubjects(KNOWN_SUBJECTS_2)
                    .build();

            final Collection<String> result2 = findAll(aggregation2);
            assertThat(result2).contains(KNOWN_THING_ID);

            final PolicyRestrictedSearchAggregation aggregation3 = abf
                    .newBuilder(cf.existsCriteria(fef.existsByFeatureId(expectedFeatureId)))
                    .authorizationSubjects(KNOWN_SUBJECTS_2)
                    .build();

            final Collection<String> result3 = findAll(aggregation3);
            assertThat(result3).contains(KNOWN_THING_ID);
        }


        @Test
        public void updateFeaturePropertiesAndOneAttribute() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            insertBlockingAndResetMocks(isV2, thing, 1L, -1L, policyEnforcer, writePoliciesCollectionSpy,
                    writeThingsCollectionSpy);

            final List<ThingEvent> writes = new ArrayList<>();
            writes.add(wrapEvent(createFeaturePropertiesModified(FEATURE_ID1),
                    apiVersion));
            writes.add(wrapEvent(createAttributeModified(PROP1, newValue("s0meattr12"), 3L),
                    apiVersion));

            Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID,
                    writes, policyEnforcer, 3L)))
                    .isTrue();

            verifyCollectionWrites();

            final PolicyRestrictedSearchAggregation aggregation1 = abf
                    .newBuilder(cf.fieldCriteria(fef.filterByAttribute(PROP1), cf.eq("s0meattr12")))
                    .authorizationSubjects(KNOWN_SUBJECTS_2)
                    .build();

            final Collection<String> result1 = findAll(aggregation1);
            assertThat(result1).contains(KNOWN_THING_ID);

            final PolicyRestrictedSearchAggregation aggregation2 = abf
                    .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP6), cf.eq(PROP_VALUE6)))
                    .authorizationSubjects(KNOWN_SUBJECTS_2)
                    .build();

            final Collection<String> result2 = findAll(aggregation2);

            assertThat(result2).contains(KNOWN_THING_ID);
        }


        @Test
        public void updateSeveralFeaturePropertiesAndDeleteThing() {
            insertBlockingAndResetMocks(isV2, createThing(KNOWN_THING_ID, VALUE1, isV2), 1L, -1L, policyEnforcer,
                    writeThingsCollectionSpy, writePoliciesCollectionSpy);

            final List<ThingEvent> writes = new ArrayList<>();
            writes.add(wrapEvent(
                    createAclModified("anotherSid", 2L, org.eclipse.ditto.model.things.Permission.READ),
                    apiVersion));
            writes.add(wrapEvent(
                    createFeaturePropertyModified(FEATURE_ID1, PROP1, newValue("somethingNew"), 3L),
                    apiVersion));
            writes.add(wrapEvent(
                    createFeaturePropertyModified(FEATURE_ID1, PROP1, newValue("somethingNew2"), 4L),
                    apiVersion));
            writes.add(wrapEvent(
                    createFeaturePropertyModified(FEATURE_ID1, PROP1, newValue("somethingNew3"), 5L),
                    apiVersion));
            writes.add(wrapEvent(
                    createFeaturePropertyModified(FEATURE_ID1, PROP1, newValue("somethingNew4"), 6L),
                    apiVersion));
            writes.add(wrapEvent(ThingDeleted.of(KNOWN_THING_ID, 7L, DittoHeaders.empty()),
                    apiVersion));

            Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID,
                    writes, policyEnforcer, 7L)))
                    .isTrue();

            verifyCollectionWrites();

            final PolicyRestrictedSearchAggregation aggregation1 = abf
                    .newBuilder(cf.any())
                    .authorizationSubjects(KNOWN_SUBJECTS_2)
                    .build();

            assertThat(findAll(aggregation1)).isEmpty();
        }


        @Test
        public void delete() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            final long thingRevision = 13;
            final long policyRevision = 77;
            runBlocking(writePersistence.insertOrUpdate(thing, thingRevision, policyRevision));

            final boolean result = runBlockingWithReturn(writePersistence.delete(KNOWN_THING_ID));

            assertThat(result)
                    .isTrue();
        }

    }

    public static class MongoThingsSearchUpdaterPersistenceV1Tests extends BaseClass {

        private final boolean isV2 = false;
        private final JsonSchemaVersion apiVersion = JsonSchemaVersion.V_1;

        @Test
        public void createNewAclEntry() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            insertBlockingAndResetMocks(isV2, thing, 0, 0, policyEnforcer, writePoliciesCollectionSpy,
                    writeThingsCollectionSpy);

            final List<ThingEvent> writes = Collections.singletonList(
                    wrapEvent(
                            createAclModified("anotherSid", 1L, org.eclipse.ditto.model.things.Permission.READ),
                            JsonSchemaVersion.V_1));

            final PolicyRestrictedSearchAggregation aggregation =
                    abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(VALUE1)))
                            .authorizationSubjects(Collections.singletonList("anotherSid")).build();

            Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes,
                    policyEnforcer, 1L)))
                    .isTrue();
            verify(writeThingsCollectionSpy).bulkWrite((List) any(), any());
            verifyNoMoreInteractions(writeThingsCollectionSpy);
            verifyNoMoreInteractions(writePoliciesCollectionSpy);

            assertThat(findAll(aggregation)).contains(KNOWN_THING_ID);
        }


        @Test
        public void updateExistingAclEntryAddRead() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            insertBlockingAndResetMocks(isV2, thing, 0, 0, policyEnforcer, writeThingsCollectionSpy,
                    writePoliciesCollectionSpy);

            final List<ThingEvent> writes = Collections.singletonList(
                    wrapEvent(
                            createAclModified("some:mySid2", 1L, org.eclipse.ditto.model.things.Permission.READ,
                                    org.eclipse.ditto.model.things.Permission.WRITE), JsonSchemaVersion.V_1));

            final PolicyRestrictedSearchAggregation aggregation =
                    abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(VALUE1)))
                            .authorizationSubjects(Collections.singletonList("some:mySid2"))
                            .build();

            Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes,
                    policyEnforcer, 1L)))
                    .isTrue();

            verify(writeThingsCollectionSpy).bulkWrite((List) any(), any());
            verifyNoMoreInteractions(writeThingsCollectionSpy);
            verifyNoMoreInteractions(writePoliciesCollectionSpy);

            assertThat(findAll(aggregation)).isNotNull()
                    .contains(KNOWN_THING_ID);
        }

        @Test
        public void updateExistingAclEntryRemoveRead() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            insertBlockingAndResetMocks(isV2, thing, 0, 0, policyEnforcer, writeThingsCollectionSpy,
                    writePoliciesCollectionSpy);

            final List<ThingEvent> writes = Collections.singletonList(
                    wrapEvent(createAclModified("some:mySid3", 1L,
                            org.eclipse.ditto.model.things.Permission.WRITE), JsonSchemaVersion.V_1));

            final PolicyRestrictedSearchAggregation aggregation =
                    abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(VALUE1)))
                            .authorizationSubjects(Collections.singletonList("some:mySid3"))
                            .build();

            Assertions.assertThat(
                    runBlockingWithReturn(
                            writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes, policyEnforcer,
                                    1L)))
                    .isTrue();

            verify(writeThingsCollectionSpy).bulkWrite((List) any(), any());
            verifyNoMoreInteractions(writeThingsCollectionSpy);
            verifyNoMoreInteractions(writePoliciesCollectionSpy);

            assertThat(findAll(aggregation)).isEmpty();
        }

        @Test
        public void deleteAclEntry() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            insertBlockingAndResetMocks(isV2, thing, 0, 0, policyEnforcer, writeThingsCollectionSpy,
                    writePoliciesCollectionSpy);

            final List<ThingEvent> writes = Collections.singletonList(
                    wrapEvent(
                            AclEntryDeleted.of(KNOWN_THING_ID, AuthorizationSubject.newInstance("some:mySid3"),
                                    1L,
                                    DittoHeaders.empty()), JsonSchemaVersion.V_1));


            final PolicyRestrictedSearchAggregation aggregation =
                    abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(VALUE1)))
                            .authorizationSubjects(Collections.singletonList("some:mySid3"))
                            .build();

            Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes,
                    policyEnforcer, 1L)))
                    .isTrue();

            verify(writeThingsCollectionSpy).bulkWrite((List) any(), any());
            verifyNoMoreInteractions(writeThingsCollectionSpy);
            verifyNoMoreInteractions(writePoliciesCollectionSpy);

            assertThat(findAll(aggregation)).isEmpty();
        }

        /** */
        @Test
        public void updateWholeACL() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            insertBlockingAndResetMocks(isV2, thing, 0, 0, policyEnforcer, writePoliciesCollectionSpy,
                    writeThingsCollectionSpy);

            final List<ThingEvent> writes = Collections.singletonList(
                    wrapEvent(
                            createAclModified("newSid", 1L, org.eclipse.ditto.model.things.Permission.READ,
                                    org.eclipse.ditto.model.things.Permission.WRITE,
                                    org.eclipse.ditto.model.things.Permission.ADMINISTRATE), JsonSchemaVersion.V_1));

            Assertions.assertThat(
                    runBlockingWithReturn(
                            writePersistence.executeCombinedWrites(KNOWN_THING_ID, writes, policyEnforcer,
                                    1L)))
                    .isTrue();

            verify(writeThingsCollectionSpy).bulkWrite((List) any(), any());
            verifyNoMoreInteractions(writeThingsCollectionSpy);
            verifyNoMoreInteractions(writePoliciesCollectionSpy);

            final PolicyRestrictedSearchAggregation aggregation1 =
                    abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(VALUE1)))
                            .authorizationSubjects(KNOWN_SUBJECTS_2)
                            .build();

            final PolicyRestrictedSearchAggregation aggregation2 =
                    abf.newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(VALUE1)))
                            .authorizationSubjects(Collections.singletonList("newSid")).build();

            assertThat(findAll(aggregation1)).isEmpty();
            assertThat(findAll(aggregation2)).contains(KNOWN_THING_ID);
        }


        @Test
        public void updateFeaturePropertyAndAcl() {
            insertBlockingAndResetMocks(isV2, createThing(KNOWN_THING_ID, VALUE1, isV2), 1L, -1L, policyEnforcer);

            final AclEntry entry = ThingsModelFactory.newAclEntry(newAuthSubject("anotherSid"),
                    org.eclipse.ditto.model.things.Permission.READ);

            final List<ThingEvent> writes = new ArrayList<>();
            writes.add(wrapEvent(
                    AclEntryModified.of(KNOWN_THING_ID, entry, 2L, DittoHeaders.empty()),
                    apiVersion));
            writes.add(wrapEvent(
                    createFeaturePropertyModified(FEATURE_ID1, PROP1, newValue("somethingNew"), 3L),
                    apiVersion));

            Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID,
                    writes, policyEnforcer, 3L)))
                    .isTrue();

            final PolicyRestrictedSearchAggregation aggregation1 = abf
                    .newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(VALUE1)))
                    .authorizationSubjects(KNOWN_SUBJECTS_2)
                    .build();

            assertThat(findAll(aggregation1)).contains(KNOWN_THING_ID);

            final PolicyRestrictedSearchAggregation aggregation2 = abf
                    .newBuilder(cf.fieldCriteria(fef.filterByFeatureProperty(PROP1), cf.eq("somethingNew")))
                    .authorizationSubjects(KNOWN_SUBJECTS_2)
                    .build();

            final Collection<String> result2 = findAll(aggregation2);
            assertThat(result2).contains(KNOWN_THING_ID);
        }

        @Test
        public void createThingAndUpdateAcl() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            Assertions.assertThat(
                    runBlockingWithReturn(writePersistence.insertOrUpdate(thing, 0L, 1L)))
                    .isTrue();
            if (isV2) {
                Assertions.assertThat(runBlockingWithReturn(writePersistence.updatePolicy(thing, policyEnforcer)))
                        .isTrue();
            }

            final AclEntry aclEntry =
                    ThingsModelFactory.newAclEntry(newAuthSubject("newSid"),
                            org.eclipse.ditto.model.things.Permission.READ,
                            org.eclipse.ditto.model.things.Permission.WRITE,
                            org.eclipse.ditto.model.things.Permission.ADMINISTRATE);
            final AccessControlList acl = ThingsModelFactory.newAcl(aclEntry);
            final AclModified aclModified = AclModified.of(KNOWN_THING_ID, acl, 1L, DittoHeaders.empty());

            final List<ThingEvent> writes = new ArrayList<>();
            writes.add(wrapEvent(aclModified, apiVersion));

            Assertions.assertThat(runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID,
                    writes, policyEnforcer, 1L)))
                    .isTrue();

            final PolicyRestrictedSearchAggregation aggregation1 = abf
                    .newBuilder(cf.fieldCriteria(fef.filterByAttribute(KEY1), cf.eq(VALUE1)))
                    .authorizationSubjects(Collections.singletonList("newSid"))
                    .build();

            assertThat(findAll(aggregation1)).isNotNull()
                    .contains(KNOWN_THING_ID);
        }

    }

    public static class MongoThingsSearchUpdaterPersistenceV2Tests extends BaseClass {

        private final boolean isV2 = true;

        @Test
        public void updatePolicyForThing() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);

            insertBlockingAndResetMocks(isV2, thing, 1L, -1L, policyEnforcer);
            final Policy policy = createPolicy1();

            Assertions.assertThat(runBlockingWithReturn(
                    writePersistence.updatePolicy(thing, PolicyEnforcers.defaultEvaluator(policy)))).isTrue();
            Assertions.assertThat(runBlockingWithReturn(
                    writePersistence.updatePolicy(thing, PolicyEnforcers.defaultEvaluator(createPolicy2()))))
                    .isTrue();
        }

        @Test
        public void getThingMetadata() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            final long thingRevision = 13;
            final long policyRevision = 77;
            runBlocking(writePersistence.insertOrUpdate(thing, thingRevision, policyRevision));

            final ThingMetadata result = runBlockingWithReturn(writePersistence.getThingMetadata(KNOWN_THING_ID));
            assertThat(result.getPolicyId())
                    .isEqualTo(thing.getPolicyId().orElseThrow(() -> new IllegalStateException("not possible")));
            assertThat(result.getPolicyRevision())
                    .isEqualTo(policyRevision);
            assertThat(result.getThingRevision())
                    .isEqualTo(thingRevision);
        }

        @Test
        public void getThingMetadataForNotExistingThing() {
            final ThingMetadata result = runBlockingWithReturn(writePersistence.getThingMetadata(KNOWN_THING_ID));
            assertThat(result.getPolicyId())
                    .isNull();
            assertThat(result.getPolicyRevision())
                    .isEqualTo(-1L);
            assertThat(result.getThingRevision())
                    .isEqualTo(-1L);
        }

        @Test
        public void getThingIdsForPolicy() {
            final String policyId = "any-ns:testPolicyId";
            final Thing thing1 = createThing("test:id1", "val1", isV2).setPolicyId(policyId);
            final Thing thing2 = createThing("test:id2", "val2", isV2).setPolicyId(policyId);

            runBlocking(Arrays.asList(writePersistence.insertOrUpdate(thing1, -1L, -1L),
                    writePersistence.insertOrUpdate(thing2, -1L, -1L)));

            final Set<String> result = runBlockingWithReturn(writePersistence.getThingIdsForPolicy(policyId));

            assertThat(result.size())
                    .isEqualTo(2);
            assertThat(result)
                    .containsOnly(thing1.getId().orElseThrow(IllegalStateException::new),
                            thing2.getId().orElseThrow(IllegalStateException::new));
        }

        @Test
        public void getThingIdsForPolicyThatDoesNotExist() {
            final Set<String> result =
                    runBlockingWithReturn(writePersistence.getThingIdsForPolicy("any-ns:testPolicyId"));

            assertThat(result.isEmpty())
                    .isTrue();
        }

        @Test
        public void insertWithSameThingRevisionAndSamePolicyRevision() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            final long thingRevision = 1L;
            final long policyRevision = 0;

            insertBlockingAndResetMocks(isV2, thing, thingRevision, policyRevision, policyEnforcer);

            // should not insert thing with same revision and policy revision
            Assertions.assertThat(runBlockingWithReturn(
                    writePersistence.insertOrUpdate(thing, thingRevision, policyRevision))).isFalse();
        }

        @Test
        public void insertWithSameThingRevisionAndHigherPolicyRevision() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            final long thingRevision = 1L;
            final long policyRevision = 0L;
            final long higherPolicyRevision = 1L;

            insertBlockingAndResetMocks(isV2, thing, thingRevision, policyRevision, policyEnforcer);

            // should insert thing with same revision but higher policy revision
            Assertions.assertThat(runBlockingWithReturn(
                    writePersistence.insertOrUpdate(thing, thingRevision, higherPolicyRevision))).isTrue();
        }

        @Test
        public void insertWithLowerThingRevisionAndHigherPolicyRevision() {
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, isV2);
            final long lowerThingRevision = 0L;
            final long thingRevision = 1L;
            final long policyRevision = 0L;
            final long higherPolicyRevision = 1L;

            insertBlockingAndResetMocks(isV2, thing, thingRevision, policyRevision, policyEnforcer);

            // should insert thing with same revision but higher policy revision
            Assertions.assertThat(runBlockingWithReturn(
                    writePersistence.insertOrUpdate(thing, lowerThingRevision, higherPolicyRevision))).isFalse();
        }
    }

    public static class MongoThingsSearchUpdaterPersistenceDefaultTests extends BaseClass {

        @Test
        public void deleteForNotExistingThing() {
            final boolean result = runBlockingWithReturn(writePersistence.delete(KNOWN_THING_ID));

            assertThat(result)
                    .isFalse();
        }

        @Test
        public void executeCombinedWritesWithoutThingEvents() {
            Assertions.assertThat(
                    runBlockingWithReturn(writePersistence.executeCombinedWrites(KNOWN_THING_ID,
                            Collections.emptyList(),
                            policyEnforcer, 1L)))
                    .isTrue();

            verifyNoMoreInteractions(writeThingsCollectionSpy);
            verifyNoMoreInteractions(writePoliciesCollectionSpy);
        }

        @Test
        public void migrateExistingThingToV2() {

            // create a thing with an ACL
            final Thing thing = createThing(KNOWN_THING_ID, VALUE1, false);

            insertBlockingAndResetMocks(false, thing, 1L, -1L, policyEnforcer);

            final PolicyRestrictedSearchAggregation aggregation1 = abf.newBuilder(cf.any())
                    .authorizationSubjects(KNOWN_SUBJECTS_2)
                    .build();
            final List<String> foundAll = findAll(aggregation1);
            assertThat(foundAll).containsOnly(KNOWN_THING_ID);

            final String newUser = "some:someNewUser";
            final Policy newPolicy = createPolicyFor(newUser);

            insertBlockingAndResetMocks(true, thing, 2L, 0, PolicyEnforcers.defaultEvaluator(newPolicy));

            final List<String> foundAll2 = findAll(aggregation1);

            // none of KNOWN_SUBJECTS_2 has permissions in "newPolicy"
            assertThat(foundAll2).isEmpty();

            final PolicyRestrictedSearchAggregation aggregation2 = abf.newBuilder(cf.any())
                    .authorizationSubjects(Collections.singletonList(newUser))
                    .build();
            final List<String> foundAll3 = findAll(aggregation2);

            // newUser is allowed to read the thing
            assertThat(foundAll3).containsOnly(KNOWN_THING_ID);
        }

        @Test
        public void errorRecoveryForDuplicateKey() {
            final PartialFunction<Throwable, Source<Boolean, NotUsed>> recovery =
                    writePersistence.errorRecovery(KNOWN_THING_ID);

            assertThat(runBlockingWithReturn(recovery.apply(createMongoWriteException(11000))))
                    .isEqualTo(Boolean.FALSE);
        }

        @Test
        public void errorRecoveryForIndexTooLong() {
            final PartialFunction<Throwable, Source<Boolean, NotUsed>> recovery =
                    writePersistence.errorRecovery(KNOWN_THING_ID);

            assertThat(runBlockingWithReturn(recovery.apply(createMongoWriteException(17280))))
                    .isEqualTo(Boolean.TRUE);
        }

        @Test
        public void errorRecoveryForUnhandledException() {
            final PartialFunction<Throwable, Source<Boolean, NotUsed>> recovery =
                    writePersistence.errorRecovery(KNOWN_THING_ID);

            final IllegalArgumentException toThrow = new IllegalArgumentException("any");
            assertThatExceptionOfType(toThrow.getClass())
                    .isThrownBy(() -> {
                        runBlockingWithReturn(recovery.apply(toThrow));
                    })
                    .isEqualTo(toThrow);
        }

        private MongoWriteException createMongoWriteException(final int errorCode) {
            final WriteError writeError = new WriteError(errorCode, "error", BsonDocument.parse("{}"));
            final ServerAddress serverAddress = new ServerAddress();
            return new MongoWriteException(writeError, serverAddress);
        }


    }

    private static Thing createThing(final String thingId, final String attributeValue, final boolean isV2) {
        if (isV2) {
            return thingV2(thingId, attributeValue);
        } else {
            return thingV1(thingId, attributeValue);
        }
    }

    private static Thing thingV2(final String thingId, final String attributeValue) {
        final Attributes attributes = ThingsModelFactory.newAttributesBuilder()
                .set(KEY1, attributeValue)
                .set(KEY2, VALUE2)
                .set(KEY3, VALUE3)
                .set(KEY4, VALUE4)
                .set(KEY5, VALUE5)
                .build();

        final FeatureProperties featureProperties = ThingsModelFactory.newFeaturePropertiesBuilder()
                .set(PROP1, PROP_VALUE1)
                .set(PROP1_EXTENDED, PROP_VALUE1)
                .set(PROP2, PROP_VALUE2)
                .set(PROP3, PROP_VALUE3)
                .set(PROP4, PROP_VALUE4)
                .set(PROP7, JsonFactory.newObjectBuilder()
                        .set(JsonFactory.newKey(PROP8), PROP_VALUE8)
                        .build()
                )
                .build();
        final Feature feature = ThingsModelFactory.newFeature(FEATURE_ID1, featureProperties);

        return ThingsModelFactory.newThingBuilder()
                .setId(thingId)
                .setPolicyId(createPolicy1().getId().get())
                .setAttributes(attributes)
                .setFeature(feature)
                .setLifecycle(ThingLifecycle.ACTIVE)
                .setRevision(0L)
                .build();
    }

    private static Thing thingV1(final String thingId, final String attributeValue) {
        final AccessControlList acl = ThingsModelFactory.newAclBuilder()
                .set(ThingsModelFactory.newAclEntry(newAuthSubject("some:mySid"),
                        ThingsModelFactory.allPermissions()))
                .set(ThingsModelFactory.newAclEntry(newAuthSubject("some:mySid2"),
                        org.eclipse.ditto.model.things.Permission.WRITE))
                .set(ThingsModelFactory.newAclEntry(newAuthSubject("some:mySid3"),
                        org.eclipse.ditto.model.things.Permission.READ))
                .build();

        final Attributes attributes = ThingsModelFactory.newAttributesBuilder()
                .set(KEY1, attributeValue)
                .set(KEY2, VALUE2)
                .set(KEY3, VALUE3)
                .set(KEY4, VALUE4)
                .set(KEY5, VALUE5)
                .build();

        final FeatureProperties featureProperties = ThingsModelFactory.newFeaturePropertiesBuilder()
                .set(PROP1, PROP_VALUE1)
                .set(PROP1_EXTENDED, PROP_VALUE1)
                .set(PROP2, PROP_VALUE2)
                .set(PROP3, PROP_VALUE3)
                .set(PROP4, PROP_VALUE4)
                .set(PROP7, JsonFactory.newObjectBuilder()
                        .set(JsonFactory.newKey(PROP8), PROP_VALUE8)
                        .build()
                )
                .build();
        final Feature feature = ThingsModelFactory.newFeature(FEATURE_ID1, featureProperties);

        return ThingsModelFactory.newThingBuilder()
                .setId(thingId)
                .setPermissions(acl)
                .setAttributes(attributes)
                .setFeature(feature)
                .setLifecycle(ThingLifecycle.ACTIVE)
                .setRevision(0L)
                .build();
    }

    private static Policy createPolicy1() {
        return PoliciesModelFactory.newPolicyBuilder(KNOWN_THING_ID)
                .forLabel("someLabel")
                .setSubject(
                        Subject.newInstance(SubjectId.newInstance(DEFAULT_POLICY_SUBJECTS.iterator().next())))
                .setGrantedPermissions("thing", "/", Permission.READ)
                .setRevision(1L)
                .build();
    }

    private static AclModified createAclModified(final CharSequence authSubjectId,
            final long revision,
            final org.eclipse.ditto.model.things.Permission permission,
            final org.eclipse.ditto.model.things.Permission... furtherPermissions) {

        final AclEntry aclEntry = ThingsModelFactory.newAclEntry(AuthorizationSubject.newInstance(authSubjectId),
                permission, furtherPermissions);
        final AccessControlList accessControlList = ThingsModelFactory.newAcl(aclEntry);
        return AclModified.of(KNOWN_THING_ID, accessControlList, revision, DittoHeaders.empty());
    }

    private static AttributeModified createAttributeModified(final CharSequence attributePointer,
            final JsonValue attributeValue, final long revision) {
        return AttributeModified.of(KNOWN_THING_ID, JsonFactory.newPointer(attributePointer), attributeValue,
                revision,
                DittoHeaders.empty());
    }

    private static ThingEvent createFeatureCreated(final CharSequence featureId) {
        final Feature feature = createFeature(featureId.toString());
        return FeatureCreated.of(KNOWN_THING_ID, feature, 2L, DittoHeaders.empty());
    }


    private static Policy createPolicyFor(final CharSequence user) {
        return PoliciesModelFactory.newPolicyBuilder(KNOWN_THING_ID)
                .forLabel("someLabel")
                .setSubject(Subject.newInstance(SubjectId.newInstance(user)))
                .setGrantedPermissions("thing", "/", Permission.READ)
                .setRevision(1L)
                .build();
    }

    private static Policy createPolicy2() {
        return PoliciesModelFactory.newPolicyBuilder(KNOWN_THING_ID)
                .forLabel("someLabel")
                .setSubjects(
                        Subjects.newInstance(Subject.newInstance(SubjectId.newInstance("some:user88")),
                                Subject.newInstance(SubjectId.newInstance("some:user2"))))
                .setGrantedPermissions("thing", "/", Permission.READ)
                .setRevision(2L)
                .build();
    }

    private static Features createFeatures() {
        final Feature f1 = createFeature("f1");
        final Feature f2 = createFeature("f2");
        final Feature f3 = createFeature("f3");

        return ThingsModelFactory.newFeatures(f1, f2, f3);
    }

    private static Features createFeaturesWithDottedFeatureId(final String knownFeatureIdWithDots) {
        final Feature f1 = createFeature("f.1");
        final Feature f2 = createFeature("f.2");
        final Feature withDots = createFeature(knownFeatureIdWithDots);

        return ThingsModelFactory.newFeatures(f1, f2, withDots);
    }

    private static Features createFeaturesWithDottedPropertyNames(final String knownFeatureIdWithDots) {
        final Feature baselineFeature = createFeature(knownFeatureIdWithDots);
        final FeatureProperties featureProperties =
                baselineFeature.getProperties().orElseThrow(IllegalStateException::new)
                        .toBuilder()
                        .set(DOTTED_PROP, JsonFactory.newObjectBuilder().set(DOTTED_PROP, VALUE1).build())
                        .build();
        final Feature feature = ThingsModelFactory.newFeature(knownFeatureIdWithDots, featureProperties);

        return ThingsModelFactory.newFeatures(feature);
    }

    private static Feature createFeature(final String featureId) {
        final FeatureProperties featureProperties = ThingsModelFactory.newFeaturePropertiesBuilder()
                .set(PROP5, PROP_VALUE5)
                .set(PROP6, PROP_VALUE6)
                .set(PROP7, JsonFactory.newObjectBuilder()
                        .set(JsonFactory.newKey(PROP8), PROP_VALUE8)
                        .build()
                )
                .build();

        return ThingsModelFactory.newFeature(featureId, featureProperties);
    }

    private static ThingEvent createFeaturePropertiesDeleted(final CharSequence featureId) {
        return FeaturePropertiesDeleted.of(KNOWN_THING_ID, featureId.toString(), 2L,
                DittoHeaders.empty());
    }

    private static ThingEvent createFeaturePropertiesModified(final CharSequence featureId) {
        final Feature feature = createFeature(featureId.toString());
        final FeatureProperties properties = feature.getProperties().orElseThrow(IllegalStateException::new);
        return FeaturePropertiesModified.of(KNOWN_THING_ID, featureId.toString(), properties, 2L, DittoHeaders.empty());
    }

    private static ThingEvent createFeaturePropertyDeleted(final CharSequence featureId,
            final CharSequence featurePropertyPointer, final long revision) {
        return FeaturePropertyDeleted.of(KNOWN_THING_ID, featureId.toString(), JsonFactory
                .newPointer(featurePropertyPointer), revision, DittoHeaders.empty());
    }


    private static ThingEvent createFeaturePropertyModified(final CharSequence featureId,
            final CharSequence propertyPointer, final JsonValue propertyValue, final long revision) {
        return FeaturePropertyModified.of(KNOWN_THING_ID, featureId.toString(), JsonFactory.newPointer
                (propertyPointer), propertyValue, revision, DittoHeaders.empty());
    }

    private static ThingEvent createFeaturesModified(final Features features) {
        return FeaturesModified.of(KNOWN_THING_ID, features, 2L, DittoHeaders.empty());
    }

    private static ThingEvent wrapEvent(final ThingEvent thingEvent, final JsonSchemaVersion version) {
        final DittoHeaders versionedHeaders = thingEvent.getDittoHeaders().toBuilder()
                .schemaVersion(version)
                .build();
        return thingEvent.setDittoHeaders(versionedHeaders);
    }
}

