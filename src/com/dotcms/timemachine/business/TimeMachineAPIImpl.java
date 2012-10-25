package com.dotcms.timemachine.business;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.dotcms.enterprise.publishing.timemachine.TimeMachineConfig;
import com.dotcms.publishing.DotPublishingException;
import com.dotcms.publishing.PublishStatus;
import com.dotmarketing.beans.Host;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.quartz.QuartzUtils;
import com.dotmarketing.quartz.ScheduledTask;
import com.dotmarketing.util.ConfigUtils;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.UtilMethods;

public class TimeMachineAPIImpl implements TimeMachineAPI {

	@Override
	public PublishStatus startTimeMachineForHostAndDate(Host host, Date date) throws DotDataException {
				
		TimeMachineConfig tmconfig = new TimeMachineConfig();

		tmconfig.setUser(APILocator.getUserAPI().getSystemUser());
		tmconfig.setHosts(Arrays.asList(host));

		// tmconfig.setSourceBundle()
		tmconfig.setId("timeMachineBundle");
		tmconfig.setDestinationBundle("tm_" + date.getTime());
		// tmconfig.setExcludePatterns(Arrays.asList("*.dot"));
		// tmconfig.setLiveOnly(false);
		//Optional: time machine publisher will make it true
		tmconfig.setIncremental(true);

		try {
			return APILocator.getPublisherAPI().publish(tmconfig);
		} catch (DotPublishingException e) {
			throw new DotDataException("Error in publishing time machine bundle", e);
		}		
	}
	
	@Override
	public List<Host> getHostsWithTimeMachine() {
	    Set<Host> list = new HashSet<Host>();
        File bundlePath = new File(ConfigUtils.getBundlePath());
        for ( File file : bundlePath.listFiles()) {
            if ( file.isDirectory() && file.getName().startsWith("tm_")) {
                File hostDir = new File(file.getAbsolutePath() + File.separator + "live");
                if ( hostDir.exists() && hostDir.isDirectory() ) {
                    for(String hostname : hostDir.list()) {
                        try {
                            Host host=APILocator.getHostAPI().findByName(hostname, APILocator.getUserAPI().getSystemUser(), false);
                            if(host!=null && UtilMethods.isSet(host.getIdentifier()))
                                list.add(host);
                        }catch(Exception ex) {
                            Logger.warn(this, ex.getMessage(),ex);
                        }
                    }
                }
            }
        }
        return new ArrayList<Host>(list);
	}

	@Override
	public List<Date> getAvailableTimeMachineForSite(Host host) throws DotDataException {
		List<Date> list = new ArrayList<Date>();
		File bundlePath = new File(ConfigUtils.getBundlePath());
		for ( File file : bundlePath.listFiles()) {
			if ( file.isDirectory() && file.getName().startsWith("tm_")) {
				File hostDir = new File(file.getAbsolutePath() + File.separator + "live" + File.separator + host.getHostname());
				if ( hostDir.exists() && hostDir.isDirectory() ) {
					try {
					    Date date=new Date(Long.parseLong(file.getName().substring(3)));
						list.add(date);
					}
					catch (Throwable t) {
						Logger.error(this, "bundle seems a time machine bundle but it is not! " + file.getName());
					}
				}
			}
		}
		return list;
	}
	
	@Override
	public ScheduledTask getQuartzJob() {
	    try {
	        List<ScheduledTask> sched = QuartzUtils.getScheduledTasks("timemachine");
	        if(sched.size()==0) {
	            return null;
	        }
	        else {
	            return sched.get(0);
	        }
	    }
	    catch(Exception ex) {
	        throw new RuntimeException(ex);
	    }
	}


    @Override
    public List<String> getAvailableLangForTimeMachine(Host host, Date date) {
        File hostPath = new File(ConfigUtils.getBundlePath()+File.separator+
                                 "tm_"+date.getTime()+File.separator+
                                 "live"+File.separator+host.getHostname());
        if(hostPath.exists()) {
            return Arrays.asList(hostPath.list());
        }
        else {
            return null;
        }
    }

}
