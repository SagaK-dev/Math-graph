import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;

/** Regression tests for regular polygons, exact constructed angles, labels and PNG export. */
public final class GeometryTest {
    private static int checks=0;
    private static void yes(boolean test,String description){checks++;if(!test)throw new AssertionError(description);}
    private static boolean approx(double a,double b,double eps){return Math.abs(a-b)<eps;}
    private static double angle(Point2D.Double a,Point2D.Double b,Point2D.Double c){
        double ux=a.x-b.x,uy=a.y-b.y,vx=c.x-b.x,vy=c.y-b.y;
        return Math.toDegrees(Math.atan2(ux*vy-uy*vx,ux*vx+uy*vy));
    }
    private static int differences(BufferedImage a,BufferedImage b){
        yes(a.getWidth()==b.getWidth()&&a.getHeight()==b.getHeight(),"images same size");
        int changed=0;
        for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++)if(a.getRGB(x,y)!=b.getRGB(x,y))changed++;
        return changed;
    }
    private static void polygonTest(int n,boolean clockwise)throws Exception{
        GeoModel m=new GeoModel();GeoModel.Obj p=m.point(-1,0),q=m.point(1,0);
        GeoModel.Obj poly=m.regularPolygon(p.id,q.id,n,clockwise);
        yes(poly.points.length==n,"regular polygon n="+n);
        yes(poly.mode.equals("regular"),"regular object mode");
        yes(poly.label.equals("正"+n+"角形"),"Japanese polygon label");
        double edge=m.pointOf(poly.points[0]).distance(m.pointOf(poly.points[1]));
        for(int i=0;i<n;i++){
            Point2D.Double a=m.pointOf(poly.points[i]),b=m.pointOf(poly.points[(i+1)%n]);
            yes(a!=null&&b!=null,"polygon vertex resolves");
            yes(approx(a.distance(b),edge,1e-7),"equal length all sides: n="+n+", i="+i);
        }
        double signed=angle(m.pointOf(poly.points[0]),m.pointOf(poly.points[1]),m.pointOf(poly.points[2]));
        yes(approx(Math.abs(signed),180-360.0/n,1e-7),"equal interior angle");
        // Drag either source point: all dependent vertices must update and remain regular.
        p.y=-.8;q.x=2.3;q.y=.7;
        edge=m.pointOf(poly.points[0]).distance(m.pointOf(poly.points[1]));
        for(int i=0;i<n;i++){
            Point2D.Double a=m.pointOf(poly.points[i]),b=m.pointOf(poly.points[(i+1)%n]);
            yes(approx(a.distance(b),edge,1e-7),"dynamic regular polygon after dragging");
        }
        File temp=File.createTempFile("regular-geometry-", ".gsk");
        try {
            GeoModel.save(m,temp);GeoModel loaded=GeoModel.load(temp);
            yes(loaded.get(poly.id).points.length==n,"polygon point references preserved after reload");
            for(int i=0;i<n;i++){
                Point2D.Double a=loaded.pointOf(poly.points[i]),b=loaded.pointOf(poly.points[(i+1)%n]);
                yes(approx(a.distance(b),edge,1e-7),"saved polygon equal side length");
            }
        } finally {temp.delete();}
    }
    private static void expectException(Runnable action,String name){
        boolean threw=false;try{action.run();}catch(IllegalArgumentException expected){threw=true;}
        yes(threw,name);
    }
    public static void run(File output)throws Exception{
        for(int n:new int[]{3,4,5,6,12,60}){
            polygonTest(n,false);polygonTest(n,true);
        }
        GeoModel m=new GeoModel();GeoModel.Obj a=m.point(1,0),b=m.point(0,0);
        GeoModel.Obj fixed=m.fixedAngle(a.id,b.id,75.5,false,true);
        yes(fixed.mode.equals("fixed"),"angle has fixed mode");
        yes(fixed.showLabel,"angle value shown by default selected");
        yes(approx(m.measurement(fixed),75.5,1e-9),"exact requested angle 75.5");
        Point2D.Double end=m.pointOf(fixed.c);
        yes(approx(m.pointOf(b.id).distance(m.pointOf(a.id)),m.pointOf(b.id).distance(end),1e-9),"angle rays same length");
        yes(approx(angle(m.pointOf(fixed.a),m.pointOf(fixed.b),m.pointOf(fixed.c)),75.5,1e-8),"angle geometric direction ccw");
        m.setFixedAngleDegrees(fixed,225.25,false);
        yes(approx(m.measurement(fixed),225.25,1e-9),"reflex angle measures 225.25 not 134.75");
        double oriented=angle(m.pointOf(fixed.a),m.pointOf(fixed.b),m.pointOf(fixed.c));
        yes(approx(oriented,-134.75,1e-8),"reflex angle's second ray orientation");
        m.setFixedAngleDegrees(fixed,115.2,true);
        yes(approx(m.fixedAngleSignedDegrees(fixed),-115.2,1e-9),"clockwise sign retained");
        yes(approx(m.measurement(fixed),115.2,1e-9),"clockwise angle measure");
        yes(approx(angle(m.pointOf(fixed.a),m.pointOf(fixed.b),m.pointOf(fixed.c)),-115.2,1e-8),"clockwise geometry");
        a.x=2.8;a.y=1.5;b.y=-.3;
        yes(approx(m.measurement(fixed),115.2,1e-9),"fixed angle constant after dragging");
        yes(approx(Math.abs(angle(m.pointOf(fixed.a),m.pointOf(fixed.b),m.pointOf(fixed.c))),115.2,1e-8),"geometry constant after dragging");
        File temp=File.createTempFile("angle-geometry-", ".gsk");
        try {
            fixed.showLabel=false;GeoModel.save(m,temp);GeoModel loaded=GeoModel.load(temp);
            GeoModel.Obj round=loaded.get(fixed.id);
            yes(!round.showLabel,"angle label option saved and reloaded");
            yes(approx(loaded.fixedAngleSignedDegrees(round),-115.2,1e-9),"angle numeric and direction saved");
            yes(approx(loaded.measurement(round),115.2,1e-9),"angle still exact after reopen");
        } finally {temp.delete();}
        expectException(()->m.regularPolygon(a.id,b.id,2,false),"sides below 3 refused");
        expectException(()->m.regularPolygon(a.id,b.id,61,false),"sides over 60 refused");
        expectException(()->m.fixedAngle(a.id,b.id,0,false,true),"zero degrees refused");
        expectException(()->m.fixedAngle(a.id,b.id,360,false,true),"360 degrees refused");
        expectException(()->m.setFixedAngleDegrees(fixed,Double.NaN,false),"NaN refused");

        // Render sample including a regular hexagon, hatch lines, exact 65-degree arc and figure caption.
        GeoModel picture=new GeoModel();picture.showGrid=false;picture.showAxes=false;
        GeoModel.Obj x=picture.point(-4.1,-.9),y=picture.point(-2.2,-.9);
        GeoModel.Obj hex=picture.regularPolygon(x.id,y.id,6,false);
        hex.filled=true;hex.fillStyle="hatch";hex.color=0x54748C;hex.strokeWidth=2.7;
        GeoModel.Obj pa=picture.point(4.55,-.9),pb=picture.point(1.9,-.9);
        GeoModel.Obj arc=picture.fixedAngle(pa.id,pb.id,65,false,true);
        arc.color=0xA44B3C;arc.strokeWidth=2.6;
        picture.setCrop(-5.3,-2.1,5.6,3.75);
        PNGExport.save(picture,output,1400,0,true,"図1　正六角形と65°の角","中央下",0);
        BufferedImage withNumbers=ImageIO.read(output);
        yes(withNumbers!=null&&withNumbers.getWidth()==1400,"PNG created with requested width");
        yes(withNumbers.getHeight()==PNGExport.croppedHeight(picture,1400),"PNG crop aspect respected");
        BufferedImage hiddenLabels=PNGExport.render(picture,1400,0,true,"図1　正六角形と65°の角","中央下",0);
        arc.showLabel=false;
        BufferedImage noAngleValue=PNGExport.render(picture,1400,0,true,"図1　正六角形と65°の角","中央下",0);
        yes(differences(withNumbers,noAngleValue)>20,"PNG angle number toggles off");
        yes(differences(hiddenLabels,noAngleValue)>20,"PNG arc stays but number removed");
        File sample=new File(output.getParentFile()==null?new File("."):output.getParentFile(),"GeoSketch_v1_2_sample.gsk");
        arc.showLabel=true;GeoModel.save(picture,sample);
        GeoModel loaded=GeoModel.load(sample);
        yes(approx(loaded.measurement(loaded.get(arc.id)),65,1e-8),"example loaded with accurate fixed angle");
        yes(loaded.get(hex.id).points.length==6,"example loaded with regular hexagon");
        System.out.println("PASS: "+checks+" regular polygon / angle / PNG assertions");
        System.out.println("PNG: "+output.getAbsolutePath());
        System.out.println("PROJECT: "+sample.getAbsolutePath());
    }
}
