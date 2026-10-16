/*
 * Copyright (c) 2011 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.junit.core;

import static java.util.regex.Pattern.compile;

import static com.google.eclipse.protobuf.junit.core.GeneratedProtoFiles.ensureParentDirectoryExists;
import static com.google.eclipse.protobuf.junit.core.GeneratedProtoFiles.protoFile;
import static com.google.eclipse.protobuf.util.SystemProperties.lineSeparator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Creates named fixture sources from single- or double-comment file directives, excluding the directive itself.
 *
 * @author alruiz@google.com (Alex Ruiz)
 */
class FileCreator {
  private static final Pattern CREATE_FILE_PATTERN = compile("(?://\\s*)?Create file (.+)");

  static boolean isFileDirective(String line) {
    return CREATE_FILE_PATTERN.matcher(line.trim()).matches();
  }

  File createFileFrom(String comment) {
    try (Scanner scanner = new Scanner(comment)) {
      if (!scanner.hasNextLine()) {
        return null;
      }
      Matcher matcher = CREATE_FILE_PATTERN.matcher(scanner.nextLine().trim());
      if (!matcher.matches()) {
        return null;
      }
      StringBuilder contents = new StringBuilder();
      while (scanner.hasNextLine()) {
        contents.append(scanner.nextLine()).append(lineSeparator());
      }
      return createFile(matcher.group(1).trim(), contents.toString());
    }
  }

  private File createFile(String fileName, String contents) {
    ensureParentDirectoryExists();
    File file = protoFile(fileName);
    if (file.isFile()) {
      file.delete();
    }
    Writer out = null;
    try {
      out = new OutputStreamWriter(new FileOutputStream(file));
      out.write(contents);
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      closeQuietly(out);
    }
    return file;
  }

  private void closeQuietly(Writer out) {
    if (out == null) {
      return;
    }
    try {
      out.close();
    } catch (IOException e) {}
  }
}
