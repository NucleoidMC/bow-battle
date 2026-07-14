package io.github.gibatron.bowbattle.game;

import net.minecraft.world.BossEvent;
import net.minecraft.network.chat.Component;
import xyz.nucleoid.plasmid.api.game.common.GlobalWidgets;
import xyz.nucleoid.plasmid.api.game.common.widget.BossBarWidget;

public final class BowBattleTimerBar {
    private final BossBarWidget widget;

    public BowBattleTimerBar(GlobalWidgets widgets) {
        Component title = Component.literal("Waiting for the game to start...");
        this.widget = widgets.addBossBar(title, BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.NOTCHED_10);
    }

    public void update(long ticksUntilEnd, long totalTicksUntilEnd) {
        if (ticksUntilEnd % 20 == 0) {
            this.widget.setTitle(this.getText(ticksUntilEnd));
            this.widget.setProgress((float) ticksUntilEnd / totalTicksUntilEnd);
        }
    }

    private Component getText(long ticksUntilEnd) {
        long secondsUntilEnd = ticksUntilEnd / 20;

        long minutes = secondsUntilEnd / 60;
        long seconds = secondsUntilEnd % 60;
        String time = String.format("%02d:%02d left", minutes, seconds);

        return Component.literal(time);
    }
}
