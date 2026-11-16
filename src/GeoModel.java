import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;

/** Mutable document model; all geometry is stored as dependency IDs, not screen positions. */
public final class GeoModel {
    public enum Kind { POINT, LINE, CIRCLE, POLYGON, FUNCTION, TEXT, DISTANCE, ANGLE, SLIDER }
    public static class Obj {
        public int id, color=0x276DB4, a=-1,b=-1,c=-1,base=-1;
        public Kind kind;
        public String label="", mode="", expression="";
        public boolean visible=true, showLabel=true, filled=false;
        public double strokeWidth=2.0;
        public String lineStyle="solid", fillStyle="light";
        public double x=0,y=0,radius=1,min=-5,max=5,step=.1,value=1,angleDegrees=0;
        public int[] points=new int[0];
        public Obj(Kind kind) { this.kind=kind; }
        public Obj copy() {
            Obj o=new Obj(kind); o.id=id;o.color=color;o.a=a;o.b=b;o.c=c;o.base=base;
            o.label=label;o.mode=mode;o.expression=expression;o.visible=visible;o.showLabel=showLabel;
            o.filled=filled;o.strokeWidth=strokeWidth;o.lineStyle=lineStyle;o.fillStyle=fillStyle;o.x=x;o.y=y;o.radius=radius;o.min=min;o.max=max;o.step=step;o.value=value;o.angleDegrees=angleDegrees;
            o.points=points.clone(); return o;
        }
        public Color awtColor() { return new Color(color & 0xFFFFFF); }
    }
    public final ArrayList<Obj> objects=new ArrayList<>();
    public boolean showGrid=true,showAxes=true,showAxisNumbers=true,snapGrid=false;
    public boolean hasCrop=false;
    public double cropMinX=0,cropMinY=0,cropMaxX=0,cropMaxY=0;
    public void setCrop(double x1,double y1,double x2,double y2){
        if(!Double.isFinite(x1)||!Double.isFinite(x2)||!Double.isFinite(y1)||!Double.isFinite(y2))throw new IllegalArgumentException("切り抜き範囲が不正です");
        cropMinX=Math.min(x1,x2);cropMaxX=Math.max(x1,x2);cropMinY=Math.min(y1,y2);cropMaxY=Math.max(y1,y2);
        if(cropMaxX-cropMinX<1e-6||cropMaxY-cropMinY<1e-6)throw new IllegalArgumentException("切り抜き範囲が小さすぎます");
        hasCrop=true;
    }
    public double centerX=0,centerY=0,scale=60;
    private int nextId=1,pointCounter=0,curveCounter=0;
    public GeoModel copy() {
        GeoModel m=new GeoModel();
        for(Obj o:objects)m.objects.add(o.copy());
        m.showGrid=showGrid;m.showAxes=showAxes;m.showAxisNumbers=showAxisNumbers;m.snapGrid=snapGrid;
        m.hasCrop=hasCrop;m.cropMinX=cropMinX;m.cropMinY=cropMinY;m.cropMaxX=cropMaxX;m.cropMaxY=cropMaxY;
        m.centerX=centerX;m.centerY=centerY;m.scale=scale;
        m.nextId=nextId;m.pointCounter=pointCounter;m.curveCounter=curveCounter;
        return m;
    }
    public Obj get(int id) { for(Obj o:objects) if(o.id==id) return o; return null; }
    public Obj add(Obj o) { o.id=nextId++; objects.add(o); return o; }
    public Obj point(double x,double y) { Obj o=new Obj(Kind.POINT);o.x=x;o.y=y;o.label=pointName(pointCounter++);return add(o); }
    public Obj midpoint(int a,int b) { Obj o=new Obj(Kind.POINT);o.mode="mid";o.a=a;o.b=b;o.label=pointName(pointCounter++);o.color=0xC65C2C;return add(o); }
    /** A derived point rotated about an existing center by the given signed degrees. */
    public Obj rotatedPoint(int center,int reference,double signedDegrees){
        if(!Double.isFinite(signedDegrees)||Math.abs(signedDegrees)>=360)throw new IllegalArgumentException("角度は -360° より大きく 360° より小さくしてください");
        Point2D.Double c=pointOf(center),p=pointOf(reference);
        if(c==null||p==null||c.distance(p)<1e-8)throw new IllegalArgumentException("異なる2点を選んでください");
        Obj o=new Obj(Kind.POINT);o.mode="rotate";o.a=center;o.b=reference;o.angleDegrees=signedDegrees;
        o.color=0xC65C2C;o.label=pointName(pointCounter++);return add(o);
    }
    /** Vertex k of a regular n-gon, computed directly from the first two anchors (O(1)). */
    public Obj stepRotatedPoint(int first,int second,double signedDegrees,int vertexIndex){
        if(!Double.isFinite(signedDegrees)||Math.abs(signedDegrees)>=360||vertexIndex<2)
            throw new IllegalArgumentException("回転角または頂点番号が不正です");
        Obj o=new Obj(Kind.POINT);o.mode="stepRotation";o.a=first;o.b=second;
        o.angleDegrees=signedDegrees;o.value=vertexIndex;
        o.color=0xC65C2C;o.label=pointName(pointCounter++);return add(o);
    }
    /** Construct a regular n-gon using two consecutive vertices, dynamically tied to both. */
    public Obj regularPolygon(int first,int second,int sides,boolean clockwise){
        if(sides<3||sides>60)throw new IllegalArgumentException("辺の数は3〜60にしてください");
        Point2D.Double a=pointOf(first),b=pointOf(second);
        if(a==null||b==null||a.distance(b)<1e-7)throw new IllegalArgumentException("離れた2点を指定してください");
        ArrayList<Integer> ids=new ArrayList<>();ids.add(first);ids.add(second);
        double exterior=(clockwise?-1:1)*360.0/sides;
        for(int i=2;i<sides;i++){
            Obj o=stepRotatedPoint(first,second,exterior,i);
            o.showLabel=sides<=12;ids.add(o.id);
        }
        Obj polygon=polygon(ids);polygon.mode="regular";polygon.value=sides;
        polygon.label="正"+sides+"角形";return polygon;
    }
    /** Create an exact-angle figure; the angle is the directed reflex angle when >180°. */
    public Obj fixedAngle(int from,int vertex,double degrees,boolean clockwise,boolean showValue){
        if(!Double.isFinite(degrees)||degrees<.1||degrees>359.9)throw new IllegalArgumentException("角度は0.1〜359.9°にしてください");
        Point2D.Double a=pointOf(from),b=pointOf(vertex);
        if(a==null||b==null||a.distance(b)<1e-7)throw new IllegalArgumentException("離れた2点を指定してください");
        Obj end=rotatedPoint(vertex,from,(clockwise?-1:1)*degrees);
        Obj firstSide=line("segment",vertex,from);
        Obj secondSide=line("segment",vertex,end.id);
        firstSide.color=0x345C93;secondSide.color=0x345C93;
        Obj angle=angle(from,vertex,end.id);angle.mode="fixed";angle.showLabel=showValue;
        angle.label="指定角"+angle.id;return angle;
    }
    public Obj fixedAngleEndpoint(Obj angle){
        if(angle==null||angle.kind!=Kind.ANGLE||!"fixed".equals(angle.mode))return null;
        Obj end=get(angle.c);
        return end!=null&&end.kind==Kind.POINT&&"rotate".equals(end.mode)?end:null;
    }
    public double fixedAngleSignedDegrees(Obj angle){
        Obj end=fixedAngleEndpoint(angle);return end==null?Double.NaN:end.angleDegrees;
    }
    public void setFixedAngleDegrees(Obj angle,double degrees,boolean clockwise){
        if(!Double.isFinite(degrees)||degrees<.1||degrees>359.9)throw new IllegalArgumentException("角度は0.1〜359.9°にしてください");
        Obj end=fixedAngleEndpoint(angle);
        if(end==null)throw new IllegalArgumentException("指定角度ではありません");
        end.angleDegrees=(clockwise?-1:1)*degrees;
    }
    public Obj intersection(int a,int b,Point2D.Double near) {
        Obj o=new Obj(Kind.POINT);o.mode="intersection";o.a=a;o.b=b;o.x=near.x;o.y=near.y;
        o.label=pointName(pointCounter++);o.color=0xC65C2C;return add(o);
    }
    public Obj line(String mode,int a,int b) { Obj o=new Obj(Kind.LINE);o.mode=mode;o.a=a;o.b=b;o.label=switch(mode){case "segment"->"線分";case "ray"->"半直線";default->"直線";}+nextId;o.color=0x345C93;o.showLabel=false;return add(o); }
    public Obj derivedLine(String mode,int base,int a) {Obj o=new Obj(Kind.LINE);o.mode=mode;o.base=base;o.a=a;o.label=(mode.equals("perp")?"垂線":"平行線")+nextId;o.color=0x157E87;o.showLabel=false;return add(o);}
    public Obj circle(int center,int edge) {Obj o=new Obj(Kind.CIRCLE);o.a=center;o.b=edge;o.label="円"+nextId;o.color=0x09968F;o.showLabel=false;return add(o);}
    public Obj circleRadius(int center,double radius) {Obj o=circle(center,-1);o.radius=radius;return o;}
    public Obj polygon(List<Integer> ids) { Obj o=new Obj(Kind.POLYGON);o.points=ids.stream().mapToInt(Integer::intValue).toArray();o.label="多角形"+nextId;o.color=0x9D58AC; o.filled=true;o.showLabel=false;return add(o); }
    public Obj function(String label,String expression) {Obj o=new Obj(Kind.FUNCTION);o.label=label.isBlank()?"f"+(curveCounter+1):label; o.expression=expression;o.showLabel=false;
        int[] colors={0xD64E4E,0x3178BD,0x218F70,0x985CB6,0xD88627};o.color=colors[curveCounter++%colors.length];return add(o);}
    public Obj text(String text,double x,double y) {Obj o=new Obj(Kind.TEXT);o.expression=text;o.label="テキスト"+nextId;o.x=x;o.y=y;return add(o);}
    public Obj distance(int a,int b) {Obj o=new Obj(Kind.DISTANCE);o.a=a;o.b=b;o.label="距離"+nextId;o.color=0xBA661A;return add(o);}
    public Obj angle(int a,int b,int c) {Obj o=new Obj(Kind.ANGLE);o.a=a;o.b=b;o.c=c;o.label="角"+nextId;o.color=0xBA661A;return add(o);}
    public Obj slider(String label,double val,double min,double max,double step) {Obj o=new Obj(Kind.SLIDER);o.label=label.toLowerCase(Locale.ROOT);o.value=val;o.min=min;o.max=max;o.step=step;o.showLabel=true;o.color=0x6B53B5;return add(o);}
    private static String pointName(int count) {String s="";int k=count;do{s=(char)('A'+k%26)+s;k=k/26-1;}while(k>=0);return s;}
    public static Point2D.Double p(double x,double y) {return new Point2D.Double(x,y);}
    public Point2D.Double pointOf(int id) {return pointOf(id,new HashSet<>(),0);}
    private Point2D.Double pointOf(int id,Set<Integer> visiting,int depth) {
        Obj o=get(id);
        if(o==null || o.kind!=Kind.POINT || depth>60 || !visiting.add(id)) return null;
        try {
            if (o.mode.equals("mid")) {
                Point2D.Double a=pointOf(o.a,visiting,depth+1),b=pointOf(o.b,visiting,depth+1);
                return a!=null&&b!=null?p((a.x+b.x)/2,(a.y+b.y)/2):null;
            }
            if (o.mode.equals("rotate")||o.mode.equals("stepRotation")) {
                Point2D.Double a=pointOf(o.a,visiting,depth+1),b=pointOf(o.b,visiting,depth+1);
                if(a==null||b==null||!Double.isFinite(o.angleDegrees))return null;
                double theta=Math.toRadians(o.angleDegrees);
                if(o.mode.equals("rotate")){
                    double dx=b.x-a.x,dy=b.y-a.y,co=Math.cos(theta),si=Math.sin(theta);
                    return p(a.x+co*dx-si*dy,a.y+si*dx+co*dy);
                }
                // The circumcenter lies on the perpendicular bisector of the first side.
                // Positive exterior angles put it to the left of the directed edge A->B.
                double dx=b.x-a.x,dy=b.y-a.y,tan=Math.tan(theta/2);
                if(Math.abs(tan)<1e-10||!Double.isFinite(o.value))return null;
                double cx=(a.x+b.x)/2-dy/(2*tan),cy=(a.y+b.y)/2+dx/(2*tan);
                double turn=theta*o.value,co=Math.cos(turn),si=Math.sin(turn);
                double ux=a.x-cx,uy=a.y-cy;
                return p(cx+co*ux-si*uy,cy+si*ux+co*uy);
            }
            if (o.mode.equals("intersection")) {
                List<Point2D.Double> list=intersections(get(o.a),get(o.b),visiting,depth+1);
                return list.stream().min(Comparator.comparingDouble(q->q.distanceSq(o.x,o.y))).orElse(null);
            }
            return p(o.x,o.y);
        } finally { visiting.remove(id); }
    }
    /** Two world-space points defining the underlying line. */
    public Point2D.Double[] endpoints(Obj line) {return endpoints(line,new HashSet<>(),0);}
    private Point2D.Double[] endpoints(Obj l,Set<Integer> visiting,int depth) {
        if(l==null||l.kind!=Kind.LINE||depth>60||!visiting.add(l.id))return null;
        try{
            Point2D.Double a=pointOf(l.a,visiting,depth+1);if(a==null)return null;
            if(l.mode.equals("parallel")||l.mode.equals("perp")) {
                Point2D.Double[] src=endpoints(get(l.base),visiting,depth+1); if(src==null)return null;
                double dx=src[1].x-src[0].x,dy=src[1].y-src[0].y;
                if(l.mode.equals("perp")){double d=dx;dx=-dy;dy=d;}
                if(dx*dx+dy*dy<1e-18)return null;
                return new Point2D.Double[]{a,p(a.x+dx,a.y+dy)};
            }
            Point2D.Double b=pointOf(l.b,visiting,depth+1);return b!=null?new Point2D.Double[]{a,b}:null;
        }finally{visiting.remove(l.id);}
    }
    public double radius(Obj circle) {return radius(circle,new HashSet<>(),0);}
    private double radius(Obj c,Set<Integer> visiting,int depth){
        if(c==null||c.kind!=Kind.CIRCLE)return Double.NaN;
        if(c.b==-1)return c.radius;
        Point2D.Double a=pointOf(c.a,visiting,depth+1),b=pointOf(c.b,visiting,depth+1);
        return a!=null&&b!=null?a.distance(b):Double.NaN;
    }
    public List<Point2D.Double> intersections(Obj a,Obj b) {return intersections(a,b,new HashSet<>(),0);}
    private List<Point2D.Double> intersections(Obj a,Obj b,Set<Integer> visiting,int depth) {
        if(a==null||b==null||a.id==b.id||depth>60)return List.of();
        boolean al=a.kind==Kind.LINE,bl=b.kind==Kind.LINE,ac=a.kind==Kind.CIRCLE,bc=b.kind==Kind.CIRCLE;
        if(al&&bl){
            Point2D.Double[] x=endpoints(a,visiting,depth+1),y=endpoints(b,visiting,depth+1);
            if(x==null||y==null)return List.of();
            double ux=x[1].x-x[0].x,uy=x[1].y-x[0].y;
            double vx=y[1].x-y[0].x,vy=y[1].y-y[0].y;
            double den=ux*vy-uy*vx;if(Math.abs(den)<1e-12)return List.of();
            double dx=y[0].x-x[0].x,dy=y[0].y-x[0].y;
            double t=(dx*vy-dy*vx)/den,u=(dx*uy-dy*ux)/den;
            if(!within(a,t)||!within(b,u))return List.of();
            return List.of(p(x[0].x+t*ux,x[0].y+t*uy));
        }
        if((al&&bc)||(ac&&bl)){
            Obj line=al?a:b,circ=ac?a:b;
            Point2D.Double[] ends=endpoints(line,visiting,depth+1);
            Point2D.Double center=pointOf(circ.a,visiting,depth+1);
            double r=radius(circ,visiting,depth+1);
            if(ends==null||center==null||!Double.isFinite(r))return List.of();
            double dx=ends[1].x-ends[0].x,dy=ends[1].y-ends[0].y;
            double fx=ends[0].x-center.x,fy=ends[0].y-center.y;
            double aa=dx*dx+dy*dy,bb=2*(fx*dx+fy*dy),cc=fx*fx+fy*fy-r*r;
            if(aa<1e-18)return List.of();
            double disc=bb*bb-4*aa*cc;if(disc<-1e-10)return List.of();
            double root=Math.sqrt(Math.max(0,disc));
            double t1=(-bb-root)/(2*aa),t2=(-bb+root)/(2*aa);
            ArrayList<Point2D.Double> out=new ArrayList<>();
            if(within(line,t1))out.add(p(ends[0].x+t1*dx,ends[0].y+t1*dy));
            if(root>1e-8&&within(line,t2))out.add(p(ends[0].x+t2*dx,ends[0].y+t2*dy));
            return out;
        }
        if(ac&&bc){
            Point2D.Double c1=pointOf(a.a,visiting,depth+1),c2=pointOf(b.a,visiting,depth+1);
            double r1=radius(a,visiting,depth+1),r2=radius(b,visiting,depth+1);
            if(c1==null||c2==null||!Double.isFinite(r1)||!Double.isFinite(r2))return List.of();
            double dx=c2.x-c1.x,dy=c2.y-c1.y,d=Math.hypot(dx,dy);
            if(d<1e-12||d>r1+r2+1e-10||d<Math.abs(r1-r2)-1e-10)return List.of();
            double along=(r1*r1-r2*r2+d*d)/(2*d),h2=r1*r1-along*along;
            if(h2< -1e-9)return List.of();
            double h=Math.sqrt(Math.max(0,h2)),mx=c1.x+along*dx/d,my=c1.y+along*dy/d;
            Point2D.Double t1=p(mx-h*dy/d,my+h*dx/d);
            if(h<1e-8)return List.of(t1);
            return List.of(t1,p(mx+h*dy/d,my-h*dx/d));
        }
        return List.of();
    }
    private boolean within(Obj line,double t){
        return switch(line.mode){case "segment"->t>=-1e-9&&t<=1+1e-9;case "ray"->t>=-1e-9;default->true;};
    }
    public Map<String,Double> vars(double x){
        Map<String,Double> vars=new HashMap<>();vars.put("x",x);
        for(Obj o:objects) if(o.kind==Kind.SLIDER)vars.put(o.label.toLowerCase(Locale.ROOT),o.value);
        return vars;
    }
    public double measurement(Obj o){
        if(o.kind==Kind.DISTANCE){Point2D.Double a=pointOf(o.a),b=pointOf(o.b);return a!=null&&b!=null?a.distance(b):Double.NaN;}
        if(o.kind==Kind.ANGLE){
            Obj fixedEnd=fixedAngleEndpoint(o);
            if(fixedEnd!=null)return Math.abs(fixedEnd.angleDegrees);
            Point2D.Double a=pointOf(o.a),b=pointOf(o.b),c=pointOf(o.c);
            if(a==null||b==null||c==null)return Double.NaN;
            double u=Math.atan2(a.y-b.y,a.x-b.x),v=Math.atan2(c.y-b.y,c.x-b.x);
            double diff=Math.abs(Math.toDegrees(Math.atan2(Math.sin(u-v),Math.cos(u-v))));return diff;
        }
        return Double.NaN;
    }
    public String description(Obj o){
        return switch(o.kind){
            case POINT -> {Point2D.Double p=pointOf(o.id);yield p==null?"未定義":String.format(Locale.ROOT,"(%.3f, %.3f)%s",p.x,p.y,o.mode.isEmpty()?"":" • 従属点");}
            case LINE -> switch(o.mode){case "segment"->"線分";case "ray"->"半直線";case "parallel"->"平行線";case "perp"->"垂線";default->"直線";};
            case CIRCLE -> String.format(Locale.ROOT,"半径 %.3f",radius(o));
            case POLYGON -> "regular".equals(o.mode)?"正"+o.points.length+"角形 • 辺の長さは連動":"頂点数 "+o.points.length;
            case FUNCTION -> "y = "+o.expression;
            case TEXT -> o.expression;
            case DISTANCE -> String.format(Locale.ROOT,"%.3f",measurement(o));
            case ANGLE -> String.format(Locale.ROOT,"%.1f°%s",measurement(o),"fixed".equals(o.mode)?" • 指定角度":"");
            case SLIDER -> String.format(Locale.ROOT,"%.3f",o.value);
        };
    }
    public void deleteWithDependents(int id) {
        HashSet<Integer> doomed=new HashSet<>();doomed.add(id);
        boolean change;
        do {change=false;for(Obj o:objects)if(!doomed.contains(o.id)) {
            boolean dep=o.a!=-1&&doomed.contains(o.a)||o.b!=-1&&doomed.contains(o.b)||o.c!=-1&&doomed.contains(o.c)||o.base!=-1&&doomed.contains(o.base);
            if(!dep)for(int x:o.points)if(doomed.contains(x))dep=true;
            if(dep){doomed.add(o.id);change=true;}
        }}while(change);
        objects.removeIf(o->doomed.contains(o.id));
    }
    public static void save(GeoModel m,File file) throws Exception {
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();secure(f);
        Document d=f.newDocumentBuilder().newDocument();Element root=d.createElement("GeoSketch");root.setAttribute("version","1");d.appendChild(root);
        root.setAttribute("centerX",Double.toString(m.centerX));root.setAttribute("centerY",Double.toString(m.centerY));
        root.setAttribute("scale",Double.toString(m.scale));root.setAttribute("showGrid",Boolean.toString(m.showGrid));
        root.setAttribute("showAxes",Boolean.toString(m.showAxes));root.setAttribute("showAxisNumbers",Boolean.toString(m.showAxisNumbers));root.setAttribute("snapGrid",Boolean.toString(m.snapGrid));
        root.setAttribute("hasCrop",Boolean.toString(m.hasCrop));
        root.setAttribute("cropMinX",Double.toString(m.cropMinX));root.setAttribute("cropMinY",Double.toString(m.cropMinY));
        root.setAttribute("cropMaxX",Double.toString(m.cropMaxX));root.setAttribute("cropMaxY",Double.toString(m.cropMaxY));
        root.setAttribute("nextId",Integer.toString(m.nextId));root.setAttribute("pointCounter",Integer.toString(m.pointCounter));root.setAttribute("curveCounter",Integer.toString(m.curveCounter));
        for(Obj o:m.objects){Element e=d.createElement("object");root.appendChild(e);
            e.setAttribute("kind",o.kind.name());e.setAttribute("id",Integer.toString(o.id));e.setAttribute("label",o.label);
            e.setAttribute("mode",o.mode);e.setAttribute("expression",o.expression);e.setAttribute("color",Integer.toString(o.color));
            e.setAttribute("visible",Boolean.toString(o.visible));e.setAttribute("showLabel",Boolean.toString(o.showLabel));e.setAttribute("filled",Boolean.toString(o.filled));
            e.setAttribute("strokeWidth",Double.toString(o.strokeWidth));e.setAttribute("lineStyle",o.lineStyle);e.setAttribute("fillStyle",o.fillStyle);
            e.setAttribute("a",Integer.toString(o.a));e.setAttribute("b",Integer.toString(o.b));e.setAttribute("c",Integer.toString(o.c));e.setAttribute("base",Integer.toString(o.base));
            e.setAttribute("x",Double.toString(o.x));e.setAttribute("y",Double.toString(o.y));e.setAttribute("radius",Double.toString(o.radius));
            e.setAttribute("min",Double.toString(o.min));e.setAttribute("max",Double.toString(o.max));e.setAttribute("step",Double.toString(o.step));e.setAttribute("value",Double.toString(o.value));e.setAttribute("angleDegrees",Double.toString(o.angleDegrees));
            e.setAttribute("points",Arrays.toString(o.points).replace("[","").replace("]","").replace(" ",""));
        }
        Transformer t=TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.INDENT,"yes");t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount","2");
        Path target=file.toPath(),dir=target.toAbsolutePath().getParent();
        if(dir!=null)Files.createDirectories(dir);
        Path tmp=Files.createTempFile(dir,"geosketch-",".tmp");
        try{
            t.transform(new DOMSource(d),new StreamResult(tmp.toFile()));
            try{Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(java.nio.file.AtomicMoveNotSupportedException ex){Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(tmp);}
    }
    public static GeoModel load(File file)throws Exception{
        if(Files.size(file.toPath())>20_000_000)throw new IllegalArgumentException("ファイルが大きすぎます (上限20MB)");
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();secure(f);
        Document d=f.newDocumentBuilder().parse(file);Element root=d.getDocumentElement();
        if(!root.getTagName().equals("GeoSketch")||!root.getAttribute("version").equals("1")) throw new IOException("対応していないファイル形式です");
        GeoModel m=new GeoModel();m.centerX=dbl(root,"centerX",0);m.centerY=dbl(root,"centerY",0);m.scale=dbl(root,"scale",60);
        if(!Double.isFinite(m.scale)||m.scale<1||m.scale>1e7)throw new IOException("不正な表示倍率");
        m.showGrid=bool(root,"showGrid",true);m.showAxes=bool(root,"showAxes",true);m.showAxisNumbers=bool(root,"showAxisNumbers",true);m.snapGrid=bool(root,"snapGrid",false);
        if(bool(root,"hasCrop",false))try{m.setCrop(dbl(root,"cropMinX",0),dbl(root,"cropMinY",0),dbl(root,"cropMaxX",0),dbl(root,"cropMaxY",0));}
        catch(IllegalArgumentException ex){throw new IOException("PNG範囲が不正です",ex);}
        NodeList nodes=root.getElementsByTagName("object");if(nodes.getLength()>20000)throw new IOException("図形数が多すぎます");
        HashSet<Integer> ids=new HashSet<>();int maxId=0;
        for(int i=0;i<nodes.getLength();i++){
            Element e=(Element)nodes.item(i);Obj o=new Obj(Kind.valueOf(e.getAttribute("kind")));
            o.id=num(e,"id",-1);if(o.id<=0||!ids.add(o.id))throw new IOException("不正な図形ID");maxId=Math.max(maxId,o.id);
            o.label=e.getAttribute("label");o.mode=e.getAttribute("mode");o.expression=e.getAttribute("expression");o.color=num(e,"color",0x276DB4);
            o.visible=bool(e,"visible",true);o.showLabel=bool(e,"showLabel",true);o.filled=bool(e,"filled",false);
            o.strokeWidth=dbl(e,"strokeWidth",2);if(!Double.isFinite(o.strokeWidth)||o.strokeWidth<.5||o.strokeWidth>16)throw new IOException("線の太さが不正です");
            o.lineStyle=e.getAttribute("lineStyle");if(!Set.of("solid","dash","dot").contains(o.lineStyle))o.lineStyle="solid";
            o.fillStyle=e.getAttribute("fillStyle");if(!Set.of("light","hatch","dense","none").contains(o.fillStyle))o.fillStyle="light";
            o.a=num(e,"a",-1);o.b=num(e,"b",-1);o.c=num(e,"c",-1);o.base=num(e,"base",-1);
            o.x=dbl(e,"x",0);o.y=dbl(e,"y",0);o.radius=dbl(e,"radius",1);
            o.min=dbl(e,"min",-5);o.max=dbl(e,"max",5);o.step=dbl(e,"step",.1);o.value=dbl(e,"value",1);o.angleDegrees=dbl(e,"angleDegrees",0);if(!Double.isFinite(o.angleDegrees)||Math.abs(o.angleDegrees)>=360)throw new IOException("点の回転角が不正です");
            String pts=e.getAttribute("points");if(!pts.isEmpty())o.points=Arrays.stream(pts.split(",")).mapToInt(Integer::parseInt).toArray();
            m.objects.add(o);
        }
        m.nextId=Math.max(maxId+1,num(root,"nextId",1));m.pointCounter=num(root,"pointCounter",(int)m.objects.stream().filter(o->o.kind==Kind.POINT).count());
        m.curveCounter=num(root,"curveCounter",(int)m.objects.stream().filter(o->o.kind==Kind.FUNCTION).count());
        return m;
    }
    private static void secure(DocumentBuilderFactory f)throws Exception{
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        f.setFeature("http://xml.org/sax/features/external-general-entities",false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false);
        f.setXIncludeAware(false);f.setExpandEntityReferences(false);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
    }
    private static int num(Element e,String a,int d){String s=e.getAttribute(a);return s.isEmpty()?d:Integer.parseInt(s);}
    private static double dbl(Element e,String a,double d){String s=e.getAttribute(a);return s.isEmpty()?d:Double.parseDouble(s);}
    private static boolean bool(Element e,String a,boolean d){String s=e.getAttribute(a);return s.isEmpty()?d:Boolean.parseBoolean(s);}
}
