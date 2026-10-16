/*
 * Copyright (c) 2011 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.conversion;

import static java.util.Arrays.asList;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

import static com.google.eclipse.protobuf.junit.core.UnitTestModule.unitTestModule;
import static com.google.eclipse.protobuf.junit.core.XtextRule.overrideRuntimeModuleWith;

import java.math.BigInteger;
import java.util.Collection;

import org.eclipse.xtext.nodemodel.INode;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.google.eclipse.protobuf.junit.core.XtextRule;

/**
 * Tests for <code>{@link HEXValueConverter#toValue(String, INode)}</code>.
 *
 * @author alruiz@google.com (Alex Ruiz)
 */
@RunWith(Parameterized.class)
public class HEXValueConverter_toValue_Test {
  @Rule public XtextRule xtext = overrideRuntimeModuleWith(unitTestModule());

  private final String input;
  private final BigInteger expected;

  @Parameters
  public static Collection<Object[]> parameters() {
    return asList(new Object[][] {
      { "0x1", "1" },
      { "0xA", "10" },
      { "0xFF", "255" },
      { "-0x80000000", "-2147483648" },
      { "0xffffffffffffffff", "18446744073709551615" },
      { "0x10000000000000000", "18446744073709551616" },
      { "-0xffffffffffffffff", "-18446744073709551615" }
    });
  }

  public HEXValueConverter_toValue_Test(String input, String expected) {
    this.input = input;
    this.expected = new BigInteger(expected);
  }

  private HEXValueConverter converter;
  private INode node;

  @Before public void setUp() {
    node = mock(INode.class);
    converter = xtext.injector().getInstance(HEXValueConverter.class);
  }

  @Test public void should_parse_hexadecimal_number() {
    BigInteger value = converter.toValue(input, node);
    assertThat(value, equalTo(expected));
  }
}
