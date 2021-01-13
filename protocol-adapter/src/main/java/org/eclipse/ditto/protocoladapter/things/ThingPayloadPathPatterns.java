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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Defines supported payload paths in thing commands, responses and events.
 */
final class ThingPayloadPathPatterns {

    private static final Map<String, Pattern> PATTERNS = new HashMap<>();

    static final String THING_PATH = "thing";
    static final String ACL_ENTRY_PATH = "aclEntry";
    static final String ACL_PATH = "acl";
    static final String POLICY_ID_PATH = "policyId";
    static final String POLICY_PATH = "policy";
    static final String POLICY_ENTRIES_PATH = "policyEntries";
    static final String POLICY_ENTRY_PATH = "policyEntry";
    static final String POLICY_ENTRY_SUBJECTS_PATH = "policyEntrySubjects";
    static final String POLICY_ENTRY_SUBJECT_PATH = "policyEntrySubject";
    static final String POLICY_ENTRY_RESOURCES_PATH = "policyEntryResources";
    static final String POLICY_ENTRY_RESOURCE_PATH = "policyEntryResource";
    static final String ATTRIBUTES_PATH = "attributes";
    static final String ATTRIBUTE_PATH = "attribute";
    static final String FEATURES_PATH = "features";
    static final String FEATURE_PATH = "feature";
    static final String DEFINITION_PATH = "definition";
    static final String FEATURE_DEFINITION_PATH = "featureDefinition";
    static final String FEATURE_PROPERTIES_PATH = "featureProperties";
    static final String FEATURE_PROPERTY_PATH = "featureProperty";
    static final String FEATURE_DESIRED_PROPERTIES_PATH = "featureDesiredProperties";
    static final String FEATURE_DESIRED_PROPERTY_PATH = "featureDesiredProperty";

    static {
        PATTERNS.put(THING_PATH, Pattern.compile("^/$"));
        PATTERNS.put(ACL_PATH, Pattern.compile("^/acl$"));
        PATTERNS.put(ACL_ENTRY_PATH, Pattern.compile("^/acl/[^/]*$"));
        PATTERNS.put(POLICY_ID_PATH, Pattern.compile("^/policyId$"));
        PATTERNS.put(POLICY_PATH, Pattern.compile("^/_policy"));
        PATTERNS.put(POLICY_ENTRIES_PATH, Pattern.compile("^/_policy/entries$"));
        PATTERNS.put(POLICY_ENTRY_PATH, Pattern.compile("^/_policy/entries/.*$"));
        PATTERNS.put(POLICY_ENTRY_SUBJECTS_PATH, Pattern.compile("^/_policy/entries/[^/]*/subjects$"));
        PATTERNS.put(POLICY_ENTRY_SUBJECT_PATH, Pattern.compile("^/_policy/entries/[^/]*/subjects/.*$"));
        PATTERNS.put(POLICY_ENTRY_RESOURCES_PATH, Pattern.compile("^/_policy/entries/[^/]*/resources$"));
        PATTERNS.put(POLICY_ENTRY_RESOURCE_PATH, Pattern.compile("^/_policy/entries/[^/]*/resources/.*$"));
        PATTERNS.put(ATTRIBUTES_PATH, Pattern.compile("^/attributes$"));
        PATTERNS.put(ATTRIBUTE_PATH, Pattern.compile("^/attributes/.*$"));
        PATTERNS.put(FEATURES_PATH, Pattern.compile("^/features$"));
        PATTERNS.put(FEATURE_PATH, Pattern.compile("^/features/[^/]*$"));
        PATTERNS.put(DEFINITION_PATH, Pattern.compile("^/definition$"));
        PATTERNS.put(FEATURE_DEFINITION_PATH, Pattern.compile("^/features/[^/]*/definition$"));
        PATTERNS.put(FEATURE_PROPERTIES_PATH, Pattern.compile("^/features/[^/]*/properties$"));
        PATTERNS.put(FEATURE_PROPERTY_PATH, Pattern.compile("^/features/[^/]*/properties/.*$"));
        PATTERNS.put(FEATURE_DESIRED_PROPERTIES_PATH, Pattern.compile("^/features/[^/]*/desiredProperties$"));
        PATTERNS.put(FEATURE_DESIRED_PROPERTY_PATH, Pattern.compile("^/features/[^/]*/desiredProperties/.*$"));
    }

    /**
     * @return all path patterns that are supported in thing commands, responses or events.
     */
    static Map<String, Pattern> get() {
        return Collections.unmodifiableMap(PATTERNS);
    }

    /**
     * @return path patterns that are supported in thing commands, responses or events filtered by the given keys.
     */
    static Map<String, Pattern> get(final String... supportedPaths) {
        final List<String> supported = Arrays.asList(supportedPaths);
        return Collections.unmodifiableMap(PATTERNS.entrySet()
                .stream()
                .filter(e -> supported.contains(e.getKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue)));
    }
}
