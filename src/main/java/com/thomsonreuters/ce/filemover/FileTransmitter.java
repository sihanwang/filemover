package com.thomsonreuters.ce.filemover;

import java.io.File;

import org.apache.log4j.Logger;

public abstract class FileTransmitter {
	

	protected String user;
	protected String password;
	protected String host;
	protected int port;
	protected String remotefolder;
	protected String suffixintransmission;
	protected String prefixintransmission;
	
	public FileTransmitter(String user, String password, String host, int port,
			String remotefolder,String prefixintransmission, String suffixintransmission) {
		super();
		this.user = user;
		this.password = password;
		this.host = host;
		this.port = port;
		this.remotefolder = remotefolder;
		this.prefixintransmission=prefixintransmission;
		this.suffixintransmission = suffixintransmission;
		
	}
	
	public abstract void Connect(Logger logger);
	public abstract void TransferFile(FileWithMD5 file, Logger logger);
	public abstract void Disconnect(Logger logger);
	

}
