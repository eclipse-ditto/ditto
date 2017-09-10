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
package org.eclipse.ditto.model.things;

import static java.util.Objects.requireNonNull;

import java.text.MessageFormat;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.common.Validator;


/**
 * Instances of this class can be used to validate a specified {@link AccessControlList} with minimum required
 * permissions.
 */
@Immutable
public final class AclValidator implements Validator {

    private static final String NO_AUTH_SUBJECT_PATTERN =
            "It must contain at least one Authorization Subject with the following permission(s): <{0}>!";
    private static final String AUTH_SUBJECT_NOT_PERMITTED_PATTERN =
            "The Authorization Subject <{0}> must have at least the permission(s): <{1}>!";

    private final AccessControlList acl;
    private final Permissions permissions;
    private boolean validationResult;
    private String reason;

    private AclValidator(final AccessControlList accessControlList, final Permissions minRequiredPermissions) {
        acl = accessControlList;
        permissions = minRequiredPermissions;
        validationResult = true;
        reason = null;
    }

    /**
     * Creates a new {@code AclValidator} instance.
     *
     * @param accessControlList the ACL to be validated.
     * @param minRequiredPermissions the minimum required permissions.
     * @return a new {@code AclValidator} object.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static AclValidator newInstance(final AccessControlList accessControlList,
            final Permissions minRequiredPermissions) {
        requireNonNull(accessControlList, "The Access Control List to validate must not be null!");

        return new AclValidator(accessControlList, minRequiredPermissions);
    }

    /**
     * Validates the {@link AccessControlList} which was provided to the static factory method of this class. Validation
     * ensures that the ACL contains at least one Authorization Subject which has the permissions {@link
     * Permission#READ}
     * , {@link Permission#WRITE} and {@link Permission#ADMINISTRATE}.
     *
     * @return false if the validated ACL does not contain at least one Authorization Subject with the minimum required
     * permissions or else true.
     */
    @Override
    public boolean isValid() {
        checkIfCompletelyEmpty();
        final boolean doProceed = checkIfSingleEntryIsValid();
        if (doProceed) {
            checkIfAnyAuthorizationSubjectsWithRequiredPermissionsDoExist();
        }
        return validationResult;
    }

    @Override
    public Optional<String> getReason() {
        return Optional.ofNullable(reason);
    }

    private void checkIfCompletelyEmpty() {
        if (acl.isEmpty()) {
            reason = MessageFormat.format(NO_AUTH_SUBJECT_PATTERN, permissions);
        }
    }

    private boolean checkIfSingleEntryIsValid() {
        final boolean result;

        if (!validationResult) {
            result = false;
        } else if (1 == acl.getSize()) {
            final Set<AclEntry> aclEntries = acl.getEntriesSet();
            final Iterator<AclEntry> aclEntryIterator = aclEntries.iterator();
            final AclEntry aclEntry = aclEntryIterator.next();
            if (!aclEntry.containsAll(permissions)) {
                reason = MessageFormat
                        .format(AUTH_SUBJECT_NOT_PERMITTED_PATTERN, aclEntry.getAuthorizationSubject(), permissions);
                validationResult = false;
            }

            // In this case the single entry has the minimum required permissions.
            result = false;
        } else {
            result = true;
        }

        return result;
    }

    private void checkIfAnyAuthorizationSubjectsWithRequiredPermissionsDoExist() {
        final Set<AuthorizationSubject> authorizationSubjects = acl.getAuthorizedSubjectsFor(permissions);
        if (authorizationSubjects.isEmpty()) {
            reason = MessageFormat.format(NO_AUTH_SUBJECT_PATTERN, permissions);
            validationResult = false;
        }
    }

}
