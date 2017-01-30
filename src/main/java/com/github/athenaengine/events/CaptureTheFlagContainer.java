package com.github.athenaengine.events;

import com.github.athenaengine.core.interfaces.IEventConfig;
import com.github.athenaengine.core.model.base.BaseEvent;
import com.github.athenaengine.core.model.base.BaseEventContainer;
import com.github.athenaengine.events.config.CTFEventConfig;

public class CaptureTheFlagContainer extends BaseEventContainer {

    @Override
    protected Class<? extends IEventConfig> getConfigClass() {
        return CTFEventConfig.class;
    }

    public Class<? extends BaseEvent> getEventClass() {
        return CaptureTheFlag.class;
    }

    public String getEventName() {
        return "Capture the flag";
    }

    public String getDescription() {
        return "Two teams fight to steal the flag of the other team";
    }
}
