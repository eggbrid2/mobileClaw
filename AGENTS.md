# Project Instructions

## mobileClaw UI Memory

All Android Compose UI work in this project must follow the chat list / conversation home screen style. Treat that screen as the canonical app style.

- Use warm off-white page backgrounds, white cards, warm-gray borders, black primary text, muted gray secondary text, black primary pill buttons, and white or warm-neutral secondary pills.
- Use `LocalClawColors` and the shared `ClawTheme` tokens before adding page-local colors.
- Keep `LocalClawColors.cardAlt` warm neutral, never blue-tinted.
- Use mint `#56D6BA` only for tiny online, working, progress, or AI status cues.
- Do not add blue, purple, cyan, colorful gradients, or accent-tinted page themes for app surfaces.
- Lists should match the conversation list language: leading avatar/tile, bold title, muted subtitle/meta, optional right action, and thin inset dividers.
- Role list, role detail, role edit, settings, skills, workspace, MiniAPP, image, video, group chat, and future pages should all read as the same app.

See `docs/mobile-claw-ui-style.md` before adding or redesigning UI.
