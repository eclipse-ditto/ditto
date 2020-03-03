/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocoladapter.policies;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.eclipse.ditto.protocoladapter.AbstractAdapter;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DefaultPayloadPathMatcher;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.adaptables.MappingStrategies;
import org.eclipse.ditto.protocoladapter.signals.SignalMapper;
import org.eclipse.ditto.signals.base.Signal;

/**
 * Base class for {@link org.eclipse.ditto.protocoladapter.Adapter}s that handle policy commands.
 *
 * @param <T> the type of the policy command
 */
abstract class AbstractPolicyAdapter<T extends Signal<?>> extends AbstractAdapter<T> implements PolicyAdapter<T> {

    private static final Map<String, Pattern> POLICY_PATH_PATTERNS = new HashMap<>();

    static {
        POLICY_PATH_PATTERNS.put("policy", Pattern.compile("^/$"));
        POLICY_PATH_PATTERNS.put("policyEntry", Pattern.compile("^/entries/[^/]*$"));
        POLICY_PATH_PATTERNS.put("policyEntries", Pattern.compile("^/entries$"));
        POLICY_PATH_PATTERNS.put("resource", Pattern.compile("^/entries/[^/]*/resources/.*$"));
        POLICY_PATH_PATTERNS.put("resources", Pattern.compile("^/entries/[^/]*/resources$"));
        POLICY_PATH_PATTERNS.put("subject", Pattern.compile("^/entries/[^/]*/subjects/.*$"));
        POLICY_PATH_PATTERNS.put("subjects", Pattern.compile("^/entries/[^/]*/subjects$"));
    }

    private final SignalMapper<T> signalMapper;

    /**
     * @param mappingStrategies the {@link MappingStrategies} used to convert {@link Adaptable}s to
     * {@link org.eclipse.ditto.signals.base.Signal}s
     * @param signalMapper the {@link SignalMapper} used to convert from a
     * {@link org.eclipse.ditto.signals.base.Signal} to an {@link Adaptable}
     * @param headerTranslator the header translator
     */
    protected AbstractPolicyAdapter(final MappingStrategies<T> mappingStrategies,
            final SignalMapper<T> signalMapper, final HeaderTranslator headerTranslator) {
        super(mappingStrategies, headerTranslator, DefaultPayloadPathMatcher.from(POLICY_PATH_PATTERNS));
        this.signalMapper = signalMapper;
    }

    @Override
    protected Adaptable mapSignalToAdaptable(final T signal, final TopicPath.Channel channel) {
        return signalMapper.mapSignalToAdaptable(signal, channel);
    }
}
