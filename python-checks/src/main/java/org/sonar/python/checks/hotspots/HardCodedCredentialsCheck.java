/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2020 SonarSource SA
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
package org.sonar.python.checks.hotspots;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import org.sonar.check.Rule;
import org.sonar.check.RuleProperty;
import org.sonar.plugins.python.api.PythonSubscriptionCheck;
import org.sonar.plugins.python.api.SubscriptionContext;
import org.sonar.plugins.python.api.symbols.Symbol;
import org.sonar.plugins.python.api.tree.Argument;
import org.sonar.plugins.python.api.tree.AssignmentStatement;
import org.sonar.plugins.python.api.tree.BinaryExpression;
import org.sonar.plugins.python.api.tree.CallExpression;
import org.sonar.plugins.python.api.tree.DictionaryLiteral;
import org.sonar.plugins.python.api.tree.DictionaryLiteralElement;
import org.sonar.plugins.python.api.tree.Expression;
import org.sonar.plugins.python.api.tree.ExpressionList;
import org.sonar.plugins.python.api.tree.HasSymbol;
import org.sonar.plugins.python.api.tree.KeyValuePair;
import org.sonar.plugins.python.api.tree.Name;
import org.sonar.plugins.python.api.tree.Parameter;
import org.sonar.plugins.python.api.tree.ParameterList;
import org.sonar.plugins.python.api.tree.QualifiedExpression;
import org.sonar.plugins.python.api.tree.RegularArgument;
import org.sonar.plugins.python.api.tree.StringElement;
import org.sonar.plugins.python.api.tree.StringLiteral;
import org.sonar.plugins.python.api.tree.SubscriptionExpression;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.plugins.python.api.tree.Tree.Kind;

@Rule(key = "S2068")
public class HardCodedCredentialsCheck extends PythonSubscriptionCheck {

  private static final String DEFAULT_CREDENTIAL_WORDS = "password,passwd,pwd,passphrase";

  @RuleProperty(
    key = "credentialWords",
    description = "Comma separated list of words identifying potential credentials",
    defaultValue = DEFAULT_CREDENTIAL_WORDS)
  public String credentialWords = DEFAULT_CREDENTIAL_WORDS;

  public static final String MESSAGE = "\"%s\" detected here, review this potentially hard-coded credential.";

  private List<Pattern> variablePatterns = null;
  private List<Pattern> literalPatterns = null;
  private Map<String, Integer> sensitiveArgumentByFQN;

  private Map<String, Integer> sensitiveArgumentByFQN() {
    if (sensitiveArgumentByFQN == null) {
      sensitiveArgumentByFQN = new HashMap<>();
      sensitiveArgumentByFQN.put("mysql.connector.connect", 2);
      sensitiveArgumentByFQN.put("mysql.connector.connection.MySQLConnection", 2);
      sensitiveArgumentByFQN.put("pymysql.connect", 2);
      sensitiveArgumentByFQN.put("pymysql.connections.Connection", 2);
      sensitiveArgumentByFQN.put("psycopg2.connect", 2);
      sensitiveArgumentByFQN.put("pgdb.connect", 2);
      sensitiveArgumentByFQN.put("pg.DB", 5);
      sensitiveArgumentByFQN.put("pg.connect", 5);
      sensitiveArgumentByFQN = Collections.unmodifiableMap(sensitiveArgumentByFQN);
    }
    return sensitiveArgumentByFQN;
  }

  private Stream<Pattern> variablePatterns() {
    if (variablePatterns == null) {
      variablePatterns = toPatterns("");
    }
    return variablePatterns.stream();
  }

  private Stream<Pattern> literalPatterns() {
    if (literalPatterns == null) {
      // Avoid raising on prepared statements
      String credentials = Stream.of(credentialWords.split(","))
        .map(String::trim).collect(Collectors.joining("|"));
      literalPatterns = toPatterns("=(?!.*(" + credentials + "))[^:%'?\\s]+");
    }
    return literalPatterns.stream();
  }

