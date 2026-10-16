/*
 * Copyright (c) 2011 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.formatting;

import java.util.List;

import org.eclipse.xtext.nodemodel.ICompositeNode;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import com.google.eclipse.protobuf.junit.core.CommentReader;
import com.google.eclipse.protobuf.junit.core.OverrideRuntimeModuleSetup;
import com.google.eclipse.protobuf.junit.core.XtextRule;
import com.google.inject.Module;

/**
 * @author alruiz@google.com (Alex Ruiz)
 */
class CommentReaderRule implements MethodRule {
  private final XtextRule xtext;

  private final CommentReader commentReader;

  private ICompositeNode rootNode;
  private String expectedText;

  static CommentReaderRule overrideRuntimeModuleWith(Module...testModules) {
    return new CommentReaderRule(XtextRule.createWith(new OverrideRuntimeModuleSetup(testModules)));
  }

  private CommentReaderRule(XtextRule xtext) {
    this.xtext = xtext;
    commentReader = new CommentReader();
  }

  @Override public Statement apply(final Statement base, final FrameworkMethod method, Object target) {
    return xtext.apply(new Statement() {
      @Override public void evaluate() throws Throwable {
        rootNode = null;
        expectedText = null;
        List<String> comments = commentReader.commentsIn(method);
        if (comments.size() == 2) {
          rootNode = xtext.resource().getParseResult().getRootNode();
          expectedText = comments.get(1);
        }
        base.evaluate();
      }
    }, method, target);
  }

  ICompositeNode rootNode() {
    return rootNode;
  }

  String expectedText() {
    return expectedText;
  }
}
