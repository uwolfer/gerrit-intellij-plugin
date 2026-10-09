// Copyright (C) 2009 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.urswolfer.intellij.plugin.gerrit.util.safehtml;

// based on: https://gerrit.googlesource.com/gerrit/+/master/gerrit-gwtexpui/src/main/java/com/google/gwtexpui/safehtml/client/

/** Safely constructs a {@link SafeHtml}, escaping user provided content. */
@SuppressWarnings("serial")
public class SafeHtmlBuilder extends SafeHtml {
  private static final Impl impl = new ServerImpl();

  private final BufferDirect cb;

  public SafeHtmlBuilder() {
    cb = new BufferDirect();
  }

  /** @return true if this builder has not had an append occur yet. */
  public boolean isEmpty() {
    return cb.isEmpty();
  }

  public SafeHtmlBuilder append(boolean in) {
    cb.append(in);
    return this;
  }

  public SafeHtmlBuilder append(char in) {
    switch (in) {
      case '&':
        cb.append("&amp;");
        break;

      case '>':
        cb.append("&gt;");
        break;

      case '<':
        cb.append("&lt;");
        break;

      case '"':
        cb.append("&quot;");
        break;

      case '\'':
        cb.append("&#39;");
        break;

      default:
        cb.append(in);
        break;
    }
    return this;
  }

  public SafeHtmlBuilder append(int in) {
    cb.append(in);
    return this;
  }

  public SafeHtmlBuilder append(long in) {
    cb.append(in);
    return this;
  }

  public SafeHtmlBuilder append(float in) {
    cb.append(in);
    return this;
  }

  public SafeHtmlBuilder append(double in) {
    cb.append(in);
    return this;
  }

  /** Append already safe HTML as-is, avoiding double escaping. */
  public SafeHtmlBuilder append(SafeHtml in) {
    if (in != null) {
      cb.append(in.asString());
    }
    return this;
  }

  /** Append the string, escaping unsafe characters. */
  public SafeHtmlBuilder append(String in) {
    if (in != null) {
      impl.escapeStr(this, in);
    }
    return this;
  }

  /** Append the string, escaping unsafe characters. */
  public SafeHtmlBuilder append(StringBuilder in) {
    if (in != null) {
      append(in.toString());
    }
    return this;
  }

  /** Append the string, escaping unsafe characters. */
  public SafeHtmlBuilder append(StringBuffer in) {
    if (in != null) {
      append(in.toString());
    }
    return this;
  }

  /** Append the result of toString(), escaping unsafe characters. */
  public SafeHtmlBuilder append(Object in) {
    if (in != null) {
      append(in.toString());
    }
    return this;
  }

  /** Append the string, escaping unsafe characters. */
  public SafeHtmlBuilder append(CharSequence in) {
    if (in != null) {
      escapeCS(this, in);
    }
    return this;
  }

  /** Open an element, appending "{@code <tagName>}" to the buffer. */
  public SafeHtmlBuilder openElement(String tagName) {
    assert isElementName(tagName);
    cb.append("<");
    cb.append(tagName);
    cb.append(">");
    return this;
  }

  /** Append a closing tag for the named element. */
  public SafeHtmlBuilder closeElement(String name) {
    assert isElementName(name);
    cb.append("</");
    cb.append(name);
    cb.append(">");
    return this;
  }

  /** Append "&amp;nbsp;" - a non-breaking space, useful in empty table cells. */
  public SafeHtmlBuilder nbsp() {
    cb.append("&nbsp;");
    return this;
  }

  /** Append "&lt;br /&gt;" - a line break with no attributes */
  public SafeHtmlBuilder br() {
    cb.append("<br />");
    return this;
  }

  /** @return an immutable {@link SafeHtml} representation of the buffer. */
  public SafeHtml toSafeHtml() {
    return new SafeHtmlString(asString());
  }

  @Override
  public String asString() {
    return cb.toString();
  }

  private static void escapeCS(SafeHtmlBuilder b, CharSequence in) {
    for (int i = 0; i < in.length(); i++) {
      b.append(in.charAt(i));
    }
  }

  private static boolean isElementName(String name) {
    return name.matches("^[a-zA-Z][a-zA-Z0-9_-]*$");
  }

  private abstract static class Impl {
    abstract void escapeStr(SafeHtmlBuilder b, String in);
  }

  private static class ServerImpl extends Impl {
    @Override
    void escapeStr(SafeHtmlBuilder b, String in) {
      SafeHtmlBuilder.escapeCS(b, in);
    }
  }
}
