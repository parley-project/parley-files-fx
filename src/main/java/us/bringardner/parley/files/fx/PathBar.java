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
import java.util.List;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.browse.DirectoryListing;

/**
 * The directory shown and the directories above it, top first, as buttons: click one to go
 * there (the directory property changes). Use it on the JavaFX application thread.
 */
public class PathBar extends HBox {

	private final ObjectProperty<FileSource> directory = new SimpleObjectProperty<>(this, "directory");

	public PathBar() {
		setSpacing(2);
		setStyle("-fx-alignment: center-left;");
		directory.addListener((o, was, is)->rebuild(is));
	}

	private void rebuild(FileSource dir) {
		getChildren().clear();
		if( dir == null ) {
			return;
		}
		List<FileSource> path;
		try {
			path = DirectoryListing.ancestors(dir);
		} catch (IOException e) {
			path = List.of(dir);
		}
		for(int i=0; i < path.size(); i++) {
			FileSource d = path.get(i);
			if( i > 0 ) {
				getChildren().add(new Label("\u203a"));
			}
			Button b = new Button(label(d), FileIcons.icon(true));
			b.getStyleClass().add("path-button");
			b.setFocusTraversable(false);
			b.setOnAction(e->directory.set(d));
			if( i == path.size()-1 ) {
				b.setStyle("-fx-font-weight: bold;");
			}
			getChildren().add(b);
		}
	}

	/** A directory's name, or its whole path for a root ("/", "C:\\"). */
	static String label(FileSource dir) {
		String name = dir.getName();
		return name == null || name.isEmpty() ? dir.getAbsolutePath() : name;
	}

	public ObjectProperty<FileSource> directoryProperty() { return directory; }
	public FileSource getDirectory() { return directory.get(); }
	public void setDirectory(FileSource dir) { directory.set(dir); }
}
