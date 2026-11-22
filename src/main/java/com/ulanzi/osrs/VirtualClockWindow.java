package com.ulanzi.osrs;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.Arrays;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * The virtual clock on screen: a small borderless window of LEDs that can be dragged
 * anywhere and resized from any edge. Its few controls stay out of sight until the mouse is
 * over it.
 *
 * It never takes keyboard focus, so showing it, or clicking it to move it, never pulls the
 * game out from under your hands. Every change made from the window is written to the
 * config, which then drives the window, so the settings panel and the window always agree.
 * Everything here runs on the Swing thread; the public methods hand their work to it.
 */
@Slf4j
@Singleton
class VirtualClockWindow
{
	static final int MIN_PITCH = 4;
	static final int MAX_PITCH = 48;
	static final int LEFT = 1;
	static final int RIGHT = 2;
	static final int TOP = 4;
	static final int BOTTOM = 8;
	private static final int EDGE = 5;
	private static final int FRAME_MS = 33;
	private static final int SCREEN_MARGIN = 24;
	private static final int OPACITY_WHEEL_STEP = 5;
	private static final int MIN_OPACITY = 20;
	private static final int[] OPACITY_STEPS = {100, 75, 50, 30};
	private static final Color BEZEL = new Color(0x05, 0x05, 0x05);
	private static final Color UNLIT = new Color(0x1E, 0x1E, 0x1E);
	private static final int[] SIZE_PRESETS = {6, 10, 16, 24};

	/** The buttons shown on hover, left to right. */
	enum Control
	{
		PIN("Always on top"),
		OPACITY("Opacity: click to step, or scroll over the clock"),
		MENU("More options"),
		CLOSE("Close the virtual clock");

		final String tip;

		Control(String tip)
		{
			this.tip = tip;
		}
	}

	private final VirtualClock clock;
	private final UlanziConfig config;
	private final ConfigManager configManager;

	private JFrame frame;
	private LedPanel panel;
	private Timer timer;
	private boolean clientFocused = true;
	private boolean allowed;
	private Runnable closeHandler;

	@Inject
	VirtualClockWindow(VirtualClock clock, UlanziConfig config, ConfigManager configManager)
	{
		this.clock = clock;
		this.config = config;
		this.configManager = configManager;
	}

	/** Whether the plugin wants the window up at all right now, then matches the config. */
	void setAllowed(boolean allowed)
	{
		SwingUtilities.invokeLater(() ->
		{
			this.allowed = allowed;
			applyNow();
		});
	}

	void setCloseHandler(Runnable handler)
	{
		SwingUtilities.invokeLater(() -> closeHandler = handler);
	}

	void close()
	{
		SwingUtilities.invokeLater(() ->
		{
			allowed = false;
			closeNow();
		});
	}

	void setClientFocused(boolean focused)
	{
		SwingUtilities.invokeLater(() ->
		{
			clientFocused = focused;
			applyNow();
		});
	}

	private void applyNow()
	{
		if (!allowed || GraphicsEnvironment.isHeadless())
		{
			closeNow();
			return;
		}
		if (frame == null)
		{
			open();
		}

		panel.setPitch(pitch());
		panel.setStyle(config.virtualStyle());
		Dimension size = panel.getPreferredSize();
		if (!frame.getSize().equals(size))
		{
			frame.setSize(size);
		}
		frame.setAlwaysOnTop(config.virtualAlwaysOnTop());
		applyOpacity();
		panel.setLocked(config.virtualLocked());
		panel.setPinned(config.virtualAlwaysOnTop());

		boolean show = !(config.virtualOnlyWhenUnfocused() && clientFocused);
		if (frame.isVisible() != show)
		{
			frame.setVisible(show);
		}
		if (show && !timer.isRunning())
		{
			panel.tick();
			timer.start();
		}
		else if (!show)
		{
			timer.stop();
		}
	}

	private void open()
	{
		panel = new LedPanel();
		panel.attach(clock);
		frame = new JFrame("Ulanzi OSRS virtual clock");
		frame.setUndecorated(true);
		frame.setType(Window.Type.UTILITY);
		frame.setFocusableWindowState(false);
		frame.setAutoRequestFocus(false);
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.setContentPane(panel);
		panel.setPitch(pitch());
		frame.setSize(panel.getPreferredSize());
		frame.setLocation(startingLocation(frame.getSize()));

		WindowMouse mouse = new WindowMouse();
		panel.addMouseListener(mouse);
		panel.addMouseMotionListener(mouse);
		panel.addMouseWheelListener(mouse);

		timer = new Timer(FRAME_MS, e -> panel.tick());
		timer.setCoalesce(true);
	}

