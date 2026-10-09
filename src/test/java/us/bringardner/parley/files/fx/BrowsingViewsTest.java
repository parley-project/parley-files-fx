package us.bringardner.parley.files.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javafx.scene.control.Button;
import javafx.scene.control.TreeItem;
import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.browse.FileEntry;
import us.bringardner.parley.files.browse.SelectionMode;
import us.bringardner.parley.files.memory.MemoryFileSourceFactory;

/** FileTable, PathBar and FolderTree, on an in-memory file system. */
public class BrowsingViewsTest {

	private MemoryFileSourceFactory factory;
	private FileSource dir;

	@BeforeEach
	public void setUp() throws IOException {
		factory = new MemoryFileSourceFactory();
		factory.connect();
		dir = factory.createFileSource("/views-"+System.nanoTime());
		dir.mkdirs();
		for(String n : new String[] {"notes.txt", "data.csv", ".hidden"}) {
			try(OutputStream out = dir.getChild(n).getOutputStream()) {
				out.write(new byte[12]);
			}
		}
		dir.getChild("sub").mkdir();
		dir.getChild(".git").mkdir();
		dir.getChild("sub").getChild("inner").mkdir();
	}

	private static List<String> names(List<FileEntry> entries) {
		return entries.stream().map(FileEntry::getName).collect(Collectors.toList());
	}

	private FileTable listed(FileSource d) throws Exception {
		FileTable t = Fx.call(FileTable::new);
		Fx.run(()->t.setDirectory(d));
		Fx.waitFor(()->!t.isLoading());
		return t;
	}

	@Test
	public void listsWithoutHiddenFiles() throws Exception {
		FileTable t = listed(dir);
		assertEquals(List.of("data.csv", "notes.txt", "sub"), Fx.call(()->names(t.getShownEntries())));
		Fx.run(()->t.showHiddenProperty().set(true));
		assertEquals(5, Fx.call(()->t.getShownEntries().size()));
	}

	@Test
	public void onlyWhatCanBePickedIsSelected() throws Exception {
		FileTable t = listed(dir);
		Fx.run(()->{
			t.filterProperty().set(f->f.getName().endsWith(".txt"));
			t.selectionModeProperty().set(SelectionMode.FILES);
			t.getTableView().getSelectionModel().selectAll();
		});
		assertEquals(List.of("notes.txt"), Fx.call(()->names(t.getSelectedEntries())));
		// the rows themselves are deselected too, not just left out
		assertEquals(1, Fx.call(()->t.getTableView().getSelectionModel().getSelectedItems().size()));

		Fx.run(()->t.select(dir.getChild("data.csv")));
		assertEquals(List.of("notes.txt"), Fx.call(()->names(t.getSelectedEntries())), "data.csv can't be picked");
	}

	@Test
	public void aFileIsSelectedOnceItsListingArrives() throws Exception {
		FileTable t = Fx.call(FileTable::new);
		Fx.run(()->{
			t.setDirectory(dir);
			t.select(dir.getChild("notes.txt"));
		});
		Fx.waitFor(()->!t.isLoading());
		assertEquals(List.of("notes.txt"), Fx.call(()->names(t.getSelectedEntries())));
	}

	@Test
	public void aStaleListingIsDropped() throws Exception {
		FileTable t = Fx.call(FileTable::new);
		FileSource sub = dir.getChild("sub");
		Fx.run(()->{
			t.setDirectory(dir);
			t.setDirectory(sub);
		});
		Fx.waitFor(()->!t.isLoading());
		Thread.sleep(200);
		assertEquals(List.of("inner"), Fx.call(()->names(t.getShownEntries())));
	}

	@Test
	public void foldersOpen() throws Exception {
		FileTable t = listed(dir);
		List<FileEntry> opened = new ArrayList<>();
		Fx.run(()->{
			t.setOnOpen(opened::add);
			t.open(t.getShownEntries().get(2));
		});
		assertEquals(List.of("sub"), names(opened));
	}

	@Test
	public void anEmptyFolderSaysSo() throws Exception {
		FileTable t = listed(dir.getChild("sub").getChild("inner"));
		assertEquals("This folder is empty.", Fx.call(t::messageText));
	}

	@Test
	public void aListingThatFailsIsReported() throws Exception {
		FileSource broken = (FileSource) Proxy.newProxyInstance(FileSource.class.getClassLoader(),
				new Class<?>[] {FileSource.class}, (proxy, method, args)->{
					if( method.getName().equals("listFiles")) {
						throw new IOException("connection lost");
					}
					try {
						return method.invoke(dir, args);
					} catch (InvocationTargetException e) {
						throw e.getCause();
					}
				});
		FileTable t = listed(broken);
		assertNotNull(Fx.call(()->t.errorProperty().get()));
		assertTrue(Fx.call(t::messageText).contains("connection lost"), Fx.call(t::messageText));
	}

	@Test
	public void pathBarGoesUp() throws Exception {
		FileSource inner = dir.getChild("sub").getChild("inner");
		PathBar bar = Fx.call(PathBar::new);
		List<String> labels = Fx.call(()->{
			bar.setDirectory(inner);
			List<String> ret = new ArrayList<>();
			bar.getChildren().forEach(n->{ if( n instanceof Button b ) ret.add(b.getText()); });
			return ret;
		});
		assertEquals(List.of(dir.getName(), "sub", "inner"), labels.subList(labels.size()-3, labels.size()));
		Fx.run(()->{
			Button sub = null;
			for(var n : bar.getChildren()) {
				if( n instanceof Button b && b.getText().equals("sub")) {
					sub = b;
				}
			}
			// firing rebuilds the bar
			sub.fire();
		});
		assertEquals(dir.getChild("sub").getAbsolutePath(), Fx.call(()->bar.getDirectory().getAbsolutePath()));
	}

	@Test
	public void folderTreeListsOnExpand() throws Exception {
		FolderTree tree = Fx.call(FolderTree::new);
		TreeItem<FileSource> top = Fx.call(()->{
			tree.setTopFolders(List.of(dir));
			TreeItem<FileSource> item = tree.getRoot().getChildren().get(0);
			item.setExpanded(true);
			return item;
		});
		Fx.waitFor(()->((FolderTree.FolderItem) top).isListed());
		assertEquals(List.of("sub"), Fx.call(()->top.getChildren().stream().map(i->i.getValue().getName()).collect(Collectors.toList())));

		Fx.run(()->tree.showHiddenProperty().set(true));
		Fx.waitFor(()->top.getChildren().size() == 2);
		assertEquals(List.of(".git", "sub"), Fx.call(()->top.getChildren().stream().map(i->i.getValue().getName()).collect(Collectors.toList())));
		assertSame(dir, Fx.call(()->top.getValue()));
	}

	@Test
	public void sizesForPeople() {
		assertEquals("0 bytes", FileIcons.size(0));
		assertEquals("1 byte", FileIcons.size(1));
		assertEquals("999 bytes", FileIcons.size(999));
		assertEquals("1.5 KB", FileIcons.size(1500));
		assertEquals("12 MB", FileIcons.size(12_300_000));
		assertEquals("", FileIcons.date(0));
	}
}
