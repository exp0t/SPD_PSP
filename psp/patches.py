"""Small source edits applied to upstream SPD files at build time (the originals stay untouched).
Each entry: relative path under the java source root -> list of edits:
  (old, new)               replace text that must occur exactly once
  (start, end, new)        replace from 'start' up to (not including) 'end'; both must occur exactly once

The PSP build is single-threaded: TeaVM would otherwise turn every method that can reach a monitor or
wait() into a resumable coroutine (bigger, slower code). SPD's actor thread and level loading thread
are therefore replaced by the frame-driven scheme of the original Pixel Dungeon.
"""

PATCHES = {
    # Actor.process() runs on the render thread from GameScene.update() and returns whenever the
    # threaded version would have waited (hero needs input, an animation must finish first).
    'com/shatteredpixel/shatteredpixeldungeon/actors/Actor.java': [
        ('\tpublic static void process() {', '\tpublic static void process() {\n'
         '\t\tif (current != null) return;\n'
         '\t\tboolean doNext;\n'
         '\t\tdo {\n'
         '\t\t\tcurrent = null;\n'
         '\t\t\tif (!Game.switchingScene()) {\n'
         '\t\t\t\tfloat earliest = Float.MAX_VALUE;\n'
         '\t\t\t\tfor (Actor actor : all) {\n'
         '\t\t\t\t\tif (actor.time < earliest ||\n'
         '\t\t\t\t\t\t\tactor.time == earliest && (current == null || actor.actPriority > current.actPriority)) {\n'
         '\t\t\t\t\t\tearliest = actor.time;\n'
         '\t\t\t\t\t\tcurrent = actor;\n'
         '\t\t\t\t\t}\n'
         '\t\t\t\t}\n'
         '\t\t\t}\n'
         '\t\t\tif (current != null) {\n'
         '\t\t\t\tnow = current.time;\n'
         '\t\t\t\tActor acting = current;\n'
         '\t\t\t\t// the character\'s sprite is still moving: try again on a later frame\n'
         '\t\t\t\tif (acting instanceof Char && ((Char) acting).sprite != null && ((Char) acting).sprite.isMoving) {\n'
         '\t\t\t\t\tcurrent = null;\n'
         '\t\t\t\t\treturn;\n'
         '\t\t\t\t}\n'
         '\t\t\t\tdoNext = acting.act();\n'
         '\t\t\t\tif (doNext && (Dungeon.hero == null || !Dungeon.hero.isAlive())) {\n'
         '\t\t\t\t\tdoNext = false;\n'
         '\t\t\t\t\tcurrent = null;\n'
         '\t\t\t\t}\n'
         '\t\t\t} else {\n'
         '\t\t\t\tdoNext = false;\n'
         '\t\t\t}\n'
         '\t\t} while (doNext);\n'
         '\t}\n\n'
         '\tpublic static void resetProcessing() {\n'
         '\t\tcurrent = null;\n'
         '\t}\n\n'
         '\tpublic static void processThreaded() {'),
    ],
    # waits for every sprite to stop moving: give the turn back and retry on a later frame
    'com/shatteredpixel/shatteredpixeldungeon/actors/buffs/GravityChaosTracker.java': [
        ('\t\t//wait until all sprites have finished moving', '\t\tif (!blocked.isEmpty()){',
         '\t\t//wait until all sprites have finished moving\n'
         '\t\tfor (Char ch : Actor.chars()){\n'
         '\t\t\tif (ch.sprite != null && ch.sprite.isMoving) {\n'
         '\t\t\t\tnext();\n'
         '\t\t\t\treturn false;\n'
         '\t\t\t}\n'
         '\t\t}\n\n'),
    ],
    'com/shatteredpixel/shatteredpixeldungeon/scenes/GameScene.java': [
        ('\tpublic boolean waitForActorThread(int msToWait, boolean interrupt){',
         '\tpublic boolean waitForActorThread(int msToWait, boolean interrupt){\n'
         '\t\tif (interrupt) Actor.resetProcessing();\n'
         '\t\treturn !Actor.processing();\n'
         '\t}\n\n'
         '\tpublic boolean waitForActorThreadUnused(int msToWait, boolean interrupt){'),
        ('\t\t\tif (actorThread == null || !actorThread.isAlive()) {',
         '\t\tif (Dungeon.hero.ready && Dungeon.hero.paralysed == 0) {',
         '\t\t\tif (notifyDelay <= 0f) {\n'
         '\t\t\t\tnotifyDelay += 1/60f;\n'
         '\t\t\t\tActor.process();\n'
         '\t\t\t}\n'
         '\t\t}\n\n'),
        # the HUD keeps PixelScene's fractional uiZoom (1.7) like the menus: 15% smaller than the integer
        # zoom 2, so it covers less of the dungeon; text is rasterized at screen size and stays sharp
        # camera follow dead zone through pspApplyCamera(), which the settings window calls too
        ('\t\tswitch (SPDSettings.cameraFollow()) {', '\t\tRectF insets = getCommonInsets();',
         '\t\tpspApplyCamera();\n\n'),
        # cell cursor (psp/PSPCellCursor) hooks
        ('\tpublic static void handleCell( int cell ) {',
         '\t// PSP "fixed camera": the hero stays centred (no dead zone; HeroSprite follows with a locked pan)\n'
         '\tpublic static void pspApplyCamera() {\n'
         '\t\tswitch (SPDSettings.pspFixedCamera() ? 4 : SPDSettings.cameraFollow()) {\n'
         '\t\t\tcase 4: default:    Camera.main.setFollowDeadzone(0);      break;\n'
         '\t\t\tcase 3:             Camera.main.setFollowDeadzone(0.2f);   break;\n'
         '\t\t\tcase 2:             Camera.main.setFollowDeadzone(0.5f);   break;\n'
         '\t\t\tcase 1:             Camera.main.setFollowDeadzone(0.9f);   break;\n'
         '\t\t}\n'
         '\t}\n\n'
         '\tpublic static boolean pspTargeting() {\n'
         '\t\treturn cellSelector != null && cellSelector.listener != null && cellSelector.listener != defaultCellListener;\n'
         '\t}\n\n'
         '\tpublic static void handleCellRight( int cell ) {\n'
         '\t\tcellSelector.select( cell, PointerEvent.RIGHT );\n'
         '\t}\n\n'
         '\tpublic static void handleCell( int cell ) {'),
    ],
    # level generation runs on the render thread once the loading screen has been drawn
    'com/shatteredpixel/shatteredpixeldungeon/scenes/InterlevelScene.java': [
        # TeaVM reports a never-started Thread as alive, so track completion with a flag
        ('\tprivate static Thread thread;', '\tprivate static Thread thread;\n\tprivate static int loadDelayFrames = -1;\n\tprivate static boolean loadDone;'),
        ('\t\t\tthread.start();', '\t\t\tloadDone = false;\n\t\t\tloadDelayFrames = 2;'),
        ('if (!thread.isAlive() && error == null) {', 'if (loadDone && error == null) {'),
        ('} else if (thread != null && (int)waitingTime == 10){', '} else if (thread != null && !loadDone && (int)waitingTime == 10){'),
        ('\t\tif (btnContinue == null || !btnContinue.isActive()) {\n\t\t\twaitingTime += Game.elapsed;\n\t\t}',
         '\t\tif (btnContinue == null || !btnContinue.isActive()) {\n\t\t\twaitingTime += Game.elapsed;\n\t\t}\n\n'
         '\t\tif (loadDelayFrames > 0 && --loadDelayFrames == 0 && thread != null) {\n'
         '\t\t\tloadDelayFrames = -1;\n'
         '\t\t\tthread.run();\n'
         '\t\t\tloadDone = true;\n'
         '\t\t}'),
    ],
    # PSP button layout (no L2/R2, stick clicks or right stick); Select held = second layer, see PSPButtons
    'com/shatteredpixel/shatteredpixeldungeon/SPDAction.java': [
        ('defaultControllerBindings.put( Input.Keys.BUTTON_START,',
         'public static LinkedHashMap<Integer, GameAction> getControllerDefaults() {',
         'defaultControllerBindings.put( PSPButtons.CROSS,        SPDAction.LEFT_CLICK );\n'
         '\t\tdefaultControllerBindings.put( PSPButtons.CIRCLE,       SPDAction.BACK );\n'
         '\t\tdefaultControllerBindings.put( PSPButtons.START,        SPDAction.BACK );\n'
         '\t\tdefaultControllerBindings.put( PSPButtons.SQUARE,       SPDAction.WAIT_OR_PICKUP );\n'
         '\t\tdefaultControllerBindings.put( PSPButtons.TRIANGLE,     SPDAction.INVENTORY_SELECTOR );\n'
         '\t\tdefaultControllerBindings.put( PSPButtons.L,            SPDAction.QUICKSLOT_SELECTOR );\n'
         '\t\tdefaultControllerBindings.put( PSPButtons.R,            SPDAction.TAG_ATTACK );\n'
         '\t\tdefaultControllerBindings.put( PSPButtons.SELECT,       SPDAction.JOURNAL );\n'
         '\n'
         '\t\tdefaultControllerBindings.put( PSPButtons.SEL_CROSS,    SPDAction.RIGHT_CLICK );\n'
         '\t\tdefaultControllerBindings.put( PSPButtons.SEL_CIRCLE,   SPDAction.EXAMINE );\n'
         '\t\tdefaultControllerBindings.put( PSPButtons.SEL_SQUARE,   SPDAction.REST );\n'
         '\t\tdefaultControllerBindings.put( PSPButtons.SEL_TRIANGLE, SPDAction.HERO_INFO );\n'
         '\t\tdefaultControllerBindings.put( PSPButtons.SEL_L,        SPDAction.ZOOM_OUT );\n'
         '\t\tdefaultControllerBindings.put( PSPButtons.SEL_R,        SPDAction.ZOOM_IN );\n'
         '\t\tdefaultControllerBindings.put( PSPButtons.SEL_START,    SPDAction.INVENTORY );\n'
         '\t\tdefaultControllerBindings.put( PSPButtons.SEL_UP,       SPDAction.TAG_ACTION );\n'
         '\t\tdefaultControllerBindings.put( PSPButtons.SEL_LEFT,     SPDAction.TAG_LOOT );\n'
         '\t\tdefaultControllerBindings.put( PSPButtons.SEL_DOWN,     SPDAction.TAG_RESUME );\n'
         '\t\tdefaultControllerBindings.put( PSPButtons.SEL_RIGHT,    SPDAction.CYCLE );\n'
         '\t}\n\n\t'),
        ('import java.util.LinkedHashMap;', 'import java.util.LinkedHashMap;\nimport com.watabou.psp.PSPButtons;'),
    ],
    # D-pad menu navigation (PSPFocus) walks the pointer listeners in dispatch order
    'com/watabou/utils/Signal.java': [
        ('\tpublic synchronized int numListeners() {',
         '\tpublic synchronized java.util.List<Listener<T>> snapshot() {\n'
         '\t\treturn new java.util.ArrayList<>( listeners );\n'
         '\t}\n\n'
         '\tpublic synchronized int numListeners() {'),
    ],
    'com/watabou/input/PointerEvent.java': [
        ('\tpublic static void clearListeners(){',
         '\tpublic static java.util.List<Signal.Listener<PointerEvent>> listeners(){\n'
         '\t\treturn pointerSignal.snapshot();\n'
         '\t}\n\n'
         '\tpublic static void clearListeners(){'),
    ],
    'com/watabou/input/ControllerHandler.java': [
        ('\tpublic static String customButtonName(int keyCode){',
         '\tpublic static String customButtonName(int keyCode){\n'
         '\t\tString pspName = com.watabou.psp.PSPButtons.name(keyCode);\n'
         '\t\tif (pspName != null) return pspName;'),
    ],
    # every map layer used to send all of its cells to the GE each frame (empty cells as zero-size quads,
    # which the GE still has to transform): draw only the rows and columns in the camera's view
    'com/watabou/noosa/Tilemap.java': [
        ('\t\tscript.drawQuadSet( buffer, size, 0 );\n',
         '\t\tCamera c = camera();\n'
         '\t\tif (c == null || angle != 0 || mapWidth == 0) {\n'
         '\t\t\tscript.drawQuadSet( buffer, size, 0 );\n'
         '\t\t} else {\n'
         '\t\t\t// 2 cells of slack for tiles that draw past their cell and for camera shake\n'
         '\t\t\tfloat cw = cellW * scale.x, ch = cellH * scale.y;\n'
         '\t\t\tint c0 = Math.max( 0, (int)Math.floor( (c.scroll.x - x) / cw ) - 2 );\n'
         '\t\t\tint c1 = Math.min( mapWidth, (int)Math.ceil( (c.scroll.x + c.width - x) / cw ) + 2 );\n'
         '\t\t\tint r0 = Math.max( 0, (int)Math.floor( (c.scroll.y - y) / ch ) - 2 );\n'
         '\t\t\tint r1 = Math.min( mapHeight, (int)Math.ceil( (c.scroll.y + c.height - y) / ch ) + 2 );\n'
         '\t\t\tif (c1 - c0 >= mapWidth - 2) {\n'
         '\t\t\t\tif (r1 > r0) script.drawQuadSet( buffer, (r1 - r0) * mapWidth, r0 * mapWidth );\n'
         '\t\t\t} else if (c1 > c0) {\n'
         '\t\t\t\tfor (int r = r0; r < r1; r++) script.drawQuadSet( buffer, c1 - c0, r * mapWidth + c0 );\n'
         '\t\t\t}\n'
         '\t\t}\n'),
    ],
    # psp/PSPFocus: rows of scrolling lists are Components inside the list's content group
    'com/watabou/noosa/Group.java': [
        ('\tpublic synchronized int indexOf( Gizmo g ) {',
         '\tpublic java.util.List<Gizmo> pspMembers() {\n\t\treturn members;\n\t}\n\n\tpublic synchronized int indexOf( Gizmo g ) {'),
    ],
    # psp/PSPFocus: left/right move a focused slider by one step
    'com/shatteredpixel/shatteredpixeldungeon/ui/OptionSlider.java': [
        ('\tpublic int getSelectedValue(){',
         '\tpublic void pspStep( int delta ){\n'
         '\t\tint v = Math.max( minVal, Math.min( maxVal, selectedVal + delta ) );\n'
         '\t\tif (v != selectedVal) {\n\t\t\tsetSelectedValue( v );\n\t\t\tonChange();\n\t\t}\n\t}\n\n'
         '\tpublic int getSelectedValue(){'),
    ],
    # PSP: HOME quits the game, so Back on the title screen does nothing (SPD would exit), and the
    # "hide the menu" chevron is dropped: without a touch screen there is no tap to bring the menu back
    'com/shatteredpixel/shatteredpixeldungeon/scenes/TitleScene.java': [
        ('\t\tadd(btnFade);', '\t\tadd(btnFade);\n\t\tbtnFade.visible = false;\n\t\tbtnFade.active = false;'),
        ('\tprivate float uiAlpha;', '\t@Override\n\tprotected void onBackPressed() {\n\t}\n\n\tprivate float uiAlpha;'),
    ],
    # D-pad movement starts after the shortest hold delay (30 ms instead of 60) to cut input lag
    'com/shatteredpixel/shatteredpixeldungeon/SPDSettings.java': [
        ('return getInt(KEY_MOVE_SENS, 3, 0, 4);', 'return getInt(KEY_MOVE_SENS, 4, 0, 4);'),
        # PSP "fixed camera" option (display settings), on by default
        ('\tpublic static void cameraFollow( int value ){',
         '\tpublic static final String KEY_PSP_FIXED_CAMERA = "psp_fixed_camera";\n\n'
         '\tpublic static void pspFixedCamera( boolean value ){\n\t\tput( KEY_PSP_FIXED_CAMERA, value );\n\t}\n\n'
         '\tpublic static boolean pspFixedCamera(){\n\t\treturn getBoolean( KEY_PSP_FIXED_CAMERA, true );\n\t}\n\n'
         '\tpublic static void cameraFollow( int value ){'),
    ],
    # the D-pad drives the left stick: a tap is one step, walking on starts only after 0.3 s of holding
    # (with the short hold delay the hero is ready again before a normal tap ends and took a second step)
    'com/shatteredpixel/shatteredpixeldungeon/scenes/CellSelector.java': [
        ('\tprivate GameAction leftStickAction = SPDAction.NONE;',
         '\tprivate GameAction leftStickAction = SPDAction.NONE;\n\tprivate float pspHeld;\n\tprivate boolean pspStepped;'),
        ('\t\t\tleftStickAction = newLeftStick;',
         '\t\t\tleftStickAction = newLeftStick;\n\t\t\tpspHeld = 0f;\n\t\t\tpspStepped = false;'),
        ('\t\tif (heldDelay > 0){\n\t\t\theldDelay -= Game.elapsed;',
         '\t\tif (leftStickAction != SPDAction.NONE) pspHeld += Game.elapsed;\n\t\tif (heldDelay > 0){\n\t\t\theldDelay -= Game.elapsed;'),
        ('if (!directionFromAction(leftStickAction).isZero() && heldDelay < 0) {',
         'if (!directionFromAction(leftStickAction).isZero() && heldDelay < 0 && (!pspStepped || pspHeld >= 0.3f)) {'),
        ('\t\t\tif (moveFromActions(leftStickAction)) {\n\t\t\t\tDungeon.hero.ready = false;',
         '\t\t\tif (moveFromActions(leftStickAction)) {\n\t\t\t\tpspStepped = true;\n\t\t\t\tDungeon.hero.ready = false;'),    ],
    # saves are plain JSON on the PSP: deflate in TeaVM code stalls the game (reading still accepts gzip)
    'com/watabou/utils/Bundle.java': [
        ('private static final boolean compressByDefault = true;', 'private static final boolean compressByDefault = false;'),
    ],
    # TeaVM's SecureRandom needs the browser crypto API; a set ID only has to be unique-ish
    'com/shatteredpixel/shatteredpixeldungeon/items/weapon/missiles/MissileWeapon.java': [
        ('import java.security.SecureRandom;', ''),
        ('new SecureRandom().nextLong()', 'new java.util.Random().nextLong()'),
    ],
    # 480x272 is below SPD's 240x160 landscape minimum at zoom 2. Menus, the HUD and windows use a
    # fractional uiZoom (1.7, a 283x160 view) so every layout fits; the dungeon itself keeps zoom 2.
    # Text is rasterized at the real screen size (RenderedText.screenScale), so it stays sharp.
    'com/shatteredpixel/shatteredpixeldungeon/scenes/PixelScene.java': [
        ('\tpublic static int maxDefaultZoom = 0;', '\tpublic static int maxDefaultZoom = 0;\n\tpublic static float uiZoom = 2;'),
        ('\t\tCamera.reset( new PixelCamera( defaultZoom ) );\n\n\t\tfloat uiZoom = defaultZoom;',
         '\t\tuiZoom = Math.max(1f, Math.min(defaultZoom, Math.min(w/minWidth, h/minHeight)));\n'
         '\t\tcom.watabou.noosa.RenderedText.screenScale = uiZoom / defaultZoom;\n'
         '\t\tCamera.reset( new PixelCamera( uiZoom ) );\n'),
        ('return Math.round(pos * defaultZoom) / (float)defaultZoom;', 'return Math.round(pos * uiZoom) / uiZoom;'),
        ('\tpublic static boolean landscape(){',
         '\t// windows keep the crisp integer zoom when they fit on screen with it\n'
         '\t// (RadialMenu is a deliberately oversized Game.width x Game.height window and keeps it too)\n'
         '\tpublic static float windowZoom( float w, float h ){\n'
         '\t\tif (w >= Game.width && h >= Game.height) return defaultZoom;\n'
         '\t\treturn w * defaultZoom <= Game.width && h * defaultZoom <= Game.height ? defaultZoom : uiZoom;\n'
         '\t}\n\n'
         '\tpublic static boolean landscape(){'),
        # no pointer arrow on the PSP: PSPOverlay draws the menu focus frame and the cell cursor instead
        # no free pointer on the PSP: the right stick only steers RadialMenu and must not pan the camera
        ('\t\t//20% deadzone\n\t\tif (!Cursor.isCursorCaptured()) {', '\t\t//20% deadzone\n\t\tif (false) {'),
        ('\t\tpixelFont.tracking = -1;', '\t\tpixelFont.tracking = -1;\n\t\tcom.shatteredpixel.shatteredpixeldungeon.psp.PSPFonts.repack( pixelFont );'),
        ('\t\t//cursor is separate from the rest of the scene, always appears above', '\t//this system only preserves windows',
         '\t\tcom.shatteredpixel.shatteredpixeldungeon.psp.PSPOverlay.draw();\n\t}\n\n'),
    ],
    'com/shatteredpixel/shatteredpixeldungeon/ui/Window.java': [
        ('\t\t\t(int)chrome.height,\n\t\t\tPixelScene.defaultZoom );', '\t\t\t(int)chrome.height,\n\t\t\tPixelScene.windowZoom( chrome.width, chrome.height ) );'),
        ('\t\tshadow.boxRect(\n\t\t\t\tcamera.x / camera.zoom,\n\t\t\t\tcamera.y / camera.zoom,\n\t\t\t\tchrome.width(), chrome.height );',
         '\t\tlayoutShadow();'),
        ('\t\tcamera.resize( (int)chrome.width, (int)chrome.height );',
         '\t\tcamera.zoom = PixelScene.windowZoom( chrome.width, chrome.height );\n\t\tcamera.resize( (int)chrome.width, (int)chrome.height );'),
        ('\t\tcamera.y += insets.top;\n\t\tcamera.y += yOffset * camera.zoom;\n\n\t\tshadow.boxRect( camera.x / camera.zoom, camera.y / camera.zoom, chrome.width(), chrome.height );',
         '\t\tcamera.y += insets.top;\n\t\tcamera.y += yOffset * camera.zoom;\n\n\t\tlayoutShadow();'),
        ('\t\tthis.yOffset = yOffset;\n\t\tcamera.y += yOffset * camera.zoom;\n\n\t\tshadow.boxRect( camera.x / camera.zoom, camera.y / camera.zoom, chrome.width(), chrome.height );',
         '\t\tthis.yOffset = yOffset;\n\t\tcamera.y += yOffset * camera.zoom;\n\n\t\tlayoutShadow();\n\t}\n\n'
         '\t// the shadow lives in the scene camera, whose zoom can differ from the window\'s\n'
         '\tprotected void layoutShadow(){\n'
         '\t\tfloat z = shadow.camera.zoom, s = camera.zoom / z;\n'
         '\t\tshadow.boxRect( camera.x / z, camera.y / z, chrome.width() * s, chrome.height * s );'),
    ],
    # tabbed windows (journal, hero info...) copy Window.resize
    'com/shatteredpixel/shatteredpixeldungeon/windows/WndTabbed.java': [
        ('\t\tcamera.resize( (int)chrome.width, chrome.marginTop() + height + tabHeight() );',
         '\t\tcamera.zoom = PixelScene.windowZoom( chrome.width, chrome.marginTop() + height + tabHeight() );\n'
         '\t\tcamera.resize( (int)chrome.width, chrome.marginTop() + height + tabHeight() );'),
        ('\t\tshadow.boxRect(\n\t\t\t\tcamera.x / camera.zoom,\n\t\t\t\tcamera.y / camera.zoom,\n\t\t\t\tchrome.width(), chrome.height );\n\t\t// <- super.resize(...)',
         '\t\tlayoutShadow();\n\t\t// <- super.resize(...)'),
    ],
    'com/shatteredpixel/shatteredpixeldungeon/ui/ScrollPane.java': [
        ('new Camera( 0, 0, 1, 1, PixelScene.defaultZoom )', 'new Camera( 0, 0, 1, 1, PixelScene.uiZoom )'),
        # follow the owning window's zoom and position, which can change after the pane was laid out
        ('\tpublic synchronized void update() {\n\t\tsuper.update();',
         '\tpublic synchronized void update() {\n\t\tsuper.update();\n'
         '\t\tPoint pos = camera().cameraToScreen( x, y );\n'
         '\t\tif (content.camera.zoom != camera().zoom || content.camera.x != pos.x || content.camera.y != pos.y) layout();'),
        ('\t\tCamera cs = content.camera;\n', '\t\tCamera cs = content.camera;\n\t\tcs.zoom = camera().zoom;\n'),
    ],
    # lets psp/PSPFonts move the glyphs of the 1024 px wide pixel font into a 512 px wide atlas
    'com/watabou/noosa/TextureFilm.java': [
        ('\tpublic void add( Object id, RectF rect ) {',
         '\tpublic HashMap<Object,RectF> pspFrames() {\n\t\treturn frames;\n\t}\n\n'
         '\tpublic void pspRetarget( int width, int height ) {\n\t\ttexWidth = width;\n\t\ttexHeight = height;\n\t}\n\n'
         '\tpublic void add( Object id, RectF rect ) {'),
    ],
    # pixel font labels (HUD numbers, item counts): whole screen pixels per font pixel, origin on a pixel
    'com/watabou/noosa/BitmapText.java': [
        ('script.uModel.valueM4( matrix );', 'script.uModel.valueM4( com.watabou.psp.PSPPixel.snap( matrix, camera(), scale.x, width, height ) );'),
    ],
    # PSP "fixed camera" option: the camera keeps the hero exactly centred while walking (no lagging pan)
    'com/shatteredpixel/shatteredpixeldungeon/sprites/HeroSprite.java': [
        ('\t\t\tplay( fly );\n\t\t}\n\t\tCamera.main.panFollow(this, 20f);\n\t}\n\n\t@Override\n\tpublic void idle()',
         '\t\t\tplay( fly );\n\t\t}\n\t\tCamera.main.panFollow(this, SPDSettings.pspFixedCamera() ? 1000f : 20f);\n\t}\n\n\t@Override\n\tpublic void idle()'),
        ('\t\tplay( fly );\n\t\tCamera.main.panFollow(this, 20f);', '\t\tplay( fly );\n\t\tCamera.main.panFollow(this, SPDSettings.pspFixedCamera() ? 1000f : 20f);'),
        ('import com.watabou.noosa.Camera;', 'import com.watabou.noosa.Camera;\nimport com.shatteredpixel.shatteredpixeldungeon.SPDSettings;'),
    ],
    # display settings: the fullscreen checkbox (always disabled on the PSP) becomes the fixed camera option,
    # which turns the camera follow slider off while it is on
    'com/shatteredpixel/shatteredpixeldungeon/windows/WndSettings.java': [
        ('String fullscreenText = Messages.get(this, "fullscreen");', '\t\t\tif (DeviceCompat.isAndroid()) {\n\t\t\t\tchkLandscape',
         'chkFullscreen = new CheckBox( Messages.get(this, "fixed_camera") ) {\n'
         '\t\t\t\t@Override\n'
         '\t\t\t\tprotected void onClick() {\n'
         '\t\t\t\t\tsuper.onClick();\n'
         '\t\t\t\t\tSPDSettings.pspFixedCamera(checked());\n'
         '\t\t\t\t\toptFollowIntensity.enable(!checked());\n'
         '\t\t\t\t\tif (Game.scene() instanceof com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene) com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene.pspApplyCamera();\n'
         '\t\t\t\t}\n'
         '\t\t\t};\n'
         '\t\t\tchkFullscreen.checked(SPDSettings.pspFixedCamera());\n'
         '\t\t\tadd(chkFullscreen);\n\n'),
        ('SPDSettings.cameraFollow(getSelectedValue());',
         'SPDSettings.cameraFollow(getSelectedValue());\n'
         '\t\t\t\t\tif (Game.scene() instanceof com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene) com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene.pspApplyCamera();'),
        ('\t\t\toptFollowIntensity.setSelectedValue(SPDSettings.cameraFollow());',
         '\t\t\toptFollowIntensity.setSelectedValue(SPDSettings.cameraFollow());\n'
         '\t\t\toptFollowIntensity.enable(!SPDSettings.pspFixedCamera());'),
    ],
    # PSP port credits, between the Pixel Dungeon and libGDX sections of the About screen
    'com/shatteredpixel/shatteredpixeldungeon/scenes/AboutScene.java': [
        ('\t\t//*** libGDX Credits ***\n',
         '\t\t//*** PSP port Credits ***\n\n'
         '\t\tCreditsBlock psp = new CreditsBlock(true,\n'
         '\t\t\t\t0x5A9CFF,\n'
         '\t\t\t\t"PSP Port",\n'
         '\t\t\t\tnull,\n'
         '\t\t\t\t"Ported to the _PSP_ by\\n_yotta imoto_ and _Claude_",\n'
         '\t\t\t\tnull,\n'
         '\t\t\t\tnull);\n'
         '\t\tpsp.setRect((Camera.main.width - colWidth)/2f, Math.max(wata.bottom(), cube.bottom()) + 12, colWidth, 0);\n'
         '\t\tcontent.add(psp);\n\n'
         '\t\taddLine(psp.top() - 4, content);\n\n'
         '\t\t//*** libGDX Credits ***\n'),
        ('\t\tif (landscape()){\n\t\t\tgdx.setRect(wata.left(), wata.bottom() + 8, colWidth, 0);\n'
         '\t\t} else {\n\t\t\tgdx.setRect(wata.left(), cube.bottom() + 8, colWidth, 0);\n\t\t}',
         '\t\tgdx.setRect(wata.left(), psp.bottom() + 12, colWidth, 0);'),
    ],
}
