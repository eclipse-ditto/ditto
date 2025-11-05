/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.service.enforcement.pre;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicy;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicy;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import com.typesafe.config.ConfigFactory;

@RunWith(MockitoJUnitRunner.class)
public final class ModifyToCreatePolicyTransformerTest {

    private ActorSystem system;
    private ModifyToCreatePolicyTransformer underTest;

    @Before
    public void setup() {
        system = ActorSystem.create("test", ConfigFactory.parseMap(Map.of("pekko.actor.provider",
                "org.apache.pekko.cluster.ClusterActorRefProvider")).withFallback(ConfigFactory.load(
                "test")));
        underTest = new ModifyToCreatePolicyTransformer(system, system.settings().config());
    }

    @Test
    public void modifyPolicyStaysModifyPolicyWhenAlreadyExisting() {
        new TestKit(system) {{
            final var policyId = PolicyId.generateRandom();
            final var modifyPolicy =
                    ModifyPolicy.of(policyId, Policy.newBuilder(policyId).build(), DittoHeaders.of(Map.of("foo", "bar")));

            final CompletableFuture<Signal<?>> resultFut = underTest.apply(modifyPolicy, getRef()).toCompletableFuture();
            expectMsgClass(SudoRetrievePolicy.class);
            reply(SudoRetrievePolicyResponse.of(policyId, JsonObject.empty(), DittoHeaders.empty()));
            final Signal<?> result = resultFut.join();

            assertThat(result).isSameAs(modifyPolicy);
        }};
    }

    @Test
    public void modifyPolicyBecomesCreatePolicyWhenNotYetExisting() {
        new TestKit(system) {{
            final var policyId = PolicyId.generateRandom();
            final var modifyPolicy =
                    ModifyPolicy.of(policyId, Policy.newBuilder(policyId).build(), DittoHeaders.of(Map.of("foo", "bar")));

            final CompletableFuture<Signal<?>> resultFut = underTest.apply(modifyPolicy, getRef()).toCompletableFuture();
            expectMsgClass(SudoRetrievePolicy.class);
            reply(PolicyNotAccessibleException.newBuilder(policyId).build());
            final Signal<?> result = resultFut.join();

            assertThat(result).isInstanceOf(CreatePolicy.class);
            final CreatePolicy createPolicy = (CreatePolicy) result;
            assertThat(createPolicy.getEntityId().toString()).isEqualTo(policyId.toString());
            assertThat(createPolicy.getPolicy()).isSameAs(modifyPolicy.getPolicy());
            assertThat(createPolicy.getDittoHeaders()).isSameAs(modifyPolicy.getDittoHeaders());
        }};
    }

    @Test
    public void otherCommandsThanModifyPolicyAreJustPassedThrough() {
        new TestKit(system) {{
            final var policyId = PolicyId.generateRandom();
            final var retrievePolicy = RetrievePolicy.of(policyId, DittoHeaders.of(Map.of("foo", "bar")));

            final CompletableFuture<Signal<?>> resultFut = underTest.apply(retrievePolicy, getRef()).toCompletableFuture();
            expectNoMessage();
            final Signal<?> result = resultFut.join();

            assertThat(result).isSameAs(retrievePolicy);
        }};
    }

}
