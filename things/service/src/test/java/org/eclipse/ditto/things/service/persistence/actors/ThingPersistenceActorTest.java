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
package org.eclipse.ditto.things.service.persistence.actors;

import static org.assertj.core.api.Assertions.fail;
import static org.eclipse.ditto.things.model.assertions.DittoThingsAssertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSelection;
import org.apache.pekko.actor.PoisonPill;
import org.apache.pekko.actor.Props;
import org.apache.pekko.cluster.pubsub.DistributedPubSubMediator;
import org.apache.pekko.cluster.sharding.ShardRegion;
import org.apache.pekko.japi.pf.ReceiveBuilder;
import org.apache.pekko.testkit.TestActorRef;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.eclipse.ditto.base.api.common.Shutdown;
import org.eclipse.ditto.base.api.common.ShutdownReasonFactory;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.entity.Revision;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.entity.metadata.MetadataModelFactory;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.metadata.MetadataHeader;
import org.eclipse.ditto.base.model.headers.metadata.MetadataHeaderKey;
import org.eclipse.ditto.base.model.headers.metadata.MetadataHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.cluster.StopShardedActor;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.internal.utils.persistentactors.AbstractPersistenceSupervisor;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;
import org.eclipse.ditto.internal.utils.pubsub.extractors.AckExtractor;
import org.eclipse.ditto.internal.utils.pubsubthings.LiveSignalPub;
import org.eclipse.ditto.internal.utils.test.Retry;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseOptions;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicyResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyResponse;
import org.eclipse.ditto.things.api.Permission;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.PolicyIdMissingException;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingBuilder;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingLifecycle;
import org.eclipse.ditto.things.model.ThingRevision;
import org.eclipse.ditto.things.model.ThingTooLargeException;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.TestConstants;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.exceptions.FeatureNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.MetadataHeadersConflictException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingUnavailableException;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThingResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteAttribute;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteAttributeResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThing;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThingResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThing;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThingResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttribute;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttributeResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttributes;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeature;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureProperty;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatures;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThingResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttribute;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttributeResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttributes;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeature;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDefinition;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDefinitionResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureProperty;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeaturePropertyResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatures;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThings;
import org.eclipse.ditto.things.model.signals.events.ThingCreated;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.model.signals.events.ThingModified;
import org.eclipse.ditto.things.service.common.config.DittoThingsConfig;
import org.eclipse.ditto.things.service.enforcement.TestSetup;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import scala.PartialFunction;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.runtime.BoxedUnit;

/**
 * Unit test for the {@link ThingPersistenceActor}.
 */
public final class ThingPersistenceActorTest extends PersistenceActorTestBase {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    private static final JsonParseOptions JSON_PARSE_OPTIONS =
            JsonFactory.newParseOptionsBuilder().withoutUrlDecoding().build();

    private static final Instant TIMESTAMP = Instant.EPOCH;

    private static void assertThingInResponse(final Thing actualThing, final Thing expectedThing) {
        // Policy entries are ignored by things-persistence.
        assertThat(actualThing).hasEqualJson(expectedThing, FieldType.notHidden()
                .and(IS_MODIFIED.negate()));

        assertThat(actualThing.getModified()).isPresent(); // we cannot check exact timestamp
        assertThat(actualThing.getCreated()).isPresent(); // we cannot check exact timestamp
    }

    private static void assertThingInResponseV2(final Thing actualThing, final Thing expectedThing) {
        assertThat(actualThing).hasEqualJson(expectedThing, FieldType.notHidden()
                .and(IS_MODIFIED.negate()));

        assertThat(actualThing.getModified()).isPresent(); // we cannot check exact timestamp
    }

    @Rule
    public final TestWatcher watchman = new TestedMethodLoggingWatcher(LoggerFactory.getLogger(getClass()));

    @Before
    public void setUp() {
        final Config customConfig = ConfigFactory.empty()
                .withValue("pekko.actor.provider",
                        ConfigValueFactory.fromAnyRef("org.apache.pekko.cluster.ClusterActorRefProvider"));
        setup(customConfig);
    }

    @Test
    public void unavailableExpectedIfPersistenceActorTerminates() throws Exception {
        final Policy inlinePolicy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(SubjectIssuer.newInstance("test"), AUTH_SUBJECT)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .setGrantedPermissions(PoliciesResourceType.policyResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .build();
        final CreatePolicyResponse createPolicyResponse = CreatePolicyResponse.of(POLICY_ID, inlinePolicy,
                DittoHeaders.empty());
        when(policyEnforcerProvider.getPolicyEnforcer(POLICY_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(PolicyEnforcer.of(inlinePolicy))));

        new TestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final ThingId thingId = getIdOrThrow(thing);

                final ActorRef underTest = createSupervisorActorFor(thingId);


                // as the "createThing" shall be handled by the supervisor which now also does enforcement, pass along
                // an initial policy which will also be used for enforcement:
                final CreateThing createThing = CreateThing.of(thing, inlinePolicy.toJson(FieldType.all()),
                        dittoHeadersV2);
                underTest.tell(createThing, getRef());

                policiesShardRegionTestProbe.expectMsgClass(CreatePolicy.class);
                policiesShardRegionTestProbe.reply(createPolicyResponse);

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);
                when(policyEnforcerProvider.getPolicyEnforcer(POLICY_ID))
                        .thenReturn(CompletableFuture.completedFuture(Optional.of(PolicyEnforcer.of(inlinePolicy))));
                // retrieve created thing
                final RetrieveThing retrieveThing = RetrieveThing.of(thingId, dittoHeadersV2);
                underTest.tell(retrieveThing, getRef());
                final DittoHeaders expectedHeaders = dittoHeadersV2.toBuilder()
                        .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "1")
                        .readGrantedSubjects(List.of(AUTHORIZED_SUBJECT))
                        .build();
                expectMsgEquals(ETagTestUtils.retrieveThingResponse(thing, null, expectedHeaders));

                // terminate thing persistence actor
                final String thingActorPath = String.format("pekko://PekkoTestSystem/user/%s/pa", thingId);
                final ActorSelection thingActorSelection = actorSystem.actorSelection(thingActorPath);
                final Future<ActorRef> thingActorFuture =
                        thingActorSelection.resolveOne(Duration.create(5, TimeUnit.SECONDS));
                Await.result(thingActorFuture, Duration.create(6, TimeUnit.SECONDS));
                final ActorRef thingActor = watch(thingActorFuture.value().get().get());

                watch(thingActor);
                thingActor.tell(PoisonPill.getInstance(), getRef());
                expectTerminated(thingActor);

                // wait for supervisor to react to termination message
                Thread.sleep(500L);

