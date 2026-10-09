package us.bringardner.parley.files.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javafx.application.Platform;
import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.browse.RecentFile;
import us.bringardner.parley.files.memory.MemoryFileSourceFactory;

public class RecentFileMenuTest {

	private static final String BASE = "us/bringardner/parley/files/fx/RecentFileMenuTest";

	/** A memory factory with a secret, like a remote one. */
	public static class SecretFactory extends MemoryFileSourceFactory {
		private static final long serialVersionUID = 1L;

		@Override
		public Properties getConnectProperties() {
			Properties p = super.getConnectProperties();
			p.setProperty("user", "bob");
			p.setProperty("password", "s3cret");
			return p;
		}

		@Override
		public boolean isSecretProperty(String name) {
			return name.equals("password");
		}
	}

	/** Records errors instead of showing them. */
	static class TestMenu extends RecentFileMenu {
		final List<String> errors = Collections.synchronizedList(new ArrayList<>());

		TestMenu(Preferences prefs) throws IOException {
			super(prefs);
		}

		@Override
		protected void showError(String message, Exception e) {
			errors.add(message+": "+e.getMessage());
		}
	}

	private Preferences node;

	@BeforeEach
	public void setUp() {
		node = Preferences.userRoot().node(BASE+"/"+UUID.randomUUID());
	}

	@AfterEach
	public void tearDown() throws BackingStoreException {
		Preferences parent = node.parent();
		node.removeNode();
		parent.flush();
	}

	@AfterAll
	public static void removeBase() throws BackingStoreException {
		Preferences.userRoot().node(BASE).removeNode();
		Preferences.userRoot().flush();
	}

	@Test
	public void secretsAreNeverSavedAndTheMenuShowsTheFiles() throws Exception {
		Fx.run(()->{
			TestMenu menu = new TestMenu(node);
			menu.addRecent(new SecretFactory().createFileSource("/docs/a_b.txt"));
			String saved = node.get(RecentFileMenu.PREF_RECENT_LIST, "");
			assertFalse(saved.contains("s3cret"), saved);
			assertTrue(saved.endsWith("password|/docs/a_b.txt\n"), saved);

			assertEquals("Max Files:10", menu.getItems().get(0).getText());
			assertEquals("memory:/docs/a_b.txt", menu.getItems().get(2).getText());
			assertEquals(3, menu.getItems().size());
		});
	}

	@Test
	public void choosingAnEntryAsksForSecretsThenConnectsInTheBackground() throws Exception {
		node.put(RecentFileMenu.PREF_RECENT_LIST, new RecentFile(new SecretFactory().createFileSource("/a.txt"))+"\n"
				+"memory|user=bob|/b.txt\n");
		List<Object> opened = Collections.synchronizedList(new ArrayList<>());
		List<String> asked = new ArrayList<>();
		TestMenu menu = Fx.call(()->{
			TestMenu m = new TestMenu(node);
			m.setSecretPrompter((e, name)->{
				assertTrue(Platform.isFxApplicationThread());
				asked.add(name);
				return "typed";
			});
			m.setOnOpened(opened::add);
			// the second entry
			m.getItems().get(3).fire();
			m.getItems().get(2).fire();
			return m;
		});
		Fx.waitFor(()->opened.size() == 2);
		assertEquals(Arrays.asList("password"), asked);
		assertEquals("/b.txt", ((FileSource) opened.get(0)).getCanonicalPath());
		assertEquals("/a.txt", ((FileSource) opened.get(1)).getCanonicalPath());
		Fx.run(()->{
			assertEquals("/a.txt", menu.getRecentEntries().get(0).path);
			assertEquals("memory:/a.txt", menu.getItems().get(2).getText());
		});
		assertFalse(node.get(RecentFileMenu.PREF_RECENT_LIST, "").contains("typed"));
		assertTrue(menu.errors.isEmpty(), menu.errors::toString);
	}

	@Test
	public void cancellingTheSecretOpensNothing() throws Exception {
		node.put(RecentFileMenu.PREF_RECENT_LIST, "memory|user=bob,password|/a.txt\n");
		Fx.run(()->{
			TestMenu menu = new TestMenu(node);
			menu.setSecretPrompter((e, name)->null);
			RecentFile entry = menu.getRecentEntries().get(0);
			assertTrue(menu.openEntry(entry).isDone());
			assertNull(menu.openEntry(entry).get());
			assertEquals(Arrays.asList("password"), entry.getMissingSecrets());
		});
	}

	@Test
	public void aFailureIsShownAndTheSecretsForgotten() throws Exception {
		node.put(RecentFileMenu.PREF_RECENT_LIST, "nosuchfactory|host=h,password|/x\n");
		List<Object> opened = Collections.synchronizedList(new ArrayList<>());
		TestMenu menu = Fx.call(()->{
			TestMenu m = new TestMenu(node);
			m.setSecretPrompter((e, name)->"wrong");
			m.setOnOpened(opened::add);
			m.getItems().get(2).fire();
			return m;
		});
		Fx.waitFor(()->!menu.errors.isEmpty());
		assertEquals(Arrays.asList("Can't open nosuchfactory:/x: No FileSource factory is registered for nosuchfactory"), menu.errors);
		assertTrue(opened.isEmpty());
		assertEquals(Arrays.asList("password"), Fx.call(()->menu.getRecentEntries().get(0).getMissingSecrets()));
	}

	@Test
	public void maxFilesAndOrderSurviveARestart() throws Exception {
		Fx.run(()->{
			TestMenu menu = new TestMenu(node);
			menu.setMaxFiles(2);
			for(String p : new String[] {"/1.txt", "/2.txt", "/3.txt", "/2.txt"}) {
				menu.addRecent(new SecretFactory().createFileSource(p));
			}
			TestMenu again = new TestMenu(node);
			assertEquals(2, again.getMaxFiles());
			assertEquals("/2.txt", again.getRecentEntries().get(0).path);
			assertEquals("/3.txt", again.getRecentEntries().get(1).path);
			assertEquals(2, again.getRecentEntries().size());
		});
	}
}
