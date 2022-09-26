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
package org.eclipse.ditto.protocol.adapter;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.base.model.signals.ErrorRegistry;
import org.eclipse.ditto.base.model.signals.GlobalErrorRegistry;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.messages.model.MessageHeaderDefinition;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.acknowledgements.DefaultAcknowledgementsAdapterProvider;
import org.eclipse.ditto.protocol.adapter.connectivity.ConnectivityCommandAdapterProvider;
import org.eclipse.ditto.protocol.adapter.connectivity.DefaultConnectivityCommandAdapterProvider;
import org.eclipse.ditto.protocol.adapter.policies.DefaultPolicyCommandAdapterProvider;
import org.eclipse.ditto.protocol.adapter.provider.AcknowledgementAdapterProvider;
import org.eclipse.ditto.protocol.adapter.provider.PolicyCommandAdapterProvider;
import org.eclipse.ditto.protocol.adapter.provider.ThingCommandAdapterProvider;
import org.eclipse.ditto.protocol.adapter.things.DefaultThingCommandAdapterProvider;

/**
 * Adapter for the Ditto protocol.
 */
public final class DittoProtocolAdapter implements ProtocolAdapter {

    private final HeaderTranslator headerTranslator;
    private final ThingCommandAdapterProvider thingsAdapters;
    private final PolicyCommandAdapterProvider policiesAdapters;
    private final ConnectivityCommandAdapterProvider connectivityAdapters;
    private final AcknowledgementAdapterProvider acknowledgementAdapters;

    private final StreamingSubscriptionCommandAdapter streamingSubscriptionCommandAdapter;
    private final StreamingSubscriptionEventAdapter streamingSubscriptionEventAdapter;
    private final AdapterResolver adapterResolver;

    private DittoProtocolAdapter(final ErrorRegistry<DittoRuntimeException> errorRegistry,
            final HeaderTranslator headerTranslator) {
        this.headerTranslator = checkNotNull(headerTranslator, "headerTranslator");
        this.thingsAdapters = new DefaultThingCommandAdapterProvider(errorRegistry, headerTranslator);
        this.policiesAdapters = new DefaultPolicyCommandAdapterProvider(errorRegistry, headerTranslator);
        this.connectivityAdapters = new DefaultConnectivityCommandAdapterProvider(headerTranslator);
        this.acknowledgementAdapters = new DefaultAcknowledgementsAdapterProvider(errorRegistry, headerTranslator);
        streamingSubscriptionCommandAdapter = StreamingSubscriptionCommandAdapter.of(headerTranslator);
        streamingSubscriptionEventAdapter = StreamingSubscriptionEventAdapter.of(headerTranslator, errorRegistry);
        this.adapterResolver = new DefaultAdapterResolver(thingsAdapters, policiesAdapters, connectivityAdapters,
                acknowledgementAdapters, streamingSubscriptionCommandAdapter, streamingSubscriptionEventAdapter);
    }

    private DittoProtocolAdapter(final HeaderTranslator headerTranslator,
            final ThingCommandAdapterProvider thingsAdapters,
            final PolicyCommandAdapterProvider policiesAdapters,
            final ConnectivityCommandAdapterProvider connectivityAdapters,
            final AcknowledgementAdapterProvider acknowledgementAdapters,
            final StreamingSubscriptionCommandAdapter streamingSubscriptionCommandAdapter,
            final StreamingSubscriptionEventAdapter streamingSubscriptionEventAdapter,
            final AdapterResolver adapterResolver) {
        this.headerTranslator = checkNotNull(headerTranslator, "headerTranslator");
        this.thingsAdapters = checkNotNull(thingsAdapters, "thingsAdapters");
        this.policiesAdapters = checkNotNull(policiesAdapters, "policiesAdapters");
        this.connectivityAdapters = checkNotNull(connectivityAdapters, "connectivityAdapters");
        this.acknowledgementAdapters = checkNotNull(acknowledgementAdapters, "acknowledgementAdapters");
        this.streamingSubscriptionCommandAdapter = checkNotNull(streamingSubscriptionCommandAdapter,
                "streamingSubscriptionCommandAdapter");
        this.streamingSubscriptionEventAdapter = checkNotNull(streamingSubscriptionEventAdapter,
                "streamingSubscriptionEventAdapter");
        this.adapterResolver = checkNotNull(adapterResolver, "adapterResolver");
    }

