/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.enforcement;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.headers.DittoHeaders;

/**
 * Interface for evaluating if the entity creation should be restricted.
 */
@FunctionalInterface
public interface CreationRestrictionEnforcer {

    /**
     * The context for evaluating if an entity may be created or not.
     */
     class Context {
        private final String resourceType;
        private final String namespace;
        private final DittoHeaders headers;

         /**
          * Create a new context for the evaluation.
          *
          * @param resourceType The resource type which should be created.
          * @param namespace The namespace the entity would reside in.
          * @param headers The Ditto headers for the request.
          */
        public Context(final String resourceType, final String namespace, final DittoHeaders headers) {
            this.resourceType = resourceType;
            this.namespace = namespace;
            this.headers = headers;
        }

        public DittoHeaders getHeaders() {
            return headers;
        }

        public AuthorizationContext getAuthorizationContext() {
            return headers.getAuthorizationContext();
        }

        public String getNamespace() {
            return namespace;
        }

        public String getResourceType() {
            return resourceType;
        }
    }

    /**
     * Evaluate if the entity can be created.
     *
     * @param context the context for checking.
     * @return the outcome of the check. {@code true} would allow the creation, {@code false} would reject it.
     */
    boolean canCreate(Context context);

    /**
     * A default implementation which allows everything.
     */
    CreationRestrictionEnforcer NULL = ctx -> true;

}
