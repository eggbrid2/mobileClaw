# mobile-claw UI Style

This note records the canonical mobileClaw app style. The chat list / conversation home screen is the source of truth for all Android Compose pages.

Every new page and every redesigned page should feel like the chat list: warm off-white field, black title hierarchy, white grouped list surfaces, warm-gray dividers, black primary pills, white secondary pills, and only tiny mint status cues.

## Palette

- App background: `LocalClawColors.bg`, `#F7F4EE` in day mode or near-black `#050505` in night mode.
- Primary surface: `LocalClawColors.surface`, `#FFFEFA` / white in day mode or `#0D0D0D` in night mode.
- Card surface: `LocalClawColors.card`, usually white.
- Alternate tile surface: `LocalClawColors.cardAlt`, warm neutral `#F1EFE8`; never blue-tinted.
- Primary text: `LocalClawColors.text`.
- Secondary text: `LocalClawColors.subtext`.
- Borders: `LocalClawColors.border`, warm gray `#E8E2D7`, at `0.5.dp` to `0.8.dp`; never cold blue.
- Accent: mint `#56D6BA`, used only for online/working dots, thin progress cues, or tiny state marks.

Do not reintroduce Tech Blue, Violet, Cyan, purple gradients, or accent-tinted page themes. Theme presets must not change the app into a blue/purple/cyan UI.

## Backgrounds

- Default pages are quiet full-screen warm fields, not decorative gradients.
- Top and bottom bars may use `surface` with a thin divider.
- Chat-like and workspace-like pages should prefer `surface` top bars plus `card` / `cardAlt` content sections.
- Large black identity panels are reserved for true profile/hero moments; management pages should avoid them.
- Avoid colorful gradients and multi-hue cards on system pages.

## Buttons

- Primary actions are pill-shaped, filled with `text` and using `bg` as content color.
- Secondary actions are surface pills with a thin border.
- Circular icon buttons are 36-44dp with either `surface`/white background and a thin border.
- Button text is short, bold/semi-bold, and always one line.

## Lists

- Lists should scan like the chat/session UI: leading avatar/icon, title line, muted subtitle, optional right-side status/action.
- Grouped lists use one white `card` section, warm `cardAlt` leading tiles, and `0.5.dp` inset dividers rather than a card per row.
- Rows use compact vertical padding and ellipsized text.
- Avoid always-visible destructive controls in the main row.

## Type

- Screen titles: 15-24sp depending on app bar style.
- List titles: 14-16sp, semi-bold/black.
- Body and metadata: 11-13sp, muted.
- Keep `letterSpacing = 0.sp` and use ellipsis for long text.

## Role Pages

- Role avatar and generated portrait are one concept: `角色形象` / role identity.
- Role list uses the avatar as the identity mark.
- Role detail uses a workspace-style `card` section for role identity; generated portrait and avatar share the same preview tile.
- Role edit groups avatar/image, name, and description in one `card` section, with black/white pill actions.

## Implementation Rules

- Use `LocalClawColors` first. Add page-local colors only for functional error/warning states or small data/status marks.
- Primary action fill is `c.text`; primary action content is `c.bg`.
- Secondary action fill is `c.card` or `c.cardAlt` with `c.border`.
- Material `primary` should remain black/white through `ClawTheme`; use `c.accent` only for the tiny mint AI cue.
- Before finishing UI work, scan for cold theme constants such as `#EAF1FF`, `#D8E3F8`, `#2563EB`, `#7C3AED`, and `#06B6D4`.
