package net.minecraft.world.level.levelgen.structure.structures;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure.1;

public final class JigsawStructure extends Structure {
   public static final int MAX_TOTAL_STRUCTURE_RANGE = 128;
   public static final Codec CODEC = ExtraCodecs.validate(RecordCodecBuilder.mapCodec((p_227640_) -> p_227640_.group(settingsCodec(p_227640_), StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter((p_227656_) -> p_227656_.startPool), ResourceLocation.CODEC.optionalFieldOf("start_jigsaw_name").forGetter((p_227654_) -> p_227654_.startJigsawName), Codec.intRange(0, 7).fieldOf("size").forGetter((p_227652_) -> p_227652_.maxDepth), HeightProvider.CODEC.fieldOf("start_height").forGetter((p_227649_) -> p_227649_.startHeight), Codec.BOOL.fieldOf("use_expansion_hack").forGetter((p_227646_) -> p_227646_.useExpansionHack), Types.CODEC.optionalFieldOf("project_start_to_heightmap").forGetter((p_227644_) -> p_227644_.projectStartToHeightmap), Codec.intRange(1, 128).fieldOf("max_distance_from_center").forGetter((p_227642_) -> p_227642_.maxDistanceFromCenter)).apply(p_227640_, JigsawStructure::new)), JigsawStructure::verifyRange).codec();
   private final Holder startPool;
   private final Optional startJigsawName;
   private final int maxDepth;
   private final HeightProvider startHeight;
   private final boolean useExpansionHack;
   private final Optional projectStartToHeightmap;
   private final int maxDistanceFromCenter;

   private static DataResult verifyRange(JigsawStructure p_286886_) {
      byte var10000;
      switch (1.$SwitchMap$net$minecraft$world$level$levelgen$structure$TerrainAdjustment[p_286886_.terrainAdaptation().ordinal()]) {
         case 1:
            var10000 = 0;
            break;
         case 2:
         case 3:
         case 4:
            var10000 = 12;
            break;
         default:
            throw new IncompatibleClassChangeError();
      }

      int $$1 = var10000;
      return p_286886_.maxDistanceFromCenter + $$1 > 128 ? DataResult.error(() -> "Structure size including terrain adaptation must not exceed 128") : DataResult.success(p_286886_);
   }

   public JigsawStructure(Structure.StructureSettings p_227627_, Holder p_227628_, Optional p_227629_, int p_227630_, HeightProvider p_227631_, boolean p_227632_, Optional p_227633_, int p_227634_) {
      super(p_227627_);
      this.startPool = p_227628_;
      this.startJigsawName = p_227629_;
      this.maxDepth = p_227630_;
      this.startHeight = p_227631_;
      this.useExpansionHack = p_227632_;
      this.projectStartToHeightmap = p_227633_;
      this.maxDistanceFromCenter = p_227634_;
   }

   public JigsawStructure(Structure.StructureSettings p_227620_, Holder p_227621_, int p_227622_, HeightProvider p_227623_, boolean p_227624_, Heightmap.Types p_227625_) {
      this(p_227620_, p_227621_, Optional.empty(), p_227622_, p_227623_, p_227624_, Optional.of(p_227625_), 80);
   }

   public JigsawStructure(Structure.StructureSettings p_227614_, Holder p_227615_, int p_227616_, HeightProvider p_227617_, boolean p_227618_) {
      this(p_227614_, p_227615_, Optional.empty(), p_227616_, p_227617_, p_227618_, Optional.empty(), 80);
   }

   public Optional findGenerationPoint(Structure.GenerationContext p_227636_) {
      ChunkPos $$1 = p_227636_.chunkPos();
      int $$2 = this.startHeight.sample(p_227636_.random(), new WorldGenerationContext(p_227636_.chunkGenerator(), p_227636_.heightAccessor()));
      BlockPos $$3 = new BlockPos($$1.getMinBlockX(), $$2, $$1.getMinBlockZ());
      return JigsawPlacement.addPieces(p_227636_, this.startPool, this.startJigsawName, this.maxDepth, $$3, this.useExpansionHack, this.projectStartToHeightmap, this.maxDistanceFromCenter);
   }

   public StructureType type() {
      return StructureType.JIGSAW;
   }
}
