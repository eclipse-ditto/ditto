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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.function.Function;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.protocol.Adaptable;
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
    public void getInstanceWithNullTargetTypeThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> AdaptableToSignalMapper.of(null, mappingFunction))
                .withMessage("The targetType must not be null!")
                .withNoCause();
    }

    @Test
    public void getInstanceWithNullMappingFunctionThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> AdaptableToSignalMapper.of(CreateThing.class, null))
                .withMessage("The mappingFunction must not be null!")
                .withNoCause();
    }

    @Test
    public void getInstanceWithValidArgumentsReturnsNotNull() {
        final AdaptableToSignalMapper<CreateThingResponse> instance =
                AdaptableToSignalMapper.of(CreateThingResponse.class, mappingFunction);

        assertThat(instance).isNotNull();
    }

    @Test
    public void mapAdaptableReturnsExpectedSignalIfNoExceptionOccurred() {
        final CreateThingResponse createThingResponse = CreateThingResponse.of(Mockito.mock(Thing.class), dittoHeaders);
        Mockito.when(mappingFunction.apply(Mockito.any(MappingContext.class))).thenReturn(createThingResponse);
        final AdaptableToSignalMapper<CreateThingResponse> underTest =
                AdaptableToSignalMapper.of(createThingResponse.getClass(), mappingFunction);

        assertThat(underTest.map(Mockito.mock(Adaptable.class))).isEqualTo(createThingResponse);
    }

    @Test
    public void mapAdaptableCreatesExpectedMappingContext() {
        final ArgumentCaptor<MappingContext> mappingContextArgumentCaptor =
                ArgumentCaptor.forClass(MappingContext.class);
        final Adaptable adaptable = Mockito.mock(Adaptable.class);
        final AdaptableToSignalMapper<CreateThingResponse> underTest =
                AdaptableToSignalMapper.of(CreateThingResponse.class, mappingFunction);

        underTest.map(adaptable);

        Mockito.verify(mappingFunction).apply(mappingContextArgumentCaptor.capture());
        assertThat(mappingContextArgumentCaptor.getValue()).isEqualTo(MappingContext.of(adaptable));
    }

    @Test
    public void mapAdaptableThrowsIllegalAdaptableExceptionIfMappingFunctionThrowsDittoRuntimeException() {
        final Adaptable adaptable = Mockito.mock(Adaptable.class);
        final IllegalAdaptableException illegalAdaptableException =
                new IllegalAdaptableException("This is a message.", "This is a description.", dittoHeaders);
        Mockito.when(mappingFunction.apply(Mockito.any(MappingContext.class))).thenThrow(illegalAdaptableException);
        final Class<CreateThingResponse> targetType = CreateThingResponse.class;
        final AdaptableToSignalMapper<CreateThingResponse> underTest =
                AdaptableToSignalMapper.of(targetType, mappingFunction);

        Assertions.assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(() -> underTest.map(adaptable))
                .withMessage("Failed to get <%s> for <%s>: %s",
                        targetType.getSimpleName(),
                        adaptable,
                        illegalAdaptableException.getMessage())
                .withCause(illegalAdaptableException);
    }

    @Test
    public void mapAdaptableThrowsIllegalAdaptableExceptionIfMappingFunctionThrowsRuntimeException() {
        final Adaptable adaptable = Mockito.mock(Adaptable.class);
        Mockito.when(adaptable.getDittoHeaders()).thenReturn(dittoHeaders);
        final IllegalArgumentException illegalArgumentException = new IllegalArgumentException("This is a message.");
        Mockito.when(mappingFunction.apply(Mockito.any(MappingContext.class))).thenThrow(illegalArgumentException);
        final Class<CreateThingResponse> targetType = CreateThingResponse.class;
        final AdaptableToSignalMapper<CreateThingResponse> underTest =
                AdaptableToSignalMapper.of(targetType, mappingFunction);

        Assertions.assertThatExceptionOfType(IllegalAdaptableException.class)
                .isThrownBy(() -> underTest.map(adaptable))
                .withMessage("Failed to get <%s> for <%s>: %s",
                        targetType.getSimpleName(),
                        adaptable,
                        illegalArgumentException.getMessage())
                .withCause(illegalArgumentException);
    }

}