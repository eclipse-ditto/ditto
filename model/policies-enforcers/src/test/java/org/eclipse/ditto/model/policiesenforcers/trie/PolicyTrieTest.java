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
package org.eclipse.ditto.model.policiesenforcers.trie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.policies.EffectedPermissions;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.Resource;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policies.Resources;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.policiesenforcers.TestConstants;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test for {@link PolicyTrie}.
 */
public final class PolicyTrieTest {

    private static String subjectId;
    private static String anotherSubjectId;

    private static ResourceKey fooResourceKey;
    private static ResourceKey barResourceKey;
    private static Policy policy;

    private PolicyTrie underTest = null;

    /** */
    @BeforeClass
    public static void initTestConstants() {
        subjectId = TestConstants.Policy.SUBJECT_ID.toString();

        final SubjectId johnTitor = PoliciesModelFactory.newSubjectId(SubjectIssuer.GOOGLE, "JohnTitor");
        anotherSubjectId = johnTitor.toString();

        final JsonPointer fooResourcePath = TestConstants.Policy.RESOURCE_PATH;
        final JsonPointer barResourcePath = fooResourcePath.addLeaf(JsonFactory.newKey("Bar"));
        fooResourceKey = ResourceKey.newInstance(TestConstants.Policy.RESOURCE_TYPE, fooResourcePath);
        barResourceKey = ResourceKey.newInstance(TestConstants.Policy.RESOURCE_TYPE, barResourcePath);

        policy = Policy.newBuilder(TestConstants.Policy.POLICY_ID)
                .set(PoliciesModelFactory.newPolicyEntry(PoliciesModelFactory.newLabel(fooResourcePath.toString()),
                        PoliciesModelFactory.newSubjects(
                                PoliciesModelFactory.newSubject(TestConstants.Policy.SUBJECT_ID)),
                        Resources.newInstance(
                                Resource.newInstance(fooResourceKey,
                                        EffectedPermissions.newInstance(Collections.singleton("WRITE"),
                                                Collections.emptySet())),
                                Resource.newInstance(barResourceKey,
                                        EffectedPermissions.newInstance(Collections.singleton("READ"),
                                                Collections.emptySet())))))
                .set(PoliciesModelFactory.newPolicyEntry(PoliciesModelFactory.newLabel(barResourcePath.toString()),
                        PoliciesModelFactory.newSubjects(
                                PoliciesModelFactory.newSubject(johnTitor)),
                        Resources.newInstance(
                                Resource.newInstance(ResourceKey.newInstance(TestConstants.Policy.RESOURCE_TYPE, fooResourcePath),
                                        EffectedPermissions.newInstance(Collections.singleton("READ"),
                                                Collections.singleton("WRITE"))),
                                Resource.newInstance(barResourceKey,
                                        EffectedPermissions.newInstance(Collections.emptySet(),
                                                Collections.singleton("READ"))))))
                .build();
    }

    /** */
    @Before
    public void initTestVariables() {
        underTest = PolicyTrie.fromPolicy(policy);
    }

