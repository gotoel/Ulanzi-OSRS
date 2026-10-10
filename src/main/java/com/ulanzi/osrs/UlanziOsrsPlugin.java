package com.ulanzi.osrs;

import com.google.inject.Provides;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.Experience;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.StatChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.xptracker.XpTrackerPlugin;
import net.runelite.client.plugins.xptracker.XpTrackerService;

@Slf4j
@PluginDescriptor(
	name = "Ulanzi OSRS Clock",
	description = "Shows AFK status, combat stats, and skilling activity on an AWTRIX NG Ulanzi TC001",
	tags = {"ulanzi", "awtrix", "afk", "hitpoints", "prayer", "skilling", "clock"}
)
@PluginDependency(XpTrackerPlugin.class)
public class UlanziOsrsPlugin extends Plugin
{
	private static final long AFK_REASSERT_MS = 5_000L;
	/**
	 * Wall rocks drop the pickaxe animation between swings while the rock is still
	 * live. The same window the mining plugin waits before it calls that idle.
	 */
	static final long WALL_MINE_GAP_MS = 1_800L;
	private static final int BLOODWOOD_FULL_PROGRESS = 3_800;
	private static final long AFK_EYES_REASSERT_MS = 700L;
	private static final Color HP_FLASH_OFF = new Color(40, 0, 0);
	private static final Color PRAYER_FLASH_OFF = new Color(0, 20, 40);
	private static final Color SPEC_FLASH_OFF = new Color(40, 20, 0);
	/**
	 * How long the special attack alert holds the panel. The bar stays where it got to
	 * afterwards, so unlike the two warnings there is no state to come out of: it says
	 * its piece and gives the panel back.
	 */
	private static final long SPEC_READY_MS = 3_000L;
	/** How long a reading that has just dropped is left showing behind the new one. */
	private static final long GHOST_MS = 1_200L;
	/** How long the focus page holds a stat before a smaller change can take it. */
	private static final long FOCUS_HOLD_MS = 2_500L;
	private static final int PANEL_WIDTH = 32;
	private static final int ICON_WIDTH = 8;
	private static final int CHAR_WIDTH = 4;
	private static final int SPACE_WIDTH = 2;
	private static final Skill[] MELEE_SKILLS = {Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE};
	private static final Color XP_RATE_COLOR = Color.WHITE;
	private static final Color XP_DROP_COLOR = Color.WHITE;
	static final long PULSE_MS = 1_000L;
	/** Full colour, with a little headroom for anything not already maxed out. */
	static final double PULSE_PEAK = 1.25;
	/** Where the panel sits between drops, so white has somewhere to brighten from. */
	static final double PULSE_REST = 0.5;
	private static final long PULSE_FRAME_MS = 120L;
	private static final String CONFIG_VERSION_KEY = "configVersion";
	private static final int CONFIG_VERSION = 3;
	/** How long a page put up from the settings panel stays there. */
	private static final long PREVIEW_MS = 6_000L;
	/**
	 * How long after the last swing of an axe a forestry event still counts as yours.
	 * Events start in the seconds after a tree falls, which is exactly when you are not
	 * chopping anything, so the alert cannot ask for an axe to be swinging right now.
	 */
	private static final long CHOPPING_GRACE_MS = 60_000L;
	/**
	 * How long after the last sign of a fight you still count as fighting. Attack speeds
	 * run to six ticks, and a target takes a moment to answer, so anything shorter has
	 * the panel deciding a fight is over in the middle of one.
	 */
	private static final long COMBAT_GRACE_MS = 10_000L;
	/** How long a swap of what is on the panel takes, down to the dip and back. */
	static final long FADE_MS = 480L;
	/** How far down the panel goes at the bottom of a swap. */
	static final double FADE_DIP = 0.25;
	private static final long FADE_FRAME_MS = 80L;
	/** Objects and NPCs are counted in the same set, so their keys are kept apart. */
	private static final long OBJECT_KEY = 1L << 62;

	enum OverlayKind
	{
		AFK,
		LOW_HP,
		LOW_PRAYER,
		SPEC_READY,
		FORESTRY
	}

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private UlanziConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private AwtrixClient awtrixClient;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private XpTrackerService xpTrackerService;

	private boolean afkActive;
	private long lastAfkSentMs;
	private int lastHpAlertValue = Integer.MAX_VALUE;
	private int lastPrayerAlertValue = Integer.MAX_VALUE;
	private boolean wasLowHp;
	private boolean wasLowPray;
	private boolean wasSpecReady;
	/** The bar as it read last tick, or -1 before the first reading has come in. */
	private int lastSpec = -1;
	private long specReadyUntilMs;
	private int bigStatIndex;
	private final EnumMap<StatKind, Integer> statPercent = new EnumMap<>(StatKind.class);
	private final EnumMap<StatKind, Integer> ghostPercent = new EnumMap<>(StatKind.class);
	private final EnumMap<StatKind, Long> ghostUntilMs = new EnumMap<>(StatKind.class);
	private StatKind focusStat;
	private long focusUntilMs;
	private int focusChange;
	private long nextRotateMs;
	private long nextOverlayRotateMs;
	private int overlayRotateIndex;
	private OverlayKind currentOverlay;
	private boolean compactFlashOn;
	private boolean afkHeld;
	private final SkillActivityTracker activityTracker = new SkillActivityTracker();
	private final EnumMap<Skill, Integer> skillXp = new EnumMap<>(Skill.class);
	private final EnumMap<Skill, Integer> skillLevels = new EnumMap<>(Skill.class);
	private WorldPoint lastPlayerLocation;
	private int lastActionAnimation = -1;
	/** When a wall-rock swing last dropped to idle, or 0 when that pause is not on. */
	private long wallMineIdleSinceMs;
	private long lastCombatMs;
	private boolean inCombat;
	private boolean activityFromCombat;
	/** What the panel is showing, which lags what it wants to show across a swap. */
	private SkillActivity shownActivity;
	private boolean shownFromCombat;
	private SkillActivity fadeTo;
	private long fadeStartMs;
	private volatile ScheduledFuture<?> fadeFrames;
	/** How many skills are sharing the panel, so their turns can be counted off. */
	private int trainingCount = 1;
	private final SkillRotation rotation = new SkillRotation();
	private final XpRateTracker xpRates = new XpRateTracker();
	private final EnumMap<Skill, Integer> tickXpGains = new EnumMap<>(Skill.class);
	private Skill meleeSkill;
	private int pendingDropXp;
	private Skill pendingDropSkill;
	/**
	 * The pending drop split by skill. A drop written over a skill's own page can only
	 * speak for that skill, so it needs its share of the gains rather than the total.
	 */
	private final EnumMap<Skill, Integer> pendingDropGains = new EnumMap<>(Skill.class);
	private long nextDropMs;
	private long levelUpUntilMs;
	private volatile long pulseStartMs;
	private volatile ScheduledFuture<?> pulseFrames;
	private String inPlaceDropText;
	private long inPlaceDropUntilMs;
	private final ForestryTracker forestryTracker = new ForestryTracker();
	private ForestryEvent forestryEvent;
	private long forestryStartMs;
	private long forestryUntilMs;
	private boolean forestryHeld;
	private long lastChoppingMs;
	private volatile ScheduledFuture<?> forestryFrames;
	private volatile long previewUntilMs;
	private volatile ScheduledFuture<?> previewEnd;

	@Override
	protected void startUp()
	{
		resetState();
		migrateConfig();
		awtrixClient.setMessageSink(this::postChat);
		awtrixClient.setStatusSink(this::postStatus);
		postStatus(config.host() == null || config.host().trim().isEmpty()
			? "No clock address set."
			: "Not checked yet.");
	}

	@Override
	protected void shutDown()
	{
		awtrixClient.clearAll();
		awtrixClient.setMessageSink(null);
		awtrixClient.setStatusSink(null);
		resetState();
	}

	/**
	 * Settings that have been folded into other settings are carried across here, so an
	 * existing clock keeps behaving the way it did. Each step only writes a key that was
	 * actually set, which leaves a fresh install on the new defaults.
	 */
	private void migrateConfig()
	{
		int version = storedInt(CONFIG_VERSION_KEY, 0);
		if (version < 2)
		{
			migrateToV2();
		}
		if (version < 3)
		{
			migrateToV3();
		}
		configManager.setConfiguration(UlanziConfig.GROUP, CONFIG_VERSION_KEY, CONFIG_VERSION);
	}

	/**
	 * Version 2 folded each "Show X" checkbox into its style dropdown, dropped the
	 * compact-only flash toggles, and made thresholds a percent by default.
	 * Anyone who had set a threshold keeps it as points.
	 */
	private void migrateToV2()
	{
		migrateShowToStyle("showHitpoints", "hitpointsStyle");
		migrateShowToStyle("showPrayer", "prayerStyle");
		migrateShowToStyle("showEnergy", "energyStyle");
		migrateShowToStyle("showSpec", "specStyle");
		configManager.unsetConfiguration(UlanziConfig.GROUP, "compactFlashLowHp");
		configManager.unsetConfiguration(UlanziConfig.GROUP, "compactFlashLowPrayer");

		boolean customThreshold = configManager.getConfiguration(UlanziConfig.GROUP, "lowHitpointsThreshold") != null
			|| configManager.getConfiguration(UlanziConfig.GROUP, "lowPrayerThreshold") != null;
		if (customThreshold && configManager.getConfiguration(UlanziConfig.GROUP, "thresholdUnit") == null)
		{
			configManager.setConfiguration(UlanziConfig.GROUP, "thresholdUnit", ThresholdUnit.POINTS);
		}
	}

	/**
	 * Version 3 collapsed the pairs that had to be read together to know what the panel
	 * would do: a brightness mode beside a level, a blink switch beside its period, and
	 * three boxes per threshold warning. It also gave skill alternation its own timing,
	 * which used to borrow the Big layout's.
	 */
	private void migrateToV3()
	{
		if (anySet("brightnessMode", "brightness"))
		{
			boolean fixed = "FIXED".equals(storedString("brightnessMode", "DEVICE"));
			configManager.setConfiguration(UlanziConfig.GROUP, "brightness",
				fixed ? Math.max(AwtrixClient.MIN_FIXED_BRIGHTNESS, storedInt("brightness", 120)) : 0);
		}
		configManager.unsetConfiguration(UlanziConfig.GROUP, "brightnessMode");

		if (anySet("afkBlink", "afkBlinkMs"))
		{
			configManager.setConfiguration(UlanziConfig.GROUP, "afkBlinkMs",
				storedBoolean("afkBlink", false) ? storedInt("afkBlinkMs", 600) : 0);
		}
		configManager.unsetConfiguration(UlanziConfig.GROUP, "afkBlink");

		if (anySet("lowHitpointsEnabled", "lowHpFlash", "lowHpBeep"))
		{
			configManager.setConfiguration(UlanziConfig.GROUP, "lowHitpoints",
				alertStyle(storedBoolean("lowHitpointsEnabled", true), storedBoolean("lowHpFlash", true),
					storedBoolean("lowHpBeep", true)));
		}
		if (anySet("lowPrayerEnabled", "lowPrayerFlash", "lowPrayerBeep"))
		{
			configManager.setConfiguration(UlanziConfig.GROUP, "lowPrayer",
				alertStyle(storedBoolean("lowPrayerEnabled", true), storedBoolean("lowPrayerFlash", true),
					storedBoolean("lowPrayerBeep", false)));
		}
		for (String key : new String[] {"lowHitpointsEnabled", "lowHpFlash", "lowHpBeep",
			"lowPrayerEnabled", "lowPrayerFlash", "lowPrayerBeep"})
		{
			configManager.unsetConfiguration(UlanziConfig.GROUP, key);
		}

		if (storedBoolean("afkCompactLabel", false))
		{
			configManager.setConfiguration(UlanziConfig.GROUP, "afkTintMode",
				withLabel(storedString("afkTintMode", AfkTintMode.VALUES.name())));
		}
		configManager.unsetConfiguration(UlanziConfig.GROUP, "afkCompactLabel");

		// Alternation used to be counted off in the Big layout's rotate time, whichever
		// layout you were on, so that is what it keeps.
		if (anySet("statsRotateSeconds"))
		{
			configManager.setConfiguration(UlanziConfig.GROUP, "skillRotateSeconds",
				storedInt("statsRotateSeconds", 3));
		}

		configManager.unsetConfiguration(UlanziConfig.GROUP, "testConnection");
		configManager.unsetConfiguration(UlanziConfig.GROUP, "status");
	}

