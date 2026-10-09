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
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Window;
import us.bringardner.parley.files.FileSourceFactory;

/**
 * Connects to a file system: choose its type, fill in its connection settings (a
 * {@link ConnectionSettingsForm}), then connect. The values are checked first; connecting
 * runs in the background. The result is the connected factory, or none if canceled.
 * <p>
 * The JavaFX twin of parley-files-swing's FactoryPropertiesDialog.
 */
public class ConnectionDialog extends Dialog<FileSourceFactory> {

	public static final ButtonType TEST = new ButtonType("Test Connection", ButtonData.LEFT);

	private final ComboBox<String> types = new ComboBox<>();
	private final ConnectionSettingsForm form = new ConnectionSettingsForm();
	private final ProgressIndicator busy = new ProgressIndicator();
	private FileSourceFactory factory;
	private boolean connecting;
	// true while the type list is set from code, so it doesn't replace the factory
	private boolean settingType;

	/** Starts with the first file system type. */
	public ConnectionDialog() {
		this(null);
	}

	/**
	 * @param factory the factory to edit, with its current settings; null to start with the
	 *        first file system type
	 */
	public ConnectionDialog(FileSourceFactory factory) {
		setTitle("Connect");
		setResizable(true);
		types.getItems().addAll(fileSystemTypes());
		types.valueProperty().addListener((o, was, is)->{
			if( !settingType && is != null ) {
				setFactory(FileSourceFactory.getFileSourceFactory(is));
			}
		});

		busy.setVisible(false);
		busy.setPrefSize(24, 24);
		HBox top = new HBox(8, new Label("File system:"), types, busy);
		top.setPadding(new Insets(8, 8, 0, 8));
		BorderPane content = new BorderPane(form);
		content.setTop(top);
		getDialogPane().setContent(content);
		getDialogPane().getButtonTypes().addAll(TEST, ButtonType.OK, ButtonType.CANCEL);

		// Test and OK connect first, in the background; they don't close the dialog themselves
		getDialogPane().lookupButton(TEST).addEventFilter(ActionEvent.ACTION, e->{
			e.consume();
			tryConnect().thenAccept(ok->{
				if( ok ) {
					report(AlertType.INFORMATION, "Connected", "Connected to "+this.factory.getTitle());
				}
			});
		});
		getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, e->{
			e.consume();
			tryConnect().thenAccept(ok->{
				if( ok ) {
					setResult(this.factory);
					close();
				}
			});
		});
		setResultConverter(button->null);

		setFactory(factory != null ? factory : FileSourceFactory.getFileSourceFactory(types.getItems().get(0)));
	}

	/** The registered file system types, except the local one (unless it's the only one). */
	static List<String> fileSystemTypes() {
		String local = FileSourceFactory.fileProxyFactory.getTypeId();
		List<String> ret = new ArrayList<>();
		for(String id : FileSourceFactory.getRegisterdFactories()) {
			if( !local.equals(id)) {
				ret.add(id);
			}
		}
		if( ret.isEmpty()) {
			ret.add(local);
		}
		return ret;
	}

	/** Edits factory, with its current settings. */
	public void setFactory(FileSourceFactory factory) {
		this.factory = factory;
		settingType = true;
		try {
			if( !types.getItems().contains(factory.getTypeId())) {
				types.getItems().add(factory.getTypeId());
			}
			types.setValue(factory.getTypeId());
		} finally {
			settingType = false;
		}
		form.setFactory(factory);
		if( getDialogPane().getScene() != null && getDialogPane().getScene().getWindow() != null ) {
			getDialogPane().getScene().getWindow().sizeToScene();
		}
	}

	public FileSourceFactory getFactory() {
		return factory;
	}

	public ConnectionSettingsForm getForm() {
		return form;
	}

	/**
	 * Checks the values, then connects in the background with them.
	 * @return completes on the JavaFX thread: true if connected; false if the values weren't
	 *         usable, connecting failed (both are reported), or a connection is already being made
	 */
	CompletableFuture<Boolean> tryConnect() {
		CompletableFuture<Boolean> ret = new CompletableFuture<>();
		if( connecting ) {
			ret.complete(false);
			return ret;
		}
		List<String> problems = new ArrayList<>(form.validateValues());
		Properties props = form.getConnectionProperties();
		if( problems.isEmpty()) {
			problems.addAll(factory.validateConnection(props));
		}
		if( !problems.isEmpty()) {
			report(AlertType.WARNING, "Check the settings", String.join("\n", problems));
			ret.complete(false);
			return ret;
		}
		FileSourceFactory f = factory;
		setConnecting(true);
		Thread t = new Thread(()->{
			Exception error = null;
			boolean ok = false;
			try {
				ok = f.connect(props);
				if( ok ) {
					f.listRoots();
				} else {
					f.disConnect();
				}
			} catch (Exception e) {
				error = e;
				ok = false;
			}
			boolean connected = ok;
			Exception problem = error;
			Platform.runLater(()->{
				setConnecting(false);
				if( !connected ) {
					report(AlertType.ERROR, "Could not connect", "Could not connect to "+f.getTitle()
						+(problem == null ? "" : "\n"+problem));
				}
				ret.complete(connected);
			});
		}, "Connect "+f.getTypeId());
		t.setDaemon(true);
		t.start();
		return ret;
	}

	private void setConnecting(boolean b) {
		connecting = b;
		busy.setVisible(b);
		for(ButtonType type : new ButtonType[] {TEST, ButtonType.OK}) {
			Node button = getDialogPane().lookupButton(type);
			if( button != null ) {
				button.setDisable(b);
			}
		}
		types.setDisable(b);
		form.setDisable(b);
	}

	/** Tells the user something. Tests override it to see the messages instead. */
	protected void report(AlertType type, String title, String text) {
		Alert alert = new Alert(type, text);
		alert.setHeaderText(title);
		alert.initOwner(getDialogPane().getScene() == null ? null : getDialogPane().getScene().getWindow());
		alert.showAndWait();
	}

	/**
	 * Shows the dialog and waits.
	 * @param owner the window it belongs to, or null
	 * @return the connected factory, or empty if canceled
	 */
	public static Optional<FileSourceFactory> connect(Window owner) {
		ConnectionDialog d = new ConnectionDialog();
		if( owner != null ) {
			d.initOwner(owner);
		}
		return d.showAndWait();
	}
}