  @Override
  public void initialize(Context context) {
    context.registerSyntaxNodeConsumer(Kind.ASSIGNMENT_STMT, ctx -> handleAssignmentStatement((AssignmentStatement) ctx.syntaxNode(), ctx));
    context.registerSyntaxNodeConsumer(Kind.COMPARISON, ctx -> handleBinaryExpression((BinaryExpression) ctx.syntaxNode(), ctx));
    context.registerSyntaxNodeConsumer(Kind.STRING_LITERAL, ctx -> handleStringLiteral((StringLiteral) ctx.syntaxNode(), ctx));
    context.registerSyntaxNodeConsumer(Kind.CALL_EXPR, ctx -> handleCallExpression((CallExpression) ctx.syntaxNode(), ctx));
    context.registerSyntaxNodeConsumer(Kind.REGULAR_ARGUMENT, ctx -> handleRegularArgument((RegularArgument) ctx.syntaxNode(), ctx));
    context.registerSyntaxNodeConsumer(Kind.PARAMETER_LIST, ctx -> handleParameterList((ParameterList) ctx.syntaxNode(), ctx));
    context.registerSyntaxNodeConsumer(Kind.DICTIONARY_LITERAL, ctx -> handleDictionaryLiteral((DictionaryLiteral) ctx.syntaxNode(), ctx));
  }

  private void handleDictionaryLiteral(DictionaryLiteral dictionaryLiteral, SubscriptionContext ctx) {
    for (DictionaryLiteralElement dictionaryLiteralElement : dictionaryLiteral.elements()) {
      if (dictionaryLiteralElement.is(Kind.KEY_VALUE_PAIR)) {
        KeyValuePair keyValuePair = (KeyValuePair) dictionaryLiteralElement;
        checkKeyValuePair(keyValuePair, ctx);
      }
    }
  }

  private void checkKeyValuePair(KeyValuePair keyValuePair, SubscriptionContext ctx) {
    if (keyValuePair.key().is(Kind.STRING_LITERAL) && keyValuePair.value().is(Kind.STRING_LITERAL)) {
      String matchedCredential = matchedCredential(((StringLiteral) keyValuePair.key()).trimmedQuotesValue(), variablePatterns());
      if (matchedCredential != null) {
        StringLiteral literal = (StringLiteral) keyValuePair.value();
        if (isSuspiciousStringLiteral(literal)) {
          ctx.addIssue(keyValuePair, String.format(MESSAGE, matchedCredential));
        }
      }
    }
  }

  private void handleParameterList(ParameterList parameterList, SubscriptionContext ctx) {
    for (Parameter parameter : parameterList.nonTuple()) {
      Name parameterName = parameter.name();
      Expression defaultValue = parameter.defaultValue();
      if (parameterName == null) {
        continue;
      }
      String matchedCredential = matchedCredential(parameterName.name(), variablePatterns());
      if (matchedCredential != null && defaultValue != null && isSuspiciousStringLiteral(defaultValue)) {
        ctx.addIssue(parameter, String.format(MESSAGE, matchedCredential));
      }
    }
  }

  private void handleRegularArgument(RegularArgument regularArgument, SubscriptionContext ctx) {
    Name keywordArgument = regularArgument.keywordArgument();
    if (keywordArgument != null) {
      String matchedCredential = matchedCredential(keywordArgument.name(), variablePatterns());
      if (matchedCredential != null && isSuspiciousStringLiteral(regularArgument.expression())) {
        ctx.addIssue(regularArgument, String.format(MESSAGE, matchedCredential));
      }
    }
  }

