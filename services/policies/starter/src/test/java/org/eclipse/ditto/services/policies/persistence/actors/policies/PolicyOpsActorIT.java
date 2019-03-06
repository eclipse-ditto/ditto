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
package org.eclipse.ditto.services.policies.persistence.actors.policies;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.EffectedPermissions;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.Resource;
import org.eclipse.ditto.model.policies.SubjectType;
import org.eclipse.ditto.services.policies.persistence.actors.policy.PolicyOpsActor;
import org.eclipse.ditto.services.policies.persistence.actors.policy.PolicySupervisorActor;
import org.eclipse.ditto.services.policies.persistence.serializer.PolicyMongoSnapshotAdapter;
import org.eclipse.ditto.services.utils.persistence.mongo.ops.eventsource.NamespaceOpsActorTestCases;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicy;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicyResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicy;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyResponse;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

/**
 * Tests {@link PolicyOpsActor}.
 */
@AllValuesAreNonnullByDefault
@RunWith(Parameterized.class)
public final class PolicyOpsActorIT extends NamespaceOpsActorTestCases {

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<TestSetting> data() {
        return Arrays.asList(TestSetting.NAMESPACES_WITHOUT_SUFFIX, TestSetting.NAMESPACES_WITH_SUFFIX);
    }

    public PolicyOpsActorIT(
            final NamespaceOpsActorTestCases.TestSetting testSetting) {
        super(testSetting);
    }

    @Override
    protected String getServiceName() {
        return "policies";
    }

    @Override
    protected String getResourceType() {
        return PolicyCommand.RESOURCE_TYPE;
    }

    @Override
    protected List<String> getSupportedPrefixes() {
        return Collections.singletonList(PolicyCommand.RESOURCE_TYPE);
    }

    @Override
    protected Object getCreateEntityCommand(final String id) {
        final Policy policy = Policy.newBuilder(id)
                .forLabel("DUMMY")
                .setSubject("ditto:random-subject", SubjectType.GENERATED)
                .setResource(Resource.newInstance(PolicyCommand.RESOURCE_TYPE, "/",
                        EffectedPermissions.newInstance(Arrays.asList("READ", "WRITE"), Collections.emptyList())))
                .build();
        return CreatePolicy.of(policy, DittoHeaders.empty());
    }

    @Override
    protected Class<?> getCreateEntityResponseClass() {
        return CreatePolicyResponse.class;
    }

    @Override
    protected Object getRetrieveEntityCommand(final String id) {
        return RetrievePolicy.of(id, DittoHeaders.empty());
    }

    @Override
    protected Class<?> getRetrieveEntityResponseClass() {
        return RetrievePolicyResponse.class;
    }

    @Override
    protected Class<?> getEntityNotAccessibleClass() {
        return PolicyNotAccessibleException.class;
    }

    @Override
    protected ActorRef startActorUnderTest(final ActorSystem actorSystem, final ActorRef pubSubMediator,
            final Config config) {

        final Props opsActorProps = PolicyOpsActor.props(pubSubMediator, config);
        return actorSystem.actorOf(opsActorProps, PolicyOpsActor.ACTOR_NAME);
    }

    @Override
    protected ActorRef startEntityActor(final ActorSystem system, final ActorRef pubSubMediator, final String id) {
        // essentially never restart
        final Duration minBackOff = Duration.ofSeconds(36000);
        final Duration maxBackOff = Duration.ofSeconds(36000);
        final double randomFactor = 0.2;

        final Props props = PolicySupervisorActor.props(pubSubMediator, minBackOff, maxBackOff, randomFactor,
                new PolicyMongoSnapshotAdapter());

        return system.actorOf(props, id);
    }

}
