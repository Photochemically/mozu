package dev.expedition.bridge.diagnostics;

import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.expedition.bridge.ExpeditionBridge;
import dev.expedition.bridge.content.wda.WdaStructurePlanner;
import dev.expedition.bridge.domain.IslandPlan;
import dev.expedition.bridge.domain.DungeonSpec;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.dimdev.dimdoors.world.ModDimensions;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Operator-only read-only native planner. Does not create entrances/instances or permit player dungeon creation. */
public final class PlanningAuditCommands {
    private record Job(CommandSourceStack source,List<ResourceLocation> ids,long seed,WdaStructurePlanner.Environment environment,
                       PlanAuditReport report,Path file,int next){}
    private final Map<MinecraftServer,Job> jobs=new IdentityHashMap<>();
    @SubscribeEvent public void register(RegisterCommandsEvent event) {
        var depth=Commands.argument("depth",IntegerArgumentType.integer(4,128)).then(surface("grass")).then(surface("rock")).then(surface("void"))
                .then(Commands.literal("water").then(withModes(Commands.argument("water_depth",IntegerArgumentType.integer(2,64)),IslandPlan.Surface.WATER)));
        var rim=Commands.argument("rim",IntegerArgumentType.integer(1,64)).then(depth);
        var padding=Commands.argument("padding",IntegerArgumentType.integer(4,64)).then(rim);
        var height=Commands.argument("surface_y",IntegerArgumentType.integer(-63,319)).then(padding);
        var seed=Commands.argument("seed",LongArgumentType.longArg()).then(height);
        var branch=Commands.literal("wda").then(Commands.argument("target",ResourceLocationArgument.id()).then(seed));
        event.getDispatcher().register(Commands.literal("expedition").requires(source->source.hasPermission(2))
                .then(Commands.literal("audit").then(branch)
                        .then(Commands.literal("dimensions").executes(c->dimensions(c.getSource())))
                        .then(Commands.literal("status").executes(c->status(c.getSource())))
                        .then(Commands.literal("cancel").executes(c->cancel(c.getSource())))));
    }
    private ArgumentBuilder<CommandSourceStack,?> surface(String name) {
        return withModes(Commands.literal(name),IslandPlan.Surface.valueOf(name.toUpperCase(Locale.ROOT)));
    }
    private ArgumentBuilder<CommandSourceStack,?> withModes(ArgumentBuilder<CommandSourceStack,?> branch,IslandPlan.Surface surface) {
        var defaultMode=surface==IslandPlan.Surface.VOID?DungeonSpec.EntryMode.START_DECK:DungeonSpec.EntryMode.PERIMETER;
        branch.executes(c->start(c,surface,defaultMode));
        for(var mode:DungeonSpec.EntryMode.values())branch.then(Commands.literal(mode.name().toLowerCase(Locale.ROOT)).executes(c->start(c,surface,mode)));
        return branch;
    }
    private int start(CommandContext<CommandSourceStack> context,IslandPlan.Surface surface,DungeonSpec.EntryMode mode) {
        var source=context.getSource();var server=source.getServer();
        if(jobs.containsKey(server)){source.sendFailure(Component.literal("已有规划检查正在运行。"));return 0;}
        try {
            var target=ResourceLocationArgument.getId(context,"target");var registry=server.registryAccess().registryOrThrow(Registries.STRUCTURE);
            List<ResourceLocation> ids;
            if(target.toString().equals("minecraft:all"))ids=registry.keySet().stream().filter(id->id.getNamespace().equals("dungeons_arise")).sorted().toList();
            else {
                var id=target;
                if(!id.getNamespace().equals("dungeons_arise") || !registry.containsKey(id))throw new IllegalArgumentException("未知WDA结构："+target);
                ids=List.of(id);
            }
            if(ids.isEmpty())throw new IllegalArgumentException("没有可检查的WDA结构");
            long seed=LongArgumentType.getLong(context,"seed");
            int waterDepth=surface==IslandPlan.Surface.WATER?IntegerArgumentType.getInteger(context,"water_depth"):0;
            var environment=new WdaStructurePlanner.Environment(IntegerArgumentType.getInteger(context,"surface_y"),IntegerArgumentType.getInteger(context,"padding"),
                    IntegerArgumentType.getInteger(context,"rim"),IntegerArgumentType.getInteger(context,"depth"),surface,waterDepth,false,mode);
            String runId=UUID.randomUUID().toString();
            var report=new PlanAuditReport(runId,ids.stream().map(ResourceLocation::toString).toList(),seed,environment);
            var file=server.getWorldPath(LevelResource.ROOT).resolve("expedition_reports/wda-plan-"+runId+".json");
            var job=new Job(source,ids,seed,environment,report,file,0);write(job);jobs.put(server,job);
            source.sendSuccess(()->Component.literal("已排队检查 "+ids.size()+" 个结构。只规划，不放置建筑；单次原生规划仍可能造成短暂停顿。"),false);
            return 1;
        } catch(Exception failure){source.sendFailure(Component.literal("无法开始规划检查："+failure.getMessage()));return 0;}
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;var server=event.getServer();var job=jobs.get(server);if(job==null)return;
        var id=job.ids().get(job.next());long started=System.nanoTime();WdaStructurePlanner.Inspection inspection=null;String failure=null;
        try {inspection=new WdaStructurePlanner().inspect(server,id,job.seed(),job.environment());}
        catch(Exception error){failure=error.getClass().getSimpleName()+": "+error.getMessage();}
        job.report().record(id.toString(),inspection,failure,System.nanoTime()-started);
        int next=job.next()+1;
        if(next==job.ids().size())job.report().finish();
        try {write(job);}
        catch(IOException error) {
            jobs.remove(server);ExpeditionBridge.LOGGER.error("Cannot write native plan audit {}",job.file(),error);
            job.source().sendFailure(Component.literal("规划报告写入失败，检查已停止。"));return;
        }
        if(next==job.ids().size()) {
            jobs.remove(server);job.source().sendSuccess(()->Component.literal("规划检查结束，报告："+job.file()+"。高度可容纳不等于游戏内验收通过。"),false);
        } else jobs.put(server,new Job(job.source(),job.ids(),job.seed(),job.environment(),job.report(),job.file(),next));
    }
    private int status(CommandSourceStack source) {
        var job=jobs.get(source.getServer());
        source.sendSuccess(()->Component.literal(job==null?"当前没有运行中的规划检查。":"规划检查进度："+job.next()+"/"+job.ids().size()+"；报告："+job.file()),false);return 1;
    }
    private int dimensions(CommandSourceStack source) {
        var server=source.getServer();
        for(var key:List.of(ModDimensions.DUNGEON,ModDimensions.PUBLIC,ModDimensions.PERSONAL)) {
            var level=server.getLevel(key);
            if(level==null) {source.sendFailure(Component.literal(key.location()+": 维度不存在"));continue;}
            String type=level.dimensionTypeId().location().toString();
            String line=key.location()+" | type="+type+" | minY="+level.getMinBuildHeight()
                    +" | height="+level.getHeight()+" | maxYExclusive="+level.getMaxBuildHeight()
                    +" | generator="+level.getChunkSource().getGenerator().getClass().getSimpleName();
            source.sendSuccess(()->Component.literal(line),false);
            ExpeditionBridge.LOGGER.info("Pocket dimension audit: {}",line);
        }
        source.sendSuccess(()->Component.literal("这是当前已加载的维度信息；不代表建筑生成、传送或重载验收通过。"),false);
        return 1;
    }
    private int cancel(CommandSourceStack source) {
        var job=jobs.remove(source.getServer());if(job==null)return 0;job.report().cancel();
        try {write(job);}catch(IOException error){ExpeditionBridge.LOGGER.error("Cannot checkpoint cancelled audit",error);source.sendFailure(Component.literal("取消状态报告写入失败，请查看服务器日志。"));}
        source.sendSuccess(()->Component.literal("规划检查已取消，未检查项会保留标记。"),false);return 1;
    }
    @SubscribeEvent public void stopping(ServerStoppingEvent event) {
        var job=jobs.remove(event.getServer());if(job==null)return;job.report().cancel();
        try {write(job);}catch(IOException error){ExpeditionBridge.LOGGER.error("Cannot checkpoint interrupted native plan audit",error);}
    }
    private void write(Job job) throws IOException {
        var dir=job.file().getParent();Files.createDirectories(dir);var temp=Files.createTempFile(dir,"plan-",".tmp");
        try {
            Files.writeString(temp,new GsonBuilder().setPrettyPrinting().create().toJson(job.report().snapshot()),StandardCharsets.UTF_8);
            Files.move(temp,job.file(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally {Files.deleteIfExists(temp);}
    }
}
