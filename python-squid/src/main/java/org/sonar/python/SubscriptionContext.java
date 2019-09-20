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
package org.sonar.python;

import javax.annotation.Nullable;
import org.sonar.python.api.tree.PyToken;
import org.sonar.python.api.tree.Tree;
import org.sonar.python.semantic.SymbolTable;

public interface SubscriptionContext {
  Tree syntaxNode();

  PythonCheck.PreciseIssue addIssue(Tree element, @Nullable String message);

  PythonCheck.PreciseIssue addIssue(PyToken token, @Nullable String message);

  PythonCheck.PreciseIssue addIssue(PyToken from, PyToken to, @Nullable String message);

  PythonCheck.PreciseIssue addFileIssue(String finalMessage);

  PythonCheck.PreciseIssue addLineIssue(String message, int lineNumber);

  SymbolTable symbolTable();

  PythonFile pythonFile();
}