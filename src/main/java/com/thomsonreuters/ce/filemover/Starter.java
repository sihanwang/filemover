package com.thomsonreuters.ce.filemover;

import java.util.Properties;
import java.io.File;
import java.io.FileInputStream;
import java.util.StringTokenizer;
import java.util.HashMap;
import java.net.InetAddress;



import java.net.UnknownHostException;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import com.thomsonreuters.ce.dbor.file.FileUtilities;
import com.thomsonreuters.ce.thread.ThreadController;
import com.thomsonreuters.ce.dbor.server.SrvControl;
import com.thomsonreuters.ce.queue.MagicPipe;


public class Starter implements SrvControl {

	private static final String Config_File = "../cfg/filemover.conf";
	private static Starter filemover = null;
	public ThreadController TC = null;
	public static String SERVICE_NAME = "filemover";
	public static String tempfolder;
	public static String HOSTNAME;
	private Logger thisLogger;
	
	public static HashMap<Destination, MagicPipe<FileWithMD5[]>> RoutingTable;

	public void Start(Properties prop) {

		// ///////////////////////////////////////////////////////////////////////////
		// Initialize logging
		// ///////////////////////////////////////////////////////////////////////////
		String loggingCfg = prop.getProperty("logging.configuration");
		PropertyConfigurator.configure(loggingCfg);
		thisLogger = Logger.getLogger(SERVICE_NAME);
		thisLogger.info("Logging is working");
		
		// ////////////////////////////////////////////
		
		RoutingTable=new HashMap<Destination, MagicPipe<FileWithMD5[]>>();
		tempfolder=FileUtilities.GetAbsolutePathFromEnv(prop.getProperty("tempfolder"));
		String DatasetNames = prop.getProperty("filejobs");
		StringTokenizer DataSetNamesList = new StringTokenizer(DatasetNames,
				",", false);

		TC = new ThreadController();

		while (DataSetNamesList.hasMoreTokens()) {

			String DataSetName = DataSetNamesList.nextToken().trim();
			String waiting = FileUtilities.GetAbsolutePathFromEnv(prop
					.getProperty(DataSetName + ".waiting"));
			String fileextension = prop.getProperty(DataSetName
					+ ".fileextension");
			
			String strlogger=prop.getProperty(DataSetName + ".logger");
			Logger fileLogger=Logger.getLogger(strlogger);
			
			String destinationjson = prop.getProperty(DataSetName + ".destination");
			
			int delay=Integer.parseInt(prop.getProperty(DataSetName + ".delay"));

			Destination[] dests=ConvertToDests(destinationjson); 
			
			FileDetector newDetector = new FileDetector(waiting, fileLogger, fileextension, delay, dests,TC);
			new Thread(newDetector).start();
			
			thisLogger.info("File detector:"+DataSetName+" has been initialized");

		}

		thisLogger.info("All file detectors have been started");
	}
	
	
	public Destination[] ConvertToDests(String jsonDests)
	{
		
		JSONArray jsonArray=JSONArray.fromObject(jsonDests); 
		
		Destination[] dests=new Destination[jsonArray.size()];
		
		for (int i=0; i < jsonArray.size(); i++)
		{
			JSONObject jsonObj=jsonArray.getJSONObject(i);
			Destination dst=(Destination)JSONObject.toBean(jsonObj, Destination.class);
			dests[i]=dst;
		}
		
		return dests;
	}

	public void Stop() {
		// Normal shutdown		
		
		TC.Shutdown();
		thisLogger.info("Shutdown signal is sent");
		
		for (Destination dest:RoutingTable.keySet())
		{
			synchronized(dest)
			{
				RoutingTable.get(dest).Shutdown(false);
			}			
		}		
		
		
		TC.WaitToDone();
		thisLogger.info("All threads are done");

		thisLogger.info(SERVICE_NAME +" is put down as requested");
		
		System.exit(0);

	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {

		if (args.length > 0 && "stop".equals(args[0])) {
			if (filemover != null) {
				filemover.Stop();
			}
			System.exit(0);
		}

		// ///////////////////////////////////////////////////////////////////////////
		// Read config file into prop object
		// ///////////////////////////////////////////////////////////////////////////
		try {
			HOSTNAME=InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			System.out.println("Can't identify the host name of this FTP server: " + e);
		}
		
		Properties prop = new Properties();
		try {
			FileInputStream fis = new FileInputStream(Config_File);
			prop.load(fis);
		} catch (Exception e) {
			System.out.println("Can't read configuration file: " + Config_File);
		}

		// Start service
		filemover = new Starter();
		filemover.Start(prop);

		filemover.thisLogger.info(SERVICE_NAME + " is working now!");
	}

}