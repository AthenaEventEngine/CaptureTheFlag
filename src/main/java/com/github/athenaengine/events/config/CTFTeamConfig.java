package com.github.athenaengine.events.config;

import com.github.athenaengine.core.model.config.TeamConfig;
import com.github.athenaengine.core.model.holder.LocationHolder;
import com.google.gson.annotations.SerializedName;

public class CTFTeamConfig extends TeamConfig {

    @SerializedName("flagLoc") private LocationHolder mFlagLoc;
    @SerializedName("holderLoc") private LocationHolder mHolderLoc;

    public LocationHolder getFlagLoc() {
        return mFlagLoc;
    }

    public LocationHolder getHolderLoc() {
        return mHolderLoc;
    }
}
