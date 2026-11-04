package com.badlogic.gdx.utils;

public class GdxRuntimeException extends RuntimeException {
	public GdxRuntimeException(String message) { super(message); }
	public GdxRuntimeException(Throwable t) { super(t); }
	public GdxRuntimeException(String message, Throwable t) { super(message, t); }
}
