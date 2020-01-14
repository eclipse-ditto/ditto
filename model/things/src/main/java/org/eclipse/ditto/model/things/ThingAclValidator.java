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
package org.eclipse.ditto.model.things;

import static java.util.Objects.requireNonNull;

import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.common.Validator;


/**
 * Instances of this class can be used to validate a specified {@link AccessControlList} with regard to the rules of a
 * valid {@link Thing}.
 *
 * @deprecated AccessControlLists belong to deprecated API version 1. Use API version 2 with policies instead.
 */
@Deprecated
@Immutable
public final class ThingAclValidator implements Validator {

    private final AclValidator validator;

    private ThingAclValidator(final AccessControlList accessControlList) {
        validator = AclValidator.newInstance(accessControlList, Thing.MIN_REQUIRED_PERMISSIONS);
    }

    /**
     * Creates a new {@code ThingAclValidator} instance.
     *
     * @param accessControlList the ACL to be validated.
     * @return a new {@code ThingAclValidator} object.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ThingAclValidator newInstance(final AccessControlList accessControlList) {
        requireNonNull(accessControlList, "The Access Control List to validate must not be null!");

        return new ThingAclValidator(accessControlList);
    }

    /**
     * Validates the {@link AccessControlList} which was provided to the static factory method of this class. Validation
     * ensures that the ACL contains at least one Authorization Subject which has the permissions
     * {@link org.eclipse.ditto.model.things.Permission#READ}, {@link org.eclipse.ditto.model.things.Permission#WRITE} and
     * {@link org.eclipse.ditto.model.things.Permission#ADMINISTRATE}.
     *
     * @return false if the validated ACL does not contain at least one Authorization Subject with the minimum required
     * permissions or else true.
     */
    @Override
    public boolean isValid() {
        return validator.isValid();
    }

    @Override
    public Optional<String> getReason() {
        return validator.getReason();
    }

}
