package dev.expedition.bridge.domain;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import java.util.*;

/** All three native seabed pieces. The catalog supplies the water depth. */
public record TyphonDiveLayout(Piece root, List<Cell> magma, List<Cell> chests) {
    public static final String TARGET = "dungeons_arise:typhon";
    public static final String PREFIX = "dungeons_arise:typhon/typhon_part_";
    private static final List<String> PARTS = List.of(PREFIX + "0", PREFIX + "1", PREFIX + "2");
    private static final List<Integer> MAGMA_COUNTS = List.of(47, 49, 28);

    public record Piece(String template, Cell origin, int rotation, BlockBounds bounds,
                        List<Cell> magma, List<Cell> chests) {
        public Piece {
            magma = List.copyOf(magma);
            chests = List.copyOf(chests);
            if (rotation < 0 || rotation > 3) throw new IllegalArgumentException("Invalid Typhon rotation");
        }

        public Cell at(int x, int y, int z) {
            return switch (rotation) {
                case 0 -> origin.move(x, y, z);
                case 1 -> origin.move(-z, y, x);
                case 2 -> origin.move(-x, y, -z);
                default -> origin.move(z, y, -x);
            };
        }

        public BlockBounds expectedBounds() {
            var corner = at(47, 47, 47);
            return new BlockBounds(Math.min(origin.x(), corner.x()), origin.y(), Math.min(origin.z(), corner.z()),
                    Math.max(origin.x(), corner.x()), corner.y(), Math.max(origin.z(), corner.z()));
        }

        public Piece move(int x, int y, int z) {
            return new Piece(template, origin.move(x, y, z), rotation, bounds.move(x, y, z),
                    translate(magma, x, y, z), translate(chests, x, y, z));
        }
    }

    public record Joint(int x, String front, int name, int target) {}

    public static List<Joint> joints(String template) {
        return switch (template) {
            case PREFIX + "0" -> List.of(new Joint(47, "east", 0, 1));
            case PREFIX + "1" -> List.of(new Joint(0, "west", 1, 0), new Joint(47, "east", 2, 3));
            case PREFIX + "2" -> List.of(new Joint(0, "west", 3, 2));
            default -> throw new IllegalArgumentException("Unknown Typhon template: " + template);
        };
    }

    public TyphonDiveLayout {
        magma = List.copyOf(magma);
        chests = List.copyOf(chests);
    }

    public static void validateMode(String target, DungeonSpec.EntryMode mode) {
        if (target.equals(TARGET) != (mode == DungeonSpec.EntryMode.SHORE_DIVE)) {
            throw new IllegalArgumentException("Typhon requires its original seabed shore approach");
        }
    }

    public static TyphonDiveLayout fromPieces(List<Piece> pieces, IslandPlan island) {
        if (pieces.size() != 3 || !pieces.get(0).template().equals(PARTS.get(1))) {
            throw new IllegalArgumentException("Missing original Typhon start or complete body");
        }
        var root = pieces.get(0);
        var seen = new HashSet<String>();
        var magma = new ArrayList<Cell>();
        var chests = new ArrayList<Cell>();
        var bounds = root.bounds();
        for (var piece : pieces) {
            int index = PARTS.indexOf(piece.template());
            if (index < 0 || !seen.add(piece.template()) || piece.rotation() != root.rotation()
                    || !piece.origin().equals(root.at((index - 1) * 48, 0, 0))
                    || !piece.bounds().equals(piece.expectedBounds())) {
                throw new IllegalArgumentException("Incomplete, moved or unknown Typhon piece: " + piece.template());
            }
            List<Cell> expectedChests = switch (index) {
                case 0 -> List.of(piece.at(39, 1, 24));
                case 1 -> List.of(piece.at(28, 7, 24));
                default -> List.of();
            };
            if (piece.magma().size() != MAGMA_COUNTS.get(index) || !piece.chests().equals(expectedChests)) {
                throw new IllegalArgumentException("Changed original Typhon hazards or treasure positions");
            }
            for (var cell : piece.magma()) {
                if (!piece.bounds().contains(cell.x(), cell.y(), cell.z())) {
                    throw new IllegalArgumentException("Magma outside native piece");
                }
            }
            magma.addAll(piece.magma());
            chests.addAll(piece.chests());
            bounds = IslandPlan.union(bounds, piece.bounds());
        }
        if (new HashSet<>(magma).size() != magma.size() || !bounds.equals(island.building())) {
            throw new IllegalArgumentException("Overlapping hazards or incomplete native bounds");
        }
        var layout = new TyphonDiveLayout(root, magma, chests);
        layout.validateIsland(island);
        return layout;
    }

    public void validateIsland(IslandPlan island) {
        // Native OCEAN_FLOOR -4 and groundLevelDelta 1 put the root five blocks below the seabed.
        if (island.surface() != IslandPlan.Surface.WATER || island.levels().solidFirstFreeY() != root.origin().y() + 5) {
            throw new IllegalArgumentException("Typhon must keep its original seabed offset");
        }
        if (island.firstFreeY() <= root.at(24, 8, 3).y()) {
            throw new IllegalArgumentException("Water must cover both cells of the original exterior dive approach");
        }
    }

    public List<Cell> shores(IslandPlan island, int count) {
        validateIsland(island);
        if (count < 1 || count > 4) throw new IllegalArgumentException("Invalid shore exit count");
        int y = island.firstFreeY() - root.origin().y();
        return List.of(root.at(24, y, -3), root.at(24, y, 50), root.at(-51, y, 24), root.at(98, y, 24))
                .subList(0, count);
    }

    public Cell inward(int side) {
        var next = switch (side) {
            case 0 -> root.at(0, 0, 1);
            case 1 -> root.at(0, 0, -1);
            case 2 -> root.at(1, 0, 0);
            case 3 -> root.at(-1, 0, 0);
            default -> throw new IllegalArgumentException("Unknown shore side");
        };
        return new Cell(next.x() - root.origin().x(), 0, next.z() - root.origin().z());
    }

    public TyphonDiveLayout move(int x, int y, int z) {
        return new TyphonDiveLayout(root.move(x, y, z), translate(magma, x, y, z), translate(chests, x, y, z));
    }

    private static List<Cell> translate(List<Cell> cells, int x, int y, int z) {
        return cells.stream().map(cell -> cell.move(x, y, z)).toList();
    }
}
