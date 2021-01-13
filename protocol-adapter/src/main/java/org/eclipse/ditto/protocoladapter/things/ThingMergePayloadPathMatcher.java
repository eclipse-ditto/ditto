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
package org.eclipse.ditto.protocoladapter.things;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.protocoladapter.DefaultPayloadPathMatcher;
import org.eclipse.ditto.protocoladapter.PayloadPathMatcher;

/**
 * Implementation of {@link org.eclipse.ditto.protocoladapter.PayloadPathMatcher} for merge commands, responses and
 * events.
 */
public class ThingMergePayloadPathMatcher implements PayloadPathMatcher {

    private static final PayloadPathMatcher DELEGATE = DefaultPayloadPathMatcher.from(ThingPayloadPathPatterns.get(
            ThingPayloadPathPatterns.THING_PATH,
            ThingPayloadPathPatterns.POLICY_ID_PATH,
            ThingPayloadPathPatterns.DEFINITION_PATH,
            ThingPayloadPathPatterns.ATTRIBUTES_PATH,
            ThingPayloadPathPatterns.ATTRIBUTE_PATH,
            ThingPayloadPathPatterns.FEATURES_PATH,
            ThingPayloadPathPatterns.FEATURE_PATH,
            ThingPayloadPathPatterns.FEATURE_DEFINITION_PATH,
            ThingPayloadPathPatterns.FEATURE_PROPERTIES_PATH,
            ThingPayloadPathPatterns.FEATURE_PROPERTY_PATH,
            ThingPayloadPathPatterns.FEATURE_DESIRED_PROPERTIES_PATH,
            ThingPayloadPathPatterns.FEATURE_DESIRED_PROPERTY_PATH
    ));

    private static final ThingMergePayloadPathMatcher INSTANCE = new ThingMergePayloadPathMatcher();

    /**
     * @return the {@link org.eclipse.ditto.protocoladapter.things.ThingMergePayloadPathMatcher} instance
     */
    static ThingMergePayloadPathMatcher getInstance() {
        return INSTANCE;
    }

    @Override
    public String match(final JsonPointer path) {
        return DELEGATE.match(path);
    }
}
