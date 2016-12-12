/*
 * #%L
 * Op Finder plugin for ImageJ.
 * %%
 * Copyright (C) 2009 - 2016 Board of Regents of the University of
 * Wisconsin-Madison.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.imagej.ops.Namespace;
import net.imagej.ops.Op;

import org.jdesktop.swingx.treetable.TreeTableNode;
import org.scijava.command.CommandInfo;

/**
 * {@link TreeTableNode} implementation with tailored for ImageJ Ops usage. In
 * the tree structure built with these nodes, leaves correspond to concrete
 * {@link Op}s. Intermediate nodes are {@link Namespace}s.
 *
 * @author Mark Hiner
 */
public class OpTreeTableNode implements Comparable<OpTreeTableNode> {
	private String simpleName = "";
	private String referenceClass = "";
	private String codeCall = "";
	private CommandInfo info;
	private final List<OpTreeTableNode> children = new ArrayList<>();

	// -- Constructor --

	/**
	 * Varargs constructor. Positions are assigned in the following
	 * order:
	 * 
	 * <ul>
	 * <li>String 1: display name</li>
	 * <li>String 2: code call</li>
	 * <li>String 3: referenced class</li>
	 * </ul>
	 */
	public OpTreeTableNode(final String... fields) {
		if (fields.length > 0)
			simpleName = fields[0];
		if (fields.length > 1)
			codeCall = fields[1];
		if (fields.length > 2)
			referenceClass = fields[2];
	}

	// -- OpTreeTableNode Methods --

	/**
	 * @return The base name for this node
	 */
	public String getName() {
		return simpleName;
	}

	/**
	 * Set the name for this node
	 */
	public void setName(final String simpleName) {
		this.simpleName = simpleName;
	}

	/**
	 * @return The {@link Op} class referenced by this node.
	 */
	public String getReferenceClass() {
		return referenceClass;
	}

	/**
	 * Sets the {@link Op} class referenced by this node.
	 */
	public void setReferenceClass(final String referenceClass) {
		this.referenceClass = referenceClass;
	}

	/**
	 * @return The script snippet to invoke the {@link Op} referenced by this
	 *         node.
	 */
	public String getCodeCall() {
		return codeCall;
	}

	/**
	 * Set the script snippet to invoke the {@link Op} referenced by this node.
	 */
	public void setCodeCall(final String codeCall) {
		this.codeCall = codeCall;
	}

	/**
	 * @return A list of related nodes if this is actually a {@link Namespace}
	 *         node.
	 */
	public List<OpTreeTableNode> getChildren() {
		return children;
	}

	/**
	 * Add a child to this node.
	 */
	public void add(final OpTreeTableNode child) {
		final int index = -(Collections.binarySearch(children, child) + 1);
		children.add(index, child);
	}

	/**
	 * Sets the {@link CommandInfo} corresponding to the {@link Op} of this
	 * node.
	 */
	public void setCommandInfo(final CommandInfo info) {
		this.info = info;
	}

	/**
	 * @return The {@link CommandInfo} for this node.
	 */
	public CommandInfo getCommandInfo() {
		return info;
	}

	// -- TreeTableNode Methods --

	@Override
	public String toString() {
		return getName();
	}

	// -- Comparable api --

	@Override
	public int compareTo(final OpTreeTableNode o) {
		final int v = simpleName.compareTo(o.simpleName);
		// sort by simple name first, then by reference class
		return v != 0 ? v : referenceClass.compareTo(o.referenceClass);
	}
}
