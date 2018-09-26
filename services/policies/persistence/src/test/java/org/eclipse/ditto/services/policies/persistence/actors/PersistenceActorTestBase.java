/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.policies.persistence.actors;

import static java.util.Objects.requireNonNull;
import static org.eclipse.ditto.services.policies.persistence.TestConstants.Policy.SUBJECT_TYPE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.eclipse.ditto.json.JsonPointer;
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
import org.eclipse.ditto.model.policies.Subjects;
import org.eclipse.ditto.services.models.policies.Permission;
import org.eclipse.ditto.services.policies.persistence.TestConstants;
import org.junit.After;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.JavaTestKit;
import akka.testkit.TestProbe;

/**
 * Base test class for testing persistence actors of the policies persistence.
 */
public abstract class PersistenceActorTestBase {

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
    private static final SubjectIssuer ISSUER_GOOGLE = SubjectIssuer.GOOGLE;
    protected static final SubjectId POLICY_SUBJECT_ID =
            SubjectId.newInstance(ISSUER_GOOGLE, "allowedId");
    protected static final Subject POLICY_SUBJECT =
            Subject.newInstance(POLICY_SUBJECT_ID, SUBJECT_TYPE);
    protected static final Resources POLICY_RESOURCES_ALL = Resources.newInstance(POLICY_RESOURCE_ALL);
    protected static final Resources POLICY_RESOURCES_READ = Resources.newInstance(POLICY_RESOURCE_READ);
    protected static final Subjects POLICY_SUBJECTS = Subjects.newInstance(POLICY_SUBJECT);
    protected static final Label POLICY_LABEL = Label.of("all");
    protected static final Label ANOTHER_POLICY_LABEL = Label.of("another");

    protected static final String AUTH_SUBJECT = ISSUER_GOOGLE + ":allowedId";
    protected static final String UNAUTH_SUBJECT = ISSUER_GOOGLE + ":denied";

    private static final PolicyEntry POLICY_ENTRY =
            PoliciesModelFactory.newPolicyEntry(POLICY_LABEL, POLICY_SUBJECTS, POLICY_RESOURCES_ALL);
    private static final PolicyEntry ANOTHER_POLICY_ENTRY =
            PoliciesModelFactory.newPolicyEntry(ANOTHER_POLICY_LABEL, POLICY_SUBJECTS, POLICY_RESOURCES_READ);
    private static final long POLICY_REVISION = 0;
    protected ActorSystem actorSystem = null;
    protected ActorRef pubSubMediator = null;
    protected DittoHeaders dittoHeadersV2;

    protected static DittoHeaders createDittoHeaders(final JsonSchemaVersion schemaVersion,
            final String... authSubjects) {
        final List<String> authSubjectsStr = Arrays.asList(authSubjects);
        final List<AuthorizationSubject> authSubjectsList = new ArrayList<>();
        authSubjectsStr.stream().map(AuthorizationModelFactory::newAuthSubject).forEach(authSubjectsList::add);

        return DittoHeaders.newBuilder()
                .correlationId(null)
                .source(null)
                .responseRequired(false)
                .schemaVersion(schemaVersion)
                .authorizationSubjects(authSubjectsStr)
                .authorizationContext(AuthorizationModelFactory.newAuthContext(authSubjectsList)).build();
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

    protected void setup(final Config customConfig) {
        requireNonNull(customConfig, "Consider to use ConfigFactory.empty()");
        final Config config = customConfig.withFallback(ConfigFactory.load("test"));

        init(config);
    }

    public void setUpBase() {
        final Config config = ConfigFactory.load("test");

        init(config);
    }

    private void init(final Config config) {
        actorSystem = ActorSystem.create("AkkaTestSystem", config);
        pubSubMediator = new TestProbe(actorSystem, "mock-pubSub-mediator").ref();
        dittoHeadersV2 = createDittoHeaders(JsonSchemaVersion.V_2, AUTH_SUBJECT);
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
