# Low-resolution map controls

The desktop frontend draws the maps into the game's 480x320 framebuffer; the Android frontend draws the same map data with native Canvas. Both preserve the same island, roads, player marker, and fast-travel locations.

## Android touch

- Tap the top-right minimap or the MAP button to open the island map.
- Drag on the map to pan; pinch to zoom, or tap the top-right `+` / `-` controls.
- Tap a town/city marker to select it, then tap **TRAVEL** to confirm. Use **CLOSE** or Android Back to leave the map; Back cancels the travel prompt first.
- The game pauses while the full-screen map is open.
- On the driving screen, use the left virtual stick to walk or steer, the right-side buttons to accelerate/brake, jump or enter/exit a car, and drag on the right half to orbit the camera.

- Xbox View/Select or M: open/close full-screen map.
- Left stick, D-pad, or WASD/arrows: move the map cursor; the map follows it.
- Left mouse drag: pan the map. Mouse wheel or the on-screen `+` / `-` buttons: zoom around the map center.
- Hold Xbox right/left trigger or RB/LB to zoom in repeated steps; `+` / `-`, brackets, or Page Up/Down also zoom. Zoom ranges from 50% to 1200%.
- Xbox View/Back opens or closes the map; A selects a location and B cancels or closes.
- A / Enter near a yellow location: open fast-travel confirmation; press again to travel.
- B / Escape: cancel confirmation or close map.

The framed top-right minimap stays centered on the player and displays a zoomed-in local region with the player's heading. The game pauses while the full-screen map is open.

Build: GitHub Actions uses `:app:prepareLightweightWindows`, producing the Java game JAR, JNA dependency, and a small native Windows launcher. Java 17+ must be installed on the player's computer.
Android builds separately with `gradle :android-app:assembleDebug`; see [README.md](README.md) for APK output and touch controls.
