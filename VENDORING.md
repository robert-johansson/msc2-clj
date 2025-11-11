# Working With `external/msc2`

This repository keeps the MSC2 C sources as a git submodule (`external/msc2`). Follow the guidelines below to avoid accidental divergence and to keep the fork (github.com/robert-johansson/MSC2) in sync with this project.

## General Workflow
1. **Edit inside the submodule:** run all builds, experiments, and code edits from `external/msc2/`. Treat it like a normal repository.
2. **Commit to your fork:** when you have a meaningful change (scripts, build tweaks, experiment outputs) run `git status`, `git add …`, and `git commit` inside `external/msc2`, then push to `github.com/robert-johansson/MSC2`.
3. **Update the parent repo:** from the project root run `git add external/msc2` (this records the new submodule HEAD), then commit and push the top-level repo. This two-step flow keeps both histories aligned.

## Gotchas
- **Artifacts:** remove `NAR`, `NAR.dSYM`, `__pycache__`, or other build artifacts before committing; they should not live in git.
- **Submodule URL:** the `.gitmodules` file points to your fork. Do not re-run `git submodule add`; just pull/push to the existing remote.
- **Rebase/merge:** if you need updates from upstream `opennars/OpenNARS-for-Applications`, pull them into the fork first, resolve conflicts there, then bump the submodule pointer here.

## Adding New Scripts/Experiments
1. Write and test under `external/msc2/experiments/`.
2. Commit/push inside the submodule.
3. `git add external/msc2 && git commit` in the root repo so collaborators get the updated pointer.

## Restoring a Clean Tree
- `git submodule update --init --recursive` restores the recorded MSC2 revision.
- To discard local edits in the submodule, run `git status` inside `external/msc2`, `git checkout -- <file>` as needed, or `git reset --hard` (within the submodule only) if you’re sure you want to drop changes.

## Frequently Used Commands
```bash
# inside external/msc2
git fetch origin
git checkout MSC2
git pull --ff-only

# push fork changes
git push origin MSC2

# from project root, record new submodule HEAD
git add external/msc2
git commit -m "chore: update MSC2 submodule"
git push origin main
```

Following this flow keeps experiment scripts and docs versioned in your fork while the parent repo simply records which MSC2 commit to check out.***
