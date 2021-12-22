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
package org.eclipse.ditto.protocol.mappingstrategies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.function.Function;

import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThingResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link AdaptableToSignalMapper}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class AdaptableToSignalMapperTest {

    @Rule
    public final TestNameCorrelationId testNameCorrelationId = TestNameCorrelationId.newInstance();

    @Mock
    private Function<MappingContext, CreateThingResponse> mappingFunction;

    private DittoHeaders dittoHeaders;

    @Before
    public void before() {
        dittoHeaders = DittoHeaders.newBuilder().correlationId(testNameCorrelationId.getCorrelationId()).build();
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(AdaptableToSignalMapper.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void getInstanceWithNullSignalTypeThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> AdaptableToSignalMapper.of((String) null, mappingFunction))
                .withMessage("The signalType must not be null!")
                .withNoCause();
    }

    @Test
    public void getInstanceWithEmptySignalTypeThrowsException() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> AdaptableToSignalMapper.of("", mappingFunction))
                .withMessage("The signalType must not be blank.")
                .withNoCause();
    }

    @Test
    public void getInstanceWithBlankSignalTypeThrowsException() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> AdaptableToSignalMapper.of(" ", mappingFunction))
                .withMessage("The signalType must not be blank.")
                .withNoCause();
    }

    @Test
    public void getInstanceWithNullMappingFunctionThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> AdaptableToSignalMapper.of(CreateThing.TYPE, null))
                .withMessage("The mappingFunction must not be null!")
                .withNoCause();
    }

    @Test
    public void getInstanceWithValidArgumentsReturnsNotNull() {
        final AdaptableToSignalMapper<CreateThingResponse> instance =
                AdaptableToSignalMapper.of(CreateThingResponse.TYPE, mappingFunction);

        assertThat(instance).isNotNull();
    }

    @Test
    public void mapAdaptableReturnsExpectedSignalIfNoExceptionOccurred() {
        final CreateThingResponse createThingResponse = CreateThingResponse.of(Mockito.mock(Thing.class), dittoHeaders);
        Mockito.when(mappingFunction.apply(Mockito.any(MappingContext.class))).thenReturn(createThingResponse);
        final AdaptableToSignalMapper<CreateThingResponse> underTest =
                AdaptableToSignalMapper.of(createThingResponse.getType(), mappingFunction);

        assertThat(underTest.map(Mockito.mock(Adaptable.class))).isEqualTo(createThingResponse);
    }

    @Test
    public void mapAdaptableCreatesExpectedMappingContext() {
        final ArgumentCaptor<MappingContext> mappingContextArgumentCaptor =
                ArgumentCaptor.forClass(MappingContext.class);
        final Adaptable adaptable = Mockito.mock(Adaptable.class);
        final AdaptableToSignalMapper<CreateThingResponse> underTest =
                AdaptableToSignalMapper.of(CreateThingResponse.TYPE, mappingFunction);

        underTest.map(adaptable);

        Mockito.verify(mappingFunction).apply(mappingContextArgumentCaptor.capture());
        assertThat(mappingContextArgumentCaptor.getValue()).isEqualTo(MappingContext.of(adaptable));
    }

    @Test
    public void mapAdaptableThrowsIllegalAdaptableExceptionIfMappingFunctionThrowsDittoRuntimeException() {
        final Adaptable adaptable = Mockito.mock(Adaptable.class);
        Mockito.when(adaptable.getDittoHeaders()).thenReturn(dittoHeaders);
        Mockito.when(adaptable.getTopicPath()).thenReturn(Mockito.mock(TopicPath.class));
        final IllegalAdaptableException cause =
                IllegalAdaptableException.newInstance("This is a message.", "This is a description.", adaptable);
        Mockito.when(mappingFunction.apply(Mockito.any(MappingContext.class))).thenThrow(cause);
        final String signalType = CreateThingResponse.TYPE;
        final AdaptableToSignalMapper<CreateThingResponse> underTest =
                AdaptableToSignalMapper.of(signalType, mappingFunction);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(() -> underTest.map(adaptable))
                .withMessage("Failed to get Signal of type <%s> for <%s>: %s",
                        signalType,
                        adaptable,
                        cause.getMessage())
                .satisfies(illegalAdaptableException -> assertThat(illegalAdaptableException.getSignalType())
                        .as("signal type")
                        .contains(signalType))
                .withCause(cause);
    }

    @Test
    public void mapAdaptableThrowsIllegalAdaptableExceptionIfMappingFunctionThrowsRuntimeException() {
        final Adaptable adaptable = Mockito.mock(Adaptable.class);
        Mockito.when(adaptable.getDittoHeaders()).thenReturn(dittoHeaders);
        Mockito.when(adaptable.getTopicPath()).thenReturn(Mockito.mock(TopicPath.class));
        final IllegalArgumentException illegalArgumentException = new IllegalArgumentException("This is a message.");
        Mockito.when(mappingFunction.apply(Mockito.any(MappingContext.class))).thenThrow(illegalArgumentException);
        final String targetSignalType = CreateThingResponse.TYPE;
        final AdaptableToSignalMapper<CreateThingResponse> underTest =
                AdaptableToSignalMapper.of(targetSignalType, mappingFunction);

        assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(() -> underTest.map(adaptable))
                .withMessage("Failed to get Signal of type <%s> for <%s>: %s",
                        targetSignalType,
                        adaptable,
                        illegalArgumentException.getMessage())
                .satisfies(illegalAdaptableException -> assertThat(illegalAdaptableException.getSignalType())
                        .as("signal type")
                        .contains(targetSignalType))
                .withCause(illegalArgumentException);
    }

    @Test
    public void getSignalTypeReturnsExpected() {
        final CreateThingResponse createThingResponse = CreateThingResponse.of(Mockito.mock(Thing.class), dittoHeaders);
        final String signalType = createThingResponse.getType();
        final AdaptableToSignalMapper<CreateThingResponse> underTest =
                AdaptableToSignalMapper.of(signalType, mappingFunction);

        assertThat(underTest.getSignalType()).isEqualTo(signalType);
    }

}