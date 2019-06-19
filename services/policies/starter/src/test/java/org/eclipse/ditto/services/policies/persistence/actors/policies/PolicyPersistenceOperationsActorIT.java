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
package org.eclipse.ditto.services.policies.persistence.actors.policies;

import java.util.Arrays;
import java.util.Collections;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.EffectedPermissions;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.Resource;
import org.eclipse.ditto.model.policies.SubjectType;
import org.eclipse.ditto.services.policies.persistence.actors.policy.PolicyPersistenceOperationsActor;
import org.eclipse.ditto.services.policies.persistence.actors.policy.PolicySupervisorActor;
import org.eclipse.ditto.services.policies.persistence.serializer.PolicyMongoSnapshotAdapter;
import org.eclipse.ditto.services.utils.persistence.mongo.ops.eventsource.MongoEventSourceITAssertions;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicy;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicyResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicy;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyResponse;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;
import org.junit.Test;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

/**
 * Tests {@link PolicyPersistenceOperationsActor}.
 */
@AllValuesAreNonnullByDefault
public final class PolicyPersistenceOperationsActorIT extends MongoEventSourceITAssertions {

    @Test
    public void purgeNamespaceWithoutSuffix() {
        assertPurgeNamespaceWithoutSuffix();
    }

    @Test
    public void purgeNamespaceWithSuffix() {
        assertPurgeNamespaceWithSuffix();
    }

    @Test
    public void purgeEntitiesWithNamespaceWithoutSuffix() {
        assertPurgeEntitiesWithNamespaceWithoutSuffix();
    }

    @Test
    public void purgeEntitiesWithNamespaceWithSuffix() {
        assertPurgeEntitiesWithNamespaceWithSuffix();
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

        final Props opsActorProps = PolicyPersistenceOperationsActor.props(pubSubMediator, mongoDbConfig, config,
                persistenceOperationsConfig);
        return actorSystem.actorOf(opsActorProps, PolicyPersistenceOperationsActor.ACTOR_NAME);
    }

    @Override
    protected ActorRef startEntityActor(final ActorSystem system, final ActorRef pubSubMediator, final String id) {
        final Props props = PolicySupervisorActor.props(pubSubMediator, new PolicyMongoSnapshotAdapter());

        return system.actorOf(props, id);
    }

}
