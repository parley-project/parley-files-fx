# Changelog

## parley-files-fx 1.0.0 (unreleased)

First version. Runs on Java 11, like the other Parley modules, with JavaFX 17 LTS.

- `ConnectionSettingsForm`: a JavaFX form drawn from a factory's `ConnectionSetting`s, with the
  same behaviour as parley-files' Swing `ConnectionSettingsPanel` (they share `ConnectionSettings`):
  a field of the right kind for each setting, settings that don't apply hidden (and not sent),
  advanced ones behind a check box, Browse for local files, and validation.
- `ConnectionDialog`: choose a file system type, fill in the form, then Test Connection or OK. The
  values are checked first; connecting runs in the background; the result is the connected factory.
- `FileSourceChooser`: open (one or several) and save dialogs for any file system, used like
  JavaFX's `FileChooser`. A toolbar (back, forward, up, the path as buttons, options), a folder tree
  beside the directory's files, file types, and when saving a name field and New Folder. Saving over
  a file asks first. "Connect to..." opens a `ConnectionDialog` and switches to the new file system.
  Whether hidden files and extensions are shown is remembered.
- `RecentFileMenu`: "Recent Files", the JavaFX twin of parley-files-swing's (both are built on
  parley-files' `RecentFile`, and given the same preferences they share a list). Choosing an entry
  asks for the secrets that weren't saved, connects off the JavaFX thread, moves the entry to the
  top and passes the file to `setOnOpened`; a failure is shown and the secrets typed are forgotten.
  Needs fx-widgets.
- `FileTable`, `PathBar`, `FolderTree`: the views the chooser is made of. Directories are listed in
  the background (a listing overtaken by another is dropped); each file's details are read once per
  listing; files that can't be picked are greyed and can't be selected; folders can always be opened.
