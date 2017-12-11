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
package org.eclipse.ditto.model.thingsearchparser;

import org.parboiled.BaseParser;
import org.parboiled.Rule;
import org.parboiled.annotations.MemoMismatches;
import org.parboiled.annotations.SuppressNode;
import org.parboiled.annotations.SuppressSubnodes;

/**
 * Defines the basic rule set ready to use by concrete parser implementations.
 * <pre>
 * Literal                    = FloatLiteral | IntegerLiteral | StringLiteral | "true" | "false" | "null"
 * FloatLiteral               = [ '+' | '-' ], "0.", Digit, { Digit } |
 *                              [ '+' | '-' ], DigitWithoutZero, { Digit }, '.', Digit, { Digit }
 * IntegerLiteral             = '0' | [ '+' | '-' ], DigitWithoutZero, { Digit }
 * DigitWithoutZero           = '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9'
 * Digit                      = '0' | DigitWithoutZero
 * StringLiteral              = '"', ? printable characters ?, '"'
 * PropertyLiteral            = ? printable characters ?
 * </pre>
 */
public abstract class BaseRules extends BaseParser<Object> {

    /**
     * Defines the root rule that parses the query until EOI.
     *
     * @return the root rule
     */
    public abstract Rule root();

    // -------------------------------------------------------------------------
    // Spacing
    // -------------------------------------------------------------------------

    @SuppressNode
    protected Rule spacing() {
        return ZeroOrMore(OneOrMore(AnyOf(" \t\r\n\f").label("Whitespace")));
    }

    // -------------------------------------------------------------------------
    // Letters
    // -------------------------------------------------------------------------

    @MemoMismatches
    protected Rule letterOrDigit() {
        return FirstOf(CharRange('a', 'z'), CharRange('A', 'Z'), CharRange('0', '9'), '_', '$');
        // switch to this "extended" character space version if we need to process unicode encoded characters.
        // Be aware that this is ~10% slower.
        // return Sequence('\\', UnicodeEscape());
    }

    // -------------------------------------------------------------------------
    // Literals
    // -------------------------------------------------------------------------

    protected Rule literal() {
        return Sequence(FirstOf(floatLiteral(), integerLiteral(), stringLiteral(),
                Sequence("true", TestNot(letterOrDigit()), push(Boolean.TRUE)),
                Sequence("false", TestNot(letterOrDigit()), push(Boolean.FALSE)),
                Sequence("null", TestNot(letterOrDigit()), push(null))), spacing());
    }

    @SuppressSubnodes
    protected Rule integerLiteral() {
        return Sequence(FirstOf('0', Sequence(Optional(AnyOf("+-")), digitWithoutZero(), ZeroOrMore(digit()))),
                new IntegerLiteralAction(), push(Long.parseLong(match())));
    }

    protected Rule floatLiteral() {
        return Sequence(decimalFloat(), new FloatingPointLiteral(), push(Double.parseDouble(match())));
    }

    @SuppressSubnodes
    protected Rule decimalFloat() {
        return FirstOf(Sequence(Optional(AnyOf("+-")), "0.", OneOrMore(digit())),
                Sequence(Optional(AnyOf("+-")), digitWithoutZero(), ZeroOrMore(digit()), '.', OneOrMore(digit())));
    }

    protected Rule digit() {
        return CharRange('0', '9');
    }

    protected Rule digitWithoutZero() {
        return CharRange('1', '9');
    }

    protected Rule stringLiteral() {
        return Sequence('"', ZeroOrMore(FirstOf(escape(), Sequence(TestNot(AnyOf("\"")), ANY))).suppressSubnodes(),
                push(match()), '"');
    }

    protected Rule propertyLiteral() {
        return Sequence(ZeroOrMore(FirstOf(escape(), Sequence(TestNot(AnyOf(",)\"")), ANY))).suppressSubnodes(),
                push(match()));
    }

    protected Rule escape() {
        return Sequence('\\', AnyOf("\""));
    }

}
