package com.dotcms.publishing.bundlers;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import com.dotcms.publishing.BundlerStatus;
import com.dotcms.publishing.DotBundleException;
import com.dotcms.publishing.IBundler;
import com.dotcms.publishing.PublisherConfig;
import com.dotcms.publishing.PublisherUtil;
import com.dotmarketing.beans.Host;
import com.dotmarketing.beans.Identifier;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.business.IdentifierAPI;
import com.dotmarketing.business.UserAPI;
import com.dotmarketing.business.VersionableAPI;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.portlets.contentlet.business.ContentletAPI;
import com.dotmarketing.portlets.folders.business.FolderAPI;
import com.dotmarketing.portlets.htmlpages.business.HTMLPageAPI;
import com.dotmarketing.portlets.htmlpages.model.HTMLPage;
import com.dotmarketing.util.Config;
import com.dotmarketing.util.Logger;
import com.liferay.portal.model.User;

public class StaticHTMLPageBundler implements IBundler {

	private PublisherConfig config;
	ContentletAPI conAPI = null;
	UserAPI uAPI = null;
	FolderAPI fAPI = null;
	IdentifierAPI iAPI = null;
	HTMLPageAPI pAPI = null;
	VersionableAPI vAPI = null;
	User systemUser = null;
	
	public final static String HTML_ASSET_EXTENSION = ".dothtml" ;
	
	@Override
	public String getName() {
		return "Static HTML Page Bundler";
	}
	
	@Override
	public void setConfig(PublisherConfig pc) {
		config = pc;
		conAPI = APILocator.getContentletAPI();
		uAPI = APILocator.getUserAPI();
		fAPI = APILocator.getFolderAPI();
		iAPI = APILocator.getIdentifierAPI();
		pAPI = APILocator.getHTMLPageAPI();
		vAPI = APILocator.getVersionableAPI();
		try {
			systemUser = uAPI.getSystemUser();
		} catch (DotDataException e) {
			Logger.fatal(this,e.getMessage(),e);
		}
	}

	@Override
	public void generate(File bundleRoot, BundlerStatus status) throws DotBundleException{
		boolean include = true;
		boolean hasPatterns = false;
		List<String> patterns = null;
		List<Identifier> pageIdents = new ArrayList<Identifier>();
		List<Identifier> deletedIdents = new ArrayList<Identifier>();
		
		if(config.getExcludePatterns()!=null && config.getExcludePatterns().size()>0){
			hasPatterns = true;
			include = false;
			patterns = config.getExcludePatterns();
		}else if(config.getIncludePatterns()!=null && config.getIncludePatterns().size()>0){
			hasPatterns = true;
			include = true;
			patterns = config.getIncludePatterns();
		}
				
		try{
			for(Host h : config.getHosts()){
				if(!hasPatterns){
					try{
						pageIdents.addAll(iAPI.findByURIPattern(new HTMLPage().getType(), "/*",config.liveOnly(),false,include, h, config.getStartDate(), config.getEndDate()));
					}catch (NullPointerException e) {}
				}else{
					for(String pattern : patterns){
						try{
							pageIdents.addAll(iAPI.findByURIPattern(new HTMLPage().getType(),pattern ,config.liveOnly(),true, include, h, config.getStartDate(), config.getEndDate()));
						}catch (NullPointerException e) {}
					}
				}
				try{
					deletedIdents.addAll(iAPI.findByURIPattern(new HTMLPage().getType(), "/*",config.liveOnly(),true,include, h, config.getStartDate(), config.getEndDate()));
				}catch(NullPointerException e){}
				for (Identifier i : pageIdents) {
					String html = null;
					if(!config.liveOnly()){
						try{
							html = pAPI.getHTML(i.getURI(), h,false , null, uAPI.getSystemUser());
						}catch(Exception e){
							Logger.error(this, e.getMessage() + " Unable to get page", e);
						}
						HTMLPageWrapper w = new HTMLPageWrapper();
						try{
							w.setIdentifier(i);
							w.setVersionInfo(vAPI.getVersionInfo(i.getId()));
							w.setPage(pAPI.loadWorkingPageById(i.getId(), uAPI.getSystemUser(), true));
						}catch(Exception e){
							Logger.error(this, e.getMessage() + " : Unable to get HTMLPage to write to bundle", e);
							continue;
						}
						try{
							writeFileToDisk(bundleRoot,w, html, i.getURI(), h, false);
						}catch (IOException e) {
							Logger.error(this, e.getMessage() + " : Unable to write HTML to bundle", e);
						}
						html=null;
					}
					
					try{
						html = pAPI.getHTML(i.getURI(), h,true , null, uAPI.getSystemUser());
					}catch(Exception e){
						Logger.error(this, e.getMessage() + " Unable to get page", e);
					}
					HTMLPageWrapper w = new HTMLPageWrapper();
					try{
						w.setIdentifier(i);
						w.setVersionInfo(vAPI.getVersionInfo(i.getId()));
						w.setPage(pAPI.loadLivePageById(i.getId(), uAPI.getSystemUser(), true));
					}catch(Exception e){
						Logger.error(this, e.getMessage() + " : Unable to get HTMLPage to write to bundle", e);
						continue;
					}
					try{
						writeFileToDisk(bundleRoot,w, html, i.getURI(), h, true);
					}catch (IOException e) {
						Logger.error(this, e.getMessage() + " : Unable to write HTML to bundle", e);
					}
					html=null;
				}
			}
		}catch (DotDataException e) {
			Logger.error(this, e.getMessage() + " : Unable to get Pages for Start HTML Bundler",e);
		}
	}
	
