package dev.expedition.bridge.content.wda;

import com.google.gson.*;
import dev.expedition.bridge.domain.*;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

/** Same production planner against six actual main templates, all four rotations. Not random full-assembly acceptance. */
class ShipBoardingAuditTest {
    private BoardingRouteSearch.Cell rotate(int x,int y,int z,int r) {
        return switch(r) {case 0->new BoardingRouteSearch.Cell(x,y,z);case 1->new BoardingRouteSearch.Cell(-z,y,x);
            case 2->new BoardingRouteSearch.Cell(-x,y,-z);default->new BoardingRouteSearch.Cell(z,y,-x);};
    }
    private CompoundTag read(ZipFile zip,String path) throws Exception {
        try(var in=zip.getInputStream(Objects.requireNonNull(zip.getEntry(path)))){return NbtIo.readCompressed(in);}
    }
    @Test void allThreeSurfaceShipsHaveAnAdditiveRouteInTheOriginalJoinedMainShells() throws Exception {
        var report=new JsonObject();var rows=new JsonArray();
        try(var jar=new ZipFile("libs/DungeonsArise-1.20.x-2.1.58-release.jar")) {
            for(String ship:List.of("illager_corsair","illager_galley","undead_pirate_ship")) {
                var parts=List.of(read(jar,"data/dungeons_arise/structures/"+ship+"/"+ship+"_part_0.nbt"),
                        read(jar,"data/dungeons_arise/structures/"+ship+"/"+ship+"_part_1.nbt"));
                // Real east/west named joints give the translation of part1, without guessing from its filename.
                for(int part=0;part<2;part++) {
                    var palette=parts.get(part).getList("palette",Tag.TAG_COMPOUND);var blocks=parts.get(part).getList("blocks",Tag.TAG_COMPOUND);boolean found=false;
                    for(var raw:blocks) {var block=(CompoundTag)raw;var joint=block.getCompound("nbt");
                        if(!joint.getString("name").equals("dungeons_arise:"+ship+"_side_"+part))continue;
                        var pos=block.getList("pos",Tag.TAG_INT);assertEquals(part==0?31:0,pos.getInt(0));assertEquals(0,pos.getInt(1));assertEquals(0,pos.getInt(2));
                        assertEquals("dungeons_arise:"+ship+"_side_"+(1-part),joint.getString("target"));
                        assertEquals(part==0?"east_up":"west_up",palette.getCompound(block.getInt("state")).getCompound("Properties").getString("orientation"));found=true;
                    }assertTrue(found);
                }
                for(int rotation=0;rotation<4;rotation++) {
                    var cells=new HashMap<BoardingRouteSearch.Cell,BoardingRouteSearch.Block>();
                    for(int part=0;part<2;part++) {
                        var n=parts.get(part);var palette=n.getList("palette",Tag.TAG_COMPOUND);
                        for(var raw:n.getList("blocks",Tag.TAG_COMPOUND)) {
                            var b=(CompoundTag)raw;var pos=b.getList("pos",Tag.TAG_INT);var state=palette.getCompound(b.getInt("state"));
                            var props=state.getCompound("Properties");var id=state.getString("Name");
                            var c=rotate(pos.getInt(0)+part*32,pos.getInt(1),pos.getInt(2),rotation);
                            if(id.equals("minecraft:structure_void"))continue;
                            if(id.equals("minecraft:jigsaw")) {
                                var finalState=b.getCompound("nbt").getString("final_state");
                                if(finalState.equals("minecraft:structure_void"))continue;
                                id=finalState;
                            }
                            var kind=BoardingBlockRules.classify(id,props.getString("type"),props.getString("waterlogged").equals("true"),
                                    b.contains("nbt")&&!state.getString("Name").equals("minecraft:jigsaw"));
                            assertNull(cells.put(c,kind),"Main shells overlap");
                        }
                    }
                    var corners=List.of(rotate(0,0,0,rotation),rotate(62,0,0,rotation),rotate(0,0,30,rotation),rotate(62,0,30,rotation));
                    var box=new BlockBounds(corners.stream().mapToInt(BoardingRouteSearch.Cell::x).min().orElseThrow(),0,
                            corners.stream().mapToInt(BoardingRouteSearch.Cell::z).min().orElseThrow(),corners.stream().mapToInt(BoardingRouteSearch.Cell::x).max().orElseThrow(),47,
                            corners.stream().mapToInt(BoardingRouteSearch.Cell::z).max().orElseThrow());
                    var island=new IslandPlan(box,1,8,6,24,IslandPlan.Surface.WATER,12);
                    BoardingRouteSearch.Probe probe=p->{var original=cells.get(p);if(original!=null)return original;
                        var col=island.column(p.x(),p.z());if(col==null || p.y()<col.bottom()||p.y()>col.top())return BoardingRouteSearch.Block.AIR;
                        return island.material(p.x(),p.y(),p.z())==IslandPlan.Material.WATER?BoardingRouteSearch.Block.WATER:BoardingRouteSearch.Block.SUPPORT;};
                    var search=new BoardingRouteSearch(island,island.completeBounds().expand(16),probe);
                    int batches=0;while(!search.complete() && batches++<10000)search.step(128);
                    assertTrue(search.complete());var route=search.result().orElseThrow(()->new AssertionError("No original-shell route: "+ship));
                    for(var add:route.additions())assertTrue(Set.of(BoardingRouteSearch.Block.AIR,BoardingRouteSearch.Block.WATER).contains(probe.at(add)));
                    var row=new JsonObject();row.addProperty("structure",ship);row.addProperty("rotation",rotation);
                    row.addProperty("steps",route.feet().size());row.addProperty("newSupports",route.additions().size());
                    row.addProperty("nativeDeckCells",route.deckCells());row.add("shore",new Gson().toJsonTree(route.shore()));row.add("landing",new Gson().toJsonTree(route.landing()));rows.add(row);
                }
            }
        }
        assertEquals(12,rows.size());report.add("cases",rows);report.addProperty("runtimeAccepted",false);report.addProperty("formalPoolEnrollment",false);
        report.addProperty("scope","Original joined main shells only; excludes random spawner/entity pieces, actual collision engine and player traversal");
        Files.createDirectories(Path.of("build/reports"));Files.writeString(Path.of("build/reports/ship-boarding-audit.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
