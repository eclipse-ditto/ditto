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
package org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.scenario3;

import java.util.function.Function;

import org.eclipse.ditto.policies.model.enforcers.testbench.algorithms.PolicyAlgorithm;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.SubjectIssuer;


public interface Scenario3Revoke extends Scenario {

    String SCENARIO_GROUP_NAME = Scenario3Revoke.class.getSimpleName();

    String SUBJECT_ALL_GRANTED_ATTRIBUTES_REVOKED = "sid_all_attributes_revoked";
    String SUBJECT_FEATURES_READ_GRANTED_FIRMWARE_READ_REVOKED = "sid_features_read_firmware_read_revoke";
    String SUBJECT_FEATURE_FOO_ALL_GRANTED_SPECIAL_PROPERTY_REVOKED =
            "sid_feature_foo_all_granted_special_property_revoked";

    Policy POLICY = PoliciesModelFactory //
            .newPolicyBuilder(PolicyId.of("benchmark", Scenario3Revoke.class.getSimpleName())) //
            .forLabel("all") //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_ALL_GRANTED_ATTRIBUTES_REVOKED) //
            .setGrantedPermissions(PoliciesResourceType.thingResource("/"), "READ", "WRITE") //
            .forLabel("attributes-revoked") //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_ALL_GRANTED_ATTRIBUTES_REVOKED) //
            .setRevokedPermissions(PoliciesResourceType.thingResource("/attributes"), "READ",
                    "WRITE") //
            .forLabel("attributes-location-read-allowed") //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_ALL_GRANTED_ATTRIBUTES_REVOKED) //
            .setGrantedPermissions(PoliciesResourceType.thingResource("/attributes/location"), "READ") //
            .forLabel("features") //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_FEATURES_READ_GRANTED_FIRMWARE_READ_REVOKED) //
            .setGrantedPermissions(PoliciesResourceType.thingResource("/features"), "READ") //
            .setRevokedPermissions(PoliciesResourceType.thingResource("/features/firmware"), "READ") //
            .forLabel("features-foo") //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_FEATURE_FOO_ALL_GRANTED_SPECIAL_PROPERTY_REVOKED) //
            .setGrantedPermissions(PoliciesResourceType.thingResource("/features/foo"), "READ",
                    "WRITE") //
            .forLabel("features-foo-special-property-revoked") //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_FEATURE_FOO_ALL_GRANTED_SPECIAL_PROPERTY_REVOKED) //
            .setRevokedPermissions(PoliciesResourceType.thingResource("/features/foo/properties/special"),
                    "READ", "WRITE") //
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
