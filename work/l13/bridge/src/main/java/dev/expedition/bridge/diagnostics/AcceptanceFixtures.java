package dev.expedition.bridge.diagnostics;

import dev.expedition.bridge.domain.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Engineering cases, NOT the production catalog or approved difficulty assignments. */
public final class AcceptanceFixtures {
    public static final long DEFAULT_SEED=20261104L;
    public record Fixture(String id,NaturalEntryPlan.Type door,DungeonCatalog.Choice choice,int minimumPocketHeight,String purpose) {
        public PortalRecord record(PortalRecord.Site entrance,long seed) {
            if(!entrance.dimension().equals("minecraft:overworld"))throw new IllegalArgumentException("Test entrance must be in overworld");
            var key="expedition-acceptance-v1|"+id+"|"+entrance.key()+"|"+seed;
            var record=new PortalRecord(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)),door.kind,door.tier,entrance,seed);
            if(choice!=null)record.selectContent(choice);
            return record;
        }
    }
    private static final List<Fixture> ALL=List.of(
            nativeRoom("cherry_native",NaturalEntryPlan.Type.CHERRY,1,"dungeon/cage_monolithless","小房间、原生宝箱与重入"),
            wda("oak_camp",NaturalEntryPlan.Type.OAK,"merchant_campsite",1,256,96,16,IslandPlan.Surface.GRASS,"小型拼图、实体与原战利品"),
            nativeRoom("dark_oak_exits",NaturalEntryPlan.Type.DARK_OAK,3,"dungeon/chain_t","原生三裂隙统一返回"),
            wda("warped_windmill",NaturalEntryPlan.Type.WARPED,"illager_windmill",2,256,96,24,IslandPlan.Surface.GRASS,"多片段建筑、草地岛屿与双出口"),
            wda("crimson_keep",NaturalEntryPlan.Type.CRIMSON,"keep_kayra",4,512,65,32,IslandPlan.Surface.ROCK,"超高完整建筑、岩岛与首次生成负载"),
            new Fixture("gold_reward",NaturalEntryPlan.Type.GOLD,null,256,"专用原生奖励房、多人离开与关闭"),
            nativeRoom("bamboo_unstable",NaturalEntryPlan.Type.UNSTABLE,3,"dungeon/chain_t","竹门外观、固定测试等级III与多人关闭"));
    private AcceptanceFixtures(){}
    public static List<Fixture> all(){return ALL;}
    private static Fixture nativeRoom(String id,NaturalEntryPlan.Type type,int tier,String target,String purpose) {
        var spec=new DungeonSpec("expedition_bridge:acceptance/"+id,DungeonSpec.Provider.NATIVE,"dimdoors:"+target,Set.of(tier),1,null,0);
        return new Fixture(id,type,new DungeonCatalog.Choice(tier,spec),256,purpose);
    }
    private static Fixture wda(String id,NaturalEntryPlan.Type type,String target,int exits,int height,int firstFreeY,int depth,IslandPlan.Surface surface,String purpose) {
        var spec=new DungeonSpec("expedition_bridge:acceptance/"+id,DungeonSpec.Provider.WDA,"dungeons_arise:"+target,Set.of(type.tier),1,
                new DungeonSpec.Environment(firstFreeY,8,6,depth,surface),exits);
        return new Fixture(id,type,new DungeonCatalog.Choice(type.tier,spec),height,purpose);
    }
}
