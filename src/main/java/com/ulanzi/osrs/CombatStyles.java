package com.ulanzi.osrs;

import java.util.Locale;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.ParamID;
import net.runelite.api.StructComposition;
import net.runelite.api.VarPlayer;
import net.runelite.api.gameval.VarbitID;

/**
 * Maps the selected attack style to a melee, ranged, or magic icon.
 * Style names come from the same weapon structs the client uses.
 */
final class CombatStyles
{
	private CombatStyles()
	{
	}

	static SkillActivity current(Client client)
	{
		int weaponType = client.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY);
		int styleIndex = client.getVarpValue(VarPlayer.ATTACK_STYLE);
		int castingMode = client.getVarbitValue(VarbitID.AUTOCAST_DEFMODE);
		return activityFor(styleNames(client, weaponType), styleIndex, castingMode);
	}

	static SkillActivity activityFor(String[] styleNames, int styleIndex, int castingMode)
	{
		if (styleNames == null || styleNames.length == 0 || styleIndex < 0)
		{
			return null;
		}
		// Staff defensive autocast is the style after the spell slot.
		if (styleIndex == 4)
		{
			styleIndex += Math.max(0, castingMode);
		}
		if (styleIndex >= styleNames.length)
		{
			return null;
		}
		SkillActivity selected = activityForStyleName(styleNames[styleIndex]);
		if (selected == SkillActivity.MELEE && rangedAccurate(styleNames, styleNames[styleIndex]))
		{
			return SkillActivity.RANGED;
		}
		return selected;
	}

	/**
	 * Bows list Accurate next to Ranging and Longrange. Staves list Accurate next to Aggressive.
	 */
	private static boolean rangedAccurate(String[] styleNames, String selected)
	{
		if (selected == null || !"ACCURATE".equals(normalize(selected)))
		{
			return false;
		}
		boolean ranged = false;
		boolean otherMelee = false;
		for (String name : styleNames)
		{
			SkillActivity activity = activityForStyleName(name);
			if (activity == SkillActivity.RANGED)
			{
				ranged = true;
			}
			else if (activity == SkillActivity.MELEE && name != null && !"ACCURATE".equals(normalize(name)))
			{
				otherMelee = true;
			}
		}
		return ranged && !otherMelee;
	}

	private static String normalize(String name)
	{
		return name.toUpperCase(Locale.ENGLISH).replace(' ', '_');
	}

	static SkillActivity activityForStyleName(String name)
	{
		if (name == null || name.isEmpty())
		{
			return null;
		}
		switch (normalize(name))
		{
			case "RANGING":
			case "LONGRANGE":
				return SkillActivity.RANGED;
			case "CASTING":
			case "DEFENSIVE_CASTING":
				return SkillActivity.MAGIC;
			case "ACCURATE":
			case "AGGRESSIVE":
			case "DEFENSIVE":
			case "CONTROLLED":
				return SkillActivity.MELEE;
			default:
				return null;
		}
	}

	private static String[] styleNames(Client client, int weaponType)
	{
		EnumComposition categories = client.getEnum(EnumID.WEAPON_STYLES);
		if (categories == null)
		{
			return fallbackStyles(weaponType);
		}
		int styleEnumId = categories.getIntValue(weaponType);
		if (styleEnumId == -1)
		{
			return fallbackStyles(weaponType);
		}
		EnumComposition styles = client.getEnum(styleEnumId);
		if (styles == null || styles.getIntVals() == null)
		{
			return fallbackStyles(weaponType);
		}

		int[] structs = styles.getIntVals();
		String[] names = new String[structs.length];
		for (int i = 0; i < structs.length; i++)
		{
			StructComposition struct = client.getStructComposition(structs[i]);
			if (struct == null)
			{
				continue;
			}
			String attackStyleName = struct.getStringValue(ParamID.ATTACK_STYLE_NAME);
			if (attackStyleName == null || attackStyleName.equalsIgnoreCase("Other"))
			{
				continue;
			}
			if (i == 5 && attackStyleName.equalsIgnoreCase("Defensive"))
			{
				names[i] = "Defensive Casting";
				continue;
			}
			names[i] = attackStyleName;
		}
		return names;
	}

	private static String[] fallbackStyles(int weaponType)
	{
		if (weaponType == 22)
		{
			return new String[] {"Accurate", "Aggressive", null, "Defensive", "Casting", "Defensive Casting"};
		}
		if (weaponType == 30)
		{
			return new String[] {"Accurate", "Aggressive", "Aggressive", "Defensive"};
		}
		return new String[0];
	}
}
