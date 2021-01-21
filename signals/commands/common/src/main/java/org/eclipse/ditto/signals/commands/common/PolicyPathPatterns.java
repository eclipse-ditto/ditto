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

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Holds all path patterns used in a policy.
 *
 * TODO adapt @since annotaion @since 1.6.0
 */
public enum PolicyPathPatterns implements PathPatterns {

    POLICY_PATH("policy", Pattern.compile("^/$")),
    POLICY_ENTRIES_PATH("policyEntries", Pattern.compile("^/entries$")),
    POLICY_ENTRY_PATH("policyEntry", Pattern.compile("^/entries/[^/]*$")),
    POLICY_ENTRY_RESOURCES_PATH("resources", Pattern.compile("^/entries/[^/]*/resources$")),
    POLICY_ENTRY_RESOURCE_PATH("resource", Pattern.compile("^/entries/[^/]*/resources/.*$")),
    POLICY_ENTRY_SUBJECTS_PATH("subjects", Pattern.compile("^/entries/[^/]*/subjects$")),
    POLICY_ENTRY_SUBJECT_PATH("subject", Pattern.compile("^/entries/[^/]*/subjects/.*$"));

    private final String path;
    private final Pattern pathPattern;

    PolicyPathPatterns(final String thePath, final Pattern thePathPattern) {
        path = thePath;
        pathPattern = thePathPattern;
    }

    /**
     * @return all path patterns that are supported in policy commands, responses or events.
     */
    static List<PolicyPathPatterns> get() {
        return Collections.unmodifiableList(Stream.of(values()).collect(Collectors.toList()));
    }

    public String getPath() {
        return path;
    }

    public Pattern getPathPattern() {
        return pathPattern;
    }
}
