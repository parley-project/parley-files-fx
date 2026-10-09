/**
 * <PRE>
 * 
 * Copyright Tony Bringarder 1998, 2026 <A href="http://bringardner.com/tony">Tony Bringardner</A>
 * 
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       <A href="http://www.apache.org/licenses/LICENSE-2.0">http://www.apache.org/licenses/LICENSE-2.0</A>
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *  </PRE>
 *   
 *   
 *	@author Tony Bringardner   
 *
 *
 * ~version~V000.00.01-V000.00.00-
 */
package us.bringardner.parley.files.fx;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.prefs.Preferences;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.StringConverter;
import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.FileSourceFactory;
import us.bringardner.parley.files.FileSourceFilter;
import us.bringardner.parley.files.browse.DirectoryListing;
import us.bringardner.parley.files.browse.FileEntry;
import us.bringardner.parley.files.browse.NavigationHistory;
import us.bringardner.parley.files.browse.SelectionMode;

/**
 * What a {@link FileSourceChooser} shows: a toolbar (back, forward, up, the path, options),
 * a folder tree beside the files of the current directory, the file type, and when saving a
 * name field. It works out what the user chose; the dialog around it only shows it.
 */
class ChooserPane extends BorderPane {

	enum Mode { OPEN, OPEN_MULTIPLE, SAVE }

	static final String SHOW_HIDDEN_PREFERENCE = "ShowHiddenFiles";
	static final String SHOW_EXTENSIONS_PREFERENCE = "ShowExtensions";

	private final Mode mode;
	private final SelectionMode selectionMode;
	private final NavigationHistory history = new NavigationHistory();
	private final FileTable table = new FileTable();
	private final FolderTree tree = new FolderTree();
	private final PathBar pathBar = new PathBar();
	private final Button back = new Button("‹");
	private final Button forward = new Button("›");
	private final Button up = new Button("↑");
	private final TextField name = new TextField();
	private final ComboBox<FileSourceFilter> filters = new ComboBox<>();
	private final BooleanProperty canAccept = new SimpleBooleanProperty(this, "canAccept");
	private final Preferences prefs;
	// asks the user a yes/no question (title, text); tests replace it
	BiFunction<String,String,Boolean> confirm = (title, text)->true;
	// connects to another file system; tests replace it
	java.util.function.Supplier<Optional<FileSourceFactory>> connect = ()->Optional.empty();
	private boolean navigating;

