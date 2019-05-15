/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.gateway.endpoints.directives;

import akka.http.javadsl.server.PathMatcher0;
import akka.http.javadsl.server.PathMatchers;

/**
 * Contains custom Akka HTTP {@link PathMatchers}.
 */
public final class CustomPathMatchers {

    /**
     * PatchMather which matches double slashes in the path as well as single slashes.
     *
     * @return a PathMatcher0 merging double slashes in an unmatched path.
     */
    public static PathMatcher0 mergeDoubleSlashes() {
        return PathMatchers.slash().concat(PathMatchers.slash()) // match 2 slashes, e.g.   //foo
                .orElse(PathMatchers.slash()); // as fallback match one slash, e.g.         /foo
    }

    private CustomPathMatchers() {
        // no initialization
    }
}
