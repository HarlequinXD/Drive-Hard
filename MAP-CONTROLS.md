# Low-resolution map controls

Both the top-right minimap and the full-screen island map are drawn directly into the game's 480x320 framebuffer before scaling to the desktop window. This retains crisp nearest-neighbour pixels at all window sizes.

- Xbox View/Select or M: open/close full-screen map.
- Left stick or WASD/arrows: move map cursor quickly across the world. Camera follows cursor.
- Xbox right trigger / + / Page Up: zoom in.
- Xbox left trigger / - / Page Down: zoom out.
- A / Enter near a yellow location: open fast-travel confirmation; press again to travel.
- B / Escape: cancel confirmation or close map.

The top-right minimap stays centered on the player and displays a zoomed-in local region. The game pauses while the full-screen map is open.

Build: GitHub Actions uses `:app:prepareLightweightWindows`, producing the Java game JAR, JNA dependency, and a small native Windows launcher. Java 17+ must be installed on the player's computer.
