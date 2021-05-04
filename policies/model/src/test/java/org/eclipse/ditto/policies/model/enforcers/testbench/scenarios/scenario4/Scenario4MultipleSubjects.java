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
package org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario4;

import java.util.function.Function;

import org.eclipse.ditto.policies.model.enforcers.testbench.algorithms.PolicyAlgorithm;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.SubjectIssuer;


public interface Scenario4MultipleSubjects extends Scenario {

    String SCENARIO_GROUP_NAME = Scenario4MultipleSubjects.class.getSimpleName();

    String SUBJECT_1 = "sid_1";
    String SUBJECT_2 = "sid_2";
    String SUBJECT_3 = "gid_3";
    String SUBJECT_4 = "gid_4";
    String SUBJECT_5 = "rid_5";
    String SUBJECT_6 = "rid_6";
    String SUBJECT_7 = "rid_7";
    String SUBJECT_8 = "uid_8";
    String SUBJECT_9 = "gid_9";

    Policy POLICY = PoliciesModelFactory //
            .newPolicyBuilder(PolicyId.of("benchmark", Scenario4MultipleSubjects.class.getSimpleName())) //
            .forLabel("one") //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_1) //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_2) //
            .setGrantedPermissions(PoliciesResourceType.thingResource("/"), "READ", "WRITE") //
            .setRevokedPermissions(PoliciesResourceType.thingResource("/attributes"), "READ",
                    "WRITE") //
            .forLabel("two") //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_3) //
            .setRevokedPermissions(PoliciesResourceType.thingResource("/"), "WRITE") //
            .forLabel("three") //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_3) //
            .setGrantedPermissions(PoliciesResourceType.thingResource("/attributes"), "READ",
                    "WRITE") //
            .setGrantedPermissions(PoliciesResourceType.thingResource("/features"), "READ",
                    "WRITE") //
            .setRevokedPermissions(PoliciesResourceType.thingResource("/features/foo1"), "READ") //
            .setRevokedPermissions(PoliciesResourceType.thingResource("/features/foo2"), "WRITE") //
            .setRevokedPermissions(PoliciesResourceType.thingResource("/features/foo3"), "READ",
                    "WRITE") //
            .forLabel("four") //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_3) //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_4) //
            .setRevokedPermissions(PoliciesResourceType.thingResource("/attributes/nogo1"), "READ",
                    "WRITE") //
            .setRevokedPermissions(PoliciesResourceType.thingResource("/attributes/nogo2"), "READ") //
            .setRevokedPermissions(PoliciesResourceType.thingResource("/attributes/nogo2"), "WRITE") //
            .setGrantedPermissions(PoliciesResourceType.thingResource("/attributes/nogo1/go1"), "READ",
                    "WRITE") //
            .setGrantedPermissions(PoliciesResourceType.thingResource("/attributes/nogo2/go2"), "READ") //
            .forLabel("five") //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_5) //
            .setGrantedPermissions(PoliciesResourceType.thingResource("/features/public/properties/location"),
                    "READ") //
            .setGrantedPermissions(PoliciesResourceType.thingResource("/features/lamp/properties/config/on"),
                    "WRITE") //
            .forLabel("six_read") //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_5) //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_6) //
            .setGrantedPermissions(PoliciesResourceType.thingResource("/attributes/read_write"), "READ") //
            .forLabel("six_write") //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_6) //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_3) //
            .setGrantedPermissions(PoliciesResourceType.thingResource("/attributes/read_write"), "WRITE") //
            .forLabel("seven_write") //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_7) //
            .setGrantedPermissions(PoliciesResourceType.thingResource("/attributes"), "WRITE") //
            .forLabel("eight") //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_8) //
            .setGrantedPermissions(PoliciesResourceType.thingResource("/attributes"), "READ",
                    "WRITE") //
            .forLabel("nine_write_only")
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_9)
            .setGrantedPermissions(PoliciesResourceType.thingResource("/attribute/nogo2/go2"), "WRITE")
            .setRevokedPermissions(PoliciesResourceType.thingResource("/attribute/nogo2"), "READ")
            .build();

    default Policy getPolicy() {
        return POLICY;
    }

    @Override
    default String getScenarioGroup() {
        return SCENARIO_GROUP_NAME;
    }

    @Override
    default Function<PolicyAlgorithm, Boolean> getApplyAlgorithmFunction() {
        return algorithm -> algorithm.hasPermissionsOnResource(getSetup());
    }
}
