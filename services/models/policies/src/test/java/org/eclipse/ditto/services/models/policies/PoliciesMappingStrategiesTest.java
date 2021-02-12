/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.models.policies;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collections;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.signals.notifications.policies.SubjectExpiryNotification;
import org.junit.Test;

/**
 * Tests {@link PoliciesMappingStrategies}.
 */
public final class PoliciesMappingStrategiesTest {

    @Test
    public void deserializeSubjectExpiryNotification() {
        final Instant expiry = Instant.now();
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final SubjectExpiryNotification notification = SubjectExpiryNotification.of(
                PolicyId.of("policy:id"),
                expiry,
                Collections.singleton(SubjectId.newInstance("ditto:ditto")),
                dittoHeaders
        );

        final JsonObject json = notification.toJson();
        final PoliciesMappingStrategies underTest = PoliciesMappingStrategies.getInstance();
        final Jsonifiable<?> output = underTest.getMappingStrategy(notification.getManifest())
                .orElseThrow()
                .parse(json, dittoHeaders);

        assertThat(output).isEqualTo(notification);
    }
}