  private void handleCallExpression(CallExpression callExpression, SubscriptionContext ctx) {
    if (callExpression.arguments().isEmpty()) {
      return;
    }
    // Raising issues on pwd.__eq__("literal") calls
    if (callExpression.callee().is(Kind.QUALIFIED_EXPR)) {
      QualifiedExpression qualifiedExpression = (QualifiedExpression) callExpression.callee();
      if (qualifiedExpression.name().name().equals("__eq__")) {
        checkEqualsCall(callExpression, qualifiedExpression, ctx);
      }
    }
    Symbol calleeSymbol = callExpression.calleeSymbol();
    if (calleeSymbol != null && sensitiveArgumentByFQN().containsKey(calleeSymbol.fullyQualifiedName())) {
      checkSensitiveArgument(callExpression, sensitiveArgumentByFQN().get(calleeSymbol.fullyQualifiedName()), ctx);
    }
  }

  private void checkEqualsCall(CallExpression callExpression, QualifiedExpression qualifiedExpression, SubscriptionContext ctx) {
    String matchedCredential = matchedCredentialFromQualifiedExpression(qualifiedExpression);
    if (matchedCredential != null) {
      if (isFirstArgumentAStringLiteral(callExpression)) {
        ctx.addIssue(callExpression, String.format(MESSAGE, matchedCredential));
      }
    } else if (qualifiedExpression.qualifier().is(Kind.STRING_LITERAL)) {
      String matched = firstArgumentCredential(callExpression);
      if (matched != null) {
        ctx.addIssue(callExpression, String.format(MESSAGE, matched));
      }
    }
  }

  private String firstArgumentCredential(CallExpression callExpression) {
    Argument argument = callExpression.arguments().get(0);
    String matchedCredential = null;
    if (argument.is(Kind.REGULAR_ARGUMENT)) {
      RegularArgument regularArgument = (RegularArgument) argument;
      if (regularArgument.expression().is(Kind.NAME)) {
        matchedCredential = matchedCredential(((Name) regularArgument.expression()).name(), variablePatterns());
      }
    }
    return matchedCredential;
  }

  private static boolean isFirstArgumentAStringLiteral(CallExpression callExpression) {
    return callExpression.arguments().get(0).is(Kind.REGULAR_ARGUMENT)
      && ((RegularArgument) callExpression.arguments().get(0)).expression().is(Kind.STRING_LITERAL);
  }

  private String matchedCredentialFromQualifiedExpression(QualifiedExpression qualifiedExpression) {
    String matchedCredential = null;
    if (qualifiedExpression.qualifier().is(Kind.NAME)) {
      matchedCredential = matchedCredential(((Name) qualifiedExpression.qualifier()).name(), variablePatterns());
    }
    return matchedCredential;
  }

  private static void checkSensitiveArgument(CallExpression callExpression, int argNb, SubscriptionContext ctx) {
    for (int i = 0; i < callExpression.arguments().size(); i++) {
      Argument argument = callExpression.arguments().get(i);
      if (argument.is(Kind.REGULAR_ARGUMENT)) {
        RegularArgument regularArgument = (RegularArgument) argument;
        if (regularArgument.keywordArgument() != null) {
          return;
        } else if (i == argNb && regularArgument.expression().is(Kind.STRING_LITERAL)) {
          ctx.addIssue(regularArgument, "Review this potentially hard-coded credential.");
        }
      }
    }
  }

  private void handleStringLiteral(StringLiteral stringLiteral, SubscriptionContext ctx) {
    if (stringLiteral.stringElements().stream().anyMatch(StringElement::isInterpolated)) {
      return;
    }
    String matchedCredential = matchedCredential(stringLiteral.trimmedQuotesValue(), literalPatterns());
    if (matchedCredential != null) {
      ctx.addIssue(stringLiteral, String.format(MESSAGE, matchedCredential));
    }
    if (isURLWithCredentials(stringLiteral)) {
      ctx.addIssue(stringLiteral, "Review this hard-coded URL, which may contain a credential.");
    }
  }

