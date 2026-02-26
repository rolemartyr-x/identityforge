# IdentityForge (MVP)

Local-first web MVP to validate Identity + Habit + Vote flows.

## Run in Replit
- Tap **Run**
- Open the web preview

## Replit + Git troubleshooting
If Replit says "already up to date" but you still see older files:

```bash
git fetch --all --prune
git status
git branch -vv
git reset --hard origin/main
```

If your PR UI says "binary files are not supported", create a clean branch from `main` and cherry-pick only text-only commits:

```bash
git checkout main
git pull origin main
git checkout -b fix/replit-sync
# Cherry-pick only the commit(s) you want in the PR.
git cherry-pick <commit_sha>
```

## Data storage
Uses SQLite file `identityforge.db` in the workspace by default.
