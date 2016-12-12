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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.script.ScriptException;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.tree.TreePath;

import net.imagej.ops.Namespace;
import net.imagej.ops.Op;
import net.imagej.ops.OpInfo;
import net.imagej.ops.OpService;
import net.imagej.ops.OpUtils;
import net.imglib2.img.Img;
import net.miginfocom.swing.MigLayout;

import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;
import org.jdesktop.swingx.JXTreeTable;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;
import org.scijava.Context;
import org.scijava.command.CommandInfo;
import org.scijava.log.LogService;
import org.scijava.module.ModuleItem;
import org.scijava.platform.PlatformService;
import org.scijava.plugin.Parameter;
import org.scijava.prefs.PrefService;
import org.scijava.script.ScriptService;
import org.scijava.thread.ThreadService;

/**
 * A searchable tree-table view of all discovered {@link Op} implementations.
 * The goal of this tool is to make it easy to explore the available {@code Ops}
 * , their associated {@link Namespace}, and specific signatures available for
 * these ops. This caters to the extensibility of {@code Ops}, allowing us to
 * discover all {@code Op Plugins} at runtime instead of trying to provide
 * static documentation about available functions.
 * <p>
 * Without filtering, {@code Ops} are displayed tree-style where {@code
 * Namespaces} and {@code Op} types create a collapsible hierarchy. Concrete
 * {@code Op} signatures are the leaves. These {@code Op} signatures can be
 * executed individually or copied and pasted into the a script.
 * </p>
 * <p>
 * A filter bar allows users to narrow the displayed ops based on a fuzzy
 * scoring algorithm. When filtering, namespaces are hidden.
 * </p>
 * <p>
 * The UI itself can be toggled into a "user" or "developer" view, with
 * differing levels of granularity both in the {@code Ops} displayed and
 * the amount of information provided about an individual {@code Op}.
 * Rounding out the display is a sidebar information panel which can be
 * expanded/collapsed as desired.
 * </p>
 *
 * @author Mark Hiner <hinerm@gmail.com>
 */
@SuppressWarnings("serial")
public class OpFinder extends JFrame implements DocumentListener, ActionListener {

	// -- Constants --

	public static final int DETAILS_WINDOW_WIDTH = 400;
	public static final int MAIN_WINDOW_HEIGHT = 700;
	public static final int COLUMN_MARGIN = 5;
	public static final int HIDE_COOLDOWN = 1500;
	public static final String WINDOW_HEIGHT = "op.viewer.height";
	public static final String WINDOW_WIDTH = "op.viewer.width";
	public static final String NO_NAMESPACE = "(global)";
	public static final String BASE_JAVADOC_URL = "http://javadoc.imagej.net/ImageJ/";
	public static final String SIMPLE_KEY = "net.imagej.ui.swing.ops.opfinder.simple";

	// HACK -- these patterns are used to unify image and numeric classes in Ops.
	public static final String IMG_REGEX = "ArrayImg|PlanarImg|RandomAccessibleInterval|IterableInterval|Img|Histogram1d";
	public static final String IMGPLUS_REGEX = "ImgPlus|Dataset";
	public static final String NUMBER_REGEX = "int|short|long|double|float|byte|RealType";

	// -- Fields --

	// Simple mode
	private boolean simple = true;
	private ModeButton modeButton;
	private JLabel searchLabel;
	private boolean autoToggle = true;
	private Set<Class<?>> simpleFilterClasses;

	// Off-EDT work
	private FilterRunner lastFilter;
	private HTMLFetcher lastHTMLReq;

	// Sizing fields
	private int[] widths;

	// Child elements
	private JTextField searchField;
	private JXTreeTable treeTable;
	private JLabel successLabel = null;
	private JEditorPane textPane;
	private JScrollPane detailsPane;
	private JButton toggleDetailsButton;
	private final JPanel mainPane;
	private final JSplitPane splitPane;
	private JProgressBar progressBar;
	private OpTreeTableModel advModel;
	private OpTreeTableModel smplModel;

	// Icons
	private ImageIcon opFail;
	private ImageIcon opSuccess;
	private ImageIcon expandDetails;
	private ImageIcon hideDetails;
	private ImageIcon useView;
	private ImageIcon devView;

	// Caching TreePaths
	private Set<TreePath> advExpandedPaths;
	private Set<TreePath> smplExpandedPaths;

	// Caching web elements
	private Map<String, String> elementsMap;

	// Cache tries for matching
	private Map<Trie, OpTreeTableNode> advTries;
	private Map<Trie, OpTreeTableNode> smplTries;

	// For hiding the successLabel
	private Timer successTimer;
	private Timer progressTimer;

	// -- Parameters --

	@Parameter
	private OpService opService;

	@Parameter
	private PrefService prefService;

	@Parameter
	private LogService logService;

	@Parameter
	private PlatformService platformService;

	@Parameter
	private ScriptService scriptService;

	@Parameter
	private ThreadService threadService;

	// -- Constructor --

	public OpFinder(final Context context) {
		super("Op Finder   [shift + L]");
		context.inject(this);

		initialize();
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		// NB top panel defines column count
		mainPane = new JPanel(new MigLayout("", "[][][][][][][grow, right]", "[grow]"));

		// Build search panel
		buildTopPanel();

		// Build the tree table
		buildTreeTable();

		// Build the details pane
		buildDetailsPane();

		// Build the bottom panel
		buildBottomPanel();

		// Create the split display (main contents on the left and detail panel
		// will be on the right)
		splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mainPane, null);

		// Add the pane to this frame
		add(splitPane);

