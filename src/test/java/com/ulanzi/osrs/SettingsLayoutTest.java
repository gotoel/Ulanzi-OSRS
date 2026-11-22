package com.ulanzi.osrs;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import org.junit.Assert;
import org.junit.Test;

/** A new user should meet a handful of settings, with the rest one click away. */
public class SettingsLayoutTest
{
	private static final int MOST_ON_FIRST_SCREEN = 7;

	@Test
	public void onlyQuickSetupStartsOpen() throws Exception
	{
		for (Field field : UlanziConfig.class.getDeclaredFields())
		{
			ConfigSection section = field.getAnnotation(ConfigSection.class);
			if (section != null)
			{
				Assert.assertEquals(section.name(), !"quickSection".equals(field.getName()), section.closedByDefault());
			}
		}
	}

	@Test
	public void theFirstScreenStaysShortAndEverySettingHasASection() throws Exception
	{
		Set<String> sections = new HashSet<>();
		for (Field field : UlanziConfig.class.getDeclaredFields())
		{
			if (field.isAnnotationPresent(ConfigSection.class))
			{
				sections.add((String) field.get(null));
			}
		}
		int quick = 0;
		for (Method method : UlanziConfig.class.getDeclaredMethods())
		{
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			if (item == null || item.hidden())
			{
				continue;
			}
			Assert.assertTrue(item.keyName() + " has no section", sections.contains(item.section()));
			if (UlanziConfig.quickSection.equals(item.section()))
			{
				quick++;
			}
		}
		Assert.assertTrue("quick setup has " + quick, quick > 0 && quick <= MOST_ON_FIRST_SCREEN);
	}
}
