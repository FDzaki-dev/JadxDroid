package jadx.api.gui.tree;

import org.jetbrains.annotations.Nullable;

import jadx.api.metadata.ICodeNodeRef;

public interface ITreeNode {

	/**
	 * Locale independent node identifier
	 */
	String getID();

	/**
	 * Node title
	 */
	String getName();

	/**
	 * Node icon
	 */
	Object getIcon();

	/**
	 * Related code node reference.
	 */
	@Nullable
	ICodeNodeRef getCodeNodeRef();
}
