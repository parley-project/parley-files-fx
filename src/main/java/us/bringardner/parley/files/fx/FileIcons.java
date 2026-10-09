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

import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

/** The small pictures and text formats the file views share. */
public final class FileIcons {

	private static final String FOLDER = "M1 3 h5 l2 2 h7 v9 h-14 z";
	private static final String FILE = "M3 1 h7 l4 4 v10 h-11 z M10 1 v4 h4";
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
			.withZone(ZoneId.systemDefault());

	private FileIcons() {
	}

	/** A 16x16 folder or file picture. */
	public static Node icon(boolean directory) {
		SVGPath p = new SVGPath();
		p.setContent(directory ? FOLDER : FILE);
		p.setFill(directory ? Color.web("#7fb2e5") : Color.web("#f4f4f4"));
		p.setStroke(directory ? Color.web("#4a7fb5") : Color.web("#8a8a8a"));
		p.setStrokeWidth(1);
		p.getStyleClass().add(directory ? "folder-icon" : "file-icon");
		return p;
	}

	/** A size for people: "0 bytes", "12 KB", "3.4 MB". */
	public static String size(long bytes) {
		if( bytes < 1000 ) {
			return bytes+(bytes == 1 ? " byte" : " bytes");
		}
		String[] units = {"KB", "MB", "GB", "TB", "PB"};
		double v = bytes;
		int u = -1;
		while( v >= 1000 && u < units.length-1 ) {
			v /= 1000;
			u++;
		}
		return new DecimalFormat(v < 10 ? "0.#" : "0").format(v)+" "+units[u];
	}

	/** A date and time in the user's format, or "" for 0 (unknown). */
	public static String date(long millis) {
		return millis <= 0 ? "" : DATE.format(Instant.ofEpochMilli(millis));
	}
}
