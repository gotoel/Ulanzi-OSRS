package com.ulanzi.osrs;

import com.google.inject.Provides;
import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Ulanzi OSRS Clock",
	description = "Shows AFK status, combat stats, and skilling activity on an AWTRIX NG Ulanzi TC001",
	tags = {"ulanzi", "awtrix", "afk", "hitpoints", "prayer", "skilling", "clock"}
)
public class UlanziOsrsPlugin extends Plugin
{
	private static final long AFK_REASSERT_MS = 5_000L;
	private static final long AFK_EYES_REASSERT_MS = 700L;
	private static final Color PRAYER_COLOR = new Color(0x4F_A3_FF);
	private static final Color ENERGY_COLOR = new Color(0xFF_D4_00);
	private static final Color SPEC_COLOR = new Color(0xFF_8C_00);
	private static final Color HP_FLASH_OFF = new Color(40, 0, 0);

	enum OverlayKind
	{
		AFK,
		LOW_HP,
		LOW_PRAYER
	}

	@Inject
	private Client client;

	@Inject
	private UlanziConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private AwtrixClient awtrixClient;

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
	private final SkillActivityTracker activityTracker = new SkillActivityTracker();
	private final EnumMap<Skill, Integer> skillXp = new EnumMap<>(Skill.class);

	@Override
	protected void startUp()
	{
		resetState();
		configManager.unsetConfiguration(UlanziConfig.GROUP, "status");
	}

