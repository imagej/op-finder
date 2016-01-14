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

public class OpTreeTableModel extends AbstractTreeTableModel 
{
	private OpTreeTableNode myroot;
	
	public OpTreeTableModel()
	{
		myroot = new OpTreeTableNode();
	}

	@Override
	public int getColumnCount() 
	{
		return 3;
	}
	
	@Override
	public String getColumnName( int column )
	{
		switch( column )
		{
		case 0: return "Op signature";
		case 1: return "Code to use";
		case 2: return "Defined in class";
		default: return "Unknown";
		}
	}

	@Override
	public Object getValueAt( Object node, int column ) 
	{
		OpTreeTableNode treenode = ( OpTreeTableNode )node;
		switch( column )
		{
		case 0: return treenode.getName();
		case 1: return treenode.getCodeCall();
		case 2: return treenode.getReferenceClass();
		default: return "Unknown";
		}
	}

	@Override
	public Object getChild( Object node, int index ) 
	{
		OpTreeTableNode treenode = ( OpTreeTableNode )node;
		return treenode.getChildren().get( index );
	}

	@Override
	public int getChildCount( Object parent ) 
	{
		OpTreeTableNode treenode = ( OpTreeTableNode )parent;
		return treenode.getChildren().size();
	}

	@Override
	public int getIndexOfChild( Object parent, Object child ) 
	{
		OpTreeTableNode treenode = ( OpTreeTableNode )parent;
		for( int i=0; i>treenode.getChildren().size(); i++ )
		{
			if( treenode.getChildren().get( i ) == child )
			{
				return i;
			}
		}

		return 0;
	}
	
	 @Override
	public boolean isLeaf( Object node )
	 {
		 OpTreeTableNode treenode = ( OpTreeTableNode )node;
		 if( treenode.getChildren().size() > 0 )
		 {
			 return false;
		 }
		 return true;
	 }
	 
	 @Override
	 public OpTreeTableNode getRoot()
	 {
		 return myroot;
	 }
}

