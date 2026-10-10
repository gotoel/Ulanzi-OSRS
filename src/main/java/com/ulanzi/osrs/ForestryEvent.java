package com.ulanzi.osrs;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;

/**
 * The forestry events that can start around a tree you are chopping.
 *
 * Each one is recognised by the things it puts in the scene. Every event has NPCs or
 * objects that exist nowhere else, so a spawn is the event starting rather than
 * something that has to be inferred from a chat line or a timer.
 *
 * The label is what the panel says, so it is kept to the eight small-font characters
 * that fit across 32 pixels. The colour is what the animation is drawn in, which is
 * what tells the events apart from across a room, before any of it has been read.
 */
enum ForestryEvent
{
	RISING_ROOTS("ROOTS", 0x4F_E0_8A,
		new int[] {},
		new int[] {
			ObjectID.GATHERING_EVENT_RISING_ROOTS,
			ObjectID.GATHERING_EVENT_RISING_ROOTS_SPECIAL
		}),
	STRUGGLING_SAPLING("SAPLING", 0x8B_C3_4A,
		new int[] {
			NpcID.GATHERING_EVENT_SAPLING_NPC_HPBAR_1X1,
			NpcID.GATHERING_EVENT_SAPLING_NPC_HPBAR_2X2
		},
		new int[] {}),
	FLOWERING_TREE("FLOWERS", 0xFF_7F_BF,
		new int[] {
			NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL01,
			NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL02,
			NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL03,
			NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL04,
			NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL05,
			NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL06,
			NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL07,
			NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL08,
			NpcID.GATHERING_EVENT_FLOWERING_TREE_BEES
		},
		new int[] {}),
	POACHERS("POACHERS", 0xE0_53_3A,
		new int[] {
			NpcID.GATHERING_EVENT_POACHERS_TRAP,
			NpcID.GATHERING_EVENT_POACHERS_POACHER01_OUTDOORS,
			NpcID.GATHERING_EVENT_POACHERS_POACHER02_OUTDOORS,
			NpcID.GATHERING_EVENT_POACHERS_POACHER03_OUTDOORS,
			NpcID.GATHERING_EVENT_POACHERS_POACHER01_INDOORS,
			NpcID.GATHERING_EVENT_POACHERS_POACHER02_INDOORS,
			NpcID.GATHERING_EVENT_POACHERS_POACHER03_INDOORS,
			NpcID.GATHERING_EVENT_POACHERS_FOX_OUTDOORS,
			NpcID.GATHERING_EVENT_POACHERS_FOX_INDOORS
		},
		new int[] {}),
	PHEASANT_CONTROL("PHEASANT", 0xD9_A4_41,
		new int[] {
			NpcID.GATHERING_EVENT_PHEASANT_FORESTER,
			NpcID.GATHERING_EVENT_PHEASANT
		},
		new int[] {
			ObjectID.GATHERING_EVENT_PHEASANT_NEST01,
			ObjectID.GATHERING_EVENT_PHEASANT_NEST02
		}),
	BEEHIVE("BEEHIVE", 0xFF_C1_07,
		new int[] {
			NpcID.GATHERING_EVENT_BEES_BEEKEEPER,
			NpcID.GATHERING_EVENT_BEES_BEEBOX_1,
			NpcID.GATHERING_EVENT_BEES_BEEBOX_2,
			NpcID.GATHERING_EVENT_BEES_BEEBOX_3,
			NpcID.GATHERING_EVENT_BEES_BEEBOX_4
		},
		new int[] {
			ObjectID.GATHERING_EVENT_BEES_BEEHIVE01
		}),
	ENCHANTMENT_RITUAL("RITUAL", 0xB0_7F_FF,
		new int[] {
			NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_DRYAD,
			NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_A_1,
			NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_A_2,
			NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_A_3,
			NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_A_4,
			NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_B_1,
			NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_B_2,
			NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_B_3,
			NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_B_4,
			NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_C_1,
			NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_C_2,
			NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_C_3,
			NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_C_4,
			NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_D_1,
			NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_D_2,
			NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_D_3,
			NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_D_4
		},
		new int[] {}),
	FRIENDLY_ENT("ENT", 0xA9_74_4F,
		new int[] {
			NpcID.GATHERING_EVENT_ENTLINGS_NPC_01,
			NpcID.GATHERING_EVENT_ENTLINGS_NPC_01_PRUNED,
			NpcID.GATHERING_EVENT_ENT_STUMP_01,
			NpcID.GATHERING_EVENT_ENT_STUMP_02
		},
		new int[] {}),
	LEPRECHAUN("RAINBOW", 0xFF_D5_4A,
		new int[] {
			NpcID.GATHERING_EVENT_WOODCUTTING_LEPRECHAUN
		},
		new int[] {
			ObjectID.GATHERING_EVENT_WOODCUTTING_LEPRECHAUN_RAINBOW,
			ObjectID.GATHERING_EVENT_WOODCUTTING_LEPRECHAUN_CLOVER
		});

	private static final Map<Integer, ForestryEvent> BY_NPC = new HashMap<>();
	private static final Map<Integer, ForestryEvent> BY_OBJECT = new HashMap<>();

	static
	{
		for (ForestryEvent event : values())
		{
			for (int npc : event.npcIds)
			{
				BY_NPC.put(npc, event);
			}
			for (int object : event.objectIds)
			{
				BY_OBJECT.put(object, event);
			}
		}
	}

	private final String label;
	private final Color color;
	private final int[] npcIds;
	private final int[] objectIds;

	ForestryEvent(String label, int rgb, int[] npcIds, int[] objectIds)
	{
		this.label = label;
		this.color = new Color(rgb);
		this.npcIds = npcIds;
		this.objectIds = objectIds;
	}

	String label()
	{
		return label;
	}

	Color color()
	{
		return color;
	}

	static ForestryEvent fromNpc(int npcId)
	{
		return BY_NPC.get(npcId);
	}

	static ForestryEvent fromObject(int objectId)
	{
		return BY_OBJECT.get(objectId);
	}
}
