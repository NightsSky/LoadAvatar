package com.nightssky.loadavatar.Utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.os.Environment;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileUtil {


	/**
	 * 将Bitmap 图片保存到本地路径，并返回路径
	 * 取出图片
	 * @param c
	 * @param fileName 文件名称
	 * @param bitmap 图片
	 * @return
	 */
	public static String saveFile(Context c, String fileName, Bitmap bitmap) {
		return saveFile(c, "", fileName, bitmap);
	}

	public static String saveFile(Context c, String filePath, String fileName, Bitmap bitmap) {
		byte[] bytes = bitmapToBytes(bitmap);
		return saveFile(c, filePath, fileName, bytes);
	}

	public static byte[] bitmapToBytes(Bitmap bm) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		bm.compress(CompressFormat.JPEG, 100, baos);
		return baos.toByteArray();
	}

	public static String saveFile(Context c, String filePath, String fileName, byte[] bytes) {
		String fileFullName = "";
		FileOutputStream fos = null;
		String dateFolder = new SimpleDateFormat("yyyyMMdd", Locale.CHINA)
				.format(new Date());
		try {
			String suffix = "";
			if (filePath == null || filePath.trim().length() == 0) {
				//  + dateFolder + "/"
				filePath = Environment.getExternalStorageDirectory() + "/AHead/" ;
			}
			File file = new File(filePath);
			if (!file.exists()) {
				file.mkdirs();
			}
			File fullFile = new File(filePath, fileName + suffix);
			fileFullName = fullFile.getPath();
			fos = new FileOutputStream(new File(filePath, fileName + suffix));
			fos.write(bytes);
		} catch (Exception e) {
			fileFullName = "";
		} finally {
			if (fos != null) {
				try {
					fos.close();
				} catch (IOException e) {
					fileFullName = "";
				}
			}
		}
		return fileFullName;
	}

	public static byte[] getBuffer(String path) {
		File file = null;
		FileInputStream fis = null;
		byte[] cover = null;
		try {
			file = new File(path);
			int length = (int) file.length();
			fis = new FileInputStream(file);
			cover = new byte[length];
			fis.read(cover, 0, length);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			FileUtil.closeCloseable(fis);
		}
		return cover;
	}
	/**
	 * 关闭stream or reader
	 *
	 * @param closeObj
	 */
	public static void closeCloseable(Closeable closeObj) {
		try {
			if (null != closeObj)
				closeObj.close();
		} catch (IOException e) {
			Log.e("ReadFileUtils Error",
					"Method:readFile, Action:closeReader\t" + e.getMessage());
		}
	}
}
