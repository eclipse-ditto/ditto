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
package org.eclipse.ditto.connectivity.service.placeholders;

import org.eclipse.ditto.edge.service.placeholders.EntityIdPlaceholder;
import org.eclipse.ditto.edge.service.placeholders.FeaturePlaceholder;
import org.eclipse.ditto.edge.service.placeholders.PolicyPlaceholder;
import org.eclipse.ditto.edge.service.placeholders.RequestPlaceholder;
import org.eclipse.ditto.edge.service.placeholders.ThingPlaceholder;
import org.eclipse.ditto.placeholders.TimePlaceholder;
import org.eclipse.ditto.protocol.placeholders.ResourcePlaceholder;
import org.eclipse.ditto.protocol.placeholders.TopicPathPlaceholder;

public final class ConnectivityPlaceholders {

    private ConnectivityPlaceholders() {
        // This is a class providing static factory methods.
    }

    /**
     * @return the singleton instance of {@link ThingPlaceholder}
     */
    public static ThingPlaceholder newThingPlaceholder() {
        return ThingPlaceholder.getInstance();
    }

    /**
     * @return the singleton instance of  {@link PolicyPlaceholder}
     */
    public static PolicyPlaceholder newPolicyPlaceholder() {
        return PolicyPlaceholder.getInstance();
    }

    /**
     * @return the singleton instance of {@link FeaturePlaceholder}
     */
    public static FeaturePlaceholder newFeaturePlaceholder() {
        return FeaturePlaceholder.getInstance();
    }

    /**
     * @return the singleton instance of {@link EntityIdPlaceholder}
     */
    public static EntityIdPlaceholder newEntityPlaceholder() {
        return EntityIdPlaceholder.getInstance();
    }

    /**
     * @return the singleton instance of the placeholder with prefix {@code request}.
     */
    public static RequestPlaceholder newRequestPlaceholder() {
        return RequestPlaceholder.getInstance();
    }

    /**
     * @return the singleton instance of {@link TopicPathPlaceholder}
     */
    public static TopicPathPlaceholder newTopicPathPlaceholder() {
        return TopicPathPlaceholder.getInstance();
    }

    /**
     * @return the singleton instance of {@link ResourcePlaceholder}
     */
    public static ResourcePlaceholder newResourcePlaceholder() {
        return ResourcePlaceholder.getInstance();
    }

    /**
     * @return the singleton instance of {@link TimePlaceholder}
     */
    public static TimePlaceholder newTimePlaceholder() {
        return TimePlaceholder.getInstance();
    }

    /**
     * @return the singleton instance of {@link ConnectionIdPlaceholder}.
     */
    public static ConnectionIdPlaceholder newConnectionIdPlaceholder() {
        return ImmutableConnectionIdPlaceholder.INSTANCE;
    }

    /**
     * @return the singleton instance of {@link SourceAddressPlaceholder}
     */
    public static SourceAddressPlaceholder newSourceAddressPlaceholder() {
        return ImmutableSourceAddressPlaceholder.INSTANCE;
    }

}
