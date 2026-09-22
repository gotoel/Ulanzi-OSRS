package com.ulanzi.osrs;

import com.google.inject.Provides;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
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
	private static final long AFK_EYES_REASSERT_MS = 700L;
	private static final Color PRAYER_COLOR = new Color(0x4F_A3_FF);
	private static final Color ENERGY_COLOR = new Color(0xFF_D4_00);
	private static final Color SPEC_COLOR = new Color(0xFF_8C_00);
	private static final Color HP_FLASH_OFF = new Color(40, 0, 0);
	private static final Color PRAYER_FLASH_OFF = new Color(0, 20, 40);
	private static final int PANEL_WIDTH = 32;
	private static final int ICON_WIDTH = 8;
	private static final int BAR_WIDTH = 2;
	private static final int CHAR_WIDTH = 4;
	private static final int SPACE_WIDTH = 2;
	private static final Skill[] MELEE_SKILLS = {Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE};
	private static final Color XP_RATE_COLOR = Color.WHITE;
	private static final Color XP_DROP_COLOR = Color.WHITE;
	static final long PULSE_MS = 1_000L;
	static final double PULSE_PEAK = 1.8;
	private static final long PULSE_ATTACK_MS = 120L;
	private static final long PULSE_FRAME_MS = 120L;
	private static final String CONFIG_VERSION_KEY = "configVersion";
	private static final int CONFIG_VERSION = 2;

	enum OverlayKind
	{
		AFK,
		LOW_HP,
		LOW_PRAYER
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
	private int bigStatIndex;
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
	private long lastCombatMs;
	private boolean activityFromCombat;
	private final XpRateTracker xpRates = new XpRateTracker();
	private final EnumMap<Skill, Integer> tickXpGains = new EnumMap<>(Skill.class);
	private Skill meleeSkill;
	private int pendingDropXp;
	private Skill pendingDropSkill;
	private long nextDropMs;
	private volatile long pulseStartMs;
	private volatile ScheduledFuture<?> pulseFrames;
	private String inPlaceDropText;
	private long inPlaceDropUntilMs;

	@Override
	protected void startUp()
	{
		resetState();
		configManager.unsetConfiguration(UlanziConfig.GROUP, "status");
		migrateConfig();
		awtrixClient.setMessageSink(this::postChat);
	}

	@Override
	protected void shutDown()
	{
		awtrixClient.clearAll();
		awtrixClient.setMessageSink(null);
		resetState();
	}

	/**
	 * Version 2 folded each "Show X" checkbox into its style dropdown, dropped the
	 * compact-only flash toggles, and made thresholds a percent by default.
	 * Anyone who had set a threshold keeps it as points.
	 */
	private void migrateConfig()
	{
		String version = configManager.getConfiguration(UlanziConfig.GROUP, CONFIG_VERSION_KEY);
		if (version != null)
		{
			return;
		}

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

		configManager.setConfiguration(UlanziConfig.GROUP, CONFIG_VERSION_KEY, CONFIG_VERSION);
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

		if ("testConnection".equals(event.getKey()))
		{
			if ("true".equals(event.getNewValue()))
			{
				awtrixClient.testConnection();
				configManager.setConfiguration(UlanziConfig.GROUP, "testConnection", false);
			}
			return;
		}

		if ("status".equals(event.getKey()))
		{
			return;
		}

		if ("afkEnabled".equals(event.getKey()) && !"true".equals(event.getNewValue()))
		{
			awtrixClient.dismissAfk();
			afkHeld = false;
			afkActive = false;
			lastAfkSentMs = 0L;
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
			awtrixClient.showLevelUp(event.getSkill().getName(), SkillActivities.levelUpIcon(event.getSkill()),
				event.getLevel(), config.levelUpSound());
			// The drop queues behind the celebration instead of stacking up under it.
			nextDropMs = Math.max(nextDropMs, now + AwtrixClient.LEVEL_UP_MS);
		}

		int xp = event.getXp();
		Integer previous = skillXp.put(event.getSkill(), xp);
		if (previous == null || xp <= previous)
		{
			return;
		}
		int gained = xp - previous;
		xpRates.onXp(event.getSkill(), gained, now);
		tickXpGains.merge(event.getSkill(), gained, Integer::sum);
		if (config.showActivity())
		{
			activityTracker.onXp(SkillActivities.fromSkillXp(event.getSkill()), now);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!tickXpGains.isEmpty())
		{
			meleeSkill = meleeSkill(tickXpGains, meleeSkill);
			queueXpDrop(tickXpGains);
			tickXpGains.clear();
		}
		refreshClock();
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
		for (int gained : gains.values())
		{
			pendingDropXp += gained;
		}
		pendingDropSkill = dropSkill(gains);
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
	 */
	private void flushXpDrop(boolean panelTaken, boolean progressPage)
	{
		if (!config.xpDrops())
		{
			pendingDropXp = 0;
			pendingDropSkill = null;
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
			pendingDropXp = 0;
			pendingDropSkill = null;
			return;
		}
		if (panelTaken)
		{
			pendingDropXp = 0;
			pendingDropSkill = null;
			return;
		}
		if (pendingDropXp <= 0 || now < nextDropMs)
		{
			return;
		}
		XpDropDirection direction = config.xpDropDirection();
		long shownMs;
		if (direction == XpDropDirection.IN_PLACE && progressPage)
		{
			inPlaceDropText = fitXpDropText(pendingDropXp);
			inPlaceDropUntilMs = now + AwtrixClient.XP_DROP_IN_PLACE_MS;
			shownMs = AwtrixClient.XP_DROP_IN_PLACE_MS;
		}
		else
		{
			String icon = pendingDropSkill == null ? null : SkillActivities.levelUpIcon(pendingDropSkill);
			String text = direction.isHorizontal() ? xpDropText(pendingDropXp) : fitXpDropText(pendingDropXp);
			shownMs = awtrixClient.showXpDrop(text, XP_DROP_COLOR, icon, direction);
		}
		nextDropMs = now + shownMs;
		pendingDropXp = 0;
		pendingDropSkill = null;
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
	 * Full brightness on landing, then a fade back to normal across the rest of the
	 * second. 1.0 means nothing is being scaled.
	 */
	static double pulseFactor(long startMs, long nowMs)
	{
		if (startMs <= 0L)
		{
			return 1.0;
		}
		long elapsed = nowMs - startMs;
		if (elapsed < 0L || elapsed >= PULSE_MS)
		{
			return 1.0;
		}
		if (elapsed < PULSE_ATTACK_MS)
		{
			return PULSE_PEAK;
		}
		double fade = (elapsed - PULSE_ATTACK_MS) / (double) (PULSE_MS - PULSE_ATTACK_MS);
		return 1.0 + (PULSE_PEAK - 1.0) * (1.0 - fade);
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

			ThresholdUnit unit = config.thresholdUnit();
			boolean lowHp = config.lowHitpointsEnabled()
				&& unit.isLow(hitpoints, hitpointsMax, config.lowHitpointsThreshold());
			boolean lowPray = config.lowPrayerEnabled()
				&& unit.isLow(prayer, prayerMax, config.lowPrayerThreshold());
			SkillActivity activity = currentActivity();
			boolean afk = config.afkEnabled() && isAfk();
			if (!afk && afkHeld)
			{
				awtrixClient.dismissAfk();
				afkHeld = false;
				afkActive = false;
				lastAfkSentMs = 0L;
			}
			AlertDisplayMode display = config.alertDisplayMode();

			fireThresholdPulses(hitpoints, prayer, lowHp, lowPray, display);

			OverlayKind chosen = chooseOverlay(afk, lowHp, lowPray);
			AlertDisplayMode mode = chosen == OverlayKind.AFK ? config.afkDisplay() : display;
			flushXpDrop(chosen != null && mode.usesFullPanel(),
				chosen == null && activity != null && showsSkillProgress());
			// Everything pushed below this point is scaled by the pulse.
			awtrixClient.setPulse(pulseFactor(pulseStartMs, System.currentTimeMillis()));
			if (chosen != null)
			{
				if (afkHeld && !(chosen == OverlayKind.AFK && mode.usesFullPanel()))
				{
					awtrixClient.dismissAfk();
					afkHeld = false;
				}
				if (mode.usesFullPanel())
				{
					showFullPanelOverlay(chosen, hitpoints, prayer);
				}
				if (mode.usesStatsView())
				{
					showStatsViewOverlay(chosen, hitpoints, hitpointsMax, prayer, prayerMax, energy, spec, lowHp, lowPray, activity);
				}
				return;
			}

			clearPanelOverlays(afk);

			if (!config.statsEnabled() && activity == null)
			{
				awtrixClient.clearStats();
				return;
			}

			pushStats(hitpoints, hitpointsMax, prayer, prayerMax, energy, spec, lowHp, lowPray, null, activity);
		}
		catch (Exception ex)
		{
			log.warn("Ulanzi update failed", ex);
		}
	}

	/**
	 * Beep / one-shot full-panel flash when crossing under threshold or dropping further.
	 */
	private void fireThresholdPulses(int hitpoints, int prayer, boolean lowHp, boolean lowPray,
		AlertDisplayMode display)
	{
		if (lowHp)
		{
			boolean entered = !wasLowHp;
			boolean dropped = config.realertOnDrop() && hitpoints < lastHpAlertValue;
			if (entered || dropped)
			{
				boolean beep = config.lowHpBeep();
				boolean popupFlash = config.lowHpFlash() && display.usesFullPanel()
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
				boolean beep = config.lowPrayerBeep();
				boolean popupFlash = config.lowPrayerFlash() && display.usesFullPanel()
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
	}

	private OverlayKind chooseOverlay(boolean afk, boolean lowHp, boolean lowPray)
	{
		List<OverlayKind> active = new ArrayList<>();
		if (afk)
		{
			active.add(OverlayKind.AFK);
		}
		if (lowHp && config.lowHpFlash())
		{
			active.add(OverlayKind.LOW_HP);
		}
		if (lowPray && config.lowPrayerFlash())
		{
			active.add(OverlayKind.LOW_PRAYER);
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

	private void showFullPanelOverlay(OverlayKind kind, int hitpoints, int prayer)
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
				pushStats(hitpoints, hitpointsMax, prayer, prayerMax, energy, spec, lowHp, lowPray, OverlayKind.AFK, activity);
				break;
			}
			case LOW_HP:
			case LOW_PRAYER:
				pushStats(hitpoints, hitpointsMax, prayer, prayerMax, energy, spec, lowHp, lowPray, kind, activity);
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

		currentOverlay = null;
		nextOverlayRotateMs = 0L;
		overlayRotateIndex = 0;
	}

	private void pushStats(int hitpoints, int hitpointsMax, int prayer, int prayerMax, int energy, int spec,
		boolean lowHp, boolean lowPray, OverlayKind focus, SkillActivity activity)
	{
		if (focus == null && activity != null && showsSkillProgress())
		{
			pushSkillProgress(activity);
			return;
		}

		String activityIcon = activity == null ? null : activity.iconData();
		if (!config.statsEnabled())
		{
			if (activity == null)
			{
				awtrixClient.clearStats();
			}
			else
			{
				pushActivityLabel(activity, null);
			}
			return;
		}

		boolean flashHp = lowHp && config.lowHpFlash();
		boolean flashPray = lowPray && config.lowPrayerFlash();
		compactFlashOn = (flashHp || flashPray) && !compactFlashOn;

		Color hpColor = AwtrixClient.hpColor(hitpoints, hitpointsMax);
		if (flashHp)
		{
			hpColor = compactFlashOn ? Color.RED : HP_FLASH_OFF;
		}
		Color prayColor = PRAYER_COLOR;
		if (flashPray)
		{
			prayColor = compactFlashOn ? PRAYER_COLOR : PRAYER_FLASH_OFF;
		}

		int hpPercent = hitpointsMax <= 0 ? 0 : Math.min(100, Math.max(0, (hitpoints * 100) / hitpointsMax));
		int prayerPercent = prayerMax <= 0 ? 0 : Math.min(100, Math.max(0, (prayer * 100) / prayerMax));
		AfkTintMode tintMode = focus == OverlayKind.AFK ? config.afkTintMode() : AfkTintMode.OFF;
		Color tint = tintMode != AfkTintMode.OFF ? config.afkTintColor() : null;
		Color afkBackground = tintMode.tintsBackground() ? tint : null;
		Color energyColor = ENERGY_COLOR;
		Color specColor = SPEC_COLOR;
		Color barColor = flashHp ? Color.RED : AwtrixClient.hpColor(hitpoints, hitpointsMax);
		Color spaceColor = Color.DARK_GRAY;
		if (tintMode.tintsValues() && tint != null)
		{
			hpColor = tint;
			prayColor = tint;
			energyColor = tint;
			specColor = tint;
			barColor = tint;
			spaceColor = tint;
		}

		if (focus == OverlayKind.LOW_HP && config.statsLayout() == StatsLayout.BIG)
		{
			int progress = config.hitpointsStyle().showsBar() ? hpPercent : -1;
			awtrixClient.pushBigStat("", String.valueOf(hitpoints), hpColor, progress,
				tintMode.tintsValues() ? tint : Color.RED, afkBackground,
				activityIcon != null ? activityIcon : PixelIcon.HITPOINTS.iconData());
			return;
		}
		if (focus == OverlayKind.LOW_PRAYER && config.statsLayout() == StatsLayout.BIG)
		{
			int progress = config.prayerStyle().showsBar() ? prayerPercent : -1;
			awtrixClient.pushBigStat("", String.valueOf(prayer), prayColor, progress, prayColor, afkBackground,
				activityIcon != null ? activityIcon : SkillActivity.PRAYER.iconData());
			return;
		}

		if (config.statsLayout() == StatsLayout.COMPACT || focus != null)
		{
			String label = null;
			Color labelColor = null;
			if (focus == OverlayKind.AFK && config.afkCompactLabel())
			{
				label = AwtrixClient.truncate(config.afkText(), 4);
				labelColor = tintMode.tintsValues() && tint != null
					? tint
					: (!compactFlashOn ? config.afkTextColor() : Color.DARK_GRAY);
			}

			boolean routine = focus == null || focus == OverlayKind.AFK;
			StatStyle hpStyle = focus == OverlayKind.LOW_HP ? atLeastValue(config.hitpointsStyle()) : config.hitpointsStyle();
			StatStyle prayerStyle = focus == OverlayKind.LOW_PRAYER ? atLeastValue(config.prayerStyle()) : config.prayerStyle();
			List<CompactStat> line = new ArrayList<>();
			addCompactStat(line, hpStyle, hitpoints, hpPercent, flashHp ? hpColor : barColor);
			addCompactStat(line, prayerStyle, prayer, prayerPercent, prayColor);
			if (routine)
			{
				addCompactStat(line, config.energyStyle(), energy, energy, energyColor);
				addCompactStat(line, config.specStyle(), spec, spec, specColor);
			}
			fitCompactLine(line, label, activityIcon != null);

			List<AwtrixClient.TextFragment> fragments = new ArrayList<>();
			List<AwtrixClient.ColumnBar> bars = new ArrayList<>();
			if (label != null)
			{
				fragments.add(new AwtrixClient.TextFragment(label, labelColor));
			}
			for (CompactStat stat : line)
			{
				if (stat.style.showsBar())
				{
					bars.add(new AwtrixClient.ColumnBar(stat.percent, stat.color));
				}
				if (stat.style.showsValue())
				{
					if (!fragments.isEmpty())
					{
						fragments.add(new AwtrixClient.TextFragment(" ", spaceColor));
					}
					fragments.add(new AwtrixClient.TextFragment(String.valueOf(stat.value), stat.color));
				}
			}

			if (fragments.isEmpty() && activity != null && bars.isEmpty())
			{
				fragments.add(new AwtrixClient.TextFragment(activity.label(), activity.color()));
			}
			if (fragments.isEmpty() && bars.isEmpty())
			{
				awtrixClient.clearStats();
				return;
			}

			awtrixClient.pushCompactStats(fragments, bars, afkBackground, activityIcon);
			return;
		}

		List<BigStat> stats = new ArrayList<>();
		addBigStat(stats, config.hitpointsStyle(), hitpoints, hpPercent, hpColor, PixelIcon.HITPOINTS.iconData());
		addBigStat(stats, config.prayerStyle(), prayer, prayerPercent, prayColor, SkillActivity.PRAYER.iconData());
		addBigStat(stats, config.energyStyle(), energy, energy, energyColor, PixelIcon.RUN_ENERGY.iconData());
		addBigStat(stats, config.specStyle(), spec, spec, specColor, PixelIcon.SPECIAL_ATTACK.iconData());

		if (stats.isEmpty())
		{
			if (activity == null)
			{
				awtrixClient.clearStats();
			}
			else
			{
				pushActivityLabel(activity, afkBackground);
			}
			return;
		}

		long now = System.currentTimeMillis();
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

	private static void addCompactStat(List<CompactStat> line, StatStyle style, int value, int percent, Color color)
	{
		if (style.isShown())
		{
			line.add(new CompactStat(style, value, percent, color));
		}
	}

	private static StatStyle atLeastValue(StatStyle style)
	{
		return style.isShown() ? style : StatStyle.VALUE;
	}

	/**
	 * AWTRIX's small font is 4px per character and 2px per space. An icon takes the left 8px
	 * and each bar 2px on the right. Values that no longer fit turn into bars, last one first.
	 */
	static void fitCompactLine(List<CompactStat> line, String label, boolean icon)
	{
		for (int i = line.size() - 1; i >= 0 && !compactLineFits(line, label, icon); i--)
		{
			CompactStat stat = line.get(i);
			if (stat.style.showsValue())
			{
				stat.style = StatStyle.BAR;
			}
		}
	}

	static boolean compactLineFits(List<CompactStat> line, String label, boolean icon)
	{
		List<String> words = new ArrayList<>();
		if (label != null)
		{
			words.add(label);
		}
		int bars = 0;
		for (CompactStat stat : line)
		{
			if (stat.style.showsBar())
			{
				bars++;
			}
			if (stat.style.showsValue())
			{
				words.add(String.valueOf(stat.value));
			}
		}
		int available = PANEL_WIDTH - (icon ? ICON_WIDTH : 0) - bars * BAR_WIDTH;
		return compactTextWidth(words) <= available;
	}

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

	private boolean showsSkillProgress()
	{
		return config.skillProgress().isShown() && (!activityFromCombat || config.skillProgressInCombat());
	}

	private void pushSkillProgress(SkillActivity activity)
	{
		Skill skill = activity == SkillActivity.MELEE && meleeSkill != null
			? meleeSkill
			: SkillActivities.skillFor(activity);
		if (skill == null)
		{
			pushActivityLabel(activity, null);
			return;
		}
		long now = System.currentTimeMillis();
		int xp = client.getSkillExperience(skill);
		int level = Math.min(Experience.MAX_REAL_LEVEL, Experience.getLevelForXp(xp));
		boolean rateTurn = (now / (config.statsRotateSeconds() * 1000L)) % 2 == 1;
		List<AwtrixClient.TextFragment> text;
		if (inPlaceDropText != null && now < inPlaceDropUntilMs)
		{
			text = new ArrayList<>();
			text.add(new AwtrixClient.TextFragment(inPlaceDropText, XP_DROP_COLOR));
		}
		else
		{
			text = skillProgressText(config.skillProgress(), level, xpPerHour(skill, now), rateTurn, activity.color());
		}
		awtrixClient.pushSkillProgress(text, levelProgress(xp),
			config.progressGradient().stops(activity.color()), activity.iconData());
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
		List<AwtrixClient.TextFragment> text = new ArrayList<>();
		String levelText = String.valueOf(level);
		boolean hasRate = perHour >= 0 && mode != SkillProgressMode.LEVEL;
		String rate = hasRate ? XpRateTracker.format(perHour) : null;
		boolean both = hasRate && mode == SkillProgressMode.BOTH
			&& compactTextWidth(Arrays.asList(levelText, rate)) <= PANEL_WIDTH - ICON_WIDTH;

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
		List<AwtrixClient.TextFragment> label = new ArrayList<>();
		label.add(new AwtrixClient.TextFragment(activity.label(), activity.color()));
		awtrixClient.pushCompactStats(label, -1, activity.color(), background, activity.iconData());
	}

	private SkillActivity currentActivity()
	{
		activityFromCombat = false;
		Player player = client.getLocalPlayer();
		if (!config.showActivity() || player == null)
		{
			activityTracker.reset();
			lastCombatMs = 0L;
			return null;
		}

		long now = System.currentTimeMillis();
		long holdMs = activityHoldMs();
		int animation = player.getAnimation();
		SkillActivity fromAnimation = SkillActivities.fromAnimation(animation);
		SkillActivity skill = activityTracker.onAnimation(fromAnimation, animation, now, holdMs);
		if (skill != null)
		{
			return skill;
		}
		if (isDialogOpen())
		{
			return null;
		}

		boolean interacting = player.getInteracting() != null;
		boolean attackAnimation = fromAnimation == null && animation != -1 && interacting;
		if (attackAnimation)
		{
			lastCombatMs = now;
		}
		if (!inCombatHold(interacting, lastCombatMs, now, holdMs))
		{
			return null;
		}
		activityFromCombat = true;
		return CombatStyles.current(client);
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

	private long activityHoldMs()
	{
		return Math.max(0, config.activityHoldSeconds()) * 1000L;
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
		if (!characterIdle)
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
		bigStatIndex = 0;
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
		lastCombatMs = 0L;
		activityFromCombat = false;
		xpRates.reset();
		tickXpGains.clear();
		meleeSkill = null;
		pendingDropXp = 0;
		pendingDropSkill = null;
		nextDropMs = 0L;
		inPlaceDropText = null;
		inPlaceDropUntilMs = 0L;
		stopPulse();
		pulseStartMs = 0L;
		awtrixClient.setPulse(1.0);
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

	static final class CompactStat
	{
		StatStyle style;
		final int value;
		final int percent;
		final Color color;

		CompactStat(StatStyle style, int value, int percent, Color color)
		{
			this.style = style;
			this.value = value;
			this.percent = percent;
			this.color = color;
		}
	}
}
