/*
    Copyright 2013-2026, Sumeet Chhetri

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/
package com.gatf.selenium;

import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

/**
 * Δ163n (bug 3): an xpath locator that is not valid XPath 1.0 SYNTAX is a parse error on its .sel line again (the
 * throwError in FindCommand had been commented out, leaving a stdout warning, so the editor showed no X for it).
 * Conservative, so a script that runs today keeps running:
 * <ul>
 * <li>templated xpaths ({@code $v{x}}, Velocity {@code ${x}}, {@code @{x}}) are not checked: they only become XPath at run time;</li>
 * <li>an unknown function ("Could not find function") stays a warning: JAXP knows only the XPath 1.0 core library;</li>
 * <li>XPath 2.0-style doubled quotes in a literal ({@code 'it''s'}) stay a warning too.</li>
 * </ul>
 */
public final class XpathCheck {

    private XpathCheck() {
    }

    /** The reason {@code xpath} is not valid XPath 1.0 syntax, or null (valid, templated, or only an unknown function). */
    public static String syntaxError(XPathFactory factory, String xpath) {
        if (xpath == null || xpath.indexOf('$') != -1 || xpath.contains("@{")) return null;
        XPathFactory f = factory != null ? factory : XPathFactory.newInstance();
        // seleasy writes the selector into a Java string literal as-is, so \" and \' reach the browser as " and '
        String xp = xpath.replace("\\\"", "\"").replace("\\'", "'");
        try {
            f.newXPath().compile(xp);
            return null;
        } catch (XPathExpressionException e) {
            String m = reason(e);
            if (m.contains("Could not find function")) return null;
            // loop placeholders (xpath@"ddd-@index") are substituted at run time: if the xpath is valid once every @name
            // is a value, it is templated, not wrong
            // ... and XPath 2.0-style doubled quotes ('it''s') are left to the browser, as before
            try {
                f.newXPath().compile(xp.replaceAll("@[A-Za-z_][\\w.]*", "1").replace("''", "").replace("\"\"", ""));
                return null;
            } catch (XPathExpressionException still) {
                return "Invalid xpath expression " + xpath + ": " + m;
            }
        }
    }

    static String reason(Throwable e) {
        Throwable t = e;
        String m = null;
        while (t != null && m == null) {
            m = t.getMessage();
            t = t.getCause();
        }
        if (m == null) return "syntax error";
        int i = m.indexOf("TransformerException: ");
        return i == -1 ? m : m.substring(i + "TransformerException: ".length());
    }
}
