package com.thomsonreuters.ce.filemover;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

import org.apache.log4j.Logger;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Date;
import java.util.Hashtable;

import com.thomsonreuters.ce.thread.ControlledThread;
import com.thomsonreuters.ce.thread.ThreadController;
import com.thomsonreuters.ce.queue.MagicPipe;
import com.thomsonreuters.ce.dbor.file.ExtensionFilter;

public class FileDetector implements Runnable {

	private static final int STREAM_BUFFER_LENGTH = 524288;
	private String waiting;
	private String fileextension;
	private int delay;
	private Destination[] dests;
	private Logger logger;
	private ThreadController TC;
	
	public FileDetector(String Waiting,Logger fileLogger, String Fileextension, int delay, Destination[] Dests, ThreadController tc)
	{
		this.TC=tc;
		this.waiting=Waiting;
		this.fileextension=Fileextension;
		this.dests=Dests;
		this.logger=fileLogger;
		this.delay=delay;
	}

	
	public void run() {
		
		Path waitingFolder = Paths.get(this.waiting);
		WatchService watcher;
		WatchKey key;
		
		try {
			
			watcher = FileSystems.getDefault().newWatchService();
			key = waitingFolder.register(watcher,
					new WatchEvent.Kind[] {
							StandardWatchEventKinds.ENTRY_CREATE });
		} catch (IOException e) {
			// TODO Auto-generated catch block
			this.logger.error("Error occured when registering os event", e);
			return;
		}
		
		
		///////////////////////////////////////////////////////
		//Identify new files in waiting folder				
		
		this.logger.info("File detector is identifying outstanding files in folder:"+this.waiting);

		File WaitingFolder = new File(waiting);
		File[] ExistingFileList = WaitingFolder.listFiles(new ExtensionFilter(this.fileextension));

		FileWithMD5[] ExistingFilesWithMD5=new FileWithMD5[0];
		
		for (int i=0; i<ExistingFileList.length; i++ )
		{
			try {
				FileWithMD5[] tempFileList=new FileWithMD5[ExistingFilesWithMD5.length+1];
				System.arraycopy(ExistingFilesWithMD5, 0, tempFileList, 0, ExistingFilesWithMD5.length);
				tempFileList[ExistingFilesWithMD5.length]=new FileWithMD5(ExistingFileList[i], calculateMd5(ExistingFileList[i]));
				ExistingFilesWithMD5=tempFileList;
			} catch (Exception e) {
				// TODO Auto-generated catch block
				this.logger.error("Error occured when identifying new files in waiting folder", e);
				continue;
			}
		}
		
		for (Destination dest:dests)
		{

			String InitialMode=dest.getInitialmode();
					
			if (InitialMode.equals("N"))
			{
				//////////////////////////////////
				//Checking history
				FileLock fl=dest.LockHistoryFile();
				HashSet<Integer> filehistory=dest.getHashHistory();
				
				FileWithMD5[] newFileList=new FileWithMD5[0];
				FileWithMD5[] oldFileList=new FileWithMD5[0];
				
				for (FileWithMD5 ExistingFileWithMD5 : ExistingFilesWithMD5)
				{
					
					int CombinedHashCode=ExistingFileWithMD5.getCombinedHashCode();
					
					if (!filehistory.contains(CombinedHashCode))
					{
						FileWithMD5[] tempnewFileList=new FileWithMD5[newFileList.length+1];
						System.arraycopy(newFileList, 0, tempnewFileList, 0, newFileList.length);
						tempnewFileList[newFileList.length]=ExistingFileWithMD5;
						newFileList=tempnewFileList;
					}
					else
					{
						FileWithMD5[] tempOldFileList=new FileWithMD5[oldFileList.length+1];
						System.arraycopy(oldFileList, 0, tempOldFileList, 0, oldFileList.length);
						tempOldFileList[oldFileList.length]=ExistingFileWithMD5;
						oldFileList=tempOldFileList;						
					}
					
				}
				
				
				dest.Recreatehistory(oldFileList);

				dest.UnlockHistoryFile(fl);				
				
				//////////////////////////////////
				
				MagicPipe<FileWithMD5[]> TaskQueue =null;

				TaskQueue=new MagicPipe<FileWithMD5[]>(500, 1000, 1024, 50, Starter.tempfolder);
				Starter.RoutingTable.put(dest, TaskQueue);			
				TaskQueue.putObj(newFileList);
				dest.IncQueueSize();

				new Thread(new FileSender(dest, TaskQueue,this.TC)).start();	
				
			}
			else if(InitialMode.equals("R"))
			{
				FileLock fl=dest.LockHistoryFile();
				for (FileWithMD5 existingfile: ExistingFilesWithMD5)
				{
					dest.AppendRecord(existingfile);
				}
				dest.UnlockHistoryFile(fl);
			}
			else
			{
				this.logger.error("Dest:"+dest.toString()+" is configured with unknown initial mode:"+InitialMode);
			}

		}
		
		this.logger.info("File detector is watching new incoming files in folder:"+this.waiting);
		
		
		while(true)
		{
			ExistingFilesWithMD5=new FileWithMD5[0];
			
			try {
				key = watcher.take();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				this.logger.error("Error while listening OS file event",e);
			}
			
			for (WatchEvent<?> event : key.pollEvents()) {	
				
				Path file = (Path) event.context();
				
				if ((event.kind() == StandardWatchEventKinds.ENTRY_CREATE)	&& (file.getFileName().toString().matches(this.fileextension))) {
					
					file = waitingFolder.resolve(file);
					File newFile=file.toFile();
					
					synchronized(newFile)
					{
						try {
							newFile.wait(2000);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
					
					
					if (!newFile.exists())
					{
						this.logger.warn("New file:"+newFile.getName() +" was detected, but has disappeared for some reason");
						continue;
					}
					
					FileWithMD5[] tempFileList=new FileWithMD5[ExistingFilesWithMD5.length+1];
					System.arraycopy(ExistingFilesWithMD5, 0, tempFileList, 0, ExistingFilesWithMD5.length);
					try {
						tempFileList[ExistingFilesWithMD5.length]=new FileWithMD5(newFile,calculateMd5(newFile));
					} catch (Exception e) {
						// TODO Auto-generated catch block
						this.logger.error("Error while calculating MD5 for new file",e);
						continue;
					}
					ExistingFilesWithMD5=tempFileList;
					
				}
			}
			
			boolean valid = key.reset();
			
			if (!valid)
			{
				break;
			}
			
			if (ExistingFilesWithMD5.length>0)				
			{
				
				String strFileListInfo=ExistingFilesWithMD5.length
						+ " new file(s) had been noticed in waiting folder"+System.lineSeparator();

				for(int i=0 ; i < ExistingFilesWithMD5.length ; i++ )
				{
					strFileListInfo=strFileListInfo+"("+(i+1)+"):"+ExistingFilesWithMD5[i].getFile().getName()+System.lineSeparator();
				}

				this.logger.info(strFileListInfo);
				
				synchronized(ExistingFilesWithMD5)
				{
					try {
						ExistingFilesWithMD5.wait(this.delay);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

				for (Destination dest:dests)
				{	

					MagicPipe<FileWithMD5[]> TaskQueue =null;

					synchronized (dest)
					{

						TaskQueue = Starter.RoutingTable.get(dest);

						if (TaskQueue!=null)				
						{
							TaskQueue.putObj(ExistingFilesWithMD5);
							dest.IncQueueSize();
							continue;
						}

					}	

					TaskQueue=new MagicPipe<FileWithMD5[]>(500, 1000, 1024, 50, Starter.tempfolder);
					Starter.RoutingTable.put(dest, TaskQueue);			
					TaskQueue.putObj(ExistingFilesWithMD5);
					dest.IncQueueSize();
					new Thread(new FileSender(dest, TaskQueue,this.TC)).start();	

				}	
				
				
			}
			
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
    
    
	private static String calculateMd5(File file) throws Exception  {
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

	private class FileSender extends ControlledThread {
		
		private Destination Dest;
		private MagicPipe<FileWithMD5[]> TaskQueue;
		
		public FileSender(Destination dest, MagicPipe<FileWithMD5[]> tq, ThreadController tc)
		{
			super(tc);
			this.Dest=dest;
			this.TaskQueue=tq;
		}

		@Override
		public void ControlledProcess() {
			// TODO Auto-generated method stub
			FileTransmitter FT= Dest.getTransmitter();
			Logger destLogger=Logger.getLogger(this.Dest.getLogger());
			
			if (FT!=null)
			{

				try {

					while (!IsShuttingDown()) {

						FileWithMD5[] FileList = null;

						synchronized (Dest)
						{
							
							if (Dest.getQueueSize()==0)
							{
								Starter.RoutingTable.remove(this.Dest);
								break;
							}
							
							FileList=TaskQueue.getObj();
							if (FileList==null)
							{
								//for shutting down
								break;
							}
							
							Dest.DecQueueSize();							
						}

						
						for (FileWithMD5 Noticedfile: FileList)
						{

							FileLock fl=Dest.LockHistoryFile();
							try
							{
								if (!Noticedfile.getFile().exists())
								{
									destLogger.warn("New file:"+Noticedfile.getFile().getName() +" was detected, but has disappeared for some reason");
									continue;
								}
								
								FT.TransferFile(Noticedfile, destLogger);
								Dest.AppendRecord(Noticedfile);
							}
							finally
							{

								Dest.UnlockHistoryFile(fl);
							}
						}

					}

				} 
				finally
				{
					FT.Disconnect(destLogger);
				}
				
			}
			else
			{
				destLogger.error("Can't get file transmitter");
			}

		}
	}


}
