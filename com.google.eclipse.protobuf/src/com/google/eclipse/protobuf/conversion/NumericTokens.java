package com.google.eclipse.protobuf.conversion;

import org.eclipse.xtext.nodemodel.ILeafNode;
import org.eclipse.xtext.nodemodel.INode;

/** Visible datatype tokens, without hidden text between a sign and its magnitude. */
final class NumericTokens {
  private NumericTokens() {}

  static String text(String input, INode node) {
    if (node == null) {
      return input;
    }
    String first = null;
    String magnitude = null;
    Iterable<ILeafNode> leaves = node.getLeafNodes();
    if (leaves == null) {
      return input;
    }
    for (ILeafNode leaf : leaves) {
      if (leaf.isHidden()) {
        continue;
      }
      if (first == null) {
        first = leaf.getText();
      } else if (magnitude == null && "-".equals(first)) {
        magnitude = leaf.getText();
      } else {
        throw new NumberFormatException("Unexpected numeric token.");
      }
    }
    return first == null ? input : magnitude == null ? first : first + magnitude;
  }
}
