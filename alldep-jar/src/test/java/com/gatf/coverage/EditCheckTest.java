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
package com.gatf.coverage;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** H6 (Δ156): which edits stay inside their block. */
class EditCheckTest {

    @Test
    void localEdits() {
        for (String ok : new String[] {
                "", "  x = x * 4;", "  if (a) { b(); }", "  s = \"}{\" + '{' + `a${ {k: 1}.k }b`;", "  // a } in a comment",
                "  /* { */ y();", "  const q = 1;", "  let w = () => { return 2; };", "  arr.map(function (v) { return v; });",
                "  obj.var = 1; obj.import();", "  x = a\n    .b();", "  cb(function named() {});"}) {
            assertNull(EditCheck.check(ok), ok);
        }
    }

    @Test
    void editsThatLeaveTheirBlockOrHoist() {
        for (String bad : new String[] {
                "  } else {", "}", "  if (a) {", "  x = (1;", "  x = [1);", "  s = 'it;", "  s = \"a\nb\";", "  /* open",
                "  t = `a${b", "  t = `open", "  var q = 1;", "function f() {}", "  a();\n  function g() {}", "x = 1; async function h() {}",
                "import x from 'y';", "export const z = 1;", "enum E { A }", "namespace N {}", "declare const d: number;"}) {
            assertNotNull(EditCheck.check(bad), bad);
        }
    }
}
