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

import java.util.Arrays;
import java.util.List;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcer;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcers;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.models.policies.Permission;
import org.eclipse.ditto.services.thingsearch.persistence.TestConstants;
import org.eclipse.ditto.services.thingsearch.persistence.util.MongoSetKeyValidity;
import org.eclipse.ditto.signals.events.things.AclEntryCreated;
import org.eclipse.ditto.signals.events.things.AclEntryModified;
import org.eclipse.ditto.signals.events.things.AttributeModified;
import org.eclipse.ditto.signals.events.things.AttributesModified;
import org.eclipse.ditto.signals.events.things.FeatureModified;
import org.eclipse.ditto.signals.events.things.FeaturesModified;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.things.ThingModified;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;

import akka.event.LoggingAdapter;
import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link CombinedThingWrites}.
 */
@RunWith(Parameterized.class)
public final class CombinedThingWritesTest {

    @Parameterized.Parameter
    public static JsonSchemaVersion apiVersion;

    @Parameterized.Parameters(name = "v{0}")
    public static List<JsonSchemaVersion> apiVersions() {
        return Arrays.asList(JsonSchemaVersion.values());
    }

    private static final String SUBJECT = "CombinedThingWritesSubject";
    private static final DittoHeaders HEADERS = DittoHeaders.empty();
    private static PolicyEnforcer policyEnforcer;

    private LoggingAdapter loggingAdapter = Mockito.mock(LoggingAdapter.class);

    @BeforeClass
    public static void setupPolicyEnforcer() {
        final Policy policy = Policy.newBuilder("CombinedThingWritesTest:policyId")
                .forLabel("root")
                .setSubject(SubjectIssuer.GOOGLE_URL, SUBJECT)
                .setGrantedPermissions(PoliciesResourceType.thingResource("/"), Permission.READ)
                .build();
        policyEnforcer = PolicyEnforcers.defaultEvaluator(policy);
    }

    /** */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(CombinedThingWrites.class)
                .usingGetClass()
                .verify();
    }

    /** */
    @Test
    public void addAclEntryCreatedProducesExpected() {
        final long sourceSequenceNumber = 23;
        final long revision = sourceSequenceNumber + 1;
        final AclEntryCreated aclEntryCreated =
                AclEntryCreated.of(TestConstants.Thing.THING_ID, TestConstants.Authorization.ACL_ENTRY_GRIMES, revision,
                        DittoHeaders.empty());

        final CombinedThingWrites combinedThingWrites =
                CombinedThingWrites.newBuilder(loggingAdapter, sourceSequenceNumber, policyEnforcer)
                        .addEvent(aclEntryCreated, apiVersion)
                        .build();

        assertThat(combinedThingWrites.getCombinedWriteDocuments()).hasSize(2);
        assertThat(combinedThingWrites.getCombinedPolicyUpdates()).isEmpty();
        assertThat(combinedThingWrites.getSourceSequenceNumber()).isEqualTo(sourceSequenceNumber);
        assertThat(combinedThingWrites.getTargetSequenceNumber()).isEqualTo(sourceSequenceNumber + 1);
    }

    /** */
    @Test
    public void addAclEntryModifiedProducesExpected() {
        final long sourceSequenceNumber = 23;
        final long revision = sourceSequenceNumber + 1;
        final AclEntryModified aclEntryModified =
                AclEntryModified.of(TestConstants.Thing.THING_ID, TestConstants.Authorization.ACL_ENTRY_GRIMES,
                        revision, DittoHeaders.empty());

        final CombinedThingWrites combinedThingWrites =
                CombinedThingWrites.newBuilder(loggingAdapter, sourceSequenceNumber, policyEnforcer)
                        .addEvent(aclEntryModified, apiVersion)
                        .build();

        assertThat(combinedThingWrites.getCombinedWriteDocuments()).hasSize(2);
        assertThat(combinedThingWrites.getCombinedPolicyUpdates()).isEmpty();
        assertThat(combinedThingWrites.getSourceSequenceNumber()).isEqualTo(sourceSequenceNumber);
        assertThat(combinedThingWrites.getTargetSequenceNumber()).isEqualTo(sourceSequenceNumber + 1);
    }

    @Test
    public void escapeFeatureModifiedEvent() {
        // WHEN
        final FeatureModified event = FeatureModified.of("thing:id", featureWithDotInID(), 2, HEADERS);
        final CombinedThingWrites writes = fromEvent(event);

        // THEN
        writes.getCombinedWriteDocuments().forEach(MongoSetKeyValidity::ensure);
    }

    @Test
    public void escapeFeaturesModifiedEvent() {
        // WHEN
        final FeaturesModified event = FeaturesModified.of("thing:id",
                ThingsModelFactory.newFeatures(featureWithDotInID()), 2, HEADERS);
        final CombinedThingWrites writes = fromEvent(event);

        // THEN
        writes.getCombinedWriteDocuments().forEach(MongoSetKeyValidity::ensure);
    }

    @Test
    public void escapeAttributeModifiedEvent() {
        // WHEN
        final AttributeModified event = AttributeModified.of("thing:id",
                JsonPointer.of("attribute.0"),
                JsonFactory.newObject("{\"Ac.0\":4}"),
                2,
                HEADERS);
        final CombinedThingWrites writes = fromEvent(event);

        // THEN
        writes.getCombinedWriteDocuments().forEach(MongoSetKeyValidity::ensure);
    }

    @Test
    public void escapeAttributesModifiedEvent() {
        final AttributesModified event = AttributesModified.of("thingId",
                ThingsModelFactory.newAttributes(JsonFactory.newObject(
                        "{\"attribute.0\":{\"Ac.0\":4}}"
                )),
                2,
                HEADERS);
        final CombinedThingWrites writes = fromEvent(event);

        // THEN
        writes.getCombinedWriteDocuments().forEach(MongoSetKeyValidity::ensure);
    }

    @Test
    public void escapeThingModifiedEvent() {
        // WHEN
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId("escape:ThingModifiedEvent")
                .setFeature(featureWithDotInID())
                .build();

        final ThingModified event = ThingModified.of(thing, 2, HEADERS);
        final CombinedThingWrites writes = fromEvent(event);

        // THEN
        writes.getCombinedWriteDocuments().forEach(MongoSetKeyValidity::ensure);
    }

    private CombinedThingWrites fromEvent(final ThingEvent event) {
        return CombinedThingWrites.newBuilder(loggingAdapter, event.getRevision() - 1, policyEnforcer)
                .addEvent(event, apiVersion)
                .build();
    }

    private Feature featureWithDotInID() {
        return Feature.newBuilder()
                .properties(JsonFactory.newObjectBuilder().set("x.y", 5).build())
                .withId("Ac.0")
                .build();
    }

}
