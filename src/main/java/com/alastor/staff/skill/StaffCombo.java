package com.alastor.staff.skill;

import com.alastor.staff.item.AlastorStaffItem;
import com.alastor.staff.skill.task.ComboStrikeTask;
import com.alastor.staff.skill.task.GrabHurlTask;
import com.alastor.staff.skill.task.SkillTaskManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Basic attacks: a five-hit tentacle combo on left-click.
 * 1 right-to-left sweep · 2 left-to-right sweep · 3 overhead slam · 4 twin thrust · 5 broadcast burst.
 * Sneak + left-click rips up the ground in front and hurls it.
 */
public final class StaffCombo {
    private static final int[] GAP = {8, 8, 11, 10, 18};   // ticks before the next hit may start
    private static final int RESET = 34;
    private static final Map<UUID, int[]> STATE = new HashMap<>();   // {nextStep, readyAt, lastAt}
    private static final Map<UUID, Long> HURL_CD = new HashMap<>();

    public static void attack(Player p, boolean sneak) {
        if (!(p.level() instanceof ServerLevel level)) return;
        ItemStack staff = p.getMainHandItem();
        if (!(staff.getItem() instanceof AlastorStaffItem)) return;
        long now = level.getGameTime();

        if (sneak) {
            Long ready = HURL_CD.get(p.getUUID());
            if (ready != null && ready > now) return;
            HURL_CD.put(p.getUUID(), now + 30);
            AlastorStaffItem.markCast(staff, SkillType.CastAnim.HURL, now);
            SkillTaskManager.add(level, new GrabHurlTask(p));
            return;
        }

        int[] st = STATE.computeIfAbsent(p.getUUID(), k -> new int[]{0, 0, 0});
        int t = (int) (now & 0x7fffffff);
        if (t < st[1]) return;                       // still swinging
        if (t - st[2] > RESET) st[0] = 0;             // combo dropped
        int step = st[0];
        st[0] = (step + 1) % 5;
        st[1] = t + GAP[step];
        st[2] = t;
        int anim = switch (step) {
            case 0 -> SkillType.CastAnim.SWEEP_R;
            case 1 -> SkillType.CastAnim.SWEEP_L;
            case 2 -> SkillType.CastAnim.SLAM;
            case 3 -> SkillType.CastAnim.THRUST;
            default -> SkillType.CastAnim.BURST;
        };
        AlastorStaffItem.markCast(staff, anim, now);
        p.displayClientMessage(net.minecraft.network.chat.Component.literal("✦".repeat(step + 1))
                .withStyle(net.minecraft.ChatFormatting.DARK_GREEN), true);
        SkillTaskManager.add(level, new ComboStrikeTask(p, step));
    }

    private StaffCombo() {}
}
