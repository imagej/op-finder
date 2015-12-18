/*
 * #%L
 * Op Finder plugin for ImageJ.
 * %%
 * Copyright (C) 2009 - 2016 Board of Regents of the University of Wisconsin-Madison,
 *           Broad Institute of MIT and Harvard,
 *           and Max Planck Institute of Molecular Cell Biology and Genetics.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package net.imagej.ui.swing.ops;

import java.awt.Dimension;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeSelectionModel;

import org.scijava.Context;
import org.scijava.plugin.Parameter;
import org.scijava.prefs.PrefService;

import net.imagej.ops.Namespace;
import net.imagej.ops.Op;
import net.imagej.ops.OpInfo;
import net.imagej.ops.OpService;
import net.imagej.ops.OpUtils;

/**
 * A scrollable tree view of all discovered {@link Op} implementations. The goal
 * of this class is to make it easy to discover the available {@code Ops}, their
 * associated {@link Namespace}, and specific signatures available for these
 * ops.
 * <p>
 * {@code Ops} are sorted with {@code Namespaces} as the top level, then
 * {@code Op} name, finally with {@code Op} signatures as the leaves.
 * </p>
 *
 * @author Mark Hiner <hinerm@gmail.com>
 */
@SuppressWarnings("serial")
public class OpViewer extends JFrame {

	public static final int DEFAULT_WINDOW_WIDTH = 500;
	public static final int DEFAULT_WINDOW_HEIGHT = 700;
	public static final String WINDOW_HEIGHT = "op.viewer.height";
	public static final String WINDOW_WIDTH = "op.viewer.width";
	public static final String NO_NAMESPACE = "default namespace";

	@Parameter
	private OpService opService;

	@Parameter
	private PrefService prefService;

	public OpViewer(final Context context) {
		super("Op Viewer");
		context.inject(this);

		// Load the frame size
		loadPreferences();

		// Top node of the JTree
		final DefaultMutableTreeNode top = new DefaultMutableTreeNode(
			"Available Ops");
		createNodes(top);

		final JTree tree = new JTree(top);
		tree.getSelectionModel().setSelectionMode(
			TreeSelectionModel.SINGLE_TREE_SELECTION);

		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		// Make the JTree scrollable
		final JScrollPane pane = new JScrollPane(tree,
			ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

		setContentPane(pane);

		try {
			if (SwingUtilities.isEventDispatchThread()) {
				pack();
			}
			else {
				SwingUtilities.invokeAndWait(new Runnable() {

					@Override
					public void run() {
						pack();
					}
				});
			}
		}
		catch (final Exception ie) {
			/* ignore */
		}

		setLocationRelativeTo(null); // center on screen
		requestFocus();
	}

	/**
	 * Load any preferences saved via the {@link PrefService}, such as window
	 * width and height.
	 */
	public void loadPreferences() {
		final Dimension dim = getSize();

		// If a dimension is 0 then use the default dimension size
		if (0 == dim.width) {
			dim.width = DEFAULT_WINDOW_WIDTH;
		}
		if (0 == dim.height) {
			dim.height = DEFAULT_WINDOW_HEIGHT;
		}

		setPreferredSize(new Dimension(prefService.getInt(WINDOW_WIDTH, dim.width),
			prefService.getInt(WINDOW_HEIGHT, dim.height)));
	}

	/**
	 * Helper method to populate the {@link Op} nodes. Ops without a valid name
	 * will be skipped. Ops with no namespace will be put in a
	 * {@link #NO_NAMESPACE} category.
	 */
	private void createNodes(final DefaultMutableTreeNode top) {
		// Map namespaces and ops to their parent tree node
		final Map<String, DefaultMutableTreeNode> namespaces =
			new HashMap<>();

		final Map<String, DefaultMutableTreeNode> ops =
			new HashMap<>();

		// Iterate over all ops
		for (final OpInfo info : opService.infos()) {
			final String namespace = getName(info.getNamespace(), NO_NAMESPACE);

			// Get the namespace node for this Op
			final DefaultMutableTreeNode nsCategory = getCategory(top, namespaces,
				namespace);

			final String opName = getName(info.getSimpleName(), info.getName());

			if (!opName.isEmpty()) {
				// get the general Op node for this Op
				final DefaultMutableTreeNode opCategory = getCategory(nsCategory, ops,
					opName);

				// Create a leaf node for this particular Op's signature
				final DefaultMutableTreeNode opSignature = new DefaultMutableTreeNode(
					OpUtils.opString(info.cInfo()));

				opCategory.add(opSignature);
			}
		}
	}

	/**
	 * Helper method to get a properly formatted name. {@code name} is tried
	 * first, then {@code backupName} if needed (i.e. {@code name} is {@code null}
	 * or empty).
	 * <p>
	 * The resulting string is trimmed and set to lowercase.
	 * </p>
	 */
	private String getName(String name, final String backupName) {
		if (name == null || name.isEmpty()) name = backupName;

		return name == null ? "" : name.toLowerCase().trim();
	}

	/**
	 * Helper method to retrieved a map category with the specified name. If the
	 * category does not exist yet, it's created, added to the map, and added as a
	 * child to the parent tree node.
	 */
	private DefaultMutableTreeNode getCategory(
		final DefaultMutableTreeNode parent,
		final Map<String, DefaultMutableTreeNode> categoryMap,
		final String categoryName)
	{
		DefaultMutableTreeNode nsCategory = categoryMap.get(categoryName);
		if (nsCategory == null) {
			nsCategory = new DefaultMutableTreeNode(categoryName);
			parent.add(nsCategory);
			categoryMap.put(categoryName, nsCategory);
		}

		return nsCategory;
	}
}
