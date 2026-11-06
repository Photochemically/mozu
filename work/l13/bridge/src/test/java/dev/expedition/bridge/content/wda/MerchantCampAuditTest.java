package dev.expedition.bridge.content.wda;

import com.google.gson.*;
import dev.expedition.bridge.diagnostics.AcceptanceFixtures;
import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class MerchantCampAuditTest {
    private static final Path JAR = Path.of("libs/DungeonsArise-1.20.x-2.1.58-release.jar");
    private static final String HASH = "f8f1254dbe7ed7c1d5403f20361a36f0d0587c82b71313e3f427c7f1ce7c8a90";
    private static final String POOLS = "data/dungeons_arise/worldgen/template_pool/campsites/merchant_campsite/";
    private static final String STREET = "street_4_main";
    private static final int Y = 96;
    private static final WdaStructurePlanner.Environment ENV = new WdaStructurePlanner.Environment(Y,8,6,16,IslandPlan.Surface.GRASS);
    private record Block(BlockPos pos,String id,CompoundTag props,CompoundTag data) {}
    private record Template(String id,BlockPos size,List<Block> blocks,ListTag entities) {
        boolean projected() { return id.startsWith("street_"); }
        Block joint(String target,Direction facing) {
            return blocks.stream().filter(b->b.data.getString("target").equals("dungeons_arise:"+target)
                    && b.props.getString("orientation").startsWith(facing.getName()+"_")).findFirst().orElseThrow();
        }
    }
    private static BlockPos vector(ListTag v) { return new BlockPos(v.getInt(0),v.getInt(1),v.getInt(2)); }
    private static Cell cell(BlockPos p) { return new Cell(p.getX(),p.getY(),p.getZ()); }
    private static Map<String,Template> templates() throws IOException {
        var result=new HashMap<String,Template>();
        try (var zip=new ZipFile(JAR.toFile())) {
            for (var e:Collections.list(zip.entries())) {
                String prefix="data/dungeons_arise/structures/merchant_campsite/merchant_campsite_";
                if (!e.getName().startsWith(prefix) || !e.getName().endsWith(".nbt")) continue;
                try (var in=zip.getInputStream(e)) {
                    var n=NbtIo.readCompressed(in);
                    var palette=n.getList("palette",Tag.TAG_COMPOUND);
                    var blocks=new ArrayList<Block>();
                    for (var raw:n.getList("blocks",Tag.TAG_COMPOUND)) {
                        var b=(CompoundTag)raw;
                        var s=palette.getCompound(b.getInt("state"));
                        blocks.add(new Block(vector(b.getList("pos",Tag.TAG_INT)),s.getString("Name"),s.getCompound("Properties"),b.getCompound("nbt")));
                    }
                    var id=e.getName().substring(prefix.length(),e.getName().length()-4);
                    result.put(id,new Template(id,vector(n.getList("size",Tag.TAG_INT)),blocks,n.getList("entities",Tag.TAG_COMPOUND)));
                }
            }
        }
        return result;
    }

    private record Placed(Template template,BlockPos origin,Rotation rotation) {
        BlockPos at(BlockPos local) { return local.rotate(rotation).offset(origin); }
        BlockBounds bounds() {
            var end=at(template.size.offset(-1,-1,-1));
            return new BlockBounds(Math.min(origin.getX(),end.getX()),origin.getY(),Math.min(origin.getZ(),end.getZ()),
                    Math.max(origin.getX(),end.getX()),end.getY(),Math.max(origin.getZ(),end.getZ()));
        }
        WdaTemplatePiece piece() { return new WdaTemplatePiece(MerchantCampLayout.PREFIX+template.id,origin,rotation,bounds(),template.projected()); }
    }

    private static Placed connect(Placed parent,Block socket,Template child,Direction inletFacing) {
        var target=socket.data.getString("target");
        var inlet=child.blocks.stream().filter(b->b.data.getString("name").equals(target)
                && b.props.getString("orientation").startsWith(inletFacing.getName()+"_")).findFirst().orElseThrow();
        assertEquals(socket.data.getString("pool"),inlet.data.getString("pool"));
        var facing=parent.rotation.rotate(Direction.byName(socket.props.getString("orientation").split("_")[0]));
        Rotation rot=parent.rotation;
        if (facing.getAxis().isHorizontal()) rot=Arrays.stream(Rotation.values())
                .filter(r->r.rotate(inletFacing)==facing.getOpposite()).findFirst().orElseThrow();
        var origin=parent.at(socket.pos).relative(facing).subtract(inlet.pos.rotate(rot));
        // Native Placer uses the first free height minus child connector Y if either end is terrain-matching.
        if (parent.template.projected() || child.projected()) origin=new BlockPos(origin.getX(),Y-inlet.pos.getY(),origin.getZ());
        return new Placed(child,origin,rot);
    }
    private static List<Placed> assembly(Map<String,Template> t,int street,int tent,Rotation rot) {
        var root=new Placed(t.get(STREET),new BlockPos(-75,Y-1,219),rot);
        var road=connect(root,root.template.joint("campsite_street",Direction.EAST),t.get("street_"+street),Direction.WEST);
        var pieces=new ArrayList<>(List.of(root,road));
        var house=t.get("tent_"+tent);
        var home=connect(root,root.template.joint("campsite_tent",Direction.SOUTH),house,tent==3?Direction.SOUTH:Direction.NORTH);
        pieces.add(home);
        pieces.add(connect(home,house.joint("villager",Direction.UP),t.get("villager_normal_0"),Direction.DOWN));
        if (street==0 || street==2) {
            int second=(tent+1)%4;
            var other=connect(road,road.template.joint("campsite_tent",Direction.SOUTH),t.get("tent_"+second),second==3?Direction.SOUTH:Direction.NORTH);
            pieces.add(other);
        }
        if (street==0) pieces.add(connect(road,road.template.joint("iron_golem",Direction.UP),t.get("iron_golem_normal_0"),Direction.DOWN));
        return pieces;
    }

    private record State(String id,CompoundTag props,Direction facing,CompoundTag data) {}
    private static class Model implements MerchantCampRoute.Probe {
        final Map<Cell,State> placed=new HashMap<>();
        final Set<Cell> blocked=new HashSet<>();
        final MerchantCampLayout camp;
        final IslandPlan island;
        Model(List<Placed> pieces) {
            camp=MerchantCampLayout.inspect(pieces.stream().map(Placed::piece).toList(),ENV);
            var bounds=pieces.stream().map(Placed::bounds).reduce(IslandPlan::union).orElseThrow();
            for (var p:pieces) {
                if (p.template.projected()) bounds=IslandPlan.union(bounds,p.bounds().move(0,Y-1-p.origin.getY(),0));
                for (var b:p.template.blocks) {
                    var id=b.id;
                    if (id.equals("minecraft:jigsaw")) {
                        id=b.data.getString("final_state");
                        if (id.equals("minecraft:structure_void")) continue;
                    }
                    var pos=p.at(b.pos);
                    if (p.template.projected()) pos=new BlockPos(pos.getX(),Y-1+b.pos.getY(),pos.getZ());
                    var face=Direction.byName(b.props.getString("facing"));
                    placed.put(cell(pos),new State(id,b.props,face==null?null:p.rotation.rotate(face),
                            b.id.equals("minecraft:jigsaw")?new CompoundTag():b.data));
                }
            }
            island=new IslandPlan(bounds,Y,8,6,16,IslandPlan.Surface.GRASS);
        }
        public Type at(Cell p) {
            if (blocked.contains(p)) return Type.BLOCKED;
            var s=placed.get(p);
            if (s==null) return p.y()<Y?Type.SUPPORT:Type.AIR;
            return InteriorBlockRules.classify(s.id,s.props.getString("type"),s.props.getString("half"),
                    s.props.getString("waterlogged").equals("true"),!s.data.isEmpty());
        }
        public boolean spruceDoor(Cell p,Direction facing) {
            var top=p.move(0,1,0);
            if(blocked.contains(p)||blocked.contains(top)) return false;
            var low=placed.get(p); var high=placed.get(top);
            return low!=null && high!=null && low.id.equals("minecraft:spruce_door") && high.id.equals(low.id)
                    && low.facing==facing && high.facing==facing && low.props.getString("half").equals("lower")
                    && high.props.getString("half").equals("upper") && low.props.getString("hinge").equals(high.props.getString("hinge"));
        }
        public boolean chest(Cell p) {
            return !blocked.contains(p) && placed.containsKey(p) && placed.get(p).id.equals("minecraft:chest");
        }
        void route(int count) {
            var route=new MerchantCampRoute(camp,island,count,this);
            for(int i=0;i<20_000;i++) if(route.step())return;
            fail("Survey exceeded its island footprint");
        }
    }

    @Test void combinationsReachMapChestAndEveryOriginalTent() throws Exception {
        var t=templates();
        var cases=new ArrayList<Map<String,Object>>();
        for(var rot:Rotation.values()) for(int road=0;road<4;road++) for(int tent=0;tent<4;tent++) {
            var pieces=assembly(t,road,tent,rot);
            var m=new Model(pieces);
            for(int n=1;n<=4;n++)m.route(n);
            assertEquals("dungeons_arise:chests/merchant_campsite/merchant_campsite_map",m.placed.get(m.camp.chest()).data.getString("LootTable"));
            long expected=pieces.stream().filter(p->p.template.id.startsWith("tent_")&&!p.template.id.equals("tent_2")).count();
            assertEquals(expected,m.placed.values().stream().filter(s->s.data.getString("LootTable").endsWith("/merchant_campsite_tent")).count());
            var row=new LinkedHashMap<String,Object>();
            row.put("rotation",rot.toString()); row.put("pieces",pieces.stream().map(p->p.template.id).toList());
            row.put("tentsChecked",m.camp.tents().size()); row.put("exitCounts",List.of(1,2,3,4));
            cases.add(row);
        }
        var report=new LinkedHashMap<String,Object>();
        report.put("wdaSha256",HASH); report.put("cases",cases); report.put("runtimeAccepted",false);
        report.put("scope","Original projected streets, rigid tents and encounter records. Selected compatible assemblies on flat ground; native random frequency, door interaction, trading, loot and multiplayer await game acceptance.");
        var f=Path.of("build/reports/merchant-camp-entry-audit.json"); Files.createDirectories(f.getParent());
        Files.writeString(f,new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
    }

    @Test void refusesObstructedSecondaryTentDamagedDoorAndIsolatedMapChest() throws Exception {
        var m=new Model(assembly(templates(),0,0,Rotation.CLOCKWISE_90));
        assertEquals(2,m.camp.tents().size());
        m.blocked.add(m.camp.tents().get(1).at(5));
        assertThrows(IllegalStateException.class,()->m.route(1));
        m.blocked.clear(); m.blocked.add(m.camp.tents().get(0).at(1).move(0,1,0));
        assertThrows(IllegalStateException.class,()->m.route(1));
        m.blocked.clear(); m.blocked.add(m.camp.chestApproaches().get(0)); m.route(2);
        m.blocked.addAll(m.camp.chestApproaches()); assertThrows(IllegalStateException.class,()->m.route(1));
        m.blocked.clear(); m.blocked.add(m.camp.chest().move(0,1,0)); assertThrows(IllegalStateException.class,()->m.route(1));
    }

    @Test void rejectsEmptyCampOutOfPoolTentAndMisalignedGroundAndMovesEveryLandmark() throws Exception {
        var pieces=assembly(templates(),0,3,Rotation.COUNTERCLOCKWISE_90).stream().map(Placed::piece).toList();
        var root=pieces.get(0); var house=pieces.get(2);
        assertThrows(IllegalArgumentException.class,()->MerchantCampLayout.inspect(List.of(root),ENV));
        for(var bad:List.of(new WdaTemplatePiece(MerchantCampLayout.PREFIX+"tent_4",house.origin(),house.rotation(),house.bounds(),false),
                new WdaTemplatePiece(house.id(),house.origin().above(),house.rotation(),house.bounds().move(0,1,0),false),
                new WdaTemplatePiece(house.id(),house.origin(),house.rotation(),house.bounds(),true)))
            assertThrows(IllegalArgumentException.class,()->MerchantCampLayout.inspect(List.of(root,bad),ENV));
        var camp=MerchantCampLayout.inspect(pieces,ENV); var moved=camp.move(317,-18,-229);
        assertEquals(camp.chest().move(317,-18,-229),moved.chest());
        assertEquals(camp.chestApproaches().stream().map(c->c.move(317,-18,-229)).toList(),moved.chestApproaches());
        assertEquals(camp.tents().stream().map(h->h.approach().move(317,-18,-229)).toList(),moved.tents().stream().map(MerchantCampLayout.Tent::approach).toList());
    }

    @Test void originalPoolsAndExistingOakFixtureMatchTheSurvey() throws Exception {
        assertEquals(HASH,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(JAR))));
        var fixture=AcceptanceFixtures.all().stream().filter(f->f.id().equals("oak_camp")).findFirst().orElseThrow().choice().content();
        assertEquals(MerchantCampLayout.TARGET,fixture.target()); assertEquals(1,fixture.exits());
        assertEquals(Y,fixture.environment().firstFreeY()); assertEquals(16,fixture.environment().foundationDepth());
        try(var zip=new ZipFile(JAR.toFile())) {
            var def=json(zip,"data/dungeons_arise/worldgen/structure/merchant_campsite.json");
            assertEquals("minecraft:jigsaw",def.get("type").getAsString()); assertEquals("WORLD_SURFACE_WG",def.get("project_start_to_heightmap").getAsString());
            assertEquals(0,def.getAsJsonObject("start_height").get("absolute").getAsInt());
            assertEquals("dungeons_arise:campsites/merchant_campsite/merchant_campsite_start",def.get("start_pool").getAsString());
            var pools=Map.of("merchant_campsite_start",Set.of(STREET),"merchant_campsite_streets",Set.of("street_0","street_1","street_2","street_3"),
                    "merchant_campsite_tents",Set.of("tent_0","tent_1","tent_2","tent_3"),"villagers",Set.of("villager_normal_0","iron_golem_normal_0"));
            for(var entry:pools.entrySet()) {
                var ids=new HashSet<String>();
                for(var raw:json(zip,POOLS+entry.getKey()+".json").getAsJsonArray("elements")) {
                    var e=raw.getAsJsonObject().getAsJsonObject("element");
                    var id=e.get("location").getAsString();
                    assertTrue(id.startsWith(MerchantCampLayout.PREFIX));
                    var name=id.substring(MerchantCampLayout.PREFIX.length()); ids.add(name);
                    assertEquals(name.startsWith("street_")?"terrain_matching":"rigid",e.get("projection").getAsString());
                    assertEquals("minecraft:empty",e.get("processors").getAsString());
                    assertEquals("minecraft:single_pool_element",e.get("element_type").getAsString());
                }
                assertEquals(entry.getValue(),ids);
            }
        }
        var parts=templates();
        assertTrue(parts.containsKey("tent_4")); assertTrue(parts.get("iron_golem_normal_0").entities.isEmpty());
        assertEquals("minecraft:villager",parts.get("villager_normal_0").entities.getCompound(0).getCompound("nbt").getString("id"));
    }
    private static JsonObject json(ZipFile zip,String key) throws IOException {
        try(var in=zip.getInputStream(zip.getEntry(key))) {return JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();}
    }
}
