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
package org.eclipse.ditto.signals.commands.common;


import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Holds all path patterns used in a thing.
 *
 * TODO adapt @since annotaion @since 1.6.0
 */
public enum ThingPathPatterns implements PathPatterns {

    THING_PATH("thing", Pattern.compile("^/$")),
    ACL_PATH("acl", Pattern.compile("^/acl$")),
    ACL_ENTRY_PATH("aclEntry", Pattern.compile("^/acl/[^/]*$")),
    POLICY_ID_PATH("policyId", Pattern.compile("^/policyId$")),
    POLICY_PATH("policy", Pattern.compile("^/_policy")),
    POLICY_ENTRIES_PATH("policyEntries", Pattern.compile("^/_policy/entries$")),
    POLICY_ENTRY_PATH("policyEntry", Pattern.compile("^/_policy/entries/.*$")),
    POLICY_ENTRY_SUBJECTS_PATH("policyEntrySubjects", Pattern.compile("^/_policy/entries/[^/]*/subjects$")),
    POLICY_ENTRY_SUBJECT_PATH("policyEntrySubject", Pattern.compile("^/_policy/entries/[^/]*/subjects/.*$")),
    POLICY_ENTRY_RESOURCES_PATH("policyEntryResources", Pattern.compile("^/_policy/entries/[^/]*/resources$")),
    POLICY_ENTRY_RESOURCE_PATH("policyEntryResource", Pattern.compile("^/_policy/entries/[^/]*/resources/.*$")),
    ATTRIBUTES_PATH("attributes", Pattern.compile("^/attributes$")),
    ATTRIBUTE_PATH("attribute", Pattern.compile("^/attributes/.*$")),
    FEATURES_PATH("features", Pattern.compile("^/features$")),
    FEATURE_PATH("feature", Pattern.compile("^/features/[^/]*$")),
    DEFINITION_PATH("definition", Pattern.compile("^/definition$")),
    FEATURE_DEFINITION_PATH("featureDefinition", Pattern.compile("^/features/[^/]*/definition$")),
    FEATURE_PROPERTIES_PATH("featureProperties", Pattern.compile("^/features/[^/]*/properties$")),
    FEATURE_PROPERTY_PATH("featureProperty", Pattern.compile("^/features/[^/]*/properties/.*$")),
    FEATURE_DESIRED_PROPERTIES_PATH("featureDesiredProperties", Pattern.compile("^/features/[^/]*/desiredProperties$")),
    FEATURE_DESIRED_PROPERTY_PATH("featureDesiredProperty", Pattern.compile("^/features/[^/]*/desiredProperties/.*$"));

    private final String path;
    private final Pattern pathPattern;

    ThingPathPatterns(final String thePath, final Pattern thePathPattern) {
        path = thePath;
        pathPattern = thePathPattern;
    }

    /**
     * @return all path patterns that are supported in thing commands, responses or events.
     */
    public static List<ThingPathPatterns> get() {
        return Collections.unmodifiableList(Stream.of(values()).collect(Collectors.toList()));
    }

    /**
     * @return path patterns that are supported in thing commands, responses or events filtered by the given keys.
     */
    static List<ThingPathPatterns> get(final ThingPathPatterns... supportedPaths) {
        final List<ThingPathPatterns> supported = Arrays.asList(supportedPaths);
        return Collections.unmodifiableList(Stream.of(values())
                .filter(supported::contains)
                .collect(Collectors.toList()));
    }

    public String getPath() {
        return path;
    }

    public Pattern getPathPattern() {
        return pathPattern;
    }
}