	static AlertStyle alertStyle(boolean enabled, boolean flash, boolean beep)
	{
		if (!enabled || (!flash && !beep))
		{
			return AlertStyle.OFF;
		}
		if (flash && beep)
		{
			return AlertStyle.FLASH_AND_BEEP;
		}
		return flash ? AlertStyle.FLASH : AlertStyle.BEEP;
	}

	/** The AFK label was its own checkbox; here it joins whatever the tint was set to. */
	static AfkTintMode withLabel(String tintMode)
	{
		if (AfkTintMode.VALUES.name().equals(tintMode))
		{
			return AfkTintMode.VALUES_AND_LABEL;
		}
		if (AfkTintMode.OFF.name().equals(tintMode))
		{
			return AfkTintMode.LABEL;
		}
		try
		{
			return AfkTintMode.valueOf(tintMode);
		}
		catch (IllegalArgumentException ex)
		{
			return AfkTintMode.VALUES_AND_LABEL;
		}
	}

	/** Config values arrive as the name they are stored under, or as nothing at all. */
	static <T extends Enum<T>> T parseEnum(Class<T> type, String value, T fallback)
	{
		if (value == null)
		{
			return fallback;
		}
		try
		{
			return Enum.valueOf(type, value.trim());
		}
		catch (IllegalArgumentException ex)
		{
			return fallback;
		}
	}

	private boolean anySet(String... keys)
	{
		for (String key : keys)
		{
			if (configManager.getConfiguration(UlanziConfig.GROUP, key) != null)
			{
				return true;
			}
		}
		return false;
	}

	private String storedString(String key, String fallback)
	{
		String value = configManager.getConfiguration(UlanziConfig.GROUP, key);
		return value == null ? fallback : value;
	}

	private boolean storedBoolean(String key, boolean fallback)
	{
		String value = configManager.getConfiguration(UlanziConfig.GROUP, key);
		return value == null ? fallback : Boolean.parseBoolean(value);
	}

	private int storedInt(String key, int fallback)
	{
		String value = configManager.getConfiguration(UlanziConfig.GROUP, key);
		if (value == null)
		{
			return fallback;
		}
		try
		{
			return Integer.parseInt(value.trim());
		}
		catch (NumberFormatException ex)
		{
			return fallback;
		}
	}

	private void migrateShowToStyle(String showKey, String styleKey)
	{
		String show = configManager.getConfiguration(UlanziConfig.GROUP, showKey);
		if (show == null)
		{
			return;
		}
		if (!Boolean.parseBoolean(show))
		{
			configManager.setConfiguration(UlanziConfig.GROUP, styleKey, StatStyle.OFF);
		}
		else if (configManager.getConfiguration(UlanziConfig.GROUP, styleKey) == null)
		{
			configManager.setConfiguration(UlanziConfig.GROUP, styleKey, StatStyle.VALUE);
		}
		configManager.unsetConfiguration(UlanziConfig.GROUP, showKey);
	}