    /**
     * Creates a new {@code DittoProtocolAdapter} instance with the given header translator.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return new DittoProtocolAdapter
     */
    public static DittoProtocolAdapter of(final HeaderTranslator headerTranslator) {
        return new DittoProtocolAdapter(GlobalErrorRegistry.getInstance(), headerTranslator);
    }

    /**
     * Creates a new {@code DittoProtocolAdapter} instance.
     *
     * @return the instance.
     */
    public static DittoProtocolAdapter newInstance() {
        return new DittoProtocolAdapter(GlobalErrorRegistry.getInstance(), getHeaderTranslator());
    }

    /**
     * Creates a default header translator for this protocol adapter.
     *
     * @return the default header translator.
     */
    public static HeaderTranslator getHeaderTranslator() {
        return HeaderTranslator.of(DittoHeaderDefinition.values(), MessageHeaderDefinition.values());
    }

    /**
     * Factory method used in tests.
     *
     * @param headerTranslator translator between external and Ditto headers
     * @param thingCommandAdapterProvider command adapters for thing commands
     * @param policyCommandAdapterProvider command adapters for policy commands
     * @param connectivityAdapters adapters for connectivity commands.
     * @param acknowledgementAdapters adapters for acknowledgements.
     * @param streamingSubscriptionCommandAdapter adapters for streaming subscription commands.
     * @param streamingSubscriptionEventAdapter adapters for streaming subscription events.
     * @param adapterResolver resolves the correct adapter from a command
     * @return new instance of {@link DittoProtocolAdapter}
     */
    static DittoProtocolAdapter newInstance(final HeaderTranslator headerTranslator,
            final ThingCommandAdapterProvider thingCommandAdapterProvider,
            final PolicyCommandAdapterProvider policyCommandAdapterProvider,
            final ConnectivityCommandAdapterProvider connectivityAdapters,
            final AcknowledgementAdapterProvider acknowledgementAdapters,
            final StreamingSubscriptionCommandAdapter streamingSubscriptionCommandAdapter,
            final StreamingSubscriptionEventAdapter streamingSubscriptionEventAdapter,
            final AdapterResolver adapterResolver) {
        return new DittoProtocolAdapter(headerTranslator, thingCommandAdapterProvider, policyCommandAdapterProvider,
                connectivityAdapters, acknowledgementAdapters,
                streamingSubscriptionCommandAdapter, streamingSubscriptionEventAdapter, adapterResolver
        );
    }

    @Override
    public Signal<?> fromAdaptable(final Adaptable adaptable) {
        final Adapter<? extends Signal<?>> adapter = adapterResolver.getAdapter(adaptable);
        return DittoJsonException.wrapJsonRuntimeException(() ->
                adapter.fromAdaptable(adapter.validateAndPreprocess(adaptable)));
    }

    @Override
    public Adaptable toAdaptable(final Signal<?> signal) {
        final TopicPath.Channel channel = ProtocolAdapter.determineChannel(signal);
        return adapterResolver.getAdapter(signal, channel).toAdaptable(signal, channel);
    }

    @Override
    public Adaptable toAdaptable(final Signal<?> signal, final TopicPath.Channel channel) {
        return adapterResolver.getAdapter(signal, channel).toAdaptable(signal, channel);
    }

    @Override
    public HeaderTranslator headerTranslator() {
        return headerTranslator;
    }

    @Override
    public TopicPath toTopicPath(final Signal<?> signal) {
        final TopicPath.Channel channel = ProtocolAdapter.determineChannel(signal);
        return adapterResolver.getAdapter(signal, channel).toTopicPath(signal, channel);
    }

}
