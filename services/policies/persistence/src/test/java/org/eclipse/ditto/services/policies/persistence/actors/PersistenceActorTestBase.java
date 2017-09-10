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
package org.eclipse.ditto.services.policies.persistence.actors;

import static java.util.Objects.requireNonNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.EffectedPermissions;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.Permissions;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyLifecycle;
import org.eclipse.ditto.model.policies.Resource;
import org.eclipse.ditto.model.policies.Resources;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.policies.SubjectType;
import org.eclipse.ditto.model.policies.Subjects;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AccessControlListModelFactory;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.models.policies.Permission;
import org.eclipse.ditto.services.policies.persistence.TestConstants;
import org.eclipse.ditto.services.policies.persistence.actors.policy.PolicySupervisorActor;
import org.eclipse.ditto.services.utils.distributedcache.actors.CacheFacadeActor;
import org.eclipse.ditto.services.utils.distributedcache.actors.CacheRole;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicy;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyResponse;
import org.junit.After;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSub;
import akka.testkit.JavaTestKit;

/**
 * Base test class for testing persistence actors of the policies persistence.
 */
public abstract class PersistenceActorTestBase {

    private static final SubjectIssuer ISSUER_GOOGLE = SubjectIssuer.GOOGLE_URL;

    protected static final String THING_ID = "thingId";
    protected static final String AUTH_SUBJECT = ISSUER_GOOGLE + ":allowedId";
    protected static final AuthorizationSubject AUTHORIZED_SUBJECT =
            AuthorizationModelFactory.newAuthSubject(AUTH_SUBJECT);
    protected static final String UNAUTH_SUBJECT = ISSUER_GOOGLE + ":denied";
    protected static final AuthorizationSubject UNAUTHORIZED_SUBJECT =
            AuthorizationModelFactory.newAuthSubject(UNAUTH_SUBJECT);
    protected static final String READ_SUBJECT = ISSUER_GOOGLE + ":readonly";
    protected static final AuthorizationSubject READONLY_SUBJECT =
            AuthorizationModelFactory.newAuthSubject(READ_SUBJECT);
    protected static final AccessControlList THING_ACL = ThingsModelFactory
            .newAcl(ThingsModelFactory.newAclEntry(AUTHORIZED_SUBJECT, Thing.MIN_REQUIRED_PERMISSIONS),
                    ThingsModelFactory.newAclEntry(READONLY_SUBJECT, org.eclipse.ditto.model.things.Permission.READ));
    protected static final Attributes THING_ATTRIBUTES = ThingsModelFactory.emptyAttributes();
    protected static final String POLICY_ID = "org.eclipse.ditto:myPolicy";
    protected static final PolicyLifecycle POLICY_LIFECYCLE = PolicyLifecycle.ACTIVE;
    protected static final JsonPointer POLICY_RESOURCE_PATH = JsonPointer.empty();
    protected static final Resource POLICY_RESOURCE_ALL =
            Resource.newInstance(PoliciesResourceType.policyResource(POLICY_RESOURCE_PATH),
                    EffectedPermissions.newInstance(TestConstants.Policy.PERMISSIONS_ALL,
                            PoliciesModelFactory.noPermissions()));
    protected static final Resource POLICY_RESOURCE_READ =
            Resource.newInstance(PoliciesResourceType.policyResource(POLICY_RESOURCE_PATH),
                    EffectedPermissions.newInstance(Permissions.newInstance(Permission.READ),
                            PoliciesModelFactory.noPermissions()));
    protected static final SubjectId POLICY_SUBJECT_ID =
            SubjectId.newInstance(ISSUER_GOOGLE, "allowedId");
    protected static final Subject POLICY_SUBJECT =
            Subject.newInstance(POLICY_SUBJECT_ID, SubjectType.JWT);
    protected static final Resources POLICY_RESOURCES_ALL = Resources.newInstance(POLICY_RESOURCE_ALL);
    protected static final Resources POLICY_RESOURCES_READ = Resources.newInstance(POLICY_RESOURCE_READ);
    protected static final Subjects POLICY_SUBJECTS = Subjects.newInstance(POLICY_SUBJECT);
    protected static final Label POLICY_LABEL = Label.of("all");
    protected static final Label ANOTHER_POLICY_LABEL = Label.of("another");
    protected static final PolicyEntry POLICY_ENTRY =
            PoliciesModelFactory.newPolicyEntry(POLICY_LABEL, POLICY_SUBJECTS, POLICY_RESOURCES_ALL);
    protected static final PolicyEntry ANOTHER_POLICY_ENTRY =
            PoliciesModelFactory.newPolicyEntry(ANOTHER_POLICY_LABEL, POLICY_SUBJECTS, POLICY_RESOURCES_READ);
    protected static final Policy POLICY = Policy.newBuilder(POLICY_ID)
            .set(POLICY_ENTRY)
            .set(ANOTHER_POLICY_ENTRY)
            .build();
    protected static final long POLICY_REVISION = 0;
    static final Features THING_FEATURES = ThingsModelFactory.emptyFeatures();
    private static final ThingLifecycle THING_LIFECYCLE = ThingLifecycle.ACTIVE;
    private static final long THING_REVISION = 1;
    protected ActorSystem actorSystem = null;
    protected ActorRef pubSubMediator = null;
    protected ActorRef policyCacheFacade;
    protected DittoHeaders dittoHeadersMockV1;
    protected DittoHeaders dittoHeadersMockV2;