  private static boolean isURLWithCredentials(StringLiteral stringLiteral) {
    try {
      URL url = new URL(stringLiteral.trimmedQuotesValue());
      String userInfo = url.getUserInfo();
      if (userInfo != null && userInfo.matches("\\S+:\\S+")) {
        return true;
      }
    } catch (MalformedURLException e) {
      return false;
    }
    return false;
  }

  private void handleBinaryExpression(BinaryExpression binaryExpression, SubscriptionContext ctx) {
    String matchedCredential = null;
    if (binaryExpression.leftOperand() instanceof HasSymbol && binaryExpression.rightOperand().is(Tree.Kind.STRING_LITERAL)) {
      matchedCredential = credentialSymbolName(((HasSymbol) binaryExpression.leftOperand()).symbol());
    }
    if (binaryExpression.rightOperand() instanceof HasSymbol && binaryExpression.leftOperand().is(Tree.Kind.STRING_LITERAL)) {
      matchedCredential = credentialSymbolName(((HasSymbol) binaryExpression.rightOperand()).symbol());
    }
    if (matchedCredential != null) {
      ctx.addIssue(binaryExpression, String.format(MESSAGE, matchedCredential));
    }
  }

  private void handleAssignmentStatement(AssignmentStatement assignmentStatement, SubscriptionContext ctx) {
    ExpressionList lhs = assignmentStatement.lhsExpressions().get(0);
    Expression expression = lhs.expressions().get(0);

    if (expression instanceof HasSymbol) {
      Symbol symbol = ((HasSymbol) expression).symbol();
      String matchedCredential = credentialSymbolName(symbol);
      if (matchedCredential != null) {
        checkAssignedValue(assignmentStatement, matchedCredential, ctx);
      }
    }

    if (expression.is(Kind.SUBSCRIPTION)) {
      SubscriptionExpression subscriptionExpression = (SubscriptionExpression) expression;
      for (Expression expr : subscriptionExpression.subscripts().expressions()) {
        if (expr.is(Kind.STRING_LITERAL)) {
          String matchedCredential = matchedCredential(((StringLiteral) expr).trimmedQuotesValue(), variablePatterns());
          if (matchedCredential != null) {
            checkAssignedValue(assignmentStatement, matchedCredential, ctx);
          }
        }
      }
    }
  }

  private void checkAssignedValue(AssignmentStatement assignmentStatement, String matchedCredential, SubscriptionContext ctx) {
    Expression assignedValue = assignmentStatement.assignedValue();
    if (isSuspiciousStringLiteral(assignedValue)) {
      ctx.addIssue(assignmentStatement, String.format(MESSAGE, matchedCredential));
    }
  }

  private String credentialSymbolName(@CheckForNull Symbol symbol) {
    if (symbol != null) {
      return matchedCredential(symbol.name(), variablePatterns());
    }
    return null;
  }

  private boolean isSuspiciousStringLiteral(Tree tree) {
    return tree.is(Kind.STRING_LITERAL) && !((StringLiteral) tree).trimmedQuotesValue().isEmpty()
      && !isCredential(((StringLiteral) tree).trimmedQuotesValue(), variablePatterns());
  }

  private static boolean isCredential(String target, Stream<Pattern> patterns) {
    return patterns.anyMatch(pattern -> pattern.matcher(target).find());
  }

  private static String matchedCredential(String target, Stream<Pattern> patterns) {
    Optional<Pattern> matched = patterns.filter(pattern -> pattern.matcher(target).find()).findFirst();
    if (matched.isPresent()) {
      String matchedPattern = matched.get().pattern();
      int suffixStart = matchedPattern.indexOf('=');
      if (suffixStart > 0) {
        return matchedPattern.substring(0, suffixStart);
      } else {
        return matchedPattern;
      }
    }
    return null;
  }

  private List<Pattern> toPatterns(String suffix) {
    return Stream.of(credentialWords.split(","))
      .map(String::trim)
      .map(word -> Pattern.compile(word + suffix, Pattern.CASE_INSENSITIVE))
      .collect(Collectors.toList());
  }
}