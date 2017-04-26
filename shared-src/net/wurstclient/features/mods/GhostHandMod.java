/*
 * Copyright � 2014 - 2017 | Wurst-Imperium | All rights reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package net.wurstclient.features.mods;

import net.wurstclient.features.HelpPage;
import net.wurstclient.features.Mod;
import net.wurstclient.features.SearchTags;

@SearchTags({"ghost hand"})
@HelpPage("Mods/GhostHand")
@Mod.Bypasses(ghostMode = false)
public final class GhostHandMod extends Mod
{
	public GhostHandMod()
	{
		super("GhostHand",
			"Allows you to reach specific blocks through walls.\n"
				+ "Use .ghosthand id <block id> or .ghosthand name <block name>\n"
				+ "to specify it.");
	}
	
	@Override
	public String getRenderName()
	{
		return getName() + " [" + wurst.options.ghostHandID + "]";
	}
}
