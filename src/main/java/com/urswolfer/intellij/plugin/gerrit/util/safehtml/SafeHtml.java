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

import java.util.List;

/** Immutable string safely placed as HTML without further escaping. */
@SuppressWarnings("UnusedDeclaration")
public abstract class SafeHtml {

    /** @return the existing HTML text, wrapped in a safe buffer. */
    public static SafeHtml asis(final String htmlText) {
        return new SafeHtmlString(htmlText);
    }

    /** Convert bare http:// and https:// URLs into &lt;a href&gt; tags. */
    public SafeHtml linkify() {
        final String part = "(?:" +
                "[a-zA-Z0-9$_.+!*',%;:@=?#/~-]" +
                "|&(?!lt;|gt;)" +
                ")";
        return replaceAll(
                "(https?://" +
                        part + "{2,}" +
                        "(?:[(]" + part + "*" + "[)])*" +
                        part + "*" +
                        ")",
                "<a href=\"$1\">$1</a>");
    }

    /**
     * Apply {@link #linkify()}, and "\n\n" to &lt;p&gt;.
     * Lines that start with whitespace are assumed to be preformatted.
     */
    public SafeHtml wikify() {
        final SafeHtmlBuilder r = new SafeHtmlBuilder();
        for (final String p : linkify().asString().split("\n\n")) {
            if (isPreFormat(p)) {
                r.openElement("pre");
                for (final String line : p.split("\n")) {
                    r.append(asis(line));
                    r.br();
                }
                r.closeElement("pre");

            } else if (isList(p)) {
                wikifyList(r, p);

            } else {
                r.openElement("p");
                r.append(asis(p));
                r.closeElement("p");
            }
        }
        return r.toSafeHtml();
    }

    private void wikifyList(final SafeHtmlBuilder r, final String p) {
        boolean in_ul = false;
        boolean in_p = false;
        for (String line : p.split("\n")) {
            if (line.startsWith("-") || line.startsWith("*")) {
                if (!in_ul) {
                    if (in_p) {
                        in_p = false;
                        r.closeElement("p");
                    }

                    in_ul = true;
                    r.openElement("ul");
                }
                line = line.substring(1).trim();

            } else if (!in_ul) {
                if (!in_p) {
                    in_p = true;
                    r.openElement("p");
                } else {
                    r.append(' ');
                }
                r.append(asis(line));
                continue;
            }

            r.openElement("li");
            r.append(asis(line));
            r.closeElement("li");
        }

        if (in_ul) {
            r.closeElement("ul");
        } else if (in_p) {
            r.closeElement("p");
        }
    }

    private static boolean isPreFormat(final String p) {
        return p.contains("\n ") || p.contains("\n\t") || p.startsWith(" ")
                || p.startsWith("\t");
    }

    private static boolean isList(final String p) {
        return p.contains("\n- ") || p.contains("\n* ") || p.startsWith("- ")
                || p.startsWith("* ");
    }

    /**
     * Replace first occurrence of <code>regex</code> with <code>repl</code> .
     * <p>
     * <b>WARNING:</b> This replacement is being performed against an otherwise
     * safe HTML string. The caller must ensure that the replacement does not
     * introduce cross-site scripting attack entry points.
     *
     * @param regex regular expression pattern to match the substring with.
     * @param repl replacement expression. Capture groups within
     *        <code>regex</code> can be referenced with <code>$<i>n</i></code>.
     * @return a new string, after the replacement has been made.
     */
    public SafeHtml replaceFirst(final String regex, final String repl) {
        return new SafeHtmlString(asString().replaceFirst(regex, repl));
    }

    /**
     * Replace each occurrence of <code>regex</code> with <code>repl</code> .
     * <p>
     * <b>WARNING:</b> This replacement is being performed against an otherwise
     * safe HTML string. The caller must ensure that the replacement does not
     * introduce cross-site scripting attack entry points.
     *
     * @param regex regular expression pattern to match substrings with.
     * @param repl replacement expression. Capture groups within
     *        <code>regex</code> can be referenced with <code>$<i>n</i></code>.
     * @return a new string, after the replacements have been made.
     */
    public SafeHtml replaceAll(final String regex, final String repl) {
        return new SafeHtmlString(asString().replaceAll(regex, repl));
    }

    /**
     * Go through the {@link RegexFindReplace} list, calling
     * {@link #replaceAll(String,String)} on the HTML string for every
     * find/replace pair in the list.
     */
    public SafeHtml replaceAll(final List<RegexFindReplace> findReplaceList) {
        if (findReplaceList == null) {
            return this;
        }
        String html = this.asString();
        for (RegexFindReplace findReplace : findReplaceList) {
            html = html.replaceAll(findReplace.find(), findReplace.replace());
        }
        return new SafeHtmlString(html);
    }

    /** @return a clean HTML string safe for inclusion in any context. */
    public abstract String asString();
}