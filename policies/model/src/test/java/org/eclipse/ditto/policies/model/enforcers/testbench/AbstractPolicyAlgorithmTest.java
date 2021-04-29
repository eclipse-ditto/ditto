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
package org.eclipse.ditto.policies.model.enforcers.testbench;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import java.util.Set;
import java.util.UUID;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.policies.model.enforcers.EffectedSubjects;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.policies.model.enforcers.testbench.algorithms.PolicyAlgorithm;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.ScenarioSetup;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.jsonview.JsonViewScenario1;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.jsonview.JsonViewScenario10;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.jsonview.JsonViewScenario11;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.jsonview.JsonViewScenario12;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.jsonview.JsonViewScenario13;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.jsonview.JsonViewScenario2;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.jsonview.JsonViewScenario3;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.jsonview.JsonViewScenario4;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.jsonview.JsonViewScenario5;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.jsonview.JsonViewScenario6;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.jsonview.JsonViewScenario7;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.jsonview.JsonViewScenario8;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.jsonview.JsonViewScenario9;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario1.Scenario1Simple1;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario1.Scenario1Simple2;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario1.Scenario1Simple3;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario1.Scenario1Simple4;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario2.Scenario2Nested1;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario2.Scenario2Nested10;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario2.Scenario2Nested11;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario2.Scenario2Nested2;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario2.Scenario2Nested3;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario2.Scenario2Nested4;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario2.Scenario2Nested5;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario2.Scenario2Nested6;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario2.Scenario2Nested7;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario2.Scenario2Nested8;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario2.Scenario2Nested9;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke1;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke10;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke11;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke12;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke13;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke14;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke15;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke16;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke17;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke18;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke2;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke3;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke4;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke5;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke6;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke7;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke8;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3.Scenario3Revoke9;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects1;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects10;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects11;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects12;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects13;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects14;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects15;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects16;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects17;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects18;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects19;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects2;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects20;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects21;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects22;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects23;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects24;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects25;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects26;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects27;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects3;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects4;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects5;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects6;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects7;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects8;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4.Scenario4MultipleSubjects9;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario5.Scenario5Simple1;
import org.eclipse.ditto.policies.model.enforcers.tree.TreeBasedPolicyEnforcer;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.junit.Assert;
import org.junit.Test;


public abstract class AbstractPolicyAlgorithmTest {

    /**
     * Returns the PolicyAlgorithm to use for the tests defined in this abstract class.
     *
     * @return the PolicyAlgorithm to use.
     */
    protected abstract PolicyAlgorithm getPolicyAlgorithm(final Policy policy);

    @Test
    public void test_Scenario1Simple1() {
        testScenarioWithAlgorithm(new Scenario1Simple1());
    }

    @Test
    public void test_Scenario1Simple2() {
        testScenarioWithAlgorithm(new Scenario1Simple2());
    }

    @Test
    public void test_Scenario1Simple3() {
        testScenarioWithAlgorithm(new Scenario1Simple3());
    }

    @Test
    public void test_Scenario1Simple4() {
        testScenarioWithAlgorithm(new Scenario1Simple4());
    }

    @Test
    public void test_Scenario2Nested1() {
        testScenarioWithAlgorithm(new Scenario2Nested1());
    }

    @Test
    public void test_Scenario2Nested2() {
        testScenarioWithAlgorithm(new Scenario2Nested2());
    }

    @Test
    public void test_Scenario2Nested3() {
        testScenarioWithAlgorithm(new Scenario2Nested3());
    }

    @Test
    public void test_Scenario2Nested4() {
        testScenarioWithAlgorithm(new Scenario2Nested4());
    }

    @Test
    public void test_Scenario2Nested5() {
        testScenarioWithAlgorithm(new Scenario2Nested5());
    }

    @Test
    public void test_Scenario2Nested6() {
        testScenarioWithAlgorithm(new Scenario2Nested6());
    }

    @Test
    public void test_Scenario2Nested7() {
        testScenarioWithAlgorithm(new Scenario2Nested7());
    }

