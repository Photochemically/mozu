package dev.expedition.bridge.content.wda;

import com.google.gson.*;
import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class WindmillAuditTest {
    private static final Path JAR = Path.of("libs/DungeonsArise-1.20.x-2.1.58-release.jar");
    private static final String HASH = "f8f1254dbe7ed7c1d5403f20361a36f0d0587c82b71313e3f427c7f1ce7c8a90";
    private static final String POOL = "dungeons_arise:prairie/illager_windmill/illager_windmill_";
    private static final int SURFACE = 96;
    private static final WdaStructurePlanner.Environment ENV = new WdaStructurePlanner.Environment(SURFACE, 8, 6, 24, IslandPlan.Surface.GRASS);
    private record Block(BlockPos pos, String id, CompoundTag props, CompoundTag data) {}
    private record Part(String id, BlockPos size, List<Block> blocks, ListTag entities) {
        Block joint(String pool, String target, String front) {
            return blocks.stream().filter(b -> b.data.getString("pool").equals(POOL + pool)
                    && b.data.getString("target").equals("dungeons_arise:" + target)
                    && b.props.getString("orientation").startsWith(front + "_")).findFirst().orElseThrow();
        }
    }
    private static BlockPos pos(Tag raw) {
        var v = (ListTag) raw;
        return new BlockPos(v.getInt(0), v.getInt(1), v.getInt(2));
    }
    private static Map<String, Part> loadParts() throws IOException {
        var parts = new HashMap<String, Part>();
        try (var zip = new ZipFile(JAR.toFile())) {
            for (var id : List.of("part_0","part_1","field_0","field_1","field_2","field_3","field_4","pillager_0","vindicator_1","witch_2")) {
                var entry = zip.getEntry("data/dungeons_arise/structures/illager_windmill/illager_windmill_" + id + ".nbt");
                try (var in = zip.getInputStream(entry)) {
                    var n = NbtIo.readCompressed(in);
                    var palette = n.getList("palette", Tag.TAG_COMPOUND);
                    var blocks = new ArrayList<Block>();
                    for (var raw : n.getList("blocks", Tag.TAG_COMPOUND)) {
                        var b = (CompoundTag) raw;
                        var s = palette.getCompound(b.getInt("state"));
                        blocks.add(new Block(pos(b.get("pos")), s.getString("Name"), s.getCompound("Properties"), b.getCompound("nbt")));
                    }
                    parts.put(id, new Part(id, pos(n.get("size")), blocks, n.getList("entities", Tag.TAG_COMPOUND)));
                }
            }
        }
        return parts;
    }
    private static Cell cell(BlockPos p) { return new Cell(p.getX(), p.getY(), p.getZ()); }

    private record Placed(Part part, BlockPos origin, Rotation rotation) {
        BlockPos at(BlockPos local) { return local.rotate(rotation).offset(origin); }
        WdaTemplatePiece piece() {
            var end = at(part.size.offset(-1,-1,-1));
            var b = new BlockBounds(Math.min(origin.getX(),end.getX()), origin.getY(), Math.min(origin.getZ(),end.getZ()),
                    Math.max(origin.getX(),end.getX()), end.getY(), Math.max(origin.getZ(),end.getZ()));
            return new WdaTemplatePiece(WindmillLayout.PREFIX + part.id, origin, rotation, b, part.id.startsWith("field_"));
        }
    }
    private static Placed attach(Placed parent, Block socket, Part part, Block inlet, Rotation rotation) {
        assertEquals(socket.data.getString("target"), inlet.data.getString("name"));
        var front = parent.rotation.rotate(Direction.byName(socket.props.getString("orientation").split("_")[0]));
        var back = rotation.rotate(Direction.byName(inlet.props.getString("orientation").split("_")[0]));
        assertEquals(front.getOpposite(), back);
        assertEquals(socket.data.getString("pool"), inlet.data.getString("pool"));
        if (socket.data.getString("joint").equals("aligned")) {
            assertEquals(parent.rotation.rotate(Direction.byName(socket.props.getString("orientation").split("_")[1])),
                    rotation.rotate(Direction.byName(inlet.props.getString("orientation").split("_")[1])));
        }
        var start = parent.at(socket.pos).relative(front).subtract(inlet.pos.rotate(rotation));
        // Flat terrain-matching fields use GravityProcessor: heightmap - 1 + original local Y.
        if (part.id.startsWith("field_")) start = new BlockPos(start.getX(), SURFACE - 1, start.getZ());
        return new Placed(part, start, rotation);
    }

    private static List<Placed> assembly(Map<String,Part> parts, Rotation rot, int variant) {
        var base = parts.get("part_0");
        var root = new Placed(base, new BlockPos(-162, SURFACE - 3, 217), rot);
        var top = parts.get("part_1");
        var pieces = new ArrayList<Placed>();
        pieces.add(root);
        pieces.add(attach(root, base.joint("main","illager_windmill_0","up"), top, top.joint("main","illager_windmill_1","down"), rot));
        for (int side = 0; side < 2; side++) {
            if ((variant & (1 << side)) == 0) continue;
            var field = parts.get(side == 0 ? "field_0" : "field_4");
            var facing = side == 0 ? rot : rot.getRotated(Rotation.CLOCKWISE_180);
            var sidePort = new BlockPos(side == 0 ? 0 : 47, 9, 18);
            var socket = base.blocks.stream().filter(b -> b.pos.equals(sidePort)).findFirst().orElseThrow();
            var large = attach(root, socket, field, field.joint("fields","illager_windmill_field_1","east"), facing);
            pieces.add(large);
            var small = parts.get("field_" + variant);
            pieces.add(attach(large, field.joint("fields","illager_windmill_field_small","west"),
                    small, small.joint("fields","illager_windmill_field_large","east"), facing));
        }
        var mob = parts.get(List.of("pillager_0","vindicator_1","witch_2","pillager_0").get(variant));
        String target = mob.id.equals("witch_2") ? "windmill_witch" : "windmill_illager";
        pieces.add(attach(root, base.joint("spawners",target,"up"), mob, mob.joint("spawners",target,"down"), rot));
        return pieces;
    }

    private record State(Block b, Direction facing) {}
    private static class Model implements WindmillRoute.Probe {
        final Map<Cell, State> placed = new HashMap<>();
        final Set<Cell> blocked = new HashSet<>();
        final WindmillLayout mill;
        final IslandPlan island;
        Model(List<Placed> parts) {
            var pieces = parts.stream().map(Placed::piece).toList();
            WindmillLayout.validate(pieces, ENV);
            mill = new WindmillLayout(parts.get(0).origin, parts.get(0).rotation);
            var bounds = pieces.stream().map(WdaTemplatePiece::bounds).reduce(IslandPlan::union).orElseThrow();
            island = new IslandPlan(bounds,SURFACE,8,6,24,IslandPlan.Surface.GRASS);
            for (var p : parts) for (var b : p.part.blocks) {
                var where = cell(p.at(b.pos));
                if (b.id.equals("minecraft:structure_block")) continue;
                if (b.id.equals("minecraft:jigsaw")) {
                    var id = b.data.getString("final_state");
                    if (id.equals("minecraft:structure_void")) continue;
                    assertEquals("minecraft:air", id);
                    b = new Block(b.pos, id, new CompoundTag(), new CompoundTag());
                }
                var facing = Direction.byName(b.props.getString("facing"));
                placed.put(where, new State(b, facing == null ? null : p.rotation.rotate(facing)));
            }
        }
        public Type at(Cell p) {
            if (blocked.contains(p)) return Type.BLOCKED;
            var s = placed.get(p);
            if (s == null) return p.y() < SURFACE ? Type.SUPPORT : Type.AIR;
            var b = s.b;
            return InteriorBlockRules.classify(b.id,b.props.getString("type"),b.props.getString("half"),
                    b.props.getString("waterlogged").equals("true") || b.id.equals("minecraft:water"),!b.data.isEmpty());
        }
        public boolean originalStep(Cell p, Direction facing, boolean andesite) {
            var s = placed.get(p);
            return !blocked.contains(p) && s != null && s.b.id.equals(andesite ? "minecraft:andesite_stairs" : "minecraft:cobblestone_stairs")
                    && s.facing == facing && s.b.props.getString("half").equals("bottom")
                    && s.b.props.getString("waterlogged").equals("false") && s.b.props.getString("shape").equals("straight");
        }
        void route(int count) {
            var route = new WindmillRoute(mill, island, count, this);
            for (int i = 0; i < 100_000; i++) if (route.step()) return;
            fail("Flood fill did not finish within the island footprint");
        }
    }

    @Test void originalProjectedFieldsAndBothTowerHalvesReachRequestedExits() throws Exception {
        var parts = loadParts();
        var cases = new ArrayList<Map<String,Object>>();
        for (var rot : Rotation.values()) for (int variant=0;variant<4;variant++) {
            var pieces = assembly(parts,rot,variant);
            var model = new Model(pieces);
            for (int n=1;n<=4;n++) model.route(n);
            // Verify each side independently, so a regression cannot silently disable an entire staircase.
            for (int foot : List.of(5,43)) {
                var otherSide = model.mill.at(foot,3,18);
                model.blocked.add(otherSide);
                model.route(2);
                model.blocked.remove(otherSide);
            }
            long tnt = model.placed.values().stream().filter(s -> s.b.id.equals("minecraft:tnt")).count();
            assertEquals(63,tnt);
            assertEquals(14,model.placed.values().stream().filter(s -> s.b.data.contains("LootTable")).count());
            var row = new LinkedHashMap<String,Object>();
            row.put("rotation",rot.toString()); row.put("templates",pieces.stream().map(p -> p.part.id).toList());
            row.put("eachStairwayIndependentlyWithTwoExits",true);
            row.put("grassFirstFree",SURFACE); row.put("rootY",model.mill.origin().getY()); row.put("exitCounts",List.of(1,2,3,4));
            cases.add(row);
        }
        var result = new LinkedHashMap<String,Object>();
        result.put("wdaSha256",HASH); result.put("cases",cases); result.put("runtimeAccepted",false);
        result.put("scope","Real tower/field/mob template records and flat field projection; selected attachment combinations, no native random generation, live entities, fluid update, redstone or collision simulation.");
        var file = Path.of("build/reports/windmill-entry-audit.json");
        Files.createDirectories(file.getParent()); Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");
    }

    @Test void remainingStairwayWorksButDisconnectedOrBlockedExitsAreRejected() throws Exception {
        var m = new Model(assembly(loadParts(),Rotation.CLOCKWISE_90,3));
        m.blocked.add(m.mill.at(7,5,18));
        m.route(4);
        m.blocked.add(m.mill.at(41,6,18)); // head clearance above the other flight
        assertThrows(IllegalStateException.class,()->m.route(1));
        m.blocked.clear();
        var west = m.island.perimeterSites(2).get(1);
        m.blocked.add(west.move(0,1,0));
        assertThrows(IllegalStateException.class,()->m.route(2));
        m.blocked.clear();
        for (int z=m.island.coreMinZ();z<=m.island.coreMaxZ();z++) m.blocked.add(new Cell(west.x()+2,SURFACE,z));
        m.route(1);
        assertThrows(IllegalStateException.class,()->m.route(2));
    }

    @Test void refusesMissingTowerMisalignedTowerAndUnexpectedProjection() throws Exception {
        var good = assembly(loadParts(),Rotation.COUNTERCLOCKWISE_90,1).stream().map(Placed::piece).toList();
        var root = good.get(0); var top = good.get(1); var field = good.get(2);
        assertThrows(IllegalArgumentException.class,()->WindmillLayout.validate(List.of(root),ENV));
        var detached = new WdaTemplatePiece(top.id(), top.origin().above(),top.rotation(),top.bounds().move(0,1,0),false);
        assertThrows(IllegalArgumentException.class,()->WindmillLayout.validate(List.of(root,detached),ENV));
        var rigidField = new WdaTemplatePiece(field.id(),field.origin(),field.rotation(),field.bounds(),false);
        assertThrows(IllegalArgumentException.class,()->WindmillLayout.validate(List.of(root,top,rigidField),ENV));
        assertThrows(IllegalArgumentException.class,()->WindmillLayout.validate(good,new WdaStructurePlanner.Environment(97,8,6,24,IslandPlan.Surface.GRASS)));
        assertThrows(IllegalArgumentException.class,()->WindmillLayout.validate(good,new WdaStructurePlanner.Environment(96,8,6,24,IslandPlan.Surface.WATER,8)));
    }

    @Test void lockedResourceDefinesRigidTowerProjectedFieldsAndOriginalEncounters() throws Exception {
        assertEquals(HASH,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(JAR))));
        try (var jar = new ZipFile(JAR.toFile())) {
            var prefix = "data/dungeons_arise/worldgen/";
            var def = json(jar,prefix + "structure/illager_windmill.json");
            assertEquals("minecraft:jigsaw",def.get("type").getAsString());
            assertEquals("WORLD_SURFACE_WG",def.get("project_start_to_heightmap").getAsString());
            assertEquals(-2,def.getAsJsonObject("start_height").get("absolute").getAsInt());
            assertEquals(POOL+"start",def.get("start_pool").getAsString());
            for (var pool : List.of("start","main","fields","spawners")) {
                var data = json(jar,prefix+"template_pool/prairie/illager_windmill/illager_windmill_"+pool+".json");
                var ids = new HashSet<String>();
                for (var raw : data.getAsJsonArray("elements")) {
                    var e = raw.getAsJsonObject().getAsJsonObject("element");
                    assertEquals("minecraft:single_pool_element",e.get("element_type").getAsString());
                    assertEquals("minecraft:empty",e.get("processors").getAsString());
                    assertEquals(pool.equals("fields")?"terrain_matching":"rigid",e.get("projection").getAsString());
                    ids.add(e.get("location").getAsString().substring(WindmillLayout.PREFIX.length()));
                }
                assertEquals(switch(pool) {
                    case "start" -> Set.of("part_0"); case "main" -> Set.of("part_0","part_1");
                    case "fields" -> Set.of("field_0","field_1","field_2","field_3","field_4");
                    default -> Set.of("pillager_0","vindicator_1","witch_2");
                },ids);
            }
        }
        var parts = loadParts();
        for (var id : List.of("pillager_0","vindicator_1","witch_2"))
            assertEquals("minecraft:"+id.split("_")[0],parts.get(id).entities.getCompound(0).getCompound("nbt").getString("id"));
    }

    private static JsonObject json(ZipFile jar,String key) throws IOException {
        try (var in = jar.getInputStream(jar.getEntry(key))) {
            return JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
