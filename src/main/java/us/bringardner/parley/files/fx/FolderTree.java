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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.browse.DirectoryListing;
import us.bringardner.parley.files.browse.FileEntry;

/**
 * A tree of directories, like a file browser's sidebar. A directory's children are listed in
 * the background the first time it's expanded. Use it on the JavaFX application thread.
 */
public class FolderTree extends TreeView<FileSource> {

	private final BooleanProperty showHidden = new SimpleBooleanProperty(this, "showHidden");

	/** A directory whose children are listed when it's first expanded. */
	final class FolderItem extends TreeItem<FileSource> {
		private boolean listed;
		private boolean listing;
		private final boolean leaf;

		FolderItem(FileSource dir, boolean leaf) {
			super(dir, FileIcons.icon(true));
			this.leaf = leaf;
			if( !leaf ) {
				// a placeholder, so it can be expanded before its children are known
				getChildren().add(new TreeItem<>(null));
			}
			expandedProperty().addListener((o, was, is)->{
				if( is ) {
					list();
				}
			});
		}

		@Override
		public boolean isLeaf() {
			return leaf || (listed && getChildren().isEmpty());
		}

		/** True once its children have been listed. */
		boolean isListed() {
			return listed;
		}

		void list() {
			if( listed || listing ) {
				return;
			}
			listing = true;
			FileSource dir = getValue();
			boolean hidden = showHidden.get();
			Task<List<FileEntry>> task = new Task<>() {
				@Override
				protected List<FileEntry> call() throws Exception {
					return DirectoryListing.directories(dir, hidden, null);
				}
			};
			task.setOnSucceeded(e->{
				List<TreeItem<FileSource>> kids = new ArrayList<>();
				for(FileEntry d : task.getValue()) {
					kids.add(new FolderItem(d.getFile(), false));
				}
				getChildren().setAll(kids);
				listed = true;
				listing = false;
			});
			task.setOnFailed(e->{
				getChildren().clear();
				listed = true;
				listing = false;
			});
			Background.EXECUTOR.execute(task);
		}

		/** Lists again, as directories may have come or gone, or hidden ones are now wanted. */
		void relist() {
			if( listed ) {
				listed = false;
				list();
			}
		}
	}

	public FolderTree() {
		setShowRoot(false);
		setRoot(new TreeItem<>(null));
		setCellFactory(t->new TreeCell<>() {
			@Override
			protected void updateItem(FileSource dir, boolean empty) {
				super.updateItem(dir, empty);
				if( empty ) {
					setText(null);
					setGraphic(null);
				} else if( dir == null ) {
					setText("Loading...");
					setGraphic(null);
				} else {
					setText(PathBar.label(dir));
					setGraphic(getTreeItem() == null ? null : getTreeItem().getGraphic());
				}
			}
		});
		showHidden.addListener((o, was, is)->relistAll(getRoot()));
	}

	private void relistAll(TreeItem<FileSource> item) {
		if( item instanceof FolderItem ) {
			((FolderItem) item).relist();
		}
		for(TreeItem<FileSource> kid : new ArrayList<>(item.getChildren())) {
			relistAll(kid);
		}
	}

	/** Shows these directories at the top level (a file system's roots, say, or favorites). */
	public void setTopFolders(List<FileSource> folders) {
		List<TreeItem<FileSource>> items = new ArrayList<>();
		for(FileSource f : folders) {
			items.add(new FolderItem(f, false));
		}
		getRoot().getChildren().setAll(items);
	}

	/** The selected directory, or null. */
	public FileSource getSelectedFolder() {
		TreeItem<FileSource> item = getSelectionModel().getSelectedItem();
		return item == null ? null : item.getValue();
	}

	public BooleanProperty showHiddenProperty() { return showHidden; }
}
