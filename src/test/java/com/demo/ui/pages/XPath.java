package com.demo.ui.pages;

/** Builds XPath string literals that are safe for any text, including quotes. */
final class XPath {

  private XPath() {}

  static String literal(String text) {
    if (!text.contains("'")) {
      return "'" + text + "'";
    }
    if (!text.contains("\"")) {
      return "\"" + text + "\"";
    }
    return "concat('" + text.replace("'", "', \"'\", '") + "')";
  }
}
