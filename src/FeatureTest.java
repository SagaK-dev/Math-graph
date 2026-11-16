import java.io.File;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.imageio.ImageIO;

/** Automated regression test for requested v1.1 export and styling features. */
public final class FeatureTest {
    private static int assertions=0;
    private static void require(boolean ok,String description){assertions++;if(!ok)throw new AssertionError("FAILED: "+description);}
    private static int differences(BufferedImage a,BufferedImage b){
        require(a.getWidth()==b.getWidth()&&a.getHeight()==b.getHeight(),"compare equal-size images");
        int count=0;
        for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++)if(a.getRGB(x,y)!=b.getRGB(x,y))count++;
        return count;
    }
    public static void run(File output)throws Exception{
        GeoModel model=new GeoModel();model.showGrid=false;model.showAxes=false;
        GeoModel.Obj a=model.point(-1.6,-1);GeoModel.Obj b=model.point(1.6,-1);
        GeoModel.Obj c=model.point(1.6,1);GeoModel.Obj d=model.point(-1.6,1);
        for(GeoModel.Obj point:new GeoModel.Obj[]{a,b,c,d})point.visible=false;
        GeoModel.Obj poly=model.polygon(List.of(a.id,b.id,c.id,d.id));
        poly.fillStyle="hatch";poly.strokeWidth=3;poly.color=0x777777;poly.lineStyle="dash";
        GeoModel.Obj line=model.line("segment",a.id,c.id);
        line.lineStyle="dot";line.strokeWidth=5.5;line.color=0x9B2226;
        model.setCrop(-2.25,-1.5,2.25,1.5);
        File proj=new File(output.getParentFile()==null?new File("."):output.getParentFile(),"GeoSketch_v1_1_sample.gsk");
        GeoModel.save(model,proj);
        GeoModel loaded=GeoModel.load(proj);
        require(loaded.hasCrop,"crop enabled persists");
        require(Math.abs(loaded.cropMinX+2.25)<1e-9,"crop coordinates persist");
        require("hatch".equals(loaded.get(poly.id).fillStyle),"hatch persists");
        require("dash".equals(loaded.get(poly.id).lineStyle),"dash persists");
        require("dot".equals(loaded.get(line.id).lineStyle),"dot persists");
        require(Math.abs(loaded.get(line.id).strokeWidth-5.5)<1e-9,"line width persists");
        require(loaded.get(line.id).color==0x9B2226,"line color persists");
        require(PNGExport.croppedHeight(loaded,1200)==800,"aspect-ratio crop height");
        PNGExport.save(loaded,output,1200,0,true,"図12","右下",0);
        BufferedImage actual=ImageIO.read(output);
        require(actual!=null&&actual.getWidth()==1200&&actual.getHeight()==800,"cropped image size");
        BufferedImage noCaption=PNGExport.render(loaded,1200,0,true,"","右下",0);
        require(differences(actual,noCaption)>150,"figure caption printed in PNG");
        int changedAtBottomRight=0;
        for(int y=720;y<795;y++)for(int x=1050;x<1190;x++)if(actual.getRGB(x,y)!=noCaption.getRGB(x,y))changedAtBottomRight++;
        require(changedAtBottomRight>80,"figure caption placed at right-bottom");
        int hatchCount=0;
        // At the left-center of the closed polygon, gray hatching creates non-white pixels.
        for(int y=260;y<540;y++)for(int x=210;x<420;x++)if((actual.getRGB(x,y)&0xFFFFFF)!=0xFFFFFF)hatchCount++;
        require(hatchCount>1000,"hatch interior rendered");
        GeoModel plain=loaded.copy();plain.get(poly.id).filled=false;
        BufferedImage noHatch=PNGExport.render(plain,1200,0,true,"","右下",0);
        require(differences(noCaption,noHatch)>5000,"hatch fill affects polygon interior");
        GeoModel solid=loaded.copy();solid.get(line.id).lineStyle="solid";
        BufferedImage solidPic=PNGExport.render(solid,1200,0,true,"","右下",0);
        require(differences(noCaption,solidPic)>100,"dotted vs solid render differs");
        GeoModel thin=loaded.copy();thin.get(line.id).strokeWidth=1;
        BufferedImage thinPic=PNGExport.render(thin,1200,0,true,"","右下",0);
        require(differences(noCaption,thinPic)>100,"line thickness changes exported image");
        GeoModel axes=loaded.copy();axes.showGrid=false;axes.showAxes=true;axes.showAxisNumbers=true;
        BufferedImage numbered=PNGExport.render(axes,1200,0,true,"","左上",0);
        axes.showAxisNumbers=false;
        BufferedImage noNumbers=PNGExport.render(axes,1200,0,true,"","左上",0);
        require(differences(numbered,noNumbers)>100,"axes with vs without numbers differs");
        axes.showAxes=false;
        BufferedImage noAxes=PNGExport.render(axes,1200,0,true,"","左上",0);
        require(differences(noNumbers,noAxes)>100,"axes hidden render differs");
        // Check a graphics-only pixel that is outside crop in world space.
        GeoModel outside=new GeoModel();outside.showAxes=false;outside.showGrid=false;
        GeoModel.Obj remote=outside.point(10,10);remote.color=0x000000;
        outside.setCrop(-2,-1,2,1);
        BufferedImage clipped=PNGExport.render(outside,800,0,true,"","左上",0);
        int notWhite=0;for(int y=0;y<clipped.getHeight();y++)for(int x=0;x<clipped.getWidth();x++)if((clipped.getRGB(x,y)&0xFFFFFF)!=0xFFFFFF)notWhite++;
        require(notWhite==0,"outside-crop objects excluded");
        System.out.println("PASS: "+assertions+" enhanced feature assertions");
        System.out.println("PNG: "+output.getAbsolutePath());
        System.out.println("PROJECT: "+proj.getAbsolutePath());
    }
}
