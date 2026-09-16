package com.valkyrie.client.module;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * Фильтр существ по типу — общий блок настроек для модулей, работающих с целями.
 * <p>
 * Фильтр принадлежит модулю, а не клиенту целиком: ESP по игрокам и трейсеры по
 * мобам — рабочий сценарий, а один общий список сделал бы его невозможным.
 */
public final class TargetFilter {
    private final Setting.Bool players;
    private final Setting.Bool hostiles;
    private final Setting.Bool passives;

    private TargetFilter(Setting.Bool players, Setting.Bool hostiles, Setting.Bool passives) {
        this.players = players;
        this.hostiles = hostiles;
        this.passives = passives;
    }

    /** Добавляет три тумблера в конец настроек модуля. */
    public static TargetFilter attach(Module module, boolean players, boolean hostiles, boolean passives) {
        return new TargetFilter(
            module.add(new Setting.Bool("players", "Players", "other players", players)),
            module.add(new Setting.Bool("hostiles", "Hostiles", "monsters", hostiles)),
            module.add(new Setting.Bool("passives", "Passives", "animals", passives))
        );
    }

    public boolean matches(LivingEntity living) {
        if (living instanceof Player) {
            return players.value();
        }
        if (living instanceof Enemy) {
            return hostiles.value();
        }
        return passives.value();
    }

    public boolean players() {
        return players.value();
    }

    public boolean hostiles() {
        return hostiles.value();
    }

    public boolean passives() {
        return passives.value();
    }
}
