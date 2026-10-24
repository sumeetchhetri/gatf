---
title: Visual and accessibility checks
sidebar_position: 13
---

# Visual regression and accessibility checks

## Visual regression

```sel title="home-visual.sel"
open chrome
goto https://shop.example.com
screenshot compare home
screenshot compare "checkout step 2" 0.1 maxdiff 0.5%
screenshot compare dashboard mask id@clock mask css@'.ad-banner'
```

`screenshot compare <name>` compares the full page with the baseline `<testCaseDir>/__screenshots__/<browser>/<script>/<name>.png`. The optional threshold (0 to 1) sets the per-pixel tolerance, `maxdiff` how much of the page may differ, and each `mask` blanks out an element (clocks, ads). Anti-aliasing differences are tolerated.

- A mismatch fails the step with the expected, actual and diff images attached; the report shows a before/after slider.
- The first run writes the baseline. Under `CI=true` a missing baseline fails instead, so commit the `__screenshots__` folder.
- `gatf test --update-snapshots` (`-u`, or `updateSnapshots` in the config) rewrites the baselines after an intended change.

## Accessibility

```sel title="home-a11y.sel"
open chrome
goto https://shop.example.com
a11y check
a11y check impact>=serious
a11y check include id@main exclude css@'.ad-banner' impact>=critical
a11y check report
```

`a11y check` runs the bundled axe-core in the page. Violations at or above the `impact` level fail the step and are listed with their selectors; `report` records them without failing. Results show in the report's **Checks** tab and the attachments gallery.

Syntax: [screenshot compare](../seleasy/REFERENCE.md#compare-the-page-with-its-baseline-screenshot-visual-regression), [a11y check](../seleasy/REFERENCE.md#check-the-page-for-accessibility-violations-axe-core).
