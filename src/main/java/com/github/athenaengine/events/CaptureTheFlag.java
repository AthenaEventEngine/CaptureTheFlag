package com.github.athenaengine.events;

/*
 * Copyright (C) 2015-2016 L2J EventEngine
 *
 * This file is part of L2J EventEngine.
 *
 * L2J EventEngine is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * L2J EventEngine is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

import com.github.athenaengine.core.builders.TeamsBuilder;
import com.github.athenaengine.core.config.BaseConfigLoader;
import com.github.athenaengine.core.datatables.MessageData;
import com.github.athenaengine.core.dispatcher.events.*;
import com.github.athenaengine.core.enums.*;
import com.github.athenaengine.core.helper.RewardHelper;
import com.github.athenaengine.core.helper.ScreenMessageHelper;
import com.github.athenaengine.core.managers.ItemInstanceManager;
import com.github.athenaengine.core.model.base.BaseEvent;
import com.github.athenaengine.core.model.entity.Character;
import com.github.athenaengine.core.model.entity.Npc;
import com.github.athenaengine.core.model.entity.Player;
import com.github.athenaengine.core.model.entity.Team;
import com.github.athenaengine.core.model.holder.LocationHolder;
import com.github.athenaengine.core.model.template.ItemTemplate;
import com.github.athenaengine.core.util.EventUtil;
import com.github.athenaengine.events.config.CTFEventConfig;
import com.github.athenaengine.events.config.CTFTeamConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CaptureTheFlag extends BaseEvent<CTFEventConfig> {
    // FlagItem
    private static final int FLAG_ITEM = 6718;
    // Time for resurrection
    private static final int TIME_RES_PLAYER = 10;
    // Radius spawn
    private final Map<Integer, TeamType> _flagSpawn = new ConcurrentHashMap<>();
    private final Map<Integer, TeamType> _holderSpawn = new ConcurrentHashMap<>();
    private final Map<Integer, TeamType> _flagHasPlayer = new ConcurrentHashMap<>();
    private final Map<String, LocationHolder> _flagsLoc = new HashMap<>();

    @Override
    protected String getInstanceFile() {
        return getConfig().getInstanceFile();
    }

    @Override
    protected TeamsBuilder onCreateTeams() {
        return new TeamsBuilder()
                .addTeams(getConfig().getTeams())
                .setPlayers(getPlayerEventManager().getAllEventPlayers());
    }

    @Override
    protected void onEventStart() {
        addSuscription(ListenerType.ON_INTERACT);
        addSuscription(ListenerType.ON_KILL);
        addSuscription(ListenerType.ON_DEATH);
        addSuscription(ListenerType.ON_USE_ITEM);
        addSuscription(ListenerType.ON_LOG_OUT);

        spawnFlagsAndHolders();
        for (Player ph : getPlayerEventManager().getAllEventPlayers()) updateTitle(ph);
    }

    @Override
    protected void onEventFight() {
        // Nothing
    }

    @Override
    protected void onEventEnd() {
        clearFlags();
        giveRewardsTeams();
    }

    @Override
    public void onInteract(OnInteractEvent event)
    {
        Player ph = getPlayerEventManager().getEventPlayer(event.getPlayer());
        Npc npc = event.getNpc();

        if (npc.getTemplateId() == getConfig().getFlagNpcId()) {
            if (hasFlag(ph)) return;

            TeamType flagTeam = _flagSpawn.get(npc.getObjectId());
            if (ph.getTeamType() != flagTeam) {
                // Animation
                ph.castSkill(ph, 1034, 1, 1, 1);
                // Delete the flag from the map
                _flagSpawn.remove(npc.getObjectId());
                // Save the player has the flag
                _flagHasPlayer.put(event.getPlayer().getObjectId(), flagTeam);
                // Remove the flag from its position
                getSpawnManager().removeNpc(npc);
                // Equip the flag
                equipFlag(ph, flagTeam);
                // Announce the flag was taken
                EventUtil.announceTo(MessageType.BATTLEFIELD, "ctf_captured_the_flag", "%holder%", ph.getTeam().getName(), CollectionTarget.ALL_PLAYERS_IN_EVENT);
            }
        } else if (npc.getTemplateId() == getConfig().getHolderNpcId()) {
            if (ph.getTeamType() == _holderSpawn.get(npc.getObjectId())) {
                if (hasFlag(ph)) {
                    // Animation Large FireWork
                    ph.castSkill(ph, 2025, 1, 1, 1);
                    // Increase the points
                    getTeamsManager().getPlayerTeam(ph).increasePoints(ScoreType.POINT, getConfig().getPointsConquerFlag());
                    // Remove the flag from player
                    unequiFlag(ph);
                    Team th = getTeamsManager().getTeam(_flagHasPlayer.remove(ph.getObjectId()));
                    // Spawn the flag again
                    LocationHolder flagLocation = _flagsLoc.get(th.getName());
                    _flagSpawn.put(getSpawnManager().addNpc(getConfig().getFlagNpcId(), flagLocation, th.getName(), false, getInstanceWorldManager().getAllInstances().get(0).getInstanceId()).getObjectId(), th.getTeamType());
                    // Announce the flag was taken
                    EventUtil.announceTo(MessageType.BATTLEFIELD, "ctf_conquered_the_flag", "%holder%", ph.getTeam().getName(), CollectionTarget.ALL_PLAYERS_IN_EVENT);
                    // Show team points
                    showPoint(ph.getTeam());
                }
            }
        }
    }

    @Override
    public void onKill(OnKillEvent event) {
        Player ph = getPlayerEventManager().getEventPlayer(event.getAttacker());
        Character target = event.getTarget();

        Player targetEvent = getPlayerEventManager().getEventPlayer(target);
        if (hasFlag(targetEvent)) {
            unequiFlag(targetEvent);
            dropFlag(targetEvent);
        }

        getTeamsManager().getPlayerTeam(ph).increasePoints(ScoreType.POINT, getConfig().getPointsKill());

        if (getConfig().isRewardKillEnabled()) ph.giveItems(getConfig().getRewardKill());

        if (getConfig().isRewardPvPKillEnabled()) {
            ph.setPvpKills(ph.getPvpKills() + getConfig().getRewardPvPKill());
            EventUtil.sendEventMessage(ph, MessageData.getInstance().getMsgByLang(ph, "reward_text_pvp", true).replace("%count%", getConfig().getRewardPvPKill() + ""));
        }

        if (getConfig().isRewardFameKillEnabled()) {
            ph.setFame(ph.getFame() + getConfig().getRewardFameKill());
            EventUtil.sendEventMessage(ph, MessageData.getInstance().getMsgByLang(ph, "reward_text_fame", true).replace("%count%", getConfig().getRewardFameKill() + ""));
        }

        if (BaseConfigLoader.getInstance().getMainConfig().isKillerMessageEnabled()) EventUtil.messageKill(ph, target);
        showPoint(ph.getTeam());
    }

    @Override
    public void onDeath(OnDeathEvent event) {
        scheduleRevivePlayer(event.getTarget(), TIME_RES_PLAYER);
    }

    @Override
    public void onUseItem(OnUseItemEvent event) {
        Player ph = event.getPlayer();
        ItemTemplate item = event.getItem();

        if (item.getTemplateId() == FLAG_ITEM  || (hasFlag(ph) && item.isWeapong())) return;
        event.setCancel(true);
    }

    @Override
    public void onLogout(OnLogOutEvent event) {
        Player ph = event.getPlayer();

        if (hasFlag(ph)) {
            unequiFlag(ph);
            dropFlag(ph);
        }
    }

    // VARIOUS METHODS -------------------------------------------------
    /**
     * Spawn flags and holders.
     */
    private void spawnFlagsAndHolders()
    {
        int instanceId = getInstanceWorldManager().getAllInstances().get(0).getInstanceId();

        Map<String, LocationHolder> mapFlags = new HashMap<>();
        Map<String, LocationHolder> mapHolders = new HashMap<>();

        for (CTFTeamConfig config : getConfig().getTeams()) {
            mapFlags.put(config.getName(), config.getFlagLoc());
            mapHolders.put(config.getName(), config.getHolderLoc());
        }

        for (Team th : getTeamsManager().getAllTeams()) {
            if (mapFlags.containsKey(th.getName())) {
                LocationHolder flagLocation = mapFlags.get(th.getName());
                _flagsLoc.put(th.getName(), flagLocation);

                LocationHolder holderLocation = mapHolders.get(th.getName());
                _flagSpawn.put(getSpawnManager().addNpc(getConfig().getFlagNpcId(), flagLocation, th.getName(), false, instanceId).getObjectId(), th.getTeamType());
                _holderSpawn.put(getSpawnManager().addNpc(getConfig().getHolderNpcId(), holderLocation, th.getName(), false, instanceId).getObjectId(), th.getTeamType());
            }
        }
    }

    private void giveRewardsTeams() {
        if (getPlayerEventManager().getAllEventPlayers().isEmpty()) return;

        RewardHelper.newInstance()
                .setParticipants(getTeamsManager().getAllTeams())
                .setScoreType(ScoreType.POINT)
                .addReward(1, getConfig().getReward())
                .distribute(AnnounceType.WINNER);
    }

    private void showPoint(Team team) {
        ScreenMessageHelper.newInstance()
                .setTime(10000)
                .setMessage(" | %teamName% %points% | ")
                .replaceHolder("%teamName%", team.getName())
                .replaceHolder("%points%", String.valueOf(team.getPoints(ScoreType.POINT)))
                .show(getPlayerEventManager().getAllEventPlayers());
    }

    private boolean hasFlag(Player ph) {
        return _flagHasPlayer.containsKey(ph.getObjectId());
    }

    private void equipFlag(Player ph, TeamType flagTeam) {
        _flagHasPlayer.put(ph.getObjectId(), flagTeam);
        ph.equipItem(ItemInstanceManager.getInstance().createItem(FLAG_ITEM, ph));
    }

    private void unequiFlag(Player ph) {
        ph.unequipItem(InventoryItemType.PAPERDOLL_RHAND);
    }

    private void dropFlag(Player ph)
    {
        Team th = getTeamsManager().getTeam(_flagHasPlayer.remove(ph.getObjectId()));
        _flagSpawn.put(getSpawnManager().addNpc(getConfig().getFlagNpcId(), ph.getLocation(), th.getName(), false, ph.getWorldInstanceId()).getObjectId(), th.getTeamType());
        Map<String, String> map = new HashMap<>();
        // We announced that a flag was taken
        map.put("%holder%", ph.getName());
        map.put("%flag%", th.getName());
        EventUtil.announceTo(MessageType.BATTLEFIELD, "player_dropped_flag", map, CollectionTarget.ALL_PLAYERS_IN_EVENT);
    }

    private void clearFlags() {
        for (int playerId : _flagHasPlayer.keySet()) {
            unequiFlag(getPlayerEventManager().getEventPlayer(playerId));
        }
        _flagHasPlayer.clear();
    }

    private void updateTitle(Player ph) {
        ph.setTitle("[ " + ph.getTeam().getName() + " ]");
    }
}