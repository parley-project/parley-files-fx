package us.bringardner.parley.files.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.FileSourceFilter;
import us.bringardner.parley.files.browse.FileEntry;
import us.bringardner.parley.files.browse.SelectionMode;
import us.bringardner.parley.files.memory.MemoryFileSourceFactory;

/** The chooser's workings, driven without showing a dialog. */
public class FileSourceChooserTest {

	/** Preferences kept in memory, so tests don't touch the real ones. */
	static class MemoryPreferences extends AbstractPreferences {
		final Map<String,String> values = new HashMap<>();
		MemoryPreferences() { super(null, ""); }
		@Override protected void putSpi(String key, String value) { values.put(key, value); }
		@Override protected String getSpi(String key) { return values.get(key); }
		@Override protected void removeSpi(String key) { values.remove(key); }
		@Override protected void removeNodeSpi() { }
		@Override protected String[] keysSpi() { return values.keySet().toArray(new String[0]); }
		@Override protected String[] childrenNamesSpi() { return new String[0]; }
		@Override protected AbstractPreferences childSpi(String name) { return new MemoryPreferences(); }
		@Override protected void syncSpi() { }
		@Override protected void flushSpi() { }
	}

	private MemoryFileSourceFactory factory;
	private FileSource dir;
	private final FileSourceFilter scripts = new FileSourceFilter() {
		@Override public boolean accept(FileSource f) { return f.getName().endsWith(".sh"); }
		@Override public String getDescription() { return "Shell scripts"; }
	};

	@BeforeEach
	public void setUp() throws IOException {
		factory = new MemoryFileSourceFactory();
		factory.connect();
		dir = factory.createFileSource("/chooser-"+System.nanoTime());
		dir.mkdirs();
		for(String n : new String[] {"a.sh", "b.sh", "notes.txt"}) {
			try(OutputStream out = dir.getChild(n).getOutputStream()) {
				out.write(1);
			}
		}
		dir.getChild("sub").mkdir();
	}

	private ChooserPane pane(ChooserPane.Mode mode, SelectionMode selection) throws Exception {
		FileSourceChooser fc = new FileSourceChooser();
		fc.setInitialDirectory(dir);
		fc.getFilters().add(scripts);
		fc.setSelectionMode(selection);
		fc.setInitialFileName("out.sh");
		fc.setPreferences(null);
		ChooserPane p = Fx.call(()->fc.createPane(mode));
		Fx.waitFor(()->!p.table().isLoading());
		return p;
	}

	private static int indexOf(ChooserPane p, String name) {
		List<FileEntry> shown = p.table().getShownEntries();
		for(int i=0; i < shown.size(); i++) {
			if( shown.get(i).getName().equals(name)) {
				return i;
			}
		}
		throw new AssertionError(name+" isn't shown");
	}

	private static void select(ChooserPane p, String... names) {
		p.table().getTableView().getSelectionModel().clearSelection();
		for(String n : names) {
			p.table().getTableView().getSelectionModel().select(indexOf(p, n));
		}
	}

	private static List<String> names(List<FileSource> files) {
		return files == null ? null : files.stream().map(FileSource::getName).collect(Collectors.toList());
	}

	@Test
	public void opensAFile() throws Exception {
		ChooserPane p = pane(ChooserPane.Mode.OPEN, SelectionMode.FILES);
		// the first filter is chosen, not "All Files"
		assertEquals(scripts, Fx.call(()->p.filterChoice().getValue()));
		assertFalse(Fx.call(()->p.canAcceptProperty().get()));
		assertNull(Fx.call(p::result));
		Fx.run(()->select(p, "b.sh"));
		Fx.waitFor(()->p.canAcceptProperty().get());
		assertEquals(List.of("b.sh"), names(Fx.call(p::result)));
	}

	@Test
	public void filesTheFilterRulesOutCantBeChosen() throws Exception {
		ChooserPane p = pane(ChooserPane.Mode.OPEN, SelectionMode.FILES);
		Fx.run(()->select(p, "notes.txt"));
		Thread.sleep(100);
		assertFalse(Fx.call(()->p.canAcceptProperty().get()));
		Fx.run(()->p.filterChoice().getSelectionModel().select(0));   // All Files
		Fx.run(()->select(p, "notes.txt"));
		Fx.waitFor(()->p.canAcceptProperty().get());
	}

