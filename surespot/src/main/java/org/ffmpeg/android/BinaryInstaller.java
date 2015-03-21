package org.ffmpeg.android;

/* Copyright (c) 2009, Nathan Freitas, Orbot / The Guardian Project - http://openideals.com/guardian */
/* See LICENSE for licensing information */

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import android.content.Context;
import android.util.Log;

import com.twofours.surespot.R;

public class BinaryInstaller {

	File installFolder;
	Context context;

	private final static int FILE_WRITE_BUFFER_SIZE = 32256;

	public BinaryInstaller(Context context, File installFolder) {
		this.installFolder = installFolder;

		this.context = context;
	}

	//
	/*
	 * Extract the Tor binary from the APK file using ZIP
	 */
	public boolean installFromRaw(boolean pie) throws IOException, FileNotFoundException {

		InputStream is;
		File outFile;

		if (pie) {
			is = context.getResources().openRawResource(R.raw.ffmpegpie);
		}
		else {
			is = context.getResources().openRawResource(R.raw.ffmpeg);
		}
		outFile = new File(installFolder, "ffmpeg" + (pie ? "pie" : ""));
		streamToFile(is, outFile, false, false, "700");
		return true;
	}

	/*
	 * Write the inputstream contents to the file
	 */
	private static boolean streamToFile(InputStream stm, File outFile, boolean append, boolean zip, String mode) throws IOException

	{
		byte[] buffer = new byte[FILE_WRITE_BUFFER_SIZE];

		int bytecount;

		OutputStream stmOut = new FileOutputStream(outFile, append);

		if (zip) {
			ZipInputStream zis = new ZipInputStream(stm);
			ZipEntry ze = zis.getNextEntry();
			stm = zis;

		}

		while ((bytecount = stm.read(buffer)) > 0) {

			stmOut.write(buffer, 0, bytecount);

		}

		stmOut.close();
		stm.close();

		Runtime.getRuntime().exec("chmod " + mode + " " + outFile.getCanonicalPath());

		return true;

	}

	// copy the file from inputstream to File output - alternative impl
	public void copyFile(InputStream is, File outputFile) {

		try {
			outputFile.createNewFile();
			DataOutputStream out = new DataOutputStream(new FileOutputStream(outputFile));
			DataInputStream in = new DataInputStream(is);

			int b = -1;
			byte[] data = new byte[1024];

			while ((b = in.read(data)) != -1) {
				out.write(data);
			}

			if (b == -1)
				; // rejoice

			//
			out.flush();
			out.close();
			in.close();
			// chmod?

		}
		catch (IOException ex) {
			Log.e("ffmpeg", "error copying binary", ex);
		}

	}

}
