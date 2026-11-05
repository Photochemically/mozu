package dev.expedition.bridge.domain;

/** Reject known unresolved entry mechanisms before allocation. Never substitute or erase the original building. */
public final class AerialContentPolicy {
    private AerialContentPolicy(){}
    public static void validate(String id,String generator,boolean projectsToSurface) {
        if(!generator.equals("minecraft:jigsaw") || projectsToSurface)
            throw new IllegalArgumentException("Void placement requires a jigsaw without surface projection; original definition retained");
        if(id.equals("dungeons_arise:mechanical_nest"))
            throw new IllegalArgumentException("Mechanical nest needs an original bridge-side entry outside its piston gates; adapter pending, no gate bypass or removal");
    }
}
