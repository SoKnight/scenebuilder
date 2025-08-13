package com.oracle.javafx.scenebuilder.app.logging;

import ch.qos.logback.core.rolling.TriggeringPolicyBase;

import java.io.File;

public final class RollOncePerSessionTriggeringPolicy<E> extends TriggeringPolicyBase<E> {

    private volatile boolean doRolling = true;

    @Override
    public boolean isTriggeringEvent(File activeFile, E event) {
        if (!doRolling)
            return false;

        if (activeFile.length() == 0L) {
            this.doRolling = false;
            return false;
        }

        this.doRolling = false;
        return true;
    }

}