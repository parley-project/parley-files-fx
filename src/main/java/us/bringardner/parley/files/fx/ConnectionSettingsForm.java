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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import us.bringardner.parley.files.ConnectionSetting;
import us.bringardner.parley.files.ConnectionSettings;
import us.bringardner.parley.files.FileSourceFactory;
import us.bringardner.parley.files.credentials.CredentialException;
import us.bringardner.parley.files.credentials.CredentialProvider;
import us.bringardner.parley.files.credentials.CredentialRef;
import us.bringardner.parley.files.credentials.Credentials;

/**
 * A JavaFX form for any factory's {@link ConnectionSetting}s: a field of the right kind for
 * each, the settings that don't apply hidden, and the advanced ones behind a check box.
 * The JavaFX twin of parley-files-swing's ConnectionSettingsPanel; both work from the same
 * descriptions and {@link ConnectionSettings}, so they behave the same.
 * <p>
 * Use it on the JavaFX application thread.
 */
public class ConnectionSettingsForm extends VBox {

	/** One setting's label and field. */
	private static final class Row {
		final ConnectionSetting setting;
		final Label label;
		final Node field;
		final Control editor;
		/** For a secret: store a typed password in the keychain; null when there's no keychain. */
		CheckBox saveToKeychain;

		Row(ConnectionSetting setting, Label label, Node field, Control editor) {
			this.setting = setting;
			this.label = label;
			this.field = field;
			this.editor = editor;
		}

		String value() {
			if( editor instanceof TextArea ) {
				return ((TextArea) editor).getText();
			} else if( editor instanceof TextInputControl ) {
				// a TextField or PasswordField
				TextInputControl text = (TextInputControl) editor;
				return editor instanceof PasswordField ? text.getText() : text.getText().trim();
			} else if( editor instanceof CheckBox ) {
				return ""+((CheckBox) editor).isSelected();
			} else if( editor instanceof ComboBox<?> ) {
				Object item = ((ComboBox<?>) editor).getValue();
				return item == null ? "" : item.toString();
			}
			return "";
		}
	}

	private List<ConnectionSetting> settings = new ArrayList<>();
	private final Map<String,Row> rows = new LinkedHashMap<>();
	// values of settings without a field (hidden ones, and properties nobody described)
	private final Properties carried = new Properties();
	private final GridPane grid = new GridPane();
	private final CheckBox showAdvanced = new CheckBox("Show advanced settings");
	private final Label none = new Label("There are no connection settings for this file system.");
	/** The factory's type id, for the keychain entry a typed password is saved as; null if unknown. */
	private String factoryId;

	public ConnectionSettingsForm() {
		setSpacing(8);
		setPadding(new Insets(8));
		grid.setHgap(8);
		grid.setVgap(6);
		ColumnConstraints labels = new ColumnConstraints();
		labels.setHalignment(HPos.RIGHT);
		ColumnConstraints fields = new ColumnConstraints();
		fields.setHgrow(Priority.ALWAYS);
		fields.setMinWidth(260);
		grid.getColumnConstraints().addAll(labels, fields);
		showAdvanced.selectedProperty().addListener((o, was, is)->updateVisibility());
		getChildren().addAll(grid, showAdvanced);
	}

	/** Shows a form for settings, filled from values (missing ones get their defaults). */
	public void setSettings(List<ConnectionSetting> settings, Properties values) {
		this.settings = new ArrayList<>(settings);
		rows.clear();
		carried.clear();
		grid.getChildren().clear();
		Properties initial = ConnectionSettings.initialValues(settings, values);
		if( values != null ) {
			for(String key : values.stringPropertyNames()) {
				if( ConnectionSettings.find(settings, key) == null ) {
					carried.setProperty(key, values.getProperty(key));
				}
			}
		}
		for(ConnectionSetting s : settings) {
			String value = initial.getProperty(s.key(), "");
			if( s.kind() == ConnectionSetting.Kind.HIDDEN ) {
				carried.setProperty(s.key(), value);
				continue;
			}
			rows.put(s.key(), createRow(s, value));
		}
		boolean anyAdvanced = settings.stream().anyMatch(s->s.advanced() && s.kind() != ConnectionSetting.Kind.HIDDEN);
		showAdvanced.setVisible(anyAdvanced);
		showAdvanced.setManaged(anyAdvanced);
		updateVisibility();
	}

	/** Fills the form with factory's settings and current values. */
	public void setFactory(FileSourceFactory factory) {
		factoryId = factory.getTypeId();
		setSettings(factory.getConnectionSettings(), factory.getConnectProperties());
	}

