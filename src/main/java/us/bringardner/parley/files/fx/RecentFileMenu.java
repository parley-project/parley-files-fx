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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import javafx.application.Platform;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;
import us.bringardner.fx.menu.RecentItemsMenu;
import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.FileSourceFactory;
import us.bringardner.parley.files.browse.RecentFile;
import us.bringardner.swing.menu.RecentItems;

/**
 * A "Recent Files" menu, saved with java.util.prefs.Preferences: the JavaFX twin of
 * parley-files-swing's RecentFileMenu. The menu itself is fx-widgets' {@link RecentItemsMenu}, and
 * each entry is a parley-files {@link RecentFile}, which holds everything that isn't JavaFX. Both
 * menus save the same list in the same form, so given the same preferences node they share it.
 * <p>
 * Each entry keeps its factory's connection properties so it can be reopened, but secret values
 * (the factory decides which, see {@link FileSourceFactory#isSecretProperty(String)}) are never
 * saved. When an entry is opened the user is asked for them (see
 * {@link #setSecretPrompter(RecentFile.SecretPrompter)}), and the answers are kept in memory for the
 * rest of the session.
 * <p>
 * Choosing an entry asks for its secrets, then connects off the JavaFX thread; when connected,
 * the entry moves to the top and the {@link FileSource} is passed to
 * {@link #setOnOpened(Consumer) onOpened}. A failure is shown with {@link #showError(String, Exception)}.
 * Use the menu on the JavaFX thread.
 */
public class RecentFileMenu extends RecentItemsMenu<RecentFile> {

	/** Where 1.0.1 and earlier kept the encrypted list; migrated, then removed. */
	public static final String PREF_LEGACY_RECENT_LIST = RecentFile.PREF_LEGACY_RECENT_LIST;
	/** The list: one entry per line, secret values left out. */
	public static final String PREF_RECENT_LIST = RecentItems.PREF_RECENT_LIST;
	public static final String PREF_MAX_FILES = RecentItems.PREF_MAX_ITEMS;

	private static final RecentItems.Codec<RecentFile> CODEC = new RecentItems.Codec<RecentFile>() {
		@Override
		public String encode(RecentFile entry) {
			return entry.toString();
		}

		@Override
		public RecentFile decode(String line) {
			return new RecentFile(line);
		}
	};

	private RecentFile.SecretPrompter secretPrompter = this::askForSecret;

	/** Saves the list in the preferences node for targetClass's package. */
	public RecentFileMenu(Class<?> targetClass) throws IOException {
		this(Preferences.userNodeForPackage(targetClass));
	}

	/** Saves the list in the given preferences node. */
	public RecentFileMenu(Preferences prefs) throws IOException {
		super("Recent Files", RecentFile.migrateLegacyList(prefs), CODEC);
	}

	/** Sets how secrets are asked for, on the JavaFX thread; null restores the default (a password dialog). */
	public void setSecretPrompter(RecentFile.SecretPrompter prompter) {
		secretPrompter = prompter == null ? this::askForSecret : prompter;
	}

	/** @return the entries, newest first, without connecting to anything */
	public List<RecentFile> getRecentEntries() {
		return getEntries();
	}

	/**
	 * Opens an entry: asks for any secret that wasn't saved, then connects off the JavaFX thread.
	 * If the connection fails, the secrets just entered are forgotten so they are asked for again.
	 * Call it on the JavaFX thread.
	 *
	 * @return completes on the JavaFX thread with the file, or with null if a prompt was cancelled
	 */
	public CompletableFuture<FileSource> openEntry(RecentFile entry) {
		List<String> asked = entry.fillMissingSecrets(secretPrompter);
		if( asked == null ) {
			return CompletableFuture.completedFuture(null);
		}
		CompletableFuture<FileSource> ret = new CompletableFuture<>();
		Background.EXECUTOR.execute(()->{
			try {
				FileSource file = entry.getFile();
				Platform.runLater(()->ret.complete(file));
			} catch (IOException | RuntimeException e) {
				entry.forgetSecrets(asked);
				Platform.runLater(()->ret.completeExceptionally(e));
			}
		});
		return ret;
	}

	public void setRecentFiles(List<FileSource> list) throws IOException {
		List<RecentFile> ret = new ArrayList<>();
		for(FileSource file : list) {
			ret.add(new RecentFile(file));
		}
		setEntries(ret);
	}

	public void setMaxFiles(int maxRecent) throws IOException {
		setMaxItems(maxRecent);
	}

	public int getMaxFiles() {
		return getMaxItems();
	}

	public void addRecent(FileSource file) throws IOException {
		addEntry(new RecentFile(file));
	}

	@Override
	protected String getLabel(RecentFile entry) {
		return entry.getLabel();
	}

	/** Local files that are gone are dropped; remote entries aren't checked (see {@link RecentFile#isGone()}). */
	@Override
	protected boolean isStale(RecentFile entry) {
		return entry.isGone();
	}

	/** Keeps the secrets entered this session for the entry being replaced. */
	@Override
	protected RecentFile merge(RecentFile newer, RecentFile older) {
		return newer.keepSecretsFrom(older);
	}

	/**
	 * Starts opening the entry and returns null, so the menu does nothing more for now; once
	 * connected, the entry moves to the top and the file goes to onOpened.
	 */
	@Override
	protected Object openItem(RecentFile entry) {
		openEntry(entry).whenComplete((file, error)->{
			if( error != null ) {
				Throwable e = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
				showError("Can't open "+getLabel(entry), e instanceof Exception ? (Exception) e : new IOException(e));
			} else if( file != null ) {
				try {
					addEntry(entry);
				} catch (IOException e) {
					showError("Can't save the recent list", e);
				}
				Consumer<Object> handler = getOnOpened();
				if( handler != null ) {
					handler.accept(file);
				}
			}
		});
		return null;
	}

	@Override
	protected String getMaxItemsText(int max) {
		return "Max Files:"+max;
	}

	private String askForSecret(RecentFile entry, String name) {
		PasswordField field = new PasswordField();
		Dialog<String> dialog = new Dialog<>();
		dialog.setTitle("Enter "+name);
		dialog.setHeaderText(getLabel(entry));
		dialog.getDialogPane().setContent(new VBox(6, new Label(name), field));
		dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		dialog.setResultConverter(button->button == ButtonType.OK ? field.getText() : null);
		Platform.runLater(field::requestFocus);
		return dialog.showAndWait().orElse(null);
	}
}
