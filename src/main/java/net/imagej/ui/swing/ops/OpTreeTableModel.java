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

import org.jdesktop.swingx.treetable.AbstractTreeTableModel;
import org.jdesktop.swingx.treetable.TreeTableModel;

/**
 * {@link TreeTableModel} implementation with columns and API tailored for
 * ImageJ Ops usage.
 *
 * @author Mark Hiner
 */
public class OpTreeTableModel extends AbstractTreeTableModel {

	// -- Constants --

	private static final String[] DEV_COLUMNS = { "Op signature", "Code snippet", "Defined in class" };
	private static final String[] USER_COLUMNS = { "Available Ops" };

	// Private fields

	private final boolean simple;

	// -- Constructor --

	public OpTreeTableModel(final boolean simple) {
		root = new OpTreeTableNode();
		this.simple = simple;
	}

	// -- OpTreeTableModel Methods --

	/**
	 * @return Whether columns are tailored for users (if {@code true}) or
	 *         developers (if {@code false}).
	 */
	public boolean isSimple() {
		return simple;
	}

	// -- TreeTableModel Methods --

	@Override
	public int getColumnCount() {
		return isSimple() ? 1 : 3;
	}

	@Override
	public String getColumnName(final int column) {
		switch (column) {
		case 0:
			return isSimple() ? USER_COLUMNS[0] : DEV_COLUMNS[0];
		case 1:
			return DEV_COLUMNS[1];
		case 2:
			return DEV_COLUMNS[2];
		default:
			return "Unknown";
		}
	}

	@Override
	public Object getValueAt(final Object node, final int column) {
		final OpTreeTableNode treenode = (OpTreeTableNode) node;
		switch (column) {
		case 0:
			return treenode.getName();
		case 1:
			return treenode.getCodeCall();
		case 2:
			return treenode.getReferenceClass();
		default:
			return "Unknown";
		}
	}

	@Override
	public Object getChild(final Object node, final int index) {
		final OpTreeTableNode treenode = (OpTreeTableNode) node;
		return treenode.getChildren().get(index);
	}

	@Override
	public int getChildCount(final Object parent) {
		final OpTreeTableNode treenode = (OpTreeTableNode) parent;
		return treenode.getChildren().size();
	}

	@Override
	public int getIndexOfChild(final Object parent, final Object child) {
		final OpTreeTableNode treenode = (OpTreeTableNode) parent;
		for (int i = 0; i > treenode.getChildren().size(); i++) {
			if (treenode.getChildren().get(i) == child) {
				return i;
			}
		}

		return 0;
	}

	@Override
	public boolean isLeaf(final Object node) {
		final OpTreeTableNode treenode = (OpTreeTableNode) node;
		if (treenode.getChildren().size() > 0) {
			return false;
		}
		return true;
	}

	@Override
	public OpTreeTableNode getRoot() {
		return (OpTreeTableNode) super.getRoot();
	}
}