	ChooserPane(Mode mode, SelectionMode selectionMode, List<FileSourceFilter> filterList, boolean acceptAll,
			FileSource initialDirectory, String initialName, Preferences prefs) {
		this.mode = mode;
		this.selectionMode = mode == Mode.SAVE ? SelectionMode.FILES : selectionMode;
		this.prefs = prefs;

		table.selectionModeProperty().set(this.selectionMode);
		table.setMultipleSelection(mode == Mode.OPEN_MULTIPLE);
		table.showHiddenProperty().set(prefs != null && prefs.getBoolean(SHOW_HIDDEN_PREFERENCE, false));
		table.showExtensionsProperty().set(prefs == null || prefs.getBoolean(SHOW_EXTENSIONS_PREFERENCE, true));
		tree.showHiddenProperty().bind(table.showHiddenProperty());

		// toolbar
		back.setTooltip(new Tooltip("Back"));
		forward.setTooltip(new Tooltip("Forward"));
		up.setTooltip(new Tooltip("Enclosing folder"));
		back.setOnAction(e->showDirectory(history.back()));
		forward.setOnAction(e->showDirectory(history.forward()));
		up.setOnAction(e->goUp());
		MenuButton options = new MenuButton("☰");
		CheckMenuItem hidden = new CheckMenuItem("Show Hidden Files");
		hidden.selectedProperty().bindBidirectional(table.showHiddenProperty());
		CheckMenuItem extensions = new CheckMenuItem("Show File Extensions");
		extensions.selectedProperty().bindBidirectional(table.showExtensionsProperty());
		MenuItem connectItem = new MenuItem("Connect to...");
		connectItem.setOnAction(e->connect.get().ifPresent(this::showFileSystem));
		options.getItems().addAll(hidden, extensions, new SeparatorMenuItem(), connectItem);
		table.showHiddenProperty().addListener((o, was, is)->save(SHOW_HIDDEN_PREFERENCE, is));
		table.showExtensionsProperty().addListener((o, was, is)->save(SHOW_EXTENSIONS_PREFERENCE, is));
		Region gap = new Region();
		HBox.setHgrow(gap, Priority.ALWAYS);
		HBox toolbar = new HBox(4, back, forward, up, pathBar, gap, options);
		toolbar.setPadding(new Insets(4));
		toolbar.setStyle("-fx-alignment: center-left;");

		// navigation
		pathBar.directoryProperty().addListener((o, was, is)->{
			if( !navigating && is != null ) {
				navigate(is);
			}
		});
		tree.getSelectionModel().selectedItemProperty().addListener((o, was, is)->{
			if( !navigating && is != null && is.getValue() != null ) {
				navigate(is.getValue());
			}
		});
		table.setOnOpen(entry->{
			if( entry.isDirectory()) {
				navigate(entry.getFile());
			} else if( DirectoryListing.isSelectable(entry, this.selectionMode, table.filterProperty().get())) {
				if( mode == Mode.SAVE ) {
					name.setText(entry.getName());
				}
				fireAccept();
			}
		});

		// file types
		if( acceptAll || filterList.isEmpty()) {
			filters.getItems().add(null);
		}
		filters.getItems().addAll(filterList);
		filters.setConverter(new StringConverter<FileSourceFilter>() {
			@Override
			public String toString(FileSourceFilter f) {
				return f == null ? "All Files" : f.getDescription();
			}
			@Override
			public FileSourceFilter fromString(String s) {
				return null;
			}
		});
		filters.valueProperty().addListener((o, was, is)->table.filterProperty().set(is));
		filters.getSelectionModel().select(acceptAll && !filterList.isEmpty() ? 1 : 0);

		// bottom: file type, and when saving the name and New Folder
		HBox bottom = new HBox(8);
		bottom.setPadding(new Insets(6, 4, 4, 4));
		bottom.setStyle("-fx-alignment: center-left;");
		if( mode == Mode.SAVE ) {
			name.setText(initialName == null ? "" : initialName);
			name.setPrefColumnCount(24);
			Button newFolder = new Button("New Folder");
			newFolder.setOnAction(e->newFolder());
			bottom.getChildren().addAll(new Label("Save As:"), name, newFolder);
		}
		Region gap2 = new Region();
		HBox.setHgrow(gap2, Priority.ALWAYS);
		bottom.getChildren().addAll(gap2, new Label("Format:"), filters);

		SplitPane split = new SplitPane(tree, table);
		split.setDividerPositions(0.27);
		SplitPane.setResizableWithParent(tree, false);
		setTop(toolbar);
		setCenter(split);
		setBottom(bottom);
		setPrefSize(820, 520);

		// what can be accepted
		table.getTableView().getSelectionModel().getSelectedItems().addListener(
				(javafx.collections.ListChangeListener<FileEntry>) c->updateCanAccept());
		if( mode == Mode.SAVE ) {
			// choosing a file puts its name in the name field
			table.getTableView().getSelectionModel().selectedItemProperty().addListener((o, was, e)->{
				if( e != null && !e.isDirectory()) {
					name.setText(e.getName());
				}
			});
		}
		name.textProperty().addListener((o, was, is)->updateCanAccept());
		table.directoryProperty().addListener((o, was, is)->updateCanAccept());

		FileSource start = initialDirectory != null ? initialDirectory : defaultDirectory();
		showFileSystemOf(start);
		navigate(start);
	}

	private void save(String key, boolean value) {
		if( prefs != null ) {
			prefs.putBoolean(key, value);
		}
	}

	/** The default file system's current directory, else its first root. */
	static FileSource defaultDirectory() {
		FileSourceFactory f = FileSourceFactory.getDefaultFactory();
		try {
			FileSource cwd = f.getCurrentDirectory();
			if( cwd != null ) {
				return cwd;
			}
			return f.listRoots()[0];
		} catch (IOException e) {
			throw new IllegalStateException("No directory to start in", e);
		}
	}

	/** The folder tree shows file's file system: its roots. */
	private void showFileSystemOf(FileSource file) {
		try {
			FileSource[] roots = file.getFileSourceFactory().listRoots();
			tree.setTopFolders(roots == null ? List.of() : Arrays.asList(roots));
		} catch (IOException e) {
			tree.setTopFolders(List.of());
		}
	}

