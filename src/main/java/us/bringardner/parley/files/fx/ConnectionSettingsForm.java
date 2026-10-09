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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import us.bringardner.parley.files.ConnectionSetting;
import us.bringardner.parley.files.ConnectionSettings;
import us.bringardner.parley.files.FileSourceFactory;

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

		Row(ConnectionSetting setting, Label label, Node field, Control editor) {
			this.setting = setting;
			this.label = label;
			this.field = field;
			this.editor = editor;
		}

		String value() {
			if( editor instanceof TextArea area ) {
				return area.getText();
			} else if( editor instanceof TextInputControl text ) {
				// a TextField or PasswordField
				return editor instanceof PasswordField ? text.getText() : text.getText().trim();
			} else if( editor instanceof CheckBox box ) {
				return ""+box.isSelected();
			} else if( editor instanceof ComboBox<?> box ) {
				Object item = box.getValue();
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
			PasswordField pw = new PasswordField();
			pw.setText(value);
			editor = pw;
			field = pw;
			break;
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
		if( editor instanceof TextInputControl text ) {
			// settings can depend on a text value too
			text.textProperty().addListener((o, was, is)->updateVisibility());
		}
		if( !s.description().isEmpty()) {
			label.setTooltip(new Tooltip(s.description()));
			editor.setTooltip(new Tooltip(s.description()));
		}
		label.setLabelFor(editor);
		return new Row(s, label, field, editor);
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

	/** The label and field for key are shown (for tests). */
	boolean isShown(String key) {
		Row r = rows.get(key);
		return r != null && r.field.isVisible();
	}
}
