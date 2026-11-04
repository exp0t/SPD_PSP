package com.badlogic.gdx;

import com.badlogic.gdx.files.FileHandle;

public class Files {
	public enum FileType { Classpath, Internal, External, Absolute, Local }

	// internal = read-only game assets next to the EBOOT, local = save data
	public static String internalRoot = "assets/";
	public static String localRoot = "";

	public FileHandle classpath(String path) { return new FileHandle(path, FileType.Classpath); }
	public FileHandle internal(String path) { return new FileHandle(path, FileType.Internal); }
	public FileHandle external(String path) { return new FileHandle(path, FileType.External); }
	public FileHandle absolute(String path) { return new FileHandle(path, FileType.Absolute); }
	public FileHandle local(String path) { return new FileHandle(path, FileType.Local); }
}
