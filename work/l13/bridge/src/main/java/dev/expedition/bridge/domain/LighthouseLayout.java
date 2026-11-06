package dev.expedition.bridge.domain;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import java.util.List;

/** Transform and native graph contract for the two original lighthouse templates. */
public record LighthouseLayout(Cell origin, int turns) {
    public static final String TARGET = "dungeons_arise:lighthouse";
    public static final String BASE = "dungeons_arise:lighthouse/lighthouse_part_1";
    public static final String TOWER = "dungeons_arise:lighthouse/lighthouse_part_2";

    public record Piece(String id, Cell origin, int turns, BlockBounds bounds) {}

    public LighthouseLayout {
        if (turns < 0 || turns > 3) throw new IllegalArgumentException("Invalid lighthouse rotation");
    }

    public static LighthouseLayout inspect(List<Piece> pieces, int planningSurface, BlockBounds complete) {
        if (pieces.size() != 2) throw new IllegalArgumentException("Lighthouse needs both original pieces");
        var base = pieces.get(0);
        var tower = pieces.get(1);
        var layout = new LighthouseLayout(base.origin(), base.turns());
        if (!base.id().equals(BASE) || !tower.id().equals(TOWER) || tower.turns() != layout.turns()
                || !tower.origin().equals(layout.at(0, 31, 0))) {
            throw new IllegalArgumentException("Incorrect lighthouse root or disconnected tower");
        }
        if (layout.groundY() != planningSurface - 2 || !base.bounds().equals(layout.bounds(0, 30))
                || !tower.bounds().equals(layout.bounds(31, 67)) || !complete.equals(layout.bounds(0, 67))) {
            throw new IllegalArgumentException("Lighthouse height or original placement envelope changed");
        }
        return layout;
    }

    public static void validateEnvironment(IslandPlan.Surface surface, DungeonSpec.EntryMode mode, boolean boarding) {
        if ((surface != IslandPlan.Surface.GRASS && surface != IslandPlan.Surface.ROCK)
                || mode != DungeonSpec.EntryMode.PERIMETER || boarding) {
            throw new IllegalArgumentException("Lighthouse requires dry ground with perimeter entries");
        }
    }

    public int groundY() { return origin.y(); }

    public Cell at(int x, int y, int z) {
        return switch (turns) {
            case 0 -> origin.move(x, y, z);
            case 1 -> origin.move(-z, y, x);
            case 2 -> origin.move(-x, y, -z);
            default -> origin.move(z, y, -x);
        };
    }

    private BlockBounds bounds(int bottom, int top) {
        var a = at(0, bottom, 0);
        var b = at(30, top, 30);
        return new BlockBounds(Math.min(a.x(), b.x()), a.y(), Math.min(a.z(), b.z()),
                Math.max(a.x(), b.x()), b.y(), Math.max(a.z(), b.z()));
    }

    public LighthouseLayout move(int x, int y, int z) {
        return new LighthouseLayout(origin.move(x, y, z), turns);
    }
}
