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
package org.eclipse.ditto.services.models.connectivity.placeholders;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.placeholders.Placeholder;

public final class ConnectivityPlaceholders {

    private ConnectivityPlaceholders() {
        // This is a class providing static factory methods.
    }

    /**
     * @return new instance of the {@link ThingPlaceholder}
     */
    public static ThingPlaceholder newThingPlaceholder() {
        return ImmutableThingPlaceholder.INSTANCE;
    }

    /**
     * @return new instance of the {@link PolicyPlaceholder}
     */
    public static PolicyPlaceholder newPolicyPlaceholder() {
        return ImmutablePolicyPlaceholder.INSTANCE;
    }

    /**
     * @return new instance of the {@link FeaturePlaceholder}
     * @since 1.5.0
     */
    public static FeaturePlaceholder newFeaturePlaceholder() {
        return ImmutableFeaturePlaceholder.INSTANCE;
    }

    /**
     * @return new instance of the {@link EntityPlaceholder}
     */
    public static EntityPlaceholder newEntityPlaceholder() {
        return ImmutableEntityPlaceholder.INSTANCE;
    }

    /**
     * @return the unique instance of the placeholder with prefix {@code request}.
     */
    public static Placeholder<AuthorizationContext> newRequestPlaceholder() {
        return ImmutableRequestPlaceholder.INSTANCE;
    }

    /**
     * @return new instance of the {@link TopicPathPlaceholder}
     */
    public static TopicPathPlaceholder newTopicPathPlaceholder() {
        return ImmutableTopicPathPlaceholder.INSTANCE;
    }

    /**
     * @return the singleton instance of {@link ConnectionIdPlaceholder}.
     * @since 1.4.0
     */
    public static ConnectionIdPlaceholder newConnectionIdPlaceholder() {
        return ImmutableConnectionIdPlaceholder.INSTANCE;
    }

    /**
     * @return new instance of the {@link SourceAddressPlaceholder}
     */
    public static SourceAddressPlaceholder newSourceAddressPlaceholder() {
        return ImmutableSourceAddressPlaceholder.INSTANCE;
    }

}
