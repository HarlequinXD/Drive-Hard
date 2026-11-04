# Low-resolution map controls

Both the top-right minimap and the full-screen island map are drawn directly into the game's 480x320 framebuffer before scaling to the desktop window. This retains crisp nearest-neighbour pixels at all window sizes.

- Xbox View/Select or M: open/close full-screen map.
- Left stick, D-pad, or WASD/arrows: move the map cursor; the map follows it.
- Left mouse drag: pan the map. Mouse wheel or the on-screen `+` / `-` buttons: zoom around the map center.
- Xbox right/left trigger or RB/LB, `+` / `-`, brackets, or Page Up/Down: zoom in/out. Zoom ranges from 50% to 1200%.
- Xbox View/Back opens or closes the map; A selects a location and B cancels or closes.
- A / Enter near a yellow location: open fast-travel confirmation; press again to travel.
- B / Escape: cancel confirmation or close map.

The framed top-right minimap stays centered on the player and displays a zoomed-in local region with the player's heading. The game pauses while the full-screen map is open.

Build: GitHub Actions uses `:app:prepareLightweightWindows`, producing the Java game JAR, JNA dependency, and a small native Windows launcher. Java 17+ must be installed on the player's computer.
