import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.regex.*;

/**
 * GeoSketch - standalone dynamic 2D geometry and graphing application.
 * Independent Java2D/Swing implementation, no external runtime dependencies,
 * accounts, telemetry, ads, network calls, or third-party resources.
 */
public final class GeoSketch extends JFrame {
    enum Tool {
        MOVE("移動","点をドラッグして移動。余白をドラッグすると画面を移動"),
        POINT("点","作図領域をクリックすると点を置きます"),
        SEGMENT("線分","始点、終点を順にクリック"),
        LINE("直線","直線上の2点をクリック"),
        RAY("半直線","始点と通過点をクリック"),
        CIRCLE("円","中心点と円周上の点をクリック"),
        POLYGON("多角形","頂点を順にクリック。最初の点またはダブルクリックで完成"),
        REGULAR_POLYGON("正多角形","隣り合う2つの頂点をクリックし、辺の数（3〜60）を指定"),
        HATCH("斜線領域","3点以上の頂点をクリック。最初の点を再クリックまたはダブルクリックで完成"),
        CROP("PNG範囲","マウスドラッグでPNGに出力する範囲を囲みます"),
        MIDPOINT("中点","2つの点をクリック（自動作図）"),
        PARALLEL("平行線","基準となる直線を選び、通過する点をクリック"),
        PERP("垂線","基準となる直線を選び、通過する点をクリック"),
        INTERSECTION("交点","2つの直線または円をクリック"),
        DISTANCE("距離","2つの点をクリック"),
        ANGLE("角度","3点を順にクリック（2番目が頂点）"),
        FIXED_ANGLE("指定角度","1点目は始点A、2点目が頂点B。角度と方向を入力"),
        TEXT("文字","配置する場所をクリック"),
        DELETE("削除","消したい図形をクリック");
        final String text,help;
        Tool(String text,String help){this.text=text;this.help=help;}
    }
    private GeoModel model=new GeoModel();
    private final Deque<GeoModel> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    private Tool tool=Tool.MOVE;
    private final ArrayList<Integer> pending=new ArrayList<>();
    private int selected=-1;
    private PlotPanel canvas;
    private JLabel status, toolHelp;
    private DefaultListModel<GeoModel.Obj> listData;
    private JList<GeoModel.Obj> objectList;
    private JPanel inspector, tools;
    private final Map<Tool,JToggleButton> toggles=new EnumMap<>(Tool.class);
    private JTextField formulaInput;
    private JCheckBox gridCheck,snapCheck;
    private JComboBox<String> axisMode;
    private File currentFile;
    private boolean dirty=false,updatingList=false,updatingView=false;
    private JButton undoBtn,redoBtn;
    private static final Color BLUE=new Color(0x2974C8),TEXT=new Color(0x25344A),BORDER=new Color(0xDCE4ED),PALE=new Color(0xF5F8FB);
    private static final Font UI_FONT=new Font("SansSerif",Font.PLAIN,13);
    private GeoSketch(){
        super("GeoSketch — 新規");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent e){if(confirmSave())dispose();}});
        setMinimumSize(new Dimension(850,600));setSize(1370,875);setLocationRelativeTo(null);
        createUI();setTool(Tool.MOVE);refreshAll();
    }
    private JButton button(String label,String tip,Runnable action){
        JButton b=new JButton(label);b.setFont(UI_FONT);b.setFocusPainted(false);b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBackground(Color.WHITE);b.setForeground(TEXT);b.setBorder(new CompoundBorder(new LineBorder(BORDER,1,true),new EmptyBorder(7,12,7,12)));
        b.setToolTipText(tip);b.addActionListener(e->action.run());return b;
    }
    private void createUI(){
        JPanel root=new JPanel(new BorderLayout());root.setBackground(Color.WHITE);setContentPane(root);
        root.add(createHeader(),BorderLayout.NORTH);
        JPanel side=createSide();canvas=new PlotPanel();
        JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,side,canvas);
        split.setDividerLocation(315);split.setResizeWeight(0);split.setDividerSize(5);split.setBorder(null);
        root.add(split,BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout());footer.setBorder(new EmptyBorder(7,14,7,14));footer.setBackground(PALE);
        status=new JLabel("準備完了");status.setFont(UI_FONT);status.setForeground(new Color(0x56677B));
        JLabel label=new JLabel("GeoSketch 1.2 • オフライン • Java 17+");label.setFont(new Font("SansSerif",Font.PLAIN,11));label.setForeground(new Color(0x8795A7));
        footer.add(status,BorderLayout.WEST);footer.add(label,BorderLayout.EAST);root.add(footer,BorderLayout.SOUTH);
        setJMenuBar(createMenu());
    }
    private JPanel createHeader(){
        JPanel shell=new JPanel(new BorderLayout());shell.setBackground(Color.WHITE);
        JPanel head=new JPanel(new BorderLayout());head.setBackground(Color.WHITE);head.setBorder(new EmptyBorder(11,16,9,16));
        JPanel branding=new JPanel(new FlowLayout(FlowLayout.LEFT,10,0));branding.setOpaque(false);
        JLabel icon=new JLabel("◈");icon.setFont(new Font("SansSerif",Font.BOLD,29));icon.setForeground(BLUE);
        JLabel title=new JLabel("GeoSketch");title.setFont(new Font("SansSerif",Font.BOLD,23));title.setForeground(TEXT);
        JLabel subtitle=new JLabel("動的幾何・関数グラフ");subtitle.setForeground(new Color(0x8996A6));subtitle.setFont(UI_FONT);
        branding.add(icon);branding.add(title);branding.add(subtitle);head.add(branding,BorderLayout.WEST);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));actions.setOpaque(false);
        undoBtn=button("↶ 元に戻す","Ctrl+Z",this::undo);redoBtn=button("↷ やり直す","Ctrl+Y",this::redo);
        JButton png=button("PNG 出力","作図領域をPNGファイルとして保存 (Ctrl+E)",this::exportDialog);
        png.setBackground(BLUE);png.setForeground(Color.WHITE);
        actions.add(undoBtn);actions.add(redoBtn);actions.add(png);head.add(actions,BorderLayout.EAST);
        shell.add(head,BorderLayout.NORTH);
        JPanel toolRows=new JPanel(new BorderLayout());toolRows.setBackground(PALE);
        tools=new JPanel(new FlowLayout(FlowLayout.LEFT,5,5));tools.setOpaque(false);
        ButtonGroup group=new ButtonGroup();
        for(Tool t:Tool.values()){
            JToggleButton b=new JToggleButton(t.text);b.setFont(UI_FONT);b.setFocusPainted(false);
            b.setBorder(new CompoundBorder(new LineBorder(BORDER,1,true),new EmptyBorder(7,10,7,10)));
            b.setBackground(Color.WHITE);b.setForeground(TEXT);b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.setToolTipText(t.help);b.addActionListener(e->setTool(t));
            group.add(b);toggles.put(t,b);tools.add(b);
        }
        JScrollPane scroller=new JScrollPane(tools,ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroller.setBorder(new MatteBorder(1,0,1,0,BORDER));scroller.getHorizontalScrollBar().setUnitIncrement(25);
        toolRows.add(scroller,BorderLayout.CENTER);
        toolHelp=new JLabel(" ");toolHelp.setBorder(new EmptyBorder(5,16,7,16));toolHelp.setForeground(new Color(0x64758A));toolHelp.setFont(new Font("SansSerif",Font.PLAIN,12));
        toolRows.add(toolHelp,BorderLayout.SOUTH);shell.add(toolRows,BorderLayout.CENTER);
        return shell;
    }
    private JPanel createSide(){
        JPanel side=new JPanel(new BorderLayout());side.setBackground(Color.WHITE);
        side.setPreferredSize(new Dimension(315,550));side.setMinimumSize(new Dimension(265,400));
        JPanel top=new JPanel(new BorderLayout());top.setBackground(Color.WHITE);
        JLabel head=new JLabel("  オブジェクト / 代数");head.setFont(new Font("SansSerif",Font.BOLD,16));head.setForeground(TEXT);
        head.setBorder(new EmptyBorder(16,10,12,10));top.add(head,BorderLayout.NORTH);
        listData=new DefaultListModel<>();objectList=new JList<>(listData);
        objectList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);objectList.setFixedCellHeight(54);
        objectList.setBackground(Color.WHITE);objectList.setCellRenderer(new ObjectRenderer());
        objectList.addListSelectionListener(e->{if(!e.getValueIsAdjusting()&&!updatingList){
            GeoModel.Obj o=objectList.getSelectedValue();selected=o==null?-1:o.id;renderInspector();canvas.repaint();
        }});
        objectList.addMouseListener(new MouseAdapter(){@Override public void mouseClicked(MouseEvent e){
            int i=objectList.locationToIndex(e.getPoint());
            if(i>=0 && objectList.getCellBounds(i,i).contains(e.getPoint())){
                GeoModel.Obj o=listData.get(i);
                if(e.getX()<35){change();o.visible=!o.visible;refreshed();}
                else if(e.getClickCount()==2){selected=o.id;renderInspector();}
            }
        }});
        JScrollPane listScroll=new JScrollPane(objectList);listScroll.setBorder(new MatteBorder(1,0,1,0,BORDER));top.add(listScroll,BorderLayout.CENTER);
        side.add(top,BorderLayout.CENTER);
        JPanel lower=new JPanel(new BorderLayout());lower.setBackground(Color.WHITE);
        JPanel exprPane=new JPanel(new BorderLayout(5,7));exprPane.setBackground(PALE);
        exprPane.setBorder(new EmptyBorder(12,13,12,13));
        JLabel inputTitle=new JLabel("数式・コマンド入力");inputTitle.setFont(new Font("SansSerif",Font.BOLD,13));inputTitle.setForeground(TEXT);
        exprPane.add(inputTitle,BorderLayout.NORTH);
        JPanel row=new JPanel(new BorderLayout(5,0));row.setOpaque(false);
        formulaInput=new JTextField();formulaInput.setFont(new Font("Monospaced",Font.PLAIN,13));
        formulaInput.setToolTipText("例: f(x)=x^2, y=sin(x), A=(2,3), a=2");
        formulaInput.setBorder(new CompoundBorder(new LineBorder(BORDER,1,true),new EmptyBorder(8,8,8,8)));
        formulaInput.addActionListener(e->addExpression());
        JButton go=button("＋","数式を追加",this::addExpression);row.add(formulaInput,BorderLayout.CENTER);row.add(go,BorderLayout.EAST);
        exprPane.add(row,BorderLayout.CENTER);
        JLabel example=new JLabel("例：f(x)=x^2  /  A=(2,3)  /  a=2");example.setFont(new Font("SansSerif",Font.PLAIN,11));example.setForeground(new Color(0x78879A));
        exprPane.add(example,BorderLayout.SOUTH);
        inspector=new JPanel(new BorderLayout());inspector.setBackground(Color.WHITE);
        inspector.setBorder(new MatteBorder(1,0,0,0,BORDER));
        inspector.setPreferredSize(new Dimension(315,230));
        JPanel view=new JPanel(new FlowLayout(FlowLayout.LEFT,8,6));view.setBackground(Color.WHITE);
        gridCheck=new JCheckBox("方眼",true);snapCheck=new JCheckBox("グリッド吸着",false);
        for(JCheckBox c:new JCheckBox[]{gridCheck,snapCheck}){c.setBackground(Color.WHITE);c.setFont(new Font("SansSerif",Font.PLAIN,12));view.add(c);}
        view.add(new JLabel("座標軸:"));
        axisMode=new JComboBox<>(new String[]{"数字あり","数字なし","非表示"});
        axisMode.setFont(new Font("SansSerif",Font.PLAIN,12));view.add(axisMode);
        gridCheck.addActionListener(e->{change();model.showGrid=gridCheck.isSelected();refreshed();});
        axisMode.addActionListener(e->{if(canvas==null||updatingView)return;change();int mode=axisMode.getSelectedIndex();model.showAxes=mode!=2;model.showAxisNumbers=mode==0;refreshed();});
        snapCheck.addActionListener(e->{change();model.snapGrid=snapCheck.isSelected();refreshed();});
        JPanel combined=new JPanel(new BorderLayout());combined.add(exprPane,BorderLayout.NORTH);combined.add(inspector,BorderLayout.CENTER);combined.add(view,BorderLayout.SOUTH);
        lower.add(combined,BorderLayout.CENTER);side.add(lower,BorderLayout.SOUTH);
        renderInspector();return side;
    }
    private final class ObjectRenderer extends JPanel implements ListCellRenderer<GeoModel.Obj>{
        private final JLabel dot=new JLabel("●",SwingConstants.CENTER),main=new JLabel(),detail=new JLabel(),eye=new JLabel("◉",SwingConstants.CENTER);
        ObjectRenderer(){setLayout(new BorderLayout());setBorder(new EmptyBorder(4,5,4,8));
            dot.setPreferredSize(new Dimension(27,30));dot.setFont(new Font("SansSerif",Font.BOLD,15));
            JPanel content=new JPanel(new GridLayout(2,1,0,1));content.setOpaque(false);
            main.setFont(new Font("SansSerif",Font.BOLD,13));detail.setFont(new Font("SansSerif",Font.PLAIN,11));
            content.add(main);content.add(detail);add(eye,BorderLayout.WEST);
            JPanel center=new JPanel(new BorderLayout());center.setOpaque(false);center.add(dot,BorderLayout.WEST);center.add(content,BorderLayout.CENTER);
            add(center,BorderLayout.CENTER);
            dot.setPreferredSize(new Dimension(24,36));eye.setPreferredSize(new Dimension(25,36));
        }
        @Override public Component getListCellRendererComponent(JList<? extends GeoModel.Obj> l,GeoModel.Obj o,int i,boolean sel,boolean focus){
            setBackground(sel?new Color(0xEDF5FF):Color.WHITE);setOpaque(true);
            eye.setText(o.visible?"◉":"○");eye.setForeground(o.visible?BLUE:new Color(0xB4C0CC));
            dot.setForeground(o.awtColor());main.setText(o.label.isBlank()?"(名前なし)":o.label);main.setForeground(TEXT);
            String d=model.description(o);if(d.length()>38)d=d.substring(0,37)+"…";
            detail.setText(d);detail.setForeground(new Color(0x75869A));
            return this;
        }
    }
    private void renderInspector(){
        if(inspector==null)return;inspector.removeAll();
        GeoModel.Obj o=model.get(selected);
        if(o==null){
            JLabel none=new JLabel("   図形を選択すると属性を変更できます");none.setForeground(new Color(0x8090A1));none.setFont(new Font("SansSerif",Font.PLAIN,12));
            inspector.add(none,BorderLayout.CENTER);
        }else{
            JPanel prop=new JPanel();prop.setBackground(Color.WHITE);prop.setLayout(new BoxLayout(prop,BoxLayout.Y_AXIS));
            prop.setBorder(new EmptyBorder(10,14,8,14));
            JLabel title=new JLabel("選択中："+o.label);title.setFont(new Font("SansSerif",Font.BOLD,13));title.setAlignmentX(LEFT_ALIGNMENT);
            prop.add(title);prop.add(Box.createVerticalStrut(7));
            JPanel buttons=new JPanel(new FlowLayout(FlowLayout.LEFT,5,0));buttons.setOpaque(false);buttons.setAlignmentX(LEFT_ALIGNMENT);
            JButton rename=button("名前","オブジェクト名を変更",()->{
                String s=JOptionPane.showInputDialog(this,"新しい名前",o.label);
                if(s!=null&&!s.isBlank()){change();o.label=s.trim();refreshed();}
            });
            JButton color=button("色","線と点の色を変更",()->{
                Color c=JColorChooser.showDialog(this,"色を選択",o.awtColor());
                if(c!=null){change();o.color=c.getRGB()&0xFFFFFF;refreshed();}
            });
            JButton del=button("削除","関連する従属図形も削除",()->deleteObject(o.id));
            buttons.add(rename);buttons.add(color);buttons.add(del);prop.add(buttons);
            JPanel styles=new JPanel(new FlowLayout(FlowLayout.LEFT,5,3));styles.setOpaque(false);styles.setAlignmentX(LEFT_ALIGNMENT);
            if(o.kind!=GeoModel.Kind.TEXT&&o.kind!=GeoModel.Kind.SLIDER){
                styles.add(new JLabel("太さ"));
                JSpinner stroke=new JSpinner(new SpinnerNumberModel(o.strokeWidth,0.5,16.0,0.5));
                stroke.setPreferredSize(new Dimension(61,26));
                stroke.addChangeListener(e->{double val=((Number)stroke.getValue()).doubleValue();if(Math.abs(o.strokeWidth-val)>1e-5){change();o.strokeWidth=val;dirty=true;refreshList();canvas.repaint();}});
                styles.add(stroke);
                JComboBox<String> pattern=new JComboBox<>(new String[]{"実線","破線","点線"});
                pattern.setSelectedIndex(switch(o.lineStyle){case "dash"->1;case "dot"->2;default->0;});
                pattern.setPreferredSize(new Dimension(76,26));
                pattern.addActionListener(e->{change();o.lineStyle=switch(pattern.getSelectedIndex()){case 1->"dash";case 2->"dot";default->"solid";};refreshed();});
                styles.add(pattern);
                prop.add(styles);
            }
            if(o.kind==GeoModel.Kind.POLYGON||o.kind==GeoModel.Kind.CIRCLE){
                JPanel fills=new JPanel(new FlowLayout(FlowLayout.LEFT,5,3));fills.setOpaque(false);fills.setAlignmentX(LEFT_ALIGNMENT);
                fills.add(new JLabel("塗りつぶし"));
                JComboBox<String> fill=new JComboBox<>(new String[]{"なし","薄い色","グレー斜線","濃い斜線"});
                fill.setSelectedIndex(!o.filled?0:switch(o.fillStyle){case "hatch"->2;case "dense"->3;default->1;});
                fill.addActionListener(e->{change();o.filled=fill.getSelectedIndex()!=0;o.fillStyle=switch(fill.getSelectedIndex()){case 0->"none";case 2->"hatch";case 3->"dense";default->"light";};refreshed();});
                fills.add(fill);prop.add(fills);
            }
            if(o.kind==GeoModel.Kind.ANGLE&&model.fixedAngleEndpoint(o)!=null){
                GeoModel.Obj ep=model.fixedAngleEndpoint(o);
                JPanel angleEditor=new JPanel(new FlowLayout(FlowLayout.LEFT,5,3));
                angleEditor.setOpaque(false);angleEditor.setAlignmentX(LEFT_ALIGNMENT);
                angleEditor.add(new JLabel("指定角度"));
                JSpinner degrees=new JSpinner(new SpinnerNumberModel(Math.abs(ep.angleDegrees),0.1,359.9,1.0));
                degrees.setPreferredSize(new Dimension(82,26));
                JSpinner.NumberEditor degreeEditor=new JSpinner.NumberEditor(degrees,"0.0#°");
                degrees.setEditor(degreeEditor);angleEditor.add(degrees);
                JComboBox<String> direction=new JComboBox<>(new String[]{"反時計回り","時計回り"});
                direction.setSelectedIndex(ep.angleDegrees<0?1:0);direction.setPreferredSize(new Dimension(105,26));
                angleEditor.add(direction);
                Runnable apply=()->{
                    double next=((Number)degrees.getValue()).doubleValue()*(direction.getSelectedIndex()==1?-1:1);
                    if(Math.abs(ep.angleDegrees-next)>1e-8){
                        change();ep.angleDegrees=next;dirty=true;refreshList();canvas.repaint();
                        setStatus(String.format(Locale.ROOT,"角度を %.2f° に変更しました",Math.abs(next)));
                    }
                };
                degrees.addChangeListener(e->apply.run());direction.addActionListener(e->apply.run());
                prop.add(angleEditor);
            }
            JPanel flags=new JPanel(new FlowLayout(FlowLayout.LEFT,10,4));flags.setOpaque(false);flags.setAlignmentX(LEFT_ALIGNMENT);
            JCheckBox visibility=new JCheckBox("表示",o.visible),labels=new JCheckBox(o.kind==GeoModel.Kind.ANGLE?"角度の数字を表示":"名前表示",o.showLabel);
            for(JCheckBox b:new JCheckBox[]{visibility,labels}){b.setBackground(Color.WHITE);b.setFont(new Font("SansSerif",Font.PLAIN,12));flags.add(b);}
            visibility.addActionListener(e->{change();o.visible=visibility.isSelected();refreshed();});
            labels.addActionListener(e->{change();o.showLabel=labels.isSelected();refreshed();});
            prop.add(flags);
            if(o.kind==GeoModel.Kind.SLIDER){
                JSlider slider=new JSlider(0,1000,(int)Math.round((o.max==o.min?0:(o.value-o.min)/(o.max-o.min))*1000));
                slider.setOpaque(false);slider.setAlignmentX(LEFT_ALIGNMENT);
                slider.addChangeListener(e->{o.value=PlotRenderer.sliderValueFromX(o,30+(int)(202*slider.getValue()/1000.0));
                    canvas.repaint();if(!slider.getValueIsAdjusting()){dirty=true;refreshList();}});
                prop.add(slider);
            }
            JScrollPane propertyScroll=new JScrollPane(prop,ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            propertyScroll.setBorder(null);propertyScroll.getVerticalScrollBar().setUnitIncrement(12);
            inspector.add(propertyScroll,BorderLayout.CENTER);
        }
        inspector.revalidate();inspector.repaint();
    }
    private JMenuBar createMenu(){
        JMenuBar bar=new JMenuBar();bar.setBackground(Color.WHITE);
        JMenu file=new JMenu("ファイル"),edit=new JMenu("編集"),view=new JMenu("表示"),add=new JMenu("作図"),help=new JMenu("ヘルプ");
        menu(file,"新規",KeyEvent.VK_N,()->newDocument());
        menu(file,"開く…",KeyEvent.VK_O,this::openDialog);
        menu(file,"保存",KeyEvent.VK_S,this::save);
        menu(file,"名前を付けて保存…",-1,this::saveAs);
        file.addSeparator();menu(file,"PNG画像として出力…",KeyEvent.VK_E,this::exportDialog);
        menu(file,"PNG切り抜き範囲を解除",-1,()->{if(model.hasCrop){change();model.hasCrop=false;refreshed();}});
        file.addSeparator();menu(file,"終了",-1,()->{if(confirmSave())dispose();});
        menu(edit,"元に戻す",KeyEvent.VK_Z,this::undo);menu(edit,"やり直す",KeyEvent.VK_Y,this::redo);
        menu(edit,"選択した図形を削除",KeyEvent.VK_BACK_SPACE,()->deleteObject(selected));
        for(Tool t:Tool.values())menu(add,t.text,-1,()->setTool(t));
        menu(view,"拡大",KeyEvent.VK_EQUALS,()->zoom(1.25,canvas.getWidth()/2,canvas.getHeight()/2));
        menu(view,"縮小",KeyEvent.VK_MINUS,()->zoom(.8,canvas.getWidth()/2,canvas.getHeight()/2));
        menu(view,"表示位置をリセット",KeyEvent.VK_0,()->{model.centerX=0;model.centerY=0;model.scale=60;canvas.repaint();});
        menu(view,"座標軸：数字あり",-1,()->{axisMode.setSelectedIndex(0);});
        menu(view,"座標軸：数字なし",-1,()->{axisMode.setSelectedIndex(1);});
        menu(view,"座標軸：非表示",-1,()->{axisMode.setSelectedIndex(2);});
        menu(help,"操作ガイド",KeyEvent.VK_F1,()->JOptionPane.showMessageDialog(this,guide(),"操作ガイド",JOptionPane.INFORMATION_MESSAGE));
        menu(help,"このアプリについて",-1,()->JOptionPane.showMessageDialog(this,"GeoSketch 1.2\nJava標準ライブラリのみ使用した独立実装\nネット接続・アカウント・広告なし\nGeoGebraの製品または公式互換実装ではありません。","GeoSketchについて",JOptionPane.INFORMATION_MESSAGE));
        bar.add(file);bar.add(edit);bar.add(view);bar.add(add);bar.add(help);return bar;
    }
    private void menu(JMenu root,String title,int key,Runnable fn){
        JMenuItem item=new JMenuItem(title);item.setFont(UI_FONT);if(key!=-1){
            int mask=Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
            item.setAccelerator(KeyStroke.getKeyStroke(key,mask));
        }item.addActionListener(e->fn.run());root.add(item);
    }
    private static String guide(){return "【主な操作】\n"
        +"・移動：点をドラッグ。空白ドラッグで平行移動\n"
        +"・線分/直線/半直線/円：2点クリック\n"
        +"・多角形：頂点をクリックし、最初の点を再クリックして完成\n"
        +"・中点：2点クリック（動的に追従）\n"
        +"・垂線/平行線：まず直線、次に通過点\n"
        +"・交点：2本の直線や円を選択\n"
        +"・角度：頂点が真ん中になる順に3点クリック\n"
        +"・正多角形：隣接する2頂点をクリック→辺の数と向きを入力。点の移動に連動\n"
        +"・指定角度：点A・頂点Bをクリック→0.1〜359.9°を入力して作図\n"
        +"・角度を選択すると左側で角度の数値・方向・数値表示の有無を変更可能\n"
        +"・数式：f(x)=x^2、y=sin(x)、a=2、A=(1,2)\n"
        +"・マウスホイール：カーソル位置を中心に拡大縮小\n"
        +"・右クリック：図形のメニュー / Esc：作図キャンセル\n"
        +"・Ctrl+S：保存、Ctrl+E：PNG画像出力\n"
        +"・線の色、太さ、実線/破線/点線は図形選択後に左の属性から変更\n"
        +"・多角形の属性でグレー斜線を選択。斜線領域ツールなら任意の範囲を指定\n"
        +"・PNG範囲ツール：ドラッグで切り抜き矩形を設定（座標で保存）\n"
        +"・PNG出力時に図番号と6方向の表示位置を指定可能\n"
        +"・座標軸は数字あり/数字なし/非表示を選択可能";}
    private record RegularSpec(int sides,boolean clockwise) {}
    private RegularSpec regularDialog(){
        JSpinner sides=new JSpinner(new SpinnerNumberModel(5,3,60,1));
        JCheckBox clockwise=new JCheckBox("時計回りに作図",false);
        JPanel pane=new JPanel(new GridLayout(2,2,6,8));
        pane.add(new JLabel("辺（頂点）の数"));pane.add(sides);
        pane.add(new JLabel("作図方向"));pane.add(clockwise);
        int result=JOptionPane.showConfirmDialog(this,pane,"正多角形の作図",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE);
        return result==JOptionPane.OK_OPTION?new RegularSpec(((Number)sides.getValue()).intValue(),clockwise.isSelected()):null;
    }
    private record AngleSpec(double degrees,boolean clockwise,boolean showValue) {}
    private AngleSpec angleDialog(){
        JSpinner degrees=new JSpinner(new SpinnerNumberModel(60.0,0.1,359.9,1.0));
        degrees.setEditor(new JSpinner.NumberEditor(degrees,"0.0#°"));
        JCheckBox clockwise=new JCheckBox("時計回りに作図",false);
        JCheckBox showValue=new JCheckBox("角度の数値を表示",true);
        JPanel pane=new JPanel(new GridLayout(3,2,6,8));
        pane.add(new JLabel("角度（度）"));pane.add(degrees);
        pane.add(new JLabel("向き"));pane.add(clockwise);
        pane.add(new JLabel("図への数値表示"));pane.add(showValue);
        if(JOptionPane.showConfirmDialog(this,pane,"指定角度で作図",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return null;
        return new AngleSpec(((Number)degrees.getValue()).doubleValue(),clockwise.isSelected(),showValue.isSelected());
    }
    private void setTool(Tool t){
        tool=t;pending.clear();if(toggles.get(t)!=null)toggles.get(t).setSelected(true);
        for(var e:toggles.entrySet()){e.getValue().setBackground(e.getKey()==t?new Color(0xE3F0FF):Color.WHITE);
            e.getValue().setForeground(e.getKey()==t?BLUE:TEXT);}
        if(toolHelp!=null)toolHelp.setText("操作："+t.help+"   |   Esc: キャンセル   |   右クリック: オブジェクト操作");
        if(canvas!=null){canvas.setCursor(Cursor.getPredefinedCursor(t==Tool.MOVE?Cursor.DEFAULT_CURSOR:Cursor.CROSSHAIR_CURSOR));canvas.repaint();}
    }
    private void refreshAll(){
        refreshList();renderInspector();if(canvas!=null)canvas.repaint();
        if(gridCheck!=null){updatingView=true;try{
            gridCheck.setSelected(model.showGrid);snapCheck.setSelected(model.snapGrid);
            int index=!model.showAxes?2:(model.showAxisNumbers?0:1);
            if(axisMode.getSelectedIndex()!=index)axisMode.setSelectedIndex(index);
        }finally{updatingView=false;}}
        if(undoBtn!=null)undoBtn.setEnabled(!undo.isEmpty());if(redoBtn!=null)redoBtn.setEnabled(!redo.isEmpty());
        setTitle("GeoSketch — "+(currentFile==null?"新規":currentFile.getName())+(dirty?" *":""));
    }
    private void refreshList(){
        if(listData==null)return;
        updatingList=true;try{
            listData.clear();for(GeoModel.Obj o:model.objects)listData.addElement(o);
            int index=-1;for(int i=0;i<listData.size();i++)if(listData.get(i).id==selected){index=i;break;}
            if(index>=0)objectList.setSelectedIndex(index);else{selected=-1;objectList.clearSelection();}
        }finally{updatingList=false;}
    }
    private void refreshed(){dirty=true;refreshAll();}
    private void change(){undo.push(model.copy());if(undo.size()>100)undo.removeLast();redo.clear();}
    private void undo(){if(undo.isEmpty())return;redo.push(model.copy());model=undo.pop();selected=-1;pending.clear();refreshed();}
    private void redo(){if(redo.isEmpty())return;undo.push(model.copy());model=redo.pop();selected=-1;pending.clear();refreshed();}
    private void deleteObject(int id){if(model.get(id)==null)return;change();model.deleteWithDependents(id);selected=-1;pending.clear();refreshed();}
    private boolean confirmSave(){
        if(!dirty)return true;
        int answer=JOptionPane.showConfirmDialog(this,"変更内容を保存しますか？","未保存の変更",JOptionPane.YES_NO_CANCEL_OPTION);
        if(answer==JOptionPane.CANCEL_OPTION||answer==JOptionPane.CLOSED_OPTION)return false;
        if(answer==JOptionPane.YES_OPTION){save();return !dirty;}return true;
    }
    private void newDocument(){if(!confirmSave())return;model=new GeoModel();currentFile=null;undo.clear();redo.clear();dirty=false;selected=-1;pending.clear();refreshAll();}
    private JFileChooser chooser(String suffix){JFileChooser f=new JFileChooser();
        if(suffix.equals("gsk"))f.setFileFilter(new FileNameExtensionFilter("GeoSketch 作図データ (*.gsk)","gsk"));
        if(suffix.equals("png"))f.setFileFilter(new FileNameExtensionFilter("PNG 画像 (*.png)","png"));
        return f;}
    private File suffix(File f,String ext){return f.getName().toLowerCase(Locale.ROOT).endsWith("."+ext)?f:new File(f.getAbsolutePath()+"."+ext);}
    private void openDialog(){if(!confirmSave())return;JFileChooser c=chooser("gsk");
        if(c.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        try{GeoModel next=GeoModel.load(c.getSelectedFile());model=next;currentFile=c.getSelectedFile();undo.clear();redo.clear();selected=-1;dirty=false;pending.clear();refreshAll();setStatus("開きました: "+currentFile.getName());}
        catch(Exception ex){error("ファイルを開けません: "+ex.getMessage());}
    }
    private void save(){if(currentFile==null){saveAs();return;}try{GeoModel.save(model,currentFile);dirty=false;refreshAll();setStatus("保存しました: "+currentFile.getName());}
        catch(Exception ex){error("保存できません: "+ex.getMessage());}}
    private void saveAs(){JFileChooser c=chooser("gsk");c.setSelectedFile(new File(currentFile==null?"math_diagram.gsk":currentFile.getName()));
        if(c.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        File f=suffix(c.getSelectedFile(),"gsk");if(f.exists()&&!overwrite(f))return;
        currentFile=f;save();}
    private boolean overwrite(File f){return JOptionPane.showConfirmDialog(this,f.getName()+" を上書きしますか？","確認",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION;}
    public static void exportPNG(GeoModel m,File dest,int width,int height)throws IOException{
        PNGExport.save(m,dest,width,height,false,"","左上",0);
    }
    private void exportDialog(){
        JPanel form=new JPanel(new GridLayout(6,2,8,9));
        int dw=Math.min(8000,Math.max(500,canvas.getWidth()*2)),dh=Math.min(8000,Math.max(350,canvas.getHeight()*2));
        JComboBox<String> region=new JComboBox<>(new String[]{"作図画面全体","ドラッグで選択した範囲"});
        if(!model.hasCrop)region.setEnabled(false);else region.setSelectedIndex(1);
        JSpinner width=new JSpinner(new SpinnerNumberModel(dw,100,8000,100));
        JSpinner height=new JSpinner(new SpinnerNumberModel(dh,100,8000,100));
        JLabel heightNotice=new JLabel(" ");heightNotice.setFont(new Font("SansSerif",Font.PLAIN,11));
        JTextField caption=new JTextField("図1");
        JComboBox<String> placement=new JComboBox<>(PNGExport.PLACEMENTS);
        JLabel note=new JLabel(model.hasCrop?"範囲は作図画面のドラッグで再指定できます":"先に「PNG範囲」ツールでドラッグすると切り抜けます");
        note.setFont(new Font("SansSerif",Font.PLAIN,11));
        Runnable recalc=()->{
            boolean useCrop=model.hasCrop&&region.getSelectedIndex()==1;
            height.setEnabled(!useCrop);
            if(useCrop){
                try{int h=PNGExport.croppedHeight(model,((Number)width.getValue()).intValue());
                    heightNotice.setText("自動: "+h+" px（範囲の縦横比を保持）");}
                catch(Exception ex){heightNotice.setText("範囲が不正です");}
            }else heightNotice.setText("高さは自由に指定できます");
        };
        width.addChangeListener(e->recalc.run());region.addActionListener(e->recalc.run());
        form.add(new JLabel("出力範囲"));form.add(region);
        form.add(new JLabel("画像の幅（px）"));form.add(width);
        form.add(new JLabel("画像の高さ（px）"));form.add(height);
        form.add(new JLabel("図番号・文字（空欄ならなし）"));form.add(caption);
        form.add(new JLabel("図番号の位置"));form.add(placement);
        form.add(note);form.add(heightNotice);recalc.run();
        if(JOptionPane.showConfirmDialog(this,form,"PNG出力設定",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return;
        boolean useCrop=model.hasCrop&&region.getSelectedIndex()==1;
        int w=((Number)width.getValue()).intValue(),h=((Number)height.getValue()).intValue();
        try{PNGExport.render(model,w,h,useCrop,caption.getText(),(String)placement.getSelectedItem(),canvas.getWidth());}
        catch(Exception ex){error("PNG出力の設定を確認してください: "+ex.getMessage());return;}
        JFileChooser c=chooser("png");c.setSelectedFile(new File("math_diagram.png"));
        if(c.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        File output=suffix(c.getSelectedFile(),"png");if(output.exists()&&!overwrite(output))return;
        try{PNGExport.save(model,output,w,h,useCrop,caption.getText(),(String)placement.getSelectedItem(),canvas.getWidth());
            setStatus("PNGを書き出しました: "+output.getAbsolutePath());
            JOptionPane.showMessageDialog(this,"PNG出力に成功しました。\n"+output.getAbsolutePath(),"PNG出力",JOptionPane.INFORMATION_MESSAGE);
        }catch(Exception ex){error("PNG出力に失敗: "+ex.getMessage());}
    }
    private void setStatus(String s){if(status!=null)status.setText(s);}
    private void error(String s){JOptionPane.showMessageDialog(this,s,"エラー",JOptionPane.ERROR_MESSAGE);setStatus(s);}
    private GeoModel.Obj objectNamed(String name){for(GeoModel.Obj o:model.objects)if(o.label.equalsIgnoreCase(name))return o;return null;}
    private void addExpression(){
        String input=formulaInput.getText().trim();if(input.isEmpty())return;
        try{
            Matcher pt=Pattern.compile("(?i)^([a-z][a-z0-9_]*)\\s*=\\s*\\(\\s*(.+?)\\s*,\\s*(.+?)\\s*\\)$").matcher(input);
            Matcher fn=Pattern.compile("(?i)^([a-z][a-z0-9_]*)\\s*\\(\\s*x\\s*\\)\\s*=\\s*(.+)$").matcher(input);
            Matcher assign=Pattern.compile("(?i)^([a-z][a-z0-9_]*)\\s*=\\s*(.+)$").matcher(input);
            if(pt.matches()){
                if(objectNamed(pt.group(1))!=null)throw new IllegalArgumentException("同じ名前のオブジェクトが存在します");
                double x=MathExpression.compile(pt.group(2)).eval(model.vars(0));double y=MathExpression.compile(pt.group(3)).eval(model.vars(0));
                if(!Double.isFinite(x)||!Double.isFinite(y))throw new IllegalArgumentException("座標は有限の値にしてください");
                change();GeoModel.Obj o=model.point(x,y);o.label=pt.group(1);selected=o.id;
            }else if(fn.matches()){
                MathExpression.compile(fn.group(2));change();GeoModel.Obj o=model.function(fn.group(1),fn.group(2));selected=o.id;
            }else if(assign.matches()){
                String name=assign.group(1),right=assign.group(2).trim();
                if(name.equalsIgnoreCase("y")){
                    MathExpression.compile(right);change();GeoModel.Obj o=model.function("f"+(model.objects.size()+1),right);selected=o.id;
                }else if(name.equalsIgnoreCase("x")){
                    double x=MathExpression.compile(right).eval(model.vars(0));if(!Double.isFinite(x))throw new IllegalArgumentException("xの値が不正です");
                    change();GeoModel.Obj a=model.point(x,-3),b=model.point(x,3),line=model.line("line",a.id,b.id);a.visible=false;b.visible=false;
                    line.label="x="+String.format(Locale.ROOT,"%.3f",x);selected=line.id;
                }else{
                    double value=MathExpression.compile(right).eval(model.vars(0));
                    if(!Double.isFinite(value))throw new IllegalArgumentException("パラメータの値が不正です");
                    GeoModel.Obj existing=objectNamed(name);
                    if(existing!=null&&existing.kind!=GeoModel.Kind.SLIDER)throw new IllegalArgumentException("同じ名前の図形があります");
                    change();
                    if(existing!=null){existing.value=value;selected=existing.id;}
                    else{double range=Math.max(5,Math.ceil(Math.abs(value)*1.4));GeoModel.Obj o=model.slider(name,value,-range,range,.1);selected=o.id;}
                }
            }else{
                MathExpression.compile(input);change();GeoModel.Obj o=model.function("f"+(model.objects.size()+1),input);selected=o.id;
            }
            formulaInput.setText("");refreshed();setStatus("追加しました: "+input);
        }catch(Exception ex){error("入力を確認してください: "+ex.getMessage());}
    }
    private void zoom(double factor,int x,int y){
        double oldX=model.centerX+(x-canvas.getWidth()/2.0)/model.scale;
        double oldY=model.centerY-(y-canvas.getHeight()/2.0)/model.scale;
        double next=Math.max(1,Math.min(2e5,model.scale*factor));model.scale=next;
        model.centerX=oldX-(x-canvas.getWidth()/2.0)/next;
        model.centerY=oldY+(y-canvas.getHeight()/2.0)/next;
        dirty=true;canvas.repaint();
    }
    private class PlotPanel extends JPanel {
        private int mouseX=0,mouseY=0;
        private boolean dragging=false,moved=false;
        private Point initialMouse;
        private Point2D.Double initialCenter;
        private GeoModel dragBefore;
        private int dragPoint=-1,dragSlider=-1;
        private Point cropStart=null,cropEnd=null;
        PlotPanel(){
            setOpaque(true);setBackground(Color.WHITE);setFocusable(true);
            addMouseWheelListener(e->zoom(Math.pow(1.13,-e.getPreciseWheelRotation()),e.getX(),e.getY()));
            MouseAdapter listener=new MouseAdapter(){
                @Override public void mouseMoved(MouseEvent e){mouseX=e.getX();mouseY=e.getY();showCoords(e);repaint();}
                @Override public void mousePressed(MouseEvent e){
                    requestFocusInWindow();mouseX=e.getX();mouseY=e.getY();
                    if(SwingUtilities.isRightMouseButton(e)){
                        int id=hit(e.getX(),e.getY());if(id!=-1){selected=id;refreshList();renderInspector();popup(e,id);}return;
                    }
                    initialMouse=e.getPoint();initialCenter=new Point2D.Double(model.centerX,model.centerY);
                    moved=false;dragging=false;dragPoint=-1;dragSlider=-1;dragBefore=null;
                    if(tool==Tool.CROP){cropStart=e.getPoint();cropEnd=e.getPoint();repaint();return;}
                    if(tool==Tool.MOVE){
                        int id=hit(e.getX(),e.getY());selected=id;refreshList();renderInspector();
                        GeoModel.Obj o=model.get(id);
                        if(o!=null&&o.kind==GeoModel.Kind.POINT&&o.mode.isEmpty()){dragPoint=id;dragBefore=model.copy();}
                        else if(o!=null&&o.kind==GeoModel.Kind.SLIDER){dragSlider=id;dragBefore=model.copy();}
                    }
                    repaint();
                }
                @Override public void mouseDragged(MouseEvent e){
                    mouseX=e.getX();mouseY=e.getY();if(initialMouse==null)return;
                    if(e.getPoint().distance(initialMouse)<2&&!dragging)return;
                    dragging=true;moved=true;
                    if(tool==Tool.CROP){cropEnd=e.getPoint();setStatus("PNGの出力範囲をドラッグ中（ボタンを離して確定）");repaint();return;}
                    if(tool==Tool.MOVE && dragPoint!=-1){
                        GeoModel.Obj o=model.get(dragPoint);Point2D.Double p=world(e.getX(),e.getY());p=snap(p);o.x=p.x;o.y=p.y;
                        dirty=true;refreshList();
                    }else if(tool==Tool.MOVE && dragSlider!=-1){
                        GeoModel.Obj o=model.get(dragSlider);o.value=PlotRenderer.sliderValueFromX(o,e.getX());dirty=true;refreshList();
                    }else{
                        model.centerX=initialCenter.x-(e.getX()-initialMouse.x)/model.scale;
                        model.centerY=initialCenter.y+(e.getY()-initialMouse.y)/model.scale;dirty=true;
                    }
                    showCoords(e);repaint();
                }
                @Override public void mouseReleased(MouseEvent e){
                    if(tool==Tool.CROP&&cropStart!=null){
                        cropEnd=e.getPoint();
                        if(Math.abs(cropEnd.x-cropStart.x)>=12&&Math.abs(cropEnd.y-cropStart.y)>=12){
                            Point2D.Double p=world(cropStart.x,cropStart.y),q=world(cropEnd.x,cropEnd.y);
                            change();model.setCrop(p.x,p.y,q.x,q.y);refreshed();
                            setStatus("PNGの切り抜き範囲を設定しました。右上のPNG出力を押してください");
                        }else setStatus("12px以上の大きさでドラッグして範囲を指定してください");
                        cropStart=null;cropEnd=null;dragging=false;initialMouse=null;repaint();return;
                    }
                    if(dragging&&dragBefore!=null){undo.push(dragBefore);if(undo.size()>100)undo.removeLast();redo.clear();refreshAll();}
                    dragging=false;dragBefore=null;initialMouse=null;
                }
                @Override public void mouseClicked(MouseEvent e){
                    if(!SwingUtilities.isLeftMouseButton(e)||moved)return;
                    if(tool==Tool.MOVE||tool==Tool.CROP)return;
                    mouseX=e.getX();mouseY=e.getY();handleClick(e);repaint();
                }
            };
            addMouseListener(listener);addMouseMotionListener(listener);
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),"cancel-tool");
            getActionMap().put("cancel-tool",new AbstractAction(){@Override public void actionPerformed(ActionEvent e){pending.clear();setStatus("作図をキャンセルしました");repaint();}});
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE,0),"delete-object");
            getActionMap().put("delete-object",new AbstractAction(){@Override public void actionPerformed(ActionEvent e){deleteObject(selected);}});
        }
        private void popup(MouseEvent e,int id){
            JPopupMenu popup=new JPopupMenu();
            GeoModel.Obj o=model.get(id);if(o==null)return;
            JMenuItem toggle=new JMenuItem(o.visible?"非表示":"表示");toggle.addActionListener(a->{change();o.visible=!o.visible;refreshed();});popup.add(toggle);
            JMenuItem rename=new JMenuItem("名前・色を変更（左のプロパティ）");rename.addActionListener(a->{selected=id;refreshAll();});popup.add(rename);
            JMenuItem remove=new JMenuItem("この図形を削除");remove.addActionListener(a->deleteObject(id));popup.add(remove);
            popup.show(this,e.getX(),e.getY());
        }
        private void showCoords(MouseEvent e){Point2D.Double p=world(e.getX(),e.getY());
            setStatus(String.format(Locale.ROOT,"x = %.3f   y = %.3f   |   倍率 %.0f px/単位   |   図形 %d個",p.x,p.y,model.scale,model.objects.size()));}
        private PlotRenderer.View view(){return new PlotRenderer.View(model,getWidth(),getHeight());}
        private int hit(int x,int y){return PlotRenderer.hit(view(),x,y);}
        private Point2D.Double world(int x,int y){PlotRenderer.View v=view();return GeoModel.p(v.wx(x),v.wy(y));}
        private Point2D.Double snap(Point2D.Double p){
            if(!model.snapGrid)return p;
            double n=60/model.scale,power=Math.pow(10,Math.floor(Math.log10(n))),step=10*power;
            for(double x:new double[]{1,2,5,10})if(x*power>=n){step=x*power;break;}
            return GeoModel.p(Math.rint(p.x/step)*step,Math.rint(p.y/step)*step);
        }
        /** A new point is created only after the user's click, preserving existing shared points. */
        private int ensurePoint(int px,int py){
            int id=hit(px,py);GeoModel.Obj p=model.get(id);
            if(p!=null&&p.kind==GeoModel.Kind.POINT)return id;
            change();Point2D.Double q=snap(world(px,py));GeoModel.Obj n=model.point(q.x,q.y);refreshed();return n.id;
        }
        private void handleClick(MouseEvent e){
            int id=hit(e.getX(),e.getY());GeoModel.Obj clicked=model.get(id);
            switch(tool){
                case POINT->{int p=ensurePoint(e.getX(),e.getY());selected=p;refreshAll();}
                case SEGMENT,LINE,RAY,CIRCLE,MIDPOINT,DISTANCE,ANGLE->{
                    int p=ensurePoint(e.getX(),e.getY());
                    if(tool==Tool.ANGLE){pending.add(p);if(pending.size()==3){
                        change();GeoModel.Obj o=model.angle(pending.get(0),pending.get(1),pending.get(2));selected=o.id;pending.clear();refreshed();}
                    }else{
                        if(pending.size()==0)pending.add(p);
                        else if(pending.get(0)!=p){
                            change();GeoModel.Obj o=switch(tool){
                                case SEGMENT->model.line("segment",pending.get(0),p);
                                case LINE->model.line("line",pending.get(0),p);
                                case RAY->model.line("ray",pending.get(0),p);
                                case CIRCLE->model.circle(pending.get(0),p);
                                case MIDPOINT->model.midpoint(pending.get(0),p);
                                case DISTANCE->model.distance(pending.get(0),p);
                                default->throw new IllegalStateException();};
                            selected=o.id;pending.clear();refreshed();
                        }
                    }
                }
                case REGULAR_POLYGON,FIXED_ANGLE->{
                    int p=ensurePoint(e.getX(),e.getY());
                    if(pending.isEmpty()){pending.add(p);setStatus(tool==Tool.REGULAR_POLYGON?"隣り合う2番目の頂点をクリックしてください":"角の頂点Bをクリックしてください");}
                    else if(pending.get(0)!=p){
                        int first=pending.get(0);
                        Point2D.Double aa=model.pointOf(first),bb=model.pointOf(p);
                        if(aa==null||bb==null||aa.distance(bb)<1e-7){setStatus("異なる2点をクリックしてください");break;}
                        if(tool==Tool.REGULAR_POLYGON){
                            RegularSpec spec=regularDialog();
                            if(spec!=null){change();GeoModel.Obj o=model.regularPolygon(first,p,spec.sides(),spec.clockwise());selected=o.id;refreshed();}
                        }else{
                            AngleSpec spec=angleDialog();
                            if(spec!=null){change();GeoModel.Obj o=model.fixedAngle(first,p,spec.degrees(),spec.clockwise(),spec.showValue());selected=o.id;refreshed();}
                        }
                        pending.clear();
                    }
                }
                case POLYGON,HATCH->{
                    if(!pending.isEmpty()&&pending.size()>=3&&id==pending.get(0)){
                        change();GeoModel.Obj o=model.polygon(pending);
                        if(tool==Tool.HATCH){o.filled=true;o.fillStyle="hatch";o.color=0x666666;o.showLabel=false;}
                        selected=o.id;pending.clear();refreshed();
                    }else{
                        int p=ensurePoint(e.getX(),e.getY());
                        if(pending.isEmpty()||pending.get(pending.size()-1)!=p)pending.add(p);
                        if(e.getClickCount()>=2&&pending.size()>=3){
                            change();GeoModel.Obj o=model.polygon(pending);
                            if(tool==Tool.HATCH){o.filled=true;o.fillStyle="hatch";o.color=0x666666;o.showLabel=false;}
                            selected=o.id;pending.clear();refreshed();
                        }
                    }
                }
                case PARALLEL,PERP->{
                    if(pending.isEmpty()){
                        if(clicked==null||clicked.kind!=GeoModel.Kind.LINE){setStatus("先に基準となる直線をクリックしてください");return;}
                        pending.add(id);
                    }else{
                        int p=ensurePoint(e.getX(),e.getY());change();GeoModel.Obj o=model.derivedLine(tool==Tool.PARALLEL?"parallel":"perp",pending.get(0),p);
                        selected=o.id;pending.clear();refreshed();
                    }
                }
                case INTERSECTION->{
                    if(clicked==null||(clicked.kind!=GeoModel.Kind.LINE&&clicked.kind!=GeoModel.Kind.CIRCLE)){
                        setStatus("直線または円の線上をクリックしてください");return;
                    }
                    if(pending.isEmpty())pending.add(id);
                    else if(pending.get(0)!=id){
                        List<Point2D.Double> solutions=model.intersections(model.get(pending.get(0)),clicked);
                        if(solutions.isEmpty()){setStatus("交点がありません");pending.clear();return;}
                        Point2D.Double near=world(e.getX(),e.getY());
                        Point2D.Double p=solutions.stream().min(Comparator.comparingDouble(q->q.distanceSq(near))).orElse(solutions.get(0));
                        change();GeoModel.Obj o=model.intersection(pending.get(0),id,p);selected=o.id;pending.clear();refreshed();
                    }
                }
                case TEXT->{
                    String str=JOptionPane.showInputDialog(GeoSketch.this,"図に配置する文字列","テキスト",JOptionPane.QUESTION_MESSAGE);
                    if(str!=null&&!str.isBlank()){Point2D.Double p=world(e.getX(),e.getY());change();GeoModel.Obj o=model.text(str,p.x,p.y);selected=o.id;refreshed();}
                }
                case DELETE->{if(id!=-1)deleteObject(id);}
                default->{}
            }
            if(!pending.isEmpty())setStatus("作図中："+tool.text+"（"+pending.size()+"点/要素を選択済み）   Escで取り消し");
        }
        @Override protected void paintComponent(Graphics gg){
            super.paintComponent(gg);Graphics2D g=(Graphics2D)gg.create();
            try{
                PlotRenderer.draw(g,model,getWidth(),getHeight(),selected,true);
                if(!pending.isEmpty()&&tool!=Tool.MOVE){
                    PlotRenderer.View v=view();g.setColor(new Color(0x2179CF));
                    g.setStroke(new BasicStroke(1.5f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_ROUND,10,new float[]{6,5},0));
                    if(tool==Tool.POLYGON||tool==Tool.HATCH){
                        Point2D.Double prev=null;
                        for(int id:pending){Point2D.Double p=model.pointOf(id);if(p==null)continue;Point2D.Double q=v.pixel(p);
                            if(prev!=null)g.draw(new Line2D.Double(prev,q));prev=q;}
                        if(prev!=null)g.draw(new Line2D.Double(prev.x,prev.y,mouseX,mouseY));
                    }else if(tool==Tool.SEGMENT||tool==Tool.LINE||tool==Tool.RAY||tool==Tool.CIRCLE||tool==Tool.MIDPOINT||tool==Tool.DISTANCE||tool==Tool.REGULAR_POLYGON||tool==Tool.FIXED_ANGLE){
                        Point2D.Double p=model.pointOf(pending.get(0));if(p!=null){Point2D.Double center=v.pixel(p);
                            if(tool==Tool.CIRCLE){double r=center.distance(mouseX,mouseY);g.draw(new Ellipse2D.Double(center.x-r,center.y-r,r*2,r*2));}
                            else g.draw(new Line2D.Double(center.x,center.y,mouseX,mouseY));}
                    }
                }
                if(model.hasCrop||cropStart!=null){
                    Rectangle r=null;
                    if(cropStart!=null&&cropEnd!=null){r=new Rectangle(Math.min(cropStart.x,cropEnd.x),Math.min(cropStart.y,cropEnd.y),
                        Math.abs(cropStart.x-cropEnd.x),Math.abs(cropStart.y-cropEnd.y));}
                    else if(model.hasCrop){PlotRenderer.View v=view();
                        int x=(int)Math.round(v.sx(model.cropMinX)),y=(int)Math.round(v.sy(model.cropMaxY));
                        int w=(int)Math.round((model.cropMaxX-model.cropMinX)*model.scale),
                            h=(int)Math.round((model.cropMaxY-model.cropMinY)*model.scale);
                        r=new Rectangle(x,y,Math.max(1,w),Math.max(1,h));}
                    if(r!=null){
                        if(tool==Tool.CROP){
                            g.setColor(new Color(40,64,98,37));
                            java.awt.geom.Area outside=new java.awt.geom.Area(new Rectangle(0,0,getWidth(),getHeight()));
                            outside.subtract(new java.awt.geom.Area(r));g.fill(outside);
                        }
                        g.setColor(new Color(28,115,198));
                        g.setStroke(new BasicStroke(2f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_ROUND,10,new float[]{9,5},0));
                        g.draw(r);
                        g.setFont(new Font("SansSerif",Font.BOLD,12));
                        g.setColor(new Color(20,88,149));g.drawString("PNG出力範囲",Math.max(6,r.x+5),Math.max(15,r.y-7));
                    }
                }
            }finally{g.dispose();}
        }
    }
    public static void main(String[] args){
        if(args.length>0){
            if(args[0].equals("--self-test")||args[0].equals("--feature-test")||args[0].equals("--geometry-test")||args[0].equals("--export")||args[0].equals("--export-crop"))System.setProperty("java.awt.headless","true");
            try {
                if(args[0].equals("--self-test")){SmokeTest.run(args.length>=2?new File(args[1]):new File("geosketch_sample.png"));return;}
                if(args[0].equals("--feature-test")){FeatureTest.run(args.length>=2?new File(args[1]):new File("geosketch_features.png"));return;}
                if(args[0].equals("--geometry-test")){GeometryTest.run(args.length>=2?new File(args[1]):new File("geosketch_geometry.png"));return;}
                if(args[0].equals("--export")){
                    if(args.length!=5)throw new IllegalArgumentException("使用法: --export 入力.gsk 出力.png 幅 高さ");
                    GeoModel m=GeoModel.load(new File(args[1]));exportPNG(m,new File(args[2]),Integer.parseInt(args[3]),Integer.parseInt(args[4]));
                    System.out.println("PNG OK: "+args[2]);return;
                }
                if(args[0].equals("--export-crop")){
                    if(args.length<4||args.length>6)throw new IllegalArgumentException("使用法: --export-crop 入力.gsk 出力.png 幅 [図番号] [位置]");
                    GeoModel m=GeoModel.load(new File(args[1]));int w=Integer.parseInt(args[3]);
                    PNGExport.save(m,new File(args[2]),w,0,true,args.length>=5?args[4]:"",args.length>=6?args[5]:"左上",0);
                    System.out.println("CROPPED PNG OK: "+args[2]);return;
                }
                if(args[0].equals("--screenshot")){
                    if(args.length<2)throw new IllegalArgumentException("使用法: --screenshot 出力.png [入力.gsk]");
                    final GeoSketch[] holder=new GeoSketch[1];
                    SwingUtilities.invokeAndWait(()->{
                        GeoSketch app=new GeoSketch();
                        if(args.length>=3)try{app.model=GeoModel.load(new File(args[2]));
                            if(args.length>=4){int sid=Integer.parseInt(args[3]);if(app.model.get(sid)!=null)app.selected=sid;}
                            app.refreshAll();}catch(Exception err){throw new RuntimeException(err);}
                        app.setVisible(true);holder[0]=app;
                    });
                    Thread.sleep(1000);
                    GeoSketch app=holder[0];
                    Point loc=app.getLocationOnScreen();
                    Rectangle bounds=new Rectangle(loc.x,loc.y,app.getWidth(),app.getHeight());
                    BufferedImage shot=new Robot().createScreenCapture(bounds);
                    ImageIO.write(shot,"png",new File(args[1]));
                    SwingUtilities.invokeAndWait(app::dispose);
                    System.out.println("GUI screenshot: "+args[1]);return;
                }
                if(args[0].equals("--help")){System.out.println("GeoSketch 1.2\njava -jar GeoSketch.jar\njava -jar GeoSketch.jar --self-test sample.png\njava -jar GeoSketch.jar --export diagram.gsk diagram.png 1600 1000\njava -jar GeoSketch.jar --export-crop diagram.gsk crop.png 1600 図1 右下");return;}
            }catch(Exception e){System.err.println("GeoSketch error: "+e.getMessage());System.exit(1);}
        }
        SwingUtilities.invokeLater(()->{
            try{UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());}catch(Exception ignored){}
            UIManager.put("OptionPane.messageFont",UI_FONT);
            GeoSketch ui=new GeoSketch();ui.setVisible(true);
        });
    }
}
