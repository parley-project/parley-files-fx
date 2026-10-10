# parley-files-fx

JavaFX user interface for parley-files (Parley, formerly the Bringardner Java Library). Owner: Tony
Bringardner. **Java 11** like the other Parley modules (JavaFX 17 LTS, which runs on 11), Maven, JUnit 5.
Related repos live next to this one in `/Volumes/Data/eclipse-git/`: parley-parent, parley-core,
parley-io, parley-files, parley-files-sftp, -ftp, -jdbc.

## How Tony wants work done

- **One git branch per batch of work**, created from `master`. Never commit to `master` directly.
- Tony runs `mvn package` himself, then publishes and merges from GitHub Desktop. Don't push.
- `mvn package` must pass with every test running before a batch is called done.
- Commit messages say what changed and why, in plain words.

## Building and testing

- `mvn package` runs the tests. They start JavaFX (`Fx.start()`), so they need a display; where
  JavaFX can't start they're skipped, not failed. There's no headless (Monocle) setup.
- Tests run UI code on the JavaFX thread with `Fx.run` / `Fx.call`.
- Dialogs report to the user through `report(...)`; tests override it to record the messages
  instead of showing alerts that would block.

## How the code is laid out

- Package `us.bringardner.parley.files.fx`.
- The logic every UI shares (which settings apply, validation, the values to connect with) is in
  parley-files (`ConnectionSetting`, `ConnectionSettings`). Keep it there, not here, so the Swing
  and JavaFX UIs behave the same.
- `ConnectionSettingsForm` mirrors parley-files-swing's `ConnectionSettingsPanel`; a change to one
  usually belongs in the other too.
- The browsing logic (listing, sorting, which entries can be picked, back/forward) is UI-free, in
  parley-files' `us.bringardner.parley.files.browse`. The views here (`FileTable`, `PathBar`,
  `FolderTree`, and `ChooserPane` inside `FileSourceChooser`) only show it.
- File system work runs off the JavaFX thread (`Background.EXECUTOR`); results come back with
  `Task` handlers. A view must never list a directory on the JavaFX thread.
- `RecentFileMenu` mirrors parley-files-swing's; the entry logic is parley-files' `RecentFile`, and
  the menu itself fx-widgets' `RecentItemsMenu`.
- `ChooserPane` holds the chooser's workings so tests can drive it without showing a dialog.
