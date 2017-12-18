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
package org.eclipse.ditto.model.thingsearchparser.parser

import java.util

import org.eclipse.ditto.model.thingsearch
import org.eclipse.ditto.model.thingsearch.{LimitOption, Option, SearchModelFactory, SortOption, SortOptionEntry}
import org.eclipse.ditto.model.thingsearchparser.ParserException
import org.eclipse.ditto.model.thingsearchparser.options.rql.OptionParser
import org.parboiled2._

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
  def OptionsRoot: Rule1[Seq[thingsearch.Option]] = rule { WhiteSpace ~ Options ~ EOI }

  /**
    * Options                    = Option, { ',', Option }
    */
  private def Options: Rule1[Seq[thingsearch.Option]] = rule {
    oneOrMore(Option).separatedBy(',')
  }

  /**
    * Option                     = Sort | Limit
    */
  private def Option: Rule1[thingsearch.Option] = rule {
    Sort | Limit
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
    SortOrder ~ PropertyLiteral ~> ((order, property) => SearchModelFactory.newSortOptionEntry(order, property))
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
    * @throws ParserException if input could not be parsed.
    */
  override def parse(input: String): util.List[Option] = parseOptions(input)

  private def parseOptions(string: String): util.List[Option] = {
    val parser = rqlOptionsParser(string)
    parser.OptionsRoot.run() match {
      case Success(o) => JavaConverters.seqAsJavaList(o)
      case Failure(f: ParseError) => throw new ParserException(parser.formatError(f), f)
      case Failure(f) => throw new ParserException("Unknown error during parsing options: " + f.getMessage, f)
    }
  }

  private def rqlOptionsParser(string: String): RqlOptionParser = new RqlOptionParser(ParserInput.apply(string))
}
