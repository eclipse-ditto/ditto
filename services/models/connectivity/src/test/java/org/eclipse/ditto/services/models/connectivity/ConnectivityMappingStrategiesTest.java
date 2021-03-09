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
package org.eclipse.ditto.services.models.connectivity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collections;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.services.models.policies.PoliciesMappingStrategies;
import org.eclipse.ditto.signals.announcements.policies.SubjectDeletionAnnouncement;
import org.junit.Test;

/**
 * Tests {@link ConnectivityMappingStrategies}.
 */
public final class ConnectivityMappingStrategiesTest {

    @Test
    public void deserializeSubjectDeletionAnnouncement() {
        final Instant expiry = Instant.now();
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final SubjectDeletionAnnouncement announcement = SubjectDeletionAnnouncement.of(
                PolicyId.of("policy:id"),
                expiry,
                Collections.singleton(SubjectId.newInstance("ditto:ditto")),
                dittoHeaders
        );

        final JsonObject json = announcement.toJson();
        final PoliciesMappingStrategies underTest = PoliciesMappingStrategies.getInstance();
        final Jsonifiable<?> output = underTest.getMappingStrategy(announcement.getManifest())
                .orElseThrow()
                .parse(json, dittoHeaders);

        assertThat(output).isEqualTo(announcement);
    }

}
