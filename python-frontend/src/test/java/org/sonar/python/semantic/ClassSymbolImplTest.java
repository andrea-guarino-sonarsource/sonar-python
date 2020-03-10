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
package org.sonar.python.semantic;

import java.util.Collections;
import org.junit.Test;
import org.sonar.plugins.python.api.symbols.Symbol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ClassSymbolImplTest {

  @Test
  public void hasUnresolvedTypeHierarchy() {
    ClassSymbolImpl a = new ClassSymbolImpl("x", null);
    assertThat(a.hasUnresolvedTypeHierarchy()).isFalse();

    ClassSymbolImpl b = new ClassSymbolImpl("x", null);
    b.addSuperClass(new ClassSymbolImpl("s", null));
    assertThat(b.hasUnresolvedTypeHierarchy()).isFalse();

    ClassSymbolImpl c = new ClassSymbolImpl("x", null);
    c.addSuperClass(new SymbolImpl("s", null));
    assertThat(c.hasUnresolvedTypeHierarchy()).isTrue();

    ClassSymbolImpl d = new ClassSymbolImpl("x", null);
    d.addSuperClass(c);
    assertThat(d.hasUnresolvedTypeHierarchy()).isTrue();

    ClassSymbolImpl e = new ClassSymbolImpl("x", null);
    e.setHasSuperClassWithoutSymbol();
    assertThat(e.hasUnresolvedTypeHierarchy()).isTrue();
  }

  @Test
  public void cycle_between_super_classes() {
    ClassSymbolImpl x = new ClassSymbolImpl("x", "x");
    ClassSymbolImpl y = new ClassSymbolImpl("y", "y");
    ClassSymbolImpl z = new ClassSymbolImpl("z", "z");
    x.addSuperClass(y);
    y.addSuperClass(z);
    z.addSuperClass(x);
    assertThat(x.hasUnresolvedTypeHierarchy()).isFalse();
    assertThat(x.isOrExtends("y")).isTrue();
    assertThat(x.isOrExtends("a")).isFalse();
  }

  @Test
  public void should_throw_when_adding_super_class_after_super_classes_were_read() {
    ClassSymbolImpl a = new ClassSymbolImpl("a", null);
    ClassSymbolImpl b = new ClassSymbolImpl("b", null);
    ClassSymbolImpl c = new ClassSymbolImpl("c", null);
    a.addSuperClass(b);
    assertThat(a.superClasses()).containsExactly(b);
    assertThatThrownBy(() -> a.addSuperClass(c)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void should_throw_when_adding_super_class_after_checking_hierarchy() {
    ClassSymbolImpl a = new ClassSymbolImpl("a", null);
    ClassSymbolImpl b = new ClassSymbolImpl("b", null);
    ClassSymbolImpl c = new ClassSymbolImpl("c", null);
    a.addSuperClass(b);
    assertThat(a.hasUnresolvedTypeHierarchy()).isFalse();
    assertThatThrownBy(() -> a.addSuperClass(c)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void resolveMember() {
    assertThat(new ClassSymbolImpl("a", null).resolveMember("foo")).isEmpty();

    ClassSymbolImpl a = new ClassSymbolImpl("a", null);
    Symbol fooA = new SymbolImpl("foo", "a.foo");
    a.addMembers(Collections.singleton(fooA));
    assertThat(a.resolveMember("foo")).contains(fooA);
  }

  @Test
  public void resolve_inherited_member() {
    ClassSymbolImpl a = new ClassSymbolImpl("a", null);
    ClassSymbolImpl b = new ClassSymbolImpl("b", null);
    Symbol fooB = new SymbolImpl("foo", "b.foo");
    b.addMembers(Collections.singleton(fooB));
    a.addSuperClass(b);
    assertThat(a.resolveMember("foo")).contains(fooB);
  }

  @Test
  public void resolve_overridden_member() {
    ClassSymbolImpl a = new ClassSymbolImpl("a", null);
    Symbol fooA = new SymbolImpl("foo", "a.foo");
    a.addMembers(Collections.singleton(fooA));
    ClassSymbolImpl b = new ClassSymbolImpl("b", null);
    Symbol fooB = new SymbolImpl("foo", "b.foo");
    b.addMembers(Collections.singleton(fooB));
    a.addSuperClass(b);
    assertThat(a.resolveMember("foo")).contains(fooA);
  }

  @Test
  public void should_throw_when_adding_member_after_call_to_resolveMember() {
    ClassSymbolImpl a = new ClassSymbolImpl("a", null);
    a.addMembers(Collections.singleton(new SymbolImpl("m1", null)));
    assertThat(a.resolveMember("m1")).isNotEmpty();
    assertThatThrownBy(() -> a.addMembers(Collections.singleton(new SymbolImpl("m2", null)))).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void isOrExtends() {
    ClassSymbolImpl a = new ClassSymbolImpl("a", "mod1.a");
    ClassSymbolImpl b = new ClassSymbolImpl("b", "mod2.b");
    a.addSuperClass(b);
    assertThat(a.isOrExtends("a")).isFalse();
    assertThat(a.isOrExtends("mod1.a")).isTrue();
    assertThat(a.isOrExtends("mod2.b")).isTrue();
    assertThat(a.isOrExtends("mod2.x")).isFalse();

    assertThat(a.isOrExtends(a)).isTrue();
    assertThat(a.isOrExtends(b)).isTrue();
    assertThat(b.isOrExtends(a)).isFalse();
    ClassSymbolImpl c = new ClassSymbolImpl("c", "mod2.c");
    assertThat(a.isOrExtends(c)).isFalse();
  }
}