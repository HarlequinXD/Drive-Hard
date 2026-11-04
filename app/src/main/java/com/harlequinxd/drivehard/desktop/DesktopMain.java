package com.harlequinxd.drivehard.desktop;

import com.harlequinxd.drivehard.core.Game;
import com.harlequinxd.drivehard.core.map.MapData;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.*;

/** Native Windows desktop frontend retaining Drive Hard's original software renderer and game simulation. */
public final class DesktopMain extends JPanel implements KeyListener, Runnable {
    private static final int W=480, H=320;
    private final Set<Integer> keys = ConcurrentHashMap.newKeySet();
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
    private BufferedImage mapImage;
    private volatile boolean mapImageReady;


    private DesktopMain() {
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);
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
                    if(mapOpen) { mapCursorX=g.mapX(); mapCursorZ=g.mapZ(); }
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
                        float speed=g.map.extent*0.28f*dt;
                        mapCursorX=Math.max(g.map.x0,Math.min(g.map.x0+g.map.extent,mapCursorX+panX*speed));
                        mapCursorZ=Math.max(g.map.z0,Math.min(g.map.z0+g.map.extent,mapCursorZ+panZ*speed));
                        if(confirm&&!lastConfirm) {
                            MapData.Label nearest=null; float best=g.map.extent*0.045f;
                            for(MapData.Label label:g.map.labels) {
                                if(label.rank==4) continue;
                                float d=(float)Math.hypot(label.x-mapCursorX,label.z-mapCursorZ);
                                if(d<best) {best=d;nearest=label;}
                            }
                            if(nearest!=null) { selectedDestination=nearest; travelMenu=true; }
                        }
                    }
                    lastConfirm=confirm;lastCancel=cancel;
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
                synchronized(image) { System.arraycopy(g.renderer.fb,0,pixels,0,pixels.length); imageReady=true; }
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
        g.setColor(Color.WHITE);g.setFont(new Font(Font.MONOSPACED,Font.BOLD,13));
        g.drawString("Drive Hard | "+Math.round(fps)+" FPS | Xbox: LS move / steer, RS camera, RT gas, LT brake, A jump, Y enter",12,22);
        if(mapImageReady) {
            if(mapOpen) drawMap(g,Math.max(18,width/12),Math.max(48,height/12),Math.min(width*5/6,height*5/6),true);
            else drawMap(g,width-Math.max(145,width/5)-16,38,Math.max(145,width/5),false);
        }
    }
    private void drawMap(Graphics g,int x,int y,int size,boolean full) {
        Game current=game;
        if(current==null || mapImage==null || size<=0) return;
        MapData m=current.map;
        Graphics2D gg=(Graphics2D)g.create();
        gg.setColor(new Color(9,15,23,225));gg.fillRoundRect(x-7,y-7,size+14,size+(full?64:14),12,12);
        gg.setClip(x,y,size,size);
        gg.drawImage(mapImage,x,y,size,size,null);
        double scale=size/(double)m.extent;
        gg.setStroke(new BasicStroke(full?1.5f:1f));
        for(MapData.Road road:m.roads) {
            gg.setColor(road.cls.city?new Color(244,235,195):new Color(215,201,154));
            for(int i=1;i<road.x.length;i++) {
                int x0=x+(int)((road.x[i-1]-m.x0)*scale),z0=y+(int)((road.z[i-1]-m.z0)*scale);
                int x1=x+(int)((road.x[i]-m.x0)*scale),z1=y+(int)((road.z[i]-m.z0)*scale);
                gg.drawLine(x0,z0,x1,z1);
            }
        }
        if(full) {
            for(MapData.Label label:m.labels) {
                if(label.rank==4)continue;
                int lx=x+(int)((label.x-m.x0)*scale),lz=y+(int)((label.z-m.z0)*scale);
                gg.setColor(new Color(255,217,85));gg.fillOval(lx-4,lz-4,8,8);
                gg.setColor(Color.BLACK);gg.drawOval(lx-4,lz-4,8,8);
                gg.setColor(Color.WHITE);gg.setFont(new Font(Font.SANS_SERIF,Font.BOLD,12));
                gg.drawString(label.name,lx+8,lz-5);
            }
            int cx=x+(int)((mapCursorX-m.x0)*scale),cz=y+(int)((mapCursorZ-m.z0)*scale);
            gg.setColor(Color.WHITE);gg.setStroke(new BasicStroke(2));
            gg.drawLine(cx-11,cz,cx-3,cz);gg.drawLine(cx+3,cz,cx+11,cz);
            gg.drawLine(cx,cz-11,cx,cz-3);gg.drawLine(cx,cz+3,cx,cz+11);
            gg.drawOval(cx-3,cz-3,6,6);
        }
        int px=x+(int)((current.mapX()-m.x0)*scale),pz=y+(int)((current.mapZ()-m.z0)*scale);
        gg.setColor(Color.BLACK);gg.fillOval(px-7,pz-7,14,14);
        gg.setColor(new Color(75,220,255));gg.fillOval(px-5,pz-5,10,10);
        gg.setClip(null);
        if(full) {
            gg.setColor(Color.WHITE);gg.setFont(new Font(Font.SANS_SERIF,Font.BOLD,15));
            gg.drawString("ISLAND MAP  |  SELECT/M: close  |  Left stick/WASD: cursor  |  A/Enter: select",x,y+size+25);
            if(travelMenu && selectedDestination!=null) {
                int mw=Math.min(size-20,420),mh=120,mx=x+(size-mw)/2,my=y+(size-mh)/2;
                gg.setColor(new Color(12,21,32,245));gg.fillRoundRect(mx,my,mw,mh,16,16);
                gg.setColor(Color.WHITE);gg.setFont(new Font(Font.SANS_SERIF,Font.BOLD,20));
                gg.drawString("Fast travel to " + selectedDestination.name + "?",mx+18,my+40);
                gg.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,14));
                gg.drawString("A / Enter: Travel      B / Esc: Cancel",mx+18,my+80);
            } else gg.drawString("Move the cursor onto a yellow location marker, then press A",x,y+size+48);
        }
        gg.dispose();
    }

    @Override public void keyPressed(KeyEvent e) {keys.add(e.getKeyCode());}
    @Override public void keyReleased(KeyEvent e) {keys.remove(e.getKeyCode());}
    @Override public void keyTyped(KeyEvent e) { }
}