    protected void setup(final Config customConfig) {
        requireNonNull(customConfig, "Consider to use ConfigFactory.empty()");
        final Config config = customConfig.withFallback(ConfigFactory.load("test"));

        actorSystem = ActorSystem.create("AkkaTestSystem", config);
        pubSubMediator = DistributedPubSub.get(actorSystem).mediator();
        policyCacheFacade = actorSystem.actorOf(CacheFacadeActor.props(CacheRole.POLICY,
                actorSystem.settings().config()), CacheFacadeActor.actorNameFor(CacheRole.POLICY));
        dittoHeadersMockV1 = createDittoHeadersMock(JsonSchemaVersion.V_1, AUTH_SUBJECT);
        dittoHeadersMockV2 = createDittoHeadersMock(JsonSchemaVersion.V_2, AUTH_SUBJECT);
    }

    protected static AuthorizationContext createAuthContextMock(final AuthorizationSubject firstAuthSubject,
            final AuthorizationSubject... furtherAuthSubjects) {
        final List<AuthorizationSubject> allAuthSubjects = new ArrayList<>();
        allAuthSubjects.add(firstAuthSubject);
        Collections.addAll(allAuthSubjects, furtherAuthSubjects);

        final AuthorizationContext result = mock(AuthorizationContext.class);
        when(result.getAuthorizationSubjects()).thenReturn(allAuthSubjects);
        when(result.getFirstAuthorizationSubject()).thenReturn(Optional.of(firstAuthSubject));
        return result;
    }

    protected ActorRef createSupervisorActorFor(final String policyId) {
        final java.time.Duration minBackoff = java.time.Duration.ofSeconds(3);
        final java.time.Duration maxBackoff = java.time.Duration.ofSeconds(60);
        final double randomFactor = 0.2;
        final Props props =
                PolicySupervisorActor.props(pubSubMediator, minBackoff, maxBackoff, randomFactor, policyCacheFacade);

        return actorSystem.actorOf(props, policyId);
    }

    protected static DittoHeaders createDittoHeadersMock(final JsonSchemaVersion schemaVersion,
            final String... authSubjects) {
        final DittoHeaders result = mock(DittoHeaders.class);
        when(result.getCorrelationId()).thenReturn(Optional.empty());
        when(result.getSource()).thenReturn(Optional.empty());
        when(result.isResponseRequired()).thenReturn(false);
        when(result.getSchemaVersion()).thenReturn(Optional.ofNullable(schemaVersion));
        final List<String> authSubjectsStr = Arrays.asList(authSubjects);
        when(result.getAuthorizationSubjects()).thenReturn(authSubjectsStr);
        final List<AuthorizationSubject> authSubjectsList = new ArrayList<>();
        authSubjectsStr.stream().map(AuthorizationModelFactory::newAuthSubject).forEach(authSubjectsList::add);
        when(result.getAuthorizationContext()).thenReturn(AuthorizationModelFactory.newAuthContext(authSubjectsList));
        return result;
    }