	private Row createRow(ConnectionSetting s, String value) {
		Label label = new Label(s.label()+(s.required() ? " *" : "")+":");
		// labels keep their full width; the fields take what's left
		label.setMinWidth(Region.USE_PREF_SIZE);
		Control editor;
		Node field;
		switch (s.kind()) {
		case SECRET: {
			// a reference (@keychain:...) isn't a secret: it's shown in clear, a password masked.
			// Two fields share one text; the one that fits the value is shown.
			PasswordField pw = new PasswordField();
			TextField clear = new TextField();
			clear.textProperty().bindBidirectional(pw.textProperty());
			pw.setText(value);
			StackPane both = new StackPane(pw, clear);
			Button fromStore = new Button("From store...");
			fromStore.setTooltip(new Tooltip("Use a secret kept in a credential store, such as @keychain:parley/prod-db"));
			fromStore.setOnAction(e->chooseReference(pw, fromStore));
			HBox line = new HBox(4, both, fromStore);
			HBox.setHgrow(both, Priority.ALWAYS);
			VBox box = new VBox(4, line);
			CheckBox save = keychainCanStore() ? new CheckBox("Save to keychain") : null;
			if( save != null ) {
				save.setTooltip(new Tooltip("Keep the password in the keychain, and only a reference to it here"));
				box.getChildren().add(save);
			}
			Runnable show = ()->{
				boolean ref = CredentialRef.isReference(pw.getText());
				boolean moveFocus = (ref ? pw : clear).isFocused();
				clear.setVisible(ref);
				pw.setVisible(!ref);
				if( moveFocus ) {
					TextField now = ref ? clear : pw;
					now.requestFocus();
					now.positionCaret(now.getText().length());
				}
				if( save != null ) {
					save.setDisable(ref || pw.getText().isEmpty());
				}
			};
			show.run();
			pw.textProperty().addListener((o, was, is)->show.run());
			editor = pw;
			field = box;
			Row row = new Row(s, label, field, editor);
			row.saveToKeychain = save;
			pw.textProperty().addListener((o, was, is)->updateVisibility());
			if( !s.description().isEmpty()) {
				label.setTooltip(new Tooltip(s.description()));
				pw.setTooltip(new Tooltip(s.description()));
				clear.setTooltip(new Tooltip(s.description()));
			}
			label.setLabelFor(pw);
			return row;
		}
		case MULTILINE_SECRET: {
			TextArea area = new TextArea(value);
			area.setPrefRowCount(6);
			area.setPrefColumnCount(40);
			editor = area;
			field = area;
			break;
		}
		case BOOLEAN: {
			CheckBox box = new CheckBox();
			box.setSelected(Boolean.parseBoolean(value));
			box.selectedProperty().addListener((o, was, is)->updateVisibility());
			editor = box;
			field = box;
			break;
		}
		case CHOICE: {
			ComboBox<String> box = new ComboBox<>();
			box.getItems().addAll(s.choices());
			if( !value.isEmpty() && !s.choices().contains(value)) {
				box.getItems().add(value);
			}
			// an empty choice means the default
			box.setConverter(new StringConverter<String>() {
				@Override
				public String toString(String v) {
					return v == null ? "" : v.isEmpty() ? "(default)" : v;
				}

				@Override
				public String fromString(String text) {
					return "(default)".equals(text) ? "" : text;
				}
			});
			box.setValue(value);
			box.valueProperty().addListener((o, was, is)->updateVisibility());
			editor = box;
			field = box;
			break;
		}
		case LOCAL_FILE: {
			TextField text = new TextField(value);
			Button browse = new Button("Browse...");
			browse.setOnAction(e->browse(text));
			HBox box = new HBox(4, text, browse);
			HBox.setHgrow(text, Priority.ALWAYS);
			editor = text;
			field = box;
			break;
		}
		default: {
			TextField text = new TextField(value);
			if( s.kind() == ConnectionSetting.Kind.INTEGER ) {
				text.setPrefColumnCount(8);
				text.setMaxWidth(120);
			}
			editor = text;
			field = text;
		}
		}
		if( editor instanceof TextInputControl ) {
			// settings can depend on a text value too
			((TextInputControl) editor).textProperty().addListener((o, was, is)->updateVisibility());
		}
		if( !s.description().isEmpty()) {
			label.setTooltip(new Tooltip(s.description()));
			editor.setTooltip(new Tooltip(s.description()));
		}
		label.setLabelFor(editor);
		return new Row(s, label, field, editor);
	}

	/** True if a keychain provider is installed, works here and can store secrets. */
	private static boolean keychainCanStore() {
		return Credentials.get("keychain").filter(p->p.isAvailable() && p.canStore()).isPresent();
	}

	/** A menu of the references the credential stores can list, and an item to type one. */
	private void chooseReference(PasswordField field, Button under) {
		ContextMenu menu = new ContextMenu();
		for(String ref : storedReferences()) {
			MenuItem item = new MenuItem(ref);
			item.setOnAction(e->field.setText(ref));
			menu.getItems().add(item);
		}
		if( menu.getItems().isEmpty()) {
			MenuItem nothing = new MenuItem("No stored secrets to list");
			nothing.setDisable(true);
			menu.getItems().add(nothing);
		}
		menu.getItems().add(new SeparatorMenuItem());
		MenuItem type = new MenuItem("Type a reference...");
		type.setOnAction(e->{
			TextInputDialog d = new TextInputDialog("@keychain:parley/");
			d.setTitle("Use a stored secret");
			d.setHeaderText(null);
			d.setContentText("A reference, such as @keychain:parley/prod-db or @env:DB_PASSWORD:");
			d.showAndWait().map(String::trim).ifPresent(v->{
				if( CredentialRef.isReference(v)) {
					field.setText(v);
				} else {
					new Alert(Alert.AlertType.WARNING, "'"+v+"' isn't a reference: use @scheme:locator").showAndWait();
				}
			});
		});
		menu.getItems().add(type);
		menu.show(under, Side.BOTTOM, 0, 0);
	}

