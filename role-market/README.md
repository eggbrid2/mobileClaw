# mobileClaw Role Market

This directory is the static role-template catalog consumed directly by mobileClaw.

- `templates.json`: maintainable source definitions and image prompts.
- `avatars/`: GPT Image 2 generated role avatars.
- `packages/`: installable `.mobileclaw-role` archives.
- `index.json`: downloadable catalog with URLs, Skill dependencies, and SHA-256 checksums.

Rebuild the catalog after changing templates or avatars:

```bash
python3 tools/generate_role_market_avatars.py --workers 2
python3 tools/build_role_market.py
```

Avatar generation uses the locally configured GPT Image 2 plugin. It is resumable: existing avatars are skipped, and `--only ROLE_ID` retries one role without rebuilding the others. Keep worker counts low when the configured Image 2 gateway applies rate limits.

Roleplay templates are unofficial community templates. Character names and source works belong to their respective rights holders. The prompts in this repository are original and do not copy third-party character-card text.