		// Restore state from preferences
		setState(prefService.getBoolean(SIMPLE_KEY, true));
	}

	// -- OpFinder methods --

	/**
	 * Set whether the {@code Op Finder} should be in simple or advanced mode.
	 *
	 * @param toSimple
	 *            true if we should go to simple mode, false if we should go to
	 *            advanced mode.
	 */
	public void setState(final boolean toSimple) {
		if (toSimple != simple) {
			simple = toSimple;
			prefService.put(SIMPLE_KEY, simple);
			modeButton.setLabels(toSimple);

			// Cache the current expanded nodes, then restore the paths for the
			// mode we are going to - but do NOT delete the cache at this point
			if (treeTable != null) {
				cacheExpandedPaths(!toSimple);
				treeTable.setTreeTableModel(toSimple ? smplModel : advModel);
				restoreExpandedPaths(toSimple, false);
			}

			// If the details pane has never been manually toggled, we want
			// to change its state now.
			if (autoToggle)
				toggleDetails();

			// Simple and Advanced trees are kept separately, so we need to
			// re-filter when changing modes.
			filterOps(searchField.getDocument());
		}
	}

	// -- Component methods --
	
	@Override
	public void requestFocus() {
		super.requestFocus();
		// The search field is the only component of the Op Finder that accepts
		// text input so it is a logical candidate for focus.
		searchField.requestFocusInWindow();
	}

	// -- Window methods --

	@Override
	public void pack() {
		try {
			// Ensure we only pack *on* the EDT.
			if (SwingUtilities.isEventDispatchThread()) {
				super.pack();
			} else {
				SwingUtilities.invokeAndWait(new Runnable() {

					@Override
					public void run() {
						OpFinder.super.pack();
					}
				});
			}
		} catch (final Exception ie) {
			logService.error(ie);
		}
	}

	// -- ActionListener methods --

	@Override
	public void actionPerformed(final ActionEvent e) {
		// If the toggleDetails button is ever clicked, in addition to toggling
		// we want to ensure that auto-toggle functionality is disabled so
		// switching between developer/user views preserves the details pane
		// state.
		if (e.getSource() == toggleDetailsButton) {
			autoToggle = false;
			toggleDetails();
		}
	}

	// -- DocumentListener methods --

	@Override
	public void insertUpdate(final DocumentEvent e) {
		// All changes to the search field require re-filtering of visible ops
		filterOps(e);
	}

	@Override
	public void removeUpdate(final DocumentEvent e) {
		// All changes to the search field require re-filtering of visible ops
		filterOps(e);
	}

	@Override
	public void changedUpdate(final DocumentEvent e) {
		// All changes to the search field require re-filtering of visible ops
		filterOps(e);
	}

	// -- Helper methods --

	/**
	 * Initialize local variables
	 */
	private void initialize() {
		advExpandedPaths = new HashSet<>();
		smplExpandedPaths = new HashSet<>();
		elementsMap = new HashMap<>();
		advTries = new HashMap<>();
		smplTries = new HashMap<>();
		advModel = new OpTreeTableModel(false);
		smplModel = new OpTreeTableModel(true);
		widths = new int[advModel.getColumnCount()];
	
		buildSimpleInputs();
	
		buildTimers();
	}

	/**
	 * These timers are used to hide any temporary visual feedback icons
	 * (progress bar, success labels) after a brief delay set by
	 * {@link #HIDE_COOLDOWN}.
	 */
	private void buildTimers() {
		successTimer = new Timer(HIDE_COOLDOWN, new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent evt) {
				successLabel.setVisible(false);
			}
		});
	
		progressTimer = new Timer(HIDE_COOLDOWN, new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent evt) {
				progressBar.setVisible(false);
			}
		});
	}

	/**
	 * Create {@link Op} nodes in the tree-table structure to display to users.
	 * Also build and attach the necessary structures to respond to user inputs.
	 */
	private void buildTreeTable() {
		// Populate the nodes
		createNodes();
	
		// Create a custom TreeTable to fill the tool-tip text appropriately
		treeTable = new JXTreeTable(simple ? smplModel : advModel) {
			// Adapted from:
			// http://stackoverflow.com/a/21281257/1027800
			@Override
			public String getToolTipText(final MouseEvent e) {
				final String tip = "";
				final Point p = e.getPoint();
				final int rowIndex = rowAtPoint(p);
				final int colIndex = columnAtPoint(p);
	
				try {
					// The first row is an empty placeholder
					// Other rows can be either concrete Ops or Namespaces
					// Tool-tip text will reflect this, as appropriate
					final OpTreeTableNode n = getNodeAtRow(rowIndex);
					if (n != null) {
						switch (colIndex) {
						case 0:
							String name;
							if (rowIndex == 0)
								name = "all available ops";
							else
								name = n.getName();
							if (rowIndex > 0 && n.getCodeCall().isEmpty()) {
								final OpTreeTableNode firstChild = n.getChildren().get(0);
								if (firstChild != null && firstChild.getChildren().isEmpty()) {
									// If a child of this node is a leaf then
									// this node
									// is an Op node
									name += " op";
								}
								// Otherwise this is a namespace
								else
									name += " namespace";
							}
							return name;
						default:
							return (String) treeTable.getValueAt(rowIndex, colIndex);
						}
					}
				} catch (final RuntimeException e1) {
					// catch null pointer exception if mouse is over an empty
					// line
				}
	
				return tip;
			}
		};
	
		// Add a mouse-listener to respond to double-click events, copying the
		// clicked cell contents to the clipboard
	
		// Adapted from:
		// http://stackoverflow.com/a/25918436/1027800
		treeTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(final MouseEvent e) {
				if (e.getClickCount() == 2) {
					final Point p = e.getPoint();
					final int rowIndex = treeTable.rowAtPoint(p);
					final int colIndex = treeTable.columnAtPoint(p);
					final OpTreeTableNode n = getNodeAtRow(rowIndex);
	
					if (n != null) {
						final String text = treeTable.getValueAt(rowIndex, colIndex).toString();
	
						if (text.isEmpty()) {
							selectFail();
						} else {
							final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
							clipboard.setContents(new StringSelection(text), null);
							copyPass();
						}
						successTimer.restart();
					}
				}
			}
		});
	
		// Add a selection listener: if a concrete op row is selected and the
		// details pane is visible, fetch the javadoc for that op and display it.
		treeTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(final ListSelectionEvent event) {
				final OpTreeTableNode n = getNodeAtRow(treeTable.getSelectedRow());
				if (n != null && detailsPane.isVisible()) {
					String newText = n.getReferenceClass();
					if (!newText.isEmpty()) {
						newText = newText.replaceAll("\\.", "/");
						if (newText.contains("$")) {
							// For nested classes, replace $ with a URL-safe '.'
							final String suffix = newText.substring(newText.lastIndexOf("$"));
							newText = newText.replace(suffix, "%2E" + suffix.substring(1));
						}
						final String requestedClass = newText;
						final StringBuilder sb = new StringBuilder();
						sb.append(BASE_JAVADOC_URL);
						sb.append(newText);
						sb.append(".html");
						final String url = sb.toString();
	
						synchronized (elementsMap) {
							if (elementsMap.containsKey(url)) {
								textPane.setText(elementsMap.get(url));
								scrollToTop();
							} else {
								if (lastHTMLReq != null && !lastHTMLReq.isDone())
									lastHTMLReq.stop();
	
								lastHTMLReq = new HTMLFetcher(sb, url, requestedClass);
								threadService.run(lastHTMLReq);
							}
						}
					}
				}
			}
	
		});
	
		// Space the columns slightly
		treeTable.setColumnMargin(COLUMN_MARGIN);
	
		// Allow rows to be selected
		treeTable.setRowSelectionAllowed(true);
	
		// Default the top row to be expanded. This should show all top-level namespaces, collapsed.
		treeTable.expandRow(0);
	
		// Add our tree-table as a scrollable window
		final int preferredWidth = getPreferredMainWidth();
		mainPane.add(
				new JScrollPane(treeTable, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
						ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED),
				"span, wrap, grow, w " + preferredWidth / 2 + ":" + preferredWidth + ", h " + MAIN_WINDOW_HEIGHT);
	}

	/**
	 * Queue a request to scroll the details pane to its top position, off the
	 * EDT.
	 */
	private void scrollToTop() {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				// scroll to 0,0
				detailsPane.getVerticalScrollBar().setValue(0);
				detailsPane.getHorizontalScrollBar().setValue(0);
			}
		});
	}

	/**
	 * Helper method to compute the preferred width of the main tree-table
	 * window
	 */
	private int getPreferredMainWidth() {
		int preferredWidth = 0;
		for (final int i : widths)
			preferredWidth += (i + COLUMN_MARGIN);
	
		return preferredWidth;
	}

	/**
	 * Helper method to build the top panel of the UI. This includes the
	 * search/filter bar, action buttons and status icons.
	 */
	private void buildTopPanel() {
		// Search/filter field
		final int searchWidth = 160;
		searchField = new JTextField(searchWidth);
		searchLabel = new JLabel();
		searchLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		mainPane.add(searchLabel, "w 145!");
		mainPane.add(searchField, "w " + searchWidth + "!");
	
		// Build action (run, copy, wiki) buttons
		useView = new ImageIcon(getClass().getResource("/icons/opbrowser/view_use.png"));
		devView = new ImageIcon(getClass().getResource("/icons/opbrowser/view_dev.png"));
		modeButton = new ModeButton();
		final JButton runButton = new JButton(new ImageIcon(getClass().getResource("/icons/opbrowser/play.png")));
		final JButton snippetButton = new JButton(
				new ImageIcon(getClass().getResource("/icons/opbrowser/paperclip.png")));
		final JButton wikiButton = new JButton(new ImageIcon(getClass().getResource("/icons/opbrowser/globe.png")));
	
		runButton.setToolTipText("Run the selected Op");
		runButton.addActionListener(new RunButtonListener());
	
		snippetButton.setToolTipText("<html>Copy the selected cell contents to your clipboard.<br />"
				+ "You can also double-click a cell to copy its contents.</html>");
		snippetButton.addActionListener(new CopyButtonListener());
	
		wikiButton.setToolTipText("Learn more about ImageJ Ops");
		wikiButton.addActionListener(new WikiButtonListener());
	
		mainPane.add(modeButton, "w 145!, h 32!, gapleft 15");
		mainPane.add(runButton, "w 32!, h 32!, gapleft 15");
		mainPane.add(snippetButton, "w 32!, h 32!");
		mainPane.add(wikiButton, "w 32!, h 32!");
	
		// Add icons for visual feedback after clicking a button (success/failure)
		opFail = new ImageIcon(getClass().getResource("/icons/opbrowser/redx.png"));
		opSuccess = new ImageIcon(getClass().getResource("/icons/opbrowser/greencheck.png"));
		successLabel = new JLabel();
		successLabel.setHorizontalTextPosition(SwingConstants.LEFT);
		successLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		successLabel.setVisible(false);
	
		mainPane.add(successLabel, "h 20!, w 155!, wrap");
	
		// Add listener to respond to text entry in the search bar
		searchField.getDocument().addDocumentListener(this);
	}

	/**
	 * Helper method for constructing the details pane. This pane can be
	 * shown/hidden off the right hand side of the main window. It's intended to
	 * show additional details on the currently selected row.
	 */
	private void buildDetailsPane() {
		// No content to display at the beginning. Content is set by the
		// ListSelectionListener attached when buliding the TreeTable
		textPane = new JEditorPane("text/html", "Select an Op for more information");
		textPane.setEditable(false);
	
		detailsPane = new JScrollPane(textPane, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
	
		detailsPane.setPreferredSize(new Dimension(DETAILS_WINDOW_WIDTH, MAIN_WINDOW_HEIGHT));
		detailsPane.setVisible(false);
	}

	/**
	 * Helper method for constructing the bottom panel of the Op Finder. This
	 * pane contains a progress bar and the expand/hide button for the details
	 * pane.
	 */
	private void buildBottomPanel() {
		progressBar = new JProgressBar(SwingConstants.HORIZONTAL, 0, 100);
		mainPane.add(progressBar, "w 100!");
		progressBar.setVisible(false);
	
		hideDetails = new ImageIcon(getClass().getResource("/icons/opbrowser/arrow_left.png"));
		expandDetails = new ImageIcon(getClass().getResource("/icons/opbrowser/arrow_right.png"));
		toggleDetailsButton = new JButton(expandDetails);
		toggleDetailsButton.setToolTipText("Show / Hide Details");
		toggleDetailsButton.addActionListener(this);
		mainPane.add(toggleDetailsButton, "span, align right, w 32!, h 32!");
	}

	/**
	 * Helper method to update progress of the bottom panel's progress bar.
	 * Triggers the corresponding {@link Timer} to hide the progress bar, if
	 * needed.
	 */
	private void setProgress(final int progress) {
		progressBar.setVisible(true);
		progressBar.setValue(progress);
		if (progress >= progressBar.getMaximum() || progress <= progressBar.getMinimum())
			progressTimer.restart();
		else
			progressTimer.stop();
	}

	/**
	 * Extracts the {@link Document} from the given {@link DocumentEvent} and
	 * passes it to {@link #filterOps(Document)}.
	 */
	private void filterOps(final DocumentEvent e) {
		final Document doc = e.getDocument();
		filterOps(doc);
	}

	/**
	 * Filter the contents of the main content pane based on the search filter
	 * bar's search string.
	 */
	private void filterOps(final Document doc) {
		try {
			final String text = doc.getText(0, doc.getLength());

			// If a previous filter process is running, it is now outdated and
			// can be stopped.
			if (lastFilter != null) {
				lastFilter.stop();
			}

			// If there is no text then we want to restore the full tree-table
			// model, based on the current mode flag.
			if (text == null || text.isEmpty()) {
				treeTable.setTreeTableModel(simple ? smplModel : advModel);
				restoreExpandedPaths(simple, true);
			} else {
				// Expanded "paths" (showing the nesting of Namespaces) are only
				// visible when no filter is applied. Thus, when applying a
				// filter, we need to cache the state of currently expanded
				// paths. This way if the search field is cleared, we go back to
				// the previous state of the view.
				cacheExpandedPaths(simple);

				// Start running the requested filter on a new thread. We do
				// this because filtering is purely data structure manipulation,
				// and can be lengthy depending on the number of Ops (especially
				// the first time filtering is performed and tries are being
				// built). Running off the EDT ensures we don't kill the entire
				// app, and allows us to post progress updated.
				lastFilter = new FilterRunner(text);
				threadService.run(lastFilter);
			}
		} catch (final BadLocationException exc) {
			logService.error(exc);
		}
	}

	/**
	 * Typically used when changing views or clearing the filter results.
	 * Restores the state of any expanded tree nodes in the main pane after
	 * storage with {@link #cacheExpandedPaths(boolean)}.
	 *
	 * @param isSimple whether the simple or advanced cache should be restored
	 * @param clearCache if true, the cache will be deleted after it's restored.
	 */
	private void restoreExpandedPaths(final boolean isSimple, final boolean clearCache) {
		final Set<TreePath> paths = isSimple ? smplExpandedPaths : advExpandedPaths;

		if (paths.isEmpty()) {
			// Top row is always expanded by default
			treeTable.expandRow(0);
		} else {
			for (final TreePath path : paths) {
				treeTable.expandPath(path);
			}
		}

		if (clearCache) {
			paths.clear();
		}

	}

	/**
	 * Look at each path check which paths are expanded and cache
	 * them for future restoration.
	 * 
	 * @param isSimple whether the simple or advanced cache should be saved
	 */
	private void cacheExpandedPaths(final boolean isSimple) {
		final Set<TreePath> paths = isSimple ? smplExpandedPaths : advExpandedPaths;

		// Find and cache the expanded paths
		for (int i = 0; i < treeTable.getRowCount(); i++) {
			if (treeTable.isExpanded(i))
				paths.add(treeTable.getPathForRow(i));
		}
	}

	/**
	 * Toggle the state (show or hide) of the details pane and update any
	 * associated UI elements as necessary.
	 */
	private void toggleDetails() {
		if (detailsPane == null)
			return;
		final boolean hide = detailsPane.isVisible();
	
		if (hide) {
			detailsPane.setPreferredSize(detailsPane.getSize());
			splitPane.remove(detailsPane);
			detailsPane.setVisible(false);
			toggleDetailsButton.setIcon(expandDetails);
		} else {
			detailsPane.setVisible(true);
			splitPane.add(detailsPane);
			toggleDetailsButton.setIcon(hideDetails);
		}
	
		if (isVisible()) {
			// Prevent left side from resizing
			final Component lc = splitPane.getLeftComponent();
			lc.setPreferredSize(lc.getSize());
	
			pack();
		}
	}

	/**
	 * Helper method to populate the {@link Op} nodes. Ops without a valid name
	 * will be skipped. Ops with no namespace will be put in a
	 * {@link #NO_NAMESPACE} category.
	 */
	private void createNodes() {
		// We maintain separate data structures for each mode
		final OpTreeTableNode advParent = new OpTreeTableNode("ops", "# @OpService ops", "net.imagej.ops.OpService");
		final OpTreeTableNode smplParent = new OpTreeTableNode("ops", "# @OpService ops", "net.imagej.ops.OpService");
		advModel.getRoot().add(advParent);
		smplModel.getRoot().add(smplParent);

		// Map namespaces and ops to their parent tree node
		final Map<String, OpTreeTableNode> advNamespaces = new HashMap<>();
		final Map<String, OpTreeTableNode> smplNamespaces = new HashMap<>();
		final Set<String> smplOps = new HashSet<>();

		// Iterate over all ops
		for (final OpInfo info : opService.infos()) {

			final String opName = getName(info.getSimpleName(), info.getName());

			if (!opName.isEmpty()) {
				final String namespacePath = getName(info.getNamespace(), NO_NAMESPACE);
				final String pathToOp = namespacePath + "." + opName;

				// Build the node path to this op. There is one node per
				// namespace. Then a general Op type node, the leaves of which
				// are the actual implementations.
				final OpTreeTableNode advOpType = buildNamespaceHierarchy(advParent, advNamespaces, pathToOp);
				final OpTreeTableNode smplOpType = buildNamespaceHierarchy(smplParent, smplNamespaces, pathToOp);

				final String delegateClass = info.cInfo().getDelegateClassName();
				String simpleName = OpUtils.simpleString(info.cInfo());
				final String codeCall = OpUtils.opCall(info.cInfo());

				// Create a leaf node for this particular Op's signature
				final OpTreeTableNode opSignature = new OpTreeTableNode(simpleName, codeCall, delegateClass);
				opSignature.setCommandInfo(info.cInfo());

				// Create the dictionary which will be used for filtering
				final Trie advTrie = buildTries(delegateClass, '.');
				advTries.put(advTrie, opSignature);
				advOpType.add(opSignature);

				simpleName = simplifyTypes(simpleName);

				// If this Op matches our criteria for inclusion in simple mode,
				// we update the corresponding for the simple data structures.
				if (isSimple(info.cInfo(), simpleName, smplOps)) {
					final OpTreeTableNode simpleOp = new OpTreeTableNode(simpleName, codeCall, delegateClass);
					simpleOp.setCommandInfo(info.cInfo());
					final Trie smplTrie = buildTries(simpleName);
					smplTries.put(smplTrie, simpleOp);
					smplOpType.add(simpleOp);
				}

				updateWidths(widths, simpleName, codeCall, delegateClass);
			}
		}

		pruneEmptyNodes(smplParent);
	}

	/**
	 * HACK
	 * Build the whitelist of classes that we will display in the simple view.
	 */
	private void buildSimpleInputs() {
		simpleFilterClasses = new HashSet<>();
		// Only Img and things convertible to Img will be considered
		simpleFilterClasses.add(Img.class);
	}

	/**
	 * HACK
	 * Perform string replacements to simplify names of Op parameters.
	 */
	private String simplifyTypes(String simpleName) {
		// The goal is to boil down all parameter to "Image" or "Number" labels for display purposes.
		simpleName = simpleName.replaceAll(IMG_REGEX + "|" + IMGPLUS_REGEX, "Image");
		simpleName = simpleName.replaceAll(NUMBER_REGEX, "Number");

		// Remove optional parameters
		simpleName = simpleName.replaceAll("[a-zA-Z0-9]+(\\[\\])? [a-zA-Z0-9]+\\?", "");

		// Clean up variable separators from removed optional params
		simpleName = simpleName.replaceAll(", (, )+", ", "); // multiple adjacent optional params
		simpleName = simpleName.replaceAll("(, )+(\\))", "$2"); // last param is optional
		simpleName = simpleName.replaceAll("(\\()(, )+", "$1"); // first param is optional

		// Remove the return variable
		final int splitPoint = simpleName.substring(0, simpleName.indexOf('(')).lastIndexOf(' ');

		return simpleName.substring(splitPoint + 1);
	}

	/**
	 * HACK
	 * @return true iff the given {@link Op} meets the criteria for display in simple mode.
	 */
	private boolean isSimple(final CommandInfo info, final String simpleName, final Set<String> simpleOps) {
		if (!simpleOps.contains(simpleName)) {
			// Check that at least one of the Op's inputs is on the "simple types" white list.
			for (final ModuleItem<?> moduleItem : info.inputs()) {
				final Class<?> inputType = moduleItem.getType();
				for (final Class<?> acceptedClass : simpleFilterClasses) {
					if (acceptedClass.isAssignableFrom(inputType)) {
						simpleOps.add(simpleName);
						return true;
					}
				}
			}
		}

		return false;
	}

	/**
	 * Build a {@link Trie} dictionary for filter parsing, using all substrings
	 * of the given input dictionary and delimiters.
	 */
	private Trie buildTries(final String rawDict, final char... delim) {
		final Trie trie = new Trie().removeOverlaps();
		final Set<String> substrings = getSubstringsWithDelim(rawDict.toLowerCase(Locale.getDefault()), delim);
		for (final String substring : substrings)
			trie.addKeyword(substring);
		
		return trie;
	}
	
	/**
	 * Recursively prune any node that signifies an "empty" namespace, that is:
	 * 
	 *  <ul>
	 *  <li>has no children</li>
	 *  <li>has no {@code ReferenceClass} field</li>
	 *  </ul>
	 *
	 * @return true if this node should be removed from the child list.
	 */
	private boolean pruneEmptyNodes(final OpTreeTableNode node) {
		// A code call indicates the node is a true Op and not a namespace.
		boolean removeThis = node.getCodeCall().isEmpty();
		final List<OpTreeTableNode> preservedChildren = new ArrayList<>();

		// Recursive step over child nodes
		for (final OpTreeTableNode child : node.getChildren()) {
			if (!pruneEmptyNodes(child))
				preservedChildren.add(child);
		}

		node.getChildren().retainAll(preservedChildren);

		// If all children have been eliminated, mark this node for removal
		removeThis &= node.getChildren().isEmpty();

		return removeThis;
	}

	/**
	 * A provided base string is broken down into substrings as follows:
	 * <ul>
	 * <li>For each delimiter: {@link String#split(String)} the base string</li>
	 * <li>For each leading substring of the split: add to final string set</li>
	 * <li>For the last substring of the split: add all possible substrings to the final set</li>
	 * </ul>
	 * <p>
	 * For example, if given the input string "org.scijava.haha" and delimiter ".", this method would return the set:
	 * <ul>
	 * <li>"org", "scijava", "h", "a", "ah", "aha", "hah", "haha"</li>
	 * </ul>
	 * </p>
	 */
	private Set<String> getSubstringsWithDelim(final String string, final char... delims) {
		final Set<String> substringsToCheck = new HashSet<>();

		String strOfInterest = string;

		// For each delimiter, add all leading split strings and save the final split
		for (final char delim : delims) {
			int dotIndex = 0;
			while (dotIndex >= 0) {
				final int startIndex = dotIndex;
				dotIndex = string.indexOf(delim, dotIndex + 1);

				if (dotIndex < 0) {
					strOfInterest = string.substring(startIndex, string.length());
				} else {
					substringsToCheck.add(string.substring(startIndex, dotIndex + 1));
				}
			}
		}

		// find all substrings of each final split
		for (int start = 0; start < strOfInterest.length() - 1; start++) {
			// iterate over all substring positions
			for (int end = start + 1; end <= strOfInterest.length(); end++) {
				substringsToCheck.add(strOfInterest.substring(start, end));
			}
		}

		return substringsToCheck;
	}

	/**
	 * Helper method to ensure all nodes for a given namespace hierarchy exist.
	 * For example, if given an input string "math.transform.fft", nodes for
	 * "math", "transform" and "fft" would be created. The "fft" node would be
	 * returned.
	 *
	 * @param parent
	 *            Top-level root of the resulting hierarchy
	 * @param namespaceMap
	 *            Map of namespace names to nodes
	 * @param namespace
	 *            Linear string representation of namespace hierarchy
	 * @return The node for the final "leaf" namespace in the given hierarchy
	 *         (e.g. the node to add any concrete Op implementations)
	 */
	private OpTreeTableNode buildNamespaceHierarchy(final OpTreeTableNode parent,
			final Map<String, OpTreeTableNode> namespaceMap, final String namespace) {

		final StringBuilder sb = new StringBuilder();

		OpTreeTableNode prevParent = parent;
		// Iterate over all namespaces in the namespace string
		// For each namespace, look up the corresponding node, creating it if
		// the node does not already exist.
		for (final String ns : namespace.split("\\.")) {
			sb.append(ns);
			final String key = sb.toString().toLowerCase(Locale.getDefault());
			OpTreeTableNode nsNode = namespaceMap.get(key);
			if (nsNode == null) {
				nsNode = new OpTreeTableNode(ns);
				namespaceMap.put(key, nsNode);
				prevParent.add(nsNode);
			}
			prevParent = nsNode;
		}

		return prevParent;
	}

	/**
	 * Helper method to update the widths array to track the longest strings in
	 * each column.
	 */
	private void updateWidths(final int[] colWidths, final String... colContents) {
		for (int i = 0; i < Math.min(colWidths.length, colContents.length); i++) {
			colWidths[i] = Math.max(colWidths[i], colContents[i].length());
		}
	}

	/**
	 * Helper method when successfully copying cell contents. Updates icons,
	 * visual feedback and timers.
	 */
	private void copyPass() {
		setSuccessIcon(opSuccess);
		successLabel.setText("copied ");
		successTimer.restart();
	}

	/**
	 * Helper method when copying fails. Updates icons, visual feedback and
	 * timers.
	 */
	private void selectFail() {
		setSuccessIcon(opFail);
		successLabel.setText("no selection ");
		successTimer.restart();
	}

	/**
	 * Helper method that sets and displays the success status icon.
	 *
	 * @param icon
	 *            Image to place in the "success status" spot of the UI
	 *            (typically to indicate success or failure)
	 */
	private void setSuccessIcon(final ImageIcon icon) {
		successLabel.setVisible(true);
		successLabel.setIcon(icon);
	}

	/**
	 * Helper method to get a properly formatted name. {@code name} is tried
	 * first, then {@code backupName} if needed (i.e. {@code name} is
	 * {@code null} or empty).
	 * <p>
	 * The resulting string is trimmed and set to lowercase.
	 * </p>
	 */
	private String getName(String name, final String backupName) {
		if (name == null || name.isEmpty())
			name = backupName;

		return name == null ? "" : name.trim();
	}

	/**
	 * @return The node of the currently selected row of the Op tree-table.
	 */
	private OpTreeTableNode getSelectedNode() {
		final int row = treeTable.getSelectedRow();
		if (row < 0)
			return null;

		return getNodeAtRow(row);
	}

	/**
	 * @return The node of the specified row of the Op tree-table.
	 */
	private OpTreeTableNode getNodeAtRow(final int row) {
		final TreePath path = treeTable.getPathForRow(row);
		return path == null ? null : (OpTreeTableNode) path.getPath()[path.getPathCount() - 1];
	}

	

	// -- Helper classes --

	/**
	 * Helper class for expensive operations that should run off the EDT, but
	 * need to regularly poll state to check if they have been canceled.
	 */
	private abstract class InterruptableRunner implements Runnable {
		private boolean stop = false;
	
		/**
		 * @return {@code true} if this runner has been instructed to stop
		 */
		public synchronized boolean poll() {
			return stop;
		}
	
		/**
		 * Tell this runner to stop its current operation
		 */
		public synchronized void stop() {
			stop = true;
		}
	
		/**
		 * @return {@code true} if this runner has finished, naturally or by
		 *         termination.
		 */
		public synchronized boolean isDone() {
			return stop;
		}
	}

	/**
	 * {@link InterruptableRunner} for filtering the Op tree-table based on a
	 * query string. Since only one view on the Op data can be active at a time,
	 * once a filter request comes in we no longer care about any previous
	 * request.
	 */
	private class FilterRunner extends InterruptableRunner {
		private final String text;
	
		public FilterRunner(final String text) {
			this.text = text;
		}
	
		@Override
		public synchronized void stop() {
			super.stop();
			setProgress(0);
		}
	
		@Override
		public void run() {
			// We apply the filter on a temporary, non-visible model first. If
			// this operation is not canceled then we can replace the displayed
			// model with this filtered version.
			final OpTreeTableModel tempModel = new OpTreeTableModel(simple);
			final OpTreeTableNode filtered = applyFilter(text.toLowerCase(Locale.getDefault()),
					simple ? smplTries : advTries);
	
			if (filtered == null)
				return;
	
			tempModel.getRoot().add(filtered);
	
			if (poll())
				return;
	
			try {
				SwingUtilities.invokeAndWait(new Runnable() {
	
					@Override
					public void run() {
						// Don't update AWT stuff off the EDT
						treeTable.setTreeTableModel(tempModel);
	
						// When filtering we ignore namespaces, so we display a
						// more table-based view. This allows items of interest
						// to be directly visible without intermingling cruft.
						treeTable.expandAll();
					}
				});
			} catch (InvocationTargetException | InterruptedException exc) {
				logService.error(exc);
			}
		}
	
		/**
		 * Parse the given filter with each {@link Trie}. Each parse is scored
		 * by scoring each emitted token and summing these scores: e.g. if a
		 * trie containing ["a", "ah", "bah"] parses the string "bahflah" it
		 * would emit "ah" and "bah". Fragments are scored to prioritize long
		 * matches over numerous small matches. Highest scoring tries are used
		 * to look up their corresponding nodes, which are added in order of
		 * descending score until a threshold of "acceptable" scores is reached.
		 */
		private OpTreeTableNode applyFilter(final String filter, final Map<Trie, OpTreeTableNode> tries) {
	
			// this will be the root of the filtered tree
			final OpTreeTableNode parent = new OpTreeTableNode("ops", "# @OpService ops", "net.imagej.ops.OpService");
	
			// Intermediate data structure for mapping parse scores to the
			// node(s) with those scores.
			final Map<Integer, List<OpTreeTableNode>> scoredOps = new HashMap<>();

			// Each key is a score. This list contains all "kept" scores that
			// will be displayed in our filter results.
			final List<Integer> keys = new ArrayList<>();
	
			// How many top scores to keep
			// A higher threshold allows more "fuzziness"
			final int keep = 1;
			int count = 0;

			// Progress updates are emitted in intervals of this value
			// i.e. every 5%
			double nextProgress = 0.05;
	
			// For each Op, parse the filter text
			// Each fragment scores ((2 * length) - 1)
			for (final Trie trie : tries.keySet()) {
				count++;
				// If we've crossed a progress threshold, we update the status
				// bar. Also poll here to see if this run has been canceled.
				if (((double) count / tries.keySet().size()) >= nextProgress) {
					if (poll())
						return null;
					setProgress((int) (nextProgress * 100));
					nextProgress += 0.05;
				}
	
				final Collection<Emit> parse = trie.parseText(filter);
				int score = 0;
				for (final Emit e : parse)
					score += ((2 * e.getKeyword().length()) - 1);
	
				// get the positional index of this key
				final int pos = -(Collections.binarySearch(keys, score) + 1);
	
				// Same value as another score - add to existing list and record the score
				if (scoredOps.containsKey(score)) {
					scoredOps.get(score).add(tries.get(trie));
					if (!keys.contains(score))
						keys.add(score, pos);
				} else {
					// If we haven't filled our score quota yet
					// we can freely add this score.
					if (keys.size() < keep || pos > 0) {
						final List<OpTreeTableNode> ops = new ArrayList<>();
						ops.add(tries.get(trie));
						scoredOps.put(score, ops);
						keys.add(pos, score);
						// If we are bumping a score, remove the lowest
						// key
						if (keys.size() > keep) {
							scoredOps.remove(keys.remove(0));
						}
					}
				}
			}
	
			final List<OpTreeTableNode> children = parent.getChildren();
	
			// Add the Ops to our root node in descending score order
			for (int i = keys.size() - 1; i >= 0; i--) {
				final Integer key = keys.get(i);
				for (final OpTreeTableNode node : scoredOps.get(key))
					children.add(node);
			}
	
			setProgress(100);
	
			return parent;
		}
	}

	/**
	 * {@link InterruptableRunner} for reading HTML from a remote resource to
	 * populate the details pane. As the details pane only contains the contents
	 * of a single row, if a new request comes in, we can discard any previous
	 * request(s).
	 */
	private class HTMLFetcher extends InterruptableRunner {
	
		private final String url;
		private final String requestedClass;
		private final StringBuilder sb;
	
		public HTMLFetcher(final StringBuilder sb, final String url, final String requestedClass) {
			this.url = url;
			this.requestedClass = requestedClass;
			this.sb = sb;
		}
	
		@Override
		public void run() {
			// The full HTML for these Javadoc pages contains unnecessary
			// elements, like menus and headers, that pollute the HTML display.
			// The "div.contentContainer" has the meat of the Javadoc that we're
			// interested in. After reading the HTML we cache it to avoid future
			// remote connection requests.
			try {
				final org.jsoup.nodes.Document doc = Jsoup.connect(sb.toString()).get();
				final Elements elements = doc.select("div.header");
				elements.addAll(doc.select("div.contentContainer"));
				synchronized (elementsMap) {
					elementsMap.put(url, elements.html());
				}
			} catch (final IOException exc) {
				synchronized (elementsMap) {
					elementsMap.put(url, "Javadoc not available for: " + requestedClass);
				}
			}
			if (poll())
				return;

			// If this request was still desired, update the text pane contents
			// and scroll to the top position
			textPane.setText(elementsMap.get(url));
			scrollToTop();
	
			stop();
		}
	
	}

	/**
	 * Button for switching between user and developer views.
	 */
	private class ModeButton extends JButton {
		private final String toolTip = "Toggle User and Developer views";

		private final String simpleFilterLabel = "Filter Ops:  ";
		private final String advancedFilterLabel = "Filter Ops by Class:  ";

		/**
		 * Create the button and attach an {@link ActionListener} to change the
		 * state.
		 */
		public ModeButton() {
			setLabels(simple);

			addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(final ActionEvent e) {
					setState(!simple);
				}
			});
		}

		/**
		 * @param simple
		 *            Update all UI labels for the mode button and search field
		 *            based on current state.
		 */
		public void setLabels(final boolean simple) {
			setIcon(simple ? useView : devView);
			setToolTipText(toolTip);
			searchLabel.setText(simple ? simpleFilterLabel : advancedFilterLabel);
		}

	}

	/**
	 * {@link ActionListener} to open the ImageJ Ops wiki page when the
	 * help/wiki button is clicked.
	 */
	private class WikiButtonListener implements ActionListener {
		@Override
		public void actionPerformed(final ActionEvent e) {
			try {
				platformService.open(new URL("http://imagej.net/ImageJ_Ops"));
			} catch (final IOException exc) {
				logService.error(exc);
			}
		}
	}

	/**
	 * {@link ActionListener} to run the selected row's code snippet via the
	 * {@link OpService} when the run button is clicked.
	 */
	private class RunButtonListener implements ActionListener {
		@Override
		public void actionPerformed(final ActionEvent e) {
			final OpTreeTableNode selectedNode = getSelectedNode();
			CommandInfo cInfo;
			// Make sure we have a command to actually run
			if (selectedNode == null || (cInfo = selectedNode.getCommandInfo()) == null) {
				selectFail();
				return;
			}

			try {
				final String script = makeScript(cInfo);
				scriptService.run("op_browser.py", script, true);
			} catch (IOException | ScriptException | NoSuchFieldException | SecurityException | InstantiationException
					| IllegalAccessException | ClassNotFoundException exc) {
				logService.error(exc);
			}
		}

		/**
		 * Helper method to turn a {@link CommandInfo} into a script.
		 */
		private String makeScript(final CommandInfo cInfo) throws NoSuchFieldException, SecurityException,
				InstantiationException, IllegalAccessException, ClassNotFoundException {
			final StringBuffer sb = new StringBuffer();
			// Add @Parameters for the OpService and each command input.
			sb.append("# @OpService ops\n");

			final String imgRegex = ".*" + IMG_REGEX + ".*";
			final String imgPlusRegex = ".*" + IMGPLUS_REGEX + ".*";

			final List<String> inputNames = new ArrayList<>();
			for (final ModuleItem<?> in : cInfo.inputs()) {
				if (in.isRequired()) {
					sb.append("# @");
					// HACK - Use ImgPlus inputs for any "image" type
					// This avoids problems, e.g. not finding an available ArrayImg
					final String type = in.getType().getName();
					final String name = "input_" + in.getName();
					inputNames.add(name);

					if (type.matches(imgPlusRegex)) sb.append("ImgPlus");
					else if (type.matches(imgRegex)) sb.append("Img");
					else sb.append(type);

					sb.append(" ");
					sb.append(name);
					sb.append(" ");
					sb.append("\n");
				}
			}

			String outType = "";
			String outName = "";
			// If we have exactly one OUTPUT type, annotate it
			for (final ModuleItem<?> out : cInfo.outputs()) {
				if (outType.isEmpty()) {
					outType = out.getType().getName();
					outName = out.getName();
					// HACK - filter image types out
					if (outType.matches(imgPlusRegex)) outType = "ImgPlus";
					else if (outType.matches(imgRegex)) outType = "Img";
				}
				else {
					outType = null;
					break;
				}
			}

			// Declare and set the OUTPUT type if there was one
			if (outType != null && !outType.isEmpty()) {
				sb.append("# @OUTPUT ");
				sb.append(outType);
				sb.append(" ");
				sb.append(outName);
				sb.append("\n");
				sb.append(outName);
				sb.append(" = ");
			}

			// Call the Op via ops.run(OpName, params..)
			sb.append("ops.run(\"");
			sb.append(OpUtils.getOpName(cInfo));
			sb.append("\"");

			// Append the Parameter inputs to the Op call
			for (final String name : inputNames) {
				sb.append(", ");
				sb.append(name);
			}
			sb.append(")\n");

			return sb.toString();
		}
	}

	/**
	 * {@link ActionListener} to copy the contents for the currently
	 * selected row when the "copy" button is clicked.
	 */
	private class CopyButtonListener implements ActionListener {

		@Override
		public void actionPerformed(final ActionEvent e) {

			final int rowIndex = treeTable.getSelectedRow();
			final int colIndex = treeTable.getSelectedColumn();

			String toCopy;

			// Get string at selected column/row. If no column selected,
			// default to the code snippet.
			if (rowIndex < 0)
				toCopy = "";
			else if (colIndex < 0)
				toCopy = getSelectedNode().getCodeCall();
			else
				toCopy = treeTable.getValueAt(rowIndex, colIndex).toString();

			if (toCopy.isEmpty()) {
				selectFail();
			} else {
				final StringSelection stringSelection = new StringSelection(toCopy);
				final Clipboard clpbrd = Toolkit.getDefaultToolkit().getSystemClipboard();
				clpbrd.setContents(stringSelection, null);
				copyPass();
			}
		}

	}
}
