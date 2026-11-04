/*
 * PSP port: libGDX FileHandle subset over native file calls.
 * Internal files live under Files.internalRoot (read-only assets), Local/External under Files.localRoot.
 */
package com.badlogic.gdx.files;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.watabou.psp.PSPNative;

import org.teavm.interop.Address;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class FileHandle {

	private final String path;
	private final Files.FileType type;

	public FileHandle(String path, Files.FileType type) {
		this.path = path.replace('\\', '/');
		this.type = type;
	}

	public String path() { return path; }
	public Files.FileType type() { return type; }

	public String name() {
		String p = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
		int i = p.lastIndexOf('/');
		return i < 0 ? p : p.substring(i + 1);
	}

	public String extension() {
		String n = name();
		int i = n.lastIndexOf('.');
		return i < 0 ? "" : n.substring(i + 1);
	}

	public String nameWithoutExtension() {
		String n = name();
		int i = n.lastIndexOf('.');
		return i < 0 ? n : n.substring(0, i);
	}

	public String fullPath() {
		String root = type == Files.FileType.Internal || type == Files.FileType.Classpath ? Files.internalRoot
				: type == Files.FileType.Absolute ? "" : Files.localRoot;
		return root + path;
	}

	private byte[] c() { return PSPNative.cstr(fullPath()); }

	public FileHandle child(String name) {
		return new FileHandle(path.isEmpty() ? name : (path.endsWith("/") ? path + name : path + "/" + name), type);
	}

	public FileHandle sibling(String name) { return parent().child(name); }

	public FileHandle parent() {
		int i = path.lastIndexOf('/', path.endsWith("/") ? path.length() - 2 : path.length());
		return new FileHandle(i < 0 ? "" : path.substring(0, i), type);
	}

	public boolean exists() { return PSPNative.fileSize(Address.ofData(c())) != -1; }
	public boolean isDirectory() { return PSPNative.fileSize(Address.ofData(c())) == -2; }

	public long length() {
		int s = PSPNative.fileSize(Address.ofData(c()));
		return s < 0 ? 0 : s;
	}

	public long lastModified() { return PSPNative.fileMtime(Address.ofData(c())); }

	public byte[] readBytes() {
		byte[] p = c();
		int size = PSPNative.fileSize(Address.ofData(p));
		if (size < 0) throw new GdxRuntimeException("File not found: " + fullPath());
		byte[] data = new byte[size];
		if (size > 0 && PSPNative.fileRead(Address.ofData(p), Address.ofData(data), size) != size) {
			throw new GdxRuntimeException("Error reading file: " + fullPath());
		}
		return data;
	}

	public String readString() { return readString("UTF-8"); }

	public String readString(String charset) {
		try {
			return new String(readBytes(), charset);
		} catch (IOException e) {
			throw new GdxRuntimeException(e);
		}
	}

	public InputStream read() { return new ByteArrayInputStream(readBytes()); }

	public OutputStream write(final boolean append) {
		if (type == Files.FileType.Internal || type == Files.FileType.Classpath) {
			throw new GdxRuntimeException("Cannot write to an internal file: " + path);
		}
		parent().mkdirs();
		return new ByteArrayOutputStream() {
			private boolean closed;
			@Override
			public void close() throws IOException {
				if (closed) return;
				closed = true;
				FileHandle.this.writeBytes(toByteArray(), append);
			}
		};
	}

	public void writeBytes(byte[] bytes, boolean append) {
		if (type == Files.FileType.Internal || type == Files.FileType.Classpath) {
			throw new GdxRuntimeException("Cannot write to an internal file: " + path);
		}
		parent().mkdirs();
		if (PSPNative.fileWrite(Address.ofData(c()), Address.ofData(bytes), bytes.length, append ? 1 : 0) < 0) {
			throw new GdxRuntimeException("Error writing file: " + fullPath());
		}
	}

	public void writeString(String string, boolean append) {
		writeBytes(string.getBytes(java.nio.charset.StandardCharsets.UTF_8), append);
	}

	public void mkdirs() {
		// an empty path is the root of its file type (e.g. the save folder), which may not exist yet
		if (!fullPath().isEmpty()) PSPNative.fileMkdirs(Address.ofData(c()));
	}

	public boolean delete() { return PSPNative.fileDelete(Address.ofData(c())) >= 0; }

	public boolean deleteDirectory() {
		if (isDirectory()) {
			for (FileHandle f : list()) {
				if (f.isDirectory()) f.deleteDirectory(); else f.delete();
			}
		}
		return delete();
	}

	public void moveTo(FileHandle dest) {
		dest.delete();
		if (PSPNative.fileRename(Address.ofData(c()), Address.ofData(dest.c())) < 0) {
			dest.writeBytes(readBytes(), false);
			delete();
		}
	}

	public FileHandle[] list() {
		byte[] buf = new byte[16 * 1024];
		int n = PSPNative.dirList(Address.ofData(c()), Address.ofData(buf), buf.length);
		ArrayList<FileHandle> out = new ArrayList<>();
		if (n > 0) {
			String names = new String(buf, 0, n, java.nio.charset.StandardCharsets.UTF_8);
			for (String s : names.split("\n")) {
				if (!s.isEmpty()) out.add(child(s));
			}
		}
		return out.toArray(new FileHandle[0]);
	}

	@Override
	public String toString() { return path; }
}