	/** Switches to a newly connected file system, at its first root. */
	void showFileSystem(FileSourceFactory factory) {
		try {
			FileSource[] roots = factory.listRoots();
			if( roots != null && roots.length > 0 ) {
				showFileSystemOf(roots[0]);
				navigate(roots[0]);
			}
		} catch (IOException e) {
			table.refresh();
		}
	}

	/** Shows dir, as a new step in the history. */
	void navigate(FileSource dir) {
		if( dir == null ) {
			return;
		}
		history.go(dir);
		showDirectory(history.current());
	}

	private void showDirectory(FileSource dir) {
		navigating = true;
		try {
			table.setDirectory(dir);
			pathBar.setDirectory(dir);
		} finally {
			navigating = false;
		}
		back.setDisable(!history.canGoBack());
		forward.setDisable(!history.canGoForward());
		FileSource parent = null;
		try {
			parent = dir.getParentFile();
		} catch (IOException e) {
		}
		up.setDisable(parent == null);
	}

	void goUp() {
		try {
			FileSource parent = currentDirectory().getParentFile();
			if( parent != null ) {
				FileSource from = currentDirectory();
				navigate(parent);
				table.select(from);
			}
		} catch (IOException e) {
		}
	}

	void goBack() {
		showDirectory(history.back());
	}

	void goForward() {
		showDirectory(history.forward());
	}

	/** Makes "New Folder" (or "New Folder 2" ...) in the current directory and selects it. */
	FileSource newFolder() {
		try {
			FileSource dir = currentDirectory();
			FileSource folder = dir.getChild("New Folder");
			for(int i=2; folder.exists() && i < 1000; i++) {
				folder = dir.getChild("New Folder "+i);
			}
			if( !folder.mkdir()) {
				throw new IOException("Can't create "+folder.getAbsolutePath());
			}
			table.refresh();
			table.select(folder);
			return folder;
		} catch (IOException e) {
			confirm.apply("Can't create a folder", e.getMessage());
			return null;
		}
	}

	FileSource currentDirectory() {
		return history.current();
	}

	private void updateCanAccept() {
		boolean ok;
		if( mode == Mode.SAVE ) {
			ok = !name.getText().trim().isEmpty() && currentDirectory() != null;
		} else if( selectionMode == SelectionMode.DIRECTORIES ) {
			// with nothing selected, the folder shown is the one chosen
			ok = currentDirectory() != null;
		} else {
			ok = !table.getSelectedEntries().isEmpty();
		}
		canAccept.set(ok);
	}

	BooleanProperty canAcceptProperty() {
		return canAccept;
	}

	// set by the dialog: what happens when the user accepts by double-click or Enter
	Runnable onAccept = ()->{};

	private void fireAccept() {
		updateCanAccept();
		if( canAccept.get()) {
			onAccept.run();
		}
	}

	/**
	 * What the user chose, or null if nothing can be chosen yet. Saving over an existing file
	 * asks first (null if the user says no).
	 */
	List<FileSource> result() {
		updateCanAccept();
		if( !canAccept.get()) {
			return null;
		}
		List<FileSource> ret = new ArrayList<>();
		if( mode == Mode.SAVE ) {
			try {
				FileSource file = currentDirectory().getChild(name.getText().trim());
				if( file.exists() && !confirm.apply("Replace?", "\""+file.getName()+"\" already exists. Replace it?")) {
					return null;
				}
				ret.add(file);
			} catch (IOException e) {
				confirm.apply("Can't save there", e.getMessage());
				return null;
			}
		} else {
			for(FileEntry e : table.getSelectedEntries()) {
				ret.add(e.getFile());
			}
			if( ret.isEmpty() && selectionMode == SelectionMode.DIRECTORIES ) {
				ret.add(currentDirectory());
			}
		}
		return ret;
	}

	// for tests and the dialog
	FileTable table() { return table; }
	FolderTree tree() { return tree; }
	TextField nameField() { return name; }
	ComboBox<FileSourceFilter> filterChoice() { return filters; }
	boolean canGoBack() { return !back.isDisabled(); }
	boolean canGoForward() { return !forward.isDisabled(); }
	boolean canGoUp() { return !up.isDisabled(); }
}
