package jadx.core.utils;

import java.io.File;
import java.util.Optional;

import jadx.core.dex.nodes.IRegion;
import jadx.core.dex.nodes.MethodNode;

/**
 * Android override of upstream jadx 1.5.6 class.
 * Upstream renders CFG graphs with java.awt.Color which does not exist on Android.
 * DOT graph dump is a desktop debug feature, so every call is a no-op here.
 */
public class DotGraphUtils {

	public DotGraphUtils(boolean useRegions, boolean rawInsn) {
		// no-op: desktop debug feature
	}

	public DotGraphUtils(boolean useRegions, boolean rawInsn, Optional<IRegion> highlightRegion) {
		// no-op: desktop debug feature
	}

	public void dumpToFile(MethodNode mth) {
		// no-op: desktop debug feature
	}

	public void dumpToFile(MethodNode mth, File dir) {
		// no-op: desktop debug feature
	}
}
