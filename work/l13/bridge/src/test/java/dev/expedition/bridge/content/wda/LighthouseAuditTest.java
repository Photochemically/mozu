package dev.expedition.bridge.content.wda;

import com.google.gson.*;
import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import dev.expedition.bridge.storage.ExpeditionSavedData;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class LighthouseAuditTest {
    private static final Path JAR = Path.of("libs/DungeonsArise-1.20.x-2.1.58-release.jar");
    private static final String SHA = "f8f1254dbe7ed7c1d5403f20361a36f0d0587c82b71313e3f427c7f1ce7c8a90";
    private record State(String id, CompoundTag props, Direction facing, CompoundTag data) {}
    private record Model(LighthouseLayout layout, IslandPlan island, Map<Cell, State> cells) {}

    private static CompoundTag template(ZipFile jar, int part) throws IOException {
        var path = "data/dungeons_arise/structures/lighthouse/lighthouse_part_" + part + ".nbt";
        try (var in = jar.getInputStream(Objects.requireNonNull(jar.getEntry(path)))) { return NbtIo.readCompressed(in); }
    }

    private static JsonObject json(ZipFile jar, String path) throws IOException {
        try (var in = jar.getInputStream(Objects.requireNonNull(jar.getEntry("data/dungeons_arise/worldgen/" + path + ".json")))) {
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test void pinnedCustomGeneratorChecksOccupiedHeightBeforeDelegatingToJigsaw() throws Exception {
        try (var jar = new ZipFile(JAR.toFile())) {
            var def = json(jar, "structure/lighthouse");
            assertEquals("dungeons_arise:generic_structures", def.get("type").getAsString());
            assertEquals("WORLD_SURFACE_WG", def.get("project_start_to_heightmap").getAsString());
            assertEquals(-1, def.getAsJsonObject("start_height").get("absolute").getAsInt());
            assertEquals("dungeons_arise:enigmatic/lighthouse/lighthouse_start", def.get("start_pool").getAsString());
            for (var name : List.of("start", "tower")) {
                var pool = json(jar, "template_pool/enigmatic/lighthouse/lighthouse_" + name);
                var ids = new ArrayList<String>();
                for (var item : pool.getAsJsonArray("elements")) {
                    var e = item.getAsJsonObject().getAsJsonObject("element");
                    assertEquals("minecraft:empty", e.get("processors").getAsString());
                    assertEquals("rigid", e.get("projection").getAsString());
                    ids.add(e.get("location").getAsString());
                }
                assertEquals(name.equals("start") ? List.of(LighthouseLayout.BASE) : List.of(LighthouseLayout.BASE, LighthouseLayout.TOWER), ids);
            }
            var node = new ClassNode();
            try (var in = jar.getInputStream(jar.getEntry("net/aurelj/dungeons_arise/structures/WDAGenericStructures.class"))) {
                new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
            var check = node.methods.stream().filter(m -> m.name.equals("extraSpawningChecks")).findFirst().orElseThrow();
            var code = Arrays.asList(check.instructions.toArray());
            assertTrue(code.stream().anyMatch(i -> i instanceof FieldInsnNode f && f.name.equals("MOTION_BLOCKING_NO_LEAVES")));
            var height = code.stream().filter(i -> i instanceof MethodInsnNode m && m.name.equals("m_223235_")).findFirst().orElseThrow();
            assertEquals(150, ((IntInsnNode) height.getNext()).operand);
            assertEquals(Opcodes.IF_ICMPGE, height.getNext().getNext().getOpcode());
            var mapping = Files.readString(Path.of("build/createMcpToSrg/output.tsrg"));
            assertTrue(mapping.lines().anyMatch(l -> l.contains("getFirstOccupiedHeight ") && l.endsWith(" m_223235_")));
            var generation = node.methods.stream().filter(m -> m.name.equals("m_214086_")).findFirst().orElseThrow();
            var calls = Arrays.stream(generation.instructions.toArray()).filter(i -> i instanceof MethodInsnNode)
                    .map(i -> (MethodInsnNode)i).toList();
            int guard = calls.indexOf(calls.stream().filter(m -> m.name.equals("extraSpawningChecks")).findFirst().orElseThrow());
            int jigsaw = calls.indexOf(calls.stream().filter(m -> m.owner.equals("net/aurelj/dungeons_arise/structures/ModifiedJigsawPlacement")
                    && m.name.equals("addPieces")).findFirst().orElseThrow());
            assertTrue(guard < jigsaw);
        }
    }

    private static BlockPos vector(Tag raw) {
        var v = (ListTag) raw;
        return new BlockPos(v.getInt(0), v.getInt(1), v.getInt(2));
    }

    private static Cell cell(BlockPos pos) { return new Cell(pos.getX(), pos.getY(), pos.getZ()); }
    private static CompoundTag joint(CompoundTag n, boolean lower) {
        var list = n.getList("blocks", Tag.TAG_COMPOUND).stream().map(b -> (CompoundTag)b)
                .filter(b -> b.getCompound("nbt").getString("id").equals("minecraft:jigsaw")).toList();
        assertEquals(1, list.size());
        var b = list.get(0);
        var state = n.getList("palette", Tag.TAG_COMPOUND).getCompound(b.getInt("state"));
        assertEquals(lower ? "up_south" : "down_south", state.getCompound("Properties").getString("orientation"));
        assertEquals(new BlockPos(15, lower ? 30 : 0, 20), vector(b.get("pos")));
        var tag = b.getCompound("nbt");
        assertEquals(LighthouseLayout.TARGET, tag.getString("name"));
        assertEquals(LighthouseLayout.TARGET, tag.getString("target"));
        assertEquals("aligned", tag.getString("joint"));
        assertEquals("dungeons_arise:enigmatic/lighthouse/lighthouse_tower", tag.getString("pool"));
        assertEquals("minecraft:structure_void", tag.getString("final_state"));
        return b;
    }

    private static Model compose(List<CompoundTag> templates, int turns, int surfaceY, IslandPlan.Surface ground) {
        var rotate = Rotation.values()[turns];
        var origin = new BlockPos(-210, surfaceY - 2, 305);
        var lowerJoint = vector(joint(templates.get(0), true).get("pos")).rotate(rotate).offset(origin);
        var upperOrigin = lowerJoint.above().subtract(vector(joint(templates.get(1), false).get("pos")).rotate(rotate));
        var pieces = new ArrayList<LighthouseLayout.Piece>();
        var cells = new HashMap<Cell, State>();
        for (int i = 0; i < templates.size(); i++) {
            var n = templates.get(i);
            var start = i == 0 ? origin : upperOrigin;
            var size = vector(n.get("size"));
            assertEquals(new BlockPos(31, i == 0 ? 31 : 37, 31), size);
            var end = size.offset(-1, -1, -1).rotate(rotate).offset(start);
            var b = new BlockBounds(Math.min(start.getX(),end.getX()), start.getY(), Math.min(start.getZ(),end.getZ()),
                    Math.max(start.getX(),end.getX()), end.getY(), Math.max(start.getZ(),end.getZ()));
            pieces.add(new LighthouseLayout.Piece(i == 0 ? LighthouseLayout.BASE : LighthouseLayout.TOWER, cell(start), turns, b));
            var palette = n.getList("palette", Tag.TAG_COMPOUND);
            for (var raw : n.getList("blocks", Tag.TAG_COMPOUND)) {
                var block = (CompoundTag) raw;
                var state = palette.getCompound(block.getInt("state"));
                String id = state.getString("Name");
                if (id.equals("minecraft:jigsaw")) continue; // checked structure_void final state
                var props = state.getCompound("Properties");
                var facing = Direction.byName(props.getString("facing"));
                var pos = vector(block.get("pos")).rotate(rotate).offset(start);
                assertNull(cells.put(cell(pos), new State(id, props, facing == null ? null : rotate.rotate(facing), block.getCompound("nbt"))));
            }
        }
        var bounds = IslandPlan.union(pieces.get(0).bounds(), pieces.get(1).bounds());
        var layout = LighthouseLayout.inspect(pieces, surfaceY, bounds);
        return new Model(layout, new IslandPlan(bounds, layout.groundY(), 8, 6, 24, ground), cells);
    }

    private static LighthouseRoute.Probe probe(Map<Cell, State> cells, IslandPlan island, Set<Cell> blocked) {
        return new LighthouseRoute.Probe() {
            public Type at(Cell p) {
                if (blocked.contains(p)) return Type.BLOCKED;
                var state = cells.get(p);
                if (state != null) return InteriorBlockRules.classify(state.id(), state.props().getString("type"),
                        state.props().getString("half"), state.props().getBoolean("waterlogged")
                        || state.props().getString("waterlogged").equals("true"), !state.data().isEmpty());
                var col = island.solidColumn(p.x(),p.z());
                return col != null && p.y() >= col.bottom() && p.y() <= col.top() ? Type.SUPPORT : Type.AIR;
            }
            public boolean originalStep(Cell p, int turns) {
                var s = cells.get(p);
                return !blocked.contains(p) && s != null && s.id().equals("minecraft:stone_brick_stairs")
                        && s.props().getString("half").equals("bottom") && s.props().getString("shape").equals("straight")
                        && s.props().getString("waterlogged").equals("false") && s.facing() == Direction.from2DDataValue(turns);
            }
            public boolean originalLadder(Cell p, int turns) {
                var s = cells.get(p);
                if (blocked.contains(p) || s == null || !s.id().equals("minecraft:ladder")
                        || !s.props().getString("waterlogged").equals("false") || s.facing() != Direction.from2DDataValue(turns).getOpposite()) return false;
                var back = s.facing().getOpposite();
                return at(p.move(back.getStepX(),0,back.getStepZ())) == Type.SUPPORT;
            }
        };
    }

    private static void check(Model m, int count, Set<Cell> blocked) {
        var route = new LighthouseRoute(m.layout(),m.island(),count,probe(m.cells(),m.island(),blocked));
        for (int i = 0; i < 256; i++) if (route.step()) return;
        fail("Unbounded lighthouse survey");
    }

    @Test void actualBaseAndUpperTowerReachOriginalLadderAcrossRotationsHeightsAndExitCounts() throws Exception {
        var rows = new JsonArray();
        try (var jar = new ZipFile(JAR.toFile())) {
            var templates = List.of(template(jar,1),template(jar,2));
            for (int turns = 0; turns < 4; turns++) for (int height : List.of(96,150))
                for (var surface : List.of(IslandPlan.Surface.GRASS, IslandPlan.Surface.ROCK)) {
                    var m = compose(templates,turns,height,surface);
                    var before = Map.copyOf(m.cells());
                    for (int count = 1; count <= 4; count++) check(m,count,Set.of());
                    var nominal = new IslandPlan(m.island().building(),height,8,6,24,surface);
                    var head = m.layout().at(15,1,6);
                    assertEquals(Type.SUPPORT,probe(before,nominal,Set.of()).at(head));
                    assertEquals(Type.AIR,probe(before,m.island(),Set.of()).at(head));
                    assertEquals(84,before.values().stream().filter(s->s.id().equals("minecraft:redstone_lamp")).count());
                    assertEquals(32,before.values().stream().filter(s->s.id().equals("minecraft:repeater")).count());
                    assertEquals("dungeons_arise:chests/lighthouse/lighthouse_top",before.get(m.layout().at(11,48,18)).data().getString("LootTable"));
                    assertEquals(before,m.cells());
                    var row = new JsonObject();
                    row.addProperty("rotation",turns); row.addProperty("surface",surface.name()); row.addProperty("planningSurface",height);
                    row.addProperty("constructionFirstFree",m.island().firstFreeY()); row.addProperty("requiredHeight",m.island().completeBounds().expand(16).height());
                    row.addProperty("exitCountsChecked","1,2,3,4"); rows.add(row);
                }
        }
        var digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(JAR)));
        assertEquals(SHA,digest);
        var report = new JsonObject();
        report.addProperty("wdaSha256",digest); report.add("cases",rows); report.addProperty("runtimeAccepted",false);
        report.addProperty("scope","Two joined original pieces; perimeter to ladder foot. Lamp operation, climbing upper floors, collisions, native generation and multiplayer restart await in-game acceptance.");
        Files.createDirectories(Path.of("build/reports"));
        Files.writeString(Path.of("build/reports/lighthouse-entry-audit.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }

    @Test void alternatePerimeterWalkIsAllowedButDisconnectedExitsAndBlockedOriginalStairsAreRejected() throws Exception {
        try (var jar = new ZipFile(JAR.toFile())) {
            var m = compose(List.of(template(jar,1),template(jar,2)),0,96,IslandPlan.Surface.ROCK);
            var l = m.layout();
            check(m,4,Set.of(l.at(33,0,5)));
            assertThrows(IllegalStateException.class,()->check(m,1,Set.of(l.at(33,0,5),l.at(33,0,25))));
            for (var p : List.of(l.at(15,0,6),l.at(15,4,8),l.at(15,5,16),l.at(15,4,19),l.at(15,4,20)))
                assertThrows(IllegalStateException.class,()->check(m,1,Set.of(p)));
            var step = l.at(15,1,7);
            var s = m.cells().get(step);
            m.cells().put(step,new State(s.id(),s.props(),s.facing().getOpposite(),s.data()));
            assertThrows(IllegalStateException.class,()->check(m,1,Set.of()));
        }
    }

    @Test void refusesPartialOrDetachedTowerAndWetEnvironment() {
        var base = new LighthouseLayout.Piece(LighthouseLayout.BASE,new Cell(0,94,0),0,new BlockBounds(0,94,0,30,124,30));
        var tower = new LighthouseLayout.Piece(LighthouseLayout.TOWER,new Cell(0,125,0),0,new BlockBounds(0,125,0,30,161,30));
        var bounds = IslandPlan.union(base.bounds(),tower.bounds());
        var layout = LighthouseLayout.inspect(List.of(base,tower),96,bounds);
        assertEquals(layout.at(15,4,19).move(-48,20,65),layout.move(-48,20,65).at(15,4,19));
        assertThrows(IllegalArgumentException.class,()->LighthouseLayout.inspect(List.of(base),96,bounds));
        assertThrows(IllegalArgumentException.class,()->LighthouseLayout.inspect(List.of(base,base),96,bounds));
        var detached = new LighthouseLayout.Piece(tower.id(),tower.origin().move(0,1,0),0,tower.bounds().move(0,1,0));
        assertThrows(IllegalArgumentException.class,()->LighthouseLayout.inspect(List.of(base,detached),96,bounds));
        assertThrows(IllegalArgumentException.class,()->LighthouseLayout.inspect(List.of(base,tower),97,bounds));
        assertThrows(IllegalArgumentException.class,()->LighthouseLayout.validateEnvironment(IslandPlan.Surface.WATER,DungeonSpec.EntryMode.PERIMETER,false));
        assertThrows(IllegalArgumentException.class,()->LighthouseLayout.validateEnvironment(IslandPlan.Surface.ROCK,DungeonSpec.EntryMode.START_ROOM,false));
    }

    @Test void readyPerimeterSnapshotsRetainTheirActualArrivalAcrossTheTerrainCorrection() throws Exception {
        try (var jar = new ZipFile(JAR.toFile())) {
            var m = compose(List.of(template(jar,1),template(jar,2)),1,96,IslandPlan.Surface.GRASS);
            var nominal = new IslandPlan(m.island().building(),96,8,6,24,IslandPlan.Surface.GRASS);
            for (var island : List.of(nominal,m.island())) {
                var selection = new DungeonSpec("test:lighthouse",DungeonSpec.Provider.WDA,LighthouseLayout.TARGET,Set.of(2),1,
                        new DungeonSpec.Environment(96,8,6,24,IslandPlan.Surface.GRASS),4);
                var portal = new PortalRecord(UUID.randomUUID(),PortalKind.EXPLORATION,2,
                        new PortalRecord.Site("minecraft:overworld",0,70,0),1234);
                portal.selectContent(new DungeonCatalog.Choice(2,selection));
                var sites = island.perimeterSites(4).stream().map(c -> new PortalRecord.Site("dimdoors:dungeon_pockets",c.x(),c.y(),c.z())).toList();
                portal.reserve(selection.target(),new PortalRecord.Destination(sites.get(0).dimension(),7,island.completeBounds().expand(16),sites.get(0)));
                portal.generationStarted();
                sites.forEach(portal::registerExit);
                portal.generationComplete();
                var data = new ExpeditionSavedData();
                data.add(portal);
                var restored = ExpeditionSavedData.load(data.save(new CompoundTag())).get(portal.id());
                assertEquals(island.firstFreeY(),restored.destination().arrival().y());
                assertEquals(portal.exits(),restored.exits());
                assertEquals(96,restored.selection().content().environment().firstFreeY());
            }
        }
    }
}
