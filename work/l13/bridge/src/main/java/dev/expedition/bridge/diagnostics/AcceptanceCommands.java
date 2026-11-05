package dev.expedition.bridge.diagnostics;

import com.mojang.brigadier.arguments.LongArgumentType;
import dev.expedition.bridge.ExpeditionBridge;
import dev.expedition.bridge.content.*;
import dev.expedition.bridge.domain.*;
import dev.expedition.bridge.portal.ManagedEntrancePlacement;
import dev.expedition.bridge.storage.ExpeditionSavedData;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.dimdev.dimdoors.world.ModDimensions;

/** Explicit operator commands; normal players cannot provision exploration dungeons. */
public final class AcceptanceCommands {
    @SubscribeEvent public void register(RegisterCommandsEvent event) {
        var test=Commands.literal("test").requires(source->source.hasPermission(4));
        test.then(Commands.literal("list").executes(c->list(c.getSource())));
        var entrance=Commands.literal("entrance");
        for(var fixture:AcceptanceFixtures.all())entrance.then(Commands.literal(fixture.id())
                .then(Commands.argument("pos",BlockPosArgument.blockPos())
                        .executes(c->place(c.getSource(),fixture,BlockPosArgument.getBlockPos(c,"pos"),AcceptanceFixtures.DEFAULT_SEED))
                        .then(Commands.argument("seed",LongArgumentType.longArg())
                                .executes(c->place(c.getSource(),fixture,BlockPosArgument.getBlockPos(c,"pos"),LongArgumentType.getLong(c,"seed"))))));
        test.then(entrance);
        event.getDispatcher().register(Commands.literal("expedition").requires(source->source.hasPermission(2)).then(test));
    }
    private void require(CommandSourceStack source) throws Exception {
        // Also check execution, not only Brigadier discovery/parse. No command can enable this marker.
        if(!source.hasPermission(4))throw new IllegalStateException("需要4级管理员权限");
        DevelopmentWorldGate.require(source.getServer().getWorldPath(LevelResource.ROOT));
    }
    private int list(CommandSourceStack source) {
        try {
            require(source);
            for(var f:AcceptanceFixtures.all())source.sendSuccess(()->Component.literal(f.id()+"："+f.purpose()+"；目标="
                    +(f.choice()==null?NativeRewardRoomProvider.TEMPLATE:f.choice().content().target())
                    +"；探索高度至少"+f.minimumPocketHeight()),false);
            source.sendSuccess(()->Component.literal("案例使用临时等级与固定内容；不验证随机目录、门密度或最终平衡。"),false);return 1;
        }catch(Exception error){return failure(source,error);}
    }
    private int place(CommandSourceStack source,AcceptanceFixtures.Fixture fixture,BlockPos pos,long seed) {
        try {
            require(source);
            var server=source.getServer();var level=source.getLevel();
            if(!server.isSameThread() || !level.dimension().equals(Level.OVERWORLD))throw new IllegalStateException("请在主世界执行");
            var data=ExpeditionSavedData.get(server);
            var record=fixture.record(new PortalRecord.Site("minecraft:overworld",pos.getX(),pos.getY(),pos.getZ()),seed);
            var existing=data.get(record.id());
            if(existing!=null) {
                source.sendSuccess(()->Component.literal("已有案例 "+fixture.id()+"，实例="+existing.id()+"，状态="+existing.phase()
                        +"；未重建门或刷新内容。可用 /expedition audit snapshot "+existing.id()+" 检查。"),false);return 1;
            }
            if(data.records().size()>=32)throw new IllegalStateException("快速验收世界最多32个实例，请另建测试世界执行批量验收");
            if(data.records().stream().anyMatch(p->p.entrance().dimension().equals("minecraft:overworld")
                    && NaturalEntryPlan.tooClose(pos.getX(),pos.getZ(),p.entrance().x(),p.entrance().z(),8)))
                throw new IllegalArgumentException("距已有入口不足8格；不会替换旧入口或重放已关闭案例");
            var world=server.getLevel(ModDimensions.DUNGEON);
            if(world==null || world.getHeight()<fixture.minimumPocketHeight())
                throw new IllegalStateException("此案例要求探索空间高度至少"+fixture.minimumPocketHeight()+"；请先用 audit dimensions 检查独立高度试验包");
            if(fixture.choice()==null)new NativeRewardRoomProvider().validate(server,record);
            else if(fixture.choice().content().provider()==DungeonSpec.Provider.NATIVE)NativeRoomProvider.validate(fixture.choice().content());
            else if(!server.registryAccess().registryOrThrow(Registries.STRUCTURE).containsKey(ResourceLocation.parse(fixture.choice().content().target())))
                throw new IllegalArgumentException("所需WDA结构未注册："+fixture.choice().content().target());
            // No natural attempt/policy is recorded, and no production catalog is enabled or replaced.
            ManagedEntrancePlacement.place(level,pos,fixture.door(),0,record);
            ExpeditionBridge.LOGGER.info("Acceptance entrance {} id={} source={} seed={} selection={}; lazy generation retained",
                    fixture.id(),record.id(),record.entrance(),seed,record.selection());
            source.sendSuccess(()->Component.literal("已放置验收门 "+fixture.id()+"，实例="+record.id()+"，位置="+pos.toShortString()
                    +"，种子="+seed+"。尚未生成地牢；首次实际进入才生成，原边狱概率仍生效。"),false);return 1;
        } catch(Exception error){return failure(source,error);}
    }
    private int failure(CommandSourceStack source,Exception error) {
        ExpeditionBridge.LOGGER.warn("Acceptance command refused or failed: {}",error.toString());
        source.sendFailure(Component.literal("验收操作未完成："+error.getMessage()));return 0;
    }
}
