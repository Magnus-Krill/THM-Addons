---
name: thm-release
description: Build and publish a THM Addons GitHub release with its jar, release tag, and player-facing notes. Also use for THM release-notes-only requests.
---

<!--
  This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
  Copyright (c) THM Addons contributors. Credit the devs, keep the link.
  By using this code you agree to the license terms and to keep your repo public.
-->

# THM release

Use this skill only in the THM-Addons repository. A notes-only request ends after drafting the notes; it does not build, tag, push, or publish.

Every release must clearly identify its Minecraft target: use a title such as `Release <version> for Minecraft <minecraft-version>` with a compatibility notice near the top of the notes, or a jar filename such as `THM-Addons-<version>-mc<minecraft-version>.jar`. For notes-only requests, include the target near the top of the notes. Read the target from the release's `gradle/libs.versions.toml` and verify it against the built jar's `fabric.mod.json`; do not infer it from the addon version or the current branch when editing an older release.

1. Inspect `git status --short`, the current branch and remote, local/remote tags, the latest published GitHub release, and `mod-version` in `gradle/libs.versions.toml`. Default to the next patch version after the latest `releaseX.Y.Z` GitHub release unless the user specifies a version. Use `release<version>` as the tag. For publication, stop for a dirty tree, a missing/diverged remote branch, an existing tag/release, or a version that is not newer. For notes-only, exclude and disclose uncommitted work instead.
2. Read `.claude/commands/thm-release.md` and draft notes from the code changes since the previous release tag, not just commit messages. Keep its player-facing format and compare link. For notes-only, print the draft and stop.
3. If `mod-version` differs from the chosen version, show the proposed version change and get approval before editing and committing only that file with a Conventional Commit. Build only after that commit so the jar's embedded Git SHA matches the release commit.
4. Run `./gradlew build --rerun-tasks`, never `runClient`. Set `THM_UPDATE_BRANCH` to the release's source branch for a detached build. Verify `build/libs/THM-Addons-<version>.jar` exists, contains the expected version and source branch in `fabric.mod.json`, and is the release jar. Record the JAR's SHA-256. Stop on any failed check.
5. Show the release title, Minecraft target, tag, commit, branch, jar name and hash, and complete notes. Get explicit approval before pushing or publishing. Then push the release commit if needed and the tag without force, and create a GitHub release with the reviewed title, exact jar and drafted notes. Verify the published release's tag, body, asset, and visible Minecraft target. If a later step fails, report what already exists; do not recreate or overwrite it blindly.

Never include unrelated uncommitted work, silently change the Minecraft target, or publish a jar built from a different commit than the tag.
