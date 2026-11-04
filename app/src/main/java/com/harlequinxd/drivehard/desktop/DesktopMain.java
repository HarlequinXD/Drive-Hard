package com.harlequinxd.drivehard.desktop;

import com.harlequinxd.drivehard.core.Game;
import com.harlequinxd.drivehard.core.map.MapData;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;

/** Native Windows desktop frontend retaining Drive Hard's original software renderer and game simulation. */
public final class DesktopMain extends JPanel implements KeyListener, Runnable {
    private static final int W=480, H=320;
    private static final float MIN_MAP_ZOOM=0.5f, MAX_MAP_ZOOM=12f;
    private final Set<Integer> keys = ConcurrentHashMap.newKeySet();
    private final AtomicInteger mapWheelSteps = new AtomicInteger(), mapZoomClicks = new AtomicInteger();
    private final AtomicInteger mapDragX = new AtomicInteger(), mapDragY = new AtomicInteger();
    private final XboxInput pad = new XboxInput();
    private final BufferedImage image = new BufferedImage(W,H,BufferedImage.TYPE_INT_ARGB);
    private volatile String loading = "Loading world...";
    private volatile Game game;
    private volatile boolean running = true;
    private volatile boolean fullscreen;
    private JFrame window;
    private boolean lastJump,lastCar,lastFullscreen;
    private volatile boolean imageReady;
    private float fps;
    private volatile boolean mapOpen, travelMenu;
    private volatile float mapCursorX, mapCursorZ;
    private volatile MapData.Label selectedDestination;
    private boolean lastSelect, lastConfirm, lastCancel;
    private float mapZoom=1f, mapViewX, mapViewZ;
    private volatile int pointerX, pointerY;
    private volatile boolean mapDragging;
    private BufferedImage mapImage;
    private volatile boolean mapImageReady;


