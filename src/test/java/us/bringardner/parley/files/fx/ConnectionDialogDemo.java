package us.bringardner.parley.files.fx;

import java.util.concurrent.CountDownLatch;

import javafx.application.Platform;
import us.bringardner.parley.files.FileSource;

/**
 * Try the connection dialog: run this class (from Eclipse, say). It lists the file system
 * types on the classpath; add parley-files-sftp, -ftp or -jdbc to the run's classpath to
 * connect to those. On success it prints the new connection's roots.
 */
public class ConnectionDialogDemo {

	public static void main(String[] args) throws Exception {
		CountDownLatch done = new CountDownLatch(1);
		Platform.startup(()->{
			try {
				ConnectionDialog.connect(null).ifPresentOrElse(factory->{
					try {
						System.out.println("Connected: "+factory.getTitle());
						for(FileSource root : factory.listRoots()) {
							System.out.println("  "+root.getAbsolutePath());
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}, ()->System.out.println("Canceled"));
			} finally {
				done.countDown();
			}
		});
		done.await();
		Platform.exit();
	}
}
