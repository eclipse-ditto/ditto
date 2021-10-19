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
package org.eclipse.ditto.policies.model.signals.commands;

import org.eclipse.ditto.json.JsonPointer;

/**
 * Defines test cases for policy resources.
 */
enum PolicyResourceTestCase {
    POLICY("", PolicyResource.POLICY),
    POLICY_IMPORTS("imports", PolicyResource.POLICY_IMPORTS),
    POLICY_IMPORT("imports/importedPolicyId", PolicyResource.POLICY_IMPORT),
    POLICY_ENTRIES("entries", PolicyResource.POLICY_ENTRIES),
    POLICY_ENTRY("entries/label", PolicyResource.POLICY_ENTRY),
    POLICY_ENTRY_RESOURCES("entries/label/resources", PolicyResource.POLICY_ENTRY_RESOURCES),
    POLICY_ENTRY_RESOURCE("entries/label/resources/thing", PolicyResource.POLICY_ENTRY_RESOURCE),
    POLICY_ENTRY_RESOURCE2("entries/label/resources/thing/attributes", PolicyResource.POLICY_ENTRY_RESOURCE),
    POLICY_ENTRY_SUBJECTS("entries/label/subjects", PolicyResource.POLICY_ENTRY_SUBJECTS),
    POLICY_ENTRY_SUBJECT("entries/label/subjects/id", PolicyResource.POLICY_ENTRY_SUBJECT),
    POLICY_ENTRY_SUBJECT2("entries/label/subjects/id/xy", PolicyResource.POLICY_ENTRY_SUBJECT);

    private final JsonPointer path;
    private final PolicyResource expectedResource;

    PolicyResourceTestCase(final String path, final PolicyResource expectedResource) {
        this.path = JsonPointer.of(path);
        this.expectedResource = expectedResource;
    }

    JsonPointer getPath() {
        return path;
    }

    PolicyResource getExpectedResource() {
        return expectedResource;
    }

    @Override
    public String toString() {
        return "given path: " + path + " -> expected resource: " + expectedResource;
    }
}
