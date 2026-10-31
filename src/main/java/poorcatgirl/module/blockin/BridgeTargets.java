package poorcatgirl.module.blockin;

import java.util.ArrayList;
import java.util.List;

public final class BridgeTargets {
    public static final class Cell {
        public final int x,y,z;
        public Cell(int x,int y,int z) {this.x=x;this.y=y;this.z=z;}
        @Override public boolean equals(Object other) {
            if (!(other instanceof Cell)) return false;
            Cell cell=(Cell)other;
            return x==cell.x && y==cell.y && z==cell.z;
        }
        @Override public int hashCode() {return (x*31+y)*31+z;}
    }

    private BridgeTargets() {}

    public static boolean isDiagonalTravel(int travelX, int travelZ) {
        return travelX != 0 && travelZ != 0;
    }

    public static List<Cell> diagonal(int feetX,int feetZ,int y,int travelX,int travelZ) {
        List<Cell> targets=new ArrayList<Cell>();
        if (travelX==0 || travelZ==0) return targets;
        int stepX=travelX>0?1:-1;
        int stepZ=travelZ>0?1:-1;
        add(targets,feetX,y,feetZ);
        add(targets,feetX+stepX,y,feetZ);
        add(targets,feetX,y,feetZ+stepZ);
        add(targets,feetX+stepX,y,feetZ+stepZ);
        return targets;
    }

    private static void add(List<Cell> targets,int x,int y,int z) {
        Cell cell=new Cell(x,y,z);
        if (!targets.contains(cell)) targets.add(cell);
    }
}
