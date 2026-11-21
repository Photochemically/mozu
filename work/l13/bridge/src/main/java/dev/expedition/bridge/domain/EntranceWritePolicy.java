package dev.expedition.bridge.domain;

import java.util.Arrays;

/** Only the recorded entrance's two door halves may be installed or deliberately retired. */
public final class EntranceWritePolicy {
    private EntranceWritePolicy() {}
    public static boolean allows(PortalRecord portal,PortalRecord.Site site,String before,String after) {
        var entrance=portal.entrance();
        if(!site.dimension().equals(entrance.dimension()) || site.x()!=entrance.x() || site.z()!=entrance.z()
                || (site.y()!=entrance.y() && (long)site.y()!=(long)entrance.y()+1))return false;
        String block=Arrays.stream(NaturalEntryPlan.Type.values())
                .filter(t->t.kind==portal.kind() && t.tier==portal.tier()).findFirst().orElseThrow().block;
        boolean empty=before.equals("minecraft:air") || before.equals("minecraft:cave_air") || before.equals("minecraft:void_air");
        return portal.phase()==PortalRecord.Phase.NEW && empty && after.equals(block)
                || portal.phase()==PortalRecord.Phase.CLOSING && portal.kind()!=PortalKind.EXPLORATION
                && before.equals(block) && after.equals("minecraft:air");
    }
}
