/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.routes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.gateway.service.util.config.endpoints.HttpConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit test for {@link QueryParametersToHeadersMap}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class QueryParametersToHeadersMapTest {

    @Mock
    private HttpConfig httpConfig;

    @Test
    public void assertImmutability() {
        assertInstancesOf(QueryParametersToHeadersMap.class,
                areImmutable(),
                assumingFields("queryParametersAsHeaders")
                        .areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void tryToGetInstanceWithNullHttpConfig() {
        assertThatNullPointerException()
                .isThrownBy(() -> QueryParametersToHeadersMap.getInstance(null))
                .withMessage("The httpConfig must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToConvertNullQueryParameters() {
        Mockito.when(httpConfig.getQueryParametersAsHeaders()).thenReturn(Collections.emptySet());

        final QueryParametersToHeadersMap underTest = QueryParametersToHeadersMap.getInstance(httpConfig);

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.apply(null))
                .withMessage("The queryParameters must not be null!")
                .withNoCause();
    }

    @Test
    public void convertEmptyQueryParameters() {
        Mockito.when(httpConfig.getQueryParametersAsHeaders())
                .thenReturn(Set.of(DittoHeaderDefinition.REQUESTED_ACKS, DittoHeaderDefinition.TIMEOUT));

        final QueryParametersToHeadersMap underTest = QueryParametersToHeadersMap.getInstance(httpConfig);

        assertThat(underTest.apply(Collections.emptyMap())).isEmpty();
    }

    @Test
    public void convertQueryParametersWithoutConfiguredHeaderNames() {
        Mockito.when(httpConfig.getQueryParametersAsHeaders()).thenReturn(Collections.emptySet());
        final Map<String, String> queryParameters =
                Map.of(DittoHeaderDefinition.REQUESTED_ACKS.getKey(), DittoAcknowledgementLabel.TWIN_PERSISTED.toString(),
                        DittoHeaderDefinition.TIMEOUT.getKey(), "5s");

        final QueryParametersToHeadersMap underTest = QueryParametersToHeadersMap.getInstance(httpConfig);

        assertThat(underTest.apply(queryParameters)).isEmpty();
    }

    @Test
    public void convertQueryParameters() {
        Mockito.when(httpConfig.getQueryParametersAsHeaders())
                .thenReturn(Set.of(DittoHeaderDefinition.REQUESTED_ACKS, DittoHeaderDefinition.TIMEOUT,
                        DittoHeaderDefinition.ALLOW_POLICY_LOCKOUT, DittoHeaderDefinition.CONDITION));
        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put("foo", "bar");
        queryParams.put(DittoHeaderDefinition.REQUESTED_ACKS.getKey(), DittoAcknowledgementLabel.TWIN_PERSISTED.toString());
        queryParams.put(DittoHeaderDefinition.CONTENT_TYPE.getKey(), "application/json");
        queryParams.put(DittoHeaderDefinition.TIMEOUT.getKey(), "5s");
        queryParams.put(DittoHeaderDefinition.ALLOW_POLICY_LOCKOUT.getKey(), "false");
        queryParams.put(DittoHeaderDefinition.CONDITION.getKey(), "eq(attributes/value, 42)");

        final Map<String, String> expected = new HashMap<>();
        expected.put(DittoHeaderDefinition.REQUESTED_ACKS.getKey(), DittoAcknowledgementLabel.TWIN_PERSISTED.toString());
        expected.put(DittoHeaderDefinition.TIMEOUT.getKey(), "5s");
        expected.put(DittoHeaderDefinition.ALLOW_POLICY_LOCKOUT.getKey(), "false");
        expected.put(DittoHeaderDefinition.CONDITION.getKey(), "eq(attributes/value, 42)");

        final QueryParametersToHeadersMap underTest = QueryParametersToHeadersMap.getInstance(httpConfig);

        assertThat(underTest.apply(queryParams)).isEqualTo(expected);
    }

}