	/**
	 * The references the available credential stores can list (a keychain can't be listed; a
	 * locked keystore is left out rather than asking for its master password here).
	 */
	static List<String> storedReferences() {
		List<String> ret = new ArrayList<>();
		for(CredentialProvider p : Credentials.providers()) {
			if( !p.isAvailable() || !p.canStore()) {
				continue;
			}
			try {
				for(CredentialRef r : p.list()) {
					ret.add(r.toString());
				}
			} catch (CredentialException e) {
				// can't be listed now
			}
		}
		return ret;
	}

	/**
	 * Saves typed passwords whose "Save to keychain" box is ticked, and puts a reference to
	 * each in its field instead. A failure is shown and the password is left as typed.
	 */
	private void saveTypedSecretsToKeychain(Properties values) {
		for(Row r : rows.values()) {
			CheckBox save = r.saveToKeychain;
			if( save == null || !save.isSelected() || !(r.editor instanceof PasswordField)) {
				continue;
			}
			PasswordField pw = (PasswordField) r.editor;
			String typed = pw.getText();
			if( typed.isEmpty() || CredentialRef.isReference(typed)) {
				continue;
			}
			char[] secret = typed.toCharArray();
			try {
				CredentialRef ref = ConnectionSettings.suggestedKeychainRef(factoryId,
						values.getProperty("user"), values.getProperty("host"));
				Credentials.store(ref, secret);
				pw.setText(ref.toString());
				save.setSelected(false);
			} catch (CredentialException e) {
				if( getScene() != null ) {
					new Alert(Alert.AlertType.WARNING, "The password wasn't saved to the keychain:\n"+e.getMessage()).showAndWait();
				}
			} finally {
				Arrays.fill(secret, '\0');
			}
		}
	}

	private void browse(TextField text) {
		FileChooser fc = new FileChooser();
		String current = text.getText().trim();
		if( !current.isEmpty()) {
			File file = new File(current);
			if( file.getParentFile() != null && file.getParentFile().isDirectory()) {
				fc.setInitialDirectory(file.getParentFile());
				fc.setInitialFileName(file.getName());
			}
		}
		File chosen = fc.showOpenDialog(getScene() == null ? null : getScene().getWindow());
		if( chosen != null ) {
			text.setText(chosen.getAbsolutePath());
		}
	}

	private Properties currentValues() {
		Properties ret = new Properties();
		ret.putAll(carried);
		for(Row r : rows.values()) {
			ret.setProperty(r.setting.key(), r.value());
		}
		return ret;
	}

	/** Lays out the rows that apply (a hidden row would still take up the grid's spacing). */
	private void updateVisibility() {
		Properties values = currentValues();
		grid.getChildren().clear();
		int y = 0;
		for(Row r : rows.values()) {
			boolean visible = ConnectionSettings.isVisible(r.setting, settings, values)
					&& (!r.setting.advanced() || showAdvanced.isSelected());
			r.label.setVisible(visible);
			r.field.setVisible(visible);
			if( visible ) {
				grid.add(r.label, 0, y);
				grid.add(r.field, 1, y);
				GridPane.setHgrow(r.field, Priority.ALWAYS);
				y++;
			}
		}
		if( rows.isEmpty()) {
			grid.add(none, 0, 0, 2, 1);
		}
		if( getScene() != null && getScene().getWindow() != null ) {
			getScene().getWindow().sizeToScene();
		}
	}

	/** What's wrong with the values entered; empty if nothing is. */
	public List<String> validateValues() {
		return ConnectionSettings.validate(settings, currentValues());
	}

	/** The properties to connect with (settings that don't apply are emptied). */
	public Properties getConnectionProperties() {
		saveTypedSecretsToKeychain(currentValues());
		return ConnectionSettings.forConnect(settings, currentValues());
	}

	/** Shows or hides the advanced settings. */
	public void setShowAdvanced(boolean show) {
		showAdvanced.setSelected(show);
	}

	public boolean isShowAdvanced() {
		return showAdvanced.isSelected();
	}

	/** The field for key, for tests; null if there's none. */
	Control fieldFor(String key) {
		Row r = rows.get(key);
		return r == null ? null : r.editor;
	}

	/** The "Save to keychain" box for a secret setting, for tests; null if there's none. */
	CheckBox saveToKeychainFor(String key) {
		Row r = rows.get(key);
		return r == null ? null : r.saveToKeychain;
	}

	/** Sets the factory type id used to name keychain entries, for tests. */
	void setFactoryId(String id) {
		factoryId = id;
	}

	/** The label and field for key are shown (for tests). */
	boolean isShown(String key) {
		Row r = rows.get(key);
		return r != null && r.field.isVisible();
	}
}
