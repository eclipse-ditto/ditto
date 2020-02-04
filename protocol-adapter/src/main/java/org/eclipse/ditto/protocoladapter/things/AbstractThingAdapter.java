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
package org.eclipse.ditto.protocoladapter.things;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.protocoladapter.AbstractAdapter;
import org.eclipse.ditto.protocoladapter.DefaultPayloadPathMatcher;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.adaptables.MappingStrategies;
import org.eclipse.ditto.signals.base.WithId;

/**
 * TODO
 */
abstract class AbstractThingAdapter<T extends Jsonifiable.WithPredicate<JsonObject, JsonField> & WithId> extends
        AbstractAdapter<T> {

    private static final Map<String, Pattern> THING_PATH_PATTERNS = new HashMap<>();

    static {
        THING_PATH_PATTERNS.put("thing", Pattern.compile("^/$"));
        THING_PATH_PATTERNS.put("acl", Pattern.compile("^/acl$"));
        THING_PATH_PATTERNS.put("aclEntry", Pattern.compile("^/acl/[^/]*$"));
        THING_PATH_PATTERNS.put("policyId", Pattern.compile("^/policyId$"));
        THING_PATH_PATTERNS.put("policy", Pattern.compile("^/_policy"));
        THING_PATH_PATTERNS.put("policyEntries", Pattern.compile("^/_policy/entries$"));
        THING_PATH_PATTERNS.put("policyEntry", Pattern.compile("^/_policy/entries/.*$"));
        THING_PATH_PATTERNS.put("policyEntrySubjects", Pattern.compile("^/_policy/entries/[^/]*/subjects$"));
        THING_PATH_PATTERNS.put("policyEntrySubject", Pattern.compile("^/_policy/entries/[^/]*/subjects/.*$"));
        THING_PATH_PATTERNS.put("policyEntryResources", Pattern.compile("^/_policy/entries/[^/]*/resources$"));
        THING_PATH_PATTERNS.put("policyEntryResource", Pattern.compile("^/_policy/entries/[^/]*/resources/.*$"));
        THING_PATH_PATTERNS.put("attributes", Pattern.compile("^/attributes$"));
        THING_PATH_PATTERNS.put("attribute", Pattern.compile("^/attributes/.*$"));
        THING_PATH_PATTERNS.put("features", Pattern.compile("^/features$"));
        THING_PATH_PATTERNS.put("feature", Pattern.compile("^/features/[^/]*$"));
        THING_PATH_PATTERNS.put("definition", Pattern.compile("^/definition$"));
        THING_PATH_PATTERNS.put("featureDefinition", Pattern.compile("^/features/[^/]*/definition$"));
        THING_PATH_PATTERNS.put("featureProperties", Pattern.compile("^/features/[^/]*/properties$"));
        THING_PATH_PATTERNS.put("featureProperty", Pattern.compile("^/features/[^/]*/properties/.*$"));
    }

    protected AbstractThingAdapter(final MappingStrategies<T> mappingStrategies,
            final HeaderTranslator headerTranslator) {
        super(mappingStrategies, headerTranslator, DefaultPayloadPathMatcher.from(THING_PATH_PATTERNS));
    }

}
