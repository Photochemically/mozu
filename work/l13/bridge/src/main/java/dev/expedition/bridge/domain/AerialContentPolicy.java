package dev.expedition.bridge.domain;

/** Reject unsupported generators. Mechanical nests additionally require the positioned bridge adapter. */
public final class AerialContentPolicy {
    private AerialContentPolicy(){}
    public static boolean needsGateBridge(String id){return id.equals("dungeons_arise:mechanical_nest");}
    public static void validate(String id,String generator,boolean projectsToSurface) {
        if(!generator.equals("minecraft:jigsaw") || projectsToSurface)
            throw new IllegalArgumentException("Void placement requires a jigsaw without surface projection; original definition retained");
    }
}
