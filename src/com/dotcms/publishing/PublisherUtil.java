package com.dotcms.publishing;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import com.dotmarketing.util.ConfigUtils;
import com.dotmarketing.util.Logger;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

public class PublisherUtil {


	
	/**
	 * This method takes a config and will create the bundle directory and
	 * write the bundle.xml file to it
	 * @param config
	 */
	protected File initBundle(PublisherConfig config){
		

		String bundlePath = ConfigUtils.getBundlePath()+ File.separator + config.getId();
		
		
		File dir = new File(bundlePath);
		File xml = new File(bundlePath + File.separator + "bundle.xml");
		if(dir.exists() && xml.exists()){
			return dir;
		}
		dir.mkdirs();

		objectToXML(config, xml);
		return dir;

	}
	
	
	protected void objectToXML(Object obj, File f){
		XStream xstream = new XStream(new DomDriver());


		
		try {
			BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(f));
			xstream.toXML(obj, out);
			out.close();
			
			
		} catch (FileNotFoundException e) {
			Logger.error(PublisherUtil.class,e.getMessage(),e);
		}catch (IOException e) {
			Logger.error(PublisherUtil.class,e.getMessage(),e);
		}	

	}
	
	
	
	
	
	

}
