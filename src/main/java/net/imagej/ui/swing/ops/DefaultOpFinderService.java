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

import org.scijava.Context;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.service.AbstractService;
import org.scijava.service.Service;

/**
 * Default implementation of the {@link OpFinderService}. Manages an
 * {@link OpFinder} instance.
 *
 * @author Mark Hiner
 */
@Plugin(type = Service.class)
public class DefaultOpFinderService extends AbstractService implements OpFinderService {

	@Parameter
	private Context context;

	private OpFinder opFinder;

	@Override
	public void showOpFinder() {
		final boolean initSize = opFinder == null || opFinder.isVisible() == false;

		if (opFinder == null) {
			makeOpFinder();
		}

		if (initSize) {
			opFinder.setVisible(true);
			opFinder.pack();
			opFinder.setLocationRelativeTo(null); // center on screen
		}
		opFinder.requestFocus();
	}

	private synchronized void makeOpFinder() {
		if (opFinder == null)
			opFinder = new OpFinder(context);
	}

}
