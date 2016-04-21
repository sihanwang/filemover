package com.thomsonreuters.ce.filemover;

import java.io.File;
import java.io.Serializable;

public class FileWithMD5 implements Serializable {
	

	public FileWithMD5(File file, String md5)
	{
		this.FileDetected=file;
		this.MD5=md5;
	}
	
	private File FileDetected;
	private String MD5;
	
	public File getFile() {
		return FileDetected;
	}
	public void setFile(File file) {
		this.FileDetected = file;
	}
	public String getMD5() {
		return MD5;
	}
	public void setMD5(String mD5) {
		MD5 = mD5;
	}	
	public int getCombinedHashCode()
	{
		return FileDetected.getName().hashCode()*31+MD5.hashCode();
	}
}
