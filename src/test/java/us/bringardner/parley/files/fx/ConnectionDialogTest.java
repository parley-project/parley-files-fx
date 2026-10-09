package us.bringardner.parley.files.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import javafx.scene.control.Alert.AlertType;
import us.bringardner.parley.files.ConnectionSetting;
import us.bringardner.parley.files.FileSourceFactory;
import us.bringardner.parley.files.memory.MemoryFileSourceFactory;

/** Connecting from the dialog: values are checked first, and connecting runs in the background. */
public class ConnectionDialogTest {

	/** Records what the dialog would tell the user, instead of showing alerts. */
	static class TestDialog extends ConnectionDialog {
		final List<String> reports = new ArrayList<>();

		TestDialog(FileSourceFactory factory) {
			super(factory);
		}

		@Override
		protected void report(AlertType type, String title, String text) {
			reports.add(type+": "+title+": "+text);
		}
	}

	/** A memory file system that needs a host, so its settings can be wrong. */
	static class NeedsAHost extends MemoryFileSourceFactory {
		private static final long serialVersionUID = 1L;
		volatile boolean fail;

		@Override
		public List<ConnectionSetting> getConnectionSettings() {
			return List.of(ConnectionSetting.text("host", "Host").asRequired());
		}

		@Override
		protected boolean connectImpl() throws IOException {
			if( fail ) {
				throw new IOException("refused");
			}
			return super.connectImpl();
		}
	}

	private static boolean connect(TestDialog d) throws Exception {
		CompletableFuture<Boolean> done = Fx.call(d::tryConnect);
		return done.get(30, TimeUnit.SECONDS);
	}

	@Test
	public void connects() throws Exception {
		MemoryFileSourceFactory factory = new MemoryFileSourceFactory();
		TestDialog d = Fx.call(()->new TestDialog(factory));
		assertTrue(connect(d));
		assertTrue(factory.isConnected());
		assertEquals(List.of(), d.reports);
	}

	@Test
	public void settingsAreCheckedBeforeConnecting() throws Exception {
		NeedsAHost factory = new NeedsAHost();
		TestDialog d = Fx.call(()->new TestDialog(factory));
		assertFalse(connect(d));
		assertEquals(List.of("WARNING: Check the settings: Host is required"), d.reports);
		assertFalse(factory.isConnected());
	}

	@Test
	public void aFailedConnectionIsReported() throws Exception {
		NeedsAHost factory = new NeedsAHost();
		factory.fail = true;
		TestDialog d = Fx.call(()->{
			TestDialog dialog = new TestDialog(factory);
			((javafx.scene.control.TextField) dialog.getForm().fieldFor("host")).setText("example.org");
			return dialog;
		});
		assertFalse(connect(d));
		assertEquals(1, d.reports.size());
		assertTrue(d.reports.get(0).startsWith("ERROR: Could not connect: "), d.reports.get(0));
		assertTrue(d.reports.get(0).contains("refused"), d.reports.get(0));
	}

	@Test
	public void startsWithTheFactoryItIsGiven() throws Exception {
		NeedsAHost factory = new NeedsAHost();
		TestDialog d = Fx.call(()->new TestDialog(factory));
		assertTrue(d.getFactory() == factory);
		assertTrue(Fx.call(()->d.getForm().fieldFor("host") != null));
	}

	@Test
	public void theLocalFileSystemIsOnlyOfferedAlone() {
		List<String> types = ConnectionDialog.fileSystemTypes();
		assertFalse(types.isEmpty());
		if( types.size() > 1 ) {
			assertFalse(types.contains(FileSourceFactory.fileProxyFactory.getTypeId()));
		}
	}
}
