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
import com.sonar.sslr.api.GenericTokenType;
import com.sonar.sslr.api.RecognitionException;
import com.sonar.sslr.api.Token;
import org.sonar.python.api.tree.PyToken;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.python.DocstringExtractor;
import org.sonar.python.api.PythonGrammar;
import org.sonar.python.api.PythonKeyword;
import org.sonar.python.api.PythonPunctuator;
import org.sonar.python.api.PythonTokenType;
import org.sonar.python.api.tree.PyAliasedNameTree;
import org.sonar.python.api.tree.PyAnnotatedAssignmentTree;
import org.sonar.python.api.tree.PyAnyParameterTree;
import org.sonar.python.api.tree.PyArgListTree;
import org.sonar.python.api.tree.PyArgumentTree;
import org.sonar.python.api.tree.PyAssertStatementTree;
import org.sonar.python.api.tree.PyAssignmentStatementTree;
import org.sonar.python.api.tree.PyBreakStatementTree;
import org.sonar.python.api.tree.PyCallExpressionTree;
import org.sonar.python.api.tree.PyClassDefTree;
import org.sonar.python.api.tree.PyCompoundAssignmentStatementTree;
import org.sonar.python.api.tree.PyComprehensionClauseTree;
import org.sonar.python.api.tree.PyComprehensionExpressionTree;
import org.sonar.python.api.tree.PyComprehensionForTree;
import org.sonar.python.api.tree.PyConditionalExpressionTree;
import org.sonar.python.api.tree.PyContinueStatementTree;
import org.sonar.python.api.tree.PyDecoratorTree;
import org.sonar.python.api.tree.PyDelStatementTree;
import org.sonar.python.api.tree.PyDottedNameTree;
import org.sonar.python.api.tree.PyElseStatementTree;
import org.sonar.python.api.tree.PyExceptClauseTree;
import org.sonar.python.api.tree.PyExecStatementTree;
import org.sonar.python.api.tree.PyExpressionListTree;
import org.sonar.python.api.tree.PyExpressionStatementTree;
import org.sonar.python.api.tree.PyExpressionTree;
import org.sonar.python.api.tree.PyFileInputTree;
import org.sonar.python.api.tree.PyFinallyClauseTree;
import org.sonar.python.api.tree.PyForStatementTree;
import org.sonar.python.api.tree.PyFunctionDefTree;
import org.sonar.python.api.tree.PyGlobalStatementTree;
import org.sonar.python.api.tree.PyIfStatementTree;
import org.sonar.python.api.tree.PyImportFromTree;
import org.sonar.python.api.tree.PyImportNameTree;
import org.sonar.python.api.tree.PyImportStatementTree;
import org.sonar.python.api.tree.PyKeyValuePairTree;
import org.sonar.python.api.tree.PyLambdaExpressionTree;
import org.sonar.python.api.tree.PyNameTree;
import org.sonar.python.api.tree.PyNonlocalStatementTree;
import org.sonar.python.api.tree.PyParameterListTree;
import org.sonar.python.api.tree.PyPassStatementTree;
import org.sonar.python.api.tree.PyPrintStatementTree;
import org.sonar.python.api.tree.PyQualifiedExpressionTree;
import org.sonar.python.api.tree.PyRaiseStatementTree;
import org.sonar.python.api.tree.PyReturnStatementTree;
import org.sonar.python.api.tree.PySliceItemTree;
import org.sonar.python.api.tree.PySliceListTree;
import org.sonar.python.api.tree.PyStatementListTree;
import org.sonar.python.api.tree.PyStatementTree;
import org.sonar.python.api.tree.PyStringElementTree;
import org.sonar.python.api.tree.PyTryStatementTree;
import org.sonar.python.api.tree.PyTypeAnnotationTree;
import org.sonar.python.api.tree.PyWithItemTree;
import org.sonar.python.api.tree.PyWithStatementTree;
import org.sonar.python.api.tree.PyYieldExpressionTree;
import org.sonar.python.api.tree.PyYieldStatementTree;
import org.sonar.python.api.tree.Tree;

public class PythonTreeMaker {

  public PyFileInputTree fileInput(AstNode astNode) {
    List<PyStatementTree> statements = getStatements(astNode).stream().map(this::statement).collect(Collectors.toList());
    PyStatementListTreeImpl statementList = statements.isEmpty() ? null : new PyStatementListTreeImpl(astNode, statements, toPyToken(astNode.getTokens()));
    PyFileInputTreeImpl pyFileInputTree = new PyFileInputTreeImpl(astNode, statementList, toPyToken(DocstringExtractor.extractDocstring(astNode)));
    setParents(pyFileInputTree);
    return pyFileInputTree;
  }

  private static PyToken toPyToken(@Nullable Token token) {
    if (token == null) {
      return null;
    }
    return new PyTokenImpl(token);
  }

  private static List<PyToken> toPyToken(List<Token> tokens) {
    return tokens.stream().map(PyTokenImpl::new).collect(Collectors.toList());
  }

  public void setParents(Tree root) {
    for (Tree child : root.children()) {
      if (child != null) {
        ((PyTree) child).setParent(root);
        setParents(child);
      }
    }
  }

  PyStatementTree statement(AstNode astNode) {
    if (astNode.is(PythonGrammar.IF_STMT)) {
      return ifStatement(astNode);
    }
    if (astNode.is(PythonGrammar.PASS_STMT)) {
      return passStatement(astNode);
    }
    if (astNode.is(PythonGrammar.PRINT_STMT)) {
      return printStatement(astNode);
    }
    if (astNode.is(PythonGrammar.EXEC_STMT)) {
      return execStatement(astNode);
    }
    if (astNode.is(PythonGrammar.ASSERT_STMT)) {
      return assertStatement(astNode);
    }
    if (astNode.is(PythonGrammar.DEL_STMT)) {
      return delStatement(astNode);
    }
    if (astNode.is(PythonGrammar.RETURN_STMT)) {
      return returnStatement(astNode);
    }
    if (astNode.is(PythonGrammar.YIELD_STMT)) {
      return yieldStatement(astNode);
    }
    if (astNode.is(PythonGrammar.RAISE_STMT)) {
      return raiseStatement(astNode);
    }
    if (astNode.is(PythonGrammar.BREAK_STMT)) {
      return breakStatement(astNode);
    }
    if (astNode.is(PythonGrammar.CONTINUE_STMT)) {
      return continueStatement(astNode);
    }
    if (astNode.is(PythonGrammar.FUNCDEF)) {
      return funcDefStatement(astNode);
    }
    if (astNode.is(PythonGrammar.CLASSDEF)) {
      return classDefStatement(astNode);
    }
    if (astNode.is(PythonGrammar.IMPORT_STMT)) {
      return importStatement(astNode);
    }
    if (astNode.is(PythonGrammar.FOR_STMT)) {
      return forStatement(astNode);
    }
    if (astNode.is(PythonGrammar.WHILE_STMT)) {
      return whileStatement(astNode);
    }
    if (astNode.is(PythonGrammar.GLOBAL_STMT)) {
      return globalStatement(astNode);
    }
    if (astNode.is(PythonGrammar.NONLOCAL_STMT)) {
      return nonlocalStatement(astNode);
    }
    if (astNode.is(PythonGrammar.EXPRESSION_STMT) && astNode.hasDirectChildren(PythonGrammar.ANNASSIGN)) {
      return annotatedAssignment(astNode);
    }
    if (astNode.is(PythonGrammar.EXPRESSION_STMT) && astNode.hasDirectChildren(PythonPunctuator.ASSIGN)) {
      return assignment(astNode);
    }
    if (astNode.is(PythonGrammar.EXPRESSION_STMT) && astNode.hasDirectChildren(PythonGrammar.AUGASSIGN)) {
      return compoundAssignment(astNode);
    }
    if (astNode.is(PythonGrammar.EXPRESSION_STMT)) {
      return expressionStatement(astNode);
    }
    if (astNode.is(PythonGrammar.TRY_STMT)) {
      return tryStatement(astNode);
    }
    if (astNode.is(PythonGrammar.ASYNC_STMT) && astNode.hasDirectChildren(PythonGrammar.FOR_STMT)) {
      return forStatement(astNode);
    }
    if (astNode.is(PythonGrammar.ASYNC_STMT) && astNode.hasDirectChildren(PythonGrammar.WITH_STMT)) {
      return withStatement(astNode);
    }
    if (astNode.is(PythonGrammar.WITH_STMT)) {
      return withStatement(astNode);
    }
    throw new IllegalStateException("Statement " + astNode.getType() + " not correctly translated to strongly typed AST");
  }

