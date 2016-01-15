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
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import net.imagej.ops.Namespace;
import net.imagej.ops.Op;
import net.imagej.ops.OpInfo;
import net.imagej.ops.OpService;
import net.imagej.ops.OpUtils;

import org.jdesktop.swingx.JXTreeTable;
import org.scijava.Context;
import org.scijava.plugin.Parameter;
import org.scijava.prefs.PrefService;

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

	public static final int DEFAULT_WINDOW_WIDTH = 800;
	public static final int DEFAULT_WINDOW_HEIGHT = 700;
	public static final int COLUMN_MARGIN = 5;
	public static final String WINDOW_HEIGHT = "op.viewer.height";
	public static final String WINDOW_WIDTH = "op.viewer.width";
	public static final String NO_NAMESPACE = "default namespace";

	// Sizing fields
	private int[] widths;

	@Parameter
	private OpService opService;

	@Parameter
	private PrefService prefService;

	public OpViewer(final Context context) {
		super("Op Viewer");
		context.inject(this);

		// Load the frame size
		loadPreferences();

		final OpTreeTableModel model = new OpTreeTableModel();
		widths = new int[model.getColumnCount()];

		// Root of the TreeTable
		final OpTreeTableNode root = new OpTreeTableNode("Available Ops", "# @OpService ops;",
				"net.imagej.ops.OpService");
		model.getRoot().add(root);

		// Populate the nodes
		createNodes(root);

		final JXTreeTable treeTable = new JXTreeTable(model);
		treeTable.setColumnMargin(COLUMN_MARGIN);

		// Expand the top row
		treeTable.expandRow(0);

		// Update dimensions
		final Dimension dims = new Dimension(getSize());
		int preferredWidth = 0;
		for (int i : widths) preferredWidth += (i + COLUMN_MARGIN);
		dims.setSize(preferredWidth, DEFAULT_WINDOW_HEIGHT);
		setPreferredSize(dims);

		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		for (int i=0; i<model.getColumnCount(); i++) {
			treeTable.getColumn(i).setPreferredWidth(widths[i]);
		}

		// Make the treetable scrollable
		final JScrollPane pane = new JScrollPane(treeTable,
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
	private void createNodes(final OpTreeTableNode parent) {
		// Map namespaces and ops to their parent tree node
		final Map<String, OpTreeTableNode> namespaces =
			new HashMap<>();

		final Map<String, OpTreeTableNode> ops =
			new HashMap<>();

		// Iterate over all ops
		for (final OpInfo info : opService.infos()) {
			final String namespace = getName(info.getNamespace(), NO_NAMESPACE);

			// Get the namespace node for this Op
			final OpTreeTableNode nsCategory = getCategory(parent, namespaces,
				namespace);

			final String opName = getName(info.getSimpleName(), info.getName());

			if (!opName.isEmpty()) {
				// get the general Op node for this Op
				final OpTreeTableNode opCategory = getCategory(nsCategory, ops,
					opName);

				final String simpleName = OpUtils.simpleString(info.cInfo());
				//TODO
				final String codeCall = "";
				final String delegateClass = info.cInfo().getDelegateClassName();

				updateWidths(widths, simpleName, codeCall, delegateClass);

				// Create a leaf node for this particular Op's signature
				final OpTreeTableNode opSignature = new OpTreeTableNode(
					simpleName, codeCall, delegateClass);

				opCategory.add(opSignature);
			}
		}
	}

	/**
	 * Helper method to update the widths array to track the longest strings in each column.
	 */
	private void updateWidths(int[] colWidths, String... colContents) {
		
		for (int i=0; i<Math.min(colWidths.length, colContents.length); i++) {
			colWidths[i] = Math.max(colWidths[i], colContents[i].length());
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
	private OpTreeTableNode getCategory(
		final OpTreeTableNode parent,
		final Map<String, OpTreeTableNode> ops,
		final String categoryName)
	{
		OpTreeTableNode nsCategory = ops.get(categoryName);
		if (nsCategory == null) {
			nsCategory = new OpTreeTableNode(categoryName);
			parent.add(nsCategory);
			ops.put(categoryName, nsCategory);
		}

		return nsCategory;
	}
}
