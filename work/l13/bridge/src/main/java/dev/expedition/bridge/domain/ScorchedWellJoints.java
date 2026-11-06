package dev.expedition.bridge.domain;

import java.util.*;

/** Only the mandatory original well-to-hub graph; branch selection stays with the native generator. */
public final class ScorchedWellJoints {
    public record Joint(int x,int y,int z,String front,String top,String name,String target,String pool,String alignment){}
    public static List<Joint> forTemplate(String id) {
        if(!id.startsWith(ScorchedWellLayout.PREFIX))return List.of();
        return switch(id.substring(ScorchedWellLayout.PREFIX.length())) {
            case "well_0"->List.of(well(5,0,5,"down"));
            case "hub_part_0"->List.of(side(1,1,23,"south",2,3),side(23,1,1,"east",0,1));
            case "hub_part_1"->List.of(side(0,1,1,"west",1,0),side(23,1,23,"south",7,6));
            case "hub_part_2"->List.of(side(0,1,23,"west",5,4),side(23,1,0,"north",6,7),well(0,25,0,"up"));
            case "hub_part_3"->List.of(side(1,1,0,"north",3,2),side(23,1,23,"east",4,5));
            default->List.of();
        };
    }
    private static Joint side(int x,int y,int z,String front,int name,int target){return new Joint(x,y,z,front,"up","dungeons_arise:scorched_mines_side_"+name,"dungeons_arise:scorched_mines_side_"+target,"dungeons_arise:underworld/scorched_mines/scorched_mines_hub","aligned");}
    private static Joint well(int x,int y,int z,String front){return new Joint(x,y,z,front,"west","dungeons_arise:scorched_mines_well","dungeons_arise:scorched_mines_well","dungeons_arise:underworld/scorched_mines/scorched_mines_well","aligned");}
}
