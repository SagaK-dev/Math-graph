import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

/** Java2D renderer shared by the interactive canvas and true-resolution PNG output. */
public final class PlotRenderer {
    private PlotRenderer() {}
    static final Color BG=new Color(0xFFFFFF),GRID=new Color(0xE9EDF2),AXIS=new Color(0x9EAAB7),INK=new Color(0x28394C);
    public record View(GeoModel m,int w,int h) {
        public double sx(double x){return w*.5+(x-m.centerX)*m.scale;}
        public double sy(double y){return h*.5-(y-m.centerY)*m.scale;}
        public double wx(double x){return m.centerX+(x-w*.5)/m.scale;}
        public double wy(double y){return m.centerY-(y-h*.5)/m.scale;}
        public Point2D.Double pixel(Point2D.Double p){return new Point2D.Double(sx(p.x),sy(p.y));}
    }
    private static final Map<String,MathExpression.Node> EXPR=new LinkedHashMap<>(){
        @Override protected boolean removeEldestEntry(Map.Entry<String,MathExpression.Node> eldest){return size()>300;}
    };
    private static MathExpression.Node expr(String s){
        MathExpression.Node node=EXPR.get(s);
        if(node==null){node=MathExpression.compile(s);EXPR.put(s,node);}return node;
    }
    public static void draw(Graphics2D g,GeoModel m,int w,int h,int selected,boolean drawSelection) {
        if(w<1||h<1)return;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);
        g.setColor(BG);g.fillRect(0,0,w,h);
        View v=new View(m,w,h);
        drawGrid(g,v);
        for(GeoModel.Obj o:m.objects)if(o.visible&&o.kind==GeoModel.Kind.POLYGON)drawObject(g,v,o,drawSelection&&o.id==selected);
        for(GeoModel.Obj o:m.objects)if(o.visible&&o.kind!=GeoModel.Kind.POLYGON&&o.kind!=GeoModel.Kind.POINT&&o.kind!=GeoModel.Kind.SLIDER)drawObject(g,v,o,drawSelection&&o.id==selected);
        for(GeoModel.Obj o:m.objects)if(o.visible&&o.kind==GeoModel.Kind.POINT)drawObject(g,v,o,drawSelection&&o.id==selected);
        int index=0;for(GeoModel.Obj o:m.objects)if(o.visible&&o.kind==GeoModel.Kind.SLIDER)drawSlider(g,v,o,index++,drawSelection&&o.id==selected);
    }
    private static double step(double scale){
        double n=60/scale;
        double pow=Math.pow(10,Math.floor(Math.log10(n)));
        for(double mul:new double[]{1,2,5,10})if(mul*pow>=n)return mul*pow;
        return 10*pow;
    }
    private static String nfmt(double x,double increment){
        if(Math.abs(x)<increment*1e-6)return "0";
        if(Math.abs(x)>1000000||Math.abs(x)<.00001)return String.format(Locale.ROOT,"%.1e",x);
        int digits=(int)Math.max(0,Math.min(6,Math.ceil(-Math.log10(increment))));
        return String.format(Locale.ROOT,"%."+digits+"f",x);
    }
    private static void drawGrid(Graphics2D g,View v){
        GeoModel m=v.m;
        double ss=step(m.scale),xmin=v.wx(0),xmax=v.wx(v.w),ymin=v.wy(v.h),ymax=v.wy(0);
        if(!Double.isFinite(ss)||ss<=0)return;
        g.setFont(new Font("SansSerif",Font.PLAIN,11));
        if(m.showGrid){
            g.setColor(GRID);g.setStroke(new BasicStroke(1));
            long left=(long)Math.ceil(xmin/ss),right=(long)Math.floor(xmax/ss);
            for(long i=left;i<=right&&i<left+1000;i++){
                int x=(int)Math.round(v.sx(i*ss));g.drawLine(x,0,x,v.h);
            }
            long low=(long)Math.ceil(ymin/ss),high=(long)Math.floor(ymax/ss);
            for(long i=low;i<=high&&i<low+1000;i++){
                int y=(int)Math.round(v.sy(i*ss));g.drawLine(0,y,v.w,y);
            }
        }
        if(m.showAxes){
            int ox=(int)Math.round(v.sx(0)),oy=(int)Math.round(v.sy(0));
            g.setColor(AXIS);g.setStroke(new BasicStroke(1.25f));
            if(ox>=0&&ox<v.w)g.drawLine(ox,0,ox,v.h);
            if(oy>=0&&oy<v.h)g.drawLine(0,oy,v.w,oy);
            g.setColor(new Color(0x728092));
            long left=(long)Math.ceil(xmin/ss),right=(long)Math.floor(xmax/ss);
            for(long i=left;i<=right&&i<left+1000;i++){
                if(i==0)continue;double val=i*ss;int x=(int)Math.round(v.sx(val));
                if(oy>=0&&oy<v.h)g.drawLine(x,oy-3,x,oy+3);
                if(m.showAxisNumbers){String label=nfmt(val,ss);int yy=(oy>=10&&oy<v.h-22)?oy+18:v.h-8;
                g.drawString(label,x-g.getFontMetrics().stringWidth(label)/2,yy);}
            }
            long low=(long)Math.ceil(ymin/ss),high=(long)Math.floor(ymax/ss);
            for(long i=low;i<=high&&i<low+1000;i++){
                if(i==0)continue;double val=i*ss;int y=(int)Math.round(v.sy(val));
                if(ox>=0&&ox<v.w)g.drawLine(ox-3,y,ox+3,y);
                if(m.showAxisNumbers){String label=nfmt(val,ss);int xx=(ox>50&&ox<v.w-50)?ox-9-g.getFontMetrics().stringWidth(label):8;
                g.drawString(label,xx,y-5);}
            }
        }
    }
    /** Angle arc in world coordinates, preserving reflex/directed angles for constructed figures. */
    private static double[] angleArc(GeoModel m,GeoModel.Obj o){
        Point2D.Double a=m.pointOf(o.a),b=m.pointOf(o.b),c=m.pointOf(o.c);
        if(a==null||b==null||c==null)return null;
        double start=Math.toDegrees(Math.atan2(a.y-b.y,a.x-b.x));
        GeoModel.Obj fixed=m.fixedAngleEndpoint(o);
        double extent;
        if(fixed!=null){extent=fixed.angleDegrees;}
        else{
            double finish=Math.atan2(c.y-b.y,c.x-b.x),begin=Math.atan2(a.y-b.y,a.x-b.x);
            extent=Math.toDegrees(Math.atan2(Math.sin(finish-begin),Math.cos(finish-begin)));
        }
        return new double[]{start,extent};
    }
    private static void drawObject(Graphics2D g,View v,GeoModel.Obj o,boolean selected) {
        Color color=o.awtColor();g.setColor(color);
        g.setStroke(objectStroke(o,selected));
        GeoModel m=v.m;
        switch(o.kind){
            case POINT->{
                Point2D.Double pos=m.pointOf(o.id);if(pos==null)break;
                Point2D.Double p=v.pixel(pos);
                if(selected){g.setColor(new Color(0xCFE4FF));g.fill(new Ellipse2D.Double(p.x-10,p.y-10,20,20));}
                g.setColor(Color.WHITE);g.fill(new Ellipse2D.Double(p.x-5,p.y-5,10,10));g.setColor(color);g.draw(new Ellipse2D.Double(p.x-5,p.y-5,10,10));
                if(o.showLabel)label(g,o.label,p.x+9,p.y-9,color);
            }
            case LINE->{
                double[] line=clippedLine(v,o);if(line==null)break;
                g.draw(new Line2D.Double(line[0],line[1],line[2],line[3]));
                if(o.showLabel){double x=(line[0]+line[2])*.5,y=(line[1]+line[3])*.5; label(g,o.label,x+7,y-6,color);}
            }
            case CIRCLE->{
                Point2D.Double p=m.pointOf(o.a);double radius=m.radius(o);
                if(p==null||!Double.isFinite(radius)||radius<=0)break;
                double x=v.sx(p.x),y=v.sy(p.y),r=radius*m.scale;
                if(r>1e7)break;
                Ellipse2D.Double shape=new Ellipse2D.Double(x-r,y-r,r*2,r*2);
                if(o.filled)fillShape(g,shape,o);g.setColor(color);g.draw(shape);
                if(o.showLabel)label(g,o.label,x+r*.72+8,y-r*.72-4,color);
            }
            case POLYGON->{
                if(o.points.length<3)break;
                Path2D path=new Path2D.Double();boolean ok=true;double cx=0,cy=0;
                for(int i=0;i<o.points.length;i++){
                    Point2D.Double p=m.pointOf(o.points[i]);if(p==null){ok=false;break;}
                    double px=v.sx(p.x),py=v.sy(p.y);if(i==0)path.moveTo(px,py);else path.lineTo(px,py);
                    cx+=px;cy+=py;
                }
                if(!ok)break;path.closePath();
                if(o.filled)fillShape(g,path,o);
                g.setColor(color);g.draw(path);
                if(o.showLabel)label(g,o.label,cx/o.points.length+5,cy/o.points.length,color);
            }
            case FUNCTION->{
                MathExpression.Node node;
                try{node=expr(o.expression);}catch(Exception e){break;}
                Path2D path=new Path2D.Double();boolean active=false;double prevY=0;int segments=0;
                for(int px=0;px<=v.w;px++){
                    double x=v.wx(px),y;
                    try{y=node.eval(m.vars(x));}catch(Exception e){active=false;continue;}
                    double sy=v.sy(y);
                    if(!Double.isFinite(sy)||Math.abs(sy)>1000000){active=false;continue;}
                    if(active&&Math.abs(prevY-sy)<v.h*.8){path.lineTo(px,sy);segments++;}
                    else path.moveTo(px,sy);
                    active=true;prevY=sy;
                }
                if(segments>0)g.draw(path);
            }
            case TEXT->{
                g.setFont(new Font("SansSerif",Font.PLAIN,17));
                g.drawString(o.expression,(float)v.sx(o.x),(float)v.sy(o.y));
            }
            case DISTANCE->{
                Point2D.Double pa=m.pointOf(o.a),pb=m.pointOf(o.b);if(pa==null||pb==null)break;
                Point2D.Double a=v.pixel(pa),b=v.pixel(pb);
                g.setStroke(new BasicStroke(1f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,10,new float[]{4,5},0));
                g.draw(new Line2D.Double(a,b));
                label(g,String.format(Locale.ROOT,"%.3f",m.measurement(o)),(a.x+b.x)*.5+8,(a.y+b.y)*.5-8,color);
            }
            case ANGLE->{
                Point2D.Double aa=m.pointOf(o.a),bb=m.pointOf(o.b),cc=m.pointOf(o.c);if(aa==null||bb==null||cc==null)break;
                Point2D.Double a=v.pixel(aa),b=v.pixel(bb),c=v.pixel(cc);
                double[] arc=angleArc(m,o);if(arc==null)break;
                double s=arc[0],delta=arc[1],rr=34;
                g.draw(new Arc2D.Double(b.x-rr,b.y-rr,rr*2,rr*2,s,delta,Arc2D.OPEN));
                if(o.showLabel){
                    double mid=Math.toRadians(s+delta*.5);
                    label(g,String.format(Locale.ROOT,"%.1f°",m.measurement(o)),b.x+Math.cos(mid)*54,b.y-Math.sin(mid)*54,color);
                }
            }
            default -> {}
        }
    }
    /** Fill is clipped to the selected closed shape; hatch spacing is expressed in output pixels. */
    private static void fillShape(Graphics2D g,Shape shape,GeoModel.Obj obj){
        Color c=obj.awtColor();
        if(obj.fillStyle.equals("none"))return;
        if(obj.fillStyle.equals("light")){
            g.setColor(new Color(c.getRed(),c.getGreen(),c.getBlue(),45));g.fill(shape);return;
        }
        Graphics2D layer=(Graphics2D)g.create();
        try{
            layer.clip(shape);
            Rectangle bounds=shape.getBounds();
            layer.setColor(new Color(219,219,219,100));layer.fill(shape);
            int space=obj.fillStyle.equals("dense")?6:11;
            layer.setStroke(new BasicStroke(obj.fillStyle.equals("dense")?1.4f:1.0f));
            layer.setColor(new Color(118,118,118,195));
            int bottom=bounds.y+bounds.height;
            for(long k=(long)bounds.x-bounds.height-space*2L;k<(long)bounds.x+bounds.width+space*2L;k+=space){
                layer.draw(new Line2D.Double(k,bottom,k+bounds.height,bounds.y));
            }
        }finally{layer.dispose();}
    }
    private static BasicStroke objectStroke(GeoModel.Obj o,boolean selected){
        float width=(float)Math.max(.5,Math.min(16,o.strokeWidth))+(selected?1.2f:0);
        float[] pattern=switch(o.lineStyle){
            case "dash"->new float[]{9f,6f};
            case "dot"->new float[]{1f,5f};
            default->null;
        };
        return pattern==null?new BasicStroke(width,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND):
            new BasicStroke(width,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND,10f,pattern,0f);
    }
    private static void label(Graphics2D g,String s,double x,double y,Color fg){
        if(s==null||s.isBlank())return;
        g.setFont(new Font("SansSerif",Font.PLAIN,13));
        FontMetrics fm=g.getFontMetrics();int w=fm.stringWidth(s);
        g.setColor(new Color(255,255,255,218));g.fillRoundRect((int)x-3,(int)y-fm.getAscent()-1,w+6,fm.getHeight()+2,5,5);
        g.setColor(fg);g.drawString(s,(float)x,(float)y);
    }
    private static double[] clippedLine(View v,GeoModel.Obj o) {
        Point2D.Double[] ends=v.m.endpoints(o);if(ends==null)return null;
        double dx=ends[1].x-ends[0].x,dy=ends[1].y-ends[0].y;
        if(dx*dx+dy*dy<1e-20)return null;
        double lo=o.mode.equals("segment")?0:(o.mode.equals("ray")?0:Double.NEGATIVE_INFINITY);
        double hi=o.mode.equals("segment")?1:Double.POSITIVE_INFINITY;
        double xmin=v.wx(-5),xmax=v.wx(v.w+5),ymin=v.wy(v.h+5),ymax=v.wy(-5);
        double[] t={lo,hi};
        if(!clip(-dx,ends[0].x-xmin,t)||!clip(dx,xmax-ends[0].x,t)||
           !clip(-dy,ends[0].y-ymin,t)||!clip(dy,ymax-ends[0].y,t)||t[0]>t[1])return null;
        return new double[]{v.sx(ends[0].x+t[0]*dx),v.sy(ends[0].y+t[0]*dy),
                            v.sx(ends[0].x+t[1]*dx),v.sy(ends[0].y+t[1]*dy)};
    }
    private static boolean clip(double p,double q,double[] t){
        if(Math.abs(p)<1e-15)return q>=0;
        double r=q/p;
        if(p<0){if(r>t[1])return false;if(r>t[0])t[0]=r;}
        else {if(r<t[0])return false;if(r<t[1])t[1]=r;}
        return true;
    }
    public static int hit(View v,int sx,int sy){
        int best=-1;double score=Double.POSITIVE_INFINITY;
        for(GeoModel.Obj o:v.m.objects){if(!o.visible)continue;
            double distance=Double.POSITIVE_INFINITY;
            switch(o.kind){
                case POINT->{Point2D.Double p=v.m.pointOf(o.id);if(p!=null)distance=v.pixel(p).distance(sx,sy)-3.5;}
                case LINE->{double[] line=clippedLine(v,o);if(line!=null)distance=Line2D.ptSegDist(line[0],line[1],line[2],line[3],sx,sy)+1;}
                case CIRCLE->{Point2D.Double p=v.m.pointOf(o.a);if(p!=null)distance=Math.abs(v.pixel(p).distance(sx,sy)-v.m.radius(o)*v.m.scale)+1;}
                case POLYGON->{
                    for(int i=0;i<o.points.length;i++){
                        Point2D.Double p1=v.m.pointOf(o.points[i]),p2=v.m.pointOf(o.points[(i+1)%o.points.length]);
                        if(p1!=null&&p2!=null){Point2D.Double a=v.pixel(p1),b=v.pixel(p2);distance=Math.min(distance,Line2D.ptSegDist(a.x,a.y,b.x,b.y,sx,sy)+2);}
                    }
                }
                case FUNCTION->{
                    MathExpression.Node expr;
                    try{expr=expr(o.expression);}
                    catch(Exception e){break;}
                    for(int dx=-3;dx<=3;dx+=3){double fx=v.wx(sx+dx);
                        try{double fy=expr.eval(v.m.vars(fx));if(Double.isFinite(fy))distance=Math.min(distance,Math.hypot(dx,v.sy(fy)-sy)+1.5);}catch(Exception ignored){}
                    }
                }
                case TEXT->{distance=Math.hypot(v.sx(o.x)-sx,v.sy(o.y)-sy)-5;}
                case SLIDER->{int i=sliderIndex(v.m,o);double y=v.h-44-49*i;if(Math.abs(sy-y)<15&&sx>=20&&sx<=255)distance=0;}
                case ANGLE->{
                    double[] arc=angleArc(v.m,o);Point2D.Double bb=v.m.pointOf(o.b);
                    if(arc!=null&&bb!=null){
                        Point2D.Double center=v.pixel(bb);
                        int segments=Math.max(2,(int)Math.ceil(Math.abs(arc[1])/4));
                        double prevX=0,prevY=0;
                        for(int i=0;i<=segments;i++){
                            double theta=Math.toRadians(arc[0]+arc[1]*i/segments);
                            double x=center.x+34*Math.cos(theta),y=center.y-34*Math.sin(theta);
                            if(i>0)distance=Math.min(distance,Line2D.ptSegDist(prevX,prevY,x,y,sx,sy));
                            prevX=x;prevY=y;
                        }
                    }
                }
                case DISTANCE -> {}
            }
            if(distance<=9&&distance<score){score=distance;best=o.id;}
        }
        return best;
    }
    private static int sliderIndex(GeoModel m,GeoModel.Obj o){int i=0;for(GeoModel.Obj item:m.objects){if(item==o)break;if(item.visible&&item.kind==GeoModel.Kind.SLIDER)i++;}return i;}
    private static void drawSlider(Graphics2D g,View v,GeoModel.Obj o,int index,boolean selected){
        int x=30,y=v.h-44-index*49,width=202;
        g.setColor(new Color(255,255,255,242));g.fillRoundRect(x-14,y-27,width+32,57,10,10);
        g.setColor(selected?new Color(0xACBCEB):new Color(0xDBE3EC));g.drawRoundRect(x-14,y-27,width+32,57,10,10);
        g.setFont(new Font("SansSerif",Font.PLAIN,12));g.setColor(INK);
        g.drawString(o.label+" = "+String.format(Locale.ROOT,"%.2f",o.value),x,y-10);
        g.setColor(new Color(0xDCE4EF));g.setStroke(new BasicStroke(4,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.drawLine(x,y+7,x+width,y+7);
        double t=(o.max==o.min)?0:(o.value-o.min)/(o.max-o.min);t=Math.max(0,Math.min(1,t));
        int knob=x+(int)Math.round(width*t);
        g.setColor(o.awtColor());g.drawLine(x,y+7,knob,y+7);g.fillOval(knob-7,y,14,14);
    }
    public static double sliderValueFromX(GeoModel.Obj o,int x){
        double t=Math.max(0,Math.min(1,(x-30.0)/202));double val=o.min+t*(o.max-o.min);
        if(o.step>0)val=Math.round((val-o.min)/o.step)*o.step+o.min;
        return Math.max(o.min,Math.min(o.max,val));
    }
}
