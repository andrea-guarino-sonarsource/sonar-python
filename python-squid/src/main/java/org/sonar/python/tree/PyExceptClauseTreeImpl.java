/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2019 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.python.tree;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.python.api.tree.PyExceptClauseTree;
import org.sonar.python.api.tree.PyExpressionTree;
import org.sonar.python.api.tree.PyStatementListTree;
import org.sonar.python.api.tree.PyToken;
import org.sonar.python.api.tree.PyTreeVisitor;
import org.sonar.python.api.tree.Tree;

public class PyExceptClauseTreeImpl extends PyTree implements PyExceptClauseTree {
  private final PyToken exceptKeyword;
  private final PyStatementListTree body;
  private final PyExpressionTree exception;
  private final PyToken asKeyword;
  private final PyToken commaToken;
  private final PyExpressionTree exceptionInstance;

  public PyExceptClauseTreeImpl(PyToken exceptKeyword, PyStatementListTree body) {
    super(exceptKeyword, body.lastToken());
    this.exceptKeyword = exceptKeyword;
    this.body = body;
    this.exception = null;
    this.asKeyword = null;
    this.commaToken = null;
    this.exceptionInstance = null;
  }

  public PyExceptClauseTreeImpl(PyToken exceptKeyword, PyStatementListTree body,
                                PyExpressionTree exception, @Nullable PyToken asNode, @Nullable PyToken commaNode, PyExpressionTree exceptionInstance) {
    super(exceptKeyword, body.lastToken());
    this.exceptKeyword = exceptKeyword;
    this.body = body;
    this.exception = exception;
    this.asKeyword = asNode;
    this.commaToken = commaNode;
    this.exceptionInstance = exceptionInstance;
  }

  public PyExceptClauseTreeImpl(PyToken exceptKeyword, PyStatementListTree body, PyExpressionTree exception) {
    super(exceptKeyword, body.lastToken());
    this.exceptKeyword = exceptKeyword;
    this.body = body;
    this.exception = exception;
    this.asKeyword = null;
    this.commaToken = null;
    this.exceptionInstance = null;
  }

  @Override
  public PyToken exceptKeyword() {
    return exceptKeyword;
  }

  @Override
  public PyStatementListTree body() {
    return body;
  }

  @CheckForNull
  @Override
  public PyToken asKeyword() {
    return asKeyword;
  }

  @CheckForNull
  @Override
  public PyToken commaToken() {
    return commaToken;
  }

  @CheckForNull
  @Override
  public PyExpressionTree exception() {
    return exception;
  }

  @CheckForNull
  @Override
  public PyExpressionTree exceptionInstance() {
    return exceptionInstance;
  }

  @Override
  public Kind getKind() {
    return Kind.EXCEPT_CLAUSE;
  }

  @Override
  public void accept(PyTreeVisitor visitor) {
    visitor.visitExceptClause(this);
  }

  @Override
  public List<Tree> children() {
    return Stream.of(exceptKeyword, exception, asKeyword, exceptionInstance, commaToken, body).filter(Objects::nonNull).collect(Collectors.toList());
  }
}