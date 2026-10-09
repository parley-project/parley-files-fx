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
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.FileSourceFilter;
import us.bringardner.parley.files.FileSourceProgress;
import us.bringardner.parley.files.browse.DirectoryListing;
import us.bringardner.parley.files.browse.FileEntry;

/**
 * The files in one directory: name, date modified and size. Listing runs in the background,
 * with a "Loading" overlay; a listing still running when the directory changes is dropped.
 * Files that can't be picked (the selection mode or filter rules them out) are greyed and
 * can't be selected; directories can always be opened, so they're never greyed.
 * <p>
 * Use it on the JavaFX application thread.
 */
public class FileTable extends StackPane {

	private final TableView<FileEntry> table = new TableView<>();
	private final ObjectProperty<FileSource> directory = new SimpleObjectProperty<>(this, "directory");
	private final BooleanProperty showHidden = new SimpleBooleanProperty(this, "showHidden");
	private final BooleanProperty showExtensions = new SimpleBooleanProperty(this, "showExtensions", true);
	private final ObjectProperty<us.bringardner.parley.files.browse.SelectionMode> selectionMode =
			new SimpleObjectProperty<>(this, "selectionMode", us.bringardner.parley.files.browse.SelectionMode.FILES_AND_DIRECTORIES);
	private final ObjectProperty<FileSourceFilter> filter = new SimpleObjectProperty<>(this, "filter");
	private final ReadOnlyBooleanWrapper loading = new ReadOnlyBooleanWrapper(this, "loading");
	private final ReadOnlyObjectWrapper<Throwable> error = new ReadOnlyObjectWrapper<>(this, "error");
	private final ObjectProperty<Consumer<FileEntry>> onOpen = new SimpleObjectProperty<>(this, "onOpen");

	// everything in the directory; the table shows them less the hidden ones, unless showHidden
	private List<FileEntry> all = new ArrayList<>();
	private final ObservableList<FileEntry> shown = FXCollections.observableArrayList();
	private Task<List<FileEntry>> listing;
	private boolean dropScheduled;
	// selected once the listing that has it arrives
	private FileSource pendingSelection;
	private final VBox overlay;
	private final Label message = new Label();

