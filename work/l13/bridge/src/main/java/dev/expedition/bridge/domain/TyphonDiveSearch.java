package dev.expedition.bridge.domain;

import dev.expedition.bridge.domain.BoardingRouteSearch.Cell;
import dev.expedition.bridge.domain.InteriorBlockRules.Type;
import java.util.*;

/** Read-only dry-bank and underwater clearance checks; never removes drowning or magma hazards. */
public final class TyphonDiveSearch {
    public interface Probe extends InteriorSiteSearch.Probe {
        boolean water(Cell p);boolean magma(Cell p);boolean chest(Cell p);boolean stone(Cell p);
    }
    private enum Kind { PLATFORM, STAND, AIR, WATER, MAGMA, CHEST, STONE }
    private record Check(Cell cell,Kind kind) {}
    private final Probe world;
    private final List<Check> checks;
    private final List<Cell> shores;
    private int cursor;
    private boolean failed;
    public TyphonDiveSearch(TyphonDiveLayout layout,IslandPlan island,int count,Probe world) {
        this.world=Objects.requireNonNull(world);shores=List.copyOf(layout.shores(island,count));var checks=new LinkedHashSet<Check>();
        layout.magma().forEach(p->checks.add(new Check(p,Kind.MAGMA)));layout.chests().forEach(p->checks.add(new Check(p,Kind.CHEST)));
        for(int i=0;i<shores.size();i++) {
            var shore=shores.get(i);var d=layout.inward(i);var bank=shore.move(d.x(),0,d.z());var water=bank.move(d.x(),0,d.z());
            checks.add(new Check(shore,Kind.PLATFORM));checks.add(new Check(bank,Kind.STAND));
            checks.add(new Check(water,Kind.AIR));checks.add(new Check(water.move(0,1,0),Kind.AIR));
            // Entire outside descent/ascent column, with two-cell body room at the bottom.
            for(int y=layout.root().origin().y()+7;y<island.firstFreeY();y++)checks.add(new Check(new Cell(water.x(),y,water.z()),Kind.WATER));
        }
        // Original start-piece exterior, adjacent to its stone face, before either treasure location.
        for(int z=-1;z<=3;z++)for(int y=7;y<=8;y++)checks.add(new Check(layout.root().at(24,y,z),Kind.WATER));
        checks.add(new Check(layout.root().at(24,7,4),Kind.STONE));this.checks=List.copyOf(checks);
    }
    public boolean complete(){return failed||cursor==checks.size();}
    public List<Cell> result(){return complete()&&!failed?shores:List.of();}
    public void step(int budget) {
        if(budget<=0)throw new IllegalArgumentException("Invalid dive inspection budget");
        while(budget-->0&&!complete()) {
            var c=checks.get(cursor++);var p=c.cell();boolean ok=switch(c.kind()) {
                case PLATFORM->InteriorSiteSearch.clearPlatform(p,world);
                case STAND->world.at(p.move(0,-1,0))==Type.SUPPORT && world.at(p)==Type.AIR && world.at(p.move(0,1,0))==Type.AIR && world.at(p.move(0,2,0))==Type.AIR;
                case AIR->world.at(p)==Type.AIR;
                case WATER->world.water(p);
                case MAGMA->world.magma(p);
                case CHEST->world.chest(p);
                case STONE->world.stone(p);
            };if(!ok)failed=true;
        }
    }
}
