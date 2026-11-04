/*
 * PSP port: hands NIO buffer contents to native code. Heap buffers (Quad.create) pass their array directly,
 * direct buffers made by game code are copied into a scratch array first.
 */
package com.watabou.psp;

import org.teavm.interop.Address;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public final class PSPBuffers {

	private static float[] scratch = new float[1024];
	private static short[] scratchShort = new short[1024];

	// address of element 'start'; the native side reads up to 'end'
	public static Address address( FloatBuffer b, int start, int end ) {
		if (b.hasArray()) {
			return Address.ofData( b.array() ).add( (b.arrayOffset() + start) * 4 );
		}
		int n = end - start;
		if (scratch.length < n) scratch = new float[Math.max( n, scratch.length * 2 )];
		for (int i = 0; i < n; i++) scratch[i] = b.get( start + i );
		return Address.ofData( scratch );
	}

	public static Address address( ShortBuffer b, int start, int end ) {
		if (b.hasArray()) {
			return Address.ofData( b.array() ).add( (b.arrayOffset() + start) * 2 );
		}
		int n = end - start;
		if (scratchShort.length < n) scratchShort = new short[Math.max( n, scratchShort.length * 2 )];
		for (int i = 0; i < n; i++) scratchShort[i] = b.get( start + i );
		return Address.ofData( scratchShort );
	}
}
