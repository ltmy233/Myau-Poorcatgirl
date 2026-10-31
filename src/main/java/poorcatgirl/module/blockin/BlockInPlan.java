package poorcatgirl.module.blockin;

import java.util.*;

public final class BlockInPlan {
    public enum Phase { PAUSED, ENCLOSE, READY, DIG, MANUAL, DEFEND, REPAIR }
    public static final class Cell {
        public final int x, y, z;
        public Cell(int x, int y, int z) { this.x=x; this.y=y; this.z=z; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Cell)) return false;
            Cell c=(Cell)o; return x==c.x && y==c.y && z==c.z;
        }
        @Override public int hashCode() { return (x*31+y)*31+z; }
    }
    private final Set<Cell> shell=new LinkedHashSet<>(), floor=new LinkedHashSet<>();
    private final Set<Cell> protectedOpenings=new HashSet<>(), solid=new HashSet<>(), confirmed=new HashSet<>();
    private final Set<Cell> breached=new HashSet<>();
    private boolean completed, defensive;

    public BlockInPlan(int minX,int minY,int minZ,int maxX,int maxY,int maxZ) {
        for (int x=minX;x<=maxX;x++) for (int z=minZ;z<=maxZ;z++) {
            floor.add(new Cell(x,minY-1,z));
        }
        for (int y=minY;y<=maxY;y++) {
            for (int x=minX;x<=maxX;x++) {
                shell.add(new Cell(x,y,minZ-1));shell.add(new Cell(x,y,maxZ+1));
            }
            for (int z=minZ;z<=maxZ;z++) {
                shell.add(new Cell(minX-1,y,z));shell.add(new Cell(maxX+1,y,z));
            }
        }
    }
    public Set<Cell> shell() { return Collections.unmodifiableSet(shell); }
    public Set<Cell> floor() { return Collections.unmodifiableSet(floor); }
    public void protect(Cell c) { protectedOpenings.add(c); breached.remove(c); }
    public boolean isProtected(Cell c) { return protectedOpenings.contains(c); }
    public boolean isComplete() { return missing().isEmpty(); }
    public void observe(Set<Cell> currentSolid,Set<Cell> serverConfirmed) {
        if (completed) for (Cell c:confirmed)
            if (!currentSolid.contains(c) && !isProtected(c)) breached.add(c);
        solid.clear();solid.addAll(currentSolid);
        confirmed.clear();confirmed.addAll(serverConfirmed);confirmed.retainAll(solid);
        breached.removeIf(c -> isProtected(c) || confirmed.contains(c));
    }
    public Set<Cell> missing() {
        Set<Cell> result=new LinkedHashSet<>(shell);
        result.removeAll(confirmed);result.removeAll(protectedOpenings);return result;
    }
    public Phase next(boolean valid,boolean manual,boolean autoMine,boolean nearbyEnemy,boolean attackable) {
        return next(valid,manual,autoMine,nearbyEnemy,attackable,true);
    }
    public Phase next(boolean valid,boolean manual,boolean autoMine,boolean nearbyEnemy,boolean attackable,boolean canPlace) {
        if (!valid) return Phase.PAUSED;
        if (completed && !breached.isEmpty() && nearbyEnemy) defensive=true;
        if (defensive && nearbyEnemy && attackable) return Phase.DEFEND;
        if (!canPlace) {
            if(defensive && nearbyEnemy)return Phase.READY;
            defensive=false;
            return manual || !autoMine ? Phase.MANUAL : Phase.DIG;
        }
        if (defensive && !isComplete()) return Phase.REPAIR;
        if (defensive && nearbyEnemy) return Phase.READY;
        if (manual) return Phase.MANUAL;
        if (!isComplete()) return completed ? Phase.REPAIR : Phase.ENCLOSE;
        completed=true;
        if (defensive && nearbyEnemy) return Phase.READY;
        defensive=false;
        return autoMine ? Phase.DIG : Phase.MANUAL;
    }
    /** material IDs: 1 wool, 2 planks, 3 end stone, 4 clay, 5 obsidian; 0 mode means automatic. */
    public static int selectMaterial(int mode,int[] materials,float[] hardness,int[] counts) {
        int best=-1;float strength=-1;
        for (int i=0;i<materials.length;i++) {
            if (materials[i]<1 || materials[i]>5 || counts[i]<=0 || hardness[i]<0 || !Float.isFinite(hardness[i])) continue;
            if (mode!=0 && materials[i]!=mode) continue;
            if (best<0 || hardness[i]>strength) {best=i;strength=hardness[i];}
        }
        return best;
    }
}
