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
package org.eclipse.ditto.base.model.acks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.base.model.acks.ImmutableFilteredAcknowledgementRequest}.
 */
public final class ImmutableFilteredAcknowledgementRequestTest {

    static final String FILTER = "fn:filter(header:qos,'ne',0)";
    static final Set<AcknowledgementRequest> INCLUDES = new HashSet<>(Collections.singletonList(
            AcknowledgementRequest.parseAcknowledgementRequest(DittoAcknowledgementLabel.TWIN_PERSISTED)));
    static final FilteredAcknowledgementRequest FILTERED_ACKNOWLEDGEMENT_REQUEST =
            FilteredAcknowledgementRequest.of(INCLUDES, FILTER);

    static final JsonObject FILTERED_ACKNOWLEDGEMENT_REQUEST_JSON = JsonObject
            .newBuilder()
            .set(FilteredAcknowledgementRequest.JsonFields.INCLUDES, INCLUDES.stream()
                    .map(AcknowledgementRequest::getLabel)
                    .map(AcknowledgementLabel::toString)
                    .map(JsonFactory::newValue)
                    .collect(JsonCollectors.valuesToArray()))
            .set(FilteredAcknowledgementRequest.JsonFields.FILTER, FILTER)
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableFilteredAcknowledgementRequest.class,
                areImmutable(),
                provided(AcknowledgementRequest.class).isAlsoImmutable()
        );
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableFilteredAcknowledgementRequest.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void tryToGetInstanceWithNullIncludes() {
        assertThatNullPointerException()
                .isThrownBy(() -> ImmutableFilteredAcknowledgementRequest.getInstance(null, null))
                .withMessage("The includes must not be null!")
                .withNoCause();
    }

    @Test
    public void getIncludesAndFilterReturnsExpected() {
        final ImmutableFilteredAcknowledgementRequest underTest =
                ImmutableFilteredAcknowledgementRequest.getInstance(INCLUDES, FILTER);

        assertThat(underTest.getIncludes()).isEqualTo(INCLUDES);
        assertThat(underTest.getFilter().orElse(null)).isEqualTo(FILTER);
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual = FILTERED_ACKNOWLEDGEMENT_REQUEST.toJson();
        assertThat(actual).isEqualTo(FILTERED_ACKNOWLEDGEMENT_REQUEST_JSON);
    }

    @Test
    public void fromJsonReturnsExpected() {
        final FilteredAcknowledgementRequest actual =
                ImmutableFilteredAcknowledgementRequest.fromJson(FILTERED_ACKNOWLEDGEMENT_REQUEST_JSON);
        assertThat(actual).isEqualTo(FILTERED_ACKNOWLEDGEMENT_REQUEST);
    }

    @Test
    public void toStringReturnsExpected() {
        final ImmutableFilteredAcknowledgementRequest underTest =
                ImmutableFilteredAcknowledgementRequest.getInstance(INCLUDES, FILTER);

        assertThat(underTest.toString()).hasToString(
                ImmutableFilteredAcknowledgementRequest.class.getSimpleName() + " [" +
                        "includes=" + INCLUDES +
                        ", filter=" + FILTER +
                        "]");
    }

}