	@Test
	public void navigates() throws Exception {
		ChooserPane p = pane(ChooserPane.Mode.OPEN, SelectionMode.FILES);
		assertFalse(Fx.call(p::canGoBack));
		Fx.run(()->p.table().open(p.table().getShownEntries().get(indexOf(p, "sub"))));
		Fx.waitFor(()->!p.table().isLoading());
		assertEquals("sub", Fx.call(()->p.currentDirectory().getName()));
		assertTrue(Fx.call(p::canGoBack));

		Fx.run(p::goBack);
		assertEquals(dir.getName(), Fx.call(()->p.currentDirectory().getName()));
		assertTrue(Fx.call(p::canGoForward));
		Fx.run(p::goForward);
		assertEquals("sub", Fx.call(()->p.currentDirectory().getName()));

		// up selects the folder it came from
		Fx.run(p::goUp);
		Fx.waitFor(()->!p.table().isLoading());
		assertEquals(dir.getName(), Fx.call(()->p.currentDirectory().getName()));
		assertTrue(Fx.call(p::canGoUp));
	}

	@Test
	public void choosesAFolder() throws Exception {
		ChooserPane p = pane(ChooserPane.Mode.OPEN, SelectionMode.DIRECTORIES);
		// nothing selected: the folder shown
		assertEquals(List.of(dir.getName()), names(Fx.call(p::result)));
		Fx.run(()->select(p, "sub"));
		Fx.waitFor(()->!p.table().getSelectedEntries().isEmpty());
		assertEquals(List.of("sub"), names(Fx.call(p::result)));
	}

	@Test
	public void choosesSeveral() throws Exception {
		ChooserPane p = pane(ChooserPane.Mode.OPEN_MULTIPLE, SelectionMode.FILES);
		Fx.run(()->select(p, "a.sh", "b.sh"));
		Fx.waitFor(()->p.table().getSelectedEntries().size() == 2);
		assertEquals(List.of("a.sh", "b.sh"), names(Fx.call(p::result)));
	}

	@Test
	public void saves() throws Exception {
		ChooserPane p = pane(ChooserPane.Mode.SAVE, SelectionMode.FILES);
		assertEquals("out.sh", Fx.call(()->p.nameField().getText()));
		assertEquals(List.of("out.sh"), names(Fx.call(p::result)));

		// choosing a file puts its name in the field
		Fx.run(()->select(p, "a.sh"));
		Fx.waitFor(()->p.nameField().getText().equals("a.sh"));

		// replacing asks first
		List<String> asked = new ArrayList<>();
		Fx.run(()->p.confirm = (title, text)->{ asked.add(text); return false; });
		assertNull(Fx.call(p::result));
		assertEquals(1, asked.size());
		Fx.run(()->p.confirm = (title, text)->true);
		assertEquals(List.of("a.sh"), names(Fx.call(p::result)));

		Fx.run(()->p.nameField().setText("  "));
		assertFalse(Fx.call(()->p.canAcceptProperty().get()));
	}

	@Test
	public void newFolders() throws Exception {
		ChooserPane p = pane(ChooserPane.Mode.SAVE, SelectionMode.FILES);
		assertEquals("New Folder", Fx.call(p::newFolder).getName());
		Fx.waitFor(()->!p.table().isLoading());
		assertEquals("New Folder 2", Fx.call(p::newFolder).getName());
		assertTrue(dir.getChild("New Folder 2").isDirectory());
	}

	@Test
	public void switchesToAnotherFileSystem() throws Exception {
		ChooserPane p = pane(ChooserPane.Mode.OPEN, SelectionMode.FILES);
		MemoryFileSourceFactory other = new MemoryFileSourceFactory();
		other.connect();
		Fx.run(()->p.showFileSystem(other));
		Fx.waitFor(()->!p.table().isLoading());
		FileSource root = other.listRoots()[0];
		assertEquals(root.getAbsolutePath(), Fx.call(()->p.currentDirectory().getAbsolutePath()));
		assertEquals(root.getAbsolutePath(), Fx.call(()->p.tree().getRoot().getChildren().get(0).getValue().getAbsolutePath()));
	}

	@Test
	public void remembersTheViewOptions() throws Exception {
		MemoryPreferences prefs = new MemoryPreferences();
		FileSourceChooser fc = new FileSourceChooser();
		fc.setInitialDirectory(dir);
		fc.setPreferences(prefs);
		ChooserPane p = Fx.call(()->fc.createPane(ChooserPane.Mode.OPEN));
		assertFalse(Fx.call(()->p.table().showHiddenProperty().get()));
		Fx.run(()->{
			p.table().showHiddenProperty().set(true);
			p.table().showExtensionsProperty().set(false);
		});
		assertEquals("true", prefs.values.get(ChooserPane.SHOW_HIDDEN_PREFERENCE));
		ChooserPane again = Fx.call(()->fc.createPane(ChooserPane.Mode.OPEN));
		assertTrue(Fx.call(()->again.table().showHiddenProperty().get()));
		assertFalse(Fx.call(()->again.table().showExtensionsProperty().get()));
	}
}
