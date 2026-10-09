# parley-files-fx

JavaFX user interface for [parley-files](https://github.com/tony-bringardner/parley-files). The Swing
counterpart is [parley-files-swing](https://github.com/tony-bringardner/parley-files-swing).

## Why this library exists

[parley-files](https://github.com/tony-bringardner/parley-files) reads and writes files on any file
system: local, SFTP, FTP, a database. Servers, command-line tools and shells use it with no screen
at all, so it must not need a UI toolkit. But a desktop application also needs to show those file
systems. JavaFX's `FileChooser` only knows local files, and it can't be extended. This library is
that UI, for JavaFX: open and save dialogs that reach remote servers, a form to connect, and a recent
files menu that can reopen a remote file.

The logic every UI needs (which connection settings apply, listing and sorting, copying, what a
recent files entry saves) stays in parley-files, without a UI. This library and parley-files-swing
only show it, so the JavaFX and Swing UIs behave the same and share one recent files list.

It's separate from parley-files-swing because JavaFX needs Java 17 and its own native libraries,
which a Swing application shouldn't have to carry.

**Use parley-files-fx** when your application's UI is JavaFX. For a Swing application, or one that
must run on Java 11, use parley-files-swing.

## What's inside

- `ConnectionSettingsForm`: a form for any file system's connection settings, drawn from
  `FileSourceFactory.getConnectionSettings()` (the JavaFX twin of parley-files-swing's
  `ConnectionSettingsPanel`).
- `ConnectionDialog`: choose a file system type, fill in its settings and connect, in the
  background.
- `FileSourceChooser`: open and save dialogs for `FileSource`s on any file system (local, or remote
  through "Connect to..."), used like JavaFX's `FileChooser`, which only knows local files.
- `RecentFileMenu`: a recent-files menu that can reopen remote files, the JavaFX twin of
  parley-files-swing's. Secrets are never saved; it asks for them, then connects in the background
  and passes the `FileSource` to `setOnOpened`. Built on fx-widgets' `RecentItemsMenu`, with
  parley-files' `RecentFile` entries, so it shares its list with the Swing menu.
- The pieces it's built from, for other views (a Finder-like browser is next): `FileTable` (a
  directory's files, listed in the background), `PathBar` and `FolderTree`.

```java
FileSourceChooser fc = new FileSourceChooser();
fc.setInitialDirectory(dir);
FileSource file = fc.showOpenDialog(window);   // null if canceled
```

```java
ConnectionDialog.connect(ownerWindow).ifPresent(factory -> ...);
```

`ConnectionDialogDemo` and `FileSourceChooserDemo` (in the tests) show the dialogs; put
parley-files-sftp, -ftp or -jdbc on their classpath to connect to those.

The browsing model they share with any other UI (listing, sorting, what can be picked, history) is
UI-free and lives in parley-files, package `us.bringardner.parley.files.browse`.

## Requirements

- Java 17 (JavaFX 21). The other Parley modules stay at Java 11.
- parley-files and [fx-widgets](https://github.com/tony-bringardner/fx-widgets)

## Building

```bash
mvn package
```

The tests start JavaFX, so they need a display; where JavaFX can't start they're skipped.
