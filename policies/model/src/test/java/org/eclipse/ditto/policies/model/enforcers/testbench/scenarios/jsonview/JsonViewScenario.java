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
package org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.jsonview;

import java.util.function.Function;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.enforcers.testbench.algorithms.PolicyAlgorithm;
import org.eclipse.ditto.policies.model.enforcers.testbench.scenarios.Scenario;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.SubjectIssuer;


public interface JsonViewScenario extends Scenario {

    String SCENARIO_GROUP_NAME = JsonViewScenario.class.getSimpleName();

    String SUBJECT_ALL_GRANTED = "sid_all";
    String SUBJECT_ATTRIBUTES_ALL_GRANTED = "sid_all_attributes";
    String SUBJECT_ALL_GRANTED_ATTRIBUTES_REVOKED = "sid_all_attributes_revoked";
    String SUBJECT_NONEXISTENT_ATTRIBUTE_GRANTED = "sid_nonexistent_attribute_granted";
    String SUBJECT_FEATURES_READ_GRANTED = "sid_features_read";
    String SUBJECT_FEATURES_READ_GRANTED_FIRMWARE_READ_REVOKED = "sid_features_read_firmware_read_revoke";
    String SUBJECT_FEATURE_FOO_ALL_GRANTED_SPECIAL_PROPERTY_REVOKED =
            "sid_feature_foo_all_granted_special_property_revoked";
    String SUBJECT_ALL_FEATURES_REVOKED = "sid_all_features_revoked";
    String SUBJECT_SOME_GRANTED = "sid_some_granted";
    String SUBJECT_SOME_REVOKED = "sid_some_revoked";

    Policy POLICY = PoliciesModelFactory //
            .newPolicyBuilder(PolicyId.of("benchmark", JsonViewScenario.class.getSimpleName())) //
            .forLabel("all") //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_ALL_GRANTED) //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_ALL_GRANTED_ATTRIBUTES_REVOKED) //
            .setGrantedPermissions(PoliciesResourceType.thingResource("/"), "READ", "WRITE") //
            .forLabel("all-attributes") //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_ATTRIBUTES_ALL_GRANTED) //
            .setGrantedPermissions(PoliciesResourceType.thingResource("/attributes"), "READ",
                    "WRITE") //
            .forLabel("attributes-revoked") //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_ALL_GRANTED_ATTRIBUTES_REVOKED) //
            .setRevokedPermissions(PoliciesResourceType.thingResource("/attributes"), "READ",
                    "WRITE") //
            .forLabel("attributes-location-read-allowed") //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_ALL_GRANTED_ATTRIBUTES_REVOKED) //
            .setGrantedPermissions(PoliciesResourceType.thingResource("/attributes/location"), "READ") //
            .forLabel("features-read") //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_FEATURES_READ_GRANTED) //
            .setGrantedPermissions(PoliciesResourceType.thingResource("/features"), "READ") //
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
            .forLabel("features-revoked") //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_ALL_FEATURES_REVOKED) //
            .setRevokedPermissions(PoliciesResourceType.thingResource("/features"), "READ") //
            .forLabel("some-granted-of-each") //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_SOME_GRANTED) //
            .setGrantedPermissions(PoliciesResourceType.thingResource("/attributes/attr2"), "READ") //
            .setGrantedPermissions(PoliciesResourceType.thingResource("/attributes/location"), "READ") //
            .setGrantedPermissions(
                    PoliciesResourceType.thingResource("/features/firmware/properties/modulesVersions/b"),
                    "READ") //
            .setGrantedPermissions(PoliciesResourceType.thingResource("/features/foo/properties/special"),
                    "READ") //
            .forLabel("some-revoked-of-each") //
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_SOME_REVOKED) //
            .setRevokedPermissions(PoliciesResourceType.thingResource("/attributes/location/latitude"),
                    "READ") //
            .setRevokedPermissions(PoliciesResourceType.thingResource("/features/firmware/properties/modulesVersions"),
                    "READ") //
            .setRevokedPermissions(PoliciesResourceType.thingResource("/features/foo/properties/special"),
                    "READ") //
            .forLabel("nonexistent-attribute-granted")
            .setSubject(SubjectIssuer.GOOGLE, SUBJECT_NONEXISTENT_ATTRIBUTE_GRANTED)
            .setGrantedPermissions(PoliciesResourceType.thingResource("/attributes/location/nonexistentAttribute"),
                    "READ")
            .build();

    JsonObject THING = JsonFactory.newObjectBuilder()
            .set("attributes", JsonFactory.newObjectBuilder()
                    .set("attr1", 1) //
                    .set("attr2", "string") //
                    .set("attr3", false) //
                    .set("location", JsonFactory.newObjectBuilder() //
                            .set("latitude", 47.123) //
                            .set("longitude", 42.547) //
                            .build() //
                    ) //
                    .build()
            ) //
            .set("features", JsonFactory.newObjectBuilder() //
                    .set("firmware", JsonFactory.newObjectBuilder()//
                            .set("properties", JsonFactory.newObjectBuilder() //
                                    .set("version", "2.0.5") //
                                    .set("updated", "2016-11-11T08:39:05Z") //
                                    .set("modulesVersions", JsonFactory.newObjectBuilder() //
                                            .set("a", "3.2.0") //
                                            .set("b", "42") //
                                            .build() //
                                    ) //
                                    .build() //
                            ) //
                            .build() //
                    ) //
                    .set("foo", JsonFactory.newObjectBuilder() //
                            .set("properties", JsonFactory.newObjectBuilder() //
                                    .set("ordinary", 123456) //
                                    .set("special", "secret") //
                                    .build() //
                            ) //
                            .build() //
                    ) //
                    .build() //
            ) //
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
        // algorithm invoked with hasPermissionsOnResourceOrAnySubresource! as we would like to know if the subject can read anywhere
        // in the hierarchy below the passed path:
        return algorithm -> algorithm.hasPermissionsOnResourceOrAnySubresource(getSetup());
    }
}
