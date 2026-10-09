package us.bringardner.parley.files.fx;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import javafx.application.Platform;
import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.FileSourceFilter;

/**
 * Try the chooser: run this class (from Eclipse, say). It shows an Open dialog for several
 * files, then a Save dialog, on the local file system; "Connect to..." in the options menu
 * reaches remote ones when parley-files-sftp, -ftp or -jdbc is on the run's classpath.
 */
public class FileSourceChooserDemo {

	public static void main(String[] args) throws Exception {
		CountDownLatch done = new CountDownLatch(1);
		Platform.startup(()->{
			try {
				FileSourceChooser fc = new FileSourceChooser();
				fc.getFilters().add(new FileSourceFilter() {
					@Override
					public boolean accept(FileSource f) {
						return f.getName().endsWith(".sh");
					}

					@Override
					public String getDescription() {
						return "Shell scripts (*.sh)";
					}
				});
				List<FileSource> opened = fc.showOpenMultipleDialog(null);
				System.out.println("Opened: "+opened);
				fc.setInitialFileName("new-script.sh");
				System.out.println("Save to: "+fc.showSaveDialog(null));
			} finally {
				done.countDown();
			}
		});
		done.await();
		Platform.exit();
	}
}
