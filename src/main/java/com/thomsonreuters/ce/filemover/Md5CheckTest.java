package com.thomsonreuters.ce.filemover;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Date;

public class Md5CheckTest {

	private static final int STREAM_BUFFER_LENGTH = 524288;

	private static String calculateMd5(String file) throws Exception  {
		FileInputStream is=new FileInputStream(file);
		MessageDigest digest = MessageDigest.getInstance("MD5");
		byte[] bytesBuffer = new byte[STREAM_BUFFER_LENGTH];
		int bytesRead;
		while ((bytesRead = is.read(bytesBuffer)) != -1) {
			digest.update(bytesBuffer, 0, bytesRead);
		}
		is.close();
		return toHexString(digest.digest());
		
	}
	
    public static String toHexString(byte[] bytes) {
        String hex = null;

        if(bytes != null) {
            hex = "";
            for(byte b : bytes) {
                String h = Integer.toString(b & 0xff, 16);
                hex += (h.length() == 1 ? "0" : "") + h;
            }
        }
        
        return hex;
    }
    
    public static byte[] hexStringToByteArray(String hex) {
        byte[] ret = null;
        if(hex != null) {
            ret = new byte[hex.length()/2];
            for(int i = 0; i < ret.length; ++i) {
                ret[i] = (byte)Integer.parseInt(hex.substring(i*2, i*2+2), 16);
            }
        }
        return ret;
    }
    
	public static void main(String args[]) throws Exception {
		
		long time=new Date().getTime();
		
		File folder = new File(args[0]);
		File[] listOfFiles = folder.listFiles();

		for (int i = 0; i < listOfFiles.length; i++) {
			if (listOfFiles[i].isFile()) {
				String fineName = listOfFiles[i].getName();
				String md5 = calculateMd5(listOfFiles[i].getPath());
				System.out.println(fineName + ":  "+ md5);

			}
		}
		
		System.out.println("Total time:" + (new Date().getTime()-time));
	}
}