	private void postChat(String message)
	{
		String formatted = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append("Ulanzi clock: ")
			.append(ChatColorType.NORMAL)
			.append(message)
			.build();
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(formatted)
			.build());
	}

	@Provides
	UlanziConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(UlanziConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!UlanziConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		// Written by the plugin rather than by anyone reading the panel, so neither is a
		// reason to redraw anything.
		if ("status".equals(event.getKey()) || CONFIG_VERSION_KEY.equals(event.getKey()))
		{
			return;
		}

		// Both of these read the value out of the event rather than back off the config,
		// so neither depends on the setter having landed before the event was posted.
		if ("preview".equals(event.getKey()))
		{
			showPreview(parseEnum(Preview.class, event.getNewValue(), Preview.OFF));
			return;
		}

		if ("profile".equals(event.getKey()))
		{
			applyProfile(parseEnum(Profile.class, event.getNewValue(), Profile.CUSTOM));
			return;
		}

		leaveProfileIfChanged(event.getKey(), event.getNewValue());

		if ("host".equals(event.getKey()))
		{
			postStatus(event.getNewValue() == null || event.getNewValue().trim().isEmpty()
				? "No clock address set."
				: "Not checked yet.");
		}

		if ("afkEnabled".equals(event.getKey()) && !"true".equals(event.getNewValue()))
		{
			awtrixClient.dismissAfk();
			afkHeld = false;
			afkActive = false;
			lastAfkSentMs = 0L;
		}

		if ("forestryEnabled".equals(event.getKey()) && !"true".equals(event.getNewValue()))
		{
			awtrixClient.dismissForestry();
			forestryHeld = false;
			forestryEvent = null;
			stopForestryFrames();
		}

		awtrixClient.clearCache();
		// This runs on the Swing thread. Everything below reads client state, so it
		// has to be handed to the client thread; touching the client from here trips
		// an assertion that escapes the config panel and leaves its controls stuck.
		clientThread.invokeLater(() ->
		{
			afkActive = false;
			lastAfkSentMs = 0L;
			bigStatIndex = 0;
			nextRotateMs = 0L;
			nextOverlayRotateMs = 0L;
			overlayRotateIndex = 0;
			currentOverlay = null;
			refreshClock();
		});
	}

	/*
	 * Sample readings for the preview pages. Two digits on hitpoints and prayer, three on
	 * spec, and a prayer well down on its maximum: between them they show what a line does
	 * when it runs out of room, which is the thing hardest to picture from the settings.
	 */
	private static final int PREVIEW_HP = 31;
	private static final int PREVIEW_HP_MAX = 99;
	private static final int PREVIEW_PRAYER = 12;
	private static final int PREVIEW_PRAYER_MAX = 70;
	private static final int PREVIEW_ENERGY = 64;
	private static final int PREVIEW_SPEC = 100;
	private static final int PREVIEW_LOW_HP = 8;
	private static final SkillActivity PREVIEW_ACTIVITY = SkillActivity.WOODCUTTING;
	private static final int PREVIEW_LEVEL = 62;
	private static final int PREVIEW_XP_RATE = 41_000;
	private static final int PREVIEW_PROGRESS = 47;

	/**
	 * Puts a page on the clock with made-up readings, so a layout can be judged from the
	 * settings panel rather than by logging in and waiting for a stat to do something.
	 * It runs while you are logged out, and it holds the panel for a few seconds so the
	 * next game tick cannot wipe it before it has been looked at.
	 */
	private void showPreview(Preview preview)
	{
		if (preview == Preview.OFF)
		{
			return;
		}
		// Turning the setting back off comes round here again as an OFF, which stops.
		configManager.setConfiguration(UlanziConfig.GROUP, "preview", Preview.OFF);

		if (preview == Preview.CONNECTION)
		{
			awtrixClient.testConnection();
			return;
		}

		previewUntilMs = System.currentTimeMillis() + PREVIEW_MS;
		clientThread.invokeLater(() ->
		{
			awtrixClient.clearCache();
			awtrixClient.applyBrightness();
			awtrixClient.setPulse(1.0);
			drawPreview(preview);
		});

		ScheduledFuture<?> previous = previewEnd;
		if (previous != null)
		{
			previous.cancel(false);
		}
		previewEnd = executor.schedule(() -> clientThread.invokeLater(this::endPreview),
			PREVIEW_MS, TimeUnit.MILLISECONDS);
	}

	private void drawPreview(Preview preview)
	{
		switch (preview)
		{
			case STATS:
				// No activity, so the skill page cannot take the panel from under a
				// preview of the stats page.
				pushStats(PREVIEW_HP, PREVIEW_HP_MAX, PREVIEW_PRAYER, PREVIEW_PRAYER_MAX, PREVIEW_ENERGY,
					PREVIEW_SPEC, false, false, null, null, null);
				break;

			case SKILL:
				drawSkillPreview();
				break;

			case LOW_HITPOINTS:
			{
				AlertDisplayMode display = config.alertDisplayMode();
				if (display.usesFullPanel())
				{
					awtrixClient.showLowHpAlert(PREVIEW_LOW_HP, true, false, true);
				}
				if (display.usesStatsView())
				{
					pushStats(PREVIEW_LOW_HP, PREVIEW_HP_MAX, PREVIEW_PRAYER, PREVIEW_PRAYER_MAX, PREVIEW_ENERGY,
						PREVIEW_SPEC, true, false, OverlayKind.LOW_HP, null, null);
				}
				break;
			}

			case SPEC_READY:
			{
				AlertDisplayMode display = config.alertDisplayMode();
				if (display.usesFullPanel())
				{
					awtrixClient.showSpecReadyAlert(PREVIEW_SPEC, true, false, true);
				}
				if (display.usesStatsView())
				{
					pushStats(PREVIEW_HP, PREVIEW_HP_MAX, PREVIEW_PRAYER, PREVIEW_PRAYER_MAX, PREVIEW_ENERGY,
						PREVIEW_SPEC, false, false, OverlayKind.SPEC_READY, null, null);
				}
				break;
			}

			case AFK:
			{
				AlertDisplayMode display = config.afkDisplay();
				if (display.usesFullPanel())
				{
					awtrixClient.showAfk(false);
				}
				if (display.usesStatsView())
				{
					pushStats(PREVIEW_HP, PREVIEW_HP_MAX, PREVIEW_PRAYER, PREVIEW_PRAYER_MAX, PREVIEW_ENERGY,
						PREVIEW_SPEC, false, false, OverlayKind.AFK, null, null);
				}
				break;
			}

			default:
				break;
		}
	}

	/**
	 * The skill page is drawn from here rather than through the live path, which would
	 * want a real skill's XP to work out a level from.
	 */
	private void drawSkillPreview()
	{
		long now = System.currentTimeMillis();
		List<CompactLayout.Cell> bars = edgeBars(PREVIEW_HP, PREVIEW_HP_MAX,
			AwtrixClient.percentOf(PREVIEW_HP, PREVIEW_HP_MAX), PREVIEW_PRAYER, PREVIEW_PRAYER_MAX,
			AwtrixClient.percentOf(PREVIEW_PRAYER, PREVIEW_PRAYER_MAX), PREVIEW_ENERGY, PREVIEW_SPEC, now);
		int columns = 0;
		for (CompactLayout.Cell cell : bars)
		{
			if (cell.showsBar())
			{
				columns++;
			}
		}
		// Off is not a page, so the preview shows what turning it on would look like.
		SkillProgressMode mode = skillProgressMode().isShown() ? skillProgressMode() : SkillProgressMode.BOTH;
		List<AwtrixClient.TextFragment> text = skillProgressText(mode, PREVIEW_LEVEL, PREVIEW_XP_RATE, false,
			PREVIEW_ACTIVITY.color(), PANEL_WIDTH - ICON_WIDTH - CompactLayout.edgeReserve(columns));
		awtrixClient.pushSkillProgress(text, PREVIEW_PROGRESS,
			config.progressGradient().stops(PREVIEW_ACTIVITY.color()), PREVIEW_ACTIVITY.iconData(), bars);
	}

	private void endPreview()
	{
		previewUntilMs = 0L;
		previewEnd = null;
		awtrixClient.dismissAfk();
		awtrixClient.dismissLowAlerts();
		awtrixClient.clearStats();
		awtrixClient.clearCache();
		// The sample readings left trails behind them; none of it belongs to the game.
		statPercent.clear();
		ghostPercent.clear();
		ghostUntilMs.clear();
		focusStat = null;
		focusUntilMs = 0L;
		focusChange = 0;
		afkActive = false;
		afkHeld = false;
		lastAfkSentMs = 0L;
		currentOverlay = null;
		refreshClock();
	}

	/**
	 * A profile fills in the settings it stands for rather than quietly winning over
	 * them, so the panel and the settings never disagree about what is going to happen.
	 */
	private void applyProfile(Profile profile)
	{
		for (Map.Entry<String, String> setting : profile.settings().entrySet())
		{
			configManager.setConfiguration(UlanziConfig.GROUP, setting.getKey(), setting.getValue());
		}
		awtrixClient.clearCache();
		clientThread.invokeLater(this::refreshClock);
	}

	/**
	 * Changing something a profile put there is the point at which the panel stops being
	 * that profile, so the dropdown says so instead of sitting on a name it no longer
	 * matches. A profile writing its own settings passes straight through here, since the
	 * value it wrote is the value it asked for.
	 */
	private void leaveProfileIfChanged(String key, String newValue)
	{
		Profile profile = config.profile();
		String expected = profile.valueFor(key);
		if (expected == null || expected.equals(newValue))
		{
			return;
		}
		configManager.setConfiguration(UlanziConfig.GROUP, "profile", Profile.CUSTOM);
		postChat("Quick profile is now Custom, since you changed one of its settings.");
	}

	/** The one-line answer to "is the clock there", written where the question is asked. */
	private void postStatus(String status)
	{
		if (status == null || status.equals(config.status()))
		{
			return;
		}
		configManager.setConfiguration(UlanziConfig.GROUP, "status", status);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN
			|| state == GameState.LOGIN_SCREEN_AUTHENTICATOR)
		{
			awtrixClient.clearAll();
			resetState();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		long now = System.currentTimeMillis();
		Integer previousLevel = skillLevels.put(event.getSkill(), event.getLevel());
		if (isLevelUp(previousLevel, event.getLevel()) && config.levelUpEnabled())
		{
			// Several at once play one after another, so the celebration runs until the
			// last of them has had its turn.
			levelUpUntilMs = Math.max(levelUpUntilMs, now) + AwtrixClient.LEVEL_UP_MS;
			// The AFK page is held, and a held notification sits in front of the queue:
			// the celebration would wait behind it until you came back. Take it down
			// first, and let the tick loop put it back when the level has been read.
			if (afkHeld)
			{
				awtrixClient.dismissAfk();
				afkHeld = false;
			}
			awtrixClient.showLevelUp(event.getSkill().getName(), SkillActivities.levelUpIcon(event.getSkill()),
				event.getLevel(), config.levelUpSound());
			// The drop queues behind the celebration instead of stacking up under it.
			nextDropMs = Math.max(nextDropMs, levelUpUntilMs);
		}

		int xp = event.getXp();
		Integer previous = skillXp.put(event.getSkill(), xp);
		if (previous == null || xp <= previous)
		{
			return;
		}
		int gained = xp - previous;
		if (event.getSkill() == Skill.WOODCUTTING)
		{
			lastChoppingMs = now;
		}
		xpRates.onXp(event.getSkill(), gained, now);
		tickXpGains.merge(event.getSkill(), gained, Integer::sum);
		// What you are training is tracked whether or not its icon is drawn: the skill
		// page and skill alternation both ask this, and neither is about the icon.
		activityTracker.onXp(SkillActivities.fromSkillXp(event.getSkill()), now);
	}

	/**
	 * Hits either way count as a fight, which covers the styles that spend most of a
	 * fight with no animation at all, and the moment something else opens on you.
	 */
	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		Player player = client.getLocalPlayer();
		if (player == null || !isFightHitsplat(event.getHitsplat().getHitsplatType()))
		{
			return;
		}
		Actor actor = event.getActor();
		if (actor == player || (player.getInteracting() != null && actor == player.getInteracting()))
		{
			lastCombatMs = System.currentTimeMillis();
		}
	}

	/**
	 * Damage and blocks from something swinging at you or you at it. Poison, venom,
	 * disease and burns keep ticking long after a fight, and heals are not one at all.
	 */
	static boolean isFightHitsplat(int type)
	{
		return type == HitsplatID.DAMAGE_ME
			|| type == HitsplatID.DAMAGE_OTHER
			|| type == HitsplatID.BLOCK_ME
			|| type == HitsplatID.BLOCK_OTHER
			|| type == HitsplatID.DAMAGE_ME_CYAN
			|| type == HitsplatID.DAMAGE_OTHER_CYAN
			|| type == HitsplatID.DAMAGE_ME_ORANGE
			|| type == HitsplatID.DAMAGE_OTHER_ORANGE
			|| type == HitsplatID.DAMAGE_ME_YELLOW
			|| type == HitsplatID.DAMAGE_OTHER_YELLOW
			|| type == HitsplatID.DAMAGE_ME_WHITE
			|| type == HitsplatID.DAMAGE_OTHER_WHITE
			|| type == HitsplatID.DAMAGE_MAX_ME
			|| type == HitsplatID.DAMAGE_MAX_ME_CYAN
			|| type == HitsplatID.DAMAGE_MAX_ME_ORANGE
			|| type == HitsplatID.DAMAGE_MAX_ME_YELLOW
			|| type == HitsplatID.DAMAGE_MAX_ME_WHITE
			|| type == HitsplatID.DAMAGE_ME_POISE
			|| type == HitsplatID.DAMAGE_OTHER_POISE
			|| type == HitsplatID.DAMAGE_MAX_ME_POISE;
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		NPC npc = event.getNpc();
		forestrySpawned(ForestryEvent.fromNpc(npc.getId()), npc.getIndex());
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		NPC npc = event.getNpc();
		forestryTracker.onDespawn(ForestryEvent.fromNpc(npc.getId()), npc.getIndex(), System.currentTimeMillis());
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		GameObject object = event.getGameObject();
		forestrySpawned(ForestryEvent.fromObject(object.getId()), object.getHash() | OBJECT_KEY);
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		GameObject object = event.getGameObject();
		forestryTracker.onDespawn(ForestryEvent.fromObject(object.getId()), object.getHash() | OBJECT_KEY,
			System.currentTimeMillis());
	}

	/**
	 * The scene gained something only a forestry event puts there. The tracker decides
	 * whether that is the event starting, and everything below decides whether it is
	 * yours: the events run for anyone in the area, and one you walked past while doing
	 * something else is not worth taking the panel for.
	 */
	private void forestrySpawned(ForestryEvent event, long key)
	{
		long now = System.currentTimeMillis();
		ForestryEvent started = forestryTracker.onSpawn(event, key, now);
		if (started == null || !config.forestryEnabled())
		{
			return;
		}
		if (config.forestryWhileChopping() && now - lastChoppingMs > CHOPPING_GRACE_MS)
		{
			return;
		}

		forestryEvent = started;
		forestryStartMs = now;
		forestryUntilMs = now + config.forestrySeconds() * 1000L;
		if (config.forestryBeep())
		{
			awtrixClient.beepForestry();
		}
		if (config.forestryEffect().isAnimated())
		{
			startForestryFrames();
		}
		refreshClock();
	}

	private ForestryEvent currentForestry(long nowMs)
	{
		if (forestryEvent == null)
		{
			return null;
		}
		if (!config.forestryEnabled() || nowMs >= forestryUntilMs)
		{
			forestryEvent = null;
			stopForestryFrames();
			return null;
		}
		// Picks up an effect changed while an event is already on the panel.
		if (config.forestryEffect().isAnimated())
		{
			startForestryFrames();
		}
		else
		{
			stopForestryFrames();
		}
		return forestryEvent;
	}

	/**
	 * Game ticks are too far apart for anything to look like it is growing, so the
	 * animation is redrawn between them the way an XP pulse is.
	 */
	private void startForestryFrames()
	{
		if (forestryFrames == null || forestryFrames.isDone())
		{
			forestryFrames = executor.scheduleWithFixedDelay(this::forestryFrame,
				ForestryAnimation.FRAME_MS, ForestryAnimation.FRAME_MS, TimeUnit.MILLISECONDS);
		}
	}

	private void forestryFrame()
	{
		clientThread.invokeLater(this::refreshClock);
	}

	private void stopForestryFrames()
	{
		ScheduledFuture<?> frames = forestryFrames;
		forestryFrames = null;
		if (frames != null)
		{
			frames.cancel(false);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!tickXpGains.isEmpty())
		{
			meleeSkill = config.hideMaxedSkill()
				? meleePageSkill(tickXpGains, meleeSkill, this::skillAtMax)
				: meleeSkill(tickXpGains, meleeSkill);
			queueXpDrop(tickXpGains);
			tickXpGains.clear();
		}
		trackChopping();
		refreshClock();
	}

	/**
	 * An axe swinging counts as chopping as much as the XP it earns does: an event can
	 * start on the tick a tree falls, before any of that tree's last XP has arrived.
	 */
	private void trackChopping()
	{
		Player player = client.getLocalPlayer();
		if (player != null && SkillActivities.fromAnimation(player.getAnimation()) == SkillActivity.WOODCUTTING)
		{
			lastChoppingMs = System.currentTimeMillis();
		}
	}

	/**
	 * The melee skill the page should follow. The one that paid most still wins, and a
	 * tie still keeps the current one. When that skill is already 99, a style that also
	 * paid a skill still climbing follows that one, so the page is not spent on a skill
	 * with nowhere left to go.
	 */
	static Skill meleePageSkill(Map<Skill, Integer> gains, Skill current, Predicate<Skill> maxed)
	{
		Skill chosen = meleeSkill(gains, current);
		if (chosen == null || maxed == null || !maxed.test(chosen))
		{
			return chosen;
		}
		Skill best = null;
		int bestXp = -1;
		for (Skill skill : MELEE_SKILLS)
		{
			if (skill == chosen || maxed.test(skill))
			{
				continue;
			}
			Integer gained = gains.get(skill);
			if (gained == null || gained <= 0)
			{
				continue;
			}
			if (gained > bestXp)
			{
				best = skill;
				bestXp = gained;
			}
		}
		return best == null ? chosen : best;
	}

	/**
	 * The melee skill that got the most XP this tick. Controlled splits it evenly,
	 * so a tie keeps the current one instead of flickering between them.
	 */
	static Skill meleeSkill(Map<Skill, Integer> gains, Skill current)
	{
		Skill best = null;
		int bestXp = 0;
		for (Skill skill : MELEE_SKILLS)
		{
			Integer gained = gains.get(skill);
			if (gained == null)
			{
				continue;
			}
			if (gained > bestXp || (gained == bestXp && skill == current))
			{
				best = skill;
				bestXp = gained;
			}
		}
		return best == null ? current : best;
	}

	/**
	 * One drop per tick with the total, like the in-game counter, iconed with the skill that got the most.
	 */
	private void queueXpDrop(Map<Skill, Integer> gains)
	{
		for (Map.Entry<Skill, Integer> gain : gains.entrySet())
		{
			pendingDropXp += gain.getValue();
			pendingDropGains.merge(gain.getKey(), gain.getValue(), Integer::sum);
		}
		pendingDropSkill = dropSkill(gains);
	}

	/**
	 * What a drop written over a skill page is allowed to say: the XP from what that page
	 * is about, and none of whatever else you have going at the same time. The page is one
	 * skill's level, rate and bar, so another skill's gain flashed over it reads as this
	 * skill's — cooking a fish would flash the fishing page it is sharing the panel with.
	 *
	 * A fight is the exception that is not one: an attack style splits melee XP across up
	 * to three skills and every hit pays hitpoints on top, and all of it is the one page.
	 */
	static int pageDropXp(Map<Skill, Integer> gains, SkillActivity page)
	{
		int xp = 0;
		for (Map.Entry<Skill, Integer> gain : gains.entrySet())
		{
			if (pageShows(gain.getKey(), page))
			{
				xp += gain.getValue();
			}
		}
		return xp;
	}

	/** Whether a skill's XP is part of what the page on the panel is showing. */
	static boolean pageShows(Skill skill, SkillActivity page)
	{
		if (page == null)
		{
			return false;
		}
		if (skill == Skill.HITPOINTS)
		{
			// Hitpoints has no page of its own to be shown on, and in a fight it is not a
			// second thing you are doing: it is the same hits the page is already counting.
			return isCombatPage(page);
		}
		if (page == SkillActivity.MELEE)
		{
			for (Skill melee : MELEE_SKILLS)
			{
				if (skill == melee)
				{
					return true;
				}
			}
			return false;
		}
		return SkillActivities.skillFor(page) == skill;
	}

	private static boolean isCombatPage(SkillActivity page)
	{
		return page == SkillActivity.MELEE || page == SkillActivity.RANGED || page == SkillActivity.MAGIC;
	}

	/**
	 * Hitpoints comes with every hit, so it only names the drop when nothing else gained.
	 */
	static Skill dropSkill(Map<Skill, Integer> gains)
	{
		Skill best = null;
		int bestXp = 0;
		for (Map.Entry<Skill, Integer> gain : gains.entrySet())
		{
			if (gain.getKey() != Skill.HITPOINTS && gain.getValue() > bestXp)
			{
				best = gain.getKey();
				bestXp = gain.getValue();
			}
		}
		return best != null || !gains.containsKey(Skill.HITPOINTS) ? best : Skill.HITPOINTS;
	}

	/**
	 * In place on the skill progress page swaps its text for a moment and keeps the bar.
	 * Anywhere else a drop is a short notification.
	 *
	 * @param progressActivity the skill page on the panel, or null when that is not what is up.
	 */
	private void flushXpDrop(boolean panelTaken, SkillActivity progressActivity)
	{
		if (!config.xpDrops())
		{
			clearPendingDrop();
			return;
		}
		long now = System.currentTimeMillis();
		// A pulse lights whatever is already on the panel rather than showing anything
		// of its own, so it runs even when an overlay has taken the panel and it never
		// waits behind a drop that is still on screen.
		if (config.xpDropDirection() == XpDropDirection.PULSE)
		{
			if (pendingDropXp > 0)
			{
				startPulse(now);
			}
			clearPendingDrop();
			return;
		}
		if (panelTaken)
		{
			clearPendingDrop();
			return;
		}
		if (pendingDropXp <= 0 || now < nextDropMs)
		{
			return;
		}
		XpDropDirection direction = config.xpDropDirection();
		// A fight pays XP on every hit, and a drop page of its own for each one is the
		// sword forever popping over the stats you are fighting with. So in a fight a drop
		// only ever goes over the skill page, in place, and without one it is let go. The
		// last hit's drop lands after the target has gone, so a fight that has just ended
		// with nothing else started still counts as one.
		if (activityFromCombat || (inCombat && shownActivity == null))
		{
			if (progressActivity == null)
			{
				clearPendingDrop();
				return;
			}
			direction = XpDropDirection.IN_PLACE;
		}
		if (direction == XpDropDirection.IN_PLACE && progressActivity != null)
		{
			// Only this page's own XP goes over this page. A drop that was all somebody
			// else's is let go rather than held for the turn that skill gets later: a drop
			// is what has just landed, and one saved up for a turn is neither.
			int mine = pageDropXp(pendingDropGains, progressActivity);
			clearPendingDrop();
			if (mine <= 0)
			{
				return;
			}
			inPlaceDropText = fitXpDropText(mine);
			inPlaceDropUntilMs = now + AwtrixClient.XP_DROP_IN_PLACE_MS;
			nextDropMs = inPlaceDropUntilMs;
			return;
		}
		String icon = pendingDropSkill == null ? null : SkillActivities.levelUpIcon(pendingDropSkill);
		String text = direction.isHorizontal() ? xpDropText(pendingDropXp) : fitXpDropText(pendingDropXp);
		nextDropMs = now + awtrixClient.showXpDrop(text, XP_DROP_COLOR, icon, direction);
		clearPendingDrop();
	}

	private void clearPendingDrop()
	{
		pendingDropXp = 0;
		pendingDropSkill = null;
		pendingDropGains.clear();
	}

	/**
	 * A page change is taken down and brought back up rather than cut to. Two skills
	 * paying XP used to swap the icon and the level under your eyes on every drop; now
	 * the old page holds while the panel dims, and the new one is already up by the time
	 * it is bright again.
	 */
	private SkillActivity crossfade(SkillActivity wanted, long now)
	{
		if (!config.alternateSkills() || wanted == null || shownActivity == null || !activityIsDrawn(wanted))
		{
			// The first page up, the icon going away, or an activity that is tracked but
			// not drawn: nothing to cross over, and nothing worth dipping the panel for.
			settleActivity(wanted);
			return wanted;
		}

		if (fadeStartMs <= 0L)
		{
			if (wanted != shownActivity)
			{
				fadeStartMs = now;
				fadeTo = wanted;
				startFadeFrames();
			}
		}
		else
		{
			long elapsed = now - fadeStartMs;
			SkillActivity landing = fadeTo == null ? wanted : fadeTo;
			if (elapsed >= FADE_MS)
			{
				settleActivity(landing);
			}
			else if (elapsed >= FADE_MS / 2)
			{
				// Past the bottom of the dip: the new page comes back up with the light.
				shownActivity = landing;
			}
			else if (wanted != shownActivity)
			{
				// Changed its mind on the way down, which lands on the newest answer.
				fadeTo = wanted;
			}
		}

		// The page being held is the one the rest of the tick has to agree with.
		if (shownActivity == wanted)
		{
			shownFromCombat = activityFromCombat;
		}
		else
		{
			activityFromCombat = shownFromCombat;
		}
		return shownActivity;
	}

	/**
	 * Whether anything on the panel would change if the activity did. With both icons off
	 * and no skill page, what you are training is still tracked — the rate and the
	 * alternation want it — but none of it is on the panel to be swapped over.
	 */
	private boolean activityIsDrawn(SkillActivity activity)
	{
		return activityIcon(activity) != null || showsSkillProgress(activity);
	}

	private void settleActivity(SkillActivity activity)
	{
		shownActivity = activity;
		shownFromCombat = activityFromCombat;
		fadeTo = null;
		fadeStartMs = 0L;
		stopFadeFrames();
	}

	/**
	 * Down to the dip and back over the swap, so the change reads as one movement.
	 * Game ticks are far enough apart to make that a blink, so it is redrawn between
	 * them the way a pulse is.
	 */
	static double fadeFactor(long startMs, long nowMs)
	{
		long elapsed = nowMs - startMs;
		if (startMs <= 0L || elapsed < 0L || elapsed >= FADE_MS)
		{
			return 1.0;
		}
		double half = FADE_MS / 2.0;
		double into = elapsed < half ? elapsed / half : (FADE_MS - elapsed) / half;
		return 1.0 - (1.0 - FADE_DIP) * into;
	}

	private void startFadeFrames()
	{
		if (fadeFrames == null || fadeFrames.isDone())
		{
			fadeFrames = executor.scheduleWithFixedDelay(this::fadeFrame,
				FADE_FRAME_MS, FADE_FRAME_MS, TimeUnit.MILLISECONDS);
		}
	}

	private void fadeFrame()
	{
		clientThread.invokeLater(this::refreshClock);
	}

	private void stopFadeFrames()
	{
		ScheduledFuture<?> frames = fadeFrames;
		fadeFrames = null;
		if (frames != null)
		{
			frames.cancel(false);
		}
	}

	/**
	 * A drop landing during a pulse restarts it instead of stacking another one.
	 */
	private void startPulse(long nowMs)
	{
		pulseStartMs = nowMs;
		if (pulseFrames == null || pulseFrames.isDone())
		{
			pulseFrames = executor.scheduleWithFixedDelay(this::pulseFrame,
				PULSE_FRAME_MS, PULSE_FRAME_MS, TimeUnit.MILLISECONDS);
		}
	}

	/**
	 * Game ticks alone would make the pulse a blink, so it is redrawn between them.
	 * The frame that finds the pulse over is the one that puts the colours back.
	 */
	private void pulseFrame()
	{
		if (System.currentTimeMillis() - pulseStartMs >= PULSE_MS)
		{
			stopPulse();
		}
		clientThread.invokeLater(this::refreshClock);
	}

	private void stopPulse()
	{
		ScheduledFuture<?> frames = pulseFrames;
		pulseFrames = null;
		if (frames != null)
		{
			frames.cancel(false);
		}
	}

	/**
	 * Full brightness on landing, fading back to the resting dim over the second.
	 * The fade is steepest at the start, so the peak reads as a hit rather than
	 * something held: an even fade leaves the brightest frames looking stuck,
	 * especially where a colour is already maxed out and cannot go higher.
	 */
	static double pulseFactor(long startMs, long nowMs)
	{
		long elapsed = nowMs - startMs;
		if (startMs <= 0L || elapsed < 0L || elapsed >= PULSE_MS)
		{
			return PULSE_REST;
		}
		double remaining = 1.0 - elapsed / (double) PULSE_MS;
		return PULSE_REST + (PULSE_PEAK - PULSE_REST) * remaining * remaining;
	}

	/**
	 * Only pulse mode moves the brightness; every other drop style leaves it alone.
	 */
	private double currentPulse(long nowMs)
	{
		if (!config.xpDrops() || config.xpDropDirection() != XpDropDirection.PULSE)
		{
			return 1.0;
		}
		return pulseFactor(pulseStartMs, nowMs);
	}

	static String xpDropText(int xp)
	{
		return "+" + xp + " xp";
	}

	/**
	 * Drops that do not scroll sideways have to fit beside the icon, so the unit shrinks or goes.
	 */
	static String fitXpDropText(int xp)
	{
		for (String text : new String[] {xpDropText(xp), "+" + xp + "xp"})
		{
			if (XpDropGif.textWidth(text) <= XpDropGif.WIDTH)
			{
				return text;
			}
		}
		return "+" + xp;
	}

	private void refreshClock()
	{
		try
		{
			// A page put up from the settings panel is left alone until it has had its
			// few seconds, or the next game tick would wipe it before it was read.
			if (System.currentTimeMillis() < previewUntilMs)
			{
				return;
			}
			if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
			{
				return;
			}

			int hitpoints = client.getBoostedSkillLevel(Skill.HITPOINTS);
			int hitpointsMax = client.getRealSkillLevel(Skill.HITPOINTS);
			int prayer = client.getBoostedSkillLevel(Skill.PRAYER);
			int prayerMax = client.getRealSkillLevel(Skill.PRAYER);
			int energy = Math.min(100, Math.max(0, client.getEnergy() / 100));
			int spec = Math.min(100, Math.max(0, client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT) / 10));

			// A no-op once it has taken, and a retry if the clock was not answering yet.
			awtrixClient.applyBrightness();

			// Threshold alerts are pushed below, before the page is picked, and are
			// there to be noticed, so they never take the pulse's resting dim.
			awtrixClient.setPulse(1.0);

			ThresholdUnit unit = config.thresholdUnit();
			boolean lowHp = config.lowHitpoints().isEnabled()
				&& unit.isLow(hitpoints, hitpointsMax, config.lowHitpointsThreshold());
			boolean lowPray = config.lowPrayer().isEnabled()
				&& unit.isLow(prayer, prayerMax, config.lowPrayerThreshold());
			boolean specReady = trackSpecReady(spec, System.currentTimeMillis());
			updateCombat(System.currentTimeMillis());
			SkillActivity activity = crossfade(currentActivity(), System.currentTimeMillis());
			ForestryEvent forestry = currentForestry(System.currentTimeMillis());
			// Between one attack and the next you are standing still and pressing nothing,
			// which is what AFK is looking for, so without this the page flashes up and off
			// again all through a fight.
			boolean afk = config.afkEnabled() && (config.afkInCombat() || !inCombat) && isAfk();
			if (!afk && afkHeld)
			{
				awtrixClient.dismissAfk();
				afkHeld = false;
				afkActive = false;
				lastAfkSentMs = 0L;
			}
			AlertDisplayMode display = config.alertDisplayMode();

			fireThresholdPulses(hitpoints, prayer, spec, lowHp, lowPray, specReady, display);

			// Nothing about being away is worth covering a new level with, and the AFK
			// page would go straight back over it on the next tick.
			boolean celebrating = System.currentTimeMillis() < levelUpUntilMs;
			OverlayKind chosen = chooseOverlay(afk && !celebrating, lowHp, lowPray, specReady, forestry != null);
			AlertDisplayMode mode = displayFor(chosen, display);
			flushXpDrop(chosen != null && mode.usesFullPanel(),
				chosen == null && showsSkillProgress(activity) ? activity : null);
			// The pulse only shades the ordinary page. An overlay has taken the panel
			// to tell you something, so it stays at full brightness.
			awtrixClient.setPulse(chosen != null
				? 1.0
				: currentPulse(System.currentTimeMillis()) * fadeFactor(fadeStartMs, System.currentTimeMillis()));
			if (chosen != null)
			{
				if (afkHeld && !(chosen == OverlayKind.AFK && mode.usesFullPanel()))
				{
					awtrixClient.dismissAfk();
					afkHeld = false;
				}
				if (forestryHeld && !(chosen == OverlayKind.FORESTRY && mode.usesFullPanel()))
				{
					awtrixClient.dismissForestry();
					forestryHeld = false;
				}
				if (mode.usesFullPanel())
				{
					showFullPanelOverlay(chosen, hitpoints, prayer, spec);
				}
				if (mode.usesStatsView())
				{
					showStatsViewOverlay(chosen, hitpoints, hitpointsMax, prayer, prayerMax, energy, spec, lowHp, lowPray, activity);
				}
				return;
			}

			clearPanelOverlays(afk);

			pushStats(hitpoints, hitpointsMax, prayer, prayerMax, energy, spec, lowHp, lowPray, null, activity,
				activityIcon(activity));
		}
		catch (Exception ex)
		{
			log.warn("Ulanzi update failed", ex);
		}
	}

	/**
	 * AFK and forestry each carry their own display setting; the threshold warnings share one.
	 */
	private AlertDisplayMode displayFor(OverlayKind chosen, AlertDisplayMode alerts)
	{
		if (chosen == OverlayKind.AFK)
		{
			return config.afkDisplay();
		}
		if (chosen == OverlayKind.FORESTRY)
		{
			return config.forestryDisplay();
		}
		return alerts;
	}

	/**
	 * The special attack bar coming back up to where it can be spent, for the few seconds
	 * that is worth saying.
	 *
	 * The two warnings are states: they hold the panel for as long as the stat is low, and
	 * come off it when the stat is not. A full bar is not a state worth holding — it stays
	 * full until you spend it, and a page that waited for that would be the panel for the
	 * rest of the trip. So this is an edge, given a few seconds of its own.
	 */
	private boolean trackSpecReady(int spec, long now)
	{
		int previous = lastSpec;
		lastSpec = spec;
		if (!config.specRestored().isEnabled())
		{
			specReadyUntilMs = 0L;
			return false;
		}
		if (specRestored(previous, spec, config.specReadyThreshold()))
		{
			specReadyUntilMs = now + SPEC_READY_MS;
		}
		return now < specReadyUntilMs;
	}

	/**
	 * Crossing up onto the threshold, and only crossing: a bar that was already there is
	 * nothing new, and {@code previous} of -1 is the first reading after a login, which
	 * would otherwise announce a full bar to everyone who logs in with one.
	 */
	static boolean specRestored(int previous, int spec, int threshold)
	{
		return previous >= 0 && previous < threshold && spec >= threshold;
	}

	/**
	 * Beep / one-shot full-panel flash when crossing under threshold or dropping further,
	 * and the same when the special attack bar crosses back up onto its own.
	 */
	private void fireThresholdPulses(int hitpoints, int prayer, int spec, boolean lowHp, boolean lowPray,
		boolean specReady, AlertDisplayMode display)
	{
		if (lowHp)
		{
			boolean entered = !wasLowHp;
			boolean dropped = config.realertOnDrop() && hitpoints < lastHpAlertValue;
			if (entered || dropped)
			{
				boolean beep = config.lowHitpoints().beeps();
				boolean popupFlash = config.lowHitpoints().flashes() && display.usesFullPanel()
					&& !display.usesStatsView();
				if (popupFlash)
				{
					awtrixClient.showLowHpAlert(hitpoints, true, beep, false);
				}
				else if (beep)
				{
					awtrixClient.beepLowHp();
				}
				lastHpAlertValue = hitpoints;
			}
			wasLowHp = true;
		}
		else
		{
			if (wasLowHp)
			{
				awtrixClient.dismissLowHp();
			}
			wasLowHp = false;
			lastHpAlertValue = Integer.MAX_VALUE;
		}

		if (lowPray)
		{
			boolean entered = !wasLowPray;
			boolean dropped = config.realertOnDrop() && prayer < lastPrayerAlertValue;
			if (entered || dropped)
			{
				boolean beep = config.lowPrayer().beeps();
				boolean popupFlash = config.lowPrayer().flashes() && display.usesFullPanel()
					&& !display.usesStatsView();
				if (popupFlash)
				{
					awtrixClient.showLowPrayerAlert(prayer, true, beep, false);
				}
				else if (beep)
				{
					awtrixClient.beepLowPrayer();
				}
				lastPrayerAlertValue = prayer;
			}
			wasLowPray = true;
		}
		else
		{
			if (wasLowPray)
			{
				awtrixClient.dismissLowPrayer();
			}
			wasLowPray = false;
			lastPrayerAlertValue = Integer.MAX_VALUE;
		}

		if (specReady)
		{
			// No re-alert on a further rise: the bar is already past the point that was
			// being waited for, and ticking on to 100 is not a second piece of news.
			if (!wasSpecReady)
			{
				boolean beep = config.specRestored().beeps();
				boolean popupFlash = config.specRestored().flashes() && display.usesFullPanel()
					&& !display.usesStatsView();
				if (popupFlash)
				{
					awtrixClient.showSpecReadyAlert(spec, true, beep, false);
				}
				else if (beep)
				{
					awtrixClient.beepSpecReady();
				}
			}
			wasSpecReady = true;
		}
		else
		{
			if (wasSpecReady)
			{
				awtrixClient.dismissSpecReady();
			}
			wasSpecReady = false;
		}
	}

	private OverlayKind chooseOverlay(boolean afk, boolean lowHp, boolean lowPray, boolean specReady,
		boolean forestry)
	{
		List<OverlayKind> active = new ArrayList<>();
		if (afk)
		{
			active.add(OverlayKind.AFK);
		}
		if (lowHp && config.lowHitpoints().flashes())
		{
			active.add(OverlayKind.LOW_HP);
		}
		if (lowPray && config.lowPrayer().flashes())
		{
			active.add(OverlayKind.LOW_PRAYER);
		}
		if (specReady && config.specRestored().flashes())
		{
			active.add(OverlayKind.SPEC_READY);
		}

		// A forestry event is over in a couple of minutes and is the whole point of being
		// told about it, so it takes the panel from AFK and from a prayer warning rather
		// than waiting its turn in the rotation. Hitpoints it does not take: nothing about
		// an event is worth covering up the one warning that gets you killed.
		if (forestry && !active.contains(OverlayKind.LOW_HP))
		{
			return OverlayKind.FORESTRY;
		}

		if (active.isEmpty())
		{
			return null;
		}

		switch (config.overlayPriority())
		{
			case AFK_FIRST:
				if (active.contains(OverlayKind.AFK))
				{
					return OverlayKind.AFK;
				}
				return active.get(0);

			case ALERTS_FIRST:
				if (active.contains(OverlayKind.LOW_HP))
				{
					return OverlayKind.LOW_HP;
				}
				if (active.contains(OverlayKind.LOW_PRAYER))
				{
					return OverlayKind.LOW_PRAYER;
				}
				if (active.contains(OverlayKind.SPEC_READY))
				{
					return OverlayKind.SPEC_READY;
				}
				return active.get(0);

			case ROTATE:
			default:
				return rotateOverlay(active);
		}
	}

	private OverlayKind rotateOverlay(List<OverlayKind> active)
	{
		long now = System.currentTimeMillis();

		if (currentOverlay != null && active.contains(currentOverlay) && now < nextOverlayRotateMs)
		{
			return currentOverlay;
		}

		if (currentOverlay != null && active.contains(currentOverlay))
		{
			int currentIndex = active.indexOf(currentOverlay);
			overlayRotateIndex = (currentIndex + 1) % active.size();
		}
		else if (overlayRotateIndex >= active.size())
		{
			overlayRotateIndex = 0;
		}

		OverlayKind next = active.get(overlayRotateIndex);
		nextOverlayRotateMs = now + (config.overlayRotateSeconds() * 1000L);
		return next;
	}

	private void showFullPanelOverlay(OverlayKind kind, int hitpoints, int prayer, int spec)
	{
		switch (kind)
		{
			case AFK:
			{
				afkHeld = true;
				long now = System.currentTimeMillis();
				boolean entering = !afkActive;
				long reassertMs = config.afkEffect().usesSideEyes() ? AFK_EYES_REASSERT_MS : AFK_REASSERT_MS;
				boolean reassert = now - lastAfkSentMs >= reassertMs;
				if (entering || reassert || currentOverlay != OverlayKind.AFK)
				{
					awtrixClient.showAfk(entering);
					lastAfkSentMs = now;
				}
				afkActive = true;
				break;
			}
			case LOW_HP:
				if (currentOverlay != OverlayKind.LOW_HP || hitpoints != lastHpAlertValue)
				{
					awtrixClient.showLowHpAlert(hitpoints, true, false, true);
					lastHpAlertValue = hitpoints;
				}
				break;
			case LOW_PRAYER:
				if (currentOverlay != OverlayKind.LOW_PRAYER || prayer != lastPrayerAlertValue)
				{
					awtrixClient.showLowPrayerAlert(prayer, true, false, true);
					lastPrayerAlertValue = prayer;
				}
				break;
			case SPEC_READY:
				// Sent once and held: the bar does not move while it is up, so there is
				// nothing for a second send to say.
				if (currentOverlay != OverlayKind.SPEC_READY)
				{
					awtrixClient.showSpecReadyAlert(spec, true, false, true);
				}
				break;
			case FORESTRY:
			{
				ForestryEffect effect = config.forestryEffect();
				// An animation is a new page every frame. A flashing one is blinked by the
				// clock itself, so it only has to be sent when it first goes up.
				if (forestryEvent != null && (effect.isAnimated() || currentOverlay != OverlayKind.FORESTRY))
				{
					awtrixClient.showForestryAlert(forestryEvent, effect,
						System.currentTimeMillis() - forestryStartMs);
					forestryHeld = true;
				}
				break;
			}
		}
		currentOverlay = kind;
	}

	private void showStatsViewOverlay(OverlayKind kind, int hitpoints, int hitpointsMax, int prayer, int prayerMax,
		int energy, int spec, boolean lowHp, boolean lowPray, SkillActivity activity)
	{
		switch (kind)
		{
			case AFK:
			{
				boolean entering = !afkActive;
				if (entering && config.afkSound())
				{
					awtrixClient.beepAfk();
				}
				afkActive = true;
				// Keep stats visible; optional tint / compact AFK label are applied in pushStats.
				pushStats(hitpoints, hitpointsMax, prayer, prayerMax, energy, spec, lowHp, lowPray, OverlayKind.AFK,
					activity, activityIcon(activity));
				break;
			}
			case LOW_HP:
			case LOW_PRAYER:
			case SPEC_READY:
				pushStats(hitpoints, hitpointsMax, prayer, prayerMax, energy, spec, lowHp, lowPray, kind, activity,
					activityIcon(activity));
				break;
			case FORESTRY:
				// The event replaces the stats rather than colouring them: none of the four
				// readings has anywhere to put a name, and the name is the whole message.
				if (forestryEvent != null)
				{
					awtrixClient.pushForestryOnStats(forestryEvent, config.forestryEffect(),
						System.currentTimeMillis() - forestryStartMs);
				}
				break;
		}
		currentOverlay = kind;
	}

	private void clearPanelOverlays(boolean afk)
	{
		if (afkActive && !afk)
		{
			awtrixClient.dismissAfk();
			afkActive = false;
			lastAfkSentMs = 0L;
			awtrixClient.clearCache();
		}

		if (currentOverlay == OverlayKind.LOW_HP)
		{
			awtrixClient.dismissLowHp();
		}
		else if (currentOverlay == OverlayKind.LOW_PRAYER)
		{
			awtrixClient.dismissLowPrayer();
		}
		else if (currentOverlay == OverlayKind.SPEC_READY)
		{
			awtrixClient.dismissSpecReady();
		}

		if (forestryHeld)
		{
			awtrixClient.dismissForestry();
			forestryHeld = false;
			awtrixClient.clearCache();
		}

		currentOverlay = null;
		nextOverlayRotateMs = 0L;
		overlayRotateIndex = 0;
	}

	/**
	 * {@code activity} is what you are doing and decides whether the skill page takes the
	 * panel; {@code activityIcon} is what may be drawn for it, and is null when the icon
	 * has been switched off. The two are separate so that switching off an icon does not
	 * also switch off the page behind it.
	 */
	private void pushStats(int hitpoints, int hitpointsMax, int prayer, int prayerMax, int energy, int spec,
		boolean lowHp, boolean lowPray, OverlayKind focus, SkillActivity activity, String activityIcon)
	{
		int hpPercent = AwtrixClient.percentOf(hitpoints, hitpointsMax);
		int prayerPercent = AwtrixClient.percentOf(prayer, prayerMax);
		long now = System.currentTimeMillis();
		trackChange(StatKind.HITPOINTS, hpPercent, now);
		trackChange(StatKind.PRAYER, prayerPercent, now);
		trackChange(StatKind.ENERGY, energy, now);
		trackChange(StatKind.SPEC, spec, now);

		if (focus == null && showsSkillProgress(activity))
		{
			pushSkillProgress(activity,
				edgeBars(hitpoints, hitpointsMax, hpPercent, prayer, prayerMax, prayerPercent, energy, spec, now));
			return;
		}

		if (!statsEnabled())
		{
			if (activityIcon == null)
			{
				awtrixClient.clearStats();
			}
			else
			{
				pushActivityLabel(activity, null);
			}
			return;
		}

		boolean flashHp = lowHp && config.lowHitpoints().flashes();
		boolean flashPray = lowPray && config.lowPrayer().flashes();
		boolean flashSpec = focus == OverlayKind.SPEC_READY && config.specRestored().flashes();
		compactFlashOn = (flashHp || flashPray || flashSpec) && !compactFlashOn;

		// Each stat answers its own value, so a glance says something is running out
		// before any of the digits have been read. Prayer is the one that does not: it
		// holds its blue at every level and lets the orb and the bar carry how much is
		// left. See StatKind#drained.
		Color hpColor = StatKind.HITPOINTS.drained(hpPercent);
		if (flashHp)
		{
			hpColor = compactFlashOn ? Color.RED : HP_FLASH_OFF;
		}
		Color prayColor = StatKind.PRAYER.drained(prayerPercent);
		if (flashPray)
		{
			prayColor = compactFlashOn ? StatKind.PRAYER.getIdentity() : PRAYER_FLASH_OFF;
		}
		Color energyColor = StatKind.ENERGY.drained(energy);
		Color specColor = StatKind.SPEC.drained(spec);
		if (flashSpec)
		{
			specColor = compactFlashOn ? StatKind.SPEC.getIdentity() : SPEC_FLASH_OFF;
		}

		AfkTintMode tintMode = focus == OverlayKind.AFK ? config.afkTintMode() : AfkTintMode.OFF;
		Color tint = tintMode != AfkTintMode.OFF ? config.afkTintColor() : null;
		Color afkBackground = tintMode.tintsBackground() ? tint : null;
		boolean tinted = tintMode.tintsValues() && tint != null;
		if (tinted)
		{
			hpColor = tint;
			prayColor = tint;
			energyColor = tint;
			specColor = tint;
		}

		if (focus == OverlayKind.LOW_HP && config.statsLayout() == StatsLayout.BIG)
		{
			int progress = hitpointsStyle().showsBar() ? hpPercent : -1;
			awtrixClient.pushBigStat("", String.valueOf(hitpoints), hpColor, progress,
				tintMode.tintsValues() ? tint : Color.RED, afkBackground,
				activityIcon != null ? activityIcon : PixelIcon.HITPOINTS.iconData());
			return;
		}
		if (focus == OverlayKind.LOW_PRAYER && config.statsLayout() == StatsLayout.BIG)
		{
			int progress = prayerStyle().showsBar() ? prayerPercent : -1;
			awtrixClient.pushBigStat("", String.valueOf(prayer), prayColor, progress, prayColor, afkBackground,
				activityIcon != null ? activityIcon : SkillActivity.PRAYER.iconData());
			return;
		}
		if (focus == OverlayKind.SPEC_READY && config.statsLayout() == StatsLayout.BIG)
		{
			int progress = config.specStyle().showsBar() ? spec : -1;
			awtrixClient.pushBigStat("", String.valueOf(spec), specColor, progress, specColor, afkBackground,
				activityIcon != null ? activityIcon : PixelIcon.SPECIAL_ATTACK.iconData());
			return;
		}

		Color valueTint = tinted ? tint : null;

		if (config.statsLayout() == StatsLayout.FOCUS && focus == null)
		{
			pushFocusStats(hitpoints, hitpointsMax, hpPercent, prayer, prayerMax, prayerPercent, energy, spec,
				hpColor, prayColor, energyColor, specColor, valueTint, afkBackground, activityIcon, now);
			return;
		}

		if (config.statsLayout() == StatsLayout.COMPACT || focus != null)
		{
			boolean icon = activityIcon != null;
			boolean routine = focus == null || focus == OverlayKind.AFK;
			StatStyle hpStyle = focus == OverlayKind.LOW_HP
				? atLeastValue(hitpointsStyle()) : hitpointsStyle();
			StatStyle prayerStyle = focus == OverlayKind.LOW_PRAYER
				? atLeastValue(prayerStyle()) : prayerStyle();

			List<CompactLayout.Cell> cells = new ArrayList<>();
			if (focus == OverlayKind.AFK && tintMode.showsLabel())
			{
				Color labelColor = tinted
					? tint
					: (!compactFlashOn ? config.afkTextColor() : Color.DARK_GRAY);
				cells.add(CompactLayout.Cell.label(AwtrixClient.truncate(config.afkText(), 4), labelColor));
			}

			boolean orbs = config.statOrbs();
			addCell(cells, StatKind.HITPOINTS, hpStyle, hitpoints, hpPercent, hitpointsMax, hpColor, valueTint,
				now, orbs);
			addCell(cells, StatKind.PRAYER, prayerStyle, prayer, prayerPercent, prayerMax, prayColor, valueTint,
				now, orbs);
			if (routine)
			{
				addCell(cells, StatKind.ENERGY, energyStyle(), energy, energy, 100, energyColor, valueTint,
					now, orbs);
				addCell(cells, StatKind.SPEC, config.specStyle(), spec, spec, 100, specColor, valueTint, now, orbs);
			}
			else if (focus == OverlayKind.SPEC_READY)
			{
				// The reading the alert is about, whether or not the bar is usually shown.
				addCell(cells, StatKind.SPEC, atLeastValue(config.specStyle()), spec, spec, 100, specColor,
					valueTint, now, orbs);
			}

			CompactLayout.fit(cells, icon, config.compactPriority());
			CompactLayout.place(cells, icon);

			if (!hasAnything(cells))
			{
				if (activityIcon == null)
				{
					awtrixClient.clearStats();
					return;
				}
				pushActivityLabel(activity, afkBackground);
				return;
			}

			awtrixClient.pushCompact(cells, afkBackground, activityIcon);
			return;
		}

		List<BigStat> stats = new ArrayList<>();
		addBigStat(stats, hitpointsStyle(), hitpoints, hpPercent, hpColor, PixelIcon.HITPOINTS.iconData());
		addBigStat(stats, prayerStyle(), prayer, prayerPercent, prayColor, SkillActivity.PRAYER.iconData());
		addBigStat(stats, energyStyle(), energy, energy, energyColor, PixelIcon.RUN_ENERGY.iconData());
		addBigStat(stats, config.specStyle(), spec, spec, specColor, PixelIcon.SPECIAL_ATTACK.iconData());

		if (stats.isEmpty())
		{
			if (activityIcon == null)
			{
				awtrixClient.clearStats();
			}
			else
			{
				pushActivityLabel(activity, afkBackground);
			}
			return;
		}

		if (nextRotateMs == 0L)
		{
			nextRotateMs = now + (config.statsRotateSeconds() * 1000L);
		}
		else if (now >= nextRotateMs)
		{
			bigStatIndex = (bigStatIndex + 1) % stats.size();
			nextRotateMs = now + (config.statsRotateSeconds() * 1000L);
		}
		if (bigStatIndex >= stats.size())
		{
			bigStatIndex = 0;
		}

		BigStat current = stats.get(bigStatIndex);
		awtrixClient.pushBigStat("", current.value, current.color, current.progress, current.color, afkBackground,
			activityIcon != null ? activityIcon : current.icon);
	}

	private static void addBigStat(List<BigStat> stats, StatStyle style, int value, int percent, Color color, String icon)
	{
		if (style.isShown())
		{
			stats.add(new BigStat(String.valueOf(value), color, style.showsBar() ? percent : -1, icon));
		}
	}

	/**
	 * One stat's cell. The slot is sized by the largest number the stat can reach rather
	 * than the one it happens to be showing, which is what keeps the rest of the line
	 * still when this value gains or loses a digit.
	 */
	private void addCell(List<CompactLayout.Cell> cells, StatKind kind, StatStyle style, int value, int percent,
		int ceiling, Color color, Color tint, long now)
	{
		addCell(cells, kind, style, value, percent, ceiling, color, tint, now, false);
	}

	private void addCell(List<CompactLayout.Cell> cells, StatKind kind, StatStyle style, int value, int percent,
		int ceiling, Color color, Color tint, long now, boolean orbs)
	{
		if (!style.isShown())
		{
			return;
		}
		cells.add(new CompactLayout.Cell(kind, style, String.valueOf(value),
			kind.digitsFor(Math.max(ceiling, value)), percent, ghostFor(kind, now),
			color, tint != null ? tint : kind.getIdentity(), orbs ? kind.getOrb() : null));
	}

	private static StatStyle atLeastValue(StatStyle style)
	{
		return style.isShown() ? style : StatStyle.VALUE;
	}

	private static boolean hasAnything(List<CompactLayout.Cell> cells)
	{
		for (CompactLayout.Cell cell : cells)
		{
			if (cell.showsValue() || cell.showsBar())
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Remembers where a stat was so the bottom strip can leave the lost part showing for a
	 * moment. A run of hits keeps the highest reading of the run, so the trail measures the
	 * whole drop rather than only the last tick of it.
	 */
	private void trackChange(StatKind kind, int percent, long now)
	{
		Integer before = statPercent.put(kind, percent);
		if (before == null || before == percent)
		{
			return;
		}
		claimFocus(kind, Math.abs(percent - before), now);
		if (percent > before)
		{
			ghostPercent.remove(kind);
			ghostUntilMs.remove(kind);
			return;
		}
		Long until = ghostUntilMs.get(kind);
		Integer ghost = ghostPercent.get(kind);
		boolean running = until != null && ghost != null && now < until;
		ghostPercent.put(kind, running ? Math.max(ghost, before) : before);
		ghostUntilMs.put(kind, now + GHOST_MS);
	}

	/**
	 * Which stat the focus page shows. Run energy ticks over by a point at a time the whole
	 * while you are moving, so the last thing to change is the wrong question: a stat holds
	 * the panel for a moment once it has it, and only a bigger move takes it early.
	 */
	private void claimFocus(StatKind kind, int change, long now)
	{
		if (focusStat != null && kind != focusStat && now < focusUntilMs && change <= focusChange)
		{
			return;
		}
		focusStat = kind;
		focusChange = change;
		focusUntilMs = now + FOCUS_HOLD_MS;
	}

	private int ghostFor(StatKind kind, long now)
	{
		Long until = ghostUntilMs.get(kind);
		Integer ghost = ghostPercent.get(kind);
		if (until == null || ghost == null || now >= until)
		{
			return -1;
		}
		return ghost;
	}

	/**
	 * The focus page: whichever stat moved last in large digits, with every stat that is
	 * switched on keeping its share of the bottom strip. Nothing rotates on a timer here,
	 * so the panel is answering the game rather than a clock.
	 */
	private void pushFocusStats(int hitpoints, int hitpointsMax, int hpPercent, int prayer, int prayerMax,
		int prayerPercent, int energy, int spec, Color hpColor, Color prayColor, Color energyColor, Color specColor,
		Color tint, Color background, String activityIcon, long now)
	{
		List<CompactLayout.Cell> cells = new ArrayList<>();
		addCell(cells, StatKind.HITPOINTS, barOnly(hitpointsStyle()), hitpoints, hpPercent, hitpointsMax,
			hpColor, tint, now);
		addCell(cells, StatKind.PRAYER, barOnly(prayerStyle()), prayer, prayerPercent, prayerMax,
			prayColor, tint, now);
		addCell(cells, StatKind.ENERGY, barOnly(energyStyle()), energy, energy, 100, energyColor, tint, now);
		addCell(cells, StatKind.SPEC, barOnly(config.specStyle()), spec, spec, 100, specColor, tint, now);

		if (cells.isEmpty())
		{
			awtrixClient.clearStats();
			return;
		}

		StatKind shown = focusStat;
		if (shown == null || !isShown(shown))
		{
			shown = cells.get(0).kind;
		}
		CompactLayout.place(cells, activityIcon != null);

		String value;
		Color color;
		switch (shown)
		{
			case PRAYER:
				value = String.valueOf(prayer);
				color = prayColor;
				break;
			case ENERGY:
				value = String.valueOf(energy);
				color = energyColor;
				break;
			case SPEC:
				value = String.valueOf(spec);
				color = specColor;
				break;
			case HITPOINTS:
			default:
				value = String.valueOf(hitpoints);
				color = hpColor;
				break;
		}
		awtrixClient.pushFocusStat(value, color, cells, background, activityIcon);
	}

	/**
	 * On the focus page every stat that is on gets a bar, since the one large value is the
	 * only thing with room for digits.
	 */
	private static StatStyle barOnly(StatStyle style)
	{
		return style.isShown() ? StatStyle.BAR : StatStyle.OFF;
	}

	private boolean isShown(StatKind kind)
	{
		switch (kind)
		{
			case PRAYER:
				return prayerStyle().isShown();
			case ENERGY:
				return energyStyle().isShown();
			case SPEC:
				return config.specStyle().isShown();
			case HITPOINTS:
			default:
				return hitpointsStyle().isShown();
		}
	}

	/*
	 * A quick profile writes the settings it stands for, so every one of these is now
	 * the plain setting. Nothing is decided twice.
	 */

	private StatStyle hitpointsStyle()
	{
		return config.hitpointsStyle();
	}

	private StatStyle prayerStyle()
	{
		return config.prayerStyle();
	}

	private StatStyle energyStyle()
	{
		return config.energyStyle();
	}

	private boolean statsEnabled()
	{
		return config.statsEnabled();
	}

	private SkillProgressMode skillProgressMode()
	{
		return config.skillProgress();
	}

	/**
	 * The icon for what you are doing, or null when you have asked not to see one. That
	 * is all the two icon settings decide: the skill page behind it, the alternation and
	 * the rate all carry on either way, which they did not used to.
	 */
	private String activityIcon(SkillActivity activity)
	{
		if (activity == null)
		{
			return null;
		}
		if (activityFromCombat ? !config.activityInCombat() : !config.showActivity())
		{
			return null;
		}
		return activity.iconData();
	}

	/**
	 * AWTRIX's small font is 4px per character and 2px per space, which is what the skill
	 * progress page still measures its two words against.
	 */
	static int compactTextWidth(List<String> words)
	{
		if (words.isEmpty())
		{
			return 0;
		}
		int chars = 0;
		for (String word : words)
		{
			chars += word.length();
		}
		// The last character's 1px spacing column is blank.
		return chars * CHAR_WIDTH + (words.size() - 1) * SPACE_WIDTH - 1;
	}

	static boolean isLevelUp(Integer previousLevel, int level)
	{
		return previousLevel != null && level > previousLevel;
	}

	private boolean showsSkillProgress(SkillActivity activity)
	{
		return activity != null
			&& skillProgressMode().isShown()
			&& (!activityFromCombat || config.skillProgressInCombat())
			&& !(config.hideMaxedSkill() && activityAtMax(activity));
	}

	/**
	 * The skill the page would be about. Melee follows whichever of attack, strength
	 * and defence the XP is going to; before any has arrived it is attack, which is
	 * the same skill the page itself would draw.
	 */
	private Skill skillOnPage(SkillActivity activity)
	{
		if (activity == SkillActivity.MELEE && meleeSkill != null)
		{
			return meleeSkill;
		}
		return SkillActivities.skillFor(activity);
	}

	/** Real level, so a boost cannot hide a skill that is still under 99 or keep one that is. */
	private boolean skillAtMax(Skill skill)
	{
		return skillIsMaxed(client.getRealSkillLevel(skill));
	}

	private boolean activityAtMax(SkillActivity activity)
	{
		Skill skill = skillOnPage(activity);
		return skill != null && skillAtMax(skill);
	}

	/** A skill at 99. Past that the real level stops, however much XP is still coming in. */
	static boolean skillIsMaxed(int realLevel)
	{
		return realLevel >= Experience.MAX_REAL_LEVEL;
	}

	/**
	 * Activities that still have a skill page. A skill at 99 is taken out so it does not
	 * spend a turn of the panel that a skill still climbing could have used.
	 */
	static List<SkillActivity> activitiesForSkillPage(List<SkillActivity> training, Predicate<SkillActivity> maxed)
	{
		List<SkillActivity> open = new ArrayList<>();
		for (SkillActivity activity : training)
		{
			if (maxed == null || !maxed.test(activity))
			{
				open.add(activity);
			}
		}
		return open;
	}

	/**
	 * The stats as columns down the right edge of the skill page, so the page can keep
	 * the panel through a fight without your health leaving it. Empty unless it was asked
	 * for, which is the page as it was. It does not ask whether the stats page itself is
	 * switched on: this is a different page, and turning that one off is not a reason to
	 * drop your health off this one.
	 */
	private List<CompactLayout.Cell> edgeBars(int hitpoints, int hitpointsMax, int hpPercent, int prayer,
		int prayerMax, int prayerPercent, int energy, int spec, long now)
	{
		List<CompactLayout.Cell> cells = new ArrayList<>();
		if (!config.skillProgressBars())
		{
			return cells;
		}
		// Whatever each stat is set to, here it is a column: there is no room on this page
		// for a number beside the level and the rate. The flashing colours are left out of
		// it as well, since the page is already carrying a gradient of its own.
		addCell(cells, StatKind.HITPOINTS, barOnly(hitpointsStyle()), hitpoints, hpPercent, hitpointsMax,
			StatKind.HITPOINTS.drained(hpPercent), null, now);
		addCell(cells, StatKind.PRAYER, barOnly(prayerStyle()), prayer, prayerPercent, prayerMax,
			StatKind.PRAYER.drained(prayerPercent), null, now);
		addCell(cells, StatKind.ENERGY, barOnly(energyStyle()), energy, energy, 100,
			StatKind.ENERGY.drained(energy), null, now);
		addCell(cells, StatKind.SPEC, barOnly(config.specStyle()), spec, spec, 100,
			StatKind.SPEC.drained(spec), null, now);
		CompactLayout.placeEdge(cells);
		return cells;
	}

	private void pushSkillProgress(SkillActivity activity, List<CompactLayout.Cell> edgeBars)
	{
		Skill skill = activity == SkillActivity.MELEE && meleeSkill != null
			? meleeSkill
			: SkillActivities.skillFor(activity);
		if (skill == null)
		{
			pushActivityLabel(activity, null);
			return;
		}
		int columns = 0;
		for (CompactLayout.Cell cell : edgeBars)
		{
			if (cell.showsBar())
			{
				columns++;
			}
		}
		long now = System.currentTimeMillis();
		int xp = client.getSkillExperience(skill);
		int level = Math.min(Experience.MAX_REAL_LEVEL, Experience.getLevelForXp(xp));
		// Level and XP/h swap once every skill sharing the panel has had its turn, so a
		// skill is not stuck always showing the same one of the two.
		boolean rateTurn = (rotation.turns() / Math.max(1, trainingCount)) % 2 == 1;
		List<AwtrixClient.TextFragment> text;
		if (inPlaceDropText != null && now < inPlaceDropUntilMs)
		{
			text = new ArrayList<>();
			text.add(new AwtrixClient.TextFragment(inPlaceDropText, XP_DROP_COLOR));
		}
		else
		{
			text = skillProgressText(skillProgressMode(), level, xpPerHour(skill, now), rateTurn, activity.color(),
				PANEL_WIDTH - ICON_WIDTH - CompactLayout.edgeReserve(columns));
		}
		awtrixClient.pushSkillProgress(text, levelProgress(xp),
			config.progressGradient().stops(activity.color()), activity.iconData(), edgeBars);
	}

	/**
	 * RuneLite's XP Tracker reports 0 while it has nothing for the skill or is switched off,
	 * and then our own session average is used.
	 */
	private int xpPerHour(Skill skill, long nowMs)
	{
		XpRateSource source = config.xpRateSource();
		if (source == XpRateSource.SLIDING_WINDOW)
		{
			return xpRates.perHourWindow(skill, nowMs, config.xpRateWindowSeconds() * 1000L);
		}
		if (source == XpRateSource.XP_TRACKER)
		{
			try
			{
				int tracked = xpTrackerService.getXpHr(skill);
				if (tracked > 0)
				{
					return tracked;
				}
			}
			catch (RuntimeException ex)
			{
				log.debug("XP Tracker rate unavailable for {}", skill, ex);
			}
		}
		return xpRates.perHour(skill, nowMs);
	}

	/**
	 * Level and rate side by side when they fit beside the icon, otherwise one at a time.
	 * The rate is left out until there is one.
	 */
	static List<AwtrixClient.TextFragment> skillProgressText(SkillProgressMode mode, int level, int perHour,
		boolean rateTurn, Color levelColor)
	{
		return skillProgressText(mode, level, perHour, rateTurn, levelColor, PANEL_WIDTH - ICON_WIDTH);
	}

	/**
	 * {@code available} is what the words have left once the icon and any edge columns have
	 * taken theirs, which is what decides whether level and rate can share the line.
	 */
	static List<AwtrixClient.TextFragment> skillProgressText(SkillProgressMode mode, int level, int perHour,
		boolean rateTurn, Color levelColor, int available)
	{
		List<AwtrixClient.TextFragment> text = new ArrayList<>();
		String levelText = String.valueOf(level);
		boolean hasRate = perHour >= 0 && mode != SkillProgressMode.LEVEL;
		String rate = hasRate ? XpRateTracker.format(perHour) : null;
		boolean both = hasRate && mode == SkillProgressMode.BOTH
			&& compactTextWidth(Arrays.asList(levelText, rate)) <= available;

		if (both)
		{
			text.add(new AwtrixClient.TextFragment(levelText, levelColor));
			text.add(new AwtrixClient.TextFragment(" ", Color.DARK_GRAY));
			text.add(new AwtrixClient.TextFragment(rate, XP_RATE_COLOR));
		}
		else if (hasRate && (mode == SkillProgressMode.XP_RATE || rateTurn))
		{
			text.add(new AwtrixClient.TextFragment(rate + "/h", XP_RATE_COLOR));
		}
		else
		{
			text.add(new AwtrixClient.TextFragment(levelText, levelColor));
		}
		return text;
	}

	/**
	 * Percent of the way from this level to the next. A maxed skill shows a full bar.
	 */
	static int levelProgress(int xp)
	{
		int level = Experience.getLevelForXp(xp);
		if (level >= Experience.MAX_REAL_LEVEL)
		{
			return 100;
		}
		int start = Experience.getXpForLevel(level);
		int next = Experience.getXpForLevel(level + 1);
		return (int) Math.max(0L, Math.min(100L, (xp - start) * 100L / (next - start)));
	}

	private void pushActivityLabel(SkillActivity activity, Color background)
	{
		List<CompactLayout.Cell> cells = new ArrayList<>();
		cells.add(CompactLayout.Cell.label(activity.label(), activity.color()));
		CompactLayout.place(cells, true);
		awtrixClient.pushCompact(cells, background, activity.iconData());
	}

	/**
	 * What you are doing, which is a different question from what gets drawn for it.
	 * The icon settings answer the second; asking them here is what used to leave the
	 * skill page switched on and showing nothing at all.
	 */
	private SkillActivity currentActivity()
	{
		activityFromCombat = false;
		trainingCount = 1;
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			activityTracker.reset();
			rotation.reset();
			return null;
		}

		long now = System.currentTimeMillis();
		long holdMs = activityHoldMs();
		// The engorged bloodwood tree pays its woodcutting XP for the chopping and none
		// at all for the three quarters of a minute of sap running into the bucket, and
		// the animation for it is not one of the chopping ones. The tree keeps count of
		// that itself, so it is asked rather than guessed at.
		if (isDrainingBloodwood())
		{
			// Recorded the way XP is, so the icon survives the moment the bucket fills
			// and the drain stops, for as long as the hold lasts.
			activityTracker.onXp(SkillActivity.WOODCUTTING, now);
			return shareTurns(SkillActivity.WOODCUTTING, now);
		}
		int animation = player.getAnimation();
		SkillActivity fromAnimation = SkillActivities.fromAnimation(animation);
		SkillActivity skill = activityTracker.onAnimation(fromAnimation, animation, now, holdMs);
		if (skill != null)
		{
			return shareTurns(skill, now);
		}
		if (isDialogOpen())
		{
			return null;
		}

		boolean interacting = player.getInteracting() != null;
		if (!inCombatHold(interacting, lastCombatMs, now, holdMs))
		{
			return null;
		}
		activityFromCombat = true;
		return CombatStyles.current(client);
	}

	/**
	 * Whether a fight is on, which is a different question from which icon to draw: the
	 * icon can be switched off, and is held for as long as you asked, while this decides
	 * whether the panel is allowed to call you away.
	 */
	private void updateCombat(long now)
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			lastCombatMs = 0L;
			inCombat = false;
			return;
		}
		int animation = player.getAnimation();
		// A swing at whatever you are locked on to. A mapped skilling animation is not
		// one: fishing spots and plenty else you train on are NPCs you interact with.
		if (player.getInteracting() != null && animation != -1 && SkillActivities.fromAnimation(animation) == null)
		{
			lastCombatMs = now;
		}
		inCombat = isInCombat(lastCombatMs, now, COMBAT_GRACE_MS);
	}

	static boolean isInCombat(long lastCombatMs, long nowMs, long graceMs)
	{
		return lastCombatMs > 0L && nowMs - lastCombatMs < graceMs;
	}

	/**
	 * Combat icon stays while you still have a target. A hold of 0 keeps it for
	 * the whole fight; a positive hold drops it if you have not attacked recently.
	 */
	static boolean inCombatHold(boolean interacting, long lastCombatMs, long nowMs, long holdMs)
	{
		if (!interacting || lastCombatMs <= 0L)
		{
			return false;
		}
		return holdMs <= 0L || nowMs - lastCombatMs < holdMs;
	}

	/**
	 * Several skills paying XP at once — cooking what you fish, fletching what you chop —
	 * each get the panel for a turn of the rotate time, instead of every drop pulling it
	 * to whichever skill paid last.
	 */
	private SkillActivity shareTurns(SkillActivity current, long now)
	{
		// With alternation off nothing shares the panel, but the rotation is still run so
		// that the level and rate keep swapping over on the same beat.
		List<SkillActivity> training = config.alternateSkills()
			? activityTracker.training(now, SkillActivityTracker.TRAINING_MS)
			: new ArrayList<>();
		// A skill with no XP behind it yet — an animation the map knows, and a tree can be
		// a long time between logs — is part of what you have going and takes its turn with
		// the rest, rather than pulling the panel off whoever is holding it.
		SkillRotation.insertInOrder(training, current);
		if (config.hideMaxedSkill())
		{
			training = activitiesForSkillPage(training, this::activityAtMax);
		}
		// Everything going on is already 99. Keep the activity so its icon can stay on
		// the stats line; the page itself is left off.
		if (training.isEmpty())
		{
			trainingCount = 1;
			return current;
		}
		SkillActivity followed = training.contains(current) ? current : training.get(0);
		trainingCount = Math.max(1, training.size());
		SkillActivity turn = rotation.turn(training, followed, now, turnMs());
		return turn == null ? followed : turn;
	}

	/** How long one skill holds the panel before the rotation moves on. */
	private long turnMs()
	{
		return Math.max(1, config.skillRotateSeconds()) * 1000L;
	}

	private long activityHoldMs()
	{
		return Math.max(0, config.activityHoldSeconds()) * 1000L;
	}

	/**
	 * Sap running out of the engorged bloodwood tree into your bucket. The draining
	 * varbit is set from the moment the tree is full, and the progress varbit falls
	 * away from full once the sap is actually running, which is what tells the drain
	 * apart from the tree standing there waiting to be tapped.
	 */
	private boolean isDrainingBloodwood()
	{
		return client.getVarbitValue(VarbitID.AFK_BLOODWOOD_TREE_DRAINING) > 0
			&& client.getVarbitValue(VarbitID.AFK_BLOODWOOD_TREE_PROGRESS) < BLOODWOOD_FULL_PROGRESS;
	}

	private boolean isDialogOpen()
	{
		return visible(WidgetInfo.DIALOG_NPC_TEXT)
			|| visible(WidgetInfo.DIALOG_PLAYER_TEXT)
			|| visible(WidgetInfo.DIALOG_OPTION_OPTIONS);
	}

	private boolean visible(WidgetInfo info)
	{
		Widget widget = client.getWidget(info);
		return widget != null && !widget.isHidden();
	}

	/**
	 * Standing still with no action animation. Walk, run, and any chop/attack/skilling
	 * animation are not idle. Idle turn poses still count as standing.
	 * The default run animation is 824, which some players also report as an idle-turn id,
	 * so movement poses are excluded before the idle-turn check.
	 */
	static boolean isStandingIdle(int actionAnimation, int poseAnimation, int idlePose, int idleRotateLeft, int idleRotateRight,
		int walk, int walkBack, int walkLeft, int walkRight, int run)
	{
		if (actionAnimation != -1 || isMovementPose(poseAnimation, walk, walkBack, walkLeft, walkRight, run))
		{
			return false;
		}
		return poseAnimation == -1
			|| poseAnimation == idlePose
			|| poseAnimation == idleRotateLeft
			|| poseAnimation == idleRotateRight;
	}

	static boolean isMovementPose(int poseAnimation, int walk, int walkBack, int walkLeft, int walkRight, int run)
	{
		return poseAnimation != -1
			&& (poseAnimation == walk
				|| poseAnimation == walkBack
				|| poseAnimation == walkLeft
				|| poseAnimation == walkRight
				|| poseAnimation == run);
	}

	/**
	 * Idle only while standing with no click-to-move destination and no tile change.
	 * A destination is set as soon as the character is told to move, before the run pose starts.
	 */
	static boolean isCharacterIdle(boolean standingIdle, boolean hasMoveDestination, boolean locationChanged)
	{
		return standingIdle && !hasMoveDestination && !locationChanged;
	}

	/**
	 * AFK turns on from input idle, and stays on until the character leaves the idle pose.
	 * Mouse movement does not dismiss it.
	 */
	static boolean shouldShowAfk(boolean characterIdle, boolean alreadyShowing, boolean inputIdle)
	{
		if (!characterIdle)
		{
			return false;
		}
		return alreadyShowing || inputIdle;
	}

	/**
	 * Timestamp of the idle pause after a wall-rock swing, or 0 when there is not one.
	 * Any other animation clears it. A pause already underway keeps its start, so later
	 * idle ticks do not push the window out.
	 */
	static long wallMineIdleSince(int actionAnimation, int previousAction, long nowMs, long idleSinceMs)
	{
		if (actionAnimation != -1)
		{
			return 0L;
		}
		if (idleSinceMs > 0L)
		{
			return idleSinceMs;
		}
		if (SkillActivities.isWallMining(previousAction))
		{
			return nowMs;
		}
		return 0L;
	}

	/** True for the short idle between swings on a wall rock that is still being mined. */
	static boolean inWallMineGap(long nowMs, long idleSinceMs)
	{
		return idleSinceMs > 0L && nowMs - idleSinceMs < WALL_MINE_GAP_MS;
	}

	private boolean isAfk()
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return false;
		}

		WorldPoint here = player.getWorldLocation();
		boolean locationChanged = lastPlayerLocation != null && !lastPlayerLocation.equals(here);
		lastPlayerLocation = here;

		long now = System.currentTimeMillis();
		int action = player.getAnimation();
		wallMineIdleSinceMs = wallMineIdleSince(action, lastActionAnimation, now, wallMineIdleSinceMs);
		lastActionAnimation = action;

		boolean characterIdle = isCharacterIdle(
			isStandingIdle(
				player.getAnimation(),
				player.getPoseAnimation(),
				player.getIdlePoseAnimation(),
				player.getIdleRotateLeft(),
				player.getIdleRotateRight(),
				player.getWalkAnimation(),
				player.getWalkRotate180(),
				player.getWalkRotateLeft(),
				player.getWalkRotateRight(),
				player.getRunAnimation()),
			client.getLocalDestinationLocation() != null,
			locationChanged);
		if (!characterIdle || inWallMineGap(now, wallMineIdleSinceMs))
		{
			return false;
		}
		if (afkActive)
		{
			return true;
		}

		int afkSeconds = Math.max(0, config.afkSeconds());
		long sincePressMs = System.currentTimeMillis() - client.getMouseLastPressedMillis();
		if (recentClickBlocksAfk(sincePressMs, afkSeconds))
		{
			return false;
		}

		int idleTicks = client.getKeyboardIdleTicks();
		int mouseIdle = client.getMouseIdleTicks();
		if (mouseIdle < idleTicks)
		{
			idleTicks = mouseIdle;
		}

		return shouldShowAfk(true, false, inputIdleLongEnough(idleTicks, afkSeconds));
	}

	/**
	 * A click counts as input even when the pointer does not move.
	 * At 0 seconds only the current client tick is ignored.
	 */
	static boolean recentClickBlocksAfk(long sincePressMs, int afkSeconds)
	{
		long graceMs = afkSeconds <= 0 ? Constants.CLIENT_TICK_LENGTH : 1000L;
		return sincePressMs < graceMs;
	}

	/**
	 * Idle ticks are 0 on a tick that received input. Zero seconds means the next tick after input stops.
	 */
	static boolean inputIdleLongEnough(int idleTicks, int afkSeconds)
	{
		if (afkSeconds <= 0)
		{
			return idleTicks > 0;
		}
		int thresholdTicks = (afkSeconds * 1000) / Constants.CLIENT_TICK_LENGTH;
		return idleTicks >= thresholdTicks;
	}

	private void resetState()
	{
		afkActive = false;
		lastAfkSentMs = 0L;
		lastHpAlertValue = Integer.MAX_VALUE;
		lastPrayerAlertValue = Integer.MAX_VALUE;
		wasLowHp = false;
		wasLowPray = false;
		wasSpecReady = false;
		lastSpec = -1;
		specReadyUntilMs = 0L;
		bigStatIndex = 0;
		statPercent.clear();
		ghostPercent.clear();
		ghostUntilMs.clear();
		focusStat = null;
		focusUntilMs = 0L;
		focusChange = 0;
		nextRotateMs = 0L;
		nextOverlayRotateMs = 0L;
		overlayRotateIndex = 0;
		currentOverlay = null;
		compactFlashOn = false;
		afkHeld = false;
		activityTracker.reset();
		skillXp.clear();
		skillLevels.clear();
		lastPlayerLocation = null;
		lastActionAnimation = -1;
		wallMineIdleSinceMs = 0L;
		lastCombatMs = 0L;
		inCombat = false;
		activityFromCombat = false;
		xpRates.reset();
		tickXpGains.clear();
		meleeSkill = null;
		clearPendingDrop();
		nextDropMs = 0L;
		levelUpUntilMs = 0L;
		inPlaceDropText = null;
		inPlaceDropUntilMs = 0L;
		forestryTracker.reset();
		forestryEvent = null;
		forestryStartMs = 0L;
		forestryUntilMs = 0L;
		forestryHeld = false;
		lastChoppingMs = 0L;
		stopForestryFrames();
		stopPulse();
		pulseStartMs = 0L;
		shownActivity = null;
		shownFromCombat = false;
		fadeTo = null;
		fadeStartMs = 0L;
		trainingCount = 1;
		rotation.reset();
		stopFadeFrames();
		stopPreview();
		awtrixClient.setPulse(1.0);
	}

	private void stopPreview()
	{
		previewUntilMs = 0L;
		ScheduledFuture<?> ending = previewEnd;
		previewEnd = null;
		if (ending != null)
		{
			ending.cancel(false);
		}
	}

	private static final class BigStat
	{
		private final String value;
		private final Color color;
		private final int progress;
		private final String icon;

		private BigStat(String value, Color color, int progress, String icon)
		{
			this.value = value;
			this.color = color;
			this.progress = progress;
			this.icon = icon;
		}
	}
}