  public PyAnnotatedAssignmentTree annotatedAssignment(AstNode astNode) {
    AstNode annAssign = astNode.getFirstChild(PythonGrammar.ANNASSIGN);
    AstNode colonTokenNode = annAssign.getFirstChild(PythonPunctuator.COLON);
    PyExpressionTree variable = exprListOrTestList(astNode.getFirstChild(PythonGrammar.TESTLIST_STAR_EXPR));
    PyExpressionTree annotation = expression(annAssign.getFirstChild(PythonGrammar.TEST));
    AstNode equalTokenNode = annAssign.getFirstChild(PythonPunctuator.ASSIGN);
    PyToken equalToken = null;
    PyExpressionTree assignedValue = null;
    if (equalTokenNode != null) {
      equalToken = toPyToken(equalTokenNode.getToken());
      assignedValue = expression(equalTokenNode.getNextSibling());
    }
    return new PyAnnotatedAssignmentTreeImpl(variable, toPyToken(colonTokenNode.getToken()), annotation, equalToken, assignedValue);
  }

  private PyStatementListTree getStatementListFromSuite(AstNode suite) {
    return new PyStatementListTreeImpl(suite, getStatementsFromSuite(suite), toPyToken(suite.getTokens()));
  }

  private List<PyStatementTree> getStatementsFromSuite(AstNode astNode) {
    if (astNode.is(PythonGrammar.SUITE)) {
      List<AstNode> statements = getStatements(astNode);
      if (statements.isEmpty()) {
        AstNode stmtListNode = astNode.getFirstChild(PythonGrammar.STMT_LIST);
        return stmtListNode.getChildren(PythonGrammar.SIMPLE_STMT).stream()
          .map(AstNode::getFirstChild)
          .map(this::statement)
          .collect(Collectors.toList());
      }
      return statements.stream().map(this::statement)
        .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  private static List<AstNode> getStatements(AstNode astNode) {
    List<AstNode> statements = astNode.getChildren(PythonGrammar.STATEMENT);
    return statements.stream().flatMap(stmt -> {
      if (stmt.hasDirectChildren(PythonGrammar.STMT_LIST)) {
        AstNode stmtListNode = stmt.getFirstChild(PythonGrammar.STMT_LIST);
        return stmtListNode.getChildren(PythonGrammar.SIMPLE_STMT).stream()
          .map(AstNode::getFirstChild);
      }
      return stmt.getChildren(PythonGrammar.COMPOUND_STMT).stream()
        .map(AstNode::getFirstChild);
    }).collect(Collectors.toList());
  }

  // Simple statements

  public PyPrintStatementTree printStatement(AstNode astNode) {
    List<PyExpressionTree> expressions = expressionsFromTest(astNode);
    return new PyPrintStatementTreeImpl(astNode, toPyToken(astNode.getTokens()).get(0), expressions);
  }

  public PyExecStatementTree execStatement(AstNode astNode) {
    PyExpressionTree expression = expression(astNode.getFirstChild(PythonGrammar.EXPR));
    List<PyExpressionTree> expressions = expressionsFromTest(astNode);
    if (expressions.isEmpty()) {
      return new PyExecStatementTreeImpl(astNode, toPyToken(astNode.getTokens()).get(0), expression);
    }
    return new PyExecStatementTreeImpl(astNode, toPyToken(astNode.getTokens().get(0)), expression, expressions.get(0), expressions.size() == 2 ? expressions.get(1) : null);
  }

  public PyAssertStatementTree assertStatement(AstNode astNode) {
    List<PyExpressionTree> expressions = expressionsFromTest(astNode);
    PyExpressionTree condition = expressions.get(0);
    PyExpressionTree message = null;
    if (expressions.size() > 1) {
      message = expressions.get(1);
    }
    return new PyAssertStatementTreeImpl(astNode, toPyToken(astNode.getTokens()).get(0), condition, message);
  }

  public PyPassStatementTree passStatement(AstNode astNode) {
    return new PyPassStatementTreeImpl(astNode, toPyToken(astNode.getTokens()).get(0));
  }

  public PyDelStatementTree delStatement(AstNode astNode) {
    List<PyExpressionTree> expressionTrees = expressionsFromExprList(astNode.getFirstChild(PythonGrammar.EXPRLIST));
    return new PyDelStatementTreeImpl(astNode, toPyToken(astNode.getTokens()).get(0), expressionTrees);
  }

  public PyReturnStatementTree returnStatement(AstNode astNode) {
    AstNode testListNode = astNode.getFirstChild(PythonGrammar.TESTLIST);
    List<PyExpressionTree> expressionTrees = Collections.emptyList();
    if (testListNode != null) {
      expressionTrees = expressionsFromTest(testListNode);
    }
    return new PyReturnStatementTreeImpl(astNode, toPyToken(astNode.getTokens()).get(0), expressionTrees);
  }

  public PyYieldStatementTree yieldStatement(AstNode astNode) {
    return new PyYieldStatementTreeImpl(astNode, yieldExpression(astNode.getFirstChild(PythonGrammar.YIELD_EXPR)));
  }

  public PyYieldExpressionTree yieldExpression(AstNode astNode) {
    PyToken yieldKeyword = toPyToken(astNode.getFirstChild(PythonKeyword.YIELD).getToken());
    AstNode nodeContainingExpression = astNode;
    AstNode fromKeyword = astNode.getFirstChild(PythonKeyword.FROM);
    if (fromKeyword == null) {
      nodeContainingExpression = astNode.getFirstChild(PythonGrammar.TESTLIST);
    }
    List<PyExpressionTree> expressionTrees = Collections.emptyList();
    if (nodeContainingExpression != null) {
      expressionTrees = expressionsFromTest(nodeContainingExpression);
    }
    return new PyYieldExpressionTreeImpl(astNode, yieldKeyword, fromKeyword == null ? null : toPyToken(fromKeyword.getToken()), expressionTrees);
  }

  public PyRaiseStatementTree raiseStatement(AstNode astNode) {
    AstNode fromKeyword = astNode.getFirstChild(PythonKeyword.FROM);
    List<AstNode> expressions = new ArrayList<>();
    AstNode fromExpression = null;
    if (fromKeyword != null) {
      expressions.add(astNode.getFirstChild(PythonGrammar.TEST));
      fromExpression = astNode.getLastChild(PythonGrammar.TEST);
    } else {
      expressions = astNode.getChildren(PythonGrammar.TEST);
    }
    List<PyExpressionTree> expressionTrees = expressions.stream()
      .map(this::expression)
      .collect(Collectors.toList());
    return new PyRaiseStatementTreeImpl(astNode, toPyToken(astNode.getFirstChild(PythonKeyword.RAISE).getToken()),
      expressionTrees, fromKeyword == null ? null : toPyToken(fromKeyword.getToken()), fromExpression == null ? null : expression(fromExpression));
  }

  public PyBreakStatementTree breakStatement(AstNode astNode) {
    return new PyBreakStatementTreeImpl(astNode, toPyToken(astNode.getToken()));
  }

  public PyContinueStatementTree continueStatement(AstNode astNode) {
    return new PyContinueStatementTreeImpl(astNode, toPyToken(astNode.getToken()));
  }

  public PyImportStatementTree importStatement(AstNode astNode) {
    AstNode importStmt = astNode.getFirstChild();
    if (importStmt.is(PythonGrammar.IMPORT_NAME)) {
      return importName(importStmt);
    }
    return importFromStatement(importStmt);
  }

  private PyImportNameTree importName(AstNode astNode) {
    PyToken importKeyword = toPyToken(astNode.getFirstChild(PythonKeyword.IMPORT).getToken());
    List<PyAliasedNameTree> aliasedNames = astNode
      .getFirstChild(PythonGrammar.DOTTED_AS_NAMES)
      .getChildren(PythonGrammar.DOTTED_AS_NAME).stream()
      .map(this::aliasedName)
      .collect(Collectors.toList());
    return new PyImportNameTreeImpl(astNode, importKeyword, aliasedNames);
  }

  public PyImportFromTree importFromStatement(AstNode astNode) {
    PyToken importKeyword = toPyToken(astNode.getFirstChild(PythonKeyword.IMPORT).getToken());
    PyToken fromKeyword = toPyToken(astNode.getFirstChild(PythonKeyword.FROM).getToken());
    List<PyToken> dottedPrefixForModule = toPyToken(astNode.getChildren(PythonPunctuator.DOT).stream()
      .map(AstNode::getToken)
      .collect(Collectors.toList()));
    AstNode moduleNode = astNode.getFirstChild(PythonGrammar.DOTTED_NAME);
    PyDottedNameTree moduleName = null;
    if (moduleNode != null) {
      moduleName = dottedName(moduleNode);
    }
    AstNode importAsnames = astNode.getFirstChild(PythonGrammar.IMPORT_AS_NAMES);
    List<PyAliasedNameTree> aliasedImportNames = null;
    boolean isWildcardImport = true;
    if (importAsnames != null) {
      aliasedImportNames = importAsnames.getChildren(PythonGrammar.IMPORT_AS_NAME).stream()
        .map(this::aliasedName)
        .collect(Collectors.toList());
      isWildcardImport = false;
    }
    return new PyImportFromTreeImpl(astNode, fromKeyword, dottedPrefixForModule, moduleName, importKeyword, aliasedImportNames, isWildcardImport);
  }

  private PyAliasedNameTree aliasedName(AstNode astNode) {
    AstNode asKeyword = astNode.getFirstChild(PythonKeyword.AS);
    PyDottedNameTree dottedName;
    if (astNode.is(PythonGrammar.DOTTED_AS_NAME)) {
      dottedName = dottedName(astNode.getFirstChild(PythonGrammar.DOTTED_NAME));
    } else {
      // astNode is IMPORT_AS_NAME
      AstNode importedName = astNode.getFirstChild(PythonGrammar.NAME);
      dottedName = new PyDottedNameTreeImpl(astNode, Collections.singletonList(name(importedName)));
    }
    if (asKeyword == null) {
      return new PyAliasedNameTreeImpl(astNode, null, dottedName, null);
    }
    return new PyAliasedNameTreeImpl(astNode, toPyToken(asKeyword.getToken()), dottedName, name(astNode.getLastChild(PythonGrammar.NAME)));
  }

  private static PyDottedNameTree dottedName(AstNode astNode) {
    List<PyNameTree> names = astNode
      .getChildren(PythonGrammar.NAME).stream()
      .map(PythonTreeMaker::name)
      .collect(Collectors.toList());
    return new PyDottedNameTreeImpl(astNode, names);
  }

  public PyGlobalStatementTree globalStatement(AstNode astNode) {
    PyToken globalKeyword = toPyToken(astNode.getFirstChild(PythonKeyword.GLOBAL).getToken());
    List<PyNameTree> variables = astNode.getChildren(PythonGrammar.NAME).stream()
      .map(PythonTreeMaker::name)
      .collect(Collectors.toList());
    return new PyGlobalStatementTreeImpl(astNode, globalKeyword, variables);
  }

  public PyNonlocalStatementTree nonlocalStatement(AstNode astNode) {
    PyToken nonlocalKeyword = toPyToken(astNode.getFirstChild(PythonKeyword.NONLOCAL).getToken());
    List<PyNameTree> variables = astNode.getChildren(PythonGrammar.NAME).stream()
      .map(PythonTreeMaker::name)
      .collect(Collectors.toList());
    return new PyNonlocalStatementTreeImpl(astNode, nonlocalKeyword, variables);
  }
  // Compound statements

  public PyIfStatementTree ifStatement(AstNode astNode) {
    PyToken ifToken = toPyToken(astNode.getTokens().get(0));
    AstNode condition = astNode.getFirstChild(PythonGrammar.TEST);
    AstNode suite = astNode.getFirstChild(PythonGrammar.SUITE);
    PyStatementListTree statements = getStatementListFromSuite(suite);
    AstNode elseSuite = astNode.getLastChild(PythonGrammar.SUITE);
    PyElseStatementTree elseStatement = null;
    if (elseSuite.getPreviousSibling().getPreviousSibling().is(PythonKeyword.ELSE)) {
      elseStatement = elseStatement(elseSuite);
    }
    List<PyIfStatementTree> elifBranches = astNode.getChildren(PythonKeyword.ELIF).stream()
      .map(this::elifStatement)
      .collect(Collectors.toList());

    return new PyIfStatementTreeImpl(ifToken, expression(condition), statements, elifBranches, elseStatement);
  }

  private PyIfStatementTree elifStatement(AstNode astNode) {
    PyToken elifToken = toPyToken(astNode.getToken());
    AstNode suite = astNode.getNextSibling().getNextSibling().getNextSibling();
    AstNode condition = astNode.getNextSibling();
    PyStatementListTree statements = getStatementListFromSuite(suite);
    return new PyIfStatementTreeImpl(elifToken, expression(condition), statements);
  }

  private PyElseStatementTree elseStatement(AstNode astNode) {
    PyToken elseToken = toPyToken(astNode.getPreviousSibling().getPreviousSibling().getToken());
    PyStatementListTree statements = getStatementListFromSuite(astNode);
    return new PyElseStatementTreeImpl(elseToken, statements);
  }

  public PyFunctionDefTree funcDefStatement(AstNode astNode) {
    AstNode decoratorsNode = astNode.getFirstChild(PythonGrammar.DECORATORS);
    List<PyDecoratorTree> decorators = Collections.emptyList();
    if (decoratorsNode != null) {
      decorators = decoratorsNode.getChildren(PythonGrammar.DECORATOR).stream()
        .map(this::decorator)
        .collect(Collectors.toList());
    }
    PyNameTree name = name(astNode.getFirstChild(PythonGrammar.FUNCNAME).getFirstChild(PythonGrammar.NAME));
    PyParameterListTree parameterList = null;
    AstNode typedArgListNode = astNode.getFirstChild(PythonGrammar.TYPEDARGSLIST);
    if (typedArgListNode != null) {
      List<PyAnyParameterTree> arguments = typedArgListNode.getChildren(PythonGrammar.TFPDEF).stream()
        .map(this::parameter).collect(Collectors.toList());
      parameterList = new PyParameterListTreeImpl(typedArgListNode, arguments);
    }

    PyStatementListTree body = getStatementListFromSuite(astNode.getFirstChild(PythonGrammar.SUITE));
    AstNode defNode = astNode.getFirstChild(PythonKeyword.DEF);
    PyToken asyncToken = null;
    AstNode defPreviousSibling = defNode.getPreviousSibling();
    if (defPreviousSibling != null && defPreviousSibling.getToken().getValue().equals("async")) {
      asyncToken = toPyToken(defPreviousSibling.getToken());
    }
    PyToken lPar = toPyToken(astNode.getFirstChild(PythonPunctuator.LPARENTHESIS).getToken());
    PyToken rPar = toPyToken(astNode.getFirstChild(PythonPunctuator.RPARENTHESIS).getToken());

    PyTypeAnnotationTree returnType = null;
    AstNode returnTypeNode = astNode.getFirstChild(PythonGrammar.FUN_RETURN_ANNOTATION);
    if (returnTypeNode != null) {
      List<AstNode> children = returnTypeNode.getChildren();
      returnType = new PyTypeAnnotationTreeImpl(toPyToken(children.get(0).getToken()), toPyToken(children.get(1).getToken()), expression(children.get(2)));
    }

    PyToken colon = toPyToken(astNode.getFirstChild(PythonPunctuator.COLON).getToken());
    return new PyFunctionDefTreeImpl(astNode, decorators, asyncToken, toPyToken(defNode.getToken()), name, lPar, parameterList, rPar,
      returnType, colon, body, isMethodDefinition(astNode), toPyToken(DocstringExtractor.extractDocstring(astNode)));
  }

  private PyDecoratorTree decorator(AstNode astNode) {
    PyToken atToken = toPyToken(astNode.getFirstChild(PythonPunctuator.AT).getToken());
    PyDottedNameTree dottedName = dottedName(astNode.getFirstChild(PythonGrammar.DOTTED_NAME));
    PyToken lPar = astNode.getFirstChild(PythonPunctuator.LPARENTHESIS) == null ? null : toPyToken(astNode.getFirstChild(PythonPunctuator.LPARENTHESIS).getToken());
    PyToken rPar = astNode.getFirstChild(PythonPunctuator.RPARENTHESIS) == null ? null : toPyToken(astNode.getFirstChild(PythonPunctuator.RPARENTHESIS).getToken());
    PyArgListTree argListTree = argList(astNode.getFirstChild(PythonGrammar.ARGLIST));
    return new PyDecoratorTreeImpl(astNode, atToken, dottedName, lPar, argListTree, rPar);
  }

  private static boolean isMethodDefinition(AstNode node) {
    AstNode parent = node.getParent();
    for (int i = 0; i < 3; i++) {
      if (parent != null) {
        parent = parent.getParent();
      }
    }
    return parent != null && parent.is(PythonGrammar.CLASSDEF);
  }

  public PyClassDefTree classDefStatement(AstNode astNode) {
    AstNode decoratorsNode = astNode.getFirstChild(PythonGrammar.DECORATORS);
    List<PyDecoratorTree> decorators = Collections.emptyList();
    if (decoratorsNode != null) {
      decorators = decoratorsNode.getChildren(PythonGrammar.DECORATOR).stream()
        .map(this::decorator)
        .collect(Collectors.toList());
    }
    PyNameTree name = name(astNode.getFirstChild(PythonGrammar.CLASSNAME).getFirstChild(PythonGrammar.NAME));
    PyArgListTree args = null;
    AstNode leftPar = astNode.getFirstChild(PythonPunctuator.LPARENTHESIS);
    if (leftPar != null) {
      args = argList(astNode.getFirstChild(PythonGrammar.ARGLIST));
    }
    PyStatementListTree body = getStatementListFromSuite(astNode.getFirstChild(PythonGrammar.SUITE));
    PyToken classToken = toPyToken(astNode.getFirstChild(PythonKeyword.CLASS).getToken());
    AstNode rightPar = astNode.getFirstChild(PythonPunctuator.RPARENTHESIS);
    PyToken colon = toPyToken(astNode.getFirstChild(PythonPunctuator.COLON).getToken());
    return new PyClassDefTreeImpl(astNode, decorators, classToken, name,
      leftPar != null ? toPyToken(leftPar.getToken()) : null, args, rightPar != null ? toPyToken(rightPar.getToken()) : null, colon, body, toPyToken(DocstringExtractor.extractDocstring(astNode)));
  }

  private static PyNameTree name(AstNode astNode) {
    return new PyNameTreeImpl(astNode, astNode.getFirstChild(GenericTokenType.IDENTIFIER).getTokenOriginalValue(), astNode.getParent().is(PythonGrammar.ATOM));
  }

  public PyForStatementTree forStatement(AstNode astNode) {
    AstNode forStatementNode = astNode;
    PyToken asyncToken = null;
    if (astNode.is(PythonGrammar.ASYNC_STMT)) {
      asyncToken = toPyToken(astNode.getFirstChild().getToken());
      forStatementNode = astNode.getFirstChild(PythonGrammar.FOR_STMT);
    }
    PyToken forKeyword = toPyToken(forStatementNode.getFirstChild(PythonKeyword.FOR).getToken());
    PyToken inKeyword = toPyToken(forStatementNode.getFirstChild(PythonKeyword.IN).getToken());
    PyToken colon = toPyToken(forStatementNode.getFirstChild(PythonPunctuator.COLON).getToken());
    List<PyExpressionTree> expressions = expressionsFromExprList(forStatementNode.getFirstChild(PythonGrammar.EXPRLIST));
    List<PyExpressionTree> testExpressions = expressionsFromTest(forStatementNode.getFirstChild(PythonGrammar.TESTLIST));
    AstNode firstSuite = forStatementNode.getFirstChild(PythonGrammar.SUITE);
    PyStatementListTree body = getStatementListFromSuite(firstSuite);
    AstNode lastSuite = forStatementNode.getLastChild(PythonGrammar.SUITE);
    AstNode elseKeywordNode = forStatementNode.getFirstChild(PythonKeyword.ELSE);
    PyToken elseKeyword = null;
    PyToken elseColonKeyword = null;
    if (elseKeywordNode != null) {
      elseKeyword = toPyToken(elseKeywordNode.getToken());
      elseColonKeyword = toPyToken(elseKeywordNode.getNextSibling().getToken());
    }
    PyStatementListTree elseBody = lastSuite == firstSuite ? null : getStatementListFromSuite(lastSuite);
    return new PyForStatementTreeImpl(forStatementNode, forKeyword, expressions, inKeyword, testExpressions, colon, body, elseKeyword, elseColonKeyword, elseBody, asyncToken);
  }

  public PyWhileStatementTreeImpl whileStatement(AstNode astNode) {
    PyToken whileKeyword = toPyToken(astNode.getFirstChild(PythonKeyword.WHILE).getToken());
    PyToken colon = toPyToken(astNode.getFirstChild(PythonPunctuator.COLON).getToken());
    PyExpressionTree condition = expression(astNode.getFirstChild(PythonGrammar.TEST));
    AstNode firstSuite = astNode.getFirstChild(PythonGrammar.SUITE);
    PyStatementListTree body = getStatementListFromSuite(firstSuite);
    AstNode lastSuite = astNode.getLastChild(PythonGrammar.SUITE);
    AstNode elseKeywordNode = astNode.getFirstChild(PythonKeyword.ELSE);
    PyToken elseKeyword = null;
    PyToken elseColonKeyword = null;
    if (elseKeywordNode != null) {
      elseKeyword = toPyToken(elseKeywordNode.getToken());
      elseColonKeyword = toPyToken(elseKeywordNode.getNextSibling().getToken());
    }
    PyStatementListTree elseBody = lastSuite == firstSuite ? null : getStatementListFromSuite(lastSuite);
    return new PyWhileStatementTreeImpl(astNode, whileKeyword, condition, colon, body, elseKeyword, elseColonKeyword, elseBody);
  }

  public PyExpressionStatementTree expressionStatement(AstNode astNode) {
    List<PyExpressionTree> expressions = astNode.getFirstChild(PythonGrammar.TESTLIST_STAR_EXPR).getChildren(PythonGrammar.TEST, PythonGrammar.STAR_EXPR).stream()
      .map(this::expression)
      .collect(Collectors.toList());
    return new PyExpressionStatementTreeImpl(astNode, expressions);
  }

  public PyAssignmentStatementTree assignment(AstNode astNode) {
    List<PyToken> assignTokens = new ArrayList<>();
    List<PyExpressionListTree> lhsExpressions = new ArrayList<>();
    List<AstNode> assignNodes = astNode.getChildren(PythonPunctuator.ASSIGN);
    for (AstNode assignNode : assignNodes) {
      assignTokens.add(toPyToken(assignNode.getToken()));
      lhsExpressions.add(expressionList(assignNode.getPreviousSibling()));
    }
    AstNode assignedValueNode = assignNodes.get(assignNodes.size() - 1).getNextSibling();
    PyExpressionTree assignedValue = assignedValueNode.is(PythonGrammar.YIELD_EXPR) ? yieldExpression(assignedValueNode) : exprListOrTestList(assignedValueNode);
    return new PyAssignmentStatementTreeImpl(astNode, assignTokens, lhsExpressions, assignedValue);
  }

  public PyCompoundAssignmentStatementTree compoundAssignment(AstNode astNode) {
    AstNode augAssignNodes = astNode.getFirstChild(PythonGrammar.AUGASSIGN);
    PyExpressionTree lhsExpression = exprListOrTestList(augAssignNodes.getPreviousSibling());
    AstNode rhsAstNode = augAssignNodes.getNextSibling();
    PyExpressionTree rhsExpression;
    if (rhsAstNode.is(PythonGrammar.YIELD_EXPR)) {
      rhsExpression = yieldExpression(rhsAstNode);
    } else {
      rhsExpression = exprListOrTestList(rhsAstNode);
    }
    return new PyCompoundAssignmentStatementTreeImpl(astNode, lhsExpression, toPyToken(augAssignNodes.getToken()), rhsExpression);
  }

  private PyExpressionListTree expressionList(AstNode astNode) {
    if (astNode.is(PythonGrammar.TESTLIST_STAR_EXPR, PythonGrammar.TESTLIST_COMP)) {
      List<PyExpressionTree> expressions = astNode.getChildren(PythonGrammar.TEST, PythonGrammar.STAR_EXPR).stream()
        .map(this::expression)
        .collect(Collectors.toList());
      return new PyExpressionListTreeImpl(astNode, expressions);
    }
    return new PyExpressionListTreeImpl(astNode, Collections.singletonList(expression(astNode)));
  }

  public PyTryStatementTree tryStatement(AstNode astNode) {
    PyToken tryKeyword = toPyToken(astNode.getFirstChild(PythonKeyword.TRY).getToken());
    PyStatementListTree tryBody = getStatementListFromSuite(astNode.getFirstChild(PythonGrammar.SUITE));
    List<PyExceptClauseTree> exceptClauseTrees = astNode.getChildren(PythonGrammar.EXCEPT_CLAUSE).stream()
      .map(except -> {
        AstNode suite = except.getNextSibling().getNextSibling();
        return exceptClause(except, getStatementListFromSuite(suite));
      })
      .collect(Collectors.toList());
    PyFinallyClauseTree finallyClause = null;
    AstNode finallyNode = astNode.getFirstChild(PythonKeyword.FINALLY);
    if (finallyNode != null) {
      AstNode finallySuite = finallyNode.getNextSibling().getNextSibling();
      PyStatementListTree body = getStatementListFromSuite(finallySuite);
      finallyClause = new PyFinallyClauseTreeImpl(toPyToken(finallyNode.getToken()), body);
    }
    PyElseStatementTree elseStatementTree = null;
    AstNode elseNode = astNode.getFirstChild(PythonKeyword.ELSE);
    if (elseNode != null) {
      elseStatementTree = elseStatement(elseNode.getNextSibling().getNextSibling());
    }
    return new PyTryStatementTreeImpl(astNode, tryKeyword, tryBody, exceptClauseTrees, finallyClause, elseStatementTree);
  }

  public PyWithStatementTree withStatement(AstNode astNode) {
    AstNode withStmtNode = astNode;
    PyToken asyncKeyword = null;
    if (astNode.is(PythonGrammar.ASYNC_STMT)) {
      withStmtNode = astNode.getFirstChild(PythonGrammar.WITH_STMT);
      asyncKeyword = toPyToken(astNode.getFirstChild().getToken());
    }
    List<PyWithItemTree> withItems = withItems(withStmtNode.getChildren(PythonGrammar.WITH_ITEM));
    AstNode suite = withStmtNode.getFirstChild(PythonGrammar.SUITE);
    PyToken colon = toPyToken(suite.getPreviousSibling().getToken());
    PyStatementListTree statements = getStatementListFromSuite(suite);
    return new PyWithStatementTreeImpl(withStmtNode, withItems, colon, statements, asyncKeyword);
  }

  private List<PyWithItemTree> withItems(List<AstNode> withItems) {
    return withItems.stream().map(this::withItem).collect(Collectors.toList());
  }

  private PyWithItemTree withItem(AstNode withItem) {
    AstNode testNode = withItem.getFirstChild(PythonGrammar.TEST);
    PyExpressionTree test = expression(testNode);
    AstNode asNode = testNode.getNextSibling();
    PyExpressionTree expr = null;
    PyToken as = null;
    if (asNode != null) {
      as = toPyToken(asNode.getToken());
      expr = expression(withItem.getFirstChild(PythonGrammar.EXPR));
    }
    return new PyWithStatementTreeImpl.PyWithItemTreeImpl(withItem, test, as, expr);
  }

  private PyExceptClauseTree exceptClause(AstNode except, PyStatementListTree body) {
    PyToken exceptKeyword = toPyToken(except.getFirstChild(PythonKeyword.EXCEPT).getToken());
    AstNode exceptionNode = except.getFirstChild(PythonGrammar.TEST);
    if (exceptionNode == null) {
      return new PyExceptClauseTreeImpl(exceptKeyword, body);
    }
    AstNode asNode = except.getFirstChild(PythonKeyword.AS);
    AstNode commaNode = except.getFirstChild(PythonPunctuator.COMMA);
    if (asNode != null || commaNode != null) {
      PyExpressionTree exceptionInstance = expression(except.getLastChild(PythonGrammar.TEST));
      PyToken asNodeToken = asNode != null ? toPyToken(asNode.getToken()) : null;
      PyToken commaNodeToken = commaNode != null ? toPyToken(commaNode.getToken()) : null;
      return new PyExceptClauseTreeImpl(exceptKeyword, body, expression(exceptionNode), asNodeToken, commaNodeToken, exceptionInstance);
    }
    return new PyExceptClauseTreeImpl(exceptKeyword, body, expression(exceptionNode));
  }

  // expressions

  private List<PyExpressionTree> expressionsFromTest(AstNode astNode) {
    return astNode.getChildren(PythonGrammar.TEST).stream().map(this::expression).collect(Collectors.toList());
  }

  private List<PyExpressionTree> expressionsFromExprList(AstNode firstChild) {
    return firstChild
      .getChildren(PythonGrammar.EXPR, PythonGrammar.STAR_EXPR)
      .stream().map(this::expression).collect(Collectors.toList());
  }

  private PyExpressionTree exprListOrTestList(AstNode exprListOrTestList) {
    List<PyExpressionTree> expressions = exprListOrTestList
      .getChildren(PythonGrammar.EXPR, PythonGrammar.STAR_EXPR, PythonGrammar.TEST).stream()
      .map(this::expression)
      .collect(Collectors.toList());
    List<AstNode> commas = exprListOrTestList.getChildren(PythonPunctuator.COMMA);
    if (commas.isEmpty()) {
      return expressions.get(0);
    }
    List<PyToken> commaTokens = toPyToken(commas.stream().map(AstNode::getToken).collect(Collectors.toList()));
    return new PyTupleTreeImpl(exprListOrTestList, null, expressions, commaTokens, null);
  }

  PyExpressionTree expression(AstNode astNode) {
    if (astNode.is(PythonGrammar.ATOM) && astNode.getFirstChild().is(PythonPunctuator.LBRACKET)) {
      return listLiteral(astNode);
    }
    if (astNode.is(PythonGrammar.ATOM) && astNode.getFirstChild().is(PythonPunctuator.LPARENTHESIS)) {
      return parenthesized(astNode);
    }
    if (astNode.is(PythonGrammar.ATOM) && astNode.getFirstChild().is(PythonPunctuator.LCURLYBRACE)) {
      return dictOrSetLiteral(astNode);
    }
    if (astNode.is(PythonGrammar.ATOM) && astNode.getFirstChild().is(PythonPunctuator.BACKTICK)) {
      return repr(astNode);
    }
    if (astNode.is(PythonGrammar.ATOM) && astNode.getFirstChild().is(PythonTokenType.STRING)) {
      return stringLiteral(astNode);
    }
    if (astNode.is(PythonGrammar.ATOM) && astNode.getChildren().size() == 1) {
      return expression(astNode.getFirstChild());
    }
    if (astNode.is(PythonGrammar.TEST) && astNode.hasDirectChildren(PythonKeyword.IF)) {
      return conditionalExpression(astNode);
    }
    if (astNode.is(PythonTokenType.NUMBER)) {
      return numericLiteral(astNode);
    }
    if (astNode.is(PythonGrammar.YIELD_EXPR)) {
      return yieldExpression(astNode);
    }
    if (astNode.is(PythonGrammar.NAME)) {
      return name(astNode);
    }
    if (astNode.is(PythonGrammar.ATTRIBUTE_REF)) {
      return qualifiedExpression(astNode);
    }
    if (astNode.is(PythonGrammar.CALL_EXPR)) {
      return callExpression(astNode);
    }
    if (astNode.is(PythonGrammar.EXPR, PythonGrammar.TEST, PythonGrammar.TEST_NOCOND)) {
      if (astNode.getChildren().size() == 1) {
        return expression(astNode.getFirstChild());
      } else {
        return binaryExpression(astNode);
      }
    }
    if (astNode.is(
      PythonGrammar.A_EXPR, PythonGrammar.M_EXPR, PythonGrammar.SHIFT_EXPR,
      PythonGrammar.AND_EXPR, PythonGrammar.OR_EXPR, PythonGrammar.XOR_EXPR,
      PythonGrammar.AND_TEST, PythonGrammar.OR_TEST,
      PythonGrammar.COMPARISON)) {
      return binaryExpression(astNode);
    }
    if (astNode.is(PythonGrammar.POWER)) {
      return powerExpression(astNode);
    }
    if (astNode.is(PythonGrammar.LAMBDEF, PythonGrammar.LAMBDEF_NOCOND)) {
      return lambdaExpression(astNode);
    }
    if (astNode.is(PythonGrammar.FACTOR, PythonGrammar.NOT_TEST)) {
      return new PyUnaryExpressionTreeImpl(astNode, toPyToken(astNode.getFirstChild().getToken()), expression(astNode.getLastChild()));
    }
    if (astNode.is(PythonGrammar.STAR_EXPR)) {
      return new PyStarredExpressionTreeImpl(astNode, toPyToken(astNode.getToken()), expression(astNode.getLastChild()));
    }
    if (astNode.is(PythonGrammar.SUBSCRIPTION_OR_SLICING)) {
      PyExpressionTree baseExpr = expression(astNode.getFirstChild(PythonGrammar.ATOM));
      PyToken leftBracket = toPyToken(astNode.getFirstChild(PythonPunctuator.LBRACKET).getToken());
      PyToken rightBracket = toPyToken(astNode.getFirstChild(PythonPunctuator.RBRACKET).getToken());
      return subscriptionOrSlicing(baseExpr, leftBracket, astNode, rightBracket);
    }
    if (astNode.is(PythonKeyword.NONE)) {
      return new PyNoneExpressionTreeImpl(astNode, toPyToken(astNode.getToken()));
    }
    if (astNode.is(PythonGrammar.ELLIPSIS)) {
      return new PyEllipsisExpressionTreeImpl(astNode);
    }
    throw new IllegalStateException("Expression " + astNode.getType() + " not correctly translated to strongly typed AST");
  }

  private PyExpressionTree repr(AstNode astNode) {
    PyToken openingBacktick = toPyToken(astNode.getFirstChild(PythonPunctuator.BACKTICK).getToken());
    PyToken closingBacktick = toPyToken(astNode.getLastChild(PythonPunctuator.BACKTICK).getToken());
    List<PyExpressionTree> expressions = astNode.getChildren(PythonGrammar.TEST).stream().map(this::expression).collect(Collectors.toList());
    PyExpressionListTree expressionListTree = new PyExpressionListTreeImpl(expressions);
    return new PyReprExpressionTreeImpl(astNode, openingBacktick, expressionListTree, closingBacktick);
  }

  private PyExpressionTree dictOrSetLiteral(AstNode astNode) {
    PyToken lCurlyBrace = toPyToken(astNode.getFirstChild(PythonPunctuator.LCURLYBRACE).getToken());
    PyToken rCurlyBrace = toPyToken(astNode.getLastChild(PythonPunctuator.RCURLYBRACE).getToken());
    AstNode dictOrSetMaker = astNode.getFirstChild(PythonGrammar.DICTORSETMAKER);
    if (dictOrSetMaker == null) {
      return new PyDictionaryLiteralTreeImpl(astNode, lCurlyBrace, Collections.emptyList(), Collections.emptyList(), rCurlyBrace);
    }
    AstNode compForNode = dictOrSetMaker.getFirstChild(PythonGrammar.COMP_FOR);
    if (compForNode != null) {
      PyComprehensionForTree compFor = compFor(compForNode);
      AstNode colon = dictOrSetMaker.getFirstChild(PythonPunctuator.COLON);
      if (colon != null) {
        PyExpressionTree keyExpression = expression(dictOrSetMaker.getFirstChild(PythonGrammar.TEST));
        PyExpressionTree valueExpression = expression(dictOrSetMaker.getLastChild(PythonGrammar.TEST));
        return new PyDictCompExpressionTreeImpl(lCurlyBrace, keyExpression, toPyToken(colon.getToken()), valueExpression, compFor, rCurlyBrace);
      } else {
        PyExpressionTree resultExpression = expression(dictOrSetMaker.getFirstChild(PythonGrammar.TEST, PythonGrammar.STAR_EXPR));
        return new PyComprehensionExpressionTreeImpl(Tree.Kind.SET_COMPREHENSION, lCurlyBrace, resultExpression, compFor, rCurlyBrace);
      }
    }
    List<PyToken> commas = toPyToken(dictOrSetMaker.getChildren(PythonPunctuator.COMMA).stream().map(AstNode::getToken).collect(Collectors.toList()));
    if (dictOrSetMaker.hasDirectChildren(PythonPunctuator.COLON) || dictOrSetMaker.hasDirectChildren(PythonPunctuator.MUL_MUL)) {
      List<PyKeyValuePairTree> keyValuePairTrees = new ArrayList<>();
      List<AstNode> children = dictOrSetMaker.getChildren();
      int index = 0;
      while (index < children.size()) {
        AstNode currentChild = children.get(index);
        if (currentChild.is(PythonPunctuator.MUL_MUL)) {
          keyValuePairTrees.add(new PyKeyValuePairTreeImpl(toPyToken(currentChild.getToken()), expression(children.get(index + 1))));
          index += 3;
        } else {
          keyValuePairTrees.add(new PyKeyValuePairTreeImpl(expression(currentChild), toPyToken(children.get(index + 1).getToken()), expression(children.get(index + 2))));
          index += 4;
        }
      }
      return new PyDictionaryLiteralTreeImpl(astNode, lCurlyBrace, commas, keyValuePairTrees, rCurlyBrace);
    }
    List<PyExpressionTree> expressions = dictOrSetMaker.getChildren(PythonGrammar.TEST, PythonGrammar.STAR_EXPR).stream().map(this::expression).collect(Collectors.toList());
    return new PySetLiteralTreeImpl(astNode, lCurlyBrace, expressions, commas, rCurlyBrace);
  }

  private PyExpressionTree parenthesized(AstNode atom) {
    PyToken lPar = toPyToken(atom.getFirstChild().getToken());
    PyToken rPar = toPyToken(atom.getLastChild().getToken());

    AstNode yieldNode = atom.getFirstChild(PythonGrammar.YIELD_EXPR);
    if (yieldNode != null) {
      return new PyParenthesizedExpressionTreeImpl(lPar, expression(yieldNode), rPar);
    }

    AstNode testListComp = atom.getFirstChild(PythonGrammar.TESTLIST_COMP);
    if (testListComp == null) {
      return new PyTupleTreeImpl(atom, lPar, Collections.emptyList(), Collections.emptyList(), rPar);
    }

    AstNode compFor = testListComp.getFirstChild(PythonGrammar.COMP_FOR);
    if (compFor != null) {
      return new PyComprehensionExpressionTreeImpl(Tree.Kind.GENERATOR_EXPR, lPar, expression(testListComp.getFirstChild()), compFor(compFor), rPar);
    }
    PyExpressionListTree expressionList = expressionList(testListComp);
    List<AstNode> commas = testListComp.getChildren(PythonPunctuator.COMMA);
    if (commas.isEmpty()) {
      PyExpressionTree expression = expressionList.expressions().get(0);
      return new PyParenthesizedExpressionTreeImpl(lPar, expression, rPar);
    }

    List<PyToken> commaTokens = toPyToken(commas.stream().map(AstNode::getToken).collect(Collectors.toList()));
    return new PyTupleTreeImpl(atom, lPar, expressionList.expressions(), commaTokens, rPar);
  }

  private PyConditionalExpressionTree conditionalExpression(AstNode astNode) {
    List<AstNode> children = astNode.getChildren();
    PyExpressionTree trueExpression = expression(children.get(0));
    PyToken ifToken = toPyToken(astNode.getFirstChild(PythonKeyword.IF).getToken());
    PyExpressionTree condition = expression(children.get(2));
    PyToken elseToken = toPyToken(astNode.getFirstChild(PythonKeyword.ELSE).getToken());
    PyExpressionTree falseExpression = expression(children.get(4));
    return new PyConditionalExpressionTreeImpl(astNode, trueExpression, ifToken, condition, elseToken, falseExpression);
  }

  private PyExpressionTree powerExpression(AstNode astNode) {
    PyExpressionTree expr = expression(astNode.getFirstChild(PythonGrammar.CALL_EXPR, PythonGrammar.ATTRIBUTE_REF, PythonGrammar.ATOM));
    for (AstNode trailer : astNode.getChildren(PythonGrammar.TRAILER)) {
      expr = withTrailer(expr, trailer);
    }
    if (astNode.getFirstChild().is(GenericTokenType.IDENTIFIER)) {
      expr = new PyAwaitExpressionTreeImpl(astNode, toPyToken(astNode.getFirstChild().getToken()), expr);
    }
    AstNode powerOperator = astNode.getFirstChild(PythonPunctuator.MUL_MUL);
    if (powerOperator != null) {
      expr = new PyBinaryExpressionTreeImpl(expr, toPyToken(powerOperator.getToken()), expression(powerOperator.getNextSibling()));
    }
    return expr;
  }

  private PyExpressionTree withTrailer(PyExpressionTree expr, AstNode trailer) {
    AstNode firstChild = trailer.getFirstChild();

    if (firstChild.is(PythonPunctuator.LPARENTHESIS)) {
      AstNode argListNode = trailer.getFirstChild(PythonGrammar.ARGLIST);
      PyToken leftPar = toPyToken(firstChild.getToken());
      PyToken rightPar = toPyToken(trailer.getFirstChild(PythonPunctuator.RPARENTHESIS).getToken());
      return new PyCallExpressionTreeImpl(expr, argList(argListNode), leftPar, rightPar);

    } else if (firstChild.is(PythonPunctuator.LBRACKET)) {
      PyToken leftBracket = toPyToken(trailer.getFirstChild(PythonPunctuator.LBRACKET).getToken());
      PyToken rightBracket = toPyToken(trailer.getFirstChild(PythonPunctuator.RBRACKET).getToken());
      return subscriptionOrSlicing(expr, leftBracket, trailer.getFirstChild(PythonGrammar.SUBSCRIPTLIST), rightBracket);

    } else {
      PyNameTree name = name(trailer.getFirstChild(PythonGrammar.NAME));
      return new PyQualifiedExpressionTreeImpl(trailer, name, expr, toPyToken(trailer.getFirstChild(PythonPunctuator.DOT).getToken()));
    }
  }

  private PyExpressionTree subscriptionOrSlicing(PyExpressionTree expr, PyToken leftBracket, AstNode subscriptList, PyToken rightBracket) {
    List<Tree> slices = new ArrayList<>();
    for (AstNode subscript : subscriptList.getChildren(PythonGrammar.SUBSCRIPT)) {
      AstNode colon = subscript.getFirstChild(PythonPunctuator.COLON);
      if (colon == null) {
        slices.add(expression(subscript.getFirstChild(PythonGrammar.TEST)));
      } else {
        slices.add(sliceItem(subscript));
      }
    }

    // https://docs.python.org/3/reference/expressions.html#slicings
    // "There is ambiguity in the formal syntax here"
    // "a subscription takes priority over the interpretation as a slicing (this is the case if the slice list contains no proper slice)"
    if (slices.stream().anyMatch(s -> Tree.Kind.SLICE_ITEM.equals(s.getKind()))) {
      List<PyToken> separators = toPyToken(subscriptList.getChildren(PythonPunctuator.COMMA).stream()
        .map(AstNode::getToken)
        .collect(Collectors.toList()));
      PySliceListTree sliceList = new PySliceListTreeImpl(subscriptList, slices, separators);
      return new PySliceExpressionTreeImpl(expr, leftBracket, sliceList, rightBracket);

    } else {
      List<PyExpressionTree> expressions = slices.stream().map(PyExpressionTree.class::cast).collect(Collectors.toList());
      PyExpressionListTree subscripts = new PyExpressionListTreeImpl(expressions);
      return new PySubscriptionExpressionTreeImpl(expr, leftBracket, subscripts, rightBracket);
    }
  }

  PySliceItemTree sliceItem(AstNode subscript) {
    AstNode boundSeparator = subscript.getFirstChild(PythonPunctuator.COLON);
    PyExpressionTree lowerBound = sliceBound(boundSeparator.getPreviousSibling());
    PyExpressionTree upperBound = sliceBound(boundSeparator.getNextSibling());
    AstNode strideNode = subscript.getFirstChild(PythonGrammar.SLICEOP);
    PyToken strideSeparator = strideNode == null ? null : toPyToken(strideNode.getToken());
    PyExpressionTree stride = null;
    if (strideNode != null && strideNode.hasDirectChildren(PythonGrammar.TEST)) {
      stride = expression(strideNode.getLastChild());
    }
    return new PySliceItemTreeImpl(subscript, lowerBound, toPyToken(boundSeparator.getToken()), upperBound, strideSeparator, stride);
  }

  @CheckForNull
  private PyExpressionTree sliceBound(@Nullable AstNode node) {
    if (node == null || !node.is(PythonGrammar.TEST)) {
      return null;
    }
    return expression(node);
  }

  private PyExpressionTree listLiteral(AstNode astNode) {
    PyToken leftBracket = toPyToken(astNode.getFirstChild(PythonPunctuator.LBRACKET).getToken());
    PyToken rightBracket = toPyToken(astNode.getFirstChild(PythonPunctuator.RBRACKET).getToken());

    PyExpressionListTree elements;
    AstNode testListComp = astNode.getFirstChild(PythonGrammar.TESTLIST_COMP);
    if (testListComp != null) {
      AstNode compForNode = testListComp.getFirstChild(PythonGrammar.COMP_FOR);
      if (compForNode != null) {
        PyExpressionTree resultExpression = expression(testListComp.getFirstChild(PythonGrammar.TEST, PythonGrammar.STAR_EXPR));
        return new PyComprehensionExpressionTreeImpl(Tree.Kind.LIST_COMPREHENSION, leftBracket, resultExpression, compFor(compForNode), rightBracket);
      }
      elements = expressionList(testListComp);
    } else {
      elements = new PyExpressionListTreeImpl(astNode, Collections.emptyList());
    }
    return new PyListLiteralTreeImpl(astNode, leftBracket, elements, rightBracket);
  }

  private PyComprehensionForTree compFor(AstNode compFor) {
    PyExpressionTree expression = exprListOrTestList(compFor.getFirstChild(PythonGrammar.EXPRLIST));
    PyToken forToken = toPyToken(compFor.getFirstChild(PythonKeyword.FOR).getToken());
    PyToken inToken = toPyToken(compFor.getFirstChild(PythonKeyword.IN).getToken());
    PyExpressionTree iterable = exprListOrTestList(compFor.getFirstChild(PythonGrammar.TESTLIST));
    PyComprehensionClauseTree nested = compClause(compFor.getFirstChild(PythonGrammar.COMP_ITER));
    return new PyComprehensionForTreeImpl(compFor, forToken, expression, inToken, iterable, nested);
  }

  @CheckForNull
  private PyComprehensionClauseTree compClause(@Nullable AstNode node) {
    if (node == null) {
      return null;
    }
    AstNode child = node.getFirstChild();
    if (child.is(PythonGrammar.COMP_FOR)) {
      return compFor(child);
    } else {
      PyExpressionTree condition = expression(child.getFirstChild(PythonGrammar.TEST_NOCOND));
      PyComprehensionClauseTree nestedClause = compClause(child.getFirstChild(PythonGrammar.COMP_ITER));
      PyToken ifToken = toPyToken(child.getFirstChild(PythonKeyword.IF).getToken());
      return new PyComprehensionIfTreeImpl(child, ifToken, condition, nestedClause);
    }
  }

  public PyQualifiedExpressionTree qualifiedExpression(AstNode astNode) {
    PyExpressionTree qualifier = expression(astNode.getFirstChild());
    List<AstNode> names = astNode.getChildren(PythonGrammar.NAME);
    AstNode lastNameNode = astNode.getLastChild();
    for (AstNode nameNode : names) {
      if (nameNode != lastNameNode) {
        qualifier = new PyQualifiedExpressionTreeImpl(astNode, name(nameNode), qualifier, toPyToken(nameNode.getPreviousSibling().getToken()));
      }
    }
    return new PyQualifiedExpressionTreeImpl(astNode, name(lastNameNode), qualifier, toPyToken(lastNameNode.getPreviousSibling().getToken()));
  }

  public PyCallExpressionTree callExpression(AstNode astNode) {
    PyExpressionTree callee = expression(astNode.getFirstChild());
    AstNode argListNode = astNode.getFirstChild(PythonGrammar.ARGLIST);
    PyArgListTree argumentList = argList(argListNode);
    if (argumentList != null) {
      checkGeneratorExpressionInArgument(argumentList.arguments());
    }
    PyToken leftPar = toPyToken(astNode.getFirstChild(PythonPunctuator.LPARENTHESIS).getToken());
    PyToken rightPar = toPyToken(astNode.getFirstChild(PythonPunctuator.RPARENTHESIS).getToken());
    return new PyCallExpressionTreeImpl(astNode, callee, argumentList, leftPar, rightPar);
  }

  @CheckForNull
  private PyArgListTree argList(@Nullable AstNode argList) {
    if (argList != null) {
      List<PyArgumentTree> arguments = argList.getChildren(PythonGrammar.ARGUMENT).stream()
        .map(this::argument)
        .collect(Collectors.toList());
      return new PyArgListTreeImpl(argList, arguments);
    }
    return null;
  }

  /*
   * Post Condition on Generator Expression: parentheses can be omitted on calls with only one argument.
   * https://docs.python.org/3/reference/expressions.html#grammar-token-generator-expression
   */
  private static void checkGeneratorExpressionInArgument(List<PyArgumentTree> arguments) {
    List<PyArgumentTree> nonParenthesizedGeneratorExpressions = arguments.stream()
      .filter(arg -> arg.expression().is(Tree.Kind.GENERATOR_EXPR) && !arg.expression().firstToken().value().equals("("))
      .collect(Collectors.toList());
    if (!nonParenthesizedGeneratorExpressions.isEmpty() && arguments.size() > 1) {
      int line = nonParenthesizedGeneratorExpressions.get(0).firstToken().line();
      throw new RecognitionException(line, "Parse error at line " + line + ": Generator expression must be parenthesized if not sole argument.");
    }
  }

  public PyArgumentTree argument(AstNode astNode) {
    AstNode compFor = astNode.getFirstChild(PythonGrammar.COMP_FOR);
    if (compFor != null) {
      PyExpressionTree expression = expression(astNode.getFirstChild());
      PyComprehensionExpressionTree comprehension =
        new PyComprehensionExpressionTreeImpl(Tree.Kind.GENERATOR_EXPR, expression.firstToken(), expression, compFor(compFor), toPyToken(compFor.getLastToken()));
      return new PyArgumentTreeImpl(astNode, comprehension, null, null);
    }
    AstNode assign = astNode.getFirstChild(PythonPunctuator.ASSIGN);
    PyToken star = astNode.getFirstChild(PythonPunctuator.MUL) == null ? null : toPyToken(astNode.getFirstChild(PythonPunctuator.MUL).getToken());
    PyToken starStar = astNode.getFirstChild(PythonPunctuator.MUL_MUL) == null ? null : toPyToken(astNode.getFirstChild(PythonPunctuator.MUL_MUL).getToken());
    PyExpressionTree arg = expression(astNode.getLastChild(PythonGrammar.TEST));
    if (assign != null) {
      // Keyword in argument list must be an identifier.
      AstNode nameNode = astNode.getFirstChild(PythonGrammar.TEST).getFirstChild(PythonGrammar.ATOM).getFirstChild(PythonGrammar.NAME);
      return new PyArgumentTreeImpl(astNode, name(nameNode), arg, toPyToken(assign.getToken()), star, starStar);
    }
    return new PyArgumentTreeImpl(astNode, arg, star, starStar);
  }

  private PyExpressionTree binaryExpression(AstNode astNode) {
    List<AstNode> children = astNode.getChildren();
    PyExpressionTree result = expression(children.get(0));
    for (int i = 1; i < astNode.getNumberOfChildren(); i += 2) {
      AstNode operator = children.get(i);
      PyExpressionTree rightOperand = expression(operator.getNextSibling());
      AstNode not = operator.getFirstChild(PythonKeyword.NOT);
      PyToken notToken = not == null ? null : toPyToken(not.getToken());
      if (PythonKeyword.IN.equals(operator.getLastToken().getType())) {
        result = new PyInExpressionTreeImpl(result, notToken, toPyToken(operator.getLastToken()), rightOperand);
      } else if (PythonKeyword.IS.equals(operator.getToken().getType())) {
        result = new PyIsExpressionTreeImpl(result, toPyToken(operator.getToken()), notToken, rightOperand);
      } else {
        result = new PyBinaryExpressionTreeImpl(result, toPyToken(operator.getToken()), rightOperand);
      }
    }
    return result;
  }

  public PyLambdaExpressionTree lambdaExpression(AstNode astNode) {
    PyToken lambdaKeyword = toPyToken(astNode.getFirstChild(PythonKeyword.LAMBDA).getToken());
    PyToken colonToken = toPyToken(astNode.getFirstChild(PythonPunctuator.COLON).getToken());
    PyExpressionTree body = expression(astNode.getFirstChild(PythonGrammar.TEST, PythonGrammar.TEST_NOCOND));
    AstNode varArgsListNode = astNode.getFirstChild(PythonGrammar.VARARGSLIST);
    PyParameterListTree argListTree = null;
    if (varArgsListNode != null) {
      List<PyAnyParameterTree> parameters = varArgsListNode.getChildren(PythonGrammar.FPDEF, PythonGrammar.NAME).stream()
        .map(this::parameter).collect(Collectors.toList());
      argListTree = new PyParameterListTreeImpl(varArgsListNode, parameters);
    }

    return new PyLambdaExpressionTreeImpl(astNode, lambdaKeyword, colonToken, body, argListTree);
  }

  private PyAnyParameterTree parameter(AstNode parameter) {
    AstNode prevSibling = parameter.getPreviousSibling();

    if (parameter.is(PythonGrammar.NAME)) {
      return new PyParameterTreeImpl(parameter, toPyToken(prevSibling.getToken()), name(parameter), null, null, null);
    }

    // parameter is FPDEF or TFPDEF

    AstNode paramList = parameter.getFirstChild(PythonGrammar.TFPLIST, PythonGrammar.FPLIST);
    // Python 2 only, PEP 3113: Tuple parameter unpacking removed
    if (paramList != null) {
      List<PyAnyParameterTree> params = paramList.getChildren(PythonGrammar.TFPDEF, PythonGrammar.FPDEF).stream()
        .map(this::parameter)
        .collect(Collectors.toList());
      List<PyToken> commas = toPyToken(paramList.getChildren(PythonPunctuator.COMMA).stream().map(AstNode::getToken).collect(Collectors.toList()));
      return new PyTupleParameterTreeImpl(parameter, params, commas);
    }

    PyToken starOrStarStar = null;
    if (prevSibling != null && prevSibling.is(PythonPunctuator.MUL, PythonPunctuator.MUL_MUL)) {
      starOrStarStar = toPyToken(prevSibling.getToken());
    }

    PyNameTree name = name(parameter.getFirstChild(PythonGrammar.NAME));

    AstNode nextSibling = parameter.getNextSibling();
    PyToken assignToken = null;
    PyExpressionTree defaultValue = null;
    if (nextSibling != null && nextSibling.is(PythonPunctuator.ASSIGN)) {
      assignToken = toPyToken(nextSibling.getToken());
      defaultValue = expression(nextSibling.getNextSibling());
    }

    PyTypeAnnotationTree typeAnnotation = null;
    AstNode testNode = parameter.getFirstChild(PythonGrammar.TEST);
    if (testNode != null) {
      PyToken colonToken = toPyToken(parameter.getFirstChild(PythonPunctuator.COLON).getToken());
      typeAnnotation = new PyTypeAnnotationTreeImpl(colonToken, expression(testNode));
    }

    return new PyParameterTreeImpl(parameter, starOrStarStar, name, typeAnnotation, assignToken, defaultValue);
  }

  private static PyExpressionTree numericLiteral(AstNode astNode) {
    return new PyNumericLiteralTreeImpl(astNode);
  }

  private static PyExpressionTree stringLiteral(AstNode astNode) {
    List<PyStringElementTree> stringElements = astNode.getChildren(PythonTokenType.STRING).stream().map(PyStringElementImpl::new).collect(Collectors.toList());
    return new PyStringLiteralTreeImpl(astNode, stringElements);
  }
}