/*
 * Copyright (c) 2014 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.bugs;

import static com.google.eclipse.protobuf.junit.core.UnitTestModule.unitTestModule;
import static com.google.eclipse.protobuf.junit.core.XtextRule.overrideRuntimeModuleWith;
import static org.junit.Assert.assertTrue;

import com.google.eclipse.protobuf.junit.core.XtextRule;
import com.google.inject.Inject;

import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test that the parser correctly handles map syntax.
 */
public class MapSyntaxTest {
  @Rule public XtextRule xtext = overrideRuntimeModuleWith(unitTestModule());

  @Inject private IResourceValidator validator;

  // syntax = "proto2";
  //
  // message Foo {
  //   map<string, Bar> bar_map = 1;
  // }
  // message Bar { optional string value = 1; }
  @Test public void shouldAllowMapFields() {
    assertTrue(validator.validate(xtext.root().eResource(), CheckMode.ALL,
        CancelIndicator.NullImpl).isEmpty());
  }

  // syntax = "proto3";
  // message Foo { map<string, Bar> bar_map = 1; }
  // message Bar { string value = 1; }
  @Test public void shouldAllowProto3MapFields() {
    assertTrue(validator.validate(xtext.root().eResource(), CheckMode.ALL,
        CancelIndicator.NullImpl).isEmpty());
  }

  // syntax = "proto2";
  //
  // message Foo {
  //   optional string map = 1;
  // }
  @Test public void shouldAllowFieldsNamedMap() {
    assertTrue(validator.validate(xtext.root().eResource(), CheckMode.ALL,
        CancelIndicator.NullImpl).isEmpty());
  }
}
