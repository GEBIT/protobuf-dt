/*
 * Copyright (c) 2011 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.bugs;

import static org.junit.Assert.assertTrue;

import static com.google.eclipse.protobuf.junit.core.IntegrationTestModule.integrationTestModule;
import static com.google.eclipse.protobuf.junit.core.XtextRule.overrideRuntimeModuleWith;

import java.util.List;

import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.eclipse.xtext.validation.Issue;
import org.junit.Rule;
import org.junit.Test;

import com.google.eclipse.protobuf.junit.core.XtextRule;
import com.google.eclipse.protobuf.naming.IProtobufQualifiedNameProvider;
import com.google.eclipse.protobuf.protobuf.Message;
import com.google.inject.Inject;

/**
 * Tests fix for <a href="http://code.google.com/p/protobuf-dt/issues/detail?id=148">Issue 148</a>.
 *
 * @author alruiz@google.com (Alex Ruiz)
 */
public class Issue148_FixDuplicateNameError_Test {
  @Rule public XtextRule xtext = overrideRuntimeModuleWith(integrationTestModule());

  @Inject private IResourceValidator validator;
  @Inject private IProtobufQualifiedNameProvider qualifiedNames;

  // syntax = "proto2";
  //
  // package abc;
  //
  // message abc {}
  @Test public void should_allow_message_named_like_its_package() {
    List<Issue> issues = validator.validate(xtext.resource(), CheckMode.ALL, null);
    assertTrue(issues.toString(), issues.isEmpty());
    org.junit.Assert.assertEquals("abc.abc",
        qualifiedNames.getFullyQualifiedName(xtext.findFirst(Message.class)).toString());
  }
}
