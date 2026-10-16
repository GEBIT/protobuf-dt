/*
 * Copyright (c) 2015 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.validation;

import static com.google.eclipse.protobuf.junit.core.IntegrationTestModule.integrationTestModule;
import static com.google.eclipse.protobuf.junit.core.XtextRule.overrideRuntimeModuleWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import com.google.eclipse.protobuf.junit.core.XtextRule;
import com.google.eclipse.protobuf.protobuf.MapType;
import com.google.inject.Inject;

import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.eclipse.xtext.validation.Issue;
import org.junit.Rule;
import org.junit.Test;

/** Tests map ownership at an extension boundary and inside its group type. */
public class ProtobufJavaValidator_checkMapNotWithinTypeExtension {
  @Rule public XtextRule xtext = overrideRuntimeModuleWith(integrationTestModule());

  @Inject private IResourceValidator validator;

  @Test public void should_reject_a_map_declared_directly_as_an_extension() {
    xtext.parseText("syntax = 'proto2'; message Host { extensions 100 to 200; } "
        + "extend Host { map<string, string> entries = 100; }");
    List<Issue> issues = validate();
    assertEquals(issues.toString(), 1, issues.size());
    assertEquals(Severity.ERROR, issues.get(0).getSeverity());
    assertEquals(EcoreUtil.getURI(xtext.findFirst(MapType.class)), issues.get(0).getUriToProblem());
  }

  @Test public void should_allow_a_map_in_the_body_of_a_group_extension() {
    xtext.parseText("syntax = 'proto2'; message Host { extensions 100 to 200; } "
        + "extend Host { optional group Detail = 100 { map<string, string> entries = 1; } }");
    List<Issue> issues = validate();
    assertTrue(issues.toString(), issues.isEmpty());
  }

  private List<Issue> validate() {
    List<Issue> issues = validator.validate(xtext.resource(), CheckMode.ALL, null);
    assertTrue(xtext.resource().getErrors().toString(), xtext.resource().getErrors().isEmpty());
    return issues;
  }
}
