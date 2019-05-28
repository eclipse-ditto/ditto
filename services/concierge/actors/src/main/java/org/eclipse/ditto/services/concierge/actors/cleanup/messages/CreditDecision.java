/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.concierge.actors.cleanup.messages;

import javax.annotation.concurrent.Immutable;

/**
 * Decision about how many credits to give to clenaup actions.
 */
@Immutable
public final class CreditDecision {

    private final int credit;
    private final String explanation;

    private CreditDecision(final int credit, final String explanation) {
        this.credit = credit;
        this.explanation = explanation;
    }

    /**
     * Create a decision to not give out any credit.
     *
     * @param explanation why no credit is given.
     * @return the decision.
     */
    public CreditDecision no(final String explanation) {
        return new CreditDecision(0, explanation);
    }

    /**
     * Create a decision to give out some credit.
     *
     * @param credit how much credit to give out.
     * @param explanation why the credit is given out.
     * @return the decision.
     */
    public CreditDecision yes(final int credit, final String explanation) {
        return new CreditDecision(credit, explanation);
    }

    /**
     * Retrieve the credit allotted by this decision.
     *
     * @return the credit.
     */
    public int getCredit() {
        return credit;
    }

    /**
     * Retrieve the explanation of this decision.
     *
     * @return the explanation.
     */
    public String getExplanation() {
        return explanation;
    }

    // TODO: Equals, hashcode, test
}
