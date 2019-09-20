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

import com.sonar.sslr.api.AstNode;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.python.api.tree.PyExpressionTree;
import org.sonar.python.api.tree.PyStatementListTree;
import org.sonar.python.api.tree.PyToken;
import org.sonar.python.api.tree.PyTreeVisitor;
import org.sonar.python.api.tree.PyWithItemTree;
import org.sonar.python.api.tree.PyWithStatementTree;
import org.sonar.python.api.tree.Tree;

public class PyWithStatementTreeImpl extends PyTree implements PyWithStatementTree {

  private final List<PyWithItemTree> withItems;
  private final PyStatementListTree statements;
  private final PyToken asyncKeyword;
  private final boolean isAsync;
  private final PyToken colon;

  public PyWithStatementTreeImpl(AstNode node, List<PyWithItemTree> withItems, PyToken colon, PyStatementListTree statements, @Nullable PyToken asyncKeyword) {
    super(node);
    this.withItems = withItems;
    this.colon = colon;
    this.statements = statements;
    this.asyncKeyword = asyncKeyword;
    this.isAsync = asyncKeyword != null;
  }

  @Override
  public List<PyWithItemTree> withItems() {
    return withItems;
  }

  @Override
  public PyToken colon() {
    return colon;
  }

  @Override
  public PyStatementListTree statements() {
    return statements;
  }

  @Override
  public boolean isAsync() {
    return isAsync;
  }

  @CheckForNull
  @Override
  public PyToken asyncKeyword() {
    return asyncKeyword;
  }

  @Override
  public Kind getKind() {
    return Kind.WITH_STMT;
  }

  @Override
  public void accept(PyTreeVisitor visitor) {
    visitor.visitWithStatement(this);
  }

  @Override
  public List<Tree> children() {
    return Stream.of(Collections.singletonList(asyncKeyword), withItems, Arrays.asList(colon, statements))
      .flatMap(List::stream).collect(Collectors.toList());
  }

  public static class PyWithItemTreeImpl extends PyTree implements PyWithItemTree {

    private final PyExpressionTree test;
    private final PyToken as;
    private final PyExpressionTree expr;

    public PyWithItemTreeImpl(AstNode node, PyExpressionTree test, @Nullable PyToken as, @Nullable PyExpressionTree expr) {
      super(node);
      this.test = test;
      this.as = as;
      this.expr = expr;
    }

    @Override
    public PyExpressionTree test() {
      return test;
    }

    @CheckForNull
    @Override
    public PyToken as() {
      return as;
    }

    @CheckForNull
    @Override
    public PyExpressionTree expression() {
      return expr;
    }

    @Override
    public Kind getKind() {
      return Kind.WITH_ITEM;
    }

    @Override
    public void accept(PyTreeVisitor visitor) {
      visitor.visitWithItem(this);
    }

    @Override
    public List<Tree> children() {
      return Stream.of(test, as, expr).filter(Objects::nonNull).collect(Collectors.toList());
    }
  }
}