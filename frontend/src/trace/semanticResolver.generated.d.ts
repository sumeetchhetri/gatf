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
// Types for the generated semanticResolver.generated.js (tools/gen-semantic-resolver.mjs; SemanticLocators.java).
export interface SemanticCandidate {
  s: 'testid' | 'role' | 'label';
  v?: string;
  role?: string;
  name?: string;
  n: number;
}
export interface SemanticFallback {
  s: 'id' | 'name' | 'css';
  v: string;
  n: number;
}
export interface SemanticResolver {
  semantic(root: ParentNode | null, spec: Record<string, unknown>, tidAttr?: string, target?: Element): unknown;
  candidates(el: Element, tidAttr?: string): SemanticCandidate[];
  fallback(el: Element): SemanticFallback | null;
}
export function makeSemanticResolver(document: Document, window: Window): SemanticResolver;
