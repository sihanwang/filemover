package com.thomsonreuters.ce.filemover;

import java.io.File;

import com.jcraft.jsch.Session;
import com.thomsonreuters.ce.ssh.SshUtils;

import org.apache.log4j.Logger;

public class SCPTransmitter extends FileTransmitter {
	
	Session session = null;
	
	public SCPTransmitter(String user, String password, String host, int port,
			String remotefolder,String prefixintransmission, String suffixintransmission) {
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
				logger.info("Logging in remote server:"+host);
				session = SshUtils.connect(user, password, host, port);
				logger.info("Server login successfully");
				IsLogin=true;
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
	public void TransferFile(FileWithMD5 file, Logger logger) {
		// TODO Auto-generated method stub

		boolean IsSuccessful=false;

		do
		{
			
			if (session==null)
			{
				Connect(logger);
			}

			logger.info("Delivering file: " + file.getFile().getName());

			try {
				SshUtils.ScpTo(file.getFile().getAbsolutePath(), remotefolder, session,this.prefixintransmission,this.suffixintransmission);
				logger.info("Delivered file: " + file.getFile().getName());
				IsSuccessful=true;
			} catch (Throwable e) {
				logger.error(
						"Failed to deliver file: " + file.getFile().getName(), e);
				Disconnect(logger);

				synchronized(file)
				{
					try {
						logger.error("Will retry after 5 seconds");
						file.wait(5000);
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
			logger.info("Logging out remote server:"+host);
			session.disconnect();
			logger.info("Server logout successfully");
			session=null;
		}
		
		
	}

}
