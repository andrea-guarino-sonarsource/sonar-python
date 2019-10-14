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

import java.util.ArrayList;
import java.util.List;
import org.sonar.plugins.python.api.tree.SliceList;
import org.sonar.plugins.python.api.tree.Token;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.plugins.python.api.tree.TreeVisitor;

public class SliceListImpl extends PyTree implements SliceList {

  private final List<Tree> slices;
  private final List<Token> separators;

  public SliceListImpl(List<Tree> slices, List<Token> separators) {
    this.slices = slices;
    this.separators = separators;
  }

  @Override
  public List<Tree> slices() {
    return slices;
  }

  @Override
  public List<Token> separators() {
    return separators;
  }

  @Override
  public void accept(TreeVisitor visitor) {
    visitor.visitSliceList(this);
  }

  @Override
  public List<Tree> computeChildren() {
    List<Tree> children = new ArrayList<>();
    int i = 0;
    for (Tree argument : slices) {
      children.add(argument);
      if (i < separators.size()) {
        children.add(separators.get(i));
      }
      i++;
    }
    return children;
  }

  @Override
  public Kind getKind() {
    return Kind.SLICE_LIST;
  }
}
