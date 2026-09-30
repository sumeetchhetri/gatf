Subset of the official JSON Schema Test Suite (draft2020-12), https://github.com/json-schema-org/JSON-Schema-Test-Suite
at commit 5b0ee1613e45fcc2bddac00e07c19cd49b00d8a8, MIT licensed (see LICENSE). Used by `SchemaValidatorTest`.

Only the keyword files GATF's contract validator covers are included. Two groups are left out on purpose:
- `not.json` "collect annotations inside a 'not'…" (needs `unevaluatedProperties`)
- `pattern.json` "pattern with Unicode property escape requires unicode mode" (ECMA-262 regex semantics)
