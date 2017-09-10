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
package org.eclipse.ditto.model.thingsearchparser.options.rql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.model.thingsearch.Option;
import org.eclipse.ditto.model.thingsearch.SearchModelFactory;
import org.eclipse.ditto.model.thingsearch.SortOption;
import org.eclipse.ditto.model.thingsearch.SortOptionEntry;
import org.eclipse.ditto.model.thingsearchparser.BaseRules;
import org.parboiled.Rule;

/**
 * RQL rule definitions for the options.
 */
public class OptionRules extends BaseRules {
    // -------------------------------------------------------------------------
    // Rule Definitions
    // -------------------------------------------------------------------------

    /**
     * Defines the root rule that parses the query until EOI.
     *
     * @return the root rule
     */
    @Override
    public Rule root() {
        return Sequence(push(new ArrayList<Option>()), option(), ZeroOrMore(Sequence(",", option())), EOI);
    }

    /**
     * Defines a rule to parse single options.
     *
     * @return rule that matches queries.
     */
    Rule option() {
        return Sequence(FirstOf(sortOption(), limitOption()), addAsOption());
    }

    /**
     * Removes the value at the top of the value stack and returns it as a {@link Option}.
     *
     * @return the value as option
     * @throws IllegalArgumentException if the stack does not contain enough elements to perform this operation
     */
    Option popAsOption() {
        return (Option) pop();
    }

    /**
     * Retrieves the top {@link Option} of the value stack and adds it to the list of options.
     *
     * @return true in order for parboiled to continue processing (otherwise we cannot use this method inline).
     * @throws IllegalArgumentException if the stack does not contain enough elements to perform this operation
     */
    @SuppressWarnings("unchecked")
    boolean addAsOption() {
        final Option option = popAsOption();
        final List<Option> listOfOptions = (List<Option>) peek();
        listOfOptions.add(option);
        return true;
    }

    // -------------------------------------------------------------------------
    // Sort Option
    // -------------------------------------------------------------------------

    Rule sortOption() {
        return Sequence(String("sort"), "(", push(
                SearchModelFactory.newSortOption(Collections.emptyList())),
                sortProperty(),
                ZeroOrMore(Sequence(",", sortProperty())), ")");
    }

    Rule sortProperty() {
        return FirstOf( //
                Sequence("+", propertyLiteral(), addSortProperty(SortOptionEntry.SortOrder.ASC)),
                Sequence("-", propertyLiteral(), addSortProperty(SortOptionEntry.SortOrder.DESC)));
    }

    /**
     * Pops the property from the stack and adds it to the sort option.
     *
     * @param order the sorting order.
     * @return true in order for parboiled to continue processing (otherwise we cannot use this method inline).
     * @throws IllegalArgumentException if the stack does not contain enough elements to perform this operation
     */
    boolean addSortProperty(final SortOptionEntry.SortOrder order) {
        final String property = popAsString(0);
        final SortOption sortOption = (SortOption) pop();
        final SortOption newOption = sortOption.add(property, order);
        push(newOption);
        return true;
    }

    // -------------------------------------------------------------------------
    // Limit Option
    // -------------------------------------------------------------------------

    Rule limitOption() {
        return Sequence(String("limit"), "(", integerLiteral(), ",", integerLiteral(), ")",
                push(SearchModelFactory.newLimitOption(((Long) pop(1)).intValue(), ((Long) pop()).intValue())));
    }

    // -------------------------------------------------------------------------
    // Helper methods to work with the value stack
    // -------------------------------------------------------------------------

    /**
     * Removes the value the given number of elements below the top of the value stack and returns it as a string.
     *
     * @param down the number of elements to skip before removing the value (0 being equivalent to pop())
     * @return the value as string
     * @throws IllegalArgumentException if the stack does not contain enough elements to perform this operation
     */
    String popAsString(final int down) {
        return (String) pop(down);
    }
}