	@Override
	protected void shutDown()
	{
		awtrixClient.clearAll();
		resetState();
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

		awtrixClient.clearCache();
		afkActive = false;
		lastAfkSentMs = 0L;
		bigStatIndex = 0;
		nextRotateMs = 0L;
		nextOverlayRotateMs = 0L;
		overlayRotateIndex = 0;
		currentOverlay = null;
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
		int xp = event.getXp();
		Integer previous = skillXp.put(event.getSkill(), xp);
		if (!config.showActivity() || previous == null || xp <= previous)
		{
			return;
		}
		activityTracker.onXp(SkillActivities.fromSkillXp(event.getSkill()), System.currentTimeMillis());
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
		{
			return;
		}

		try
		{
			int hitpoints = client.getBoostedSkillLevel(Skill.HITPOINTS);
			int hitpointsMax = client.getRealSkillLevel(Skill.HITPOINTS);
			int prayer = client.getBoostedSkillLevel(Skill.PRAYER);
			int prayerMax = client.getRealSkillLevel(Skill.PRAYER);
			int energy = Math.min(100, Math.max(0, client.getEnergy() / 100));
			int spec = Math.min(100, Math.max(0, client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT) / 10));

			boolean lowHp = config.lowHitpointsEnabled() && hitpoints <= config.lowHitpointsThreshold();
			boolean lowPray = config.lowPrayerEnabled() && prayer <= config.lowPrayerThreshold();
			SkillActivity activity = currentActivity();
			boolean afk = config.afkEnabled() && isAfk();
			AlertDisplayMode display = config.alertDisplayMode();

			fireThresholdPulses(hitpoints, prayer, lowHp, lowPray, display);

			OverlayKind chosen = chooseOverlay(afk, lowHp, lowPray);
			if (chosen != null)
			{
				if (display.usesFullPanel())
				{
					showFullPanelOverlay(chosen, hitpoints, prayer);
				}
				if (display.usesStatsView())
				{
					showStatsViewOverlay(chosen, hitpoints, hitpointsMax, prayer, prayerMax, energy, spec, lowHp, lowPray, activity);
				}
				if (display.usesFullPanel() && !display.usesStatsView())
				{
					return;
				}
				if (display.usesStatsView())
				{
					return;
				}
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
		// Never leave a full-panel AFK notification up in stats/compact alert mode.
		awtrixClient.dismissAfk();

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

		boolean flashHp = lowHp && config.lowHpFlash()
			&& (config.alertDisplayMode().usesStatsView() || config.compactFlashLowHp());
		boolean flashPray = lowPray && config.lowPrayerFlash()
			&& (config.alertDisplayMode().usesStatsView() || config.compactFlashLowPrayer());

		if (flashHp || flashPray)
		{
			compactFlashOn = !compactFlashOn;
		}
		else
		{
			compactFlashOn = false;
		}

		Color hpColor = AwtrixClient.hpColor(hitpoints, hitpointsMax);
		if (flashHp)
		{
			hpColor = compactFlashOn ? Color.RED : HP_FLASH_OFF;
		}
		Color prayColor = PRAYER_COLOR;
		if (flashPray)
		{
			prayColor = compactFlashOn ? PRAYER_COLOR : new Color(0, 20, 40);
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
			awtrixClient.pushBigStat("", String.valueOf(hitpoints), hpColor, hpPercent,
				tintMode.tintsValues() ? tint : Color.RED, afkBackground, activityIcon);
			return;
		}
		if (focus == OverlayKind.LOW_PRAYER && config.statsLayout() == StatsLayout.BIG)
		{
			awtrixClient.pushBigStat("", String.valueOf(prayer), prayColor, -1,
				tintMode.tintsValues() ? tint : Color.BLACK, afkBackground, activityIcon);
			return;
		}

		if (config.statsLayout() == StatsLayout.COMPACT || focus == OverlayKind.AFK
			|| focus == OverlayKind.LOW_HP || focus == OverlayKind.LOW_PRAYER)
		{
			List<AwtrixClient.TextFragment> fragments = new ArrayList<>();
			boolean first = true;

			if (focus == OverlayKind.AFK && config.afkCompactLabel())
			{
				boolean labelOn = !compactFlashOn;
				Color afkLabelColor = tintMode.tintsValues() && tint != null
					? tint
					: (labelOn ? config.afkTextColor() : Color.DARK_GRAY);
				fragments.add(new AwtrixClient.TextFragment(AwtrixClient.truncate(config.afkText(), 4), afkLabelColor));
				first = false;
			}

			if (config.showHitpoints() || focus == OverlayKind.LOW_HP)
			{
				if (!first)
				{
					fragments.add(new AwtrixClient.TextFragment(" ", spaceColor));
				}
				fragments.add(new AwtrixClient.TextFragment(String.valueOf(hitpoints), hpColor));
				first = false;
			}
			boolean prayerColumn = config.showPrayer() || focus == OverlayKind.LOW_PRAYER;
			boolean energyColumn = false;
			if (focus == null || focus == OverlayKind.AFK)
			{
				if (config.showEnergy())
				{
					energyColumn = true;
				}
				if (config.showSpec())
				{
					if (!first)
					{
						fragments.add(new AwtrixClient.TextFragment(" ", spaceColor));
					}
					fragments.add(new AwtrixClient.TextFragment(String.valueOf(spec), specColor));
				}
			}

			if (fragments.isEmpty() && activity != null)
			{
				fragments.add(new AwtrixClient.TextFragment(activity.label(), activity.color()));
			}
			if (fragments.isEmpty() && !energyColumn && !prayerColumn)
			{
				awtrixClient.clearStats();
				return;
			}

			boolean showBar = config.showHitpoints() || focus == OverlayKind.LOW_HP;
			awtrixClient.pushCompactStats(fragments, showBar ? hpPercent : -1, barColor, afkBackground, activityIcon,
				prayerColumn ? prayerPercent : -1, prayColor, energyColumn ? energy : -1, energyColor);
			return;
		}

		List<BigStat> stats = new ArrayList<>();
		if (config.showHitpoints())
		{
			stats.add(new BigStat(String.valueOf(hitpoints), hpColor, hpPercent, hpColor));
		}
		if (config.showPrayer())
		{
			stats.add(new BigStat(String.valueOf(prayer), prayColor, -1,
				tintMode.tintsValues() ? tint : Color.BLACK));
		}
		if (config.showEnergy())
		{
			stats.add(new BigStat(String.valueOf(energy), energyColor, energy, energyColor));
		}
		if (config.showSpec())
		{
			stats.add(new BigStat(String.valueOf(spec), specColor, spec, specColor));
		}

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
		awtrixClient.pushBigStat("", current.value, current.color, current.progress, current.progressColor, afkBackground, activityIcon);
	}

	private void pushActivityLabel(SkillActivity activity, Color background)
	{
		List<AwtrixClient.TextFragment> label = new ArrayList<>();
		label.add(new AwtrixClient.TextFragment(activity.label(), activity.color()));
		awtrixClient.pushCompactStats(label, -1, activity.color(), background, activity.iconData());
	}

	private SkillActivity currentActivity()
	{
		if (!config.showActivity() || client.getLocalPlayer() == null)
		{
			activityTracker.reset();
			return null;
		}
		int animation = client.getLocalPlayer().getAnimation();
		return activityTracker.onAnimation(SkillActivities.fromAnimation(animation), animation, System.currentTimeMillis());
	}

	/**
	 * Standing still with no action animation. Walk, run, and any chop/attack/skilling
	 * animation are not idle. Idle turn poses still count as standing.
	 */
	static boolean isStandingIdle(int actionAnimation, int poseAnimation, int idlePose, int idleRotateLeft, int idleRotateRight)
	{
		if (actionAnimation != -1)
		{
			return false;
		}
		return poseAnimation == -1
			|| poseAnimation == idlePose
			|| poseAnimation == idleRotateLeft
			|| poseAnimation == idleRotateRight;
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
		boolean characterIdle = client.getLocalPlayer() == null
			|| isStandingIdle(
				client.getLocalPlayer().getAnimation(),
				client.getLocalPlayer().getPoseAnimation(),
				client.getLocalPlayer().getIdlePoseAnimation(),
				client.getLocalPlayer().getIdleRotateLeft(),
				client.getLocalPlayer().getIdleRotateRight());
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
		activityTracker.reset();
		skillXp.clear();
	}

	private static final class BigStat
	{
		private final String value;
		private final Color color;
		private final int progress;
		private final Color progressColor;

		private BigStat(String value, Color color, int progress, Color progressColor)
		{
			this.value = value;
			this.color = color;
			this.progress = progress;
			this.progressColor = progressColor;
		}
	}
}
