package com.badlogic.gdx.utils.reflect;

public final class ClassReflection {
	public static boolean isMemberClass(Class c) { return c.getEnclosingClass() != null; }
	public static boolean isStaticClass(Class c) { return java.lang.reflect.Modifier.isStatic(c.getModifiers()); }

	@SuppressWarnings("unchecked")
	public static <T> T newInstance(Class<T> c) throws ReflectionException {
		try {
			return c.newInstance();
		} catch (Exception e) {
			throw new ReflectionException("Could not instantiate " + c.getName(), e);
		}
	}

	public static Class forName(String name) throws ReflectionException {
		try {
			return Class.forName(name);
		} catch (ClassNotFoundException e) {
			throw new ReflectionException("Class not found: " + name, e);
		}
	}
}