    @Test
    public void test_Scenario2Nested8() {
        testScenarioWithAlgorithm(new Scenario2Nested8());
    }

    @Test
    public void test_Scenario2Nested9() {
        testScenarioWithAlgorithm(new Scenario2Nested9());
    }

    @Test
    public void test_Scenario2Nested10() {
        testScenarioWithAlgorithm(new Scenario2Nested10());
    }

    @Test
    public void test_Scenario2Nested11() {
        testScenarioWithAlgorithm(new Scenario2Nested11());
    }

    @Test
    public void test_Scenario3Revoke1() {
        testScenarioWithAlgorithm(new Scenario3Revoke1());
    }

    @Test
    public void test_Scenario3Revoke2() {
        testScenarioWithAlgorithm(new Scenario3Revoke2());
    }

    @Test
    public void test_Scenario3Revoke3() {
        testScenarioWithAlgorithm(new Scenario3Revoke3());
    }

    @Test
    public void test_Scenario3Revoke4() {
        testScenarioWithAlgorithm(new Scenario3Revoke4());
    }

    @Test
    public void test_Scenario3Revoke5() {
        testScenarioWithAlgorithm(new Scenario3Revoke5());
    }

    @Test
    public void test_Scenario3Revoke6() {
        testScenarioWithAlgorithm(new Scenario3Revoke6());
    }

    @Test
    public void test_Scenario3Revoke7() {
        testScenarioWithAlgorithm(new Scenario3Revoke7());
    }

    @Test
    public void test_Scenario3Revoke8() {
        testScenarioWithAlgorithm(new Scenario3Revoke8());
    }

    @Test
    public void test_Scenario3Revoke9() {
        testScenarioWithAlgorithm(new Scenario3Revoke9());
    }

    @Test
    public void test_Scenario3Revoke10() {
        testScenarioWithAlgorithm(new Scenario3Revoke10());
    }

    @Test
    public void test_Scenario3Revoke11() {
        testScenarioWithAlgorithm(new Scenario3Revoke11());
    }

    @Test
    public void test_Scenario3Revoke12() {
        testScenarioWithAlgorithm(new Scenario3Revoke12());
    }

    @Test
    public void test_Scenario3Revoke13() {
        testScenarioWithAlgorithm(new Scenario3Revoke13());
    }

    @Test
    public void test_Scenario3Revoke14() {
        testScenarioWithAlgorithm(new Scenario3Revoke14());
    }

    @Test
    public void test_Scenario3Revoke15() {
        testScenarioWithAlgorithm(new Scenario3Revoke15());
    }

    @Test
    public void test_Scenario3Revoke16() {
        testScenarioWithAlgorithm(new Scenario3Revoke16());
    }

    @Test
    public void test_Scenario3Revoke17() {
        testScenarioWithAlgorithm(new Scenario3Revoke17());
    }

    @Test
    public void test_Scenario3Revoke18() {
        testScenarioWithAlgorithm(new Scenario3Revoke18());
    }

    @Test
    public void test_Scenario4MultipleSubjects1() {
        testScenarioWithAlgorithm(new Scenario4MultipleSubjects1());
    }

    @Test
    public void test_Scenario4MultipleSubjects2() {
        testScenarioWithAlgorithm(new Scenario4MultipleSubjects2());
    }

    @Test
    public void test_Scenario4MultipleSubjects3() {
        testScenarioWithAlgorithm(new Scenario4MultipleSubjects3());
    }

    @Test
    public void test_Scenario4MultipleSubjects4() {
        testScenarioWithAlgorithm(new Scenario4MultipleSubjects4());
    }

    @Test
    public void test_Scenario4MultipleSubjects5() {
        testScenarioWithAlgorithm(new Scenario4MultipleSubjects5());
    }

    @Test
    public void test_Scenario4MultipleSubjects6() {
        testScenarioWithAlgorithm(new Scenario4MultipleSubjects6());
    }

    @Test
    public void test_Scenario4MultipleSubjects7() {
        testScenarioWithAlgorithm(new Scenario4MultipleSubjects7());
    }

    @Test
    public void test_Scenario4MultipleSubjects8() {
        testScenarioWithAlgorithm(new Scenario4MultipleSubjects8());
    }

