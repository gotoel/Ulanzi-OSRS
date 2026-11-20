package com.ulanzi.osrs;

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
import java.util.Arrays;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * The virtual clock on screen: a small borderless window of LEDs that can be dragged
 * anywhere, resized from its bottom-right corner, and kept above other windows.
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
	private static final int FRAME_MS = 33;
	private static final int SCREEN_MARGIN = 24;
	private static final Color BEZEL = new Color(0x0B, 0x0B, 0x0B);
	private static final Color UNLIT = new Color(0x1E, 0x1E, 0x1E);
	private static final int[] SIZE_PRESETS = {6, 10, 16, 24};

	private final VirtualClock clock;
	private final UlanziConfig config;
	private final ConfigManager configManager;

	private JFrame frame;
	private LedPanel panel;
	private Timer timer;
	private boolean clientFocused = true;

	@Inject
	VirtualClockWindow(VirtualClock clock, UlanziConfig config, ConfigManager configManager)
	{
		this.clock = clock;
		this.config = config;
		this.configManager = configManager;
	}

	/** Opens, closes, or updates the window to match the config. */
	void apply()
	{
		SwingUtilities.invokeLater(this::applyNow);
	}

	void close()
	{
		SwingUtilities.invokeLater(this::closeNow);
	}

	void setClientFocused(boolean focused)
	{
		SwingUtilities.invokeLater(() ->
		{
			clientFocused = focused;
			applyNow();
		});
	}

	private boolean wanted()
	{
		ClockOutput output = config.clockOutput();
		return output != null && output.toVirtual() && !GraphicsEnvironment.isHeadless();
	}

	private void applyNow()
	{
		if (!wanted())
		{
			closeNow();
			return;
		}
		if (frame == null)
		{
			open();
		}

		int pitch = pitch();
		panel.setPitch(pitch);
		panel.setStyle(config.virtualStyle());
		Dimension size = panel.getPreferredSize();
		if (!frame.getSize().equals(size))
		{
			frame.setSize(size);
		}
		frame.setAlwaysOnTop(config.virtualAlwaysOnTop());
		applyOpacity();
		panel.setLocked(config.virtualLocked());

		boolean show = !(config.virtualOnlyWhenUnfocused() && clientFocused);
		if (frame.isVisible() != show)
		{
			frame.setVisible(show);
		}
		if (show && !timer.isRunning())
		{
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
		float opacity = Math.max(20, Math.min(100, config.virtualOpacity())) / 100f;
		try
		{
			GraphicsDevice device = frame.getGraphicsConfiguration().getDevice();
			if (device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT))
			{
				if (Math.abs(frame.getOpacity() - opacity) > 0.001f)
				{
					frame.setOpacity(opacity);
				}
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

	private void set(String key, Object value)
	{
		configManager.setConfiguration(UlanziConfig.GROUP, key, value);
	}

	private JPopupMenu menu()
	{
		JPopupMenu menu = new JPopupMenu();

		JCheckBoxMenuItem onTop = new JCheckBoxMenuItem("Always on top", config.virtualAlwaysOnTop());
		onTop.addActionListener(e -> set("virtualAlwaysOnTop", onTop.isSelected()));
		menu.add(onTop);

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

		menu.addSeparator();
		JMenuItem close = new JMenuItem("Close virtual clock");
		close.setToolTipText("Pages go back to the real clock. Turn it on again under Virtual clock in the settings.");
		close.addActionListener(e -> set("clockOutput", ClockOutput.REAL));
		menu.add(close);
		return menu;
	}

	private final class WindowMouse extends MouseAdapter
	{
		private Point pressedOnScreen;
		private Point frameAtPress;
		private boolean resizing;
		private boolean moved;
		private int resizedPitch;

		@Override
		public void mousePressed(MouseEvent e)
		{
			if (e.isPopupTrigger())
			{
				menu().show(panel, e.getX(), e.getY());
				return;
			}
			if (!SwingUtilities.isLeftMouseButton(e) || config.virtualLocked())
			{
				return;
			}
			pressedOnScreen = e.getLocationOnScreen();
			frameAtPress = frame.getLocation();
			resizing = panel.inResizeCorner(e.getPoint());
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
			if (resizing)
			{
				int width = e.getXOnScreen() - frame.getX();
				int pitch = Math.max(MIN_PITCH, Math.min(MAX_PITCH,
					Math.round((width - 2f * LedPanel.bezel(panel.pitch)) / Frame.WIDTH)));
				if (pitch != panel.pitch)
				{
					panel.setPitch(pitch);
					frame.setSize(panel.getPreferredSize());
					resizedPitch = pitch;
				}
				return;
			}
			int dx = e.getXOnScreen() - pressedOnScreen.x;
			int dy = e.getYOnScreen() - pressedOnScreen.y;
			frame.setLocation(frameAtPress.x + dx, frameAtPress.y + dy);
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
		}

		@Override
		public void mouseMoved(MouseEvent e)
		{
			if (config.virtualLocked())
			{
				panel.setCursor(Cursor.getDefaultCursor());
				return;
			}
			panel.setCursor(Cursor.getPredefinedCursor(panel.inResizeCorner(e.getPoint())
				? Cursor.SE_RESIZE_CURSOR : Cursor.MOVE_CURSOR));
		}
	}

	/** Paints the 32x8 frame as LEDs on a dark bezel. */
	static final class LedPanel extends JComponent
	{
		/** LEDs look brighter than the same value on a monitor; this lifts dim colours to match. */
		private static final double GAMMA = 0.75;

		private final int[] frame = Frame.blank();
		private final int[] shown = Frame.blank();
		private double brightness = 1.0;
		private int pitch = 10;
		private LedStyle style = LedStyle.ROUND;
		private boolean locked;
		private VirtualClock clock;

		LedPanel()
		{
			setOpaque(true);
			setToolTipText(null);
		}

		void attach(VirtualClock clock)
		{
			this.clock = clock;
		}

		static int bezel(int pitch)
		{
			return Math.max(3, pitch / 2);
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

		boolean inResizeCorner(Point point)
		{
			int grip = Math.max(10, pitch);
			return !locked && point.x >= getWidth() - grip && point.y >= getHeight() - grip;
		}

		@Override
		public Dimension getPreferredSize()
		{
			int bezel = bezel(pitch);
			return new Dimension(Frame.WIDTH * pitch + 2 * bezel, Frame.HEIGHT * pitch + 2 * bezel);
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

				int bezel = bezel(pitch);
				int inset = style == LedStyle.SQUARE ? (pitch >= 6 ? 1 : 0) : Math.max(1, Math.round(pitch * 0.12f));
				int dot = Math.max(1, pitch - 2 * inset);

				if (style == LedStyle.GLOW)
				{
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

				for (int y = 0; y < Frame.HEIGHT; y++)
				{
					for (int x = 0; x < Frame.WIDTH; x++)
					{
						int rgb = Frame.get(shown, x, y);
						g.setColor(rgb == 0 ? UNLIT : lit(rgb));
						int px = bezel + x * pitch + inset;
						int py = bezel + y * pitch + inset;
						if (style == LedStyle.SQUARE)
						{
							g.fillRect(px, py, dot, dot);
						}
						else
						{
							g.fillOval(px, py, dot, dot);
						}
					}
				}
			}
			finally
			{
				g.dispose();
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
