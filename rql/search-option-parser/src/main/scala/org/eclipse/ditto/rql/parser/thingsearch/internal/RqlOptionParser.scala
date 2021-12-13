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
package org.eclipse.ditto.rql.parser.thingsearch.internal

import akka.parboiled2._
import org.eclipse.ditto.rql.model.ParserException
import org.eclipse.ditto.rql.parser.internal.RqlParserBase
import org.eclipse.ditto.rql.parser.thingsearch.OptionParser
import org.eclipse.ditto.thingsearch.model
import org.eclipse.ditto.thingsearch.model.{LimitOption, SearchModelFactory, SortOption, SortOptionEntry}

import java.util
import scala.collection.JavaConverters
import scala.util.{Failure, Success}

/**
  * RQL Parser. Parses options in the RQL "standard" according to https://github.com/persvr/rql with the following EBNF:
  * <pre>
  * Options                    = Option, { ',', Option }
  * Option                     = Sort | Limit
  * Sort                       = "sort", '(', SortProperty, { ',', SortProperty }, ')'
  * SortProperty               = SortOrder, PropertyLiteral
  * SortOrder                  = '+' | '-'
  * Limit                      = "limit", '(', IntegerLiteral, ',', IntegerLiteral, ')'
  * </pre>
  */
private class RqlOptionParser(override val input: ParserInput) extends RqlParserBase(input) {

  /**
    * @return the root for parsing RQL Options.
    */
  def OptionsRoot: Rule1[Seq[model.Option]] = rule {
    WhiteSpace ~ Options ~ EOI
  }

  /**
    * Options                    = Option, { ',', Option }
    */
  private def Options: Rule1[Seq[model.Option]] = rule {
    oneOrMore(Option).separatedBy(',')
  }

  /**
    * Option                     = Sort | Limit | Cursor | Size
    */
  private def Option: Rule1[model.Option] = rule {
    Sort | Limit | Cursor | Size
  }

  /**
    * Sort                       = "sort", '(', SortProperty, { ',', SortProperty }, ')'
    */
  private def Sort: Rule1[SortOption] = rule {
    "sort" ~ '(' ~ oneOrMore(SortProperty).separatedBy(',') ~ ')' ~>
      ((options: Seq[SortOptionEntry]) => SearchModelFactory.newSortOption(JavaConverters.seqAsJavaList(options)))
  }

  /**
    * SortProperty               = SortOrder, PropertyLiteral
    */
  private def SortProperty: Rule1[SortOptionEntry] = rule {
    SortOrder ~ PropertyLiteral ~>
      ((order: SortOptionEntry.SortOrder, property: CharSequence) =>
        SearchModelFactory.newSortOptionEntry(property, order))
  }

  /**
    * SortOrder                  = '+' | '-'
    */
  private def SortOrder: Rule1[SortOptionEntry.SortOrder] = rule {
    Asc | Desc
  }

  private def Asc: Rule1[SortOptionEntry.SortOrder] = rule {
    '+' ~ push(SortOptionEntry.SortOrder.ASC)
  }

  private def Desc: Rule1[SortOptionEntry.SortOrder] = rule {
    '-' ~ push(SortOptionEntry.SortOrder.DESC)
  }

  /**
    * Limit                      = "limit", '(', IntegerLiteral, ',', IntegerLiteral, ')'
    */
  private def Limit: Rule1[LimitOption] = rule {
    "limit" ~ '(' ~ LongLiteral ~ ',' ~ LongLiteral ~ ')' ~> ((offset: java.lang.Long, count: java.lang.Long) =>
      SearchModelFactory.newLimitOption(offset.toInt, count.toInt))
  }

  /**
    * Cursor                      = "cursor", '(', StringLiteral, ')'
    */
  private def Cursor[CursorOption] = rule {
    "cursor" ~ '(' ~ CursorString ~ ')' ~>
      ((cursor: String) => SearchModelFactory.newCursorOption(cursor))
  }

  /**
    * Size                        = "size", '(', IntegerLiteral, ')'
    */
  private def Size[SizeOption] = rule {
    "size" ~ '(' ~ capture(Digits) ~ ')' ~>
      ((size: String) => SearchModelFactory.newSizeOption(java.lang.Integer.valueOf(size)))
  }

  private def CursorString: Rule1[String] = PropertyLiteral
}

/**
  * Companion singleton Object.
  */
object RqlOptionParser extends OptionParser {

  /**
    * Parse the specified input.
    *
    * @param input the input that should be parsed.
    * @return the AST RootNode representing the root of the AST.
    * @throws NullPointerException if input is null.
    * @throws ParserException      if input could not be parsed.
    */
  override def parse(input: String): util.List[model.Option] = parseOptions(input)

  private def parseOptions(string: String): util.List[model.Option] = {
    val parser = rqlOptionsParser(string)
    parser.OptionsRoot.run() match {
      case Success(o) => JavaConverters.seqAsJavaList(o)
      case Failure(f: ParseError) => throw new ParserException(parser.formatError(f), f)
      case Failure(f) => throw new ParserException("Unknown error during parsing options: " + f.getMessage, f)
    }
  }

  private def rqlOptionsParser(string: String): RqlOptionParser = new RqlOptionParser(ParserInput.apply(string))
}