    @Test
    public void test_Scenario4MultipleSubjects9() {
        testScenarioWithAlgorithm(new Scenario4MultipleSubjects9());
    }

    @Test
    public void test_Scenario4MultipleSubjects10() {
        testScenarioWithAlgorithm(new Scenario4MultipleSubjects10());
    }

    @Test
    public void test_Scenario4MultipleSubjects11() {
        testScenarioWithAlgorithm(new Scenario4MultipleSubjects11());
    }

    @Test
    public void test_Scenario4MultipleSubjects12() {
        testScenarioWithAlgorithm(new Scenario4MultipleSubjects12());
    }

    @Test
    public void test_Scenario4MultipleSubjects13() {
        testScenarioWithAlgorithm(new Scenario4MultipleSubjects13());
    }

    @Test
    public void test_Scenario4MultipleSubjects14() {
        testScenarioWithAlgorithm(new Scenario4MultipleSubjects14());
    }

    @Test
    public void test_Scenario4MultipleSubjects15() {
        testScenarioWithAlgorithm(new Scenario4MultipleSubjects15());
    }

    @Test
    public void test_Scenario4MultipleSubjects16() {
        testScenarioWithAlgorithm(new Scenario4MultipleSubjects16());
    }

    @Test
    public void test_Scenario4MultipleSubjects17() {
        testScenarioWithAlgorithm(new Scenario4MultipleSubjects17());
    }

    @Test
    public void test_Scenario4MultipleSubjects18() {
        testScenarioWithAlgorithm(new Scenario4MultipleSubjects18());
    }

    @Test
    public void test_Scenario4MultipleSubjects19() {
        testScenarioWithAlgorithm(new Scenario4MultipleSubjects19());
    }

    @Test
    public void test_Scenario4MultipleSubjects20() {
        testScenarioWithAlgorithm(new Scenario4MultipleSubjects20());
    }

    @Test
    public void test_Scenario4MultipleSubjects21() {
        testScenarioWithAlgorithm(new Scenario4MultipleSubjects21());
    }

    @Test
    public void test_Scenario4MultipleSubjects22() {
        testScenarioWithAlgorithm(new Scenario4MultipleSubjects22());
    }

    @Test
    public void test_Scenario4MultipleSubjects23() {
        testScenarioWithAlgorithm(new Scenario4MultipleSubjects23());
    }

    @Test
    public void test_Scenario4MultipleSubjects24() {
        testScenarioWithAlgorithm(new Scenario4MultipleSubjects24());
    }

    @Test
    public void test_Scenario4MultipleSubjects25() {
        testScenarioWithAlgorithm(new Scenario4MultipleSubjects25());
    }

    @Test
    public void test_Scenario4MultipleSubjects26() {
        testScenarioWithAlgorithm(new Scenario4MultipleSubjects26());
    }

    @Test
    public void test_Scenario4MultipleSubjects27() {
        testScenarioWithAlgorithm(new Scenario4MultipleSubjects27());
    }

    @Test
    public void test_Scenario5() {
        testScenarioWithAlgorithm(new Scenario5Simple1());
    }

    @Test
    public void test_JsonViewScenario1() {
        testScenarioWithAlgorithm(new JsonViewScenario1());
    }

    @Test
    public void test_JsonViewScenario2() {
        testScenarioWithAlgorithm(new JsonViewScenario2());
    }

    @Test
    public void test_JsonViewScenario3() {
        testScenarioWithAlgorithm(new JsonViewScenario3());
    }

    @Test
    public void test_JsonViewScenario4() {
        testScenarioWithAlgorithm(new JsonViewScenario4());
    }

    @Test
    public void test_JsonViewScenario5() {
        testScenarioWithAlgorithm(new JsonViewScenario5());
    }

    @Test
    public void test_JsonViewScenario6() {
        testScenarioWithAlgorithm(new JsonViewScenario6());
    }

    @Test
    public void test_JsonViewScenario7() {
        testScenarioWithAlgorithm(new JsonViewScenario7());
    }

    @Test
    public void test_JsonViewScenario8() {
        testScenarioWithAlgorithm(new JsonViewScenario8());
    }

