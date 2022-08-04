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
package org.eclipse.ditto.policies.service.persistence.actors;

import static java.util.Objects.requireNonNull;
import static org.eclipse.ditto.policies.service.persistence.TestConstants.Policy.SUBJECT_TYPE;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.auth.AuthorizationModelFactory;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyLifecycle;
import org.eclipse.ditto.policies.model.Resource;
import org.eclipse.ditto.policies.model.Resources;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.policies.model.Subjects;
import org.eclipse.ditto.policies.service.common.config.DefaultPolicyConfig;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;
import org.eclipse.ditto.policies.service.persistence.TestConstants;
import org.junit.After;
import org.junit.BeforeClass;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.stream.Attributes;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Base test class for testing persistence actors of the policies persistence.
 */
public abstract class PersistenceActorTestBase {

    protected static final PolicyId POLICY_ID = PolicyId.of("org.eclipse.ditto", "myPolicy");
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
    private static final SubjectIssuer ISSUER_GOOGLE = SubjectIssuer.GOOGLE;
    protected static final SubjectId POLICY_SUBJECT_ID = SubjectId.newInstance(ISSUER_GOOGLE, "allowedId");
    protected static final Subject POLICY_SUBJECT = Subject.newInstance(POLICY_SUBJECT_ID, SUBJECT_TYPE);
    protected static final Resources POLICY_RESOURCES_ALL = Resources.newInstance(POLICY_RESOURCE_ALL);
    protected static final Resources POLICY_RESOURCES_READ = Resources.newInstance(POLICY_RESOURCE_READ);
    protected static final Subjects POLICY_SUBJECTS = Subjects.newInstance(POLICY_SUBJECT);
    protected static final Label POLICY_LABEL = Label.of("all");
    protected static final Label ANOTHER_POLICY_LABEL = Label.of("another");

    protected static final String AUTH_SUBJECT = ISSUER_GOOGLE + ":allowedId";
    protected static final String UNAUTH_SUBJECT = ISSUER_GOOGLE + ":denied";

    private static final PolicyEntry POLICY_ENTRY =
            PoliciesModelFactory.newPolicyEntry(POLICY_LABEL, POLICY_SUBJECTS, POLICY_RESOURCES_ALL);
    protected static final PolicyEntry ANOTHER_POLICY_ENTRY =
            PoliciesModelFactory.newPolicyEntry(ANOTHER_POLICY_LABEL, POLICY_SUBJECTS, POLICY_RESOURCES_READ);
    private static final long POLICY_REVISION = 0;
    static final Instant TIMESTAMP = Instant.EPOCH;

    protected static Config testConfig;
    protected static PolicyConfig policyConfig;

    protected ActorSystem actorSystem = null;
    protected ActorRef pubSubMediator = null;
    protected TestProbe pubSubMediatorTestProbe = null;
    protected DittoHeaders dittoHeadersV2;

    @BeforeClass
    public static void initTestFixture() {
        testConfig = ConfigFactory.load("test");
        policyConfig = DefaultPolicyConfig.of(testConfig.getConfig("ditto.policies"));
    }

    protected static DittoHeaders createDittoHeaders(final JsonSchemaVersion schemaVersion,
            final String... authSubjectIds) {

        final List<AuthorizationSubject> authSubjects = Arrays.stream(authSubjectIds)
                .map(AuthorizationModelFactory::newAuthSubject)
                .collect(Collectors.toList());

        return DittoHeaders.newBuilder()
                .correlationId(null)
                .schemaVersion(schemaVersion)
                .authorizationContext(
                        AuthorizationModelFactory.newAuthContext(DittoAuthorizationContextType.UNSPECIFIED,
                                authSubjects))
                .build();
    }

    protected static Policy createPolicyWithRandomId() {
        final Random rnd = new Random();
        final String policyName = POLICY_ID.getName() + rnd.nextInt();
        return createPolicyWithId(policyName);
    }

    protected static Policy createPolicyWithId(final String policyName) {
        return PoliciesModelFactory.newPolicyBuilder(PolicyId.of("test.ns", policyName))
                .setLifecycle(POLICY_LIFECYCLE)
                .set(POLICY_ENTRY)
                .set(ANOTHER_POLICY_ENTRY)
                .setRevision(POLICY_REVISION)
                .build();
    }

    protected void setup(final Config customConfig) {
        requireNonNull(customConfig, "Consider to use ConfigFactory.empty()");
        final Config config = customConfig.withFallback(ConfigFactory.load("test"));

        init(config);
    }

    protected void setUpBase() {
        final Config config = ConfigFactory.load("test");

        init(config);
    }

    private void init(final Config config) {
        actorSystem = ActorSystem.create("AkkaTestSystem", config);
        pubSubMediatorTestProbe = new TestProbe(actorSystem, "mock-pubSub-mediator");
        pubSubMediator = pubSubMediatorTestProbe.ref();
        dittoHeadersV2 = createDittoHeaders(JsonSchemaVersion.V_2, AUTH_SUBJECT);
    }

    @After
    public void tearDownBase() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
        }
    }

    /**
     * Disable logging for 1 test to hide stacktrace or other logs on level ERROR. Comment out to debug the test.
     */
    protected void disableLogging() {
        actorSystem.eventStream().setLogLevel(Attributes.logLevelOff());
    }

}