	private void writeFileToDisk(File bundleRoot, HTMLPageWrapper htmlPageWrapper, String html, String uri, Host h, boolean live) throws IOException, DotBundleException{
		if(html == null || uri == null){
			Logger.warn(this, "HTML or URI is not set for Bundler to write");
			return;
		}
		try{
			String wrapperFile = bundleRoot.getPath() + File.separator 
					+ (live ? "live" : "working") + File.separator 
					+ h.getHostname() 
					+ uri.replace("/", File.separator) + HTML_ASSET_EXTENSION;
			File wf = new File(wrapperFile);
			
			String staticFile = bundleRoot.getPath() + File.separator 
					+ (live ? "live" : "working") + File.separator 
					+ h.getHostname() 
					+ uri.replace("/", File.separator) + "." + Config.getStringProperty("VELOCITY_PAGE_EXTENSION");
			File sf = new File(staticFile);
			
			// Should we write or is the file already there:
			Calendar cal = Calendar.getInstance();
			cal.setTime(htmlPageWrapper.getPage().getModDate());
			cal.set(Calendar.MILLISECOND, 0);
			
			String dir = wrapperFile.substring(0, wrapperFile.lastIndexOf(File.separator));
			new File(dir).mkdirs();
			
			if(wf.exists() && wf.lastModified() == cal.getTimeInMillis()){
				BundlerUtil.objectToXML(htmlPageWrapper, wf);
				// set the time of the file
				wf.setLastModified(cal.getTimeInMillis());
			}
			
			if(sf.exists() && sf.lastModified() == cal.getTimeInMillis()){
				try {
					if(!sf.exists())sf.createNewFile();
					FileWriter fstream = new FileWriter(sf);
					BufferedWriter out = new BufferedWriter(fstream);
					out.write(html);
					out.close();
					sf.setLastModified(cal.getTimeInMillis());
				} catch (FileNotFoundException e) {
					Logger.error(PublisherUtil.class,e.getMessage(),e);
				}catch (IOException e) {
					Logger.error(PublisherUtil.class,e.getMessage(),e);
				}
			}
		}
		catch(Exception e){
			throw new DotBundleException("cant get host for " + uri + " reason " + e.getMessage());
		}
	}


	@Override
	public FileFilter getFileFilter(){
		return new StaticHTMLBundlerFilter();
	}
	
	public class StaticHTMLBundlerFilter implements FileFilter{

		@Override
		public boolean accept(File pathname) {

			return (pathname.isDirectory() || pathname.getName().endsWith(HTML_ASSET_EXTENSION));
		}

	}

}
