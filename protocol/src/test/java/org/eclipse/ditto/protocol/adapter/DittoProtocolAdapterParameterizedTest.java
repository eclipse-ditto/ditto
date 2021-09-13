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
package org.eclipse.ditto.protocol.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.protocol.TopicPath.Channel.LIVE;
import static org.eclipse.ditto.protocol.TopicPath.Channel.NONE;
import static org.eclipse.ditto.protocol.TopicPath.Channel.TWIN;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommandResponse;
import org.eclipse.ditto.policies.model.signals.commands.PolicyErrorResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.PolicyModifyCommand;
import org.eclipse.ditto.policies.model.signals.commands.modify.PolicyModifyCommandResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.PolicyQueryCommand;
import org.eclipse.ditto.policies.model.signals.commands.query.PolicyQueryCommandResponse;
import org.eclipse.ditto.protocol.HeaderTranslator;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.UnknownChannelException;
import org.eclipse.ditto.protocol.adapter.connectivity.ConnectivityCommandAdapterProvider;
import org.eclipse.ditto.protocol.adapter.provider.AcknowledgementAdapterProvider;
import org.eclipse.ditto.protocol.adapter.provider.PolicyCommandAdapterProvider;
import org.eclipse.ditto.protocol.adapter.provider.ThingCommandAdapterProvider;
import org.eclipse.ditto.things.model.signals.commands.ThingErrorResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThing;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThingResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommand;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommand;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommandResponse;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.model.signals.events.ThingMerged;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit test for {@link org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter}.
 */
