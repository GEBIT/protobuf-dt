/*
 * Copyright (c) 2011 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.bugs;

import static org.junit.Assert.assertThat;

import static com.google.eclipse.protobuf.junit.IEObjectDescriptions.descriptionsIn;
import static com.google.eclipse.protobuf.junit.core.IntegrationTestModule.integrationTestModule;
import static com.google.eclipse.protobuf.junit.core.XtextRule.overrideRuntimeModuleWith;
import static com.google.eclipse.protobuf.junit.matchers.ContainNames.contain;
import static com.google.eclipse.protobuf.protobuf.ProtobufPackage.Literals.OPTION_SOURCE__TARGET;

import org.eclipse.xtext.scoping.IScope;
import org.junit.Rule;
import org.junit.Test;
import com.google.eclipse.protobuf.junit.core.XtextRule;
import com.google.eclipse.protobuf.protobuf.CustomFieldOption;
import com.google.eclipse.protobuf.scoping.ProtobufScopeProvider;
import com.google.inject.Inject;

/**
 * Tests fix for <a href="http://code.google.com/p/protobuf-dt/issues/detail?id=156">Issue 156</a>.
 *
 * @author alruiz@google.com (Alex Ruiz)
 */
public class Issue156_AddSupportForEnumValueOptions_Test {
  @Rule public XtextRule xtext = overrideRuntimeModuleWith(integrationTestModule());

  @Inject private ProtobufScopeProvider scopeProvider;

  // syntax = "proto2";
  //
  // import 'google/protobuf/descriptor.proto';
  //
  // extend google.protobuf.EnumValueOptions {
  //   optional bool active = 1000;
  // }
  //
  // enum PhoneType {
  //   HOME = 0 [(active) = true];
  // }
  @Test public void should_provide_fields_for_custom_field_option() {
    CustomFieldOption option = xtext.find("active", ")", CustomFieldOption.class);
    IScope scope = scopeProvider.getScope(option.getSource(), OPTION_SOURCE__TARGET);
    assertThat(descriptionsIn(scope), contain("active"));
  }

  @Test public void should_resolve_field_and_enum_value_feature_support_to_distinct_descriptor_fields() {
    xtext.parseText("syntax='proto2'; import 'google/protobuf/descriptor.proto'; "
        + "message M { optional bool field=1 [feature_support={edition_introduced:EDITION_2023}]; } "
        + "enum E { E0=0 [feature_support={edition_introduced:EDITION_2023}]; }");
    java.util.List<org.eclipse.xtext.validation.Issue> issues = xtext.injector()
        .getInstance(org.eclipse.xtext.validation.IResourceValidator.class)
        .validate(xtext.resource(), org.eclipse.xtext.validation.CheckMode.ALL, null);
    org.junit.Assert.assertTrue(issues.toString(), issues.isEmpty());
    java.util.List<com.google.eclipse.protobuf.protobuf.NativeFieldOption> annotations =
        xtext.findAll(com.google.eclipse.protobuf.protobuf.NativeFieldOption.class);
    org.junit.Assert.assertEquals(2, annotations.size());
    org.eclipse.emf.ecore.EObject fieldSupport = annotations.get(0).getSource().getTarget();
    org.eclipse.emf.ecore.EObject valueSupport = annotations.get(1).getSource().getTarget();
    org.junit.Assert.assertNotSame(fieldSupport, valueSupport);
    org.junit.Assert.assertEquals("FieldOptions",
        ((com.google.eclipse.protobuf.protobuf.Message) fieldSupport.eContainer()).getName());
    org.junit.Assert.assertEquals("EnumValueOptions",
        ((com.google.eclipse.protobuf.protobuf.Message) valueSupport.eContainer()).getName());
    org.junit.Assert.assertSame(fieldSupport.eResource(), valueSupport.eResource());
  }
}