	public FileTable() {
		table.setItems(shown);
		table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

		TableColumn<FileEntry,FileEntry> name = new TableColumn<>("Name");
		name.setCellValueFactory(c->new ReadOnlyObjectWrapper<>(c.getValue()));
		name.setComparator(DirectoryListing.BY_NAME);
		name.setCellFactory(c->new TableCell<>() {
			@Override
			protected void updateItem(FileEntry e, boolean empty) {
				super.updateItem(e, empty);
				if( empty || e == null ) {
					setText(null);
					setGraphic(null);
				} else {
					setText(e.getDisplayName(showExtensions.get()));
					setGraphic(FileIcons.icon(e.isDirectory()));
				}
			}
		});
		name.setPrefWidth(280);

		TableColumn<FileEntry,FileEntry> modified = new TableColumn<>("Date Modified");
		modified.setCellValueFactory(c->new ReadOnlyObjectWrapper<>(c.getValue()));
		modified.setComparator(DirectoryListing.BY_MODIFIED);
		modified.setCellFactory(c->textCell(e->FileIcons.date(e.getLastModified())));
		modified.setPrefWidth(160);

		TableColumn<FileEntry,FileEntry> size = new TableColumn<>("Size");
		size.setCellValueFactory(c->new ReadOnlyObjectWrapper<>(c.getValue()));
		size.setComparator(DirectoryListing.BY_SIZE);
		size.setCellFactory(c->textCell(e->e.isDirectory() ? "--" : FileIcons.size(e.getLength())));
		size.setPrefWidth(90);
		size.setStyle("-fx-alignment: CENTER-RIGHT;");

		table.getColumns().add(name);
		table.getColumns().add(modified);
		table.getColumns().add(size);
		table.getSortOrder().add(name);

		table.setRowFactory(t->{
			TableRow<FileEntry> row = new TableRow<>() {
				@Override
				protected void updateItem(FileEntry e, boolean empty) {
					super.updateItem(e, empty);
					// files that can't be picked are greyed; directories never are, as they can be opened
					setOpacity(empty || e == null || e.isDirectory() || isSelectable(e) ? 1 : 0.45);
				}
			};
			row.setOnMouseClicked(m->{
				if( m.getButton() == MouseButton.PRIMARY && m.getClickCount() == 2 && !row.isEmpty()) {
					open(row.getItem());
				}
			});
			return row;
		});
		table.setOnKeyPressed(k->{
			if( k.getCode() == KeyCode.ENTER ) {
				FileEntry e = table.getSelectionModel().getSelectedItem();
				if( e != null ) {
					open(e);
					k.consume();
				}
			}
		});
		// what can't be picked can't be selected (single clicks, shift-click, select all). The
		// selection can't be changed while it's telling its listeners about a change, so it's
		// put right just after.
		table.getSelectionModel().getSelectedItems().addListener((ListChangeListener<FileEntry>) c->{
			if( !dropScheduled ) {
				dropScheduled = true;
				Platform.runLater(()->{
					dropScheduled = false;
					dropUnselectable();
				});
			}
		});

		ProgressIndicator spinner = new ProgressIndicator();
		spinner.setMaxSize(40, 40);
		overlay = new VBox(8, spinner, new Label("Loading..."));
		overlay.setStyle("-fx-alignment: center; -fx-background-color: rgba(255,255,255,0.6);");
		overlay.setVisible(false);
		overlay.setMouseTransparent(true);
		message.setWrapText(true);
		table.setPlaceholder(message);

		getChildren().addAll(table, overlay);

		directory.addListener((o, was, is)->load(is));
		showHidden.addListener((o, was, is)->updateShown());
		showExtensions.addListener((o, was, is)->table.refresh());
		selectionMode.addListener((o, was, is)->{ table.refresh(); dropUnselectable(); });
		filter.addListener((o, was, is)->{ table.refresh(); dropUnselectable(); });
	}

	private TableCell<FileEntry,FileEntry> textCell(java.util.function.Function<FileEntry,String> text) {
		return new TableCell<>() {
			@Override
			protected void updateItem(FileEntry e, boolean empty) {
				super.updateItem(e, empty);
				setText(empty || e == null ? null : text.apply(e));
			}
		};
	}

	private boolean isSelectable(FileEntry e) {
		return DirectoryListing.isSelectable(e, selectionMode.get(), filter.get());
	}

	private void dropUnselectable() {
		List<Integer> drop = new ArrayList<>();
		for(Integer i : table.getSelectionModel().getSelectedIndices()) {
			if( i != null && i >= 0 && i < table.getItems().size() && !isSelectable(table.getItems().get(i))) {
				drop.add(i);
			}
		}
		for(Integer i : drop) {
			table.getSelectionModel().clearSelection(i);
		}
	}

	void open(FileEntry e) {
		Consumer<FileEntry> handler = onOpen.get();
		if( handler != null ) {
			handler.accept(e);
		}
	}

	/** Lists dir again (its details too). */
	public void refresh() {
		load(directory.get());
	}

	private void load(FileSource dir) {
		if( listing != null ) {
			listing.cancel(true);
			listing = null;
		}
		all = new ArrayList<>();
		shown.clear();
		error.set(null);
		message.setText("");
		if( dir == null ) {
			loading.set(false);
			overlay.setVisible(false);
			return;
		}
		Task<List<FileEntry>> task = new Task<>() {
			@Override
			protected List<FileEntry> call() throws Exception {
				Task<List<FileEntry>> self = this;
				return DirectoryListing.list(dir, new FileSourceProgress() {
					private int max;
					@Override public void setMaximum(int m) { max = m; }
					@Override public int getMaximum() { return max; }
					@Override public void setProgress(int value) { updateProgress(value, Math.max(max, 1)); }
					@Override public boolean isCanceled() { return self.isCancelled(); }
				});
			}
		};
		task.setOnSucceeded(e->{
			if( listing == task ) {
				listing = null;
				all = task.getValue();
				updateShown();
				finishLoading();
			}
		});
		task.setOnFailed(e->{
			if( listing == task ) {
				listing = null;
				Throwable t = task.getException();
				if( !(t instanceof CancellationException)) {
					error.set(t);
					message.setText("Can't list "+dir.getAbsolutePath()+"\n"+t.getMessage());
				}
				finishLoading();
			}
		});
		listing = task;
		loading.set(true);
		overlay.setVisible(true);
		message.setText("");
		Background.EXECUTOR.execute(task);
	}