    private DesktopMain() {
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);
        addMouseWheelListener(e -> { if(mapOpen) mapWheelSteps.addAndGet(e.getWheelRotation()); });
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (!mapOpen || !SwingUtilities.isLeftMouseButton(e)) return;
                requestFocusInWindow();
                pointerX=e.getX(); pointerY=e.getY(); mapDragging=true;
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) mapDragging=false;
            }
            @Override public void mouseClicked(MouseEvent e) {
                if (!mapOpen || !SwingUtilities.isLeftMouseButton(e)) return;
                float x=toInternalX(e.getX()), y=toInternalY(e.getY());
                if (y>=2 && y<19 && x>=W-48 && x<W-28) mapZoomClicks.incrementAndGet();
                else if (y>=2 && y<19 && x>=W-25 && x<W-5) mapZoomClicks.decrementAndGet();
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) { pointerX=e.getX(); pointerY=e.getY(); }
            @Override public void mouseDragged(MouseEvent e) {
                if (mapOpen && mapDragging) {
                    mapDragX.addAndGet(e.getX()-pointerX);
                    mapDragY.addAndGet(e.getY()-pointerY);
                }
                pointerX=e.getX(); pointerY=e.getY();
            }
        });
        setPreferredSize(new Dimension(1200,800));
    }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            DesktopMain view = new DesktopMain();
            JFrame frame = new JFrame("Drive Hard - Windows");
            view.window = frame;
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() {
                @Override public void windowClosed(WindowEvent e) { view.running=false; }
            });
            frame.setContentPane(view);
            frame.pack(); frame.setLocationRelativeTo(null); frame.setVisible(true);
            view.requestFocusInWindow();
            new Thread(view,"drive-hard-loop").start();
        });
    }
    @Override public void run() {
        try {
            Game g = new Game(Game.DEFAULT_SEED, s -> loading=s);
            g.setResolution(W,H);
            game=g;
            mapCursorX=g.mapX(); mapCursorZ=g.mapZ();
            mapImage = new BufferedImage(MapData.SIZE,MapData.SIZE,BufferedImage.TYPE_INT_ARGB);
            mapImage.setRGB(0,0,MapData.SIZE,MapData.SIZE,g.map.relief,0,MapData.SIZE);
            mapImageReady=true;
            long previous=System.nanoTime(), stat=previous;
            int frames=0;
            while(running) {
                long start=System.nanoTime();
                float dt=Math.min(.05f,(start-previous)/1_000_000_000f);
                previous=start;
                boolean connected=pad.poll();
                boolean select=down(KeyEvent.VK_M)||(connected&&pad.button(XboxInput.BACK));
                boolean confirm=down(KeyEvent.VK_ENTER)||(connected&&pad.button(XboxInput.A));
                boolean cancel=down(KeyEvent.VK_ESCAPE)||(connected&&pad.button(XboxInput.B));
                if(select&&!lastSelect) {
                    mapOpen=!mapOpen; travelMenu=false; selectedDestination=null;
                    if(mapOpen) { mapCursorX=g.mapX(); mapCursorZ=g.mapZ(); mapViewX=mapCursorX; mapViewZ=mapCursorZ; mapZoom=1f; }
                }
                lastSelect=select;
                if(mapOpen) {
                    if(cancel&&!lastCancel) { if(travelMenu) travelMenu=false; else mapOpen=false; }
                    if(travelMenu) {
                        if(confirm&&!lastConfirm && selectedDestination!=null) {
                            g.fastTravel(selectedDestination);
                            mapOpen=false; travelMenu=false;
                        }
                    } else {
                        float panX=(connected?pad.lx():0f)+((down(KeyEvent.VK_D)||down(KeyEvent.VK_RIGHT))?1:0)-((down(KeyEvent.VK_A)||down(KeyEvent.VK_LEFT))?1:0);
                        float panZ=-(connected?pad.ly():0f)+((down(KeyEvent.VK_S)||down(KeyEvent.VK_DOWN))?1:0)-((down(KeyEvent.VK_W)||down(KeyEvent.VK_UP))?1:0);
                        // Fast cursor movement independent of the window size; zoom controls view scale.
                        float zoomIn=(connected?pad.rt():0f)+((down(KeyEvent.VK_EQUALS)||down(KeyEvent.VK_ADD)||down(KeyEvent.VK_PAGE_UP)||down(KeyEvent.VK_CLOSE_BRACKET))?1:0);
                        float zoomOut=(connected?pad.lt():0f)+((down(KeyEvent.VK_MINUS)||down(KeyEvent.VK_SUBTRACT)||down(KeyEvent.VK_PAGE_DOWN)||down(KeyEvent.VK_OPEN_BRACKET))?1:0);
                        int wheelSteps=mapWheelSteps.getAndSet(0);
                        int zoomClicks=mapZoomClicks.getAndSet(0);
                        mapZoom=Math.max(MIN_MAP_ZOOM,Math.min(MAX_MAP_ZOOM,mapZoom*(float)Math.exp((zoomIn-zoomOut)*2.4f*dt-wheelSteps*0.22f+zoomClicks*0.22f)));
                        int panelWidth=Math.min(getWidth(),(int)((long)getHeight()*W/H));
                        int panelHeight=(int)((long)panelWidth*H/W);
                        float dragX=panelWidth>0?mapDragX.getAndSet(0)*W/(float)panelWidth:0f;
                        float dragY=panelHeight>0?mapDragY.getAndSet(0)*H/(float)panelHeight:0f;
                        float mapUnitsPerPixel=g.map.extent/(mapZoom*H);
                        mapViewX-=dragX*mapUnitsPerPixel;
                        mapViewZ-=dragY*mapUnitsPerPixel;
                        if(dragX!=0f || dragY!=0f) { mapCursorX=mapViewX; mapCursorZ=mapViewZ; }
                        float speed=(g.map.extent/mapZoom)*0.85f*dt;
                        mapCursorX=Math.max(g.map.x0,Math.min(g.map.x0+g.map.extent,mapCursorX+panX*speed));
                        mapCursorZ=Math.max(g.map.z0,Math.min(g.map.z0+g.map.extent,mapCursorZ+panZ*speed));
                        // Follow the cursor smoothly so zoomed-in maps can traverse the whole world.
                        float follow=Math.min(1f,dt*12f);
                        mapViewX+=(mapCursorX-mapViewX)*follow;
                        mapViewZ+=(mapCursorZ-mapViewZ)*follow;
                        clampMapView(g);
                        if(confirm&&!lastConfirm) {
                            MapData.Label nearest=null; float best=12f*mapUnitsPerPixel;
                            for(MapData.Label label:g.map.labels) {
                                if(label.rank==4) continue;
                                float d=(float)Math.hypot(label.x-mapCursorX,label.z-mapCursorZ);
                                if(d<best) {best=d;nearest=label;}
                            }
                            if(nearest!=null) { selectedDestination=nearest; travelMenu=true; }
                        }
                    }
                    lastConfirm=confirm;lastCancel=cancel;
                    synchronized(image) { renderMapFrame(g); imageReady=true; }
                    repaint();
                    java.util.concurrent.locks.LockSupport.parkNanos(16_666_667L);
                    previous=System.nanoTime();
                    continue;
                }
                lastConfirm=confirm;lastCancel=cancel;
                float lx=connected?pad.lx():0f, ly=connected?pad.ly():0f;
                float rx=connected?pad.rx():0f, ry=connected?pad.ry():0f;
                float x=clamp(lx+((down(KeyEvent.VK_D)||down(KeyEvent.VK_RIGHT))?1:0)-((down(KeyEvent.VK_A)||down(KeyEvent.VK_LEFT))?1:0));
                float y=clamp(ly+((down(KeyEvent.VK_W)||down(KeyEvent.VK_UP))?1:0)-((down(KeyEvent.VK_S)||down(KeyEvent.VK_DOWN))?1:0));
                boolean jump=down(KeyEvent.VK_SPACE)||(connected&&pad.button(XboxInput.A));
                boolean car=down(KeyEvent.VK_E)||(connected&&pad.button(XboxInput.Y));
                boolean fs=down(KeyEvent.VK_F11)||(connected&&pad.button(XboxInput.START));
                if(fs&&!lastFullscreen) SwingUtilities.invokeLater(this::toggleFullscreen);
                lastFullscreen=fs;
                float steer=clamp(x);
                float gas=connected?pad.rt():0f;
                float brake=connected?pad.lt():0f;
                if(down(KeyEvent.VK_W)||down(KeyEvent.VK_UP)) gas=1f;
                if(down(KeyEvent.VK_S)||down(KeyEvent.VK_DOWN)) brake=1f;
                g.update(dt,x,y,-rx*2.5f*dt,-ry*1.7f*dt,jump&&!lastJump,steer,gas,brake,car&&!lastCar);
                lastJump=jump;lastCar=car;
                g.render();
                int[] pixels=((DataBufferInt)image.getRaster().getDataBuffer()).getData();
                synchronized(image) {
                    System.arraycopy(g.renderer.fb,0,pixels,0,pixels.length);
                    renderMapFrame(g);
                    imageReady=true;
                }
                repaint();
                frames++;
                if(start-stat>1_000_000_000L) {fps=frames*1_000_000_000f/(start-stat);frames=0;stat=start;}
                long wait=16_666_667L-(System.nanoTime()-start);
                if(wait>0) java.util.concurrent.locks.LockSupport.parkNanos(wait);
            }
        } catch(Throwable e) {
            e.printStackTrace();
            loading="Error: "+e;
            repaint();
        } finally { if(game!=null) game.dispose(); }
    }
    private static float clamp(float x) {return Math.max(-1f,Math.min(1f,x));}
    private static float clamp(float x,float min,float max) {return Math.max(min,Math.min(max,x));}
    private float toInternalX(int x) {
        int dw=Math.min(getWidth(),(int)((long)getHeight()*W/H));
        return dw<=0?-1f:(x-(getWidth()-dw)/2f)*W/dw;
    }
    private float toInternalY(int y) {
        int dw=Math.min(getWidth(),(int)((long)getHeight()*W/H)),dh=(int)((long)dw*H/W);
        return dh<=0?-1f:(y-(getHeight()-dh)/2f)*H/dh;
    }
    private void clampMapView(Game g) {
        float viewHeight=g.map.extent/mapZoom, viewWidth=viewHeight*W/H;
        float halfX=Math.min(g.map.extent*0.5f,viewWidth*0.5f),halfZ=Math.min(g.map.extent*0.5f,viewHeight*0.5f);
        mapViewX=clamp(mapViewX,g.map.x0+halfX,g.map.x0+g.map.extent-halfX);
        mapViewZ=clamp(mapViewZ,g.map.z0+halfZ,g.map.z0+g.map.extent-halfZ);
    }
    private boolean down(int k) {return keys.contains(k);}
    private void toggleFullscreen() {
        GraphicsDevice device=GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        window.dispose();
        fullscreen=!fullscreen;
        window.setUndecorated(fullscreen);
        window.setResizable(!fullscreen);
        if(fullscreen) device.setFullScreenWindow(window);
        else {device.setFullScreenWindow(null);window.setSize(1200,800);window.setLocationRelativeTo(null);window.setVisible(true);}
        requestFocusInWindow();
    }
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if(game==null) {
            g.setColor(Color.WHITE);g.setFont(new Font(Font.MONOSPACED,Font.BOLD,22));
            g.drawString("DRIVE HARD",30,60);g.setFont(new Font(Font.MONOSPACED,Font.PLAIN,15));g.drawString(loading,30,90);
            return;
        }
        int width=getWidth(),height=getHeight();
        int dw=Math.min(width,(int)((long)height*W/H)),dh=(int)((long)dw*H/W);
        int x=(width-dw)/2,y=(height-dh)/2;
        if(imageReady) synchronized(image) { g.drawImage(image,x,y,dw,dh,null); }
    }
    /** All HUD/map drawing occurs at the internal game resolution, then scales with the framebuffer. */
    private void renderMapFrame(Game current) {
        if(!mapImageReady) return;
        Graphics2D gg=image.createGraphics();
        try {
            gg.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            gg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if(mapOpen) {
                gg.setColor(new Color(7,12,18));gg.fillRect(0,0,W,H);
                drawMap(gg,0,0,W,H,true);
            } else {
                int x=W-132,y=8,w=124,h=140,size=112;
                gg.setColor(new Color(4,10,15,225));gg.fillRoundRect(x,y,w,h,4,4);
                gg.setColor(new Color(66,94,101));gg.drawRoundRect(x,y,w-1,h-1,4,4);
                gg.setFont(new Font(Font.MONOSPACED,Font.BOLD,8));gg.setColor(new Color(231,239,233));
                gg.drawString("LOCAL MAP",x+7,y+12);
                gg.setFont(new Font(Font.MONOSPACED,Font.PLAIN,7));gg.setColor(new Color(120,215,221));
                gg.drawString("FOLLOW / 14x",x+66,y+12);
                drawMap(gg,x+6,y+18,size,size,false);
                gg.setColor(new Color(76,104,111));gg.drawRect(x+6,y+18,size-1,size-1);
                gg.setFont(new Font(Font.MONOSPACED,Font.PLAIN,7));gg.setColor(new Color(183,200,196));
                gg.drawString("N",x+10,y+27);gg.drawString("PLAYER",x+15,y+136);
                gg.setColor(new Color(83,231,243));gg.fillRect(x+7,y+130,4,4);
            }
        } finally { gg.dispose(); }
    }
    private void drawMap(Graphics2D gg,int x,int y,int width,int height,boolean full) {
        Game current=game;
        if(current==null || mapImage==null) return;
        MapData m=current.map;
        // Zooming is performed in world-space and never in window pixels.
        float cx=full?mapViewX:current.mapX();
        float cz=full?mapViewZ:current.mapZ();
        float visible=m.extent/(full?mapZoom:14f);
        float worldWidth=visible;
        float worldHeight=visible*height/(float)width;
        if(full) { worldHeight=m.extent/mapZoom; worldWidth=worldHeight*width/(float)height; }
        float left=cx-worldWidth/2f,top=cz-worldHeight/2f;
        Graphics2D g=(Graphics2D)gg.create();
        try {
            g.setColor(new Color(14,23,30));g.fillRect(x,y,width,height);
            g.setClip(x,y,width,height);
            int ix=x+Math.round((m.x0-left)*width/worldWidth);
            int iy=y+Math.round((m.z0-top)*height/worldHeight);
            int iw=Math.max(1,Math.round(m.extent*width/worldWidth));
            int ih=Math.max(1,Math.round(m.extent*height/worldHeight));
            g.drawImage(mapImage,ix,iy,iw,ih,null);
            for(MapData.Road road:m.roads) {
                g.setColor(road.cls.city?new Color(244,235,195):new Color(215,201,154));
                for(int i=1;i<road.x.length;i++) {
                    int ax=x+Math.round((road.x[i-1]-left)*width/worldWidth);
                    int ay=y+Math.round((road.z[i-1]-top)*height/worldHeight);
                    int bx=x+Math.round((road.x[i]-left)*width/worldWidth);
                    int by=y+Math.round((road.z[i]-top)*height/worldHeight);
                    g.drawLine(ax,ay,bx,by);
                }
            }
            if(full) {
                for(MapData.Label label:m.labels) {
                    int maxRank=mapZoom<1.6f?0:mapZoom<2.8f?1:mapZoom<5f?2:3;
                    if(label.rank>maxRank)continue;
                    int lx=x+Math.round((label.x-left)*width/worldWidth);
                    int lz=y+Math.round((label.z-top)*height/worldHeight);
                    if(lx<x || lx>=x+width || lz<y || lz>=y+height)continue;
                    g.setColor(new Color(255,217,85));g.fillRect(lx-2,lz-2,5,5);
                    g.setColor(Color.BLACK);g.drawRect(lx-2,lz-2,5,5);
                    g.setColor(new Color(8,17,22,190));g.fillRect(lx+4,lz-12,label.name.length()*5+4,13);
                    g.setColor(new Color(244,245,229));g.setFont(new Font(Font.MONOSPACED,Font.BOLD,8));
                    g.drawString(label.name,lx+5,lz-4);
                }
                int mx=x+Math.round((mapCursorX-left)*width/worldWidth);
                int my=y+Math.round((mapCursorZ-top)*height/worldHeight);
                g.setColor(Color.WHITE);
                g.drawLine(mx-7,my,mx-2,my);g.drawLine(mx+2,my,mx+7,my);
                g.drawLine(mx,my-7,mx,my-2);g.drawLine(mx,my+2,mx,my+7);
            }
            int px=x+Math.round((current.mapX()-left)*width/worldWidth);
            int py=y+Math.round((current.mapZ()-top)*height/worldHeight);
            drawPlayerMarker(g,px,py,current.mapHeading(),full?7:6);
            g.setClip(null);
            if(full) {
                g.setColor(new Color(4,10,18,215));g.fillRect(0,0,W,20);g.fillRect(0,H-29,W,29);
                g.setColor(Color.WHITE);g.setFont(new Font(Font.MONOSPACED,Font.BOLD,11));
                g.drawString("ISLAND MAP",7,14);
                g.setColor(new Color(160,207,207));g.setFont(new Font(Font.MONOSPACED,Font.BOLD,9));
                g.drawString(Math.round(mapZoom*100)+"%",W-92,14);
                g.setColor(new Color(59,92,99));g.drawRoundRect(W-48,3,18,14,2,2);g.drawRoundRect(W-25,3,18,14,2,2);
                g.setColor(new Color(233,242,234));g.setFont(new Font(Font.MONOSPACED,Font.BOLD,11));
                g.drawString("+",W-43,14);g.drawString("-",W-20,14);
                g.setFont(new Font(Font.MONOSPACED,Font.PLAIN,9));
                g.drawString("DRAG / LS / WASD PAN   WHEEL / RT-LT / +/- ZOOM",7,H-16);
                g.drawString("A/ENTER FAST TRAVEL   B/ESC CANCEL",7,H-5);
                if(travelMenu&&selectedDestination!=null) {
                    int bx=65,by=H/2-35,bw=W-130,bh=70;
                    g.setColor(new Color(7,17,29));g.fillRect(bx,by,bw,bh);
                    g.setColor(Color.WHITE);g.drawRect(bx,by,bw,bh);
                    g.setFont(new Font(Font.MONOSPACED,Font.BOLD,12));
                    g.drawString("FAST TRAVEL: "+selectedDestination.name,bx+12,by+23);
                    g.setFont(new Font(Font.MONOSPACED,Font.PLAIN,10));
                    g.drawString("A CONFIRM     B CANCEL",bx+12,by+49);
                }
            } else {
                g.setColor(Color.WHITE);g.drawRect(x,y,width-1,height-1);
            }
        } finally {g.dispose();}
    }

    private static void drawPlayerMarker(Graphics2D g,int x,int y,float heading,int size) {
        float dx=(float)Math.sin(heading),dy=(float)Math.cos(heading),sx=dy,sy=-dx;
        int[] xs={x+Math.round(dx*size),x-Math.round(dx*size*0.65f)+Math.round(sx*size*0.6f),x-Math.round(dx*size*0.65f)-Math.round(sx*size*0.6f)};
        int[] ys={y+Math.round(dy*size),y-Math.round(dy*size*0.65f)+Math.round(sy*size*0.6f),y-Math.round(dy*size*0.65f)-Math.round(sy*size*0.6f)};
        g.setColor(new Color(83,231,243));g.fillPolygon(xs,ys,3);
        g.setColor(new Color(4,14,19));g.setStroke(new BasicStroke(1.4f));g.drawPolygon(xs,ys,3);
        g.setColor(new Color(235,255,248));g.drawLine(x,y,x+Math.round(dx*size*0.65f),y+Math.round(dy*size*0.65f));
        g.setStroke(new BasicStroke(1f));
    }

    @Override public void keyPressed(KeyEvent e) {keys.add(e.getKeyCode());}
    @Override public void keyReleased(KeyEvent e) {keys.remove(e.getKeyCode());}
    @Override public void keyTyped(KeyEvent e) { }
}
