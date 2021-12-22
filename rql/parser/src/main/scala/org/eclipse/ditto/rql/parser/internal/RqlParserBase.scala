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
package org.eclipse.ditto.rql.parser.internal

import akka.parboiled2._
import akka.shapeless._

import scala.{:: => _}

/**
 * RQL Parser base containing commonly used types for both Predicate and Options parsing in EBNF:
 * <pre>
 * Literal                    = FloatLiteral | IntegerLiteral | StringLiteral | "true" | "false" | "null"
 * DoubleLiteral              = [ '+' | '-' ], "0.", Digit, { Digit } | [ '+' | '-' ], DigitWithoutZero, { Digit }, '.', Digit, { Digit }
 * LongLiteral                = '0' | [ '+' | '-' ], DigitWithoutZero, { Digit }
 * DigitWithoutZero           = '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9'
 * Digit                      = '0' | DigitWithoutZero
 * StringLiteral              = '"', ? printable characters ?, '"'
 * PropertyLiteral            = ? printable characters ?
 * </pre>
 */
class RqlParserBase(val input: ParserInput) extends Parser {
  protected val sb = new java.lang.StringBuilder

  private def Digit = rule {
    anyOf("0123456789")
  }

  private def Digit19 = rule {
    anyOf("123456789")
  }

  private def HexDigit = rule {
    anyOf("0123456789ABCDEFabcdef")
  }

  private def WhiteSpaceChar = rule {
    anyOf(" \n\r\t\f")
  }

  private def CommaClosingParenthesisQuote = rule {
    anyOf(",)\"\\")
  }

  private def QuoteBackslash = rule {
    anyOf("\"\\")
  }

  private def SingleQuoteBackslash = rule {
    anyOf("'\\")
  }

  private def QuoteBackslashSlash = rule {
    QuoteBackslash | "/"
  }

  private def SingleQuoteBackslashSlash = rule {
    SingleQuoteBackslash | "/"
  }

  protected def Literal: Rule1[java.lang.Object] = rule {
    (DoubleLiteral | LongLiteral | StringLiteral | StringSingleQuoteLiteral | PlaceholderLiteral |
      "true" ~ WhiteSpace ~ push(java.lang.Boolean.TRUE) |
      "false" ~ WhiteSpace ~ push(java.lang.Boolean.FALSE) |
      "null" ~ WhiteSpace ~ push(None)
      ) ~ WhiteSpace
  }

  protected def DoubleLiteral: Rule1[java.lang.Double] = rule {
    capture(Integer ~ Frac) ~> (numb => java.lang.Double.valueOf(numb)) ~ WhiteSpace
  }

  protected def LongLiteral: Rule1[java.lang.Long] = rule {
    !"-0" ~ capture(Integer) ~> (numb => java.lang.Long.valueOf(numb)) ~ WhiteSpace
  }

  protected def Integer: Rule[HNil, HNil] = rule {
    optional(anyOf("+-")) ~ (Digit19 ~ Digits | Digit)
  }

  protected def Digits: Rule[HNil, HNil] = rule {
    oneOrMore(Digit)
  }

  protected def Frac: Rule[HNil, HNil] = rule {
    "." ~ Digits
  }

  protected def StringLiteral: Rule1[java.lang.String] = rule {
    '"' ~ clearSB() ~ CharactersInQuotes ~ ws('"') ~ push(sb.toString)
  }

  protected def StringSingleQuoteLiteral: Rule1[java.lang.String] = rule {
    '\'' ~ clearSB() ~ CharactersInSingleQuotes ~ ws('\'') ~ push(sb.toString)
  }

  protected def PlaceholderLiteral: Rule1[org.eclipse.ditto.rql.model.ParsedPlaceholder] = rule {
    capture(PlaceholderPrefix ~ PlaceholderName) ~> (p => org.eclipse.ditto.rql.model.ParsedPlaceholder.of(p)) ~ WhiteSpace
  }

  protected def PlaceholderPrefix: Rule[HNil, HNil] = rule {
    "time:"
//    "time:"|"header:"
  }

  protected def PlaceholderName: Rule[HNil, HNil] = rule {
    Characters
  }

  protected def PropertyLiteral: Rule1[java.lang.String] = rule {
    clearSB() ~ Characters ~ push(sb.toString)
  }

  protected def CharactersInQuotes: Rule[HNil, HNil] = rule {
    zeroOrMore(NormalCharInQuotes | '\\' ~ EscapedChar)
  }

  protected def CharactersInSingleQuotes: Rule[HNil, HNil] = rule {
    zeroOrMore(NormalCharInSingleQuotes | '\\' ~ SingleQuoteEscapedChar)
  }

  protected def Characters: Rule[HNil, HNil] = rule {
    zeroOrMore(NormalChar | '\\' ~ EscapedChar)
  }

  protected def NormalCharInQuotes: Rule[HNil, HNil] = rule {
    !QuoteBackslash ~ ANY ~ appendSB()
  }

  protected def NormalCharInSingleQuotes: Rule[HNil, HNil] = rule {
    !SingleQuoteBackslash ~ ANY ~ appendSB()
  }

  protected def NormalChar: Rule[HNil, HNil] = rule {
    !CommaClosingParenthesisQuote ~ ANY ~ appendSB()
  }

  protected def EscapedChar: Rule[HNil, HNil] = rule(
    QuoteBackslashSlash ~ appendSB()
      | 'b' ~ appendSB('\b')
      | 'f' ~ appendSB('\f')
      | 'n' ~ appendSB('\n')
      | 'r' ~ appendSB('\r')
      | 't' ~ appendSB('\t')
      | Unicode ~> { code: Int => sb.append(code.asInstanceOf[Char]); () }
  )

  protected def SingleQuoteEscapedChar: Rule[HNil, HNil] = rule(
    SingleQuoteBackslashSlash ~ appendSB()
      | 'b' ~ appendSB('\b')
      | 'f' ~ appendSB('\f')
      | 'n' ~ appendSB('\n')
      | 'r' ~ appendSB('\r')
      | 't' ~ appendSB('\t')
      | Unicode ~> { code: Int => sb.append(code.asInstanceOf[Char]); () }
  )

  protected def Unicode: Rule[HNil, Int :: HNil] = rule {
    'u' ~ capture(HexDigit ~ HexDigit ~ HexDigit ~ HexDigit) ~>
      ((code: String) => java.lang.Integer.parseInt(code, 16))
  }

  protected def WhiteSpace: Rule[HNil, HNil] = rule {
    zeroOrMore(WhiteSpaceChar)
  }

  protected def ws(c: Char): Rule[HNil, HNil] = rule {
    c ~ WhiteSpace
  }

  protected def clearSB(): Rule0 = rule {
    run(sb.setLength(0))
  }

  protected def appendSB(): Rule0 = rule {
    run(sb.append(lastChar))
  }

  protected def appendSB(c: Char): Rule0 = rule {
    run(sb.append(c))
  }

}