	private void finishLoading() {
		loading.set(false);
		overlay.setVisible(false);
		if( all.isEmpty() && error.get() == null ) {
			message.setText("This folder is empty.");
		}
		if( pendingSelection != null ) {
			FileSource f = pendingSelection;
			pendingSelection = null;
			select(f);
		}
	}

	private void updateShown() {
		List<FileEntry> keep = new ArrayList<>(table.getSelectionModel().getSelectedItems());
		shown.setAll(DirectoryListing.shown(all, showHidden.get()));
		table.sort();
		for(FileEntry e : keep) {
			int i = shown.indexOf(e);
			if( i >= 0 ) {
				table.getSelectionModel().select(i);
			}
		}
	}

	/**
	 * Selects file and scrolls to it, once it's listed: if its directory is still being listed,
	 * when the listing arrives. Does nothing if it isn't shown or can't be picked.
	 */
	public void select(FileSource file) {
		if( loading.get()) {
			pendingSelection = file;
			return;
		}
		for(int i=0; i < shown.size(); i++) {
			if( DirectoryListing.samePath(shown.get(i).getFile(), file) && isSelectable(shown.get(i))) {
				table.getSelectionModel().clearAndSelect(i);
				table.scrollTo(i);
				return;
			}
		}
	}

	/** The selected entries (only ones that can be picked). */
	public List<FileEntry> getSelectedEntries() {
		List<FileEntry> ret = new ArrayList<>();
		for(FileEntry e : table.getSelectionModel().getSelectedItems()) {
			if( e != null && isSelectable(e)) {
				ret.add(e);
			}
		}
		return ret;
	}

	/** The entries shown, in the order shown. */
	public List<FileEntry> getShownEntries() {
		return new ArrayList<>(shown);
	}

	public TableView<FileEntry> getTableView() {
		return table;
	}

	public void setMultipleSelection(boolean multiple) {
		table.getSelectionModel().setSelectionMode(multiple ? SelectionMode.MULTIPLE : SelectionMode.SINGLE);
	}

	public ObjectProperty<FileSource> directoryProperty() { return directory; }
	public FileSource getDirectory() { return directory.get(); }
	public void setDirectory(FileSource dir) { directory.set(dir); }

	public BooleanProperty showHiddenProperty() { return showHidden; }
	public BooleanProperty showExtensionsProperty() { return showExtensions; }
	public ObjectProperty<us.bringardner.parley.files.browse.SelectionMode> selectionModeProperty() { return selectionMode; }
	public ObjectProperty<FileSourceFilter> filterProperty() { return filter; }

	/** True while the directory is being listed. */
	public ReadOnlyBooleanProperty loadingProperty() { return loading.getReadOnlyProperty(); }
	public boolean isLoading() { return loading.get(); }

	/** Why the directory couldn't be listed, or null. */
	public ReadOnlyObjectProperty<Throwable> errorProperty() { return error.getReadOnlyProperty(); }

	/** Called when an entry is double-clicked or Enter is pressed on it. */
	public ObjectProperty<Consumer<FileEntry>> onOpenProperty() { return onOpen; }
	public void setOnOpen(Consumer<FileEntry> handler) { onOpen.set(handler); }

	// for tests
	String messageText() {
		return message.getText();
	}
}
