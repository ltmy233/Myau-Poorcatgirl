package poorcatgirl.module.blockin;

import java.util.Set;
import poorcatgirl.module.blockin.BlockInPlan.Cell;

public final class BlockInRoute {
    public interface Ray { Hit trace(double ex,double ey,double ez,double tx,double ty,double tz); }
    public static final class Hit {
        public final Cell cell;
        public final double x,y,z;
        public Hit(Cell cell,double x,double y,double z) {this.cell=cell;this.x=x;this.y=y;this.z=z;}
    }
    private BlockInRoute() {}
    public static Hit find(double ex,double ey,double ez,Cell bed,double reach,Set<Cell> excluded,Ray ray) {
        Hit best=null;double score=Double.MAX_VALUE;
        double[] offsets={.5,.2,.8};
        for (double dx:offsets) for(double dz:offsets) {
            Hit h=ray.trace(ex,ey,ez,bed.x+dx,bed.y+.25,bed.z+dz);
            if(h==null || excluded.contains(h.cell))continue;
            double dist=(h.x-ex)*(h.x-ex)+(h.y-ey)*(h.y-ey)+(h.z-ez)*(h.z-ez);
            if(dist>reach*reach)continue;
            double rank=dist+(h.cell.equals(bed)?0:reach*reach+1);
            if(rank<score){best=h;score=rank;}
        }
        return best;
    }
}
