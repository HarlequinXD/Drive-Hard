package com.harlequinxd.drivehard.desktop;

import com.harlequinxd.drivehard.core.Game;
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
            long previous=System.nanoTime(), stat=previous;
            int frames=0;
            while(running) {
                long start=System.nanoTime();
                float dt=Math.min(.05f,(start-previous)/1_000_000_000f);
                previous=start;
                boolean connected=pad.poll();
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
    }
    @Override public void keyPressed(KeyEvent e) {keys.add(e.getKeyCode());}
    @Override public void keyReleased(KeyEvent e) {keys.remove(e.getKeyCode());}
    @Override public void keyTyped(KeyEvent e) { }
}
