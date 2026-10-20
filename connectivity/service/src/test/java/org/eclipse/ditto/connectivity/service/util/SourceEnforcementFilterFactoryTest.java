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
package org.eclipse.ditto.connectivity.service.util;

import org.eclipse.ditto.base.model.common.ResponseType;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.Enforcement;
import org.eclipse.ditto.connectivity.model.EnforcementFilter;
import org.eclipse.ditto.connectivity.model.ReplyTarget;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.junit.Test;

import java.util.Map;
import java.util.Optional;

import static org.eclipse.ditto.connectivity.service.messaging.TestConstants.Authorization.AUTHORIZATION_CONTEXT;
import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

public class SourceEnforcementFilterFactoryTest {

    @Test
    public void testSourceEnforcementCreatesFilter() {
        final Enforcement enforcement = ConnectivityModelFactory.newEnforcement("{{ source:address }}", "{{ thing:namespace }}.{{ thing:name }}");
        final Source source = ConnectivityModelFactory.newSourceBuilder()
                .address("any")
                .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                .enforcement(enforcement)
                .headerMapping(TestConstants.HEADER_MAPPING)
                .payloadMapping(ConnectivityModelFactory.emptyPayloadMapping())
                .replyTarget(ReplyTarget.newBuilder()
                        .address("foo")
                        .expectedResponseTypes(ResponseType.ERROR, ResponseType.RESPONSE, ResponseType.NACK)
                        .build())
                .build();

        final Optional<EnforcementFilter<Signal<?>>> filter =
                SourceEnforcementFilterFactory.of(source).getFilter(Map.of(), "any");

        assertThat(filter).isNotEmpty();
    }

    @Test
    public void testNoEnforcementCreatesNoFilter() {
        final Source source = ConnectivityModelFactory.newSourceBuilder()
                .address("any")
                .authorizationContext(AUTHORIZATION_CONTEXT)
                .build();

        final Optional<EnforcementFilter<Signal<?>>> filter =
                SourceEnforcementFilterFactory.of(source).getFilter(Map.of(), "any");

        assertThat(filter).isEmpty();
    }

}
