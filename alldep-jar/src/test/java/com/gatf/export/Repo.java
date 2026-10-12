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
package com.gatf.export;

import java.nio.file.Path;
import java.nio.file.Paths;

/** Δ160 test helper: the repo root, whether tests run from the module dir (Maven) or the repo root. */
final class Repo {

    static final Path ROOT = Paths.get("").toAbsolutePath().getFileName().toString().equals("alldep-jar")
            ? Paths.get("..").toAbsolutePath().normalize() : Paths.get("").toAbsolutePath();

    private Repo() {}

    static Path path(String rel) {
        return ROOT.resolve(rel);
    }
}
