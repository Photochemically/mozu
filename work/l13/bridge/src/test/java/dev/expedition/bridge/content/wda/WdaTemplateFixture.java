package dev.expedition.bridge.content.wda;

import com.google.gson.*;
import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Rotation;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

final class WdaTemplateFixture {
    static final Path JAR = Path.of("libs/DungeonsArise-1.20.x-2.1.58-release.jar");
    static final String HASH = "f8f1254dbe7ed7c1d5403f20361a36f0d0587c82b71313e3f427c7f1ce7c8a90";
    static final int Y=96;
    record Block(BlockPos pos,String id,CompoundTag props,CompoundTag data) {}
    record Template(String id,String resource,Set<String> pools,boolean legacy,BlockPos size,List<Block> blocks,ListTag entities) {
        boolean projected() { return id.startsWith("street_"); }
        Block joint(String target,Direction facing) {
            return blocks.stream().filter(b->b.data.getString("target").equals("dungeons_arise:"+target)
                    && b.props.getString("orientation").startsWith(facing.getName()+"_")).findFirst().orElseThrow();
        }
    }
    private static BlockPos vector(ListTag v) { return new BlockPos(v.getInt(0),v.getInt(1),v.getInt(2)); }
    private static Cell cell(BlockPos p) { return new Cell(p.getX(),p.getY(),p.getZ()); }
    static Map<String,Template> templates(String structure) throws IOException {
        var result=new HashMap<String,Template>();
        try (var zip=new ZipFile(JAR.toFile())) {
            var elementTypes = new HashMap<String,String>();
            var pools = new HashMap<String,Set<String>>();
            String startPool=json(zip,"data/dungeons_arise/worldgen/structure/"+structure+".json").get("start_pool").getAsString();
            String poolPrefix="data/dungeons_arise/worldgen/template_pool/";
            String poolDir=poolPrefix+startPool.substring(startPool.indexOf(':')+1,startPool.lastIndexOf('/')+1);
            for (var entry : Collections.list(zip.entries())) {
                if (!entry.getName().startsWith(poolDir) || !entry.getName().endsWith(".json")) continue;
                String pool="dungeons_arise:"+entry.getName().substring(poolPrefix.length(),entry.getName().length()-5);
                for (var raw : json(zip,entry.getName()).getAsJsonArray("elements")) {
                    var element=raw.getAsJsonObject().getAsJsonObject("element");
                    var location=element.get("location").getAsString();
                    var type=element.get("element_type").getAsString();
                    var previous=elementTypes.putIfAbsent(location,type);
                    if (previous!=null && !previous.equals(type)) throw new IllegalStateException("Mixed pool element types for "+location);
                    pools.computeIfAbsent(location, ignored -> new HashSet<>()).add(pool);
                }
            }
            for (var e:Collections.list(zip.entries())) {
                String prefix="data/dungeons_arise/structures/"+structure+"/";
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
                    var filename=e.getName().substring(prefix.length(),e.getName().length()-4);
                    var id=filename.startsWith(structure+"_") ? filename.substring(structure.length()+1) : filename;
                    var location="dungeons_arise:"+structure+"/"+filename;
                    result.put(id,new Template(id,location,pools.getOrDefault(location,Set.of()),
                            "minecraft:legacy_single_pool_element".equals(elementTypes.get(location)),vector(n.getList("size",Tag.TAG_INT)),blocks,n.getList("entities",Tag.TAG_COMPOUND)));
                }
            }
        }
        return result;
    }

    record Placed(Template template,BlockPos origin,Rotation rotation) {
        BlockPos at(BlockPos local) { return local.rotate(rotation).offset(origin); }
        BlockBounds bounds() {
            var end=at(template.size.offset(-1,-1,-1));
            return new BlockBounds(Math.min(origin.getX(),end.getX()),origin.getY(),Math.min(origin.getZ(),end.getZ()),
                    Math.max(origin.getX(),end.getX()),end.getY(),Math.max(origin.getZ(),end.getZ()));
        }
        WdaTemplatePiece piece() { return new WdaTemplatePiece(template.resource,origin,rotation,bounds(),template.projected()); }
    }

    static Placed connect(Placed parent,Block socket,Template child,Direction inletFacing) {
        return connect(parent,socket,child,inletFacing,parent.rotation());
    }
    static Placed connect(Placed parent,Block socket,Template child,Direction inletFacing,Rotation preferred) {
        var target=socket.data.getString("target");
        var inlet=child.blocks.stream().filter(b->b.data.getString("name").equals(target)
                && b.props.getString("orientation").startsWith(inletFacing.getName()+"_")).findFirst().orElseThrow();
        assertTrue(child.pools.contains(socket.data.getString("pool")), "Child absent from original parent pool");
        var facing=parent.rotation.rotate(Direction.byName(socket.props.getString("orientation").split("_")[0]));
        boolean aligned=socket.data.getString("joint").equals("aligned");
        var top=parent.rotation.rotate(Direction.byName(socket.props.getString("orientation").split("_")[1]));
        var inletTop=Direction.byName(inlet.props.getString("orientation").split("_")[1]);
        var rot=Arrays.stream(Rotation.values()).map(preferred::getRotated)
                .filter(r -> r.rotate(inletFacing)==facing.getOpposite() && (!aligned || r.rotate(inletTop)==top))
                .findFirst().orElseThrow();
        var origin=parent.at(socket.pos).relative(facing).subtract(inlet.pos.rotate(rot));
        // Native Placer uses the first free height minus child connector Y if either end is terrain-matching.
        if (parent.template.projected() || child.projected()) origin=new BlockPos(origin.getX(),Y-inlet.pos.getY(),origin.getZ());
        return new Placed(child,origin,rot);
    }
    record State(String id,CompoundTag props,Direction facing,CompoundTag data) {}
    static class Terrain {
        final Map<Cell,State> placed=new HashMap<>();
        final Set<Cell> blocked=new HashSet<>();
        final IslandPlan island;
        Terrain(List<Placed> pieces) {
            var bounds=pieces.stream().map(Placed::bounds).reduce(IslandPlan::union).orElseThrow();
            for (var p:pieces) {
                if (p.template.projected()) bounds=IslandPlan.union(bounds,p.bounds().move(0,Y-1-p.origin.getY(),0));
                for (var b:p.template.blocks) {
                    var id=b.id;
                    if (id.equals("minecraft:jigsaw")) {
                        id=b.data.getString("final_state");
                        if (id.equals("minecraft:structure_void")) continue;
                    }
                    if (p.template.legacy && id.equals("minecraft:air")) continue;
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
    }
    static JsonObject json(ZipFile zip,String key) throws IOException {
        try(var in=zip.getInputStream(zip.getEntry(key))) {return JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();}
    }
}
