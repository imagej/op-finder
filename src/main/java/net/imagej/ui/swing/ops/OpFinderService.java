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

import net.imagej.ImageJService;

import org.scijava.Context;
import org.scijava.service.Service;

/**
 * {@link Service} for managing the UI elements relating to the {@link FindOps}
 * command. Using a {@code Service} for this purpose effectively makes the UI
 * components singletons within the {@link Context}, so they do not need to
 * be recreated on subsequent runs.
 *
 * @author Mark Hiner
 */
public interface OpFinderService extends ImageJService {

	void showOpFinder();
}
