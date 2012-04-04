package com.dotcms.content.elasticsearch.business;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.elasticsearch.ElasticSearchException;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;

import com.dotcms.content.elasticsearch.business.IndiciesAPI.IndiciesInfo;
import com.dotcms.publishing.SiteSearchConfig;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.quartz.CronScheduledTask;
import com.dotmarketing.quartz.QuartzUtils;
import com.dotmarketing.quartz.ScheduledTask;
import com.dotmarketing.quartz.SimpleScheduledTask;
import com.dotmarketing.sitesearch.business.DotSearchResults;
import com.dotmarketing.sitesearch.business.SiteSearchAPI;
import com.dotmarketing.sitesearch.job.SiteSearchJobProxy;

public class ESSiteSearchAPI implements SiteSearchAPI{

	private static final ESIndexAPI iapi  = new ESIndexAPI();
    private static final ESMappingAPIImpl mappingAPI = new ESMappingAPIImpl();
	/**
	 * List of all sitesearch indicies
	 * @return
	 */
    @Override
	public  List<String> listIndices() {

		List<String> indices=new ArrayList<String>();
		for(String x:iapi.listIndices()){
			if(x.startsWith(ES_SITE_SEARCH_NAME)){
				indices.add(x);
			}
			
		}
		Collections.sort(indices);
		Collections.reverse(indices);
		return indices;
	}
	@Override
	public DotSearchResults search(String query, String sort, int start, int rows, String lang, String hostId) {
		
		return null;
	}
	
	
	@Override
    public void activateIndex(String indexName) throws DotDataException {
        IndiciesInfo info=APILocator.getIndiciesAPI().loadIndicies();

        if(indexName.startsWith(ES_SITE_SEARCH_NAME)) {
        	info.site_search=indexName;
        }

        APILocator.getIndiciesAPI().point(info);
    }
	
	@Override
    public void deactivateIndex(String indexName) throws DotDataException, IOException {
        IndiciesInfo info=APILocator.getIndiciesAPI().loadIndicies();

        if(indexName.startsWith(ES_SITE_SEARCH_NAME)) {
        	info.site_search=null;
        }

        APILocator.getIndiciesAPI().point(info);
    }
	
	@Override
	public synchronized boolean createSiteSearchIndex(String indexName, int shards) throws ElasticSearchException, IOException {

		CreateIndexResponse cir = iapi.createIndex(indexName, null, shards);
		int i = 0;
		while(!cir.acknowledged()){
			
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			if(i++ > 300){
				throw new ElasticSearchException("index timed out creating");
			}
		}
		
		
		ClassLoader classLoader = null;
		URL url = null;
		classLoader = Thread.currentThread().getContextClassLoader();
		url = classLoader.getResource("es-sitesearch-mapping.json");
        // create actual index
		String mapping = new String(com.liferay.util.FileUtil.getBytes(new File(url.getPath())));
			
		mappingAPI.putMapping(indexName, "content", mapping);
			
		
		
		return true;
	}
	
	@Override
	public List<ScheduledTask> getTasks() throws SchedulerException{

		List<ScheduledTask> tasks = QuartzUtils.getScheduledTasks(ES_SITE_SEARCH_NAME);
		return tasks;

		
	}
	
	@Override
	public void scheduleTask(SiteSearchConfig config) throws SchedulerException, ParseException, ClassNotFoundException{
		String name = config.getJobName();
		String cronString = config.getCronExpression();
		boolean runNow = config.runNow();
		

	    //Create new task with updated cron expression and properties
		if(runNow){
			if (!QuartzUtils.isJobSequentiallyScheduled("site-search-execute-once", "site-search-execute-once-group")) {
				config.put("squentiallyScheduled", true);
				SimpleScheduledTask scheduledTask = new SimpleScheduledTask("site-search-execute-once", ES_SITE_SEARCH_NAME, "Site Search ",SiteSearchJobProxy.class.getCanonicalName(), false,
						"site-search-execute-once-trigger", "site-search-execute-once-trigger-group", new Date(), null,
						SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NEXT_WITH_REMAINING_COUNT, 5, true, config, 0, 0);
				try{
				   QuartzUtils.scheduleTask(scheduledTask);
				}catch(Exception e){
					QuartzUtils.removeJob("site-search-execute-once", ES_SITE_SEARCH_NAME);	
				}
			}else{

			}
		}
		else{
			ScheduledTask	task = new CronScheduledTask(name, ES_SITE_SEARCH_NAME,"Site Search ", SiteSearchJobProxy.class.getCanonicalName(),new Date(),null, 5,config,cronString);
			
			QuartzUtils.scheduleTask(task);
		}
		
	}
	
	@Override
	public void deleteTask(String taskName) throws SchedulerException{
		List<ScheduledTask> tasks = getTasks();
		for(ScheduledTask t: tasks){
			if(t.getJobName().equals(taskName))
			//Pause and remove any current jobs in the group
			QuartzUtils.pauseJob(t.getJobName(), ES_SITE_SEARCH_NAME);
			QuartzUtils.removeTaskRuntimeValues(t.getJobName(), ES_SITE_SEARCH_NAME);
			QuartzUtils.removeJob(t.getJobName(), ES_SITE_SEARCH_NAME);	
		}	
	}
	
	
	
	
}
