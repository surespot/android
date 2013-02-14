package com.twofours.surespot;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class FlushedInputStream extends FilterInputStream {
	public FlushedInputStream(InputStream inputStream) {
		super(inputStream);
	}

	@Override
	public long skip(long n) throws IOException {
		long totalBytesSkipped = 0L;
		while (totalBytesSkipped < n) {
			long bytesSkipped = in.skip(n - totalBytesSkipped);
			if (bytesSkipped == 0L) {
				int byt = read();
				if (byt < 0) {
					break; // we reached EOF
				}
				else {
					bytesSkipped = 1; // we read one byte
				}
			}
			totalBytesSkipped += bytesSkipped;
		}
		return totalBytesSkipped;
	}
}