    /** */
    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToCreateInstanceFromNullPolicy() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> PolicyTrie.fromPolicy(null))
                .withMessage("The %s must not be null!", "policy to interpret")
                .withNoCause();
    }

    /** */
    @Test
    public void getJsonKeyIteratorReturnsExpected() {
        final JsonPointer barResourcePath = barResourceKey.getResourcePath();
        final Collection<JsonKey> expectedJsonKeys = new ArrayList<>(1 + barResourcePath.getLevelCount());
        expectedJsonKeys.add(JsonFactory.newKey(barResourceKey.getResourceType()));
        barResourcePath.forEach(expectedJsonKeys::add);

        final Iterator<JsonKey> jsonKeyIterator = PolicyTrie.getJsonKeyIterator(barResourceKey);

        assertThat(jsonKeyIterator).containsExactlyElementsOf(expectedJsonKeys);
    }

    /** */
    @Test
    public void getGrandRevokeIndexOnRootReturnsEmptyIndex() {
        final GrantRevokeIndex expectedGrantRevokeIndex = new GrantRevokeIndex();

        final GrantRevokeIndex grantRevokeIndex = underTest.getGrantRevokeIndex();

        assertThat(grantRevokeIndex).isEqualTo(expectedGrantRevokeIndex);
    }

    /** */
    @Test
    public void policyTrieHasChildThing() {
        final JsonKey child = JsonFactory.newKey("thing");
        final boolean hasChild = underTest.hasChild(child);

        assertThat(hasChild).as("Has child '%s'", child).isTrue();
    }

    /** */
    @Test
    public void seekToLeastAncestorOfBarResourcePath() {
        final JsonPointer barResourcePath = barResourceKey.getResourcePath();
        final JsonKey expectedChild = barResourcePath.getLeaf().orElse(null);
        final PermissionSubjectsMap grantMap = new PermissionSubjectsMap();
        grantMap.addTotalRelationOfWeightZero(Collections.singleton("READ"), Collections.singleton(anotherSubjectId));
        grantMap.addTotalRelationOfWeightZero(Collections.singleton("WRITE"), Collections.singleton(subjectId));
        final PermissionSubjectsMap revokeMap = new PermissionSubjectsMap();
        revokeMap.addTotalRelationOfWeightZero(Collections.singleton("WRITE"), Collections.singleton(anotherSubjectId));
        final GrantRevokeIndex expectedGrantRevokeIndex = new GrantRevokeIndex(grantMap, revokeMap);

        final PolicyTrie leastAncestor = underTest.seekToLeastAncestor(PolicyTrie.getJsonKeyIterator(fooResourceKey));
        final boolean hasChild = leastAncestor.hasChild(expectedChild);
        final GrantRevokeIndex grantRevokeIndex = leastAncestor.getGrantRevokeIndex();

        assertThat(hasChild).as("Has child '%s'", expectedChild).isTrue();
        assertThat(grantRevokeIndex).isEqualTo(expectedGrantRevokeIndex);
    }

    /** */
    @Test
    public void seekToExactNodeReturnsExpected() {
        final PermissionSubjectsMap grantMap = new PermissionSubjectsMap();
        grantMap.addTotalRelationOfWeightZero(Collections.singleton("READ"), Collections.singleton(subjectId));
        final PermissionSubjectsMap revokeMap = new PermissionSubjectsMap();
        revokeMap.addTotalRelationOfWeightZero(Collections.singleton("READ"), Collections.singleton(anotherSubjectId));
        final GrantRevokeIndex expectedGrantRevokeIndex = new GrantRevokeIndex(grantMap, revokeMap);

        final Optional<PolicyTrie> exactNodeOptional =
                underTest.seekToExactNode(PolicyTrie.getJsonKeyIterator(barResourceKey));

        Assertions.assertThat(exactNodeOptional).isPresent();
        assertThat(getGrantRevokeIndex(exactNodeOptional)).isEqualTo(expectedGrantRevokeIndex);
    }

    /** */
    @Test
    public void getTransitiveClosureReturnsExpected() {

        // ARRANGE
        final PermissionSubjectsMap fooNodeGrantMap = new PermissionSubjectsMap();
        fooNodeGrantMap.put("READ", Collections.singletonMap(anotherSubjectId, 0));
        fooNodeGrantMap.put("WRITE", Collections.singletonMap(subjectId, 0));
        final PermissionSubjectsMap fooNodeRevokeMap = new PermissionSubjectsMap();
        fooNodeRevokeMap.put("WRITE", Collections.singletonMap(anotherSubjectId, 0));
        final GrantRevokeIndex expectedFooNodeGrantRevokeIndex = new GrantRevokeIndex(fooNodeGrantMap, fooNodeRevokeMap);

        final PermissionSubjectsMap barNodeGrantMap = new PermissionSubjectsMap();
        barNodeGrantMap.put("READ", Collections.singletonMap(subjectId, 0));
        barNodeGrantMap.put("WRITE", Collections.singletonMap(subjectId, -1));
        final PermissionSubjectsMap barNodeRevokeMap = new PermissionSubjectsMap();
        barNodeRevokeMap.put("READ", Collections.singletonMap(anotherSubjectId, 0));
        barNodeRevokeMap.put("WRITE", Collections.singletonMap(anotherSubjectId, -1));
        final GrantRevokeIndex expectedBarNodeGrantRevokeIndex = new GrantRevokeIndex(barNodeGrantMap, barNodeRevokeMap);

        // ACT
        final PolicyTrie transitiveClosure = underTest.getTransitiveClosure();
        final Optional<PolicyTrie> fooNodeOptional =
                transitiveClosure.seekToExactNode(PolicyTrie.getJsonKeyIterator(fooResourceKey));
        final Optional<PolicyTrie> barNodeOptional =
                transitiveClosure.seekToExactNode(PolicyTrie.getJsonKeyIterator(barResourceKey));

        // ASSERT
        Assertions.assertThat(fooNodeOptional).as("Has exact Node for '%s'", fooResourceKey).isPresent();
        Assertions.assertThat(barNodeOptional).as("Has exact Node for '%s'", barResourceKey).isPresent();
        assertThat(getGrantRevokeIndex(fooNodeOptional))
                .as("GrantRevokeIndex of foo node is expected")
                .isEqualTo(expectedFooNodeGrantRevokeIndex);
        assertThat(getGrantRevokeIndex(barNodeOptional))
                .as("GrantRevokeIndex of bar node is expected")
                .isEqualTo(expectedBarNodeGrantRevokeIndex);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static GrantRevokeIndex getGrantRevokeIndex(final Optional<PolicyTrie> policyTrieOptional) {
        return policyTrieOptional.map(PolicyTrie::getGrantRevokeIndex).orElse(null);
    }

}
