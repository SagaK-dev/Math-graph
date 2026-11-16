import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.io.IOException;

/** PNG export from a selected world-space rectangle, with an optional figure number. */
public final class PNGExport {
    private PNGExport() {}
    public static final String[] PLACEMENTS={"左上","中央上","右上","左下","中央下","右下"};
    public static int croppedHeight(GeoModel m,int width){
        if(!m.hasCrop)throw new IllegalArgumentException("切り抜き範囲を先に設定してください");
        double ratio=(m.cropMaxY-m.cropMinY)/(m.cropMaxX-m.cropMinX);
        double height=width*ratio;
        if(!Double.isFinite(height)||height>Integer.MAX_VALUE)throw new IllegalArgumentException("切り抜き範囲の縦横比が不正です");
        return (int)Math.max(1,Math.round(height));
    }
    private static void validate(int width,int height)throws IOException{
        if(width<100||height<100||width>8000||height>8000||((long)width*height)>30_000_000L)
            throw new IOException("画像サイズは各100〜8000 px、合計3000万画素以下にしてください");
    }
    public static BufferedImage render(GeoModel model,int width,int height,boolean crop,String caption,String placement,int screenWidth)throws IOException {
        if(crop){if(!model.hasCrop)throw new IOException("切り抜き範囲が指定されていません");height=croppedHeight(model,width);}
        validate(width,height);
        GeoModel m=model.copy();
        if(crop){
            m.centerX=(m.cropMinX+m.cropMaxX)/2;m.centerY=(m.cropMinY+m.cropMaxY)/2;
            m.scale=width/(m.cropMaxX-m.cropMinX);
        }else if(screenWidth>0){
            // A high-resolution export preserves the screen's geometry scale.
            m.scale*=((double)width/screenWidth);
        }
        BufferedImage img=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=img.createGraphics();
        try{
            PlotRenderer.draw(g,m,width,height,-1,false);
            if(caption!=null&&!caption.isBlank())drawCaption(g,caption.strip(),placement,width,height);
        }finally{g.dispose();}
        return img;
    }
    public static void save(GeoModel model,File dest,int width,int height,boolean crop,String caption,String placement,int screenWidth)throws IOException{
        BufferedImage img=render(model,width,height,crop,caption,placement,screenWidth);
        if(!ImageIO.write(img,"PNG",dest))throw new IOException("PNGエンコーダが見つかりません");
    }
    private static void drawCaption(Graphics2D g,String caption,String placement,int width,int height){
        int size=Math.max(15,Math.min(34,Math.round(width/45f)));
        Font font=new Font("SansSerif",Font.PLAIN,size);g.setFont(font);
        FontMetrics fm=g.getFontMetrics();
        // Do not draw unbounded text outside an image. Keep only the fitting prefix.
        if(caption.length()>160)caption=caption.substring(0,160);
        int maxTextWidth=Math.max(10,width-36);
        while(caption.length()>1&&fm.stringWidth(caption)>maxTextWidth)caption=caption.substring(0,caption.length()-1);
        int textWidth=fm.stringWidth(caption),pad=Math.max(8,size/2),blockW=textWidth+pad*2,blockH=fm.getHeight()+pad;
        int x= switch(placement){case "中央上","中央下"->(width-blockW)/2;case "右上","右下"->width-blockW-10;default->10;};
        boolean lower=placement!=null&&placement.endsWith("下");
        int y=lower?height-blockH-10:10;
        x=Math.max(0,x);y=Math.max(0,y);
        g.setColor(new Color(255,255,255,244));g.fill(new RoundRectangle2D.Double(x,y,blockW,blockH,Math.max(6,size/3),Math.max(6,size/3)));
        g.setColor(new Color(23,32,47));g.drawString(caption,x+pad,y+(blockH-fm.getHeight())/2+fm.getAscent());
    }
}
