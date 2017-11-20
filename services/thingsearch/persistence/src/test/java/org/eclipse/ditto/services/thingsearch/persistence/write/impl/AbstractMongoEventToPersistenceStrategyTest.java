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
package org.eclipse.ditto.services.thingsearch.persistence.write.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.Permissions;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policiesenforcers.EffectedSubjectIds;
import org.eclipse.ditto.model.policiesenforcers.ImmutableEffectedSubjectIds;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcer;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.thingsearch.persistence.write.IndexLengthRestrictionEnforcer;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

@RunWith(Parameterized.class)
public abstract class AbstractMongoEventToPersistenceStrategyTest {

    @Parameterized.Parameter
    public JsonSchemaVersion version;

    PolicyEnforcer policyEnforcer;

    IndexLengthRestrictionEnforcer indexLengthRestrictionEnforcer;

    @Parameterized.Parameters(name = "v{0}")
    public static List<JsonSchemaVersion> versions() {
        return Arrays.asList(JsonSchemaVersion.values());
    }

    @Before
    public void setUpMocks() {
        policyEnforcer = Mockito.mock(PolicyEnforcer.class);
        indexLengthRestrictionEnforcer = Mockito.mock(IndexLengthRestrictionEnforcer.class);

        final EffectedSubjectIds effectedSubjectIds = ImmutableEffectedSubjectIds.of(Collections.emptyList(),
                Collections.emptyList());
        when(policyEnforcer.getSubjectIdsWithPermission(any(ResourceKey.class), anyString(), any()))
                .thenReturn(effectedSubjectIds);

        when(indexLengthRestrictionEnforcer.enforceRestrictions(any(Thing.class))).thenAnswer(arg(0));
        when(indexLengthRestrictionEnforcer.enforceRestrictions(any(Feature.class))).thenAnswer(arg(0));
        when(indexLengthRestrictionEnforcer.enforceRestrictions(any(Features.class))).thenAnswer(arg(0));
        when(indexLengthRestrictionEnforcer.enforceRestrictions(any(Attributes.class))).thenAnswer(arg(0));
        when(indexLengthRestrictionEnforcer.enforceRestrictions(anyString(), any(FeatureProperties.class))).thenAnswer
                (arg(1));
        when(indexLengthRestrictionEnforcer.enforceRestrictionsOnAttributeValue(any(JsonPointer.class),
                any(JsonValue.class))).thenAnswer(arg(1));
        when(indexLengthRestrictionEnforcer.enforceRestrictionsOnFeatureProperty(anyString(), any(JsonPointer.class),
                any(JsonValue.class))).thenAnswer(arg(2));

    }

    void verifyPermissionCallForSchemaVersion(final Supplier<ResourceKey> resourceKey, final Permissions
            permissions) {
        if (!isV1()) {
            verify(policyEnforcer).getSubjectIdsWithPermission(resourceKey.get(), permissions);
        }
    }

    void verifyPermissionCallForSchemaVersion(
            final Supplier<ResourceKey> resourceKeySupplier,
            final Supplier<String> permissionSupplier,
            final int n,
            final Supplier<String>... permissionsSupplier) {
        if (!isV1()) {
            if (0 == permissionsSupplier.length) {
                verify(policyEnforcer, times(n)).getSubjectIdsWithPermission(resourceKeySupplier.get(),
                        permissionSupplier
                                .get());
            } else {
                verify(policyEnforcer, times(n)).getSubjectIdsWithPermission(resourceKeySupplier.get(),
                        permissionSupplier.get(),
                        (String[]) Stream.of(permissionsSupplier).map(Supplier::get).collect(Collectors.toList())
                                .toArray());
            }
        }
    }


    void verifyPolicyUpdatesForSchemaVersion(final List<PolicyUpdate> updates, final int expectedForV2) {
        if (isV1()) {
            assertThat(updates).isEmpty();
        } else {
            assertThat(updates).hasSize(expectedForV2);
        }
    }

    private boolean isV1() {
        return version.equals(JsonSchemaVersion.V_1);
    }

    private Answer arg(final int index) {
        return invocationOnMock -> invocationOnMock.getArgument(index);
    }

}
