# Shared Codex workflow

This folder contains a reusable skill for planning and implementing features across the SBA backend and frontend repositories.

## Install for a teammate

Copy `skills/sba-feature-workflow/` into the teammate's Codex skills directory, normally `~/.codex/skills/` (or `$CODEX_HOME/skills/` if they use a custom Codex home). Keep the skill folder name and its `SKILL.md` together. Restart or refresh Codex if the skill list does not update.

The skill is local to this workspace until shared. Since this `.codex/` folder is outside both Git repositories, it will not be included in either repository's commits. Share this folder directly or move/copy the skill into a tracked repository if you want Git to distribute updates.

Invoke it explicitly with `$sba-feature-workflow`, or ask Codex to plan or implement a cross-repository feature. It is guidance for the agent; Git branches/worktrees and code review still provide change isolation and integration control.
