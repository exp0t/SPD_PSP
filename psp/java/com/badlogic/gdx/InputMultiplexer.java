// Minimal libGDX InputMultiplexer replacement backed by an ArrayList.
package com.badlogic.gdx;

import java.util.ArrayList;

public class InputMultiplexer implements InputProcessor {
	private final ArrayList<InputProcessor> processors = new ArrayList<>(4);

	public void addProcessor(int index, InputProcessor processor) { processors.add(index, processor); }
	public void addProcessor(InputProcessor processor) { processors.add(processor); }
	public void removeProcessor(InputProcessor processor) { processors.remove(processor); }

	private InputProcessor[] snapshot() { return processors.toArray(new InputProcessor[0]); }

	public boolean keyDown(int keycode) { for (InputProcessor p : snapshot()) if (p.keyDown(keycode)) return true; return false; }
	public boolean keyUp(int keycode) { for (InputProcessor p : snapshot()) if (p.keyUp(keycode)) return true; return false; }
	public boolean keyTyped(char character) { for (InputProcessor p : snapshot()) if (p.keyTyped(character)) return true; return false; }
	public boolean touchDown(int x, int y, int pointer, int button) { for (InputProcessor p : snapshot()) if (p.touchDown(x, y, pointer, button)) return true; return false; }
	public boolean touchUp(int x, int y, int pointer, int button) { for (InputProcessor p : snapshot()) if (p.touchUp(x, y, pointer, button)) return true; return false; }
	public boolean touchCancelled(int x, int y, int pointer, int button) { for (InputProcessor p : snapshot()) if (p.touchCancelled(x, y, pointer, button)) return true; return false; }
	public boolean touchDragged(int x, int y, int pointer) { for (InputProcessor p : snapshot()) if (p.touchDragged(x, y, pointer)) return true; return false; }
	public boolean mouseMoved(int x, int y) { for (InputProcessor p : snapshot()) if (p.mouseMoved(x, y)) return true; return false; }
	public boolean scrolled(float amountX, float amountY) { for (InputProcessor p : snapshot()) if (p.scrolled(amountX, amountY)) return true; return false; }
}