@SuppressWarnings({"unchecked", "rawtypes", "java:S3740"})
@RunWith(Parameterized.class)
public final class DittoProtocolAdapterParameterizedTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(DittoProtocolAdapterParameterizedTest.class);
    private static final DittoHeaders LIVE_DITTO_HEADERS = DittoHeaders.newBuilder().channel(LIVE.getName()).build();

    // mock all adapters and give them a name
    private static final Adapter<ThingQueryCommand<?>> thingQueryCommandAdapter =
            mock(Adapter.class, "ThingQueryCommandAdapter");
    private static final Adapter<ThingQueryCommandResponse<?>> thingQueryCommandResponseAdapter =
            mock(Adapter.class, "ThingQueryCommandResponseAdapter");
    private static final Adapter<ThingModifyCommand<?>> thingModifyCommandAdapter =
            mock(Adapter.class, "ThingModifyCommandAdapter");
    private static final Adapter<ThingModifyCommandResponse<?>> thingModifyCommandResponseAdapter =
            mock(Adapter.class, "ThingModifyCommandResponseAdapter");
    private static final Adapter<MergeThing> thingMergeCommandAdapter =
            mock(Adapter.class, "ThingModifyCommandAdapter");
    private static final Adapter<MergeThingResponse> thingMergeCommandResponseAdapter =
            mock(Adapter.class, "ThingModifyCommandResponseAdapter");
    private static final Adapter<ThingEvent<?>> thingEventAdapter = mock(Adapter.class, "ThingEventAdapter");
    private static final Adapter<ThingMerged> thingMergedEventAdapter = mock(Adapter.class, "ThingMergedEventAdapter");
    private static final Adapter<MessageCommand<?, ?>> messageCommandAdapter =
            mock(Adapter.class, "MessageCommandAdapter");
    private static final Adapter<MessageCommandResponse<?, ?>> messageCommandResponseAdapter =
            mock(Adapter.class, "MessageCommandResponseAdapter");
    private static final Adapter<ThingErrorResponse> thingErrorResponseAdapter =
            mock(Adapter.class, "ThingErrorResponseAdapter");
    private static final Adapter<PolicyQueryCommand<?>> policyQueryCommandAdapter =
            mock(Adapter.class, "PolicyQueryCommandAdapter");
    private static final Adapter<PolicyQueryCommandResponse<?>> policyQueryCommandResponseAdapter =
            mock(Adapter.class, "PolicyQueryCommandResponseAdapter");
    private static final Adapter<PolicyModifyCommand<?>> policyModifyCommandAdapter =
            mock(Adapter.class, "PolicyModifyCommandAdapter");
    private static final Adapter<PolicyModifyCommandResponse<?>> policyModifyCommandResponseAdapter =
            mock(Adapter.class, "PolicyModifyCommandResponseAdapter");
    private static final Adapter<PolicyErrorResponse> policyErrorResponseAdapter =
            mock(Adapter.class, "PolicyErrorResponseAdapter");

    // build test parameters with expected outcome
    private static final List<TestParameter> PARAMS;

    static {
        PARAMS = new ArrayList<>();
        PARAMS.add(new TestParameter(ThingQueryCommand.class, thingQueryCommandAdapter, TWIN, TWIN, LIVE));
        PARAMS.add(
                new TestParameter(ThingQueryCommandResponse.class, thingQueryCommandResponseAdapter, TWIN, TWIN, LIVE));
        PARAMS.add(new TestParameter(ThingModifyCommand.class, thingModifyCommandAdapter, TWIN, TWIN, LIVE));
        PARAMS.add(new TestParameter(ThingModifyCommandResponse.class, thingModifyCommandResponseAdapter, TWIN, TWIN,
                LIVE));
        PARAMS.add(new TestParameter(MergeThing.class, thingMergeCommandAdapter, TWIN, TWIN, LIVE));
        PARAMS.add(new TestParameter(MergeThingResponse.class, thingMergeCommandResponseAdapter, TWIN, TWIN, LIVE));
        PARAMS.add(new TestParameter(ThingErrorResponse.class, thingErrorResponseAdapter, TWIN, TWIN, LIVE));
        PARAMS.add(new TestParameter(MessageCommand.class, messageCommandAdapter, LIVE, LIVE));
        PARAMS.add(new TestParameter(MessageCommandResponse.class, messageCommandResponseAdapter, LIVE, LIVE));
        PARAMS.add(new TestParameter(ThingEvent.class, thingEventAdapter, TWIN, TWIN, LIVE));
        PARAMS.add(new TestParameter(ThingMerged.class, thingMergedEventAdapter, TWIN, TWIN, LIVE));
        PARAMS.add(new TestParameter(PolicyQueryCommand.class, policyQueryCommandAdapter, NONE, NONE));
        PARAMS.add(new TestParameter(PolicyQueryCommandResponse.class, policyQueryCommandResponseAdapter, NONE, NONE));
        PARAMS.add(new TestParameter(PolicyModifyCommand.class, policyModifyCommandAdapter, NONE, NONE));
        PARAMS.add(
                new TestParameter(PolicyModifyCommandResponse.class, policyModifyCommandResponseAdapter, NONE, NONE));
        PARAMS.add(new TestParameter(PolicyErrorResponse.class, policyErrorResponseAdapter, NONE, NONE));
    }

    private ProtocolAdapter underTest;

    /**
     * Contains the input signal and the expected outcome (which adapter is called with which channel).
     */
    @Parameterized.Parameter
    public TestParameter parameter;

    /**
     * The method to call on the {@link ProtocolAdapter} (is determined via reflection to avoid hard coding every
     * combination in the test).
     */
    @Parameterized.Parameter(1)
    public Method toAdaptable;

    /**
     * The ditto headers of the signal. The ProtocolAdapter determines the (live) channel from the headers.
     */
    @Parameterized.Parameter(2)
    public DittoHeaders dittoHeaders;

    /**
     * The protocol adapter may be called with a specific channel. If this parameter is null the method without
     * specifying a channel (e.g. toAdaptable(Signal)) is called.
     */
    @Parameterized.Parameter(3)
    public TopicPath.Channel givenChannel;

    /**
     * The channel that is expected in the call to the {@link Adapter}. If this parameter {@code null} the test expects
     * an exception.
     */
    @Parameterized.Parameter(4)
    public TopicPath.Channel expectedChannel;

    /**
     * Just to have a better description for the test.
     */
    @Parameterized.Parameter(5)
    public String methodSignature;

    @Parameterized.Parameters(name = "{index} {0} | {5} | given: {3} | exp: {4} | header: {2}")
    public static Collection<Object[]> data() {
        final Collection<Object[]> data = new ArrayList<>();
        PARAMS.forEach(testParameter ->
                collectInterfaces(testParameter.signalBaseClass)
                        .forEach(signalClass -> {
                            // no channel provided via argument
                            findToAdaptable(signalClass).ifPresent(m -> data.add(new Object[]{testParameter, m,
                                    DittoHeaders.empty(), null, testParameter.expectedDefaultChannel,
                                    signatureAsString(m)}));
                            // channel (live) provided via header, no channel provided via argument
                            findToAdaptable(signalClass).ifPresent(m -> data.add(new Object[]{testParameter, m,
                                    LIVE_DITTO_HEADERS, null, testParameter.supportsLive() ? LIVE : null,
                                    signatureAsString(m)}));

                            // channel provided via argument
                            Arrays.asList(TopicPath.Channel.values()).forEach(ch -> {
                                // null means unsupported -> exception is thrown
                                final TopicPath.Channel expectedChannel
                                        = Arrays.asList(testParameter.supportedChannels).contains(ch) ? ch : null;
                                findToAdaptableWithChannel(signalClass).ifPresent(
                                        m -> data.add(new Object[]{testParameter, m,
                                                DittoHeaders.empty(), ch, expectedChannel, signatureAsString(m)}));
                            });
                        })
        );
        return data;
    }

    @Before
    public void setUp() {
        final ThingCommandAdapterProvider thingCommandAdapterProvider = mock(ThingCommandAdapterProvider.class);
        final PolicyCommandAdapterProvider policyCommandAdapterProvider = mock(PolicyCommandAdapterProvider.class);
        final AcknowledgementAdapterProvider acknowledgementAdapterProvider =
                mock(AcknowledgementAdapterProvider.class);
        final ConnectivityCommandAdapterProvider connectivityCommandAdapterProvider =
                mock(ConnectivityCommandAdapterProvider.class);

        when(thingCommandAdapterProvider.getQueryCommandAdapter())
                .thenReturn(thingQueryCommandAdapter);
        when(thingCommandAdapterProvider.getQueryCommandResponseAdapter())
                .thenReturn(thingQueryCommandResponseAdapter);
        when(thingCommandAdapterProvider.getModifyCommandAdapter())
                .thenReturn(thingModifyCommandAdapter);
        when(thingCommandAdapterProvider.getModifyCommandResponseAdapter())
                .thenReturn(thingModifyCommandResponseAdapter);
        when(thingCommandAdapterProvider.getMessageCommandAdapter())
                .thenReturn(messageCommandAdapter);
        when(thingCommandAdapterProvider.getMessageCommandResponseAdapter())
                .thenReturn(messageCommandResponseAdapter);
        when(thingCommandAdapterProvider.getErrorResponseAdapter())
                .thenReturn(thingErrorResponseAdapter);
        when(thingCommandAdapterProvider.getEventAdapter())
                .thenReturn(thingEventAdapter);
        when(thingCommandAdapterProvider.getMergeCommandAdapter())
                .thenReturn(thingMergeCommandAdapter);
        when(thingCommandAdapterProvider.getMergeCommandResponseAdapter())
                .thenReturn(thingMergeCommandResponseAdapter);
        when(thingCommandAdapterProvider.getMergedEventAdapter())
                .thenReturn(thingMergedEventAdapter);

        when(policyCommandAdapterProvider.getQueryCommandAdapter())
                .thenReturn(policyQueryCommandAdapter);
        when(policyCommandAdapterProvider.getQueryCommandResponseAdapter())
                .thenReturn(policyQueryCommandResponseAdapter);
        when(policyCommandAdapterProvider.getModifyCommandAdapter())
                .thenReturn(policyModifyCommandAdapter);
        when(policyCommandAdapterProvider.getModifyCommandResponseAdapter())
                .thenReturn(policyModifyCommandResponseAdapter);
        when(policyCommandAdapterProvider.getErrorResponseAdapter())
                .thenReturn(policyErrorResponseAdapter);

        final AdapterResolver adapterResolver = new DefaultAdapterResolver(thingCommandAdapterProvider,
                policyCommandAdapterProvider, connectivityCommandAdapterProvider, acknowledgementAdapterProvider);
        underTest = DittoProtocolAdapter.newInstance(HeaderTranslator.empty(), thingCommandAdapterProvider,
                policyCommandAdapterProvider, connectivityCommandAdapterProvider, acknowledgementAdapterProvider,
                adapterResolver);

        reset(thingQueryCommandAdapter);
        reset(thingQueryCommandResponseAdapter);
        reset(thingModifyCommandAdapter);
        reset(thingModifyCommandResponseAdapter);
        reset(thingMergeCommandAdapter);
        reset(thingMergeCommandResponseAdapter);
        reset(thingEventAdapter);
        reset(thingMergedEventAdapter);
        reset(thingErrorResponseAdapter);
        reset(policyQueryCommandAdapter);
        reset(policyQueryCommandResponseAdapter);
        reset(policyModifyCommandAdapter);
        reset(policyModifyCommandResponseAdapter);
        reset(policyErrorResponseAdapter);
        reset(messageCommandAdapter);
        reset(messageCommandResponseAdapter);
    }

    @Test
    public void testCorrectAdapterCalledOrExceptionThrownWithDefaultChannel() {
        Throwable caughtException = null;

        // channel may be determined from ditto headers e.g. when converting from signal to adaptable
        when(parameter.signal.getDittoHeaders()).thenReturn(dittoHeaders);

        try {
            final Object[] args;

            if (givenChannel != null) {
                // call the variant with fixed channel (channel is not determined from header and no default is used)
                args = new Object[]{parameter.signal, givenChannel};
            } else {
                // call the variant without channel (channel is determined from header or default is used)
                args = new Object[]{parameter.signal};
            }

            LOGGER.info("calling: {}({}){} for signal {} and headers {}", toAdaptable.getName(),
                    signatureAsString(toAdaptable),
                    givenChannel == null ? "" : " with channel '" + givenChannel + "'",
                    parameter.signal,
                    dittoHeaders.entrySet());

            toAdaptable.invoke(underTest, args);
        } catch (InvocationTargetException e) {
            caughtException = e.getCause();
        } catch (Exception e) {
            caughtException = e;
        }

        if (caughtException != null) {
            LOGGER.info("exception: {} ({})", caughtException.getClass().getName(), caughtException.getMessage());
        }

        if (expectedChannel != null) {
            assertThat(caughtException).isNull();
            // expect the correct adapter is called with correct arguments
            verify(parameter.expectedAdapter).toAdaptable(parameter.signal, expectedChannel);
        } else {
            // toAdaptable was called with invalid argument/channel -> expect exception
            assertThat(caughtException).isInstanceOf(UnknownChannelException.class);
        }
    }

    /**
     * @return all interfaces implemented by the given array of classes
     */
    private static Set<Class<?>> collectInterfaces(Class<?>... c) {
        final Set<Class<?>> ifcs = new HashSet<>();
        for (final Class<?> cls : c) {
            if (Signal.class.isAssignableFrom(cls)) {
                ifcs.add(cls);
            }
            final Class<?>[] interfaces = cls.getInterfaces();
            ifcs.addAll(collectInterfaces(interfaces));
        }
        return ifcs;
    }

    /**
     * Finds the method to test via reflection to avoid hard-coding every combination in the test.
     *
     * @param clazz type of the first parameter
     * @return the {@code toAdaptable} method to test or empty optional
     */
    private static Optional<Method> findToAdaptableWithChannel(final Class<?> clazz) {
        return Arrays.stream(ProtocolAdapter.class.getMethods())
                .filter(m -> "toAdaptable".equals(m.getName()))
                .filter(m -> 2 == m.getParameterCount())
                .filter(m -> clazz.equals(m.getParameterTypes()[0]))
                .filter(m -> TopicPath.Channel.class.equals(m.getParameterTypes()[1]))
                .findFirst();
    }

    /**
     * Finds the method to test via reflection to avoid hard-coding every combination in the test.
     *
     * @param clazz type of the first parameter
     * @return the {@code toAdaptable} method to test or empty optional
     */
    private static Optional<Method> findToAdaptable(final Class<?> clazz) {
        return Arrays.stream(ProtocolAdapter.class.getMethods())
                .filter(m -> "toAdaptable".equals(m.getName()))
                .filter(m -> clazz.equals(m.getParameterTypes()[0]))
                .filter(m -> 1 == m.getParameterCount())
                .findFirst();
    }


    private static String signatureAsString(final Method method) {
        return "toAdaptable(" +
                Arrays.stream(method.getParameterTypes())
                        .map(Class::getSimpleName).collect(Collectors.joining(",")) +
                ")";
    }

    /**
     * Container class for test parameters.
     */
    private static class TestParameter<T extends Signal<T>> {

        final Class<T> signalBaseClass;
        final T signal;
        final Adapter<T> expectedAdapter;
        final TopicPath.Channel expectedDefaultChannel;
        final TopicPath.Channel[] supportedChannels;

        private TestParameter(final Class<T> signalBaseClass, final Adapter<T> expectedAdapter,
                final TopicPath.Channel expectedDefaultChannel, TopicPath.Channel... supportedChannels) {
            this.signalBaseClass = signalBaseClass;
            this.signal = mockSignal(signalBaseClass);
            this.expectedAdapter = expectedAdapter;
            this.expectedDefaultChannel = expectedDefaultChannel;
            this.supportedChannels = supportedChannels;
        }

        private static <T extends Signal<T>> T mockSignal(Class<T> signalClass) {
            final T mockedSignal = mock(signalClass, withSettings().name(signalClass.getSimpleName()));
            when(mockedSignal.getDittoHeaders()).thenReturn(DittoHeaders.empty());
            return mockedSignal;
        }

        private boolean supportsLive() {
            return Arrays.asList(supportedChannels).contains(LIVE);
        }

        @Override
        public String toString() {
            return signalBaseClass.getSimpleName();
        }

    }

}
