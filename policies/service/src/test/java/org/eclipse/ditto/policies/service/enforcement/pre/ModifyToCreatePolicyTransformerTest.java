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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicy;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicy;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class ModifyToCreatePolicyTransformerTest {

    @Mock
    public PolicyExistenceChecker existenceChecker;
    private ModifyToCreatePolicyTransformer underTest;

    @Before
    public void setup() {
        underTest = new ModifyToCreatePolicyTransformer(existenceChecker);
    }

    @Test
    public void modifyPolicyStaysModifyPolicyWhenAlreadyExisting() {
        final var policyId = PolicyId.generateRandom();
        final var modifyPolicy =
                ModifyPolicy.of(policyId, Policy.newBuilder(policyId).build(), DittoHeaders.of(Map.of("foo", "bar")));
        when(existenceChecker.checkExistence(modifyPolicy)).thenReturn(CompletableFuture.completedStage(true));

        final Signal<?> result = underTest.apply(modifyPolicy).toCompletableFuture().join();

        assertThat(result).isSameAs(modifyPolicy);
        verify(existenceChecker).checkExistence(modifyPolicy);
    }

    @Test
    public void modifyPolicyBecomesCreatePolicyWhenNotYetExisting() {
        final var policyId = PolicyId.generateRandom();
        final var modifyPolicy =
                ModifyPolicy.of(policyId, Policy.newBuilder(policyId).build(), DittoHeaders.of(Map.of("foo", "bar")));
        when(existenceChecker.checkExistence(modifyPolicy)).thenReturn(CompletableFuture.completedStage(false));

        final Signal<?> result = underTest.apply(modifyPolicy).toCompletableFuture().join();

        assertThat(result).isInstanceOf(CreatePolicy.class);
        final CreatePolicy createPolicy = (CreatePolicy) result;
        assertThat(createPolicy.getEntityId().toString()).isEqualTo(policyId.toString());
        assertThat(createPolicy.getPolicy()).isSameAs(modifyPolicy.getPolicy());
        assertThat(createPolicy.getDittoHeaders()).isSameAs(modifyPolicy.getDittoHeaders());
        verify(existenceChecker).checkExistence(modifyPolicy);
    }

    @Test
    public void otherCommandsThanModifyPolicyAreJustPassedThrough() {
        final var policyId = PolicyId.generateRandom();
        final var retrievePolicy = RetrievePolicy.of(policyId, DittoHeaders.of(Map.of("foo", "bar")));

        final Signal<?> result = underTest.apply(retrievePolicy).toCompletableFuture().join();

        assertThat(result).isSameAs(retrievePolicy);
        verifyNoInteractions(existenceChecker);
    }

}
