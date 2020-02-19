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
package org.eclipse.ditto.protocoladapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.protocoladapter.TopicPath.Channel.LIVE;
import static org.eclipse.ditto.protocoladapter.TopicPath.Channel.NONE;
import static org.eclipse.ditto.protocoladapter.TopicPath.Channel.TWIN;
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

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.protocoladapter.provider.PolicyCommandAdapterProvider;
import org.eclipse.ditto.protocoladapter.provider.ThingCommandAdapterProvider;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponse;
import org.eclipse.ditto.signals.commands.policies.modify.PolicyModifyCommand;
import org.eclipse.ditto.signals.commands.policies.modify.PolicyModifyCommandResponse;
import org.eclipse.ditto.signals.commands.policies.query.PolicyQueryCommand;
import org.eclipse.ditto.signals.commands.policies.query.PolicyQueryCommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommandResponse;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Unit test for {@link DittoProtocolAdapter}.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
@RunWith(Parameterized.class)
public final class DittoProtocolAdapterParameterizedTest {

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger(DittoProtocolAdapterParameterizedTest.class);
    private static final DittoHeaders LIVE_DITTO_HEADERS = DittoHeaders.newBuilder().channel(LIVE.getName()).build();

    // mock all adapaters and give them a name
    private static Adapter<ThingQueryCommand<?>> thingQueryCommandAdapter =
            mock(Adapter.class, "ThingQueryCommandAdapter");
    private static Adapter<ThingQueryCommandResponse<?>> thingQueryCommandResponseAdapter =
            mock(Adapter.class, "ThingQueryCommandResponseAdapter");
    private static Adapter<ThingModifyCommand<?>> thingModifyCommandAdapter =
            mock(Adapter.class, "ThingModifyCommandAdapter");
    private static Adapter<ThingModifyCommandResponse<?>> thingModifyCommandResponseAdapter =
            mock(Adapter.class, "ThingModifyCommandResponseAdapter");
    private static Adapter<ThingEvent<?>> thingEventAdapter = mock(Adapter.class, "ThingEventAdapter");
    private static Adapter<MessageCommand<?, ?>> messageCommandAdapter = mock(Adapter.class, "MessageCommandAdapter");
    private static Adapter<MessageCommandResponse<?, ?>> messageCommandResponseAdapter =
            mock(Adapter.class, "MessageCommandResponseAdapter");
    private static Adapter<ThingErrorResponse> thingErrorResponseAdapter =
            mock(Adapter.class, "ThingErrorResponseAdapter");
    private static Adapter<PolicyQueryCommand<?>> policyQueryCommandAdapter =
            mock(Adapter.class, "PolicyQueryCommandAdapter");
    private static Adapter<PolicyQueryCommandResponse<?>> policyQueryCommandResponseAdapter =
            mock(Adapter.class, "PolicyQueryCommandResponseAdapter");
    private static Adapter<PolicyModifyCommand<?>> policyModifyCommandAdapter =
            mock(Adapter.class, "PolicyModifyCommandAdapter");
    private static Adapter<PolicyModifyCommandResponse<?>> policyModifyCommandResponseAdapter =
            mock(Adapter.class, "PolicyModifyCommandResponseAdapter");

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
        PARAMS.add(new TestParameter(MessageCommand.class, messageCommandAdapter, LIVE, LIVE));
        PARAMS.add(new TestParameter(MessageCommandResponse.class, messageCommandResponseAdapter, LIVE, LIVE));
        PARAMS.add(new TestParameter(ThingEvent.class, thingEventAdapter, TWIN, TWIN, LIVE));
        PARAMS.add(new TestParameter(PolicyQueryCommand.class, policyQueryCommandAdapter, NONE, NONE));
        PARAMS.add(new TestParameter(PolicyQueryCommandResponse.class, policyQueryCommandResponseAdapter, NONE, NONE));
        PARAMS.add(new TestParameter(PolicyModifyCommand.class, policyModifyCommandAdapter, NONE, NONE));
        PARAMS.add(
                new TestParameter(PolicyModifyCommandResponse.class, policyModifyCommandResponseAdapter, NONE, NONE));
    }

    private ProtocolAdapter underTest;

    /**
     * Determine candidates for calls to toAdaptable because we want to test all variants of toAdaptable
     * ({@code toAdaptable(Signal)}, {@code toAdaptable(Command)}, {@code toAdaptable(ThingCommand)}, ...). These
     * candidates are later used to find the methods to call in the tests.
     */
    private static final Set<Class<?>> candidates =
            collectInterfaces(PARAMS.stream().map(ti -> ti.cls).toArray(Class<?>[]::new));

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
        PARAMS.forEach(testParameter -> {
            candidates.forEach(candidateClass -> {
                if (candidateClass.isAssignableFrom(testParameter.cls)) {

                    // no channel provided via argument
                    findToAdaptable(candidateClass).ifPresent(m -> data.add(new Object[]{testParameter, m,
                            DittoHeaders.empty(), null, testParameter.expectedDefaultChannel, signatureAsString(m)}));
                    // channel (live) provided via header, no channel provided via argument
                    findToAdaptable(candidateClass).ifPresent(m -> data.add(new Object[]{testParameter, m,
                            LIVE_DITTO_HEADERS, null, testParameter.supportsLive() ? LIVE : null,
                            signatureAsString(m)}));

                    // channel provided via argument
                    Arrays.asList(TopicPath.Channel.values()).forEach(ch -> {
                        // null means unsupported -> exception is thrown
                        final TopicPath.Channel expectedChannel
                                = Arrays.asList(testParameter.supportedChannels).contains(ch) ? ch : null;
                        findToAdaptableWithChannel(candidateClass).ifPresent(
                                m -> data.add(new Object[]{testParameter, m,
                                        DittoHeaders.empty(), ch, expectedChannel, signatureAsString(m)}));
                    });

                }
            });
        });
        return data;
    }

    @Before
    public void setUp() {

        final ThingCommandAdapterProvider thingCommandAdapterProvider = mock(ThingCommandAdapterProvider.class);
        final PolicyCommandAdapterProvider policyCommandAdapterProvider = mock(PolicyCommandAdapterProvider.class);

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

        when(policyCommandAdapterProvider.getQueryCommandAdapter())
                .thenReturn(policyQueryCommandAdapter);
        when(policyCommandAdapterProvider.getQueryCommandResponseAdapter())
                .thenReturn(policyQueryCommandResponseAdapter);
        when(policyCommandAdapterProvider.getModifyCommandAdapter())
                .thenReturn(policyModifyCommandAdapter);
        when(policyCommandAdapterProvider.getModifyCommandResponseAdapter())
                .thenReturn(policyModifyCommandResponseAdapter);

        final AdapterResolver adapterResolver = mock(AdapterResolver.class);

        underTest = DittoProtocolAdapter.newInstance(HeaderTranslator.empty(), thingCommandAdapterProvider,
                policyCommandAdapterProvider, adapterResolver);

        reset(thingQueryCommandAdapter);
        reset(thingQueryCommandResponseAdapter);
        reset(thingModifyCommandAdapter);
        reset(thingModifyCommandResponseAdapter);
        reset(thingEventAdapter);
        reset(policyQueryCommandAdapter);
        reset(policyQueryCommandResponseAdapter);
        reset(policyModifyCommandAdapter);
        reset(policyModifyCommandResponseAdapter);
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
            if (cls.getPackage().getName().startsWith("org.eclipse.ditto.signals")) {
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

        final Class<T> cls;
        final T signal;
        final Adapter<T> expectedAdapter;
        final TopicPath.Channel expectedDefaultChannel;
        final TopicPath.Channel[] supportedChannels;

        private TestParameter(final Class<T> cls, final Adapter<T> expectedAdapter,
                final TopicPath.Channel expectedDefaultChannel, TopicPath.Channel... supportedChannels) {
            this.cls = cls;
            final Set<Class<?>> interfaces = collectInterfaces(cls);
            interfaces.remove(cls);
            this.signal = mockSignal(cls, interfaces);
            this.expectedAdapter = expectedAdapter;
            this.expectedDefaultChannel = expectedDefaultChannel;
            this.supportedChannels = supportedChannels;
        }

        private static <T extends Signal<T>> T mockSignal(Class<T> signalClass, Set<Class<?>> interfaces) {
            final Set<Class<?>> ifcs = new HashSet<>(interfaces);
            ifcs.remove(signalClass);
            final T mockedSignal = mock(signalClass, withSettings()
                    .extraInterfaces(ifcs.toArray(new Class[0]))
                    .name(signalClass.getSimpleName()));
            when(mockedSignal.getDittoHeaders()).thenReturn(DittoHeaders.empty());
            return mockedSignal;
        }

        private boolean supportsLive() {
            return Arrays.asList(supportedChannels).contains(LIVE);
        }

        @Override
        public String toString() {
            return cls.getSimpleName();
        }
    }
}