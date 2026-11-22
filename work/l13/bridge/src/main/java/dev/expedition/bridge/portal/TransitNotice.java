package dev.expedition.bridge.portal;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.Map;
import java.util.WeakHashMap;

/** Transient feedback only: no chat history spam, saved player data or retained disconnected players. */
final class TransitNotice {
    private static final Map<ServerPlayer,Gate> GATES=new WeakHashMap<>();
    private TransitNotice(){}
    static void show(ServerPlayer player,String message) {
        if(GATES.computeIfAbsent(player,p->new Gate()).allow(message,player.server.overworld().getGameTime()))
            player.displayClientMessage(Component.literal(message),true);
    }
    static void waiting(ServerPlayer player) {show(player,"传送尚未结束，请稍候；若仍停在门内，退开一步再穿门即可。");}
    static final class Gate {
        private String last;
        private long shownAt;
        boolean allow(String message,long now) {
            if(message.equals(last) && now>=shownAt && now-shownAt<40)return false;
            last=message;shownAt=now;return true;
        }
    }
}
