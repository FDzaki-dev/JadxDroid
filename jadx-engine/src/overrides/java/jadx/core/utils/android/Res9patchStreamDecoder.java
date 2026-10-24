package jadx.core.utils.android;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Android override of upstream jadx 1.5.6 class.
 * Upstream re-draws 9-patch images with java.awt / javax.imageio which do not exist on Android.
 * Returning false makes ResourcesLoader fall back to exporting the original file untouched.
 */
public class Res9patchStreamDecoder {

	public boolean decode(InputStream in, OutputStream out) {
		return false;
	}
}
