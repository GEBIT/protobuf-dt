/*
 * Copyright (c) 2014 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.model.util;

import org.eclipse.emf.ecore.EObject;

import com.google.eclipse.protobuf.parser.UnknownSyntaxProtobuf;
import com.google.eclipse.protobuf.protobuf.Edition;
import com.google.eclipse.protobuf.protobuf.MisplacedHeader;
import com.google.eclipse.protobuf.protobuf.Protobuf;
import com.google.eclipse.protobuf.protobuf.ProtobufFactory;
import com.google.eclipse.protobuf.protobuf.StringLiteral;
import com.google.eclipse.protobuf.protobuf.Syntax;
import com.google.eclipse.protobuf.util.Strings;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * @author alruiz@google.com (Alex Ruiz)
 */
@Singleton public final class Syntaxes {
  public static final String PROTO2 = "proto2";
  public static final String PROTO3 = "proto3";
  public static final String EDITION_2023 = "2023";
  public static final String EDITION_2024 = "2024";

  @Inject
  private StringLiterals stringLiterals;

  @Inject private ModelObjects modelObjects;

  public boolean isProto2(EObject context) {
    Protobuf root = modelObjects.rootOf(context);
    return root != null && !(root instanceof UnknownSyntaxProtobuf) && root.getEdition() == null
        && !hasMisplacedEdition(root)
        && (root.getSyntax() == null || isSpecifyingProto2Syntax(root.getSyntax()));
  }

  public boolean isProto3(EObject context) {
    Protobuf root = modelObjects.rootOf(context);
    return root != null && !(root instanceof UnknownSyntaxProtobuf) && root.getEdition() == null
        && !hasMisplacedEdition(root)
        && isSpecifyingProto3Syntax(root.getSyntax());
  }

  private boolean hasMisplacedEdition(Protobuf root) {
    for (MisplacedHeader header : root.getMisplacedHeaders()) {
      if (header.isIsEdition()) return true;
    }
    return false;
  }

  public boolean isEditions(EObject context) {
    Protobuf root = modelObjects.rootOf(context);
    if (root == null || root instanceof UnknownSyntaxProtobuf) {
      return false;
    }
    String name = getName(root.getEdition());
    return EDITION_2023.equals(name) || EDITION_2024.equals(name);
  }

  public boolean isEdition2024(EObject context) {
    Protobuf root = modelObjects.rootOf(context);
    return root != null && !(root instanceof UnknownSyntaxProtobuf)
        && EDITION_2024.equals(getName(root.getEdition()));
  }

  public String getName(Edition edition) {
    return edition == null || edition.getName() == null ? null : stringLiterals.getCombinedString(edition.getName());
  }

  public void setName(Edition edition, String name) {
    StringLiteral literal = ProtobufFactory.eINSTANCE.createStringLiteral();
    literal.getChunks().add(Strings.quote(name));
    edition.setName(literal);
  }

  /**
   * Indicates whether the given {@code String} is equal to <code>{@link #PROTO2}</code>.
   * @param s the {@code String} to check.
   * @return {@code true} if the given {@code String} is equal to "proto2," {@code false} otherwise.
   */
  public boolean isSpecifyingProto2Syntax(String s) {
    return PROTO2.equals(s);
  }

  /**
   * Indicates whether the given {@code Syntax} specifies proto2 syntax.
   * @param s the {@code Syntax} to check.
   * @return {@code true} if the given {@code Syntax} specifies "proto2," {@code false} otherwise.
   */
  public boolean isSpecifyingProto2Syntax(Syntax s) {
    return PROTO2.equals(getName(s));
  }

  /**
   * Indicates whether the given {@code String} is equal to <code>{@link #PROTO3}</code>.
   * @param s the {@code String} to check.
   * @return {@code true} if the given {@code String} is equal to "proto3," {@code false} otherwise.
   */
  public boolean isSpecifyingProto3Syntax(String s) {
    return PROTO3.equals(s);
  }

  /**
   * Indicates whether the given {@code Syntax} specifies proto3 syntax.
   * @param s the {@code Syntax} to check.
   * @return {@code true} if the given {@code Syntax} specifies "proto3," {@code false} otherwise.
   */
  public boolean isSpecifyingProto3Syntax(Syntax s) {
    return PROTO3.equals(getName(s));
  }

  /**
   * Returns the name of the syntax specified by this syntax element.
   */
  public String getName(Syntax s) {
    return s == null || s.getName() == null ? null : stringLiterals.getCombinedString(s.getName());
  }

  /**
   * Sets the name of the syntax specified by this syntax element.
   */
  public void setName(Syntax syntax, String name) {
    StringLiteral literal = ProtobufFactory.eINSTANCE.createStringLiteral();
    literal.getChunks().add(Strings.quote(name));
    syntax.setName(literal);
  }
}
