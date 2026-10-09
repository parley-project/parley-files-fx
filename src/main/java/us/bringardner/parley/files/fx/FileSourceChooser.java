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

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.stage.Window;
import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.FileSourceFilter;
import us.bringardner.parley.files.browse.SelectionMode;

/**
 * Lets the user choose a {@link FileSource}, on any file system: local, or remote through
 * "Connect to..." in the options menu. Used like JavaFX's FileChooser (which only knows local
 * files):
 *
 * <pre>
 * FileSourceChooser fc = new FileSourceChooser();
 * fc.setInitialDirectory(dir);
 * fc.getFilters().add(f -&gt; f.getName().endsWith(".sh"));
 * FileSource file = fc.showOpenDialog(window);   // null if canceled
 * </pre>
 *
 * Directories are listed in the background, so a slow file system doesn't freeze the window.
 * Whether hidden files and extensions are shown is remembered.
 */
public class FileSourceChooser {

	private String title;
	private FileSource initialDirectory;
	private String initialFileName;
	private final List<FileSourceFilter> filters = new ArrayList<>();
	private boolean acceptAllFilter = true;
	private SelectionMode selectionMode = SelectionMode.FILES;
	private Preferences preferences = Preferences.userNodeForPackage(FileSourceChooser.class);

	public String getTitle() { return title; }
	/** The window title; null for "Open" or "Save". */
	public void setTitle(String title) { this.title = title; }

	public FileSource getInitialDirectory() { return initialDirectory; }
	/** Where to start; null for the default file system's current directory. */
	public void setInitialDirectory(FileSource dir) { this.initialDirectory = dir; }

	public String getInitialFileName() { return initialFileName; }
	/** The name a save dialog starts with. */
	public void setInitialFileName(String name) { this.initialFileName = name; }

	/** The file types the user can choose from (their descriptions are shown); the first is selected. */
	public List<FileSourceFilter> getFilters() { return filters; }

	public boolean isAcceptAllFilter() { return acceptAllFilter; }
	/** Offer "All Files" as well as the filters (on by default). */
	public void setAcceptAllFilter(boolean b) { this.acceptAllFilter = b; }

	public SelectionMode getSelectionMode() { return selectionMode; }
	/** For opening: files (the default), directories, or both. Saving always picks a file. */
	public void setSelectionMode(SelectionMode mode) { this.selectionMode = mode; }

	/** Where hidden files and extensions being shown is remembered; null not to remember. */
	public void setPreferences(Preferences prefs) { this.preferences = prefs; }

	/** @return the file chosen, or null if canceled */
	public FileSource showOpenDialog(Window owner) {
		List<FileSource> ret = show(owner, ChooserPane.Mode.OPEN);
		return ret == null || ret.isEmpty() ? null : ret.get(0);
	}

	/** @return the files chosen, or null if canceled */
	public List<FileSource> showOpenMultipleDialog(Window owner) {
		return show(owner, ChooserPane.Mode.OPEN_MULTIPLE);
	}

	/** @return the file to save to (it may not exist yet), or null if canceled */
	public FileSource showSaveDialog(Window owner) {
		List<FileSource> ret = show(owner, ChooserPane.Mode.SAVE);
		return ret == null || ret.isEmpty() ? null : ret.get(0);
	}

	ChooserPane createPane(ChooserPane.Mode mode) {
		return new ChooserPane(mode, selectionMode, filters, acceptAllFilter, initialDirectory,
				initialFileName, preferences);
	}

	private List<FileSource> show(Window owner, ChooserPane.Mode mode) {
		ChooserPane pane = createPane(mode);
		Dialog<List<FileSource>> dialog = new Dialog<>();
		if( owner != null ) {
			dialog.initOwner(owner);
		}
		dialog.setTitle(title != null ? title : mode == ChooserPane.Mode.SAVE ? "Save" : "Open");
		dialog.setResizable(true);
		ButtonType accept = new ButtonType(mode == ChooserPane.Mode.SAVE ? "Save" : "Open", ButtonData.OK_DONE);
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, accept);
		dialog.getDialogPane().setContent(pane);

		pane.confirm = (heading, text)->{
			Alert a = new Alert(AlertType.CONFIRMATION, text, ButtonType.YES, ButtonType.NO);
			a.setHeaderText(heading);
			a.initOwner(dialog.getDialogPane().getScene().getWindow());
			return a.showAndWait().filter(b->b == ButtonType.YES).isPresent();
		};
		pane.connect = ()->ConnectionDialog.connect(dialog.getDialogPane().getScene().getWindow());

		Node acceptButton = dialog.getDialogPane().lookupButton(accept);
		acceptButton.disableProperty().bind(pane.canAcceptProperty().not());
		List<List<FileSource>> chosen = new ArrayList<>();
		acceptButton.addEventFilter(ActionEvent.ACTION, e->{
			List<FileSource> r = pane.result();
			if( r == null ) {
				e.consume();
			} else {
				chosen.add(r);
			}
		});
		// a double-click or Enter on a file accepts it
		pane.onAccept = ()->((javafx.scene.control.Button) acceptButton).fire();
		dialog.setResultConverter(b->b == accept && !chosen.isEmpty() ? chosen.get(0) : null);
		return dialog.showAndWait().orElse(null);
	}
}
