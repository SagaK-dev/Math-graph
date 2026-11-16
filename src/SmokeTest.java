import java.io.File;
import java.awt.image.BufferedImage;
import java.awt.geom.Point2D;
import javax.imageio.ImageIO;
import java.util.*;

/** Built-in non-GUI automated test and exportable demonstration. */
public final class SmokeTest {
    private static int checks=0;
    private static void require(boolean pass,String message){checks++;if(!pass)throw new AssertionError("TEST FAILED: "+message);}
    private static void close(double got,double expected,String name){require(Math.abs(got-expected)<1e-7,name+" expected="+expected+" actual="+got);}
    public static void run(File output)throws Exception{
        close(MathExpression.compile("2x+sin(pi/2)").eval(Map.of("x",3d)),7,"implicit multiplication and trig");
        close(MathExpression.compile("-2^2").eval(Map.of()),-4,"unary power precedence");
        close(MathExpression.compile("(-2)^2").eval(Map.of()),4,"parenthesized power");
        close(MathExpression.compile("2^-3").eval(Map.of()),.125,"negative exponent");
        close(MathExpression.compile("sqrt(9)+log(100)+abs(-2)").eval(Map.of()),7,"functions");
        close(MathExpression.compile("a*x^2").eval(Map.of("a",2d,"x",3d)),18,"slider variable");
        boolean throwsError=false;try{MathExpression.compile("foo(x)").eval(Map.of("x",1d));}catch(IllegalArgumentException ex){throwsError=true;}
        require(throwsError,"undefined variable correctly rejected");
        GeoModel m=new GeoModel();m.showGrid=true;m.showAxes=true;m.scale=48;
        GeoModel.Obj a=m.point(-3,-1);a.label="A";
        GeoModel.Obj b=m.point(3,-1);b.label="B";
        GeoModel.Obj c=m.point(0,3);c.label="C";
        m.line("segment",a.id,b.id);m.line("segment",b.id,c.id);m.line("segment",c.id,a.id);
        GeoModel.Obj polygon=m.polygon(List.of(a.id,b.id,c.id));polygon.showLabel=false;
        GeoModel.Obj mid=m.midpoint(a.id,b.id);mid.label="M";
        GeoModel.Obj altitude=m.derivedLine("perp",m.objects.get(3).id,c.id);altitude.showLabel=false;
        GeoModel.Obj segment=m.line("segment",c.id,mid.id);segment.color=0xC66B26;segment.showLabel=false;
        m.circle(c.id,mid.id);m.distance(a.id,b.id);m.angle(a.id,c.id,b.id);
        GeoModel.Obj circle=m.circleRadius(mid.id,1.5);circle.showLabel=false;
        m.slider("k",.5,-3,3,.1);
        GeoModel.Obj graph=m.function("f","0.2*x^2-3+k");graph.color=0xD64E4E;
        GeoModel.Obj wave=m.function("g","sin(x)-2");wave.color=0x1D9D84;
        m.text("三角形 ABC と関数",-6,5.2);
        Point2D.Double mp=m.pointOf(mid.id);close(mp.x,0,"midpoint before move x");close(mp.y,-1,"midpoint before move y");
        a.x=-5;mp=m.pointOf(mid.id);close(mp.x,-1,"midpoint follows vertex");a.x=-3;
        GeoModel.Obj la=m.line("line",a.id,c.id),lb=m.line("line",b.id,c.id);
        var intersections=m.intersections(la,lb);require(intersections.size()==1,"line intersection count");
        close(intersections.get(0).x,c.x,"line intersection x");close(intersections.get(0).y,c.y,"line intersection y");
        var intersection=m.intersection(la.id,lb.id,GeoModel.p(0,3));
        require(m.pointOf(intersection.id)!=null,"dynamic intersection");
        // Keep demonstration readable by hiding helper infinite lines and duplicated point.
        la.visible=false;lb.visible=false;intersection.visible=false;
        File project=new File(output.getParentFile()==null?new File("."):output.getParentFile(),"GeoSketch_sample.gsk");
        GeoModel.save(m,project);
        GeoModel loaded=GeoModel.load(project);
        require(loaded.objects.size()==m.objects.size(),"project round trip objects");
        require(loaded.showGrid==m.showGrid && loaded.showAxes==m.showAxes,"project round trip viewport flags");
        close(loaded.pointOf(mid.id).y,-1,"project round trip dynamic construction");
        require(loaded.get(graph.id).expression.equals("0.2*x^2-3+k"),"expression persists");
        GeoSketch.exportPNG(loaded,output,1400,900);
        BufferedImage im=ImageIO.read(output);
        require(im!=null&&im.getWidth()==1400&&im.getHeight()==900,"PNG export dimensions");
        int nonWhite=0;
        for(int y=100;y<800;y+=3)for(int x=200;x<1200;x+=3)if((im.getRGB(x,y)&0xFFFFFF)!=0xFFFFFF)nonWhite++;
        require(nonWhite>1000,"PNG contains rendered graphics");
        System.out.println("PASS: "+checks+" assertions");
        System.out.println("PNG: "+output.getAbsolutePath());
        System.out.println("PROJECT: "+project.getAbsolutePath());
    }
}
