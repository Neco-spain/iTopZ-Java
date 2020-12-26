/*
 * Copyright (c) 2021 iTopZ
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package itopz.com.model.entity;

import com.l2jhellas.gameserver.datatables.sql.ItemTable;
import com.l2jhellas.gameserver.model.L2World;
import com.l2jhellas.gameserver.model.actor.instance.L2PcInstance;
import com.l2jhellas.gameserver.model.actor.item.L2Item;
import com.l2jhellas.util.Rnd;
import itopz.com.gui.Gui;
import itopz.com.Configurations;
import itopz.com.model.GlobalResponse;
import itopz.com.util.URL;
import itopz.com.util.Utilities;

import java.util.*;
import java.util.logging.Logger;

/**
 * @Author Nightwolf
 * iToPz Discord: https://discord.gg/KkPms6B5aE
 * @Author Rationale
 * Base structure credits goes on Rationale Discord: Rationale#7773
 *
 * Vote Donation System
 * Script website: https://itopz.com/
 * Script version: 1.0
 * Pack Support: L2JHellas 562 https://app.assembla.com/spaces/l2hellas/subversion/source
 *
 * Personal Donate Panels: https://www.denart-designs.com/
 * Free Donate panel: https://itopz.com/
 */
public class ITOPZ_GLOBAL implements Runnable
{
	// logger
	private static final Logger _log = Logger.getLogger(ITOPZ_GLOBAL.class.getName());

	// global server vars
	private static int storedVotes, serverVotes, serverRank, serverNeededVotes, serverNextRank;
	private int responseCode;

	// ip array list
	private final List<String> fingerprint = new ArrayList<>();

	@Override
	public void run()
	{
		load();
	}

	/**
	 * Global reward main function
	 */
	public void load()
	{
		// set variables
		if (!getServerInfo())
		{
			// write on console
			Gui.getInstance().ConsoleWrite("ITOPZ Not responding maybe its the end of the world.");
			return;
		}

		// write console info from response
		Gui.getInstance().ConsoleWrite("Server Votes:" + serverVotes + " Rank:" + serverRank + " Next Rank(" + serverNextRank + ") need: " + serverNeededVotes + "votes.");
		Gui.getInstance().UpdateStats(serverVotes, serverRank, serverNextRank, serverNeededVotes);
		storedVotes = Utilities.selectGlobalVar("itopz", "votes");
		// check if default return value is -1 (failed)
		if (storedVotes == -1)
		{
			// re-set server votes
			Gui.getInstance().ConsoleWrite("ITOPZ recover votes.");
			// save votes
			Utilities.saveGlobalVar("itopz", "votes", serverVotes);
			return;
		}

		// check stored votes are lower than server votes
		if (storedVotes < serverVotes)
		{
			// write on console
			Gui.getInstance().ConsoleWrite("ITOPZ update database");
			// save votes
			Utilities.saveGlobalVar("itopz", "votes", storedVotes);
		}

		// monthly reset
		if (storedVotes > serverVotes)
		{
			// write on console
			Gui.getInstance().ConsoleWrite("ITOPZ monthly reset");
			// save votes
			Utilities.saveGlobalVar("itopz", "votes", serverVotes);
		}

		// announce current votes
		if (Configurations.ITOPZ_ANNOUNCE_STATISTICS)
			Utilities.announce("Server Votes:" + serverVotes + " Rank:" + serverRank + " Next Rank(" + serverNextRank + ") need:" + serverNeededVotes + "votes");

		// check for vote step reward
		if (serverVotes >= storedVotes + Configurations.ITOPZ_VOTE_STEP)
		{
			// reward all online players
			reward();
			// announce the reward
			Utilities.announce("Thanks for voting! Players rewarded!");
			// save votes
			Utilities.saveGlobalVar("itopz", "votes", serverVotes);
			// write on console
			Gui.getInstance().ConsoleWrite("Votes: Players rewarded!");
		}
		// announce next reward
		Utilities.announce("Next reward at " + (storedVotes + Configurations.ITOPZ_VOTE_STEP) + " votes!");
	}

	/**
	 * reward players
	 */
	private void reward()
	{
		// iterate through all players
		for (L2PcInstance player : L2World.getInstance().getAllPlayers().values())
		{
			// ignore null players
			if (player == null)
			{
				continue;
			}

			// set player signature key
			final String key = Objects.requireNonNull(player.getClient().getConnection().getInetAddress().getHostAddress(), player.getName());
			// if key exists ignore player
			if (fingerprint.contains(key))
			{
				continue;
			}
			// add the key on ignore list
			fingerprint.add(key);

			for (final Integer _itemId : Configurations.ITOPZ_GLOBAL_REWARDS.keySet())
			{
				// check if the item id exists
				final L2Item item = ItemTable.getInstance().getTemplate(_itemId);
				if (item == null)
				{
					_log.warning("Failed to find reward item.");
					continue;
				}
				final Long[] values = Configurations.ITOPZ_GLOBAL_REWARDS.get(_itemId).get(0);
				long min = values[0];
				long max = values[1];
				long chance = values[2];
				// set count of each item
				long _count = Rnd.get(min, max);
				// chance for each item
				if (Rnd.get(100) > chance || chance >= 100)
				{
					// reward item
					player.addItem("iTopZ", _itemId, (int) _count, player, true);
					// write info on console
					Gui.getInstance().ConsoleWrite("Vote: player " + player.getName() + " received x" + _count + " " + item.getItemName());
				}
			}
		}

		fingerprint.clear();
	}

	/**
	 * set server information vars
	 * if response is 200 return true
	 *
	 * @return boolean
	 */
	private boolean getServerInfo()
	{
		new Thread(() ->
		{
			// get response from itopz about this ip address
			GlobalResponse response = GlobalResponse.OPEN(URL.ITOPZ_GLOBAL_URL.toString()).connect();
			// set variables
			responseCode = response.getResponseCode();
			serverNeededVotes = response.getServerNeededVotes();
			serverNextRank = response.getServerNextRank();
			serverRank = response.getServerRank();
			serverVotes = response.getServerVotes();
		}).run();

		// check itopz response
		return responseCode == 200 && serverVotes != -2;
	}
}
