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
package org.sonar.python.checks;

import java.util.List;
import javax.annotation.Nullable;
import org.sonar.check.Rule;
import org.sonar.plugins.python.api.LocationInFile;
import org.sonar.plugins.python.api.symbols.ClassSymbol;
import org.sonar.plugins.python.api.symbols.FunctionSymbol;
import org.sonar.plugins.python.api.symbols.Symbol;
import org.sonar.plugins.python.api.tree.CallExpression;
import org.sonar.plugins.python.api.tree.Expression;
import org.sonar.plugins.python.api.tree.HasSymbol;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.plugins.python.api.types.InferredType;

import static org.sonar.plugins.python.api.symbols.Symbol.Kind.CLASS;
import static org.sonar.plugins.python.api.symbols.Symbol.Kind.FUNCTION;
import static org.sonar.python.types.InferredTypes.typeClassLocation;

@Rule(key = "S5644")
public class ItemOperationsTypeCheck extends ItemOperationsType {

  @Override
  public boolean isValidSubscription(Expression subscriptionObject, String requiredMethod, @Nullable String classRequiredMethod,
                                     List<LocationInFile> secondaries) {

    if (subscriptionObject.is(Tree.Kind.GENERATOR_EXPR)) {
      return false;
    }
    if (subscriptionObject.is(Tree.Kind.CALL_EXPR)) {
      Symbol subscriptionCalleeSymbol = ((CallExpression) subscriptionObject).calleeSymbol();
      if (subscriptionCalleeSymbol != null && subscriptionCalleeSymbol.is(FUNCTION) && ((FunctionSymbol) subscriptionCalleeSymbol).isAsynchronous()) {
        secondaries.add(((FunctionSymbol) subscriptionCalleeSymbol).definitionLocation());
        return false;
      }
    }
    if (subscriptionObject instanceof HasSymbol) {
      Symbol symbol = ((HasSymbol) subscriptionObject).symbol();
      if (symbol == null || isTypingSymbol(symbol)) {
        return true;
      }
      if (symbol.is(FUNCTION, CLASS)) {
        secondaries.add(symbol.is(FUNCTION) ? ((FunctionSymbol) symbol).definitionLocation() : ((ClassSymbol) symbol).definitionLocation());
        return canHaveMethod(symbol, requiredMethod, classRequiredMethod);
      }
    }
    InferredType type = subscriptionObject.type();
    secondaries.add(typeClassLocation(type));
    return type.canHaveMember(requiredMethod);
  }

  @Override
  public String message(@Nullable String name, String missingMethod) {
    if (name != null) {
      return String.format("Fix this code; \"%s\" does not have a \"%s\" method.", name, missingMethod);
    }
    return String.format("Fix this code; this expression does not have a \"%s\" method.", missingMethod);
  }

  private static boolean isTypingSymbol(Symbol symbol) {
    String fullyQualifiedName = symbol.fullyQualifiedName();
    // avoid FP for typing symbols like 'Awaitable[None]'
    return fullyQualifiedName != null && fullyQualifiedName.startsWith("typing");
  }

  private static boolean canHaveMethod(Symbol symbol, String requiredMethod, @Nullable String classRequiredMethod) {
    if (symbol.is(FUNCTION)) {
      // Avoid FPs for properties
      return ((FunctionSymbol) symbol).hasDecorators();
    }
    ClassSymbol classSymbol = (ClassSymbol) symbol;
    return classSymbol.canHaveMember(requiredMethod)
      || (classRequiredMethod != null && classSymbol.canHaveMember(classRequiredMethod))
      || classSymbol.hasDecorators();
  }
}
