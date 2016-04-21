package com.thomsonreuters.ce.filemover;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.log4j.Logger;

public class FTPTransmitter extends FileTransmitter {
	
	FTPClient session = null;

	public FTPTransmitter(String user, String password, String host, int port,
			String remotefolder, String prefixintransmission, String suffixintransmission) {
		super(user, password, host, port, remotefolder, prefixintransmission, suffixintransmission);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void Connect(Logger logger){
		// TODO Auto-generated method stub

		boolean IsLogin=false;

		do
		{

			try {
				logger.info("Connecting FTP server:"+host);
				session = new FTPClient();
				session.setConnectTimeout(60000);
				session.setDataTimeout(600000);
				session.connect(host,port);
				int reply = session.getReplyCode();
				if (!FTPReply.isPositiveCompletion(reply)) {
					logger.error("FTP server refused connection.");
				}
				else
				{
					logger.info("Connected FTP server:"+host+", trying to login as user:"+user);
					
					if (!session.login(user, password)) {
						logger.error("Authentication failed");
					}
					else
					{
						logger.info("Logged in as user:"+user);
						
						//entering local passive mode.
						session.enterLocalPassiveMode();
						
						if(!session.setFileType(FTPClient.BINARY_FILE_TYPE)){
							reply = session.getReplyCode();
							logger.error("Failed to set file type" + reply);							
						}
						else
						{
							if (!remotefolder.equals(""))
							{
								logger.info("Changing working folder to "+remotefolder);

								if(!session.changeWorkingDirectory(remotefolder)){
									reply = session.getReplyCode();
									logger.error("Failed to change working folder" + reply);

								}
								else
								{
									logger.info("Changed working folder to "+remotefolder);
									IsLogin=true;
								}
							}
							else
							{
								IsLogin=true;
							}
						}
					}
				}

			} catch (Throwable e) {
				// TODO Auto-generated catch block
				logger.error("Server login failed",e);
			} 

			if (!IsLogin)
			{
				synchronized(logger)
				{
					try {
						logger.error("Will retry after 5 seconds");
						logger.wait(5000);
					} catch (InterruptedException e1) {
						// TODO Auto-generated catch block
						logger.error(e1);
					}
				}
			}

		}
		while(!IsLogin);

	}

	@Override
	public void TransferFile(FileWithMD5 file, Logger logger){
		// TODO Auto-generated method stub


		//transfer files
		boolean IsSuccessful=false;

		do
		{
			long totalbytetransmitted=0;

			if (session == null ||!session.isConnected()) {
				Connect(logger);
			}

			logger.info("Delivering file: " + file.getFile().getName()+" with size:"+file.getFile().length());

			
			try {
				InputStream LocalInputStream = null;
				OutputStream FTPOutputStream = null;
				String tempRemoteFileName=prefixintransmission+file.getFile().getName()+suffixintransmission;

				try {
					
					LocalInputStream = new FileInputStream(file.getFile());
					FTPOutputStream = session.storeFileStream(tempRemoteFileName);
					byte[] bytesIn = new byte[4096];
					int read = 0;
					
					
					while ((read = LocalInputStream.read(bytesIn)) != -1) {
						FTPOutputStream.write(bytesIn, 0, read);
						totalbytetransmitted+=read;
					}
					

				} finally {
					if (LocalInputStream != null) 
					{
						try {
							LocalInputStream.close();
						} catch (Throwable e) {
							// TODO Auto-generated catch block
							logger.error(e);
						}
					}

					if (FTPOutputStream != null)
					{
						try {
							FTPOutputStream.close();
						} catch (Throwable e) {
							// TODO Auto-generated catch block
							logger.error(e);
						}						
					}
				}

				boolean completed = session.completePendingCommand();

				if (!completed) {
					int reply = session.getReplyCode();
					logger.error("Failed to store file," + reply);
				} else {
					
					logger.info("Delivered "+totalbytetransmitted+" bytes to a temp file and renaming filename back now");

					if (!session.rename(tempRemoteFileName, file.getFile().getName())) {
						int reply = session.getReplyCode();
						logger.error("Failed to rename file," + reply);
						
						logger.error("Trying to delete existing files");
						if (!session.deleteFile(tempRemoteFileName))
						{
							reply = session.getReplyCode();
							logger.info("Failed to delete temporary file:"+tempRemoteFileName+","+reply);
						}
						else
						{
							logger.info("Deleted temporary file:"+tempRemoteFileName);
						}
						
						
						if (!session.deleteFile(file.getFile().getName()))
						{
							reply = session.getReplyCode();
							logger.info("Failed to delete file:"+file.getFile().getName()+","+reply);
						}
						else
						{
							logger.info("Deleted file:"+file.getFile().getName());
						}
						
						
						
					} else {						
						logger.info("Delivered file: " + file.getFile().getName());
						IsSuccessful = true;
					}
				}

			} catch (Throwable e) {
				logger.error("Failed to deliver file: " + file.getFile().getName(), e);

			}


			if (!IsSuccessful)
			{
				Disconnect(logger);

				synchronized (file) {
					try {
						logger.error("Will retry after 10 seconds");
						file.wait(10000);
					} catch (InterruptedException e1) {
						// TODO Auto-generated catch block
						logger.error(e1);
					}
				}
			}

		}
		while(!IsSuccessful);

	}

	
	@Override
	public void Disconnect(Logger logger) {
		// TODO Auto-generated method stub
		
		if (session != null) {
			try {
				logger.info("Logging out remote server:"+host);
				session.noop();
				session.logout();
				session.disconnect();
				logger.info("logged out server successfully");
			} catch (Throwable e) {
				// TODO Auto-generated catch block
				logger.error("Exception occured during logout",e);
			}
			
			session=null;
		}

		
	}

}
