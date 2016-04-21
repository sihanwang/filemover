package com.thomsonreuters.ce.filemover;

import java.io.File;
import java.nio.channels.FileLock;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

import java.util.HashSet;

import org.apache.log4j.Logger;

import com.thomsonreuters.ce.dbor.file.FileUtilities;


public class Destination {
	
	
	
	private String user;
	private String password;
	private String host;
	private int port;
	private String remotefolder;
	private String protocol;
	private String logger;
	private Logger destLogger;
	private String initialmode;
	private FileChannel HistoryFileChannel;
	private File HistoryFile;
	private String prefixintransmission;
	private String suffixintransmission;
	
	public String getPrefixintransmission() {
		return prefixintransmission;
	}

	public void setPrefixintransmission(String prefixintransmission) {
		this.prefixintransmission = prefixintransmission;
	}
	
	public String getSuffixintransmission() {
	    return suffixintransmission;
	}

	public void setSuffixintransmission(String suffixintransmission) {
	    this.suffixintransmission = suffixintransmission;
	}

	private int QueueSize=0;
	
	
	public void IncQueueSize()
	{
		this.QueueSize++;
	}
	
	public void DecQueueSize()
	{
		this.QueueSize--;
	}
	
	public int getQueueSize()
	{
		return this.QueueSize;
	}
	
	public String getUser() {
		return user;
	}
	public String getPassword() {
		return password;
	}
	public String getHost() {
		return host;
	}
	public int getPort() {
		return port;
	}
	public String getRemotefolder() {
		return remotefolder;
	}
	public String getProtocol() {
		return protocol;
	}
	public void setUser(String user) {
		this.user = user;
	}
	public void setPassword(String password) {
		this.password = password;
	}
	public void setHost(String host) {
		this.host = host;
	}
	public void setPort(int port) {
		this.port = port;
	}
	public void setRemotefolder(String remotefolder) {
		this.remotefolder = remotefolder;
	}
	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}
	
	public String getInitialmode(){
		return this.initialmode;
	}
	
	public void setHistory(String history) {
		
		try {
			String historyfile=FileUtilities.GetAbsolutePathFromEnv(history);
			HistoryFile=new File(historyfile);
			
			if(!HistoryFile.exists())
			{
				this.initialmode="R";
				HistoryFile.createNewFile();
			}			
			else
			{
				this.initialmode="N";
			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			destLogger.error("Error occured where initializing history file",e);
		} 
	}
	
	public String getLogger() {
		return logger;
	}
	
	public void setLogger(String logger) {
		this.logger = logger;
		destLogger=Logger.getLogger(this.logger);
	}
	

	public FileLock LockHistoryFile()
	{
		try {
			HistoryFileChannel = new RandomAccessFile(HistoryFile, "rw").getChannel();  
			return HistoryFileChannel.lock();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			destLogger.error("Error occured where trying to lock history file",e);			
		}
		
		return null;
	}
	
	public void UnlockHistoryFile(FileLock fl)
	{
		try {			
			fl.release();
			HistoryFileChannel.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			destLogger.error("Error occured where trying to lock history file",e);
		}	
	}
	
	
	public void AppendRecord(FileWithMD5 newFileWithMD5)
	{
		try {
			
			BufferedWriter  writer = new BufferedWriter (new FileWriter(HistoryFile, true));
			
			String FileRecord=newFileWithMD5.getFile().getName()+","+newFileWithMD5.getMD5();
			writer.write(FileRecord);
			writer.newLine();			
			writer.flush();
			writer.close();
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			destLogger.error("Error occured where appending file record",e);
		} 
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
    
	public FileTransmitter getTransmitter()
	{
		if (protocol.equals("scp"))
		{
			return new SCPTransmitter(user, password, host, port, remotefolder,prefixintransmission, suffixintransmission);
		}
		else if (protocol.equals("ftp"))
		{
			return new FTPTransmitter(user, password, host, port, remotefolder,prefixintransmission, suffixintransmission);
		}
		
		return null;
	}
	

	public HashSet<Integer> getHashHistory()
	{
		HashSet<Integer> FileHistory = null;
		
		try {
			
			FileHistory = new HashSet<Integer>();
			
			BufferedReader br=new BufferedReader(new FileReader(HistoryFile));
			
			String strline=null;
			
			while((strline = br.readLine()) != null) {
				
				String[] Record=strline.split(",");
				
				String Filename=Record[0];
				String strMD5=Record[1];
				
				int hashvalue=Filename.hashCode()*31+strMD5.hashCode();
				FileHistory.add(hashvalue);		
           }
          
           br.close();
			
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			destLogger.error("Error occured while reading through history file",e);
		} 
		
		return FileHistory;
	}
	
	
	public void Recreatehistory(FileWithMD5[] OldFileWithMD5List)
	{
		
		try {
			
			BufferedWriter writer = new BufferedWriter (new FileWriter(HistoryFile, false));
			
			for (FileWithMD5 oldfile: OldFileWithMD5List)
			{
				String FileRecord=oldfile.getFile().getName()+","+oldfile.getMD5();
				writer.write(FileRecord);
				writer.newLine();	
			}
			
			writer.flush();
			writer.close();
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			destLogger.error("Error occured where recreating file record",e);
		}
		
	}

	@Override
	public String toString() {
		// TODO Auto-generated method stub
		return this.user+"/"+this.password+"@"+this.host+":"+this.port+":"+this.remotefolder;
	}
	

	


}