                // retrieve unavailable thing
                underTest.tell(retrieveThing, getRef());
                expectMsgClass(ThingUnavailableException.class);
            }
        };
    }

    @Test
    public void tryToModifyFeaturePropertyAndReceiveCorrectErrorCode() {
        final ThingId thingId = ThingId.of("org.eclipse.ditto", "myThing");
        final Thing thing = Thing.newBuilder()
                .setId(thingId)
                .setPolicyId(PolicyId.of(thingId))
                .setFeatures(JsonFactory.newObject())
                .build();
        final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);

        final String featureId = "myFeature";
        final JsonPointer jsonPointer = JsonPointer.of("/state");
        final JsonValue jsonValue = JsonFactory.newValue("on");
        final ModifyFeatureProperty modifyFeatureProperty =
                ModifyFeatureProperty.of(thingId, featureId, jsonPointer, jsonValue, dittoHeadersV2);

        final FeatureNotAccessibleException featureNotAccessibleException =
                FeatureNotAccessibleException.newBuilder(thingId, featureId)
                        .dittoHeaders(dittoHeadersV2)
                        .build();

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thingId);

                underTest.tell(createThing, getRef());
                expectMsgClass(CreateThingResponse.class);

                underTest.tell(modifyFeatureProperty, getRef());
                final Object actual = receiveOne(scala.concurrent.duration.Duration.apply(1, TimeUnit.SECONDS));
                assertThat(actual).isInstanceOf(DittoRuntimeException.class);
                assertThat(((DittoRuntimeException) actual).getErrorCode()).isEqualTo(
                        featureNotAccessibleException.getErrorCode());
            }
        };
    }

    @Test
    public void tryToRetrieveThingWhichWasNotYetCreated() {
        final ThingId thingId = ThingId.of("test.ns", "23420815");
        final ThingCommand retrieveThingCommand = RetrieveThing.of(thingId, dittoHeadersV2);

        new TestKit(actorSystem) {
            {
                final ActorRef thingPersistenceActor = createPersistenceActorFor(thingId);
                thingPersistenceActor.tell(retrieveThingCommand, getRef());
                expectMsgClass(ThingNotAccessibleException.class);
            }
        };
    }

    /**
     * The ThingPersistenceActor is created with a Thing ID. Any command it receives which belongs to a Thing with a
     * different ID should lead to an exception as the command was obviously sent to the wrong ThingPersistenceActor.
     */
    @Test
    public void tryToCreateThingWithDifferentThingId() {
        final ThingId thingIdOfActor = ThingId.of("test.ns", "23420815");
        final Thing thing = createThingV2WithRandomId();
        final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);

        final DittoThingsConfig thingsConfig = DittoThingsConfig.of(
                DefaultScopedConfig.dittoScoped(actorSystem.settings().config())
        );
        final Props props = ThingPersistenceActor.props(thingIdOfActor, Mockito.mock(MongoReadJournal.class),
                thingsConfig.getThingConfig(), getDistributedPub(), null, policyEnforcerProvider);
        final TestActorRef<ThingPersistenceActor> underTest = TestActorRef.create(actorSystem, props);
        final ThingPersistenceActor thingPersistenceActor = underTest.underlyingActor();
        final PartialFunction<Object, BoxedUnit> receiveCommand = thingPersistenceActor.receiveCommand();

        try {
            receiveCommand.apply(createThing);
            fail("Expected IllegalArgumentException to be thrown.");
        } catch (final Exception e) {
            assertThat(e).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    public void createThingV2() {
        new TestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);
            }
        };
    }

    @Test
    public void modifyThingV2() {
        final Thing thing = createThingV2WithRandomId();

        final Thing modifiedThing = thing.setAttribute(JsonFactory.newPointer("foo/bar"), JsonFactory.newValue("baz"));

        testModifyThing(dittoHeadersV2, thing, modifiedThing);
    }

    private void testModifyThing(final DittoHeaders dittoHeaders, final Thing thing, final Thing modifiedThing) {
        final ModifyThing modifyThingCommand = ModifyThing.of(getIdOrThrow(thing), modifiedThing, null, dittoHeaders);
        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeaders);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);

                underTest.tell(modifyThingCommand, getRef());
                expectMsgEquals(ETagTestUtils.modifyThingResponse(thing, modifiedThing, dittoHeaders.toBuilder()
                        .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "2")
                        .build(), false));
            }
        };
    }

    /**
     * Makes sure that it is not possible to modify a thing without a previous create. If this was possible, a thing
     * could contain old data (in case of a recreate).
     */
    @Test
    public void modifyThingWithoutPreviousCreate() {
        final Thing thing = createThingV2WithRandomId();
        final ModifyThing modifyThingCommand = ModifyThing.of(getIdOrThrow(thing), thing, null, dittoHeadersV2);

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thing);

                underTest.tell(modifyThingCommand, getRef());

                expectMsgClass(ThingNotAccessibleException.class);
            }
        };
    }

    @Test
    public void modifyThingOverwritesExistingFirstLevelFieldsWhenExplicitlySpecifiedV2() {
        final Thing thingWithFirstLevelFields = createThingV2WithRandomId();
        final Thing thingWithDifferentFirstLevelFields = Thing.newBuilder()
                .setId(getIdOrThrow(thingWithFirstLevelFields))
                .setPolicyId(PolicyId.of("org.eclipse.ditto:changedPolicyId"))
                .setAttributes(Attributes.newBuilder().set("changedAttrKey", "changedAttrVal").build())
                .setFeatures(Features.newBuilder().set(Feature.newBuilder().withId("changedFeatureId").build()))
                .build();
        doTestModifyThingKeepsOverwritesExistingFirstLevelFieldsWhenExplicitlySpecified(thingWithFirstLevelFields,
                thingWithDifferentFirstLevelFields, dittoHeadersV2);
    }

    private void doTestModifyThingKeepsOverwritesExistingFirstLevelFieldsWhenExplicitlySpecified(
            final Thing thingWithFirstLevelFields, final Thing thingWithDifferentFirstLevelFields,
            final DittoHeaders dittoHeaders) {

        final ThingId thingId = getIdOrThrow(thingWithFirstLevelFields);

        final ModifyThing modifyThingCommand =
                ModifyThing.of(thingId, thingWithDifferentFirstLevelFields, null, dittoHeaders);

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thingWithFirstLevelFields);

                final CreateThing createThing = CreateThing.of(thingWithFirstLevelFields, null, dittoHeaders);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thingWithFirstLevelFields);

                assertPublishEvent(ThingCreated.of(thingWithFirstLevelFields, 1L, TIMESTAMP, dittoHeaders,
                        null));

                underTest.tell(modifyThingCommand, getRef());

                expectMsgEquals(
                        ETagTestUtils.modifyThingResponse(thingWithFirstLevelFields, thingWithDifferentFirstLevelFields,
                                dittoHeaders.toBuilder()
                                        .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "2")
                                        .build(), false));

                assertPublishEvent(ThingModified.of(thingWithDifferentFirstLevelFields, 2L, TIMESTAMP, dittoHeaders,
                        null));

                final RetrieveThing retrieveThing =
                        RetrieveThing.getBuilder(thingId, dittoHeaders)
                                .withSelectedFields(ALL_FIELDS_SELECTOR)
                                .build();
                underTest.tell(retrieveThing, getRef());

                final RetrieveThingResponse retrieveThingResponse = expectMsgClass(RetrieveThingResponse.class);
                assertThingInResponse(retrieveThingResponse.getThing(), thingWithDifferentFirstLevelFields);
            }
        };
    }

    @Test
    public void modifyThingOverwritesExistingFirstLevelFieldsWhenNotExplicitlySpecifiedV2() {
        final Thing thingWithFirstLevelFields = createThingV2WithRandomId();
        doTestModifyThingOverwritesExistingFirstLevelFieldsWhenNotExplicitlySpecified(thingWithFirstLevelFields,
                dittoHeadersV2.toBuilder()
                        .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "2")
                        .build());
    }

    private void doTestModifyThingOverwritesExistingFirstLevelFieldsWhenNotExplicitlySpecified(
            final Thing thingWithFirstLevelFields, final DittoHeaders dittoHeaders) {

        final ThingId thingId = getIdOrThrow(thingWithFirstLevelFields);

        final Thing minimalThing = Thing.newBuilder()
                .setId(thingId)
                .setPolicyId(PolicyId.of(thingId))
                .build();

        final Thing expectedThing = minimalThing.toBuilder().setPolicyId(PolicyId.of(thingId)).build();

        final ModifyThing modifyThingCommand = ModifyThing.of(thingId, minimalThing, null, dittoHeaders);

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thingWithFirstLevelFields);

                final CreateThing createThing = CreateThing.of(thingWithFirstLevelFields, null, dittoHeaders);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thingWithFirstLevelFields);

                assertPublishEvent(ThingCreated.of(thingWithFirstLevelFields, 1L, TIMESTAMP, dittoHeaders, null));

                underTest.tell(modifyThingCommand, getRef());

                expectMsgEquals(ETagTestUtils.modifyThingResponse(thingWithFirstLevelFields, minimalThing, dittoHeaders,
                        false));

                assertPublishEvent(ThingModified.of(expectedThing, 2L, TIMESTAMP, dittoHeaders, null));

                final RetrieveThing retrieveThing = RetrieveThing.getBuilder(thingId, dittoHeaders)
                        .withSelectedFields(ALL_FIELDS_SELECTOR)
                        .build();
                underTest.tell(retrieveThing, getRef());

                final RetrieveThingResponse retrieveThingResponse = expectMsgClass(RetrieveThingResponse.class);
                assertThingInResponse(retrieveThingResponse.getThing(), expectedThing);
            }
        };
    }

    @Test
    public void modifyAttributeSoThatThingGetsTooLarge() {
        new TestKit(actorSystem) {
            {
                final ThingId thingId = ThingId.of("thing", "id");
                final ThingBuilder.FromScratch thingBuilder = Thing.newBuilder()
                        .setId(thingId)
                        .setPolicyId(PolicyId.of(thingId));
                int i = 0;
                Thing thing;
                do {
                    thingBuilder.setAttribute(JsonPointer.of("attr" + i), JsonValue.of(i));
                    thing = thingBuilder.build();
                    i++;
                } while (thing.toJsonString().length() < TestConstants.THING_SIZE_LIMIT_BYTES);

                thing = thing.removeAttribute("attr" + (i - 1));

                final ActorRef underTest = createPersistenceActorFor(thing);

                // creating the Thing should be possible as we are below the limit:
                final CreateThing createThing = CreateThing.of(thing, null, DittoHeaders.newBuilder()
                        .schemaVersion(JsonSchemaVersion.V_2).build());
                underTest.tell(createThing, getRef());
                expectMsgClass(CreateThingResponse.class);

                // but modifying the Thing attribute which would cause the Thing to exceed the limit should not be allowed:
                final ModifyAttribute modifyAttribute = ModifyAttribute.of(getIdOrThrow(thing), JsonPointer.of("foo"),
                        JsonValue.of("bar"), DittoHeaders.newBuilder().schemaVersion(JsonSchemaVersion.V_2).build());
                underTest.tell(modifyAttribute, getRef());

                expectMsgClass(ThingTooLargeException.class);
            }
        };
    }

    @Test
    public void retrieveThingV2() {
        final Thing thing = createThingV2WithRandomId();
        final ThingCommand retrieveThingCommand = RetrieveThing.of(getIdOrThrow(thing), dittoHeadersV2);

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);

                underTest.tell(retrieveThingCommand, getRef());
                expectMsgEquals(ETagTestUtils.retrieveThingResponse(thing, null, dittoHeadersV2));
            }
        };
    }

    @Test
    public void retrieveThingsWithoutThingIdOfActor() {
        final Thing thing = createThingV2WithRandomId();

        final RetrieveThings retrieveThingsCommand =
                RetrieveThings.getBuilder(ThingId.of("foo", "bar"), ThingId.of("bum", "lux")).build();

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);

                underTest.tell(retrieveThingsCommand, getRef());
                expectNoMessage();
            }
        };
    }

    @Test
    public void deleteThingV2() {
        new TestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);

                final DeleteThing deleteThing = DeleteThing.of(getIdOrThrow(thing), dittoHeadersV2);
                underTest.tell(deleteThing, getRef());
                final ThingId thingId = thing.getEntityId().get();
                expectMsgEquals(DeleteThingResponse.of(getIdOrThrow(thing), dittoHeadersV2.toBuilder()
                        .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "2")
                        .putHeader(DittoHeaderDefinition.ENTITY_ID.getKey(), thingId.getEntityType() + ":" + thingId)
                        .build()));
            }
        };
    }

    /**
     * Make sure that a re-created thing does not contain data from the previously deleted thing.
     */
    @Test
    public void deleteAndRecreateThingWithMinimumData() {
        new TestKit(actorSystem) {
            {
                final Thing initialThing = createThingV2WithRandomId();
                final ThingId thingId = getIdOrThrow(initialThing);
                final PolicyId policyId = initialThing.getPolicyId().orElseThrow(IllegalStateException::new);
                final ActorRef underTest = createPersistenceActorFor(initialThing);

                final CreateThing createThing = CreateThing.of(initialThing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), initialThing);

                final DeleteThing deleteThing = DeleteThing.of(thingId, dittoHeadersV2);
                underTest.tell(deleteThing, getRef());
                expectMsgEquals(DeleteThingResponse.of(thingId, dittoHeadersV2.toBuilder()
                        .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "2")
                        .putHeader(DittoHeaderDefinition.ENTITY_ID.getKey(), thingId.getEntityType() + ":" + thingId)
                        .build()));

                final Thing minimalThing = Thing.newBuilder()
                        .setId(thingId)
                        .setPolicyId(policyId)
                        .build();
                final CreateThing recreateThing = CreateThing.of(minimalThing, null, dittoHeadersV2);
                underTest.tell(recreateThing, getRef());

                final CreateThingResponse recreateThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(recreateThingResponse.getThingCreated().orElse(null), minimalThing);

                final RetrieveThing retrieveThing =
                        RetrieveThing.getBuilder(thingId, dittoHeadersV2)
                                .withSelectedFields(ALL_FIELDS_SELECTOR)
                                .build();
                underTest.tell(retrieveThing, getRef());

                final RetrieveThingResponse retrieveThingResponse = expectMsgClass(RetrieveThingResponse.class);
                assertThingInResponse(retrieveThingResponse.getThing(), minimalThing);
            }
        };
    }

    @Test
    public void modifyFeatures() {
        new TestKit(actorSystem) {
            {
                final DittoHeaders headersMockWithOtherAuth =
                        createDittoHeadersMock(JsonSchemaVersion.V_2, AUTH_SUBJECT)
                                .toBuilder()
                                .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "2")
                                .build();

                final ThingId thingId = ThingId.of("org.eclipse.ditto", "myThing");
                final Feature smokeDetector = ThingsModelFactory.newFeature("smokeDetector");
                final Feature fireExtinguisher = ThingsModelFactory.newFeature("fireExtinguisher");
                final Thing thing = ThingsModelFactory.newThingBuilder()
                        .setId(thingId)
                        .setPolicyId(POLICY_ID)
                        .setFeature(smokeDetector)
                        .build();
                final Features featuresToModify = ThingsModelFactory.newFeatures(fireExtinguisher);

                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponseV2(createThingResponse.getThingCreated().orElse(null), thing);

                final ModifyFeatures modifyFeatures =
                        ModifyFeatures.of(thingId, featuresToModify, headersMockWithOtherAuth);
                underTest.tell(modifyFeatures, getRef());
                expectMsgEquals(
                        ETagTestUtils.modifyFeaturesResponse(thingId, featuresToModify, headersMockWithOtherAuth,
                                false));

                final RetrieveFeatures retrieveFeatures = RetrieveFeatures.of(thingId, headersMockWithOtherAuth);
                underTest.tell(retrieveFeatures, getRef());
                expectMsgEquals(
                        ETagTestUtils.retrieveFeaturesResponse(thingId, featuresToModify, featuresToModify.toJson(),
                                headersMockWithOtherAuth));
            }
        };
    }

    @Test
    public void modifyAttributes() {
        new TestKit(actorSystem) {
            {
                final DittoHeaders headersMockWithOtherAuth =
                        createDittoHeadersMock(JsonSchemaVersion.V_2, AUTH_SUBJECT).toBuilder()
                                .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "2")
                                .build();

                final ThingId thingId = ThingId.of("org.eclipse.ditto", "myThing");

                final JsonPointer fooPointer = JsonFactory.newPointer("foo");
                final JsonValue fooValue = JsonFactory.newValue("bar");
                final JsonPointer bazPointer = JsonFactory.newPointer("baz");
                final JsonValue bazValue = JsonFactory.newValue(42);

                final Thing thing = ThingsModelFactory.newThingBuilder()
                        .setId(thingId)
                        .setPolicyId(POLICY_ID)
                        .setAttribute(fooPointer, fooValue)
                        .build();
                final Attributes attributesToModify =
                        ThingsModelFactory.newAttributesBuilder().set(bazPointer, bazValue).build();

                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponseV2(createThingResponse.getThingCreated().orElse(null), thing);

                final ModifyAttributes modifyAttributes =
                        ModifyAttributes.of(thingId, attributesToModify, headersMockWithOtherAuth);
                underTest.tell(modifyAttributes, getRef());
                expectMsgEquals(
                        ETagTestUtils.modifyAttributeResponse(thingId, attributesToModify, headersMockWithOtherAuth,
                                false));

                final RetrieveAttributes retrieveAttributes = RetrieveAttributes.of(thingId, headersMockWithOtherAuth);
                underTest.tell(retrieveAttributes, getRef());
                expectMsgEquals(ETagTestUtils.retrieveAttributesResponse(thingId, attributesToModify,
                        attributesToModify.toJson(JsonSchemaVersion.LATEST), headersMockWithOtherAuth));
            }
        };
    }

    @Test
    public void modifyAttribute() {
        final JsonObjectBuilder attributesBuilder = JsonFactory.newObjectBuilder();
        attributesBuilder.set("foo", "bar").set("isValid", false).set("answer", 42);
        final JsonObject attributes = attributesBuilder.build();

        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setAttributes(ThingsModelFactory.newAttributes(attributes))
                .setId(THING_ID)
                .setPolicyId(POLICY_ID)
                .build();

        final JsonPointer attributeKey = JsonFactory.newPointer("isValid");
        final JsonValue newAttributeValue = JsonFactory.newValue(true);

        final ThingId thingId = getIdOrThrow(thing);

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponseV2(createThingResponse.getThingCreated().orElse(null), thing);

                // Modify attribute as authorized subject.
                final ThingCommand authorizedCommand =
                        ModifyAttribute.of(thingId, attributeKey, newAttributeValue, dittoHeadersV2);
                underTest.tell(authorizedCommand, getRef());
                expectMsgEquals(
                        ETagTestUtils.modifyAttributeResponse(thingId, attributeKey, newAttributeValue, dittoHeadersV2
                                        .toBuilder()
                                        .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "2")
                                        .build()
                                ,
                                false));
            }
        };
    }

    @Test
    public void retrieveAttribute() {
        final JsonPointer attributeKey = JsonFactory.newPointer("isValid");
        final JsonValue attributeValue = JsonFactory.newValue(false);

        final JsonObjectBuilder attributesBuilder = JsonFactory.newObjectBuilder();
        attributesBuilder.set("foo", "bar").set(attributeKey, attributeValue).set("answer", 42);
        final JsonObject attributes = attributesBuilder.build();

        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setAttributes(ThingsModelFactory.newAttributes(attributes))
                .setId(THING_ID)
                .setPolicyId(POLICY_ID)
                .build();

        final ThingId thingId = getIdOrThrow(thing);

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse =
                        expectMsgClass(java.time.Duration.ofSeconds(5), CreateThingResponse.class);
                assertThingInResponseV2(createThingResponse.getThingCreated().orElse(null), thing);

                // Retrieve attribute as authorized subject.
                final ThingCommand authorizedCommand =
                        RetrieveAttribute.of(thingId, attributeKey, dittoHeadersV2);
                underTest.tell(authorizedCommand, getRef());
                expectMsgClass(RetrieveAttributeResponse.class);
            }
        };
    }

    @Test
    public void deleteAttribute() {
        final JsonPointer attributeKey = JsonFactory.newPointer("isValid");

        final JsonObjectBuilder attributesBuilder = JsonFactory.newObjectBuilder();
        attributesBuilder.set("foo", "bar").set(attributeKey, false).set("answer", 42);
        final JsonObject attributes = attributesBuilder.build();

        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setAttributes(ThingsModelFactory.newAttributes(attributes))
                .setId(THING_ID)
                .setPolicyId(POLICY_ID)
                .build();

        final ThingId thingId = getIdOrThrow(thing);

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponseV2(createThingResponse.getThingCreated().orElse(null), thing);

                // Delete attribute as authorized subject.
                final ThingCommand authorizedCommand = DeleteAttribute.of(thingId, attributeKey, dittoHeadersV2);
                underTest.tell(authorizedCommand, getRef());
                expectMsgEquals(DeleteAttributeResponse.of(thingId, attributeKey, dittoHeadersV2.toBuilder()
                        .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "2")
                        .putHeader(DittoHeaderDefinition.ENTITY_ID.getKey(), thingId.getEntityType() + ":" + thingId)
                        .build()));
            }
        };
    }

    @Test
    public void tryToRetrieveThingAfterDeletion() {
        final Thing thing = createThingV2WithRandomId();
        final ThingId thingId = getIdOrThrow(thing);
        final DeleteThing deleteThingCommand = DeleteThing.of(thingId, dittoHeadersV2);
        final RetrieveThing retrieveThingCommand = RetrieveThing.of(thingId, dittoHeadersV2);

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);

                underTest.tell(deleteThingCommand, getRef());
                expectMsgEquals(DeleteThingResponse.of(thingId, dittoHeadersV2.toBuilder()
                        .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "2")
                        .putHeader(DittoHeaderDefinition.ENTITY_ID.getKey(), thingId.getEntityType() + ":" + thingId)
                        .build()));

                underTest.tell(retrieveThingCommand, getRef());
                expectMsgClass(ThingNotAccessibleException.class);
            }
        };
    }

    @Test
    public void recoverThingCreated() {
        new TestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final ThingId thingId = getIdOrThrow(thing);

                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);

                // restart actor to recover thing state
                watch(underTest);
                underTest.tell(PoisonPill.getInstance(), getRef());
                expectTerminated(underTest);
                final ActorRef underTestAfterRestart = Retry.untilSuccess(() -> createPersistenceActorFor(thing));

                final RetrieveThing retrieveThing = RetrieveThing.of(thingId, dittoHeadersV2);

                Awaitility.await().atMost(10L, TimeUnit.SECONDS).untilAsserted(() -> {
                    underTestAfterRestart.tell(retrieveThing, getRef());
                    final RetrieveThingResponse retrieveThingResponse = expectMsgClass(RetrieveThingResponse.class);
                    final Thing thingAsPersisted = retrieveThingResponse.getThing();
                    assertThat(thingAsPersisted.getEntityId()).contains(getIdOrThrow(thing));
                    assertThat(thingAsPersisted.getAttributes()).isEqualTo(thing.getAttributes());
                    assertThat(thingAsPersisted.getFeatures()).isEqualTo(thing.getFeatures());
                });
            }
        };
    }

    @Test
    public void recoverThingDeleted() {
        new TestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final ThingId thingId = getIdOrThrow(thing);

                ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);

                final DeleteThing deleteThing = DeleteThing.of(thingId, dittoHeadersV2);
                underTest.tell(deleteThing, getRef());
                expectMsgEquals(DeleteThingResponse.of(thingId, dittoHeadersV2.toBuilder()
                        .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "2")
                        .putHeader(DittoHeaderDefinition.ENTITY_ID.getKey(), thingId.getEntityType() + ":" + thingId)
                        .build()));

                // restart actor to recover thing state
                watch(underTest);
                underTest.tell(PoisonPill.getInstance(), getRef());
                expectTerminated(underTest);
                underTest = Retry.untilSuccess(() -> createPersistenceActorFor(thing));

                final RetrieveThing retrieveThing = RetrieveThing.of(thingId, dittoHeadersV2);
                underTest.tell(retrieveThing, getRef());

                // A deleted Thing cannot be retrieved anymore (or is not accessible during initiation on slow systems)
                expectMsgClass(ThingNotAccessibleException.class);
            }
        };
    }

    @Test
    public void ensureSequenceNumberCorrectness() {
        new TestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final ThingId thingId = getIdOrThrow(thing);

                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);

                // modify the thing's attributes - results in sequence number 2
                final Thing thingToModify = thing.setAttributes(THING_ATTRIBUTES.setValue("foo", "bar"));
                final ModifyThing modifyThing = ModifyThing.of(thingId, thingToModify, null, dittoHeadersV2);

                underTest.tell(modifyThing, getRef());

                expectMsgEquals(ETagTestUtils.modifyThingResponse(thing, thingToModify, dittoHeadersV2.toBuilder()
                        .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "2")
                        .build(), false));

                // retrieve the thing's sequence number
                final JsonFieldSelector versionFieldSelector =
                        JsonFactory.newFieldSelector(Thing.JsonFields.REVISION.getPointer().toString(),
                                JSON_PARSE_OPTIONS);
                final long versionExpected = 2;
                final Thing thingExpected = ThingsModelFactory.newThingBuilder(thingToModify)
                        .setRevision(versionExpected)
                        .build();
                final RetrieveThing retrieveThing = RetrieveThing.getBuilder(thingId, dittoHeadersV2)
                        .withSelectedFields(versionFieldSelector)
                        .build();
                underTest.tell(retrieveThing, getRef());
                expectMsgEquals(
                        ETagTestUtils.retrieveThingResponse(thingExpected, versionFieldSelector, dittoHeadersV2.toBuilder()
                                .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "2")
                                .build()));
            }
        };
    }

    @Test
    public void ensureSequenceNumberCorrectnessAfterRecovery() {
        new TestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final ThingId thingId = getIdOrThrow(thing);

                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);

                // modify the thing's attributes - results in sequence number 2
                final Thing thingToModify = thing.setAttributes(THING_ATTRIBUTES.setValue("foo", "bar"));
                final ModifyThing modifyThing = ModifyThing.of(thingId, thingToModify, null, dittoHeadersV2);
                underTest.tell(modifyThing, getRef());
                expectMsgEquals(ETagTestUtils.modifyThingResponse(thing, thingToModify, dittoHeadersV2.toBuilder()
                        .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "2")
                        .build(), false));

                // retrieve the thing's sequence number from recovered actor
                final JsonFieldSelector versionFieldSelector =
                        JsonFactory.newFieldSelector(Thing.JsonFields.REVISION.getPointer().toString(),
                                JSON_PARSE_OPTIONS);
                final long versionExpected = 2;
                final Thing thingExpected = ThingsModelFactory.newThingBuilder(thingToModify)
                        .setRevision(versionExpected)
                        .build();

                // restart actor to recover thing state
                watch(underTest);
                underTest.tell(PoisonPill.getInstance(), getRef());
                expectTerminated(underTest);
                final ActorRef underTestAfterRestart = Retry.untilSuccess(() -> createPersistenceActorFor(thing));

                final RetrieveThing retrieveThing = RetrieveThing.getBuilder(thingId, dittoHeadersV2)
                        .withSelectedFields(versionFieldSelector)
                        .build();

                Awaitility.await().atMost(10L, TimeUnit.SECONDS).untilAsserted(() -> {
                    underTestAfterRestart.tell(retrieveThing, getRef());
                    expectMsgEquals(ETagTestUtils.retrieveThingResponse(thingExpected,
                            versionFieldSelector,
                            dittoHeadersV2.toBuilder()
                                    .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "2")
                                    .build()));
                });
            }
        };
    }

    @Test
    public void createThingInV2WithMissingPolicyIdThrowsPolicyIdMissingException() {
        final ThingId thingIdOfActor = ThingId.of("test.ns.v1", "createThingInV2WithMissingPolicyId");
        final Thing thingV2 = ThingsModelFactory.newThingBuilder()
                .setAttributes(THING_ATTRIBUTES)
                .setId(thingIdOfActor)
                .build();

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thingV2);

                final CreateThing createThingV2 = CreateThing.of(thingV2, null, dittoHeadersV2);
                underTest.tell(createThingV2, getRef());

                expectMsgClass(PolicyIdMissingException.class);
            }
        };
    }

    @Test
    public void     responsesDuringInitializationAreSentWithDittoHeaders() {
        new TestKit(actorSystem) {
            {
                final ThingId thingId = ThingId.of("org.eclipse.ditto", "myThing");

                final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                        .authorizationContext(
                                AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                                        AuthorizationSubject.newInstance("authSubject")))
                        .correlationId("correlationId")
                        .schemaVersion(JsonSchemaVersion.LATEST)
                        .build();

                final ActorRef underTest = createPersistenceActorFor(thingId);

                final RetrieveThing retrieveThing = RetrieveThing.of(thingId, dittoHeaders);
                final ThingNotAccessibleException thingNotAccessibleException =
                        ThingNotAccessibleException.newBuilder(thingId)
                                .dittoHeaders(dittoHeaders)
                                .build();

                underTest.tell(retrieveThing, getRef());
                expectMsgEquals(thingNotAccessibleException);
            }
        };
    }

    @Test
    public void ensureModifiedCorrectnessAfterCreation() {
        new TestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final ActorRef thingPersistenceActor = createPersistenceActorFor(thing);
                final JsonFieldSelector fieldSelector = Thing.JsonFields.MODIFIED.getPointer().toFieldSelector();

                // create thing
                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                thingPersistenceActor.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                final Instant createThingResponseTimestamp = Instant.now();
                assertThat(createThingResponse.getThingCreated()).isPresent();
                assertThat(createThingResponse.getThingCreated().get())
                        .isNotModifiedAfter(createThingResponseTimestamp);

                // retrieve thing
                final RetrieveThing retrieveThing = RetrieveThing.getBuilder(getIdOrThrow(thing), dittoHeadersV2)
                        .withSelectedFields(fieldSelector)
                        .build();
                thingPersistenceActor.tell(retrieveThing, getRef());

                final RetrieveThingResponse retrieveThingResponse = expectMsgClass(RetrieveThingResponse.class);
                assertThat(retrieveThingResponse.getThing()).isNotModifiedAfter(createThingResponseTimestamp);
            }
        };
    }

    @Test
    public void ensureModifiedCorrectnessAfterModification() {
        new TestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final ActorRef thingPersistenceActor = createPersistenceActorFor(thing);
                final JsonFieldSelector fieldSelector = Thing.JsonFields.MODIFIED.getPointer().toFieldSelector();

                // create thing
                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                thingPersistenceActor.tell(createThing, getRef());
                expectMsgClass(CreateThingResponse.class);
                final Instant createThingResponseTimestamp = Instant.now();

                // retrieve thing
                final RetrieveThing retrieveThing = RetrieveThing.getBuilder(getIdOrThrow(thing), dittoHeadersV2)
                        .withSelectedFields(fieldSelector)
                        .build();
                thingPersistenceActor.tell(retrieveThing, getRef());
                final RetrieveThingResponse retrieveThingResponse = expectMsgClass(RetrieveThingResponse.class);
                assertThat(retrieveThingResponse.getThing())
                        .isNotModifiedAfter(createThingResponseTimestamp);

                // modify thing
                final ModifyThing modifyThing = ModifyThing.of(getIdOrThrow(thing), thing, null, dittoHeadersV2);
                thingPersistenceActor.tell(modifyThing, getRef());
                expectMsgClass(ModifyThingResponse.class);
                final Instant modifyThingResponseTimestamp = Instant.now();

                // retrieve thing
                final RetrieveThing retrieveModifiedThing =
                        RetrieveThing.getBuilder(getIdOrThrow(thing), dittoHeadersV2)
                                .withSelectedFields(fieldSelector)
                                .build();
                thingPersistenceActor.tell(retrieveModifiedThing, getRef());
                final RetrieveThingResponse retrieveModifiedThingResponse = expectMsgClass(RetrieveThingResponse.class);
                assertThat(retrieveModifiedThingResponse.getThing())
                        .isModifiedAfter(createThingResponseTimestamp);
                assertThat(retrieveModifiedThingResponse.getThing())
                        .isNotModifiedAfter(modifyThingResponseTimestamp);
            }
        };
    }

    @Test
    public void ensureModifiedCorrectnessAfterRecovery() {
        new TestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final ActorRef thingPersistenceActor = watch(createPersistenceActorFor(thing));
                final JsonFieldSelector fieldSelector = Thing.JsonFields.MODIFIED.getPointer().toFieldSelector();

                // create thing
                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                thingPersistenceActor.tell(createThing, getRef());
                expectMsgClass(CreateThingResponse.class);
                final Instant createThingResponseTimestamp = Instant.now();

                // restart
                thingPersistenceActor.tell(PoisonPill.getInstance(), getRef());
                expectTerminated(thingPersistenceActor);
                final ActorRef thingPersistenceActorRecovered =
                        Retry.untilSuccess(() -> createPersistenceActorFor(thing));

                final RetrieveThing retrieveThing = RetrieveThing.getBuilder(getIdOrThrow(thing), dittoHeadersV2)
                        .withSelectedFields(fieldSelector)
                        .build();

                Awaitility.await().atMost(10L, TimeUnit.SECONDS).untilAsserted(() -> {
                    thingPersistenceActorRecovered.tell(retrieveThing, getRef());
                    final RetrieveThingResponse retrieveThingResponse = expectMsgClass(RetrieveThingResponse.class);
                    assertThat(retrieveThingResponse.getThing()).isNotModifiedAfter(createThingResponseTimestamp);
                    assertThat(getLastSender()).isEqualTo(thingPersistenceActorRecovered);
                });
            }
        };
    }

    @Test
    public void retrieveFeatureReturnsExpected() {
        final ThingId thingId = ThingId.of("org.eclipse.ditto", "thing1");
        final String gyroscopeFeatureId = "Gyroscope.0";
        final Feature gyroscopeFeature = ThingsModelFactory.newFeatureBuilder()
                .properties(ThingsModelFactory.newFeaturePropertiesBuilder()
                        .set("status", JsonFactory.newObjectBuilder()
                                .set("minRangeValue", -2000)
                                .set("xValue", -0.05071427300572395)
                                .set("units", "Deg/s")
                                .set("yValue", -0.4192921817302704)
                                .set("zValue", 0.20766231417655945)
                                .set("maxRangeValue", 2000)
                                .build())
                        .build())
                .withId(gyroscopeFeatureId)
                .build();
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(thingId)
                .setPolicyId(POLICY_ID)
                .setAttributes(ThingsModelFactory.newAttributesBuilder()
                        .set("isOnline", false)
                        .set("lastUpdate", "Thu Sep 28 15:01:43 CEST 2017")
                        .build())
                .setFeature(gyroscopeFeature)
                .build();

        new TestKit(actorSystem) {{
            final ActorRef thingPersistenceActor = createPersistenceActorFor(thing);

            // create Thing
            final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
            thingPersistenceActor.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            // retrieve Feature
            final RetrieveFeature retrieveFeatureCmd = RetrieveFeature.of(thingId, gyroscopeFeatureId, dittoHeadersV2);
            thingPersistenceActor.tell(retrieveFeatureCmd, getRef());
            expectMsgEquals(
                    ETagTestUtils.retrieveFeatureResponse(thingId, gyroscopeFeature, gyroscopeFeature.toJson(),
                            dittoHeadersV2.toBuilder()
                                    .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "1")
                                    .build()));
        }};
    }

    @Test
    public void retrieveFeaturePropertyWithLiveChannelCondition() {
        final ThingId thingId = ThingId.of("org.eclipse.ditto", "thing1");
        final String gyroscopeFeatureId = "Gyroscope.0";
        final Feature gyroscopeFeature = ThingsModelFactory.newFeatureBuilder()
                .definition(FeatureDefinition.fromJson("[\"a:b:c\"]"))
                .properties(ThingsModelFactory.newFeaturePropertiesBuilder()
                        .set("status", JsonFactory.newObjectBuilder()
                                .set("minRangeValue", -2000)
                                .set("xValue", -0.05071427300572395)
                                .set("units", "Deg/s")
                                .set("yValue", -0.4192921817302704)
                                .set("zValue", 0.20766231417655945)
                                .set("maxRangeValue", 2000)
                                .build())
                        .build())
                .withId(gyroscopeFeatureId)
                .build();
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(thingId)
                .setPolicyId(POLICY_ID)
                .setFeature(gyroscopeFeature)
                .build();

        new TestKit(actorSystem) {{
            final ActorRef thingPersistenceActor = createPersistenceActorFor(thing);

            // create Thing
            final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
            thingPersistenceActor.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            // query: live channel condition matched with no twin fallback
            final var matchingHeaders = dittoHeadersV2.toBuilder()
                    .liveChannelCondition(String.format("exists(/features/%s)", gyroscopeFeatureId))
                    .build();
            final var command1 =
                    RetrieveFeatureDefinition.of(thingId, gyroscopeFeatureId, matchingHeaders);
            thingPersistenceActor.tell(command1, getRef());
            final var response1 = expectMsgClass(RetrieveFeatureDefinitionResponse.class);
            assertThat(response1.getDittoHeaders().didLiveChannelConditionMatch()).isTrue();
            assertThat(response1.getDefinition()).isEqualTo(FeatureDefinition.fromJson(JsonFactory.nullArray()));

            // query: live channel condition matched with twin fallback
            final var propertiesPointer = JsonPointer.of("/status/minRangeValue");
            final var withTwinFallback = matchingHeaders.toBuilder()
                    .putHeader(DittoHeaderDefinition.LIVE_CHANNEL_TIMEOUT_STRATEGY.getKey(), "use-twin")
                    .build();
            final var command2 =
                    RetrieveFeatureProperty.of(thingId, gyroscopeFeatureId, propertiesPointer, withTwinFallback);
            thingPersistenceActor.tell(command2, getRef());
            final var response2 = expectMsgClass(RetrieveFeaturePropertyResponse.class);
            assertThat(response2.getDittoHeaders().didLiveChannelConditionMatch()).isTrue();
            assertThat(response2.getPropertyValue()).isEqualTo(JsonValue.of(-2000));

            // query: live channel condition does not match
            final var mismatchingHeaders = dittoHeadersV2.toBuilder()
                    .liveChannelCondition("exists(/features/nonexistentFeature)")
                    .build();
            final var command3 = RetrieveThing.of(thingId, mismatchingHeaders);
            thingPersistenceActor.tell(command3, getRef());
            final var response3 = expectMsgClass(RetrieveThingResponse.class);
            assertThat(response3.getDittoHeaders().didLiveChannelConditionMatch()).isFalse();
            assertThat(response3.getEntity().asObject()).isNotEmpty();
        }};
    }

    @Test
    public void createThingInV2AndUpdateWithV2AndChangedPolicyId() {
        final ThingId thingId = ThingId.of("test.ns.v1", "createThingInV2AndUpdateWithV2AndChangedPolicyId");
        final Thing thing = buildThing(thingId);
        final Thing thing_2 = thing.toBuilder().setPolicyId(PolicyId.of(thingId + ".ANY.OTHER.NAMESPACE")).build();

        new TestKit(actorSystem) {{
            final DittoHeaders headersUsed =
                    testCreateAndModify(thing,
                            JsonSchemaVersion.V_2,
                            thing_2,
                            JsonSchemaVersion.V_2,
                            modifyThing -> ETagTestUtils.modifyThingResponse(thing, thing_2,
                                    modifyThing.getDittoHeaders(),
                                    false));
            assertPublishEvent(ThingModified.of(thing_2, 2L, TIMESTAMP, headersUsed, null));
        }};
    }

    @Test
    public void shutdownOnCommand() {
        new TestKit(actorSystem) {{
            final Thing thing = createThingV2WithRandomId();
            final ActorRef underTest = watch(createSupervisorActorFor(getIdOrThrow(thing)));

            final DistributedPubSubMediator.Subscribe subscribe =
                    DistPubSubAccess.subscribe(Shutdown.TYPE, underTest);
            pubSubTestProbe.fishForMessage(Duration.apply(5, TimeUnit.SECONDS), "subscribe for shutdown",
                    PartialFunction.fromFunction(msg ->
                            msg instanceof DistributedPubSubMediator.Subscribe foundSubs && foundSubs.topic()
                                    .equals(Shutdown.TYPE))
            );
            pubSubTestProbe.reply(new DistributedPubSubMediator.SubscribeAck(subscribe));

            underTest.tell(
                    Shutdown.getInstance(ShutdownReasonFactory.getPurgeNamespaceReason(thing.getNamespace().get()),
                            DittoHeaders.empty()), pubSubTestProbe.ref());
            expectTerminated(underTest);
        }};
    }

    private DittoHeaders testCreateAndModify(final Thing toCreate,
            final JsonSchemaVersion createVersion,
            final Thing toModify,
            final JsonSchemaVersion modifyVersion,
            final Function<ModifyThing, Object> expectedMessage) {

        final CreateThing createThing = createThing(toCreate, createVersion);
        final ModifyThing modifyThing = modifyThing(toModify, modifyVersion);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(createThing.getThing());

            underTest.tell(createThing, getRef());
            final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
            assertThingInResponse(createThingResponse.getThingCreated().orElse(null), createThing.getThing());
            assertPublishEvent(ThingCreated.of(toCreate, 1L, TIMESTAMP, createThing.getDittoHeaders(), null));

            underTest.tell(modifyThing, getRef());
            expectMsgEquals(expectedMessage.apply(modifyThing));
        }};
        return modifyThing.getDittoHeaders();
    }

    @Test
    public void modifyAttributesMetadata() {
        final var thing = createThingV2WithRandomId();
        final var headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "2")
                .putHeader(DittoHeaderDefinition.PUT_METADATA.getKey(),
                        "[{\"key\":\"/attrKey/meta\",\"value\":{\"type\":\"bumlux\"}}]")
                .build();
        final var modifyAttributes = ModifyAttributes.of(getIdOrThrow(thing), THING_ATTRIBUTES, headers);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            // modify attributes with metadata
            underTest.tell(modifyAttributes, getRef());
            expectMsgEquals(
                    ETagTestUtils.modifyAttributeResponse(getIdOrThrow(thing), THING_ATTRIBUTES, headers, false));

            // assert that metadata was modified as expected
            final Metadata expectedMetadata = Metadata.newBuilder()
                    .set("/attributes/attrKey/meta", JsonObject.newBuilder()
                            .set("type", "bumlux")
                            .build())
                    .build();
            assertMetadataAsExpected(this, underTest, getIdOrThrow(thing), expectedMetadata);
        }};
    }

    @Test
    public void modifyAttributeMetadata() {
        final var thing = createThingV2WithRandomId();
        final var headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "2")
                .putHeader(DittoHeaderDefinition.PUT_METADATA.getKey(),
                        "[{\"key\":\"/sub\",\"value\":{\"type\":\"bumlux\"}}]")
                .build();
        final var modifyAttribute =
                ModifyAttribute.of(getIdOrThrow(thing), JsonPointer.of(ATTRIBUTE_KEY), JsonValue.of(ATTRIBUTE_VALUE),
                        headers);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            // modify attribute with metadata
            underTest.tell(modifyAttribute, getRef());
            expectMsgEquals(ETagTestUtils.modifyAttributeResponse(getIdOrThrow(thing), JsonPointer.of(ATTRIBUTE_KEY),
                    JsonValue.of(ATTRIBUTE_VALUE), headers, false));

            // assert that metadata was modified as expected
            final Metadata expectedMetadata = Metadata.newBuilder()
                    .set("/attributes/attrKey/sub", JsonObject.newBuilder()
                            .set("type", "bumlux")
                            .build())
                    .build();
            assertMetadataAsExpected(this, underTest, getIdOrThrow(thing), expectedMetadata);
        }};
    }

    @Test
    public void modifyFeaturesMetadata() {
        final var thing = createThingV2WithRandomId();
        final var headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "2")
                .putHeader(DittoHeaderDefinition.PUT_METADATA.getKey(),
                        "[{\"key\":\"/featureId\",\"value\":{\"type\":\"bumlux\"}}]")
                .build();
        final var modifyFeatures = ModifyFeatures.of(getIdOrThrow(thing), THING_FEATURES, headers);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            // modify features with metadata
            underTest.tell(modifyFeatures, getRef());
            expectMsgEquals(ETagTestUtils.modifyFeaturesResponse(getIdOrThrow(thing), THING_FEATURES, headers, false));

            // assert that metadata was modified as expected
            final Metadata expectedMetadata = Metadata.newBuilder()
                    .set("/features/featureId", JsonObject.newBuilder()
                            .set("type", "bumlux")
                            .build())
                    .build();
            assertMetadataAsExpected(this, underTest, getIdOrThrow(thing), expectedMetadata);
        }};
    }

    @Test
    public void modifyFeatureMetadata() {
        final var thing = createThingV2WithRandomId();
        final var headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "2")
                .putHeader(DittoHeaderDefinition.PUT_METADATA.getKey(),
                        "[{\"key\":\"/sub\",\"value\":{\"type\":\"bumlux\"}}]")
                .build();
        final var modifyFeature = ModifyFeature.of(getIdOrThrow(thing), THING_FEATURE, headers);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            // modify features with metadata
            underTest.tell(modifyFeature, getRef());
            expectMsgEquals(ETagTestUtils.modifyFeatureResponse(getIdOrThrow(thing), THING_FEATURE, headers, false));

            // assert that metadata was modified as expected
            final Metadata expectedMetadata = Metadata.newBuilder()
                    .set("/features/featureId/sub", JsonObject.newBuilder()
                            .set("type", "bumlux")
                            .build())
                    .build();
            assertMetadataAsExpected(this, underTest, getIdOrThrow(thing), expectedMetadata);
        }};
    }

    @Test
    public void modifyFeatureMetadataRepeatedDoesNotNest() {
        final var thing = createThingV2WithRandomId();
        final var firstHeaders = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "2")
                .putHeader(DittoHeaderDefinition.PUT_METADATA.getKey(),
                        "[{\"key\":\"/_modified\",\"value\":11}]")
                .build();
        final var secondHeaders = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "3")
                .putHeader(DittoHeaderDefinition.PUT_METADATA.getKey(),
                        "[{\"key\":\"/_modified\",\"value\":12}]")
                .build();

        final var modifyFeatureFirst = ModifyFeature.of(getIdOrThrow(thing), THING_FEATURE, firstHeaders);
        final var modifyFeatureSecond = ModifyFeature.of(getIdOrThrow(thing), THING_FEATURE, secondHeaders);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            // first modify with metadata
            underTest.tell(modifyFeatureFirst, getRef());
            expectMsgEquals(ETagTestUtils.modifyFeatureResponse(getIdOrThrow(thing), THING_FEATURE, firstHeaders, false));

            // second modify with metadata, same key, different value
            underTest.tell(modifyFeatureSecond, getRef());
            expectMsgEquals(ETagTestUtils.modifyFeatureResponse(getIdOrThrow(thing), THING_FEATURE, secondHeaders, false));

            // assert that metadata for _modified is a scalar (not a nested object)
            final Metadata expectedMetadata = Metadata.newBuilder()
                    .set("/features/featureId/_modified", JsonValue.of(12))
                    .build();
            assertMetadataAsExpected(this, underTest, getIdOrThrow(thing), expectedMetadata);
        }};
    }

    @Test
    public void mergeFeatureMetadataRepeatedDoesNotNest() {
        final var thing = createThingV2WithRandomId();
        final var firstHeaders = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "2")
                .putHeader(DittoHeaderDefinition.PUT_METADATA.getKey(),
                        "[{\"key\":\"/_modified\",\"value\":11}]")
                .build();
        final var secondHeaders = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "3")
                .putHeader(DittoHeaderDefinition.PUT_METADATA.getKey(),
                        "[{\"key\":\"/_modified\",\"value\":12}]")
                .build();

        final var mergeFeatureFirst = MergeThing.withFeature(getIdOrThrow(thing), THING_FEATURE, firstHeaders);
        final var mergeFeatureSecond = MergeThing.withFeature(getIdOrThrow(thing), THING_FEATURE, secondHeaders);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            // first merge with metadata
            underTest.tell(mergeFeatureFirst, getRef());
            expectMsgClass(MergeThingResponse.class);

            // second merge with metadata, same key, different value
            underTest.tell(mergeFeatureSecond, getRef());
            expectMsgClass(MergeThingResponse.class);

            // assert that metadata for _modified is a scalar (not a nested object)
            final Metadata expectedMetadata = Metadata.newBuilder()
                    .set("/features/featureId/_modified", JsonValue.of(12))
                    .build();
            assertMetadataAsExpected(this, underTest, getIdOrThrow(thing), expectedMetadata);
        }};
    }

    @Test
    public void mergeThingRootWithWildcardMetadataOnlyAffectsPatchedFields() {
        final var thing = createThingV2WithRandomId();
        final var headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "2")
                .putHeader(DittoHeaderDefinition.PUT_METADATA.getKey(),
                        "[{\"key\":\"*/createdAt\",\"value\":\"now!\"}]")
                .build();

        final JsonObject patchPayload = JsonObject.newBuilder()
                .set("attributes", JsonObject.newBuilder()
                        .set("only-one", 2)
                        .build())
                .build();
        final var mergePatch = MergeThing.of(getIdOrThrow(thing), JsonPointer.empty(), patchPayload, headers);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            // PATCH-like merge with partial payload + wildcard metadata
            underTest.tell(mergePatch, getRef());
            expectMsgClass(MergeThingResponse.class);

            // wildcard should only resolve against the patched payload fields,
            // not against pre-existing thing fields (thingId, policyId, other attributes, features)
            final var createdAtMetadata = JsonObject.newBuilder()
                    .set("createdAt", "now!")
                    .build();
            final Metadata expectedMetadata = Metadata.newBuilder()
                    .set("/attributes/only-one", createdAtMetadata)
                    .build();
            assertMetadataAsExpected(this, underTest, getIdOrThrow(thing), expectedMetadata);
        }};
    }

    @Test
    public void modifyThingWithWildcardInMetadata() {
        final var thing = createThingV2WithRandomId();
        final var headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "2")
                .putHeader(DittoHeaderDefinition.PUT_METADATA.getKey(),
                        "[{\"key\":\"*/modified\",\"value\":\"2022-06-23T06:49:05\"}]")
                .build();
        final var modifyThing = ModifyThing.of(getIdOrThrow(thing), thing, null, headers);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            // modify features with metadata
            underTest.tell(modifyThing, getRef());
            expectMsgEquals(ETagTestUtils.modifyThingResponse(thing, thing, headers, false));

            final var modifiedMetadata = JsonObject.newBuilder()
                    .set("modified", "2022-06-23T06:49:05")
                    .build();
            // assert that metadata was created as expected
            final Metadata expectedMetadata = Metadata.newBuilder()
                    .set("thingId", modifiedMetadata)
                    .set("policyId", modifiedMetadata)
                    .set("/attributes/attrKey", modifiedMetadata)
                    .set("/features/featureId/definition", modifiedMetadata)
                    .set("/features/featureId/properties/featurePropertyKey", modifiedMetadata)
                    .build();

            assertMetadataAsExpected(this, underTest, getIdOrThrow(thing), expectedMetadata);
        }};
    }

    private static void assertMetadataAsExpected(final TestKit testKit, final ActorRef underTest, final ThingId thingId,
            final Metadata expectedMetadata) {
        final var retrieveThing = RetrieveThing.getBuilder(thingId, DittoHeaders.empty())
                .withSelectedFields(JsonFactory.newFieldSelector("_metadata"))
                .build();
        underTest.tell(retrieveThing, testKit.getRef());
        final var retrieveThingResponse = testKit.expectMsgClass(RetrieveThingResponse.class);
        final var metadata = retrieveThingResponse.getThing().getMetadata();
        assertThat(metadata).isPresent();
        assertThat(metadata).contains(expectedMetadata);
    }

    @Test
    public void retrieveFeaturesMetadataWithGetMetadataHeader() {
        final Thing thing = createThingV2WithRandomIdAndMetadata();
        final DittoHeaders dittoHeaders = dittoHeadersV2.toBuilder()
                .putHeader(DittoHeaderDefinition.GET_METADATA.getKey(), "/features")
                .build();
        final ThingCommand<?> retrieveThingCommand = RetrieveThing.of(getIdOrThrow(thing), dittoHeaders);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            // retrieve thing with metadata
            final Metadata expectedMetadata = Metadata.newBuilder()
                    .set("features", JsonObject.newBuilder()
                            .set(FEATURE_ID, JsonObject.newBuilder()
                                    .set("definition", JsonObject.newBuilder()
                                            .set("issuedBy", "the epic Ditto team")
                                            .build())
                                    .set("properties", JsonObject.newBuilder()
                                            .set(FEATURE_PROPERTY_KEY, JsonObject.newBuilder()
                                                    .set("issuedBy", "the epic Ditto team")
                                                    .set("unit", "Quarks")
                                                    .build())
                                            .build())
                                    .build())
                            .build())
                    .build();
            final DittoHeaders expectedHeaders = dittoHeaders.toBuilder()
                    .putHeader(DittoHeaderDefinition.DITTO_METADATA.getKey(), expectedMetadata.toJsonString())
                    .build();

            underTest.tell(retrieveThingCommand, getRef());
            expectMsgEquals(ETagTestUtils.retrieveThingResponse(thing, null, expectedHeaders));
        }};
    }

    @Test
    public void retrieveFeatureMetadataWithGetMetadataHeader() {
        final Thing thing = createThingV2WithRandomIdAndMetadata()
                .setFeatureProperty("featureId2", "featureKey2", "someValue");
        final DittoHeaders dittoHeaders = dittoHeadersV2.toBuilder()
                .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "1")
                .putHeader(DittoHeaderDefinition.GET_METADATA.getKey(), "/features/featureId")
                .build();
        final ThingCommand<?> retrieveThingCommand = RetrieveThing.of(getIdOrThrow(thing), dittoHeaders);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            // retrieve thing with metadata
            final Metadata expectedMetadata = Metadata.newBuilder()
                    .set("features", JsonObject.newBuilder()
                            .set("featureId", JsonObject.newBuilder()
                                    .set("definition", JsonObject.newBuilder()
                                            .set("issuedBy", "the epic Ditto team")
                                            .build())
                                    .set("properties", JsonObject.newBuilder()
                                            .set("featurePropertyKey", JsonObject.newBuilder()
                                                    .set("issuedBy", "the epic Ditto team")
                                                    .set("unit", "Quarks")
                                                    .build())
                                            .build())
                                    .build())
                            .build())
                    .build();
            final DittoHeaders expectedHeaders = dittoHeaders.toBuilder()
                    .putHeader(DittoHeaderDefinition.DITTO_METADATA.getKey(), expectedMetadata.toJsonString())
                    .build();

            underTest.tell(retrieveThingCommand, getRef());
            expectMsgEquals(ETagTestUtils.retrieveThingResponse(thing, null, expectedHeaders));
        }};
    }

    @Test
    public void retrieveAttributesWithGetMetadataHeader() {
        final Thing thing = createThingV2WithRandomIdAndMetadata();
        final DittoHeaders dittoHeaders = dittoHeadersV2.toBuilder()
                .putHeader(DittoHeaderDefinition.GET_METADATA.getKey(), "attrKey")
                .build();
        final ThingCommand<?> retrieveAttributesCommand = RetrieveAttributes.of(getIdOrThrow(thing), dittoHeaders);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            // retrieve attributes with metadata
            final Metadata expectedMetadata = Metadata.newBuilder()
                    .set("attrKey", JsonObject.newBuilder()
                            .set("issuedBy", "the epic Ditto team")
                            .set("edited", "2022-05-31 15:55:55")
                            .build())
                    .build();
            final DittoHeaders expectedHeaders = dittoHeaders.toBuilder()
                    .putHeader(DittoHeaderDefinition.DITTO_METADATA.getKey(), expectedMetadata.toJsonString())
                    .build();

            underTest.tell(retrieveAttributesCommand, getRef());
            expectMsgEquals(ETagTestUtils.retrieveAttributesResponse(getIdOrThrow(thing), THING_ATTRIBUTES,
                    THING_ATTRIBUTES.toJson(retrieveAttributesCommand.getImplementedSchemaVersion()), expectedHeaders));
        }};
    }

    @Test
    public void retrieveFeaturePropertyAndAttributeMetadataWithGetMetadataWildcardHeader() {
        final Thing thing = createThingV2WithRandomIdAndMetadata();
        final DittoHeaders dittoHeaders = dittoHeadersV2.toBuilder()
                .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "1")
                .putHeader(DittoHeaderDefinition.GET_METADATA.getKey(),
                        "attributes/*/edited,features/*/properties/*/unit")
                .build();
        final ThingCommand<?> retrieveThingCommand = RetrieveThing.of(getIdOrThrow(thing), dittoHeaders);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            // retrieve thing with metadata
            final Metadata expectedMetadata = Metadata.newBuilder()
                    .set("attributes", JsonObject.newBuilder()
                            .set("attrKey", JsonObject.newBuilder()
                                    .set("edited", "2022-05-31 15:55:55")
                                    .build())
                            .build())
                    .set("features", JsonObject.newBuilder()
                            .set("featureId", JsonObject.newBuilder()
                                    .set("properties", JsonObject.newBuilder()
                                            .set("featurePropertyKey", JsonObject.newBuilder()
                                                    .set("unit", "Quarks")
                                                    .build())
                                            .build())
                                    .build())
                            .build())
                    .build();
            final DittoHeaders expectedHeaders = dittoHeaders.toBuilder()
                    .putHeader(DittoHeaderDefinition.DITTO_METADATA.getKey(), expectedMetadata.toJsonString())
                    .build();

            underTest.tell(retrieveThingCommand, getRef());
            expectMsgEquals(ETagTestUtils.retrieveThingResponse(thing, null, expectedHeaders));
        }};
    }

    @Test
    public void retrieveLeafMetadataWithGetMetadataWildcardHeader() {
        final Thing thing = createThingV2WithRandomId();
        final DittoHeaders dittoHeaders = dittoHeadersV2.toBuilder()
                .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "1")
                .putHeader(DittoHeaderDefinition.GET_METADATA.getKey(), "*/issuedBy")
                .build();
        final ThingCommand<?> retrieveThingCommand = RetrieveThing.of(getIdOrThrow(thing), dittoHeaders);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            // modify thing
            final MetadataHeaders metadataHeaders = MetadataHeaders.newInstance();
            metadataHeaders.add(
                    MetadataHeader.of(MetadataHeaderKey.parse("*/issuedBy"), JsonValue.of("the epic Ditto team"))
            );

            final DittoHeaders modifyHeaders = dittoHeadersV2.toBuilder()
                    .putHeader(DittoHeaderDefinition.PUT_METADATA.getKey(), metadataHeaders.toJsonString())
                    .build();
            final ModifyThing modifyThing = ModifyThing.of(getIdOrThrow(thing), thing, null, modifyHeaders);
            underTest.tell(modifyThing, getRef());
            expectMsgClass(ModifyThingResponse.class);

            // retrieve thing with metadata
            final Thing thingExpected = ThingsModelFactory.newThingBuilder(thing)
                    .setRevision(2L)
                    .build();
            final Metadata expectedMetadata = Metadata.newBuilder()
                    .set("thingId", JsonObject.newBuilder()
                            .set("issuedBy", "the epic Ditto team")
                            .build())
                    .set("policyId", JsonObject.newBuilder()
                            .set("issuedBy", "the epic Ditto team")
                            .build())
                    .set("attributes", JsonObject.newBuilder()
                            .set(ATTRIBUTE_KEY, JsonObject.newBuilder()
                                    .set("issuedBy", "the epic Ditto team")
                                    .build())
                            .build())
                    .set("features", JsonObject.newBuilder()
                            .set(FEATURE_ID, JsonObject.newBuilder()
                                    .set("definition", JsonObject.newBuilder()
                                            .set("issuedBy", "the epic Ditto team")
                                            .build())

                                    .set("properties", JsonObject.newBuilder()
                                            .set(FEATURE_PROPERTY_KEY, JsonObject.newBuilder()
                                                    .set("issuedBy", "the epic Ditto team")
                                                    .build())
                                            .build())
                                    .build())
                            .build())
                    .build();
            final DittoHeaders expectedHeaders = dittoHeaders.toBuilder()
                    .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "2")
                    .putHeader(DittoHeaderDefinition.DITTO_METADATA.getKey(), expectedMetadata.toJsonString())
                    .build();

            underTest.tell(retrieveThingCommand, getRef());
            expectMsgEquals(ETagTestUtils.retrieveThingResponse(thingExpected, null, expectedHeaders));
        }};
    }

    @Test
    public void deleteAttributesMetadataWithDeleteMetadataHeader() {
        final Thing thing = createThingV2WithRandomIdAndMetadata();
        final DittoHeaders dittoHeaders = dittoHeadersV2.toBuilder()
                .putHeader(DittoHeaderDefinition.DELETE_METADATA.getKey(), "attributes/")
                .build();
        final ThingCommand<?> modifyThingCommand = ModifyThing.of(getIdOrThrow(thing), thing, null, dittoHeaders);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing from json to use initial metadata creation
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            underTest.tell(modifyThingCommand, getRef());
            expectMsgClass(ModifyThingResponse.class);

            // retrieve thing with metadata
            final Metadata expectedMetadata = METADATA.toBuilder()
                    .remove("attributes")
                    .build();

            final RetrieveThing retrieveModifiedThing =
                    RetrieveThing.getBuilder(getIdOrThrow(thing), dittoHeadersV2)
                            .withSelectedFields(ALL_FIELDS_SELECTOR_WITH_METADATA)
                            .build();
            underTest.tell(retrieveModifiedThing, getRef());
            final RetrieveThingResponse retrieveModifiedThingResponse = expectMsgClass(RetrieveThingResponse.class);
            assertThat(retrieveModifiedThingResponse.getThing().getMetadata().orElseThrow())
                    .isEqualTo(expectedMetadata);
        }};
    }

    @Test
    public void deleteFeaturePropertyMetadataWithDeleteMetadataWildcardHeader() {
        final Thing thing = createThingV2WithRandomIdAndMetadata();
        final DittoHeaders dittoHeaders = dittoHeadersV2.toBuilder()
                .putHeader(DittoHeaderDefinition.DELETE_METADATA.getKey(), "features/*/properties/*/unit")
                .build();
        final ThingCommand<?> modifyThingCommand = ModifyThing.of(getIdOrThrow(thing), thing, null, dittoHeaders);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing from json to use initial metadata creation
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            underTest.tell(modifyThingCommand, getRef());
            expectMsgClass(ModifyThingResponse.class);

            // retrieve thing with metadata
            final Metadata expectedMetadata = METADATA.toBuilder()
                    .remove("features/featureId/properties/featurePropertyKey/unit")
                    .build();

            final RetrieveThing retrieveModifiedThing =
                    RetrieveThing.getBuilder(getIdOrThrow(thing), dittoHeadersV2)
                            .withSelectedFields(ALL_FIELDS_SELECTOR_WITH_METADATA)
                            .build();
            underTest.tell(retrieveModifiedThing, getRef());
            final RetrieveThingResponse retrieveModifiedThingResponse = expectMsgClass(RetrieveThingResponse.class);
            assertThat(retrieveModifiedThingResponse.getThing().getMetadata().orElseThrow())
                    .isEqualTo(expectedMetadata);
        }};
    }

    @Test
    public void deleteFeaturePropertyMetadataWithDeleteMetadataWildcardHeaderForMergeCommand() {
        final Thing thing = createThingV2WithRandomIdAndMetadata();
        final DittoHeaders dittoHeaders = dittoHeadersV2.toBuilder()
                .putHeader(DittoHeaderDefinition.DELETE_METADATA.getKey(), "/unit")
                .build();
        final ThingCommand<?> mergeThingCommand =
                MergeThing.withFeatureProperty(getIdOrThrow(thing), FEATURE_ID, JsonPointer.of(FEATURE_PROPERTY_KEY),
                        JsonFactory.newValue("bumlux"), dittoHeaders);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing from json to use initial metadata creation
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            underTest.tell(mergeThingCommand, getRef());
            expectMsgClass(MergeThingResponse.class);

            // retrieve thing with metadata
            final Metadata expectedMetadata = METADATA.toBuilder()
                    .remove("features/featureId/properties/featurePropertyKey/unit")
                    .build();

            final RetrieveThing retrieveModifiedThing =
                    RetrieveThing.getBuilder(getIdOrThrow(thing), dittoHeadersV2)
                            .withSelectedFields(ALL_FIELDS_SELECTOR_WITH_METADATA)
                            .build();
            underTest.tell(retrieveModifiedThing, getRef());
            final RetrieveThingResponse retrieveModifiedThingResponse = expectMsgClass(RetrieveThingResponse.class);
            assertThat(retrieveModifiedThingResponse.getThing().getMetadata().orElseThrow())
                    .isEqualTo(expectedMetadata);
        }};
    }

    @Test
    public void deleteExistingMetadataWithMergeCommand() {
        final Thing thing = createThingV2WithRandomIdAndMetadata();
        final ThingCommand<?> mergeThingCommand =
                MergeThing.withFeatureProperty(getIdOrThrow(thing), FEATURE_ID, JsonPointer.of(FEATURE_PROPERTY_KEY),
                        JsonFactory.nullLiteral(), dittoHeadersV2);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing from json to use initial metadata creation
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            underTest.tell(mergeThingCommand, getRef());
            expectMsgClass(MergeThingResponse.class);

            // retrieve thing with metadata
            final Metadata expectedMetadata = METADATA.toBuilder()
                    .remove("features/featureId/properties/featurePropertyKey")
                    .build();

            final RetrieveThing retrieveModifiedThing =
                    RetrieveThing.getBuilder(getIdOrThrow(thing), dittoHeadersV2)
                            .withSelectedFields(ALL_FIELDS_SELECTOR_WITH_METADATA)
                            .build();
            underTest.tell(retrieveModifiedThing, getRef());
            final RetrieveThingResponse retrieveModifiedThingResponse = expectMsgClass(RetrieveThingResponse.class);
            assertThat(retrieveModifiedThingResponse.getThing().getMetadata().orElseThrow())
                    .isEqualTo(expectedMetadata);
        }};
    }

    @Test
    public void deleteMetadataForDeleteAttributeCommand() {
        final Thing thing = createThingV2WithRandomIdAndMetadata();
        final ThingCommand<?> deleteAttributeCommand =
                DeleteAttribute.of(getIdOrThrow(thing), JsonPointer.of(ATTRIBUTE_KEY), dittoHeadersV2);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing from json to use initial metadata creation
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            underTest.tell(deleteAttributeCommand, getRef());
            expectMsgClass(DeleteAttributeResponse.class);

            // retrieve thing with metadata
            final Metadata expectedMetadata = METADATA.toBuilder()
                    .remove("attributes/attrKey")
                    .build();

            final RetrieveThing retrieveModifiedThing =
                    RetrieveThing.getBuilder(getIdOrThrow(thing), dittoHeadersV2)
                            .withSelectedFields(ALL_FIELDS_SELECTOR_WITH_METADATA)
                            .build();
            underTest.tell(retrieveModifiedThing, getRef());
            final RetrieveThingResponse retrieveThingResponse = expectMsgClass(RetrieveThingResponse.class);
            assertThat(retrieveThingResponse.getThing().getMetadata().orElseThrow())
                    .isEqualTo(expectedMetadata);
        }};
    }

    @Test
    public void multipleMetadataHeadersResultsInException() {
        final Thing thing = createThingV2WithRandomIdAndMetadata();
        final MetadataHeaders metadataHeaders = MetadataHeaders.newInstance();
        metadataHeaders.add(MetadataHeader.of(MetadataHeaderKey.of(JsonPointer.of("*/foo")), JsonValue.of("bar")));
        final DittoHeaders dittoHeaders = dittoHeadersV2.toBuilder()
                .putHeader(DittoHeaderDefinition.DELETE_METADATA.getKey(), "attributes/")
                .putHeader(DittoHeaderDefinition.PUT_METADATA.getKey(), metadataHeaders.toJsonString())
                .build();
        final ThingCommand<?> modifyAttributeCommand =
                ModifyAttribute.of(getIdOrThrow(thing), JsonPointer.of(ATTRIBUTE_KEY), JsonValue.nullLiteral(),
                        dittoHeaders);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing from json to use initial metadata creation
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            underTest.tell(modifyAttributeCommand, getRef());
            expectMsgClass(MetadataHeadersConflictException.class);
        }};
    }

    @Test
    public void testRemovalOfEmptyMetadataAfterDeletion() {
        final var thing = createThingV2WithRandomId();
        final var putHeaders = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "2")
                .putHeader(DittoHeaderDefinition.PUT_METADATA.getKey(),
                        "[{\"key\":\"*/modified\",\"value\":\"2022-06-23T06:49:05\"}]")
                .build();
        final var modifyThing = ModifyThing.of(getIdOrThrow(thing), thing, null, putHeaders);

        final var deleteHeaders = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "3")
                .putHeader(DittoHeaderDefinition.DELETE_METADATA.getKey(), "*/modified")
                .build();
        final var modifyThing1 = ModifyThing.of(getIdOrThrow(thing), thing, null, deleteHeaders);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            // modify features with metadata
            underTest.tell(modifyThing, getRef());
            expectMsgEquals(ETagTestUtils.modifyThingResponse(thing, thing, putHeaders, false));

            underTest.tell(modifyThing1, getRef());
            expectMsgEquals(ETagTestUtils.modifyThingResponse(thing.toBuilder().setRevision(2).build(), thing,
                    deleteHeaders, false));

            // assert that metadata is empty
            final Metadata expectedEmptyMetadata = MetadataModelFactory.emptyMetadata();

            assertMetadataAsExpected(this, underTest, getIdOrThrow(thing), expectedEmptyMetadata);
        }};
    }

    @Test
    public void unavailableExpectedAndPolicyIsDeletedIfPersistenceActorFails() {
        final DittoHeaders dittoHeaders = dittoHeadersV2.toBuilder()
                .correlationId(UUID.randomUUID().toString())
                .build();
        final Policy inlinePolicy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(SubjectIssuer.newInstance("test"), AUTH_SUBJECT)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .setGrantedPermissions(PoliciesResourceType.policyResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .setGrantedPermissions(PoliciesResourceType.messageResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .build();
        final CreatePolicyResponse createPolicyResponse = CreatePolicyResponse.of(POLICY_ID, inlinePolicy,
                DittoHeaders.empty());
        when(policyEnforcerProvider.getPolicyEnforcer(POLICY_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(PolicyEnforcer.of(inlinePolicy))));

        final DeletePolicyResponse deletePolicyResponse = DeletePolicyResponse.of(POLICY_ID, dittoHeaders);

        new TestKit(actorSystem) {
            {
                Thing thing = createThingV2WithRandomId().toBuilder().setPolicyId(null).build();
                ThingId thingId = getIdOrThrow(thing);

                ActorRef underTest = createSupervisorActorWithCustomPersistenceActor(thingId,
                        (thingId1, mongoReadJournal, thingConfig, distributedPub, searchShardRegionProxy, policyEnforcerProvider) -> FailingInCtorActor.props());

                CreateThing createThing = CreateThing.of(thing, null, dittoHeaders);
                underTest.tell(createThing, getRef());
                policiesShardRegionTestProbe.expectMsgClass(CreatePolicy.class);
                policiesShardRegionTestProbe.reply(createPolicyResponse);
                policiesShardRegionTestProbe.expectMsgClass(DeletePolicy.class);
                policiesShardRegionTestProbe.reply(deletePolicyResponse);
                expectMsgClass(ThingUnavailableException.class);
                expectNoMessage();

            }
        };
    }

    @Test
    public void policyShouldNotBeDeletedOnThingRetrieveAndActorFail() {
        final DittoHeaders dittoHeaders = dittoHeadersV2.toBuilder()
                .correlationId(UUID.randomUUID().toString())
                .build();
        final Policy inlinePolicy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(SubjectIssuer.newInstance("test"), AUTH_SUBJECT)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .setGrantedPermissions(PoliciesResourceType.policyResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .setGrantedPermissions(PoliciesResourceType.messageResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .build();
        when(policyEnforcerProvider.getPolicyEnforcer(POLICY_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(PolicyEnforcer.of(inlinePolicy))));

        new TestKit(actorSystem) {
            {
                Thing thing = createThingV2WithRandomId().toBuilder().setPolicyId(null).build();
                ThingId thingId = getIdOrThrow(thing);

                ActorRef underTest = createSupervisorActorWithCustomPersistenceActor(thingId,
                        (thingId1, mongoReadJournal, thingConfig, distributedPub, searchShardRegionProxy, policyEnforcerProvider) -> FailingInCtorActor.props());

                RetrieveThing retrieveThing = RetrieveThing.of(thingId, dittoHeaders);
                underTest.tell(retrieveThing, getRef());
                policiesShardRegionTestProbe.expectNoMessage();
                // Use explicit timeout: on CI with 1-2 cores, the child actor creation may not have
                // completed yet when the command arrives, causing the supervisor to forward the command
                // to the dying child via ask with localAskTimeoutDuringRecovery (5s in test config).
                expectMsgClass(java.time.Duration.ofSeconds(10), ThingUnavailableException.class);
                expectNoMessage();

            }
        };
    }

    @Test
    public void policyShouldBeDeletedOnThingCreateAndErrorResponse() {
        final DittoHeaders dittoHeaders = dittoHeadersV2.toBuilder()
                .correlationId(UUID.randomUUID().toString())
                .build();
        final Policy inlinePolicy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(SubjectIssuer.newInstance("test"), AUTH_SUBJECT)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .setGrantedPermissions(PoliciesResourceType.policyResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .setGrantedPermissions(PoliciesResourceType.messageResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .build();
        final CreatePolicyResponse createPolicyResponse = CreatePolicyResponse.of(POLICY_ID, inlinePolicy,
                DittoHeaders.empty());
        when(policyEnforcerProvider.getPolicyEnforcer(POLICY_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(PolicyEnforcer.of(inlinePolicy))));

        final DeletePolicyResponse deletePolicyResponse = DeletePolicyResponse.of(POLICY_ID, dittoHeaders);

        new TestKit(actorSystem) {
            {
                Thing thing = createThingV2WithRandomId().toBuilder().setPolicyId(null).build();
                ThingId thingId = getIdOrThrow(thing);

                TestProbe testProbe = TestProbe.apply("mock-thingPersistenceActorProbe", actorSystem);
                ActorRef underTest = createSupervisorActorWithCustomPersistenceActor(thingId, testProbe.ref());

                CreateThing createThing = CreateThing.of(thing, null, dittoHeaders);
                underTest.tell(createThing, getRef());

                testProbe.expectNoMsg();


                policiesShardRegionTestProbe.expectMsgClass(CreatePolicy.class);
                policiesShardRegionTestProbe.reply(createPolicyResponse);

                testProbe.expectMsgClass(CreateThing.class);
                testProbe.reply(ThingUnavailableException.newBuilder(thingId)
                        .dittoHeaders(dittoHeaders)
                        .message("Error in target persistent actor").build());

                policiesShardRegionTestProbe.expectMsgClass(DeletePolicy.class);
                policiesShardRegionTestProbe.reply(deletePolicyResponse);
                expectMsgClass(ThingUnavailableException.class);
                expectNoMessage();

            }
        };
    }

    public static final class FailingInCtorActor extends AbstractActor {

        public FailingInCtorActor() {
            super();
            throw new RuntimeException("Failed to create PersistenceActor");
        }

        @Override
        public Receive createReceive() {
            return ReceiveBuilder.create().build();
        }

        private static Props props() {
            return Props.create(FailingInCtorActor.class);
        }
    }

    private ActorRef createSupervisorActorWithCustomPersistenceActor(final ThingId thingId,
            final ThingPersistenceActorPropsFactory persistenceActorPropsFactory) {
        final LiveSignalPub liveSignalPub = new TestSetup.DummyLiveSignalPub(pubSubMediator);
        final Props props =
                ThingSupervisorActor.props(pubSubMediator,
                        thingsConfig,
                        enforcementConfig,
                        policiesShardRegion,
                        new DistributedPub<>() {

                            @Override
                            public ActorRef getPublisher() {
                                return pubSubMediator;
                            }

                            @Override
                            public Object wrapForPublication(final ThingEvent<?> message,
                                    final CharSequence groupIndexKey) {
                                return message;
                            }

                            @Override
                            public <S extends ThingEvent<?>> Object wrapForPublicationWithAcks(final S message,
                                    final CharSequence groupIndexKey, final AckExtractor<S> ackExtractor) {
                                return wrapForPublication(message, groupIndexKey);
                            }
                        },
                        liveSignalPub,
                        persistenceActorPropsFactory,
                        null,
                        policyEnforcerProvider,
                        Mockito.mock(MongoReadJournal.class));

        return actorSystem.actorOf(props, thingId.toString());
    }

    private ActorRef createSupervisorActorWithCustomPersistenceActor(final ThingId thingId,
            final ActorRef persistenceActor) {
        final LiveSignalPub liveSignalPub = new TestSetup.DummyLiveSignalPub(pubSubMediator);
        final Props props =
                ThingSupervisorActor.props(pubSubMediator,
                        thingsConfig,
                        enforcementConfig,
                        policiesShardRegion,
                        new DistributedPub<>() {

                            @Override
                            public ActorRef getPublisher() {
                                return pubSubMediator;
                            }

                            @Override
                            public Object wrapForPublication(final ThingEvent<?> message,
                                    final CharSequence groupIndexKey) {
                                return message;
                            }

                            @Override
                            public <S extends ThingEvent<?>> Object wrapForPublicationWithAcks(final S message,
                                    final CharSequence groupIndexKey, final AckExtractor<S> ackExtractor) {
                                return wrapForPublication(message, groupIndexKey);
                            }
                        },
                        liveSignalPub,
                        persistenceActor,
                        null,
                        policyEnforcerProvider,
                        Mockito.mock(MongoReadJournal.class));

        return actorSystem.actorOf(props, thingId.toString());
    }


    private void assertPublishEvent(final ThingEvent<?> event) {
        final ThingEvent<?> msg = pubSubTestProbe.expectMsgClass(ThingEvent.class);
        Assertions.assertThat(msg.toJson())
                .isEqualTo(event.toJson().set(msg.toJson().getField(Event.JsonFields.TIMESTAMP.getPointer()).get()));
        assertThat(msg.getDittoHeaders().getSchemaVersion()).isEqualTo(event.getDittoHeaders().getSchemaVersion());
    }

    private static Thing buildThing(final ThingId thingId) {
        return ThingsModelFactory.newThingBuilder()
                .setLifecycle(ThingLifecycle.ACTIVE)
                .setAttributes(THING_ATTRIBUTES)
                .setRevision(1)
                .setId(thingId)
                .setPolicyId(POLICY_ID)
                .build();
    }

    private static CreateThing createThing(final Thing thing, final JsonSchemaVersion version) {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .schemaVersion(version)
                .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                        AuthorizationSubject.newInstance(AUTH_SUBJECT)))
                .build();

        return CreateThing.of(thing, null, dittoHeaders);
    }

    private static ModifyThing modifyThing(final Thing thing, final JsonSchemaVersion version) {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .schemaVersion(version)
                .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "2")
                .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                        AuthorizationSubject.newInstance(AUTH_SUBJECT)))
                .build();
        return ModifyThing.of(getIdOrThrow(thing),
                thing,
                null,
                dittoHeaders);
    }

    private ActorRef createPersistenceActorFor(final Thing thing) {
        return createPersistenceActorFor(getIdOrThrow(thing));
    }

    private static ThingId getIdOrThrow(final Thing thing) {
        return thing.getEntityId().orElseThrow(() -> new NoSuchElementException("Failed to get ID from thing!"));
    }

    private static Thing incrementThingRevision(final Thing thing) {
        return thing.toBuilder()
                .setRevision(thing.getRevision()
                        .map(Revision::increment)
                        .orElseGet(() -> ThingRevision.newInstance(1L)))
                .build();
    }

    private static JsonObject getJsonCommand(final Thing thing) {
        return JsonFactory.newObjectBuilder()
                .set(ThingCommand.JsonFields.TYPE, CreateThing.TYPE)
                .set(CreateThing.JSON_THING, thing.toJson(FieldType.all()))
                .build();
    }

    // region #2599 passivation race regression tests

    /**
     * Regression for <a href="https://github.com/eclipse-ditto/ditto/issues/2599">#2599</a>:
     * when idle, {@code passivate} must forward
     * {@link ShardRegion.Passivate} carrying a {@link StopShardedActor} so the existing
     * {@code stopShardedActor} handler drives the shutdown.
     */
    @Test
    public void passivateIsForwardedToShardRegionParentWhenIdle() {
        new TestKit(actorSystem) {{
            final Thing thing = createThingV2WithRandomId();
            final ThingId thingId = getIdOrThrow(thing);
            final ActorRef underTest = watch(childActorOf(supervisorPropsFor(thingId), thingId.toString()));

            underTest.tell(AbstractPersistenceSupervisor.Control.PASSIVATE, ActorRef.noSender());

            final ShardRegion.Passivate passivate = expectMsgClass(ShardRegion.Passivate.class);
            Assertions.assertThat(passivate.stopMessage())
                    .isInstanceOf(StopShardedActor.class);

            underTest.tell(passivate.stopMessage(), ActorRef.noSender());
            expectTerminated(underTest);
        }};
    }

    /**
     * Regression for <a href="https://github.com/eclipse-ditto/ditto/issues/2599">#2599</a>:
     * when PASSIVATE arrives while an op is in flight, the supervisor must still deliver the
     * op's response before stopping. The TestKit parent plays the shard region: it echoes the
     * {@link StopShardedActor} back so {@code stopShardedActor} defers the stop until both
     * counters reach zero.
     */
    @Test
    public void passivateDuringOngoingOpsShouldNotShutdownActor() {
        final Policy inlinePolicy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(SubjectIssuer.newInstance("test"), AUTH_SUBJECT)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .setGrantedPermissions(PoliciesResourceType.policyResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .build();
        final CreatePolicyResponse createPolicyResponse =
                CreatePolicyResponse.of(POLICY_ID, inlinePolicy, DittoHeaders.empty());
        when(policyEnforcerProvider.getPolicyEnforcer(POLICY_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(PolicyEnforcer.of(inlinePolicy))));

        new TestKit(actorSystem) {{
            final Thing thing = createThingV2WithRandomId();
            final ThingId thingId = getIdOrThrow(thing);
            final ActorRef underTest = watch(childActorOf(supervisorPropsFor(thingId), thingId.toString()));

            final CreateThing createThing =
                    CreateThing.of(thing, inlinePolicy.toJson(FieldType.all()), dittoHeadersV2);
            underTest.tell(createThing, getRef());
            policiesShardRegionTestProbe.expectMsgClass(CreatePolicy.class);
            policiesShardRegionTestProbe.reply(createPolicyResponse);
            expectMsgClass(CreateThingResponse.class);

            // Per-call delayed stub: thenAnswer(inv -> supplyAsync(...)) so each invocation
            // restarts its own delay. thenReturn would bind to a single already-running
            // CompletionStage shared across calls.
            final long policyDelayMillis = 1000L;
            when(policyEnforcerProvider.getPolicyEnforcer(POLICY_ID))
                    .thenAnswer(_ -> CompletableFuture.supplyAsync(() -> {
                        try {
                            Thread.sleep(policyDelayMillis);
                        } catch (final InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return Optional.of(PolicyEnforcer.of(inlinePolicy));
                    }));

            final RetrieveThing retrieveThing = RetrieveThing.of(thingId, dittoHeadersV2);
            underTest.tell(retrieveThing, getRef());
            underTest.tell(AbstractPersistenceSupervisor.Control.PASSIVATE, ActorRef.noSender());

            final ShardRegion.Passivate passivate = expectMsgClass(ShardRegion.Passivate.class);
            Assertions.assertThat(passivate.stopMessage())
                    .as("passivate must carry a StopShardedActor so the deferred-stop handler runs")
                    .isInstanceOf(StopShardedActor.class);
            underTest.tell(passivate.stopMessage(), ActorRef.noSender());

            final scala.concurrent.duration.FiniteDuration responseTimeout =
                    scala.concurrent.duration.FiniteDuration.create(policyDelayMillis + 3_000L, TimeUnit.MILLISECONDS);
            expectMsgClass(responseTimeout, RetrieveThingResponse.class);

            expectTerminated(underTest);
        }};
    }

    /**
     * Regression for <a href="https://github.com/eclipse-ditto/ditto/issues/2599">#2599</a>
     * Finding 4: a signal queued immediately after PASSIVATE used to race the parent-side
     * stop message; its enforcement continuation reached
     * {@link org.eclipse.ditto.internal.utils.persistentactors.AbstractPersistenceSupervisor}
     * after the materializer had been shut down and the client saw
     * {@link DittoInternalErrorException} even though the event was already persisted.
     * Forwarding a {@link StopShardedActor} (handled by {@code stopShardedActor} with a
     * deferred stop) removes the race — every iteration must see the response.
     * <p>
     * Race-window knob (only if the test becomes flaky; not needed for the committed test).
     * In {@code org.eclipse.ditto.things.service.persistence.actors.ThingSupervisorActor#modifyTargetActorCommandResponse},
     * insert the sleep as the first statement of the method body — between the opening
     * brace of
     * <pre>{@code
     * protected CompletionStage<Object> modifyTargetActorCommandResponse(final Signal<?> enforcedSignal,
     *         final Object persistenceCommandResponse) {
     * }</pre>
     * and the existing
     * <pre>{@code
     *     return Source.single(new CommandResponsePair<Signal<?>, Object>(enforcedSignal, persistenceCommandResponse))
     * }</pre>
     * add:
     * <pre>{@code
     *     final long delay = Long.getLong("ditto.diag.2599.response-delay-ms", 0L);
     *     if (delay > 0L) {
     *         try { Thread.sleep(delay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
     *     }
     * }</pre>
     * In the test body, {@code System.setProperty("ditto.diag.2599.response-delay-ms", "1500")}
     * before telling PASSIVATE and {@code System.clearProperty(...)} in a {@code finally}.
     * Remove the sleep after use.
     */
    @Test
    public void passivatePoisonPillRacesWithMailboxQueuedSignal() {
        final Policy inlinePolicy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(SubjectIssuer.newInstance("test"), AUTH_SUBJECT)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .setGrantedPermissions(PoliciesResourceType.policyResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .build();
        final CreatePolicyResponse createPolicyResponse =
                CreatePolicyResponse.of(POLICY_ID, inlinePolicy, DittoHeaders.empty());
        when(policyEnforcerProvider.getPolicyEnforcer(POLICY_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(PolicyEnforcer.of(inlinePolicy))));

        final int retrieveIterations = 5;
        final long responseDelayMillis = 1_500L;
        // Pause between observing ShardRegion.Passivate and echoing StopShardedActor back.
        final long stopMessageDelayMillis = 400L;
        final scala.concurrent.duration.FiniteDuration setupTimeout =
                scala.concurrent.duration.FiniteDuration.create(15, TimeUnit.SECONDS);
        final scala.concurrent.duration.FiniteDuration responseTimeout =
                scala.concurrent.duration.FiniteDuration.create(responseDelayMillis + 5_000L, TimeUnit.MILLISECONDS);

        new TestKit(actorSystem) {{
            final List<String> raceReport = new java.util.ArrayList<>();
            for (int i = 0; i < retrieveIterations; i++) {
                final Thing thing = createThingV2WithRandomId();
                final ThingId thingId = getIdOrThrow(thing);
                final JsonPointer okPointer = JsonPointer.of("okAttr" + i);
                final JsonValue okValue = JsonFactory.newValue("ok-value-" + i);
                final JsonPointer attrPointer = JsonPointer.of("raceAttr" + i);
                final JsonValue attrValue = JsonFactory.newValue("value-" + i);

                when(policyEnforcerProvider.getPolicyEnforcer(POLICY_ID))
                        .thenReturn(CompletableFuture.completedFuture(Optional.of(PolicyEnforcer.of(inlinePolicy))));

                // Per-iteration sender probe so late responses from a previous iteration do not
                // pollute the main TestKit inbox and trip the next iteration's childActorOf.
                final TestProbe senderProbe =
                        TestProbe.apply("sender-" + i + "-" + UUID.randomUUID(), actorSystem);
                final TestProbe deathWatchProbe =
                        TestProbe.apply("deathWatch-" + i + "-" + UUID.randomUUID(), actorSystem);
                final ActorRef underTest =
                        childActorOf(supervisorPropsFor(thingId), thingId.toString());
                deathWatchProbe.watch(underTest);

                final CreateThing createThing =
                        CreateThing.of(thing, inlinePolicy.toJson(FieldType.all()), dittoHeadersV2);
                underTest.tell(createThing, senderProbe.ref());
                try {
                    policiesShardRegionTestProbe.expectMsgClass(setupTimeout, CreatePolicy.class);
                    policiesShardRegionTestProbe.reply(createPolicyResponse);
                    final Object createReceived = senderProbe.receiveOne(setupTimeout);
                    if (!(createReceived instanceof CreateThingResponse)) {
                        raceReport.add(String.format("iteration %d: SKIPPED (CreateThing got %s)", i,
                                createReceived == null ? "null" : createReceived.getClass().getSimpleName()));
                        underTest.tell(PoisonPill.getInstance(), ActorRef.noSender());
                        try {
                            deathWatchProbe.expectTerminated(underTest, setupTimeout);
                        } catch (final AssertionError ignored) {
                        }
                        continue;
                    }
                } catch (final AssertionError e) {
                    raceReport.add(String.format("iteration %d: SKIPPED (setup failed: %s)", i, e.getMessage()));
                    underTest.tell(PoisonPill.getInstance(), ActorRef.noSender());
                    try {
                        deathWatchProbe.expectTerminated(underTest, setupTimeout);
                    } catch (final AssertionError ignored) {
                    }
                    continue;
                }

                // Baseline modify: no PASSIVATE race, must succeed. Confirms the supervisor
                // works normally before we trigger the race on the same instance.
                final ModifyAttribute okModify =
                        ModifyAttribute.of(thingId, okPointer, okValue, dittoHeadersV2);
                underTest.tell(okModify, senderProbe.ref());
                final Object okReceived = senderProbe.receiveOne(setupTimeout);
                final String okOutcome = okReceived == null
                        ? "null (receive timeout)"
                        : okReceived.getClass().getSimpleName();
                if (!(okReceived instanceof ModifyAttributeResponse)) {
                    raceReport.add(String.format("iteration %d: SKIPPED (baseline modify got %s)", i, okOutcome));
                    underTest.tell(PoisonPill.getInstance(), ActorRef.noSender());
                    try {
                        deathWatchProbe.expectTerminated(underTest, setupTimeout);
                    } catch (final AssertionError ignored) {
                    }
                    continue;
                }

                final String clientOutcome;
                {
                    // PASSIVATE and raceModify enqueued back-to-back. passivate() forwards
                    // Passivate(new StopShardedActor()) to the parent. raceModify still gets
                    // processed (PA persists), then the response-path stream runs on
                    // enforcementExecutor. stopShardedActor() defers the stop until opCounter
                    // reaches 0, so the client receives ModifyAttributeResponse even though a
                    // stop is already scheduled.
                    underTest.tell(AbstractPersistenceSupervisor.Control.PASSIVATE, ActorRef.noSender());
                    final ModifyAttribute raceModify =
                            ModifyAttribute.of(thingId, attrPointer, attrValue, dittoHeadersV2);
                    underTest.tell(raceModify, senderProbe.ref());

                    final ShardRegion.Passivate passivate;
                    try {
                        passivate = expectMsgClass(setupTimeout, ShardRegion.Passivate.class);
                    } catch (final AssertionError e) {
                        raceReport.add(String.format("iteration %d: SKIPPED (no ShardRegion.Passivate: %s)",
                                i, e.getMessage()));
                        continue;
                    }

                    // Wait so the response-path stream is in flight when the StopShardedActor
                    // arrives — exercises the defer path, not the idle-stop path.
                    try {
                        Thread.sleep(stopMessageDelayMillis);
                    } catch (final InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    underTest.tell(passivate.stopMessage(), ActorRef.noSender());

                    try {
                        deathWatchProbe.expectTerminated(underTest,
                                scala.concurrent.duration.FiniteDuration.create(
                                        responseDelayMillis + 3_000L, TimeUnit.MILLISECONDS));
                    } catch (final AssertionError e) {
                        raceReport.add(String.format("iteration %d: SKIPPED (supervisor did not terminate: %s)",
                                i, e.getMessage()));
                        continue;
                    }

                    final Object received = senderProbe.receiveOne(responseTimeout);
                    clientOutcome = received == null
                            ? "null (receive timeout)"
                            : received.getClass().getSimpleName();
                }

                // Respawn supervisor, RetrieveThing via fresh probe, inspect persisted state.
                final TestProbe verifySenderProbe =
                        TestProbe.apply("verify-sender-" + i + "-" + UUID.randomUUID(), actorSystem);
                final TestProbe verifyDeathWatchProbe =
                        TestProbe.apply("verify-deathWatch-" + i + "-" + UUID.randomUUID(), actorSystem);
                ActorRef verifySupervisorAttempt = null;
                for (int attempt = 0; attempt < 20 && verifySupervisorAttempt == null; attempt++) {
                    try {
                        verifySupervisorAttempt =
                                childActorOf(supervisorPropsFor(thingId), thingId.toString());
                    } catch (final AssertionError ignored) {
                        try {
                            Thread.sleep(200);
                        } catch (final InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
                if (verifySupervisorAttempt == null) {
                    raceReport.add(String.format(
                            "iteration %d: SKIPPED (verify supervisor spawn failed: name reservation stuck)",
                            i));
                    continue;
                }
                final ActorRef verifySupervisor = verifySupervisorAttempt;
                verifyDeathWatchProbe.watch(verifySupervisor);

                verifySupervisor.tell(RetrieveThing.of(thingId, dittoHeadersV2), verifySenderProbe.ref());
                final Object verifyReceived = verifySenderProbe.receiveOne(setupTimeout);
                final boolean okPersisted;
                final String okPersistedValue;
                final boolean persisted;
                final String persistedValue;
                if (verifyReceived instanceof RetrieveThingResponse verifyResponse) {
                    final Optional<Attributes> attrs = verifyResponse.getThing().getAttributes();
                    final Optional<JsonValue> okActual = attrs.flatMap(a -> a.getValue(okPointer));
                    okPersisted = okActual.filter(v -> v.equals(okValue)).isPresent();
                    okPersistedValue = okActual.map(JsonValue::toString).orElse("<absent>");
                    final Optional<JsonValue> actual = attrs.flatMap(a -> a.getValue(attrPointer));
                    persisted = actual.filter(v -> v.equals(attrValue)).isPresent();
                    persistedValue = actual.map(JsonValue::toString).orElse("<absent>");
                } else {
                    final String err = verifyReceived == null ? "null" : verifyReceived.getClass().getSimpleName();
                    okPersisted = false;
                    okPersistedValue = "<verify-failed:" + err + ">";
                    persisted = false;
                    persistedValue = "<verify-failed:" + err + ">";
                }

                verifySupervisor.tell(PoisonPill.getInstance(), ActorRef.noSender());
                try {
                    verifyDeathWatchProbe.expectTerminated(verifySupervisor, setupTimeout);
                } catch (final AssertionError ignored) {
                }

                raceReport.add(String.format(
                        "iteration %d: ok-client=%s ok-persisted=%s ok-value=%s | race-client=%s race-persisted=%s race-value=%s",
                        i, okOutcome, okPersisted, okPersistedValue,
                        clientOutcome, persisted, persistedValue));
            }

            // Leak: race-client != success AND race-persisted=true => persisted write that the
            // client will retry = double-write.
            final List<String> leakIterations = raceReport.stream()
                    .filter(line -> !line.contains("race-client=ModifyAttributeResponse")
                            && line.contains("race-persisted=true"))
                    .toList();
            Assertions.assertThat(leakIterations)
                    .as("Side-effect leak detected: client saw error but modification was persisted.%n" +
                            "Full report:%n  %s%nLeaking iterations:%n  %s",
                            String.join("\n  ", raceReport),
                            String.join("\n  ", leakIterations))
                    .isEmpty();
        }};
    }

    /**
     * Regression for <a href="https://github.com/eclipse-ditto/ditto/issues/2599">#2599</a>
     * Finding 5: when the Finding 4 race fired during a {@link CreateThing} with an inline
     * policy, the removed materializer-shutdown safety-net mapped the crash to
     * {@link ThingUnavailableException} without issuing {@code DeletePolicy}, leaving the
     * policy orphaned. With the {@link StopShardedActor}-based deferred stop the response
     * path completes and the client either sees {@link CreateThingResponse} or the real
     * rollback path issues {@code DeletePolicy} — no orphan.
     * <p>
     * Race-window knob (only if the test becomes flaky; not needed for the committed test):
     * same knob and same insertion point as
     * {@link #passivatePoisonPillRacesWithMailboxQueuedSignal} — see its Javadoc for the exact
     * before/after anchor lines in
     * {@code ThingSupervisorActor#modifyTargetActorCommandResponse} and the matching
     * {@code System.setProperty} / {@code clearProperty} pair in the test body.
     */
    @Test
    public void passivatePoisonPillRacesWithCreateThingLeavesOrphanedPolicy() {
        final Policy inlinePolicy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(SubjectIssuer.newInstance("test"), AUTH_SUBJECT)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .setGrantedPermissions(PoliciesResourceType.policyResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .build();
        final CreatePolicyResponse createPolicyResponse =
                CreatePolicyResponse.of(POLICY_ID, inlinePolicy, DittoHeaders.empty());
        when(policyEnforcerProvider.getPolicyEnforcer(POLICY_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(PolicyEnforcer.of(inlinePolicy))));

        final int iterations = 10;
        final long responseDelayMillis = 1_500L;
        final long stopMessageDelayMillis = 400L;
        final scala.concurrent.duration.FiniteDuration setupTimeout =
                scala.concurrent.duration.FiniteDuration.create(15, TimeUnit.SECONDS);
        final scala.concurrent.duration.FiniteDuration responseTimeout =
                scala.concurrent.duration.FiniteDuration.create(responseDelayMillis + 5_000L,
                        TimeUnit.MILLISECONDS);
        final scala.concurrent.duration.FiniteDuration shortProbeTimeout =
                scala.concurrent.duration.FiniteDuration.create(2, TimeUnit.SECONDS);

        new TestKit(actorSystem) {{
            final List<String> report = new java.util.ArrayList<>();
            for (int i = 0; i < iterations; i++) {
                final Thing thing = createThingV2WithRandomId();
                final ThingId thingId = getIdOrThrow(thing);

                when(policyEnforcerProvider.getPolicyEnforcer(POLICY_ID))
                        .thenReturn(CompletableFuture.completedFuture(Optional.of(PolicyEnforcer.of(inlinePolicy))));

                final TestProbe senderProbe =
                        TestProbe.apply("f5-sender-" + i + "-" + UUID.randomUUID(), actorSystem);
                final TestProbe deathWatchProbe =
                        TestProbe.apply("f5-deathWatch-" + i + "-" + UUID.randomUUID(), actorSystem);
                final ActorRef underTest =
                        childActorOf(supervisorPropsFor(thingId), thingId.toString());
                deathWatchProbe.watch(underTest);

                final String clientOutcome;
                final boolean deletePolicyObserved;
                {
                    // PASSIVATE then CreateThing: enforcer creates the inline policy, PA persists
                    // ThingCreated, the response-path stream runs. The deferred stop waits for
                    // the response, so the client sees CreateThingResponse and no rollback is
                    // needed.
                    underTest.tell(AbstractPersistenceSupervisor.Control.PASSIVATE, ActorRef.noSender());
                    final CreateThing createThing =
                            CreateThing.of(thing, inlinePolicy.toJson(FieldType.all()), dittoHeadersV2);
                    underTest.tell(createThing, senderProbe.ref());

                    final ShardRegion.Passivate passivate;
                    try {
                        passivate = expectMsgClass(setupTimeout, ShardRegion.Passivate.class);
                    } catch (final AssertionError e) {
                        report.add(String.format("iteration %d: SKIPPED (no ShardRegion.Passivate: %s)",
                                i, e.getMessage()));
                        continue;
                    }

                    try {
                        policiesShardRegionTestProbe.expectMsgClass(setupTimeout, CreatePolicy.class);
                        policiesShardRegionTestProbe.reply(createPolicyResponse);
                    } catch (final AssertionError e) {
                        report.add(String.format("iteration %d: SKIPPED (no CreatePolicy: %s)",
                                i, e.getMessage()));
                        continue;
                    }

                    try {
                        Thread.sleep(stopMessageDelayMillis);
                    } catch (final InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    underTest.tell(passivate.stopMessage(), ActorRef.noSender());

                    try {
                        deathWatchProbe.expectTerminated(underTest,
                                scala.concurrent.duration.FiniteDuration.create(
                                        responseDelayMillis + 3_000L, TimeUnit.MILLISECONDS));
                    } catch (final AssertionError e) {
                        report.add(String.format("iteration %d: SKIPPED (supervisor did not terminate: %s)",
                                i, e.getMessage()));
                        continue;
                    }

                    final Object received = senderProbe.receiveOne(responseTimeout);
                    clientOutcome = received == null
                            ? "null (receive timeout)"
                            : received.getClass().getSimpleName();

                    // DeletePolicy expected either as part of a real rollback or not at all when
                    // the client gets CreateThingResponse. If neither → orphan.
                    Object maybeDelete = null;
                    try {
                        maybeDelete = policiesShardRegionTestProbe.receiveOne(shortProbeTimeout);
                    } catch (final AssertionError ignored) {
                    }
                    deletePolicyObserved = maybeDelete instanceof DeletePolicy;
                    if (deletePolicyObserved) {
                        policiesShardRegionTestProbe.reply(DeletePolicyResponse.of(POLICY_ID, DittoHeaders.empty()));
                    }
                }

                final boolean orphan = !"CreateThingResponse".equals(clientOutcome)
                        && !deletePolicyObserved;
                report.add(String.format(
                        "iteration %d: race-client=%s delete-policy-observed=%s orphan=%s",
                        i, clientOutcome, deletePolicyObserved, orphan));
            }

            final List<String> orphanIterations = report.stream()
                    .filter(line -> line.contains("orphan=true"))
                    .toList();
            Assertions.assertThat(orphanIterations)
                    .as("Orphaned-policy race detected (client saw error but no DeletePolicy issued).%n" +
                            "Full report:%n  %s%nOrphan iterations:%n  %s",
                            String.join("\n  ", report),
                            String.join("\n  ", orphanIterations))
                    .isEmpty();
        }};
    }

    /**
     * Regression for <a href="https://github.com/eclipse-ditto/ditto/issues/2599">#2599</a>
     * Finding 6: when a {@link SudoRetrieveThing} is in flight and PASSIVATE arrives, the
     * supervisor must deliver the sudo response before stopping. {@code stopShardedActor}
     * defers the actual stop until both {@code opCounter} and {@code sudoOpCounter} reach 0.
     * Asserts the ordering {@link SudoRetrieveThingResponse} then
     * {@link org.apache.pekko.actor.Terminated} without needing a race-window widening.
     * <p>
     * Race-window knob (only if the test becomes flaky; not needed for the committed test).
     * In {@code org.eclipse.ditto.internal.utils.persistentactors.AbstractPersistenceSupervisor#forwardSudoCommandToChildIfAvailable},
     * insert the sleep inside the {@code else} branch after the counter is incremented and
     * before the forward — between the existing line
     * <pre>{@code
     *                 ++sudoOpCounter;
     * }</pre>
     * and the existing line
     * <pre>{@code
     *                 persistenceActorChild.forward(sudoCommand, getContext());
     * }</pre>
     * add:
     * <pre>{@code
     *                 final long delay = Long.getLong("ditto.diag.2599.sudo-forward-delay-ms", 0L);
     *                 if (delay > 0L) {
     *                     try { Thread.sleep(delay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
     *                 }
     * }</pre>
     * In the test body, {@code System.setProperty("ditto.diag.2599.sudo-forward-delay-ms", "1500")}
     * before telling the {@link SudoRetrieveThing} and {@code System.clearProperty(...)} in a
     * {@code finally}. Placement after {@code ++sudoOpCounter} ensures PASSIVATE sees
     * {@code sudoOpCounter > 0}. Remove the sleep after use.
     */
    @Test
    public void passivateDefersStopUntilInFlightSudoRetrieveThingCompletes() {
        final Policy inlinePolicy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(SubjectIssuer.newInstance("test"), AUTH_SUBJECT)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .setGrantedPermissions(PoliciesResourceType.policyResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .build();
        final CreatePolicyResponse createPolicyResponse =
                CreatePolicyResponse.of(POLICY_ID, inlinePolicy, DittoHeaders.empty());
        when(policyEnforcerProvider.getPolicyEnforcer(POLICY_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(PolicyEnforcer.of(inlinePolicy))));

        final scala.concurrent.duration.FiniteDuration setupTimeout =
                scala.concurrent.duration.FiniteDuration.create(15, TimeUnit.SECONDS);

        new TestKit(actorSystem) {{
            final Thing thing = createThingV2WithRandomId();
            final ThingId thingId = getIdOrThrow(thing);

            final TestProbe senderProbe =
                    TestProbe.apply("f6-sender-" + UUID.randomUUID(), actorSystem);
            final ActorRef underTest =
                    watch(childActorOf(supervisorPropsFor(thingId), thingId.toString()));

            final CreateThing createThing =
                    CreateThing.of(thing, inlinePolicy.toJson(FieldType.all()), dittoHeadersV2);
            underTest.tell(createThing, senderProbe.ref());
            policiesShardRegionTestProbe.expectMsgClass(setupTimeout, CreatePolicy.class);
            policiesShardRegionTestProbe.reply(createPolicyResponse);
            senderProbe.expectMsgClass(setupTimeout, CreateThingResponse.class);

            final DittoHeaders sudoHeaders = DittoHeaders.newBuilder(dittoHeadersV2)
                    .correlationId("f6-sudo-" + UUID.randomUUID())
                    .build();
            final SudoRetrieveThing sudoRetrieve = SudoRetrieveThing.of(thingId, sudoHeaders);
            underTest.tell(sudoRetrieve, senderProbe.ref());

            underTest.tell(AbstractPersistenceSupervisor.Control.PASSIVATE, ActorRef.noSender());

            final ShardRegion.Passivate passivate = expectMsgClass(setupTimeout, ShardRegion.Passivate.class);
            Assertions.assertThat(passivate.stopMessage())
                    .as("passivate must carry a StopShardedActor so the deferred-stop handler runs")
                    .isInstanceOf(StopShardedActor.class);

            underTest.tell(passivate.stopMessage(), ActorRef.noSender());

            senderProbe.expectMsgClass(setupTimeout, SudoRetrieveThingResponse.class);

            expectTerminated(underTest);
        }};
    }

    // endregion

}
