# parley-files-fx

JavaFX user interface for [parley-files](https://github.com/tony-bringardner/parley-files):

- `ConnectionSettingsForm`: a form for any file system's connection settings, drawn from
  `FileSourceFactory.getConnectionSettings()` (the JavaFX twin of parley-files' Swing
  `ConnectionSettingsPanel`).
- `ConnectionDialog`: choose a file system type, fill in its settings and connect, in the
  background.

- `FileSourceChooser`: open and save dialogs for `FileSource`s on any file system (local, or remote
  through "Connect to..."), used like JavaFX's `FileChooser`, which only knows local files.
- The pieces it's built from, for other views (a Finder-like browser is next): `FileTable` (a
  directory's files, listed in the background), `PathBar` and `FolderTree`.

```java
FileSourceChooser fc = new FileSourceChooser();
fc.setInitialDirectory(dir);
FileSource file = fc.showOpenDialog(window);   // null if canceled
```

Java 17 (JavaFX 21). The other Parley modules stay at Java 11.

```java
ConnectionDialog.connect(ownerWindow).ifPresent(factory -> ...);
```

`ConnectionDialogDemo` and `FileSourceChooserDemo` (in the tests) show the dialogs; put
parley-files-sftp, -ftp or -jdbc on their classpath to connect to those.

The browsing model they share with any other UI (listing, sorting, what can be picked, history) is
UI-free and lives in parley-files, package `us.bringardner.parley.files.browse`.
