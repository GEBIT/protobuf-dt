/*
 * Copyright (c) 2026 GEBIT.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.grammar;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

import com.google.eclipse.protobuf.model.util.Syntaxes;

import org.junit.Test;

/**
 * Tests for supported protobuf editions.
 */
public class Syntaxes_edition_Test {
  @Test public void should_return_edition_2023() {
    assertThat(Syntaxes.EDITION_2023, equalTo("2023"));
  }

  @Test public void should_return_edition_2024() {
    assertThat(Syntaxes.EDITION_2024, equalTo("2024"));
  }
}