    @Test
    public void test_JsonViewScenario9() {
        testScenarioWithAlgorithm(new JsonViewScenario9());
    }

    @Test
    public void test_JsonViewScenario10() {
        testScenarioWithAlgorithm(new JsonViewScenario10());
    }

    @Test
    public void test_JsonViewScenario11() {
        testScenarioWithAlgorithm(new JsonViewScenario11());
    }

    @Test
    public void test_JsonViewScenario12() {
        testScenarioWithAlgorithm(new JsonViewScenario12());
    }

    @Test
    public void testJsonViewScenario13() {
        testScenarioWithAlgorithm(JsonViewScenario13.getInstance());
    }

    private void testScenarioWithAlgorithm(final Scenario scenario) {
        final ScenarioSetup setup = scenario.getSetup();
        final PolicyAlgorithm algorithm = getPolicyAlgorithm(scenario.getPolicy());
        final boolean authorized = scenario.getApplyAlgorithmFunction().apply(algorithm);

        assertThat(authorized)
                .overridingErrorMessage("Scenario <%s> did not result in expected result when running with algorithm " +
                        "<%s>:\n\t<%s>", scenario.getName(), algorithm.getName(), setup.getDescription())
                .isEqualTo(setup.getExpectedResult());

        setup.getExpectedJsonView().ifPresent(expectedJsonView -> {
            final JsonObject actualJsonView = setup.getFullJsonObject()
                    .map(jsonObject -> {
                        final JsonPointer resourcePointer = setup.getResource();
                        final JsonObject inputJson = jsonObject
                                .getValue(resourcePointer)
                                .map(JsonValue::asObject)
                                .orElse(JsonFactory.newObject());
                        return algorithm.buildJsonView(inputJson, setup);
                    })
                    .orElseThrow(() -> new AssertionError("jsonView was empty"));

            assertThat(actualJsonView).isEqualToIgnoringFieldDefinitions(expectedJsonView);
        });

        setup.getExpectedSubjects().ifPresent(expectedSubjectIds -> {
            final ResourceKey resKey = ResourceKey.newInstance(Scenario.THING_TYPE, setup.getResource());
            final Permissions reqPermissions = setup.getRequiredPermissions();
            final EffectedSubjects effectedSubjects = algorithm.getSubjectsWithPermission(resKey, reqPermissions);
            final Set<AuthorizationSubject> grantedSubjects = effectedSubjects.getGranted();

            assertThat(grantedSubjects)
                    .overridingErrorMessage("Expected\n<%s> to have <%s> granted on resource <%s> but actually" +
                            " only\n<%s> have!", expectedSubjectIds, reqPermissions, resKey, effectedSubjects)
                    .containsAll(expectedSubjectIds);
        });

        setup.getAdditionalAlgorithmFunction().ifPresent(algorithmFunction -> {
            final Boolean passed = algorithmFunction.apply(algorithm);
            Assert.assertTrue("AlgorithmFunction which was expected to pass did not pass", passed);
        });
    }

    @Test
    public void grantedPolicyTypeDoesNotGrantThingAccess() {
        final String SUBJECT_ALL_POLICY_GRANTED = "sid_policy_all";

        final PolicyId policyId = PolicyId.of("org.eclipse.ditto", UUID.randomUUID().toString().replace("-", ""));
        final Policy POLICY = Policy.newBuilder(policyId)
                .forLabel("DEFAULT")
                .setSubject(Subject.newInstance(SubjectIssuer.GOOGLE, SUBJECT_ALL_POLICY_GRANTED))
                .setGrantedPermissions(PoliciesResourceType.policyResource("/"),
                        "READ", "WRITE")
                .build();

        final Enforcer policyEnforcer = TreeBasedPolicyEnforcer.createInstance(POLICY);

        final boolean actual = policyEnforcer
                .hasPartialPermissions(PoliciesResourceType.thingResource("/"),
                        AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                                AuthorizationSubject.newInstance(
                                        SubjectId.newInstance(SubjectIssuer.GOOGLE, SUBJECT_ALL_POLICY_GRANTED)
                                                .toString())),
                        "READ");
        assertThat(actual).isFalse();
    }

}
