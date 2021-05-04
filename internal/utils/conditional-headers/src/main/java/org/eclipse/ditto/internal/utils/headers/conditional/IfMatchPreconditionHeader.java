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
package org.eclipse.ditto.internal.utils.headers.conditional;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatchers;

@Immutable
public final class IfMatchPreconditionHeader implements PreconditionHeader<EntityTag> {

    private static final String IF_MATCH_HEADER_KEY = DittoHeaderDefinition.IF_MATCH.getKey();

    private final EntityTagMatchers entityTagsToMatch;

    private IfMatchPreconditionHeader(final EntityTagMatchers entityTagsToMatch) {
        checkNotNull(entityTagsToMatch, "entityTagsToMatch");
        this.entityTagsToMatch = entityTagsToMatch;
    }

    @Override
    public String getKey() {
        return IF_MATCH_HEADER_KEY;
    }

    @Override
    public String getValue() {
        return entityTagsToMatch.toString();
    }

    /**
     * Indicates whether this {@link IfMatchPreconditionHeader} meets the condition for the given {@code entityTag}
     * according to <a href="https://tools.ietf.org/html/rfc7232#section-3.1">RFC7232 - Section 3.1</a>.
     *
     * @param entityTag The entity tag for which this {@link IfMatchPreconditionHeader} should meet the condition.
     * @return True if this {@link IfMatchPreconditionHeader} meets the condition. False if not.
     */
    @Override
    public boolean meetsConditionFor(@Nullable final EntityTag entityTag) {
        if (entityTag == null) {
            return false;
        }

        return entityTagsToMatch.stream().anyMatch(entityTagToMatch -> entityTagToMatch.strongMatch(entityTag));
    }

    /**
     * Extracts an {@link IfMatchPreconditionHeader} from the given {@code dittoHeaders} if present.
     *
     * @param dittoHeaders The ditto headers that could contain an {@link IfMatchPreconditionHeader}
     * @return Optional of {@link IfMatchPreconditionHeader}. Empty if the given {@code dittoHeaders} don't contain an
     * {@link IfMatchPreconditionHeader}.
     */
    public static Optional<IfMatchPreconditionHeader> fromDittoHeaders(final DittoHeaders dittoHeaders) {
        return dittoHeaders.getIfMatch().map(IfMatchPreconditionHeader::new);
    }
}
