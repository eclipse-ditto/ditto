/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.signals;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/**
 * Unit test for {@link FeatureToggle}.
 */
public final class FeatureToggleTest {

    @Test
    public void stacklessFlowControlExceptionsToggleDefaultsToTrue() {
        // Toggles are resolved once at class load via System.getProperty(name, "true"). Without an explicit
        // -D override the default is true.
        assertThat(FeatureToggle.isStacklessFlowControlExceptionsEnabled()).isTrue();
    }

    @Test
    public void stacklessFlowControlExceptionsPropertyNameIsStable() {
        // Regression guard so that renames cannot silently break operator-facing JVM properties.
        assertThat(FeatureToggle.STACKLESS_FLOW_CONTROL_EXCEPTIONS_ENABLED)
                .isEqualTo("ditto.devops.feature.stackless-flow-control-exceptions-enabled");
    }

    @Test
    public void policyLockoutPreventionToggleDefaultsToTrue() {
        assertThat(FeatureToggle.isPolicyLockoutPreventionEnabled()).isTrue();
    }

    @Test
    public void policyLockoutPreventionPropertyNameIsStable() {
        assertThat(FeatureToggle.POLICY_LOCKOUT_PREVENTION_ENABLED)
                .isEqualTo("ditto.devops.feature.policy-lockout-prevention-enabled");
    }

}