    protected static Thing createThingV2WithRandomId() {
        final Random rnd = new Random();
        final String thingId = THING_ID + rnd.nextInt();
        return createThingV2WithId(thingId);
    }

    protected static Thing createThingV2WithId(final String thingId) {
        return ThingsModelFactory.newThingBuilder()
                .setLifecycle(THING_LIFECYCLE)
                .setAttributes(THING_ATTRIBUTES)
                .setFeatures(THING_FEATURES)
                .setRevision(THING_REVISION)
                .setId("test.ns:" + thingId)
                .setPolicyId("test.ns:" + thingId)
                .build();
    }

    protected static Thing createThingV1WithRandomId() {
        final Random rnd = new Random();
        final String thingId = THING_ID + rnd.nextInt();
        return createThingV1WithId(thingId);
    }

    protected static Thing createThingV1WithId(final String thingId) {
        return ThingsModelFactory.newThingBuilder()
                .setLifecycle(THING_LIFECYCLE)
                .setAttributes(THING_ATTRIBUTES)
                .setFeatures(THING_FEATURES)
                .setRevision(THING_REVISION)
                .setId("test.ns:" + thingId)
                .setPermissions(AUTHORIZED_SUBJECT, AccessControlListModelFactory.allPermissions()).build();
    }

    protected static Policy createPolicyWithRandomId() {
        final Random rnd = new Random();
        final String policyId = POLICY_ID + rnd.nextInt();
        return createPolicyWithId(policyId);
    }

    protected static Policy createPolicyWithId(final String policyId) {
        return PoliciesModelFactory.newPolicyBuilder("test.ns:" + policyId)
                .setLifecycle(POLICY_LIFECYCLE)
                .set(POLICY_ENTRY)
                .set(ANOTHER_POLICY_ENTRY)
                .setRevision(POLICY_REVISION)
                .build();
    }

    protected static ModifyPolicyResponse createPolicyResponse(final ModifyPolicy createPolicy) {
        return ModifyPolicyResponse
                .created(POLICY_ID,
                        PoliciesModelFactory.newPolicyBuilder(createPolicy.getPolicy()).setRevision(1).build(),
                        createPolicy.getDittoHeaders());
    }

    protected static PolicyEntry createPolicyEntry(final AuthorizationSubject authorizationSubject,
            final String... permissions) {
        final SubjectId subjectId =
                PoliciesModelFactory.newSubjectId(ISSUER_GOOGLE, authorizationSubject.getId());
        final Subject subject = PoliciesModelFactory.newSubject(subjectId, SubjectType.JWT);
        final Iterable<String> grantedPermissions =
                PoliciesModelFactory.newPermissions(Arrays.asList(permissions));
        final Resource resource = PoliciesModelFactory.newResource(PoliciesResourceType.policyResource("/"),
                PoliciesModelFactory.newEffectedPermissions(grantedPermissions, new ArrayList<>()));
        return PoliciesModelFactory
                .newPolicyEntry(PoliciesModelFactory.newLabel("DEFAULT:" + authorizationSubject.getId()),
                        PoliciesModelFactory.newSubjects(subject),
                        PoliciesModelFactory.newResources(resource));
    }

    public void setUpBase() {
        final Config config = ConfigFactory.load("test");
        actorSystem = ActorSystem.create("AkkaTestSystem", config);
        pubSubMediator = DistributedPubSub.get(actorSystem).mediator();
        policyCacheFacade = actorSystem.actorOf(CacheFacadeActor.props(CacheRole.POLICY,
                actorSystem.settings().config()), CacheFacadeActor.actorNameFor(CacheRole.POLICY));
        dittoHeadersMockV1 = createDittoHeadersMock(JsonSchemaVersion.V_1, AUTH_SUBJECT);
        dittoHeadersMockV2 = createDittoHeadersMock(JsonSchemaVersion.V_2, AUTH_SUBJECT);
    }

    /** */
    @After
    public void tearDownBase() {
        if (actorSystem != null) {
            JavaTestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
        }
    }
}
