# Fork notes (Talha24Akram/aniyomi)

Private-use fork. **Never PR or merge into the official repo.**

- `main`   = exact mirror of `upstream/main` (fast-forward only, never commit here)
- `custom` = our work (default branch). Upstream is merged in, never rebased, so our history stays intact.
- Sync: `~/Projects/forks/bin/sync-upstream.sh aniyomi` (daily systemd user timer `forks-sync.timer`). Conflict -> merge aborted + notification.
- Build: `~/Projects/forks/bin/build.sh aniyomi` -> signed arm64 APK in `~/Projects/forks/out/`.
- Installs next to the official app (own application id, own icon colours); signed with the local keystore
  `~/.config/forks/fork.jks` (back it up: losing it means no in-place updates, only uninstall + restore).
- Install on phone: push APK, `pm install -r` (shell uid via the Laptop-Use phone agent).