	private void closeNow()
	{
		if (timer != null)
		{
			timer.stop();
			timer = null;
		}
		if (frame != null)
		{
			frame.dispose();
			frame = null;
			panel = null;
		}
	}

	private int pitch()
	{
		return Math.max(MIN_PITCH, Math.min(MAX_PITCH, config.virtualSize()));
	}

	private void applyOpacity()
	{
		float opacity = Math.max(MIN_OPACITY, Math.min(100, config.virtualOpacity())) / 100f;
		try
		{
			GraphicsDevice device = frame.getGraphicsConfiguration().getDevice();
			if (device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT)
				&& Math.abs(frame.getOpacity() - opacity) > 0.001f)
			{
				frame.setOpacity(opacity);
			}
		}
		catch (RuntimeException ex)
		{
			log.debug("The virtual clock's opacity could not be set", ex);
		}
	}

	/**
	 * Where it was left, if that is still on a screen; otherwise the top-right corner of the
	 * main screen, so a monitor that has since been unplugged cannot strand it.
	 */
	private Point startingLocation(Dimension size)
	{
		Point saved = parseLocation(config.virtualLocation());
		if (saved != null && onScreen(new Rectangle(saved, size)))
		{
			return saved;
		}
		Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
		return new Point(screen.x + screen.width - size.width - SCREEN_MARGIN, screen.y + SCREEN_MARGIN);
	}

	static Point parseLocation(String text)
	{
		if (text == null)
		{
			return null;
		}
		String[] parts = text.split(",");
		if (parts.length != 2)
		{
			return null;
		}
		try
		{
			return new Point(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
		}
		catch (NumberFormatException ex)
		{
			return null;
		}
	}

	/** Enough of it is on some screen to grab and drag back. */
	private static boolean onScreen(Rectangle bounds)
	{
		for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices())
		{
			Rectangle visible = device.getDefaultConfiguration().getBounds().intersection(bounds);
			if (!visible.isEmpty() && visible.width >= 20 && visible.height >= 10)
			{
				return true;
			}
		}
		return false;
	}

	/** Which edges a point is on, as a mask of LEFT, RIGHT, TOP, and BOTTOM. */
	static int edgesAt(int x, int y, int width, int height)
	{
		int edges = 0;
		if (x < EDGE)
		{
			edges |= LEFT;
		}
		else if (x >= width - EDGE)
		{
			edges |= RIGHT;
		}
		if (y < EDGE)
		{
			edges |= TOP;
		}
		else if (y >= height - EDGE)
		{
			edges |= BOTTOM;
		}
		return edges;
	}

	/** The LED size whose window comes closest to this length, across this many LEDs. */
	static int pitchForLength(int length, int leds)
	{
		int best = MIN_PITCH;
		int bestGap = Integer.MAX_VALUE;
		for (int pitch = MIN_PITCH; pitch <= MAX_PITCH; pitch++)
		{
			int gap = Math.abs(leds * pitch + 2 * LedPanel.bezel(pitch) - length);
			if (gap < bestGap)
			{
				best = pitch;
				bestGap = gap;
			}
		}
		return best;
	}

	private static int cursorFor(int edges)
	{
		switch (edges)
		{
			case LEFT:
				return Cursor.W_RESIZE_CURSOR;
			case RIGHT:
				return Cursor.E_RESIZE_CURSOR;
			case TOP:
				return Cursor.N_RESIZE_CURSOR;
			case BOTTOM:
				return Cursor.S_RESIZE_CURSOR;
			case LEFT | TOP:
				return Cursor.NW_RESIZE_CURSOR;
			case RIGHT | TOP:
				return Cursor.NE_RESIZE_CURSOR;
			case LEFT | BOTTOM:
				return Cursor.SW_RESIZE_CURSOR;
			case RIGHT | BOTTOM:
				return Cursor.SE_RESIZE_CURSOR;
			default:
				return Cursor.MOVE_CURSOR;
		}
	}

	private void set(String key, Object value)
	{
		configManager.setConfiguration(UlanziConfig.GROUP, key, value);
	}

	private void press(Control control, MouseEvent e)
	{
		switch (control)
		{
			case PIN:
				set("virtualAlwaysOnTop", !config.virtualAlwaysOnTop());
				break;
			case OPACITY:
				set("virtualOpacity", nextOpacity(config.virtualOpacity()));
				break;
			case MENU:
				menu().show(panel, e.getX(), e.getY());
				break;
			case CLOSE:
				if (closeHandler != null)
				{
					closeHandler.run();
				}
				break;
			default:
				break;
		}
	}

	/** The next step down, and back to solid from the bottom. */
	static int nextOpacity(int current)
	{
		for (int step : OPACITY_STEPS)
		{
			if (step < current)
			{
				return step;
			}
		}
		return OPACITY_STEPS[0];
	}

	private void askForClockAddress()
	{
		Object answer = JOptionPane.showInputDialog(null,
			"The clock's IP address, shown on its panel. For example 192.168.1.50.\n"
				+ "Your hitpoints, prayer, run energy, special attack, and skill progress are sent to this address.",
			"Connect a real clock", JOptionPane.PLAIN_MESSAGE, null, null, config.host());
		if (answer != null)
		{
			set("host", answer.toString().trim());
		}
	}

	private JPopupMenu menu()
	{
		JPopupMenu menu = new JPopupMenu();

		JMenuItem connect = new JMenuItem("Connect a real clock...");
		connect.addActionListener(e -> SwingUtilities.invokeLater(this::askForClockAddress));
		menu.add(connect);
		menu.addSeparator();

		JCheckBoxMenuItem onTop = new JCheckBoxMenuItem("Always on top", config.virtualAlwaysOnTop());
		onTop.addActionListener(e -> set("virtualAlwaysOnTop", onTop.isSelected()));
		menu.add(onTop);

		JCheckBoxMenuItem loggedOut = new JCheckBoxMenuItem("Hide when logged out", config.virtualHideLoggedOut());
		loggedOut.addActionListener(e -> set("virtualHideLoggedOut", loggedOut.isSelected()));
		menu.add(loggedOut);

		JCheckBoxMenuItem tabbedOut = new JCheckBoxMenuItem("Only when tabbed out", config.virtualOnlyWhenUnfocused());
		tabbedOut.addActionListener(e -> set("virtualOnlyWhenUnfocused", tabbedOut.isSelected()));
		menu.add(tabbedOut);

		JCheckBoxMenuItem locked = new JCheckBoxMenuItem("Lock position", config.virtualLocked());
		locked.addActionListener(e -> set("virtualLocked", locked.isSelected()));
		menu.add(locked);

		JMenu size = new JMenu("LED size");
		ButtonGroup sizes = new ButtonGroup();
		for (int preset : SIZE_PRESETS)
		{
			JRadioButtonMenuItem item = new JRadioButtonMenuItem(preset + " px", pitch() == preset);
			item.addActionListener(e -> set("virtualSize", preset));
			sizes.add(item);
			size.add(item);
		}
		menu.add(size);

		JMenu style = new JMenu("LED style");
		ButtonGroup styles = new ButtonGroup();
		for (LedStyle option : LedStyle.values())
		{
			JRadioButtonMenuItem item = new JRadioButtonMenuItem(option.toString(), config.virtualStyle() == option);
			item.addActionListener(e -> set("virtualStyle", option));
			styles.add(item);
			style.add(item);
		}
		menu.add(style);

		JMenu idle = new JMenu("When idle, show");
		ButtonGroup idles = new ButtonGroup();
		for (IdleDisplay option : IdleDisplay.values())
		{
			JRadioButtonMenuItem item = new JRadioButtonMenuItem(option.toString(), config.virtualIdle() == option);
			item.addActionListener(e -> set("virtualIdle", option));
			idles.add(item);
			idle.add(item);
		}
		menu.add(idle);

		JMenu time = new JMenu("Time format");
		ButtonGroup times = new ButtonGroup();
		for (TimeFormat option : TimeFormat.values())
		{
			JRadioButtonMenuItem item = new JRadioButtonMenuItem(option.toString(), config.virtualTimeFormat() == option);
			item.addActionListener(e -> set("virtualTimeFormat", option));
			times.add(item);
			time.add(item);
		}
		menu.add(time);
		return menu;
	}

	private final class WindowMouse extends MouseAdapter
	{
		private Point pressedOnScreen;
		private Rectangle boundsAtPress;
		private int edges;
		private boolean moved;
		private int resizedPitch;

		@Override
		public void mouseEntered(MouseEvent e)
		{
			panel.setHover(true, e.getPoint());
		}

		@Override
		public void mouseExited(MouseEvent e)
		{
			if (pressedOnScreen == null)
			{
				panel.setHover(false, null);
			}
		}

		@Override
		public void mousePressed(MouseEvent e)
		{
			if (e.isPopupTrigger())
			{
				menu().show(panel, e.getX(), e.getY());
				return;
			}
			if (!SwingUtilities.isLeftMouseButton(e))
			{
				return;
			}
			Control control = panel.controlAt(e.getPoint());
			if (control != null)
			{
				press(control, e);
				return;
			}
			if (config.virtualLocked())
			{
				return;
			}
			pressedOnScreen = e.getLocationOnScreen();
			boundsAtPress = frame.getBounds();
			edges = edgesAt(e.getX(), e.getY(), panel.getWidth(), panel.getHeight());
			moved = false;
			resizedPitch = -1;
		}

		@Override
		public void mouseDragged(MouseEvent e)
		{
			if (pressedOnScreen == null)
			{
				return;
			}
			int dx = e.getXOnScreen() - pressedOnScreen.x;
			int dy = e.getYOnScreen() - pressedOnScreen.y;
			if (edges != 0)
			{
				resize(dx, dy);
				return;
			}
			frame.setLocation(boundsAtPress.x + dx, boundsAtPress.y + dy);
			moved = true;
		}

		/** Keeps the clock's shape, with the edge opposite the one being dragged staying put. */
		private void resize(int dx, int dy)
		{
			int pitch = 0;
			if ((edges & (LEFT | RIGHT)) != 0)
			{
				int width = boundsAtPress.width + ((edges & RIGHT) != 0 ? dx : -dx);
				pitch = pitchForLength(width, Frame.WIDTH);
			}
			if ((edges & (TOP | BOTTOM)) != 0)
			{
				int height = boundsAtPress.height + ((edges & BOTTOM) != 0 ? dy : -dy);
				pitch = Math.max(pitch, pitchForLength(height, Frame.HEIGHT));
			}
			if (pitch == panel.pitch)
			{
				return;
			}
			panel.setPitch(pitch);
			Dimension size = panel.getPreferredSize();
			int x = (edges & LEFT) != 0 ? boundsAtPress.x + boundsAtPress.width - size.width : boundsAtPress.x;
			int y = (edges & TOP) != 0 ? boundsAtPress.y + boundsAtPress.height - size.height : boundsAtPress.y;
			frame.setBounds(x, y, size.width, size.height);
			resizedPitch = pitch;
			moved = true;
		}

		@Override
		public void mouseReleased(MouseEvent e)
		{
			if (e.isPopupTrigger())
			{
				menu().show(panel, e.getX(), e.getY());
				return;
			}
			if (pressedOnScreen == null)
			{
				return;
			}
			pressedOnScreen = null;
			if (moved)
			{
				Point at = frame.getLocation();
				set("virtualLocation", at.x + "," + at.y);
			}
			if (resizedPitch > 0)
			{
				set("virtualSize", resizedPitch);
			}
			panel.setHover(panel.contains(e.getPoint()), e.getPoint());
		}

		@Override
		public void mouseMoved(MouseEvent e)
		{
			panel.setHover(true, e.getPoint());
			Control control = panel.controlAt(e.getPoint());
			if (control != null)
			{
				panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			}
			else if (config.virtualLocked())
			{
				panel.setCursor(Cursor.getDefaultCursor());
			}
			else
			{
				panel.setCursor(Cursor.getPredefinedCursor(
					cursorFor(edgesAt(e.getX(), e.getY(), panel.getWidth(), panel.getHeight()))));
			}
		}

		@Override
		public void mouseWheelMoved(MouseWheelEvent e)
		{
			int next = config.virtualOpacity() - e.getWheelRotation() * OPACITY_WHEEL_STEP;
			next = Math.max(MIN_OPACITY, Math.min(100, next));
			if (next != config.virtualOpacity())
			{
				set("virtualOpacity", next);
			}
		}
	}

	/** Paints the 32x8 frame as LEDs on a dark bezel, with the hover controls over it. */
	static final class LedPanel extends JComponent
	{
		/** LEDs look brighter than the same value on a monitor; this lifts dim colours to match. */
		private static final double GAMMA = 0.75;
		private static final Color PILL = new Color(0x20, 0x20, 0x20, 225);
		private static final Color CONTROL_HOVER = new Color(0x48, 0x48, 0x48);
		private static final Color ICON = new Color(0xB8, 0xB8, 0xB8);
		private static final Color ICON_ON = new Color(0x4F, 0xE0, 0x8A);
		private static final Color CLOSE_HOVER = new Color(0xC8, 0x3C, 0x32);

		private final int[] frame = Frame.blank();
		private final int[] shown = Frame.blank();
		private double brightness = 1.0;
		private int pitch = 10;
		private LedStyle style = LedStyle.CLOCK;
		private boolean locked;
		private boolean pinned;
		private boolean hover;
		private Control hovered;
		private VirtualClock clock;

		LedPanel()
		{
			setOpaque(true);
			setToolTipText("");
		}

		void attach(VirtualClock clock)
		{
			this.clock = clock;
		}

		static int bezel(int pitch)
		{
			return Math.max(3, pitch / 2);
		}

		static Dimension sizeFor(int pitch)
		{
			int bezel = bezel(pitch);
			return new Dimension(Frame.WIDTH * pitch + 2 * bezel, Frame.HEIGHT * pitch + 2 * bezel);
		}

		void setPitch(int pitch)
		{
			if (this.pitch != pitch)
			{
				this.pitch = pitch;
				revalidate();
				repaint();
			}
		}

		void setStyle(LedStyle style)
		{
			if (style != null && this.style != style)
			{
				this.style = style;
				repaint();
			}
		}

		void setLocked(boolean locked)
		{
			this.locked = locked;
		}

		void setPinned(boolean pinned)
		{
			if (this.pinned != pinned)
			{
				this.pinned = pinned;
				repaint();
			}
		}

		void setHover(boolean hover, Point at)
		{
			Control over = hover && at != null ? buttonAt(at, hover) : null;
			if (this.hover != hover || hovered != over)
			{
				this.hover = hover;
				hovered = over;
				repaint();
			}
		}

		private int buttonSize()
		{
			return Math.max(14, Math.min(24, Math.round(pitch * 1.7f)));
		}

		private Rectangle buttonBounds(Control control)
		{
			int size = buttonSize();
			int count = Control.values().length;
			int x = getWidth() - EDGE - 1 - size * (count - control.ordinal());
			return new Rectangle(x, EDGE + 1, size, size);
		}

		Control controlAt(Point point)
		{
			return buttonAt(point, hover);
		}

		private Control buttonAt(Point point, boolean showing)
		{
			if (!showing)
			{
				return null;
			}
			for (Control control : Control.values())
			{
				if (buttonBounds(control).contains(point))
				{
					return control;
				}
			}
			return null;
		}

		@Override
		public String getToolTipText(MouseEvent event)
		{
			Control control = controlAt(event.getPoint());
			return control == null ? null : control.tip;
		}

		@Override
		public Dimension getPreferredSize()
		{
			return sizeFor(pitch);
		}

		/** Renders the clock and repaints only when a pixel has actually changed. */
		void tick()
		{
			if (clock == null)
			{
				return;
			}
			clock.render(frame);
			double level = clock.brightness();
			if (!Arrays.equals(frame, shown) || level != brightness)
			{
				System.arraycopy(frame, 0, shown, 0, frame.length);
				brightness = level;
				repaint();
			}
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			try
			{
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.setColor(BEZEL);
				g.fillRect(0, 0, getWidth(), getHeight());
				if (style == LedStyle.GLOW)
				{
					paintHalos(g);
				}
				paintLeds(g);
				if (hover)
				{
					paintControls(g);
				}
			}
			finally
			{
				g.dispose();
			}
		}

		private void paintHalos(Graphics2D g)
		{
			int bezel = bezel(pitch);
			int halo = Math.round(pitch * 1.7f);
			for (int y = 0; y < Frame.HEIGHT; y++)
			{
				for (int x = 0; x < Frame.WIDTH; x++)
				{
					int rgb = Frame.get(shown, x, y);
					if (rgb == 0)
					{
						continue;
					}
					Color c = lit(rgb);
					g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 60));
					int cx = bezel + x * pitch + pitch / 2;
					int cy = bezel + y * pitch + pitch / 2;
					g.fillOval(cx - halo / 2, cy - halo / 2, halo, halo);
				}
			}
		}

		private void paintLeds(Graphics2D g)
		{
			int bezel = bezel(pitch);
			boolean squared = style == LedStyle.CLOCK || style == LedStyle.SQUARE;
			int inset = squared ? (pitch >= 6 ? Math.max(1, pitch / 10) : 0) : Math.max(1, Math.round(pitch * 0.12f));
			int dot = Math.max(1, pitch - 2 * inset);
			if (squared)
			{
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
			}
			for (int y = 0; y < Frame.HEIGHT; y++)
			{
				for (int x = 0; x < Frame.WIDTH; x++)
				{
					int rgb = Frame.get(shown, x, y);
					// The real panel's dark pixels vanish into its black diffuser.
					if (rgb == 0 && style == LedStyle.CLOCK)
					{
						continue;
					}
					g.setColor(rgb == 0 ? UNLIT : lit(rgb));
					int px = bezel + x * pitch + inset;
					int py = bezel + y * pitch + inset;
					if (squared)
					{
						g.fillRect(px, py, dot, dot);
					}
					else
					{
						g.fillOval(px, py, dot, dot);
					}
				}
			}
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		}

		private void paintControls(Graphics2D g)
		{
			Rectangle first = buttonBounds(Control.PIN);
			Rectangle last = buttonBounds(Control.CLOSE);
			g.setColor(PILL);
			g.fillRoundRect(first.x, first.y, last.x + last.width - first.x, first.height, 6, 6);
			for (Control control : Control.values())
			{
				Rectangle b = buttonBounds(control);
				if (control == hovered)
				{
					g.setColor(control == Control.CLOSE ? CLOSE_HOVER : CONTROL_HOVER);
					g.fillRoundRect(b.x, b.y, b.width, b.height, 6, 6);
				}
				paintIcon(g, control, b);
			}
		}

		private void paintIcon(Graphics2D g, Control control, Rectangle b)
		{
			int cx = b.x + b.width / 2;
			int cy = b.y + b.height / 2;
			int r = Math.max(3, b.width / 4);
			g.setStroke(new BasicStroke(Math.max(1.3f, b.width / 12f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.setColor(control == Control.PIN && pinned ? ICON_ON : control == hovered && control == Control.CLOSE ? Color.WHITE : ICON);
			switch (control)
			{
				case PIN:
					// A pin: a head, a flared collar, and a point.
					g.drawLine(cx - r / 2, cy - r, cx + r / 2, cy - r);
					g.drawLine(cx - r / 2, cy - r, cx - r / 2, cy);
					g.drawLine(cx + r / 2, cy - r, cx + r / 2, cy);
					g.drawLine(cx - r, cy, cx + r, cy);
					g.drawLine(cx, cy, cx, cy + r + 1);
					break;
				case OPACITY:
					g.drawOval(cx - r, cy - r, 2 * r, 2 * r);
					g.fillArc(cx - r, cy - r, 2 * r, 2 * r, 90, 180);
					break;
				case MENU:
					for (int i = -1; i <= 1; i++)
					{
						int x = cx + i * (r + 1);
						g.fillOval(x - 1, cy - 1, 3, 3);
					}
					break;
				case CLOSE:
					g.drawLine(cx - r, cy - r, cx + r, cy + r);
					g.drawLine(cx + r, cy - r, cx - r, cy + r);
					break;
				default:
					break;
			}
		}

		private Color lit(int rgb)
		{
			return new Color(channel(rgb >> 16), channel(rgb >> 8), channel(rgb));
		}

		private int channel(int value)
		{
			int v = value & 0xFF;
			if (v == 0)
			{
				return 0;
			}
			double scaled = 255 * Math.pow(v / 255.0, GAMMA) * brightness;
			return Math.max(0, Math.min(255, (int) Math.round(scaled)));
		}
	}
}
