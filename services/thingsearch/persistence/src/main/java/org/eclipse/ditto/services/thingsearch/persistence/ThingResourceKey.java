/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.thingsearch.persistence;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.ResourceKey;

/**
 * An enumeration of relevant {@link ResourceKey}s for Things.
 */
public enum ThingResourceKey implements ResourceKey {

    ROOT("/"),

    THING_ID("/thingId"),

    POLICY_ID("/policyId"),

    ATTRIBUTES("/attributes"),

    FEATURES( "/features");

    private final ResourceKey resourceKey;

    ThingResourceKey(final CharSequence path) {
        resourceKey = ResourceKey.newInstance(PoliciesResourceType.THING, path);
    }

    @Override
    public String getResourceType() {
        return resourceKey.getResourceType();
    }

    @Override
    public JsonPointer getResourcePath() {
        return resourceKey.getResourcePath();
    }

    @Override
    public int length() {
        return resourceKey.length();
    }

    @Override
    public char charAt(final int index) {
        return resourceKey.charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return resourceKey.subSequence(start, end);
    }

    @Override
    public String toString() {
        return resourceKey.toString();
    }

}
